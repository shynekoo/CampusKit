// SPDX-License-Identifier: MPL-2.0
import assert from 'node:assert/strict';
import test from 'node:test';

import {
  InvalidRequest,
  anonId,
  beijingDay,
  isUuid,
  isVersion,
  oldestKeptDay,
  readActiveBody,
  readForgetBody,
  shiftDay,
} from '../src/rules.js';

const INSTALL = '0f8fad5b-d9cb-469f-a165-70867728950e';

test('北京时间按 UTC+8 划分自然日', () => {
  // 2026-11-20T15:59:59Z = 北京时间 23:59:59，仍是 11-20
  assert.equal(beijingDay(Date.parse('2026-11-20T15:59:59Z')), '2026-11-20');
  // 2026-11-20T16:00:00Z = 北京时间次日 00:00:00
  assert.equal(beijingDay(Date.parse('2026-11-20T16:00:00Z')), '2026-11-21');
  assert.equal(beijingDay(Date.parse('2026-01-01T00:00:00Z')), '2026-01-01');
  assert.equal(beijingDay(Date.parse('2025-12-31T16:00:00Z')), '2026-01-01');
  // 客户端时钟再离谱，这个函数也只吃服务器传进来的毫秒数。
  assert.equal(beijingDay(0), '1970-01-01');
});

test('闰年与月末的日期算术', () => {
  assert.equal(shiftDay('2024-02-28', 1), '2024-02-29');
  assert.equal(shiftDay('2024-02-29', 1), '2024-03-01');
  assert.equal(shiftDay('2026-03-01', -1), '2026-02-28');
  assert.equal(shiftDay('2026-01-01', -1), '2025-12-31');
  assert.equal(shiftDay('2026-12-31', 1), '2027-01-01');
  assert.equal(shiftDay('2026-11-20', 0), '2026-11-20');
});

test('保留期正好是 90 个自然日（含今天）', () => {
  const now = Date.parse('2026-11-20T04:00:00Z');
  assert.equal(oldestKeptDay(now), '2026-08-23');
  assert.equal(oldestKeptDay(now, 1), '2026-11-20');
  assert.equal(oldestKeptDay(now, 90), shiftDay('2026-11-20', -89));
});

test('installId 必须是 UUID，版本号必须是白名单字符', () => {
  assert.equal(isUuid(INSTALL), true);
  assert.equal(isUuid(INSTALL.toUpperCase()), true);
  assert.equal(isUuid('not-a-uuid'), false);
  assert.equal(isUuid(''), false);
  assert.equal(isUuid(null), false);
  assert.equal(isUuid(INSTALL + '0'), false);

  assert.equal(isVersion('2.6.0'), true);
  assert.equal(isVersion('2.6.0-beta.11'), true);
  assert.equal(isVersion('2.6.0 beta'), false);
  assert.equal(isVersion('x'.repeat(33)), false);
  assert.equal(isVersion(''), false);
});

test('上报请求体只认 installId 与 version', () => {
  const parsed = readActiveBody(JSON.stringify({ installId: INSTALL, version: '2.6.0-beta.11' }));
  assert.deepEqual(parsed, { installId: INSTALL, version: '2.6.0-beta.11' });
  // 大写 UUID 归一成小写：HMAC 的输入形状必须唯一，否则同一安装能出两个标识。
  assert.equal(readActiveBody(JSON.stringify({ installId: INSTALL.toUpperCase(), version: '2.6.0' })).installId, INSTALL);

  const rejected = [
    JSON.stringify({ installId: INSTALL, version: '2.6.0', studentId: '20230001' }),
    JSON.stringify({ installId: INSTALL }),
    JSON.stringify({ installId: INSTALL, version: '2.6.0', day: '2026-11-20' }),
    JSON.stringify({ installId: 'abc', version: '2.6.0' }),
    JSON.stringify({ installId: INSTALL, version: '2.6.0 ' + 'x'.repeat(40) }),
    JSON.stringify([INSTALL]),
    'null',
    '',
    'not json',
  ];
  for (const body of rejected) {
    assert.throws(() => readActiveBody(body), InvalidRequest, 'should reject: ' + body);
  }
});

test('删除请求体只认 installId', () => {
  assert.deepEqual(readForgetBody(JSON.stringify({ installId: INSTALL })), { installId: INSTALL });
  assert.throws(() => readForgetBody(JSON.stringify({ installId: INSTALL, version: '2.6.0' })), InvalidRequest);
  assert.throws(() => readForgetBody(JSON.stringify({ installId: 'nope' })), InvalidRequest);
});

test('落库标识是 HMAC，且与 UUID 不可对照', async () => {
  const secret = 'test-secret-value-0123456789';
  const first = await anonId(secret, INSTALL);
  assert.match(first, /^[0-9a-f]{64}$/);
  assert.equal(first, await anonId(secret, INSTALL.toUpperCase()), '大小写归一后必须稳定');
  assert.notEqual(first, INSTALL.replace(/-/g, ''));
  // 换一把密钥，同一个 installId 就对应不到原来那条记录。
  assert.notEqual(first, await anonId('another-secret-value-0123456789', INSTALL));
  assert.notEqual(first, await anonId(secret, '11111111-1111-4111-8111-111111111111'));
  await assert.rejects(() => anonId('short', INSTALL), /ANALYTICS_HMAC_SECRET/);
  await assert.rejects(() => anonId(undefined, INSTALL), /ANALYTICS_HMAC_SECRET/);
});
