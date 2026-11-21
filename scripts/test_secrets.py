# SPDX-License-Identifier: MPL-2.0
"""公开仓库敏感信息守卫。

针对「受 Git 跟踪的文件」（或 `--staged` 时的暂存区）做五类检查：

1. **已知禁用字面量**——2026-11-21 审计里删除的趣智校园请求签名盐。这里只存 SHA-256，
   不存原文：守卫脚本自己也不该成为新的泄露点。命中即失败。
2. **我方凭据形态**——私钥块、云厂商 AccessKey、平台令牌、以及被删除盐值的形态特征。
   命中即失败。第三方客户端的固定常量（慧生活授权串）不在此列，只告警，理由见
   `docs/security.md`「已知残留风险 R1」。
3. **被跟踪但命中 .gitignore 的文件**——忽略规则对已跟踪文件无效，这是历史上 APK 与
   分析文档被带进仓库的直接原因。非空即失败。
4. **材料本身不该入库**——实验产物与签名材料（APK / dex / 抓包 / 日志 / 密钥库）、
   本机绝对路径、代码里的插桩与反编译工具调用。这些不靠「值」识别，靠形态与路径识别。
5. **文档里的第三方接口路径**——发布说明与技术文档只写职责与约束，不抄接口路径。

允许清单 `ALLOWLIST` 用于确实必须保留的少数例外，每一条都要写清原因。

用法：

    python scripts/test_secrets.py                 # 扫工作区（默认，全部跟踪文件）
    python scripts/test_secrets.py --staged        # 只扫暂存区（提交前用）
    python scripts/test_secrets.py --apk PATH ...  # 另外扫构建产物（dex / assets / native 库）

`scripts/build_preview.py` 在打包前调用本脚本，并在打包后用它复查产物——dex、assets、资源表与
native 库都扫（不含 META-INF 签名块）。扫描不通过就不出包，且会删掉刚签好的 APK，所以 Beta
自动发布拿不到带已知盐值的包。
"""

import argparse
import hashlib
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SELF = Path(__file__).resolve()

# 只存摘要，不存原文。新增条目时把新值摘出来，加一行 sha256 即可。
BANNED_LITERALS = {
    "032605fcf24012cd08b061aa0b826e2d5435aa445e381097c5bf66697fd6f4d9":
        "趣智校园充值签名盐（短形态，2026-11-21 删除）",
    "4a0fcf73ce90335d642d01ac195db39858c1b8c6e6df645d0665b44730e9b806":
        "趣智校园充值签名盐（原生诱饵值，2026-11-21 删除）",
    "11e1e3aed324d32fa3cf152440d7a7a817139ef905588bbb5b4901b87885dc86":
        "趣智校园请求签名共享密钥（完整形态，2026-11-21 删除）",
}

