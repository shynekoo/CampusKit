/**
 * 匿名每日活跃统计 Worker。
 *
 * <p>两个接口，都没有第三个：</p>
 *
 * <ul>
 *   <li>{@code POST /v1/active} —— 一个安装的一天。请求体只有 {@code installId}（客户端随机 UUID）
 *       与 {@code version}；日期由服务器现算（UTC+8），落库的是 HMAC 后的匿名标识。</li>
 *   <li>{@code POST /v1/forget} —— 读者关闭统计时删掉这个安装的历史记录。</li>
 * </ul>
 *
 * <p>这里故意没有查询接口：第一阶段的 DAU / MAU / 版本分布直接在 D1 控制台跑 SQL
 * （见 README）。不暴露读接口就没有「统计库被当成公开数据源」这条路，也没有要多写的凭据。</p>
 *
 * <p>失败一律不写库：密钥缺失 503、请求不合规 400、频率超限 429。客户端把这三种都当作
 * 「下次进前台再试」，不会因为统计服务坏了而影响任何功能。</p>
 */

import {
  InvalidRequest,
  FORGET_SQL,
  INSERT_SQL,
  MAX_BODY_BYTES,
  PURGE_SQL,
  anonId,
  beijingDay,
  oldestKeptDay,
  readActiveBody,
  readForgetBody,
} from './rules.js';

/** 单 IP 每分钟的请求上限。D1 的联合主键已经挡住重复计数，这一层挡的是纯流量。 */
const RATE_LIMIT_PER_MINUTE = 20;
const RATE_WINDOW_MS = 60000;
/** 计数表上限：超过就整表清空重建，内存里只放最近一分钟的键，不能无限长。 */
const RATE_MAX_KEYS = 4096;

const buckets = new Map();

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, '');

    if (request.method === 'GET' && (path === '' || path === '/health')) {
      // 只回答「这个部署活着」。没有任何业务数据，也不透露密钥配置。
      return json({ ok: true, service: 'campuskit-analytics' }, 200);
    }
    if (path !== '/v1/active' && path !== '/v1/forget') return json({ ok: false, error: 'not found' }, 404);
    if (request.method !== 'POST') return json({ ok: false, error: 'method not allowed' }, 405);
    if (tooMany(request)) return json({ ok: false, error: 'too many requests' }, 429);

    let text;
    try {
      text = await readBody(request);
    } catch (tooLarge) {
      return json({ ok: false, error: 'body too large' }, 413);
    }

    try {
      return path === '/v1/active'
        ? await active(text, env, request)
        : await forget(text, env);
    } catch (invalid) {
      if (invalid instanceof InvalidRequest) return json({ ok: false, error: invalid.message }, 400);
      // 密钥缺失、D1 不可用：不写半条记录，也不把内部错误原文回给客户端。
      console.error('analytics failed:', invalid && invalid.message);
      return json({ ok: false, error: 'service unavailable' }, 503);
    }
  },

  /**
   * 定时清理：每天删掉保留期以外的行。`day` 是主键第一列，这条 DELETE 走主键前缀，
   * 不需要额外索引。
   */
  async scheduled(event, env) {
    const cutoff = oldestKeptDay(Date.now());
    const result = await env.DB.prepare(PURGE_SQL).bind(cutoff).run();
    const removed = result && result.meta ? result.meta.changes : 0;
    console.log('analytics purge:', cutoff, removed);
  },
};

async function active(text, env, request) {
  const { installId, version } = readActiveBody(text);
  const secret = env.ANALYTICS_HMAC_SECRET;
  // 没有密钥就不写库：宁可这次统计失败，也不能把可被对照的 UUID 落到磁盘上。
  const identifier = await anonId(secret, installId);
  const day = beijingDay(Date.now());
  const result = await env.DB.prepare(INSERT_SQL).bind(day, identifier, version).run();
  const counted = !!(result && result.meta && result.meta.changes > 0);
  return json({ ok: true, counted, day }, 200);
}

async function forget(text, env) {
  const { installId } = readForgetBody(text);
  const identifier = await anonId(env.ANALYTICS_HMAC_SECRET, installId);
  const result = await env.DB.prepare(FORGET_SQL).bind(identifier).run();
  const removed = result && result.meta ? result.meta.changes : 0;
  return json({ ok: true, removed }, 200);
}

/** 按 Content-Length 先挡一次，再按实际字节数挡一次：只信头不够。 */
async function readBody(request) {
  const declared = Number(request.headers.get('content-length') || 0);
  if (declared > MAX_BODY_BYTES) throw new Error('too large');
  const text = await request.text();
  if (new TextEncoder().encode(text).length > MAX_BODY_BYTES) throw new Error('too large');
  return text;
}

/**
 * 一分钟滑动窗口。键是 Cloudflare 给的客户端 IP，只存在这个 isolate 的内存里，不落盘、
 * 不进日志；换一个 isolate 就从零开始——所以这一层是「防手抖和防脚本」，真正的用量控制是
 * 面板上的 Rate limiting 规则（README 里写了怎么开）。
 */
function tooMany(request) {
  const ip = request.headers.get('cf-connecting-ip') || 'unknown';
  const now = Date.now();
  if (buckets.size > RATE_MAX_KEYS) buckets.clear();
  const seen = buckets.get(ip);
  if (!seen || now - seen.start >= RATE_WINDOW_MS) {
    buckets.set(ip, { start: now, count: 1 });
    return false;
  }
  seen.count += 1;
  return seen.count > RATE_LIMIT_PER_MINUTE;
}

function json(body, status) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      // 统计应答没有任何可复用价值，也别让中间层缓存。
      'cache-control': 'no-store',
    },
  });
}
