import assert from 'node:assert/strict';
import test from 'node:test';

import worker from '../src/index.js';

const SECRET = 'test-secret-value-0123456789';
const INSTALL = '0f8fad5b-d9cb-469f-a165-70867728950e';
let ipSeed = 0;

/** 用内存表替代 D1：把 (day, anon_id) 联合主键与 INSERT OR IGNORE 的语义照抄一遍。 */
function fakeDb() {
  const rows = new Map();
  const calls = [];
  return {
    rows,
    calls,
    prepare(sql) {
      return {
        bind(...args) {
          return {
            async run() {
              calls.push({ sql, args });
              if (sql.startsWith('INSERT OR IGNORE')) {
                const key = args[0] + '|' + args[1];
                if (rows.has(key)) return { meta: { changes: 0 } };
                rows.set(key, args[2]);
                return { meta: { changes: 1 } };
              }
              const column = sql.includes('anon_id = ?') ? 1 : 0;
              let removed = 0;
              for (const key of [...rows.keys()]) {
                const parts = key.split('|');
                if (column === 1 ? parts[1] === args[0] : parts[0] < args[0]) {
                  rows.delete(key);
                  removed += 1;
                }
              }
              return { meta: { changes: removed } };
            },
          };
        },
      };
    },
  };
}

function env(withSecret = true) {
  return withSecret
    ? { DB: fakeDb(), ANALYTICS_HMAC_SECRET: SECRET }
    : { DB: fakeDb() };
}

function post(path, body, ip) {
  return new Request('https://analytics.example' + path, {
    method: 'POST',
    headers: { 'cf-connecting-ip': ip || 'ip-' + (++ipSeed), 'content-type': 'application/json' },
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });
}

const active = (body, ip) => worker.fetch(post('/v1/active', body, ip), env(), {});
const activeWith = (e, body, ip) => worker.fetch(post('/v1/active', body, ip), e, {});