# 形态规则：不需要知道具体值也能认出来。
CREDENTIAL_PATTERNS = (
    (re.compile(r"-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----"), "私钥块"),
    (re.compile(r"\bAKIA[0-9A-Z]{16}\b"), "AWS AccessKey"),
    (re.compile(r"\bLTAI[0-9A-Za-z]{12,}\b"), "阿里云 AccessKey"),
    (re.compile(r"\bsk_live_[0-9A-Za-z]{10,}\b"), "Stripe 生产密钥"),
    (re.compile(r"\bgh[pousr]_[0-9A-Za-z]{20,}\b"), "GitHub 令牌"),
    (re.compile(r"\bgithub_pat_[0-9A-Za-z_]{20,}\b"), "GitHub 细粒度令牌"),
    (re.compile(r"\bxox[baprs]-[0-9A-Za-z-]{10,}\b"), "Slack 令牌"),
    (re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"), "Google API Key"),
    # 被删除盐值的形态：&key=<词>-<32 位十六进制 / sign-<词>=
    (re.compile(r"&key=[a-z0-9-]+-[0-9a-f]{32}\b"), "请求签名共享密钥形态"),
    (re.compile(r"\bsign-[a-z]{4,8}="), "请求签名盐形态"),
)

# 第三方客户端固定常量：不阻断，只报告，提醒审阅者按 R1 的定性判断。
KNOWN_THIRD_PARTY = (
    (re.compile(r"sign_type=RSA2&target_id="), "慧生活客户端授权常量（见 security.md R1）"),
)

# ---------------------------------------------------------------- 材料形态检查

# 实验产物与签名材料：一律不入库。历史上曾有 5 个 APK 被提交，忽略规则对已跟踪文件无效，
# 所以这里按后缀再挡一道。
FORBIDDEN_SUFFIXES = (
    ".apk", ".aab", ".dex", ".jar", ".aar", ".so",
    ".har", ".pcap", ".cap", ".log",
    ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key",
)

# 没有后缀或后缀不固定的凭据文件名。
FORBIDDEN_NAMES = (".env", ".dev.vars", "id_rsa", "id_ed25519", "keystore.properties")

# 本机绝对路径：会把开发机的用户名、目录结构带进公开仓库。
LOCAL_PATH_PATTERNS = (
    (re.compile(r"[A-Za-z]:[\\/]{1,2}Users[\\/]", re.I), "Windows 用户目录绝对路径"),
    # 排除盘符路径里的 /Users/，否则同一条会被 Windows 与 macOS 各命中一次。
    (re.compile(r"(?<![\w.:/])/home/[A-Za-z0-9_.-]{2,}/"), "Linux 家目录绝对路径"),
    (re.compile(r"(?<![\w.:/])/Users/[A-Za-z0-9_.-]{2,}/"), "macOS 家目录绝对路径"),
)

# 插桩、解包、反编译工具的调用特征。只查代码与脚本——文档在叙述审计方法时可能正当地
# 提到工具名，这里不去管它。
RESEARCH_TOOL_PATTERNS = (
    (re.compile(r"\bfrida[-_]server\b", re.I), "frida-server 注入流程"),
    (re.compile(r"\bfrida\.get_usb_device\b"), "Frida 运行时插桩"),
    (re.compile(r"\bjadx\b", re.I), "JADX 反编译命令行"),
    (re.compile(r"\bapktool\b", re.I), "apktool 解包"),
    (re.compile(r"\bdexdump\b", re.I), "dexdump 解析"),
)

# 文档里的第三方接口路径：写职责与约束即可，路径以源码为准。
ENDPOINT_PATTERN = re.compile(r"/api/v\d+/")

# 单文件过大通常意味着产物或归档被带进来了。
MAX_TEXT_BYTES = 1024 * 1024

# 允许清单：{ (相对路径或目录前缀, 规则标签): 原因 }。只在确实必须保留时添加。
ALLOWLIST = {}


def _allowed(relative, label):
    for (target, tag), _reason in ALLOWLIST.items():
        if tag != label:
            continue
        if relative == target or relative.startswith(target):
            return True
    return False


TEXT_SUFFIXES = {
    ".java", ".js", ".py", ".md", ".json", ".xml", ".html", ".css", ".txt",
    ".yml", ".yaml", ".toml", ".sql", ".gradle", ".properties", ".sh", ".bat", ".ps1",
}

# 只在这些后缀里查工具调用特征，避免把叙述审计方法的文档判成工具脚本。
CODE_SUFFIXES = {".java", ".js", ".py", ".sh", ".yml", ".yaml", ".toml", ".properties",
                 ".gradle", ".bat", ".ps1"}

# 字符串字面量：把同一个文件里的字面量拼起来再匹配，能挡住 "sign-kai" + "lu=" 这种拆写。
LITERAL = re.compile(r'"((?:[^"\\\n]|\\.){4,600})"')


def tracked_files():
    try:
        out = subprocess.run(["git", "ls-files", "-z"], cwd=str(ROOT), check=True,
                             capture_output=True).stdout.decode("utf-8")
    except (OSError, subprocess.CalledProcessError) as error:
        raise SystemExit("无法读取 git 索引：%s" % error)
    return [ROOT / name for name in out.split("\0") if name]


def staged_files():
    try:
        out = subprocess.run(["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"],
                             cwd=str(ROOT), check=True,
                             capture_output=True).stdout.decode("utf-8")
    except (OSError, subprocess.CalledProcessError) as error:
        raise SystemExit("无法读取暂存区：%s" % error)
    return [ROOT / name for name in out.split("\0") if name]


def check_material(relative, path):
    """按路径与文件形态判断「这份材料该不该入库」。返回失败项列表。"""
    failures = []
    low = relative.lower()
    suffix = path.suffix.lower()
    name = path.name.lower()
    if suffix in FORBIDDEN_SUFFIXES:
        if not _allowed(relative, "实验产物或签名材料"):
            failures.append("%s：实验产物或签名材料不入库（后缀 %s）" % (relative, suffix))
    if name in FORBIDDEN_NAMES:
        if not _allowed(relative, "凭据文件"):
            failures.append("%s：凭据文件不入库" % relative)
    try:
        size = path.stat().st_size
    except OSError:
        size = 0
    if size > MAX_TEXT_BYTES and suffix in TEXT_SUFFIXES:
        failures.append("%s：单文件超过 1 MiB（%d 字节），多半是产物或归档误入"
                        % (relative, size))
    return failures


def scan_text(path, relative):
    """返回 (failures, warnings)。"""
    failures, warnings = [], []
    try:
        text = path.read_text(encoding="utf-8", errors="strict")
    except (UnicodeDecodeError, OSError):
        return failures, warnings

    literal_hashes = set()
    joined = []
    for match in LITERAL.finditer(text):
        body = match.group(1)
        joined.append(body)
        literal_hashes.add(hashlib.sha256(body.encode("utf-8")).hexdigest())

    for digest, label in BANNED_LITERALS.items():
        if digest in literal_hashes:
            failures.append("%s：出现已知禁用字面量「%s」" % (relative, label))

    haystacks = (text, "".join(joined))
    for pattern, label in CREDENTIAL_PATTERNS:
        if any(pattern.search(hay) for hay in haystacks):
            failures.append("%s：疑似%s" % (relative, label))

    for pattern, label in KNOWN_THIRD_PARTY:
        if any(pattern.search(hay) for hay in haystacks):
            warnings.append("%s：含%s（已知并接受，见 security.md R1）" % (relative, label))

    for pattern, label in LOCAL_PATH_PATTERNS:
        if pattern.search(text) and not _allowed(relative, label):
            failures.append("%s：含%s，公开仓库只写相对路径" % (relative, label))

    if path.suffix.lower() in CODE_SUFFIXES:
        for pattern, label in RESEARCH_TOOL_PATTERNS:
            if pattern.search(text) and not _allowed(relative, label):
                failures.append("%s：出现%s，调试工具不随仓库分发" % (relative, label))

    if path.suffix.lower() == ".md" and ENDPOINT_PATTERN.search(text):
        if not _allowed(relative, "第三方接口路径"):
            failures.append("%s：文档含第三方接口路径（写职责与约束即可，路径以源码为准）"
                            % relative)
    return failures, warnings


def scan_apk(path):
    """扫构建产物：dex、assets、资源表与 native 库都不允许带回已删除的盐或我方凭据。

    dex 的字符串池是「长度前缀 + 原始字节」，所以按形态匹配（不依赖具体值）比按摘要匹配可靠：
    摘要只能命中完整独立字符串，而池里的字符串常常与相邻常量黏在一起。

    assets 与 `.so` 是同样的问题：把盐从 Java 挪进 assets、或塞进本地库，源码扫描看不见。
    这里对每个条目按 latin-1 与 UTF-16LE 两种视图各跑一遍形态规则（native 代码里的宽字符常量
    很常见），再对二进制里的可打印串做摘要比对——脚本本身仍然不持有原文。
    """
    failures = []
    if not path.exists():
        return ["产物不存在：%s" % path]
    with zipfile.ZipFile(path) as package:
        for name in package.namelist():
            if name.endswith("/"):
                continue
            raw = package.read(name)
            if name.endswith(".dex"):
                text = raw.decode("latin-1")
                for pattern, label in CREDENTIAL_PATTERNS:
                    if pattern.search(text):
                        failures.append("%s!%s：疑似%s" % (path.name, name, label))
                # 形态规则挡不住「盐值被拼进更长的常量」这种写法，摘要再兜一层。
                for match in re.finditer(rb"[\x20-\x7e]{4,400}", raw):
                    if hashlib.sha256(match.group(0)).hexdigest() in BANNED_LITERALS:
                        failures.append("%s!%s：出现已知禁用字面量（与应用内容一致）" % (path.name, name))
                        break
                continue
            # 非 dex：签名块（META-INF 下的 .SF/.RSA）是 Base64 的证书与摘要，不含源码常量。
            if name.startswith("META-INF/") and name.upper().endswith((".SF", ".RSA", ".DSA", ".EC")):
                continue
            views = (("latin-1", raw.decode("latin-1")),
                     ("utf-16le", raw.decode("utf-16-le", errors="ignore")))
            for encoding, text in views:
                for pattern, label in CREDENTIAL_PATTERNS:
                    if pattern.search(text):
                        failures.append("%s!%s[%s]：疑似%s" % (path.name, name, encoding, label))
            for match in re.finditer(rb"[\x20-\x7e]{4,400}", raw):
                if hashlib.sha256(match.group(0)).hexdigest() in BANNED_LITERALS:
                    failures.append("%s!%s：出现已知禁用字面量（与应用内容一致）" % (path.name, name))
                    break
    return failures


def tracked_but_ignored():
    out = subprocess.run(["git", "ls-files", "-ci", "--exclude-standard"],
                         cwd=str(ROOT), capture_output=True).stdout.decode("utf-8")
    return [line for line in out.splitlines() if line.strip()]


def ignored_among(paths):
    """暂存区模式下，逐个确认待提交文件没有命中忽略规则。"""
    names = [p.relative_to(ROOT).as_posix() for p in paths if p.exists()]
    if not names:
        return []
    out = subprocess.run(["git", "check-ignore", "--no-index", "-z", "--stdin"],
                         cwd=str(ROOT), input="\0".join(names).encode("utf-8"),
                         capture_output=True)
    return [n for n in out.stdout.decode("utf-8").split("\0") if n.strip()]


def main(argv=None):
    parser = argparse.ArgumentParser(description="公开仓库敏感信息守卫")
    parser.add_argument("--apk", action="append", default=[],
                        help="额外扫描的构建产物（可重复）")
    parser.add_argument("--staged", action="store_true",
                        help="只扫暂存区（提交前用）")
    parser.add_argument("--quiet", action="store_true", help="只输出失败项")
    args = parser.parse_args(argv)

    files = staged_files() if args.staged else tracked_files()
    failures, warnings, scanned = [], [], 0
    for path in files:
        if path.resolve() == SELF or not path.is_file():
            continue
        relative = path.relative_to(ROOT).as_posix()
        failures += check_material(relative, path)
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        scanned += 1
        found, noted = scan_text(path, relative)
        failures += found
        warnings += noted

    ignored = ignored_among(files) if args.staged else tracked_but_ignored()
    for name in ignored:
        failures.append("%s：被 Git 跟踪但同时命中 .gitignore（忽略规则对它无效）" % name)

    for name in args.apk:
        failures += scan_apk(Path(name))

    if not args.quiet:
        for line in warnings:
            print("NOTE: " + line)
    if failures:
        for line in failures:
            print("FAIL: " + line)
        print("%d 项敏感信息检查未通过（文本 %d 个文件、产物 %d 个）。"
              % (len(failures), scanned, len(args.apk)))
        return 1

    print("PASS: 敏感信息守卫（%s；文本 %d 个文件 / 被跟踪但忽略 %d 个 / 产物 %d 个；已知第三方常量 %d 处）"
          % ("暂存区" if args.staged else "工作区",
             scanned, len(ignored), len(args.apk), len(warnings)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
