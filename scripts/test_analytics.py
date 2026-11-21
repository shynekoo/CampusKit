# SPDX-License-Identifier: MPL-2.0
"""Guard the anonymous activity statistics' privacy boundary and its tests.

Run with: python scripts/test_analytics.py

Why a separate entry from test_diag_privacy.py: that one guards what the log says, this one guards
what actually leaves the phone. The risks are different and so are the checks:

1. the payload can only ever carry an install uuid and a version - no device id, no account, no date;
2. the uuid is only created after consent, and consent defaults to off, so an upgrade cannot switch
   it on;
3. the trigger fires once per foreground from App, not from a page's onResume (a page can resume
   many times a day);
4. the server never stores the uuid itself - only an HMAC of it - and its table has no column that
   could hold one;
5. the worker's own tests run here, so the dedupe / retention / delete behaviour is exercised on
   every build instead of being asserted in prose.
"""
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREVIEW = ROOT / "client/android/src/io/qzxy/lite/preview"
CORE = ROOT / "client/core/src/io/qzxy/lite/core"
WORKER = ROOT / "server/analytics-worker"

failures = []
checks = 0


def require(condition, message):
    global checks
    checks += 1
    if not condition:
        failures.append(message)


def require_text(source, needle, where, why):
    require(needle in source, "%s: missing %r (%s)" % (where, needle, why))