test('首次上报写一条，当天再报只计数不新增', async () => {
  const e = env();
  const first = await activeWith(e, { installId: INSTALL, version: '2.6.0-beta.11' });
  assert.equal(first.status, 200);
  const firstBody = await first.json();
  assert.equal(firstBody.ok, true);
  assert.equal(firstBody.counted, true);
  assert.match(firstBody.day, /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(e.DB.rows.size, 1);

  // 同一天再来 9 次：行数不变，counted 变 false。
  for (let i = 0; i < 9; i++) {
    const repeat = await activeWith(e, { installId: INSTALL, version: '2.6.0-beta.11' });
    assert.equal(repeat.status, 200);
    assert.equal((await repeat.json()).counted, false);
  }
  assert.equal(e.DB.rows.size, 1);
});

test('表里存的是 HMAC，不是客户端 UUID', async () => {
  const e = env();
  await activeWith(e, { installId: INSTALL, version: '2.6.0' });
  const [key] = [...e.DB.rows.keys()];
  const [, stored] = key.split('|');
  assert.match(stored, /^[0-9a-f]{64}$/);
  assert.equal(key.includes(INSTALL), false);
  assert.equal(e.DB.rows.get(key), '2.6.0');
});

test('不同版本分别计数，同一天两个安装是两条', async () => {
  const e = env();
  await activeWith(e, { installId: INSTALL, version: '2.6.0' });
  await activeWith(e, { installId: '11111111-1111-4111-8111-111111111111', version: '2.6.0-beta.11' });
  assert.equal(e.DB.rows.size, 2);
  const versions = [...e.DB.rows.values()].sort();
  assert.deepEqual(versions, ['2.6.0', '2.6.0-beta.11']);
});

test('不合规请求一律 400，且不写库', async () => {
  const e = env();
  const bad = [
    { installId: 'abc', version: '2.6.0' },
    { installId: INSTALL },
    { installId: INSTALL, version: '2.6.0', studentId: '20230001' },
    'not json',
    '',
  ];
  for (const body of bad) {
    const reply = await activeWith(e, body);
    assert.equal(reply.status, 400, 'expected 400 for ' + JSON.stringify(body));
    assert.equal((await reply.json()).ok, false);
  }
  assert.equal(e.DB.rows.size, 0);
  assert.equal(e.DB.calls.length, 0);
});

test('超长请求体 413', async () => {
  const reply = await active({ installId: INSTALL, version: '2.6.0', pad: 'x'.repeat(600) });
  assert.equal(reply.status, 413);
});

test('没有 HMAC 密钥就 503，绝不退化成明文入库', async () => {
  const e = env(false);
  const reply = await activeWith(e, { installId: INSTALL, version: '2.6.0' });
  assert.equal(reply.status, 503);
  assert.equal((await reply.json()).ok, false);
  assert.equal(e.DB.rows.size, 0);
});

test('同一 IP 一分钟超过 20 次就 429', async () => {
  const ip = 'rate-limited-ip';
  for (let i = 0; i < 20; i++) {
    const reply = await active({ installId: INSTALL, version: '2.6.0' }, ip);
    assert.notEqual(reply.status, 429, 'request ' + i + ' should still be served');
  }
  const blocked = await active({ installId: INSTALL, version: '2.6.0' }, ip);
  assert.equal(blocked.status, 429);
  // 换一个 IP 不受影响。
  assert.notEqual((await active({ installId: INSTALL, version: '2.6.0' }, 'other-ip')).status, 429);
});

test('删除接口按匿名标识删干净，重复删除不再计数', async () => {
  const e = env();
  await activeWith(e, { installId: INSTALL, version: '2.6.0' });
  assert.equal(e.DB.rows.size, 1);
  const first = await worker.fetch(post('/v1/forget', { installId: INSTALL }), e, {});
  assert.equal(first.status, 200);
  assert.deepEqual(await first.json(), { ok: true, removed: 1 });
  assert.equal(e.DB.rows.size, 0);
  const again = await worker.fetch(post('/v1/forget', { installId: INSTALL }), e, {});
  assert.deepEqual(await again.json(), { ok: true, removed: 0 });
});

test('删除只影响这一个安装，别人的记录留着', async () => {
  const e = env();
  const other = '11111111-1111-4111-8111-111111111111';
  await activeWith(e, { installId: INSTALL, version: '2.6.0' });
  await activeWith(e, { installId: other, version: '2.6.0' });
  await worker.fetch(post('/v1/forget', { installId: INSTALL }), e, {});
  assert.equal(e.DB.rows.size, 1);
  assert.equal([...e.DB.rows.values()][0], '2.6.0');
});

test('路由：健康检查、未知路径、错误方法', async () => {
  const health = await worker.fetch(new Request('https://analytics.example/health'), env(), {});
  assert.equal(health.status, 200);
  assert.equal((await health.json()).service, 'campuskit-analytics');

  const missing = await worker.fetch(new Request('https://analytics.example/v1/stats'), env(), {});
  assert.equal(missing.status, 404);

  const wrongMethod = await worker.fetch(
    new Request('https://analytics.example/v1/active', { method: 'GET' }), env(), {});
  assert.equal(wrongMethod.status, 405);
});

test('定时任务删掉保留期以外的行', async () => {
  const e = env();
  const now = Date.now();
  // 手工塞两天：很久以前的一天，和今天。
  const { anonId, beijingDay, shiftDay } = await import('../src/rules.js');
  const id = await anonId(SECRET, INSTALL);
  e.DB.rows.set(shiftDay(beijingDay(now), -120) + '|' + id, '2.0.0');
  e.DB.rows.set(beijingDay(now) + '|' + id, '2.6.0');

  await worker.scheduled({}, e, {});
  assert.equal(e.DB.rows.size, 1);
  assert.equal([...e.DB.rows.keys()][0].startsWith(beijingDay(now)), true);
});
