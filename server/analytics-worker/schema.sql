-- 匿名每日活跃统计。第一阶段只有这一张表。
--
-- 表里没有任何可以指回某个人的列：day 是服务器算的北京时间日期，anon_id 是服务端密钥
-- HMAC 过的安装标识，app_version 是版本号。(day, anon_id) 联合主键 + INSERT OR IGNORE
-- 就是「同一安装一天只算一次」的全部实现，不靠应用层的先查后写。
--
-- 迁移方式：wrangler d1 execute campuskit-analytics --file=./schema.sql --remote

CREATE TABLE IF NOT EXISTS daily_active (
    day         TEXT NOT NULL,
    anon_id     TEXT NOT NULL,
    app_version TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (day, anon_id)
) WITHOUT ROWID;

-- 常用查询（控制台里手动跑，应用不提供读取接口）：
--
-- 每日 DAU，最近 30 天：
--   SELECT day, COUNT(*) AS dau FROM daily_active
--    WHERE day >= date('now', '+8 hours', '-29 days')
--    GROUP BY day ORDER BY day DESC;
--
-- 最近 30 天 MAU（同一 anon_id 在窗口内只算一次）：
--   SELECT COUNT(DISTINCT anon_id) AS mau FROM daily_active
--    WHERE day >= date('now', '+8 hours', '-29 days');
--
-- 某一天的版本分布：
--   SELECT app_version, COUNT(DISTINCT anon_id) AS active FROM daily_active
--    WHERE day = date('now', '+8 hours') GROUP BY app_version ORDER BY active DESC;