def without_comments(text):
    """Strip block and line comments: a doc line saying "we never read ANDROID_ID" is not a read."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return "\n".join(line for line in text.splitlines() if not line.lstrip().startswith("//"))


def body_of(source, signature):
    """The method body, from its signature to the next method-level closing brace."""
    at = source.find(signature)
    if at < 0:
        return ""
    depth = 0
    started = False
    for index in range(at, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
            started = True
        elif char == "}":
            depth -= 1
            if started and depth == 0:
                return source[at:index + 1]
    return source[at:]


def main():
    prefs_path = PREVIEW / "AnalyticsPreferences.java"
    reporter_path = PREVIEW / "AnalyticsReporter.java"
    for path in (prefs_path, reporter_path):
        require(path.exists(), "缺少统计模块文件 %s" % path.name)
    if not prefs_path.exists() or not reporter_path.exists():
        report()
        return

    prefs = prefs_path.read_text(encoding="utf-8")
    reporter = reporter_path.read_text(encoding="utf-8")
    app = (PREVIEW / "App.java").read_text(encoding="utf-8")
    timetable = (PREVIEW / "TimetableActivity.java").read_text(encoding="utf-8")
    more = (PREVIEW / "MoreActivity.java").read_text(encoding="utf-8")
    privacy = (PREVIEW / "PrivacyActivity.java").read_text(encoding="utf-8")
    legal = (PREVIEW / "LegalText.java").read_text(encoding="utf-8")
    manifest = (ROOT / "client/android/AndroidManifest.xml").read_text(encoding="utf-8")
    request = (CORE / "AnalyticsRequest.java").read_text(encoding="utf-8")

    # --- 1. 请求体只有两样东西 -----------------------------------------------------------
    require_text(request, "public static final String ACTIVE_PATH = \"/v1/active\"", "AnalyticsRequest",
                 "上报路径必须固定")
    require_text(request, "MAX_INSTALL_ID = 36", "AnalyticsRequest", "UUID 长度上限")
    require_text(request, "MAX_VERSION = 32", "AnalyticsRequest", "版本号长度上限")
    require_text(request, "UpdateUrls.requireHttps(full)", "AnalyticsRequest",
                 "统计地址只允许 HTTPS，复用已经测过的那条规则")
    code_reporter, code_prefs = without_comments(reporter), without_comments(prefs)
    for banned in ("ANDROID_ID", "getImei", "IMEI", "OAID", "androidId", "getMacAddress", "MAC"):
        require(banned not in code_reporter and banned not in code_prefs,
                "统计模块不得引用设备标识 %r" % banned)
    require("studentId" not in reporter and "学号" not in reporter.replace("不含学号", ""),
            "统计请求里不能有学号字段")

    # --- 2. 默认关；没同意就不生成标识 ---------------------------------------------------
    require_text(prefs, "getBoolean(KEY_CONSENT, false)", "AnalyticsPreferences",
                 "同意状态必须默认 false，升级安装不得自动开启")
    install_getter = body_of(prefs, "static String installId(Context context)")
    require(install_getter != "", "AnalyticsPreferences: installId 读取器缺失")
    require("randomUUID" not in install_getter,
            "AnalyticsPreferences: 读取标识不能顺手生成——生成就等于开始采集")
    generator = body_of(prefs, "static String ensureInstallId(Context context)")
    require("UUID.randomUUID()" in generator, "AnalyticsPreferences: 标识必须是本机随机 UUID")
    require(code_prefs.count("randomUUID()") == 1,
            "AnalyticsPreferences: 代码里的生成点应当只有一处（ensureInstallId）")
    require_text(prefs, "!AnalyticsDay.today().equals(reportedDay(context))", "AnalyticsPreferences",
                 "一天一次由日期对照决定")
    due = body_of(prefs, "static boolean dueToday(Context context)")
    require("consented(context)" in due and "installId(context) == null" in due,
            "AnalyticsPreferences: 必须先有同意、再有标识，才轮到日期")

    # --- 3. 触发点：App 的前台切换，且只此一处 ------------------------------------------
    require_text(app, "AnalyticsReporter.onForeground(getApplicationContext())", "App",
                 "统计只在进程进入前台时检查")
    started = body_of(app, "public void onActivityStarted(Activity activity)")
    require("started++ > 0" in started, "App: 只有第一页 started 才算进入前台")
    require_text(app, "if (started > 0) started--;", "App", "退到后台要把前台计数还回去")
    require("AnalyticsReporter" not in timetable,
            "TimetableActivity: 不要在页面 onResume 里再触发一次统计")

    # --- 4. 只有服务端认可才记「今天报过了」 --------------------------------------------
    require_text(reporter, "if (AnalyticsReply.accepted(verdict))", "AnalyticsReporter",
                 "本地日期只能在服务端确认后写入")
    accepted_block = body_of(reporter, "private static void report(Context context)")
    require("setReportedDay" in accepted_block and "AnalyticsReply.accepted" in accepted_block,
            "AnalyticsReporter: setReportedDay 必须写在 accepted 分支里")
    forget_block = body_of(reporter, "static boolean forget(Context context)")
    require("AnalyticsPreferences.consented(context)" in forget_block,
            "AnalyticsReporter: 统计仍开着时只删历史，不能把标识一起丢掉（统计会悄悄停掉）")
    require_text(reporter, "setInstanceFollowRedirects(false)", "AnalyticsReporter",
                 "统计请求不跟随重定向")
    require_text(reporter, "setDaemon(true)", "AnalyticsReporter", "不上报也要能退出进程")
    require("while (true)" not in reporter and "for (int attempt" not in reporter,
            "AnalyticsReporter: 不允许循环重试，失败就等下次进前台")

    # --- 5. 诊断日志里不出现标识与请求体 ------------------------------------------------
    allowed = {"trace", "http", "stage", "duration", "count", "status"}
    for name, source in (("AnalyticsReporter", reporter), ("AnalyticsPreferences", prefs)):
        for needle in ("Diag.event(", "Diag.failure("):
            cursor = 0
            while True:
                at = source.find(needle, cursor)
                if at < 0:
                    break
                cursor = at + len(needle)
                call = source[at:source.find(");", at) + 2]
                for key in re.findall(r'"([a-z_]+)"\s*,', call):
                    require(key in allowed or key in ("ANALYTICS", "report.done", "report.failed",
                                                      "forget.done", "forget.failed"),
                            "%s: 统计日志字段 %r 不在允许列表内" % (name, key))
                require("installId" not in call and "body" not in call,
                        "%s: 统计日志调用里出现了标识或请求体" % name)

    # --- 6. 设置、隐私页与文案 ----------------------------------------------------------
    require_text(more, "private void analyticsSetting(", "MoreActivity", "更多页必须有统计开关")
    require_text(more, "AnalyticsPreferences.consented(this)", "MoreActivity", "开关读的是同意状态")
    require_text(more, "AnalyticsReporter.setConsent(this, false)", "MoreActivity", "关掉要停上报")
    require_text(privacy, "删除已上传的匿名记录", "PrivacyActivity",
                 "隐私页要能单独删除已上传记录")
    require_text(privacy, "AnalyticsPreferences.PREFS", "PrivacyActivity",
                 "清除全部本机数据要覆盖统计偏好")
    require_text(privacy, "AnalyticsReporter.forget(context)", "PrivacyActivity",
                 "清除前尽力删掉服务端记录")
    require_text(legal, "\"5. 匿名使用统计\"", "LegalText", "隐私说明必须有一节讲统计")
    require_text(legal, "90 天", "LegalText", "保留期必须写明")
    require_text(legal, "升级安装", "LegalText", "必须写明升级不会自动开启")
    require_text(legal, "HMAC-SHA256", "LegalText", "服务端处理方式必须写明")
    require_text(legal, "仍由手机直连各自官方平台", "LegalText",
                 "必须写明业务请求不经过统计服务")

    # --- 7. 清单：不新增权限，且不参与系统备份 ------------------------------------------
    declared = set(re.findall(r'uses-permission android:name="([^"]+)"', manifest))
    expected = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.CAMERA", "android.permission.REQUEST_INSTALL_PACKAGES"}
    require(declared == expected,
            "统计功能不应新增权限，当前清单：%s" % ", ".join(sorted(declared)))
    require('android:allowBackup="false"' in manifest,
            "清单必须保持 allowBackup=false，否则统计标识会跟着系统备份换机")

    # --- 8. 构建开关 --------------------------------------------------------------------
    sys.path.insert(0, str(ROOT / "scripts"))
    import build_preview
    generated = build_preview.write_build_flags(False, False, False).read_text(encoding="utf-8")
    require_text(generated, 'static final String ANALYTICS_ENDPOINT = "";', "BuildFlags",
                 "不给 --analytics-endpoint 时地址必须是空串")
    configured = build_preview.write_build_flags(
        False, False, False, "https://analytics.example.com").read_text(encoding="utf-8")
    require_text(configured, 'ANALYTICS_ENDPOINT = "https://analytics.example.com";', "BuildFlags",
                 "给的地址要原样写进构建产物")
    require(build_preview.analytics_endpoint([]) == "", "默认不配置统计服务")
    require(build_preview.analytics_endpoint(
        ["--analytics-endpoint", "https://a.example.com/"]) == "https://a.example.com",
        "地址要去掉结尾的斜杠")
    for bad in ("http://a.example.com", "a.example.com", "https://a b.example.com"):
        try:
            build_preview.analytics_endpoint(["--analytics-endpoint", bad])
        except RuntimeError:
            checks_ok = True
        else:
            checks_ok = False
        require(checks_ok, "--analytics-endpoint 必须拒绝 %r" % bad)

    # --- 9. 服务端：不存原始 UUID，表里没有能存它的列 -----------------------------------
    rules = (WORKER / "src/rules.js").read_text(encoding="utf-8")
    index = (WORKER / "src/index.js").read_text(encoding="utf-8")
    schema = (WORKER / "schema.sql").read_text(encoding="utf-8")
    require_text(index, "await anonId(secret, installId)", "analytics-worker",
                 "落库前必须 HMAC")
    require("installId" not in schema.lower().replace("install id", ""),
            "analytics-worker: 表结构里不得出现 installId 列")
    require_text(schema, "PRIMARY KEY (day, anon_id)", "analytics-worker",
                 "一天一次由联合主键保证")
    require_text(index, "INSERT_SQL", "analytics-worker", "去重靠 INSERT OR IGNORE 那条语句")
    require_text(rules, "INSERT OR IGNORE INTO daily_active", "analytics-worker",
                 "去重语句只能是 INSERT OR IGNORE")
    require_text(rules, "crypto.subtle.importKey", "analytics-worker", "HMAC 由平台实现，不自己写")
    require("console.log(installId" not in index and "console.error(installId" not in index,
            "analytics-worker: 日志里不得打印 installId")
    require_text(index, "return json({ ok: false, error: 'service unavailable' }, 503)",
                 "analytics-worker", "密钥缺失必须失败关闭")
    require_text(rules, "oldestKeptDay", "analytics-worker", "保留期只有一处算法")
    require("wrangler secret put ANALYTICS_HMAC_SECRET" in (WORKER / "README.md").read_text(encoding="utf-8"),
            "analytics-worker/README: 必须写清密钥用 secret 上传")
    # database_id 只允许两种状态：还没建库（占位值）或真的建过（UUID）。
    # 填了真值就必须在文档里写清「已部署但未启用」，否则下一个人分不清是部署过还是编的。
    toml_text = (WORKER / "wrangler.toml").read_text(encoding="utf-8")
    found = re.search(r'^database_id\s*=\s*"([^"]*)"', toml_text, re.M)
    require(found is not None, "analytics-worker/wrangler.toml: 必须有 database_id")
    if found:
        value = found.group(1)
        is_placeholder = value == "REPLACE_WITH_D1_DATABASE_ID"
        is_uuid = re.fullmatch(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", value) is not None
        require(is_placeholder or is_uuid,
                "analytics-worker/wrangler.toml: database_id 只能是占位值或真实 UUID，当前 %r" % value)
        if is_uuid:
            state_doc = (ROOT / "docs/analytics.md").read_text(encoding="utf-8")
            require("当前状态：已实现，未启用" in state_doc,
                    "wrangler.toml 填了真实 database_id，docs/analytics.md 必须写明当前状态")
            require("workers.dev" in state_doc and "SNI" in state_doc,
                    "docs/analytics.md: 已部署但不可达时，必须记下 workers.dev 被按 SNI 封锁这条事实")

    # --- 10. 跑服务端自己的测试（不需要 Cloudflare 账号、不联网） -----------------------
    node = shutil.which("node")
    require(node is not None, "需要 node 才能跑 server/analytics-worker 的测试")
    if node:
        result = subprocess.run([node, "--test"], cwd=WORKER, capture_output=True, text=True,
                                errors="replace")
        if result.returncode != 0:
            failures.append("analytics-worker: node --test 失败\n" + (result.stdout or "")[-1500:]
                            + (result.stderr or "")[-1500:])
        else:
            tail = [line for line in (result.stdout or "").splitlines() if line.startswith("# pass")]
            print("PASS: analytics-worker tests %s" % (tail[-1].strip() if tail else "ok"))

    report()


def report():
    if failures:
        print("FAIL: %d of %d analytics checks" % (len(failures), checks))
        for failure in failures:
            print("  - " + failure)
        raise SystemExit(1)
    print("PASS: %d analytics privacy checks (payload, consent, trigger, server storage, worker tests)"
          % checks)


if __name__ == "__main__":
    main()
