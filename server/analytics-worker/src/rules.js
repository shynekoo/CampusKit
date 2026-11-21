// SPDX-License-Identifier: MPL-2.0
/**
 * 匿名每日活跃统计的纯规则。没有 D1、没有 Request —— 全部输入输出都是普通值，所以能在
 * `node --test` 里逐条断言，而不必起一个 Worker。
 *
 * 四条约束，改动时不要放松：
 *
 * 1. **日期只由服务器决定**：`beijingDay()` 拿 Date 现算（UTC+8），请求体里没有日期字段，
 *    也没有任何地方读客户端的时钟。
 * 2. **落库的从来不是 installId**：`anonId()` 先用服务端密钥做 HMAC-SHA256，再取 hex；
 *    密钥缺失时整个接口 503，绝不退化成明文入库。
 * 3. **字段形状固定**：UUID 与版本号都是白名单字符集，长度有上限，未知字段直接拒绝，
 *    免得请求体成为夹带其它东西的通道。
 * 4. **保留 90 天**：`oldestKeptDay()` 是唯一一处算「哪一天之前的数据该删」的地方，
 *    定时任务和文档都以它为准。
 */

/** installId 是客户端生成的 UUID，36 个字符就是上限。 */
export const MAX_INSTALL_ID = 36;
/** 版本号（2.6.0-beta.11）远短于这个数；长度上限只是防夹带。 */
export const MAX_VERSION = 32;
/** 整个请求体的上限。合法请求不到 100 字节。 */
export const MAX_BODY_BYTES = 512;
/** 保留天数：早于「今天 - 90 天」的行由定时任务删除。 */
export const RETENTION_DAYS = 90;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const VERSION = /^[A-Za-z0-9._+-]{1,32}$/;
const DAY_MS = 86400000;
const BEIJING_OFFSET_MS = 8 * 3600 * 1000;

/** 请求不合规时抛它；Worker 把它翻成 400，且不写库。 */
export class InvalidRequest extends Error {
  constructor(message) {
    super(message);
    this.name = 'InvalidRequest';
  }
}

/**
 * UTC+8 的日期，`YYYY-MM-DD`。用算术而不是 toLocaleDateString：后者的输出依赖运行时的
 * ICU 与 locale，换一个版本就可能变成 `2026/11/20`，而这张表的主键靠字符串比较。
 */
export function beijingDay(nowMs) {
  const shifted = Number(nowMs) + BEIJING_OFFSET_MS;
  const days = Math.floor(shifted / DAY_MS);
  const { year, month, day } = civilFromDays(days);
  return `${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}`;
}

/** 把一个 `YYYY-MM-DD` 前后挪若干天，用来算保留期的截止日。 */
export function shiftDay(day, delta) {
  const { year, month, date } = parseDay(day);
  return beijingDay(Date.UTC(year, month - 1, date) + Number(delta) * DAY_MS - BEIJING_OFFSET_MS);
}

/**
 * 保留期里最早还留着的那一天。`day < oldestKeptDay()` 的行删掉，于是正好留下
 * `keepDays` 个自然日（默认 90：今天往前数 90 天，含今天）。
 */
export function oldestKeptDay(nowMs, keepDays = RETENTION_DAYS) {
  const span = Math.max(1, Math.abs(Math.trunc(keepDays)));
  return shiftDay(beijingDay(nowMs), -(span - 1));
}

export function isUuid(value) {
  return typeof value === 'string' && UUID.test(value.trim());
}

export function isVersion(value) {
  return typeof value === 'string' && VERSION.test(value.trim());
}

/**
 * 读一个上报请求体。只接受 `{ "installId", "version" }`，多一个字段都不收——一个「顺便带上
 * 学号」的客户端不该被悄悄接受。
 */
export function readActiveBody(text) {
  const body = parseObject(text);
  const keys = Object.keys(body).sort();
  if (keys.length !== 2 || keys[0] !== 'installId' || keys[1] !== 'version') {
    throw new InvalidRequest('expected exactly installId and version');
  }
  const installId = String(body.installId).trim();
  const version = String(body.version).trim();
  if (!isUuid(installId)) throw new InvalidRequest('installId is not a uuid');
  if (!isVersion(version)) throw new InvalidRequest('version has an unexpected shape');
  return { installId: installId.toLowerCase(), version };
}

/** 读一个删除请求体：同样只认 installId。 */
export function readForgetBody(text) {
  const body = parseObject(text);
  const keys = Object.keys(body).sort();
  if (keys.length !== 1 || keys[0] !== 'installId' || !isUuid(String(body.installId))) {
    throw new InvalidRequest('expected exactly installId');
  }
  return { installId: String(body.installId).trim().toLowerCase() };
}

function parseObject(text) {
  if (typeof text !== 'string' || text.trim() === '') throw new InvalidRequest('empty body');
  let body;
  try {
    body = JSON.parse(text);
  } catch (broken) {
    throw new InvalidRequest('body is not json');
  }
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    throw new InvalidRequest('body is not an object');
  }
  return body;
}

/**
 * 落库用的匿名标识：HMAC-SHA256(secret, installId) 的十六进制。
 *
 * <p>为什么要 HMAC 而不是直接存 UUID：UUID 本身是一个稳定的「这台安装」标签，拿到表的人可以
 * 拿它去和别的日志对照；加了服务端密钥之后，表里的值换一把密钥就换一套，和客户端 UUID 也不
 * 可逆地对应不上。同一把密钥下它是稳定的，所以「同一安装一天只算一次」仍然成立。</p>
 */
export async function anonId(secret, installId) {
  if (typeof secret !== 'string' || secret.length < 16) {
    throw new Error('ANALYTICS_HMAC_SECRET is missing or too short');
  }
  const key = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign(
    'HMAC', key, new TextEncoder().encode(String(installId).trim().toLowerCase()));
  return [...new Uint8Array(signature)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** 入库语句只有这一条：联合主键 + INSERT OR IGNORE 就是「一天一次」的全部实现。 */
export const INSERT_SQL =
  'INSERT OR IGNORE INTO daily_active (day, anon_id, app_version) VALUES (?, ?, ?)';
export const FORGET_SQL = 'DELETE FROM daily_active WHERE anon_id = ?';
export const PURGE_SQL = 'DELETE FROM daily_active WHERE day < ?';

function pad(value, width) {
  return String(value).padStart(width, '0');
}

function parseDay(day) {
  const matched = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(day));
  if (!matched) throw new Error('day is not YYYY-MM-DD: ' + day);
  return { year: +matched[1], month: +matched[2], date: +matched[3] };
}

/** 天数（1970-01-01 起，允许负数）→ 公历年月日。Howard Hinnant 的 civil_from_days。 */
function civilFromDays(days) {
  const z = days + 719468;
  const era = Math.floor(z / 146097);
  const doe = z - era * 146097;
  const yoe = Math.floor((doe - Math.floor(doe / 1460) + Math.floor(doe / 36524) - Math.floor(doe / 146096)) / 365);
  const y = yoe + era * 400;
  const doy = doe - (365 * yoe + Math.floor(yoe / 4) - Math.floor(yoe / 100));
  const mp = Math.floor((5 * doy + 2) / 153);
  const day = doy - Math.floor((153 * mp + 2) / 5) + 1;
  const month = mp < 10 ? mp + 3 : mp - 9;
  return { year: month <= 2 ? y + 1 : y, month, day };
}
