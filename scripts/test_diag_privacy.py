# SPDX-License-Identifier: MPL-2.0
"""Guard the diagnostics privacy boundary.

Run with: python scripts/test_diag_privacy.py

The functional side of redaction is asserted in CoreTests (values, stacks, field shapes).
This script guards the structural side, which is where a leak would actually be introduced:

1. every field key used at a call site is in DiagRedact's whitelist - an unknown key is silently
   dropped at runtime, and a new free-text key must not be added without review;
2. every module / event code / reason code used at a call site has a catalog entry, so the page
   never shows a raw identifier;
3. the old free-text door (step/raw), the SDK payload hook and the hand-rolled mask helpers are
   gone - if one comes back, this fails;
4. the log page exports UTF-8 through the system document picker and does not ask for storage.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREVIEW = ROOT / "client/android/src/io/qzxy/lite/preview"
CORE = ROOT / "client/core/src/io/qzxy/lite/core"

failures = []
checks = 0


def require(condition, message):
    global checks
    checks += 1
    if not condition:
        failures.append(message)


def block(text, start, end):
    at = text.find(start)
    if at < 0:
        return ""
    stop = text.find(end, at + 1)
    return text[at:stop if stop > 0 else len(text)]


def split_args(text):
    """Split a call's argument list on top-level commas, ignoring commas inside strings."""
    args, current, depth, quote, i = [], [], 0, None, 0
    while i < len(text):
        ch = text[i]
        if quote:
            current.append(ch)
            if ch == "\\":
                if i + 1 < len(text):
                    current.append(text[i + 1])
                    i += 2
                    continue
            elif ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
            current.append(ch)
        elif ch in "([{":
            depth += 1
            current.append(ch)
        elif ch in ")]}":
            depth -= 1
            current.append(ch)
        elif ch == "," and depth == 0:
            args.append("".join(current).strip())
            current = []
        else:
            current.append(ch)
        i += 1
    if current:
        args.append("".join(current).strip())
    return args


def calls(text, method):
    pattern = re.compile(r"\bDiag\." + method + r"\s*\(")
    for found in pattern.finditer(text):
        start = found.end() - 1
        depth, quote, i = 0, None, start
        while i < len(text):
            ch = text[i]
            if quote:
                if ch == "\\":
                    i += 2
                    continue
                if ch == quote:
                    quote = None
            elif ch in "\"'":
                quote = ch
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    yield text[start + 1:i]
                    break
            i += 1


def literal(value):
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return None


def main():
    redact = (CORE / "DiagRedact.java").read_text(encoding="utf-8")
    catalog = (CORE / "DiagCatalog.java").read_text(encoding="utf-8")

    # trace is not a field: Diag pulls it out of the pairs and prints it in the fixed position.
    allowed_fields = {"trace"}
    for marker in ("TOKENS = set(", "NUMBERS = set(", "ROUTES = set(", "DEVICES = set("):
        chunk = block(redact, marker, ";")
        allowed_fields.update(re.findall(r'"([a-z_]+)"', chunk))
    require("reason" in allowed_fields and "duration" in allowed_fields,
            "field whitelist could not be read from DiagRedact")
    require("message" not in allowed_fields and "body" not in allowed_fields
            and "text" not in allowed_fields,
            "a free-text field must never be whitelisted")

    module_block = block(catalog, "public static String module", "public static final String[] MODULES")
    modules = set(re.findall(r'case "([A-Z_]+)":', module_block))
    modules.update(re.findall(r'"([A-Z_]+)"', block(catalog, "MODULES = {", "};")))
    title_block = block(catalog, "public static String title", "public static String reason")
    codes = set(re.findall(r'case "([a-z0-9_.]+)":', title_block))
    reason_block = block(catalog, "public static String reason(String code)", "public static String summary")
    reasons = set(re.findall(r'case "([A-Z_]+)":', reason_block))

    for path in sorted(PREVIEW.glob("*.java")):
        if path.name in ("Diag.java", "DiagFiles.java"):
            continue
        source = path.read_text(encoding="utf-8")
        for raw in calls(source, "event"):
            args = split_args(raw)
            if len(args) < 3:
                failures.append("%s: Diag.event with fewer than 3 arguments" % path.name)
                continue
            module, code = literal(args[1]), literal(args[2])
            require(module in modules, "%s: unknown module %r" % (path.name, module))
            if code is None:
                # A ternary (`result<0 ? "login.failed" : "request.end"`) is still checkable:
                # every code it can produce must be in the catalog.
                for alternative in re.findall(r'"([a-z0-9_.]+)"', args[2]):
                    require(alternative in codes,
                            "%s: event code %r has no catalog title" % (path.name, alternative))
            else:
                require(code in codes, "%s: event code %r has no catalog title" % (path.name, code))
            for index in range(3, len(args) - 1, 2):
                key = literal(args[index])
                if key is None:
                    failures.append("%s: field key is not a literal, cannot verify" % path.name)
                    continue
                require(key in allowed_fields,
                        "%s: field %r is not whitelisted (it would be dropped silently)" % (path.name, key))
        for raw in calls(source, "failure"):
            args = split_args(raw)
            if len(args) < 3:
                failures.append("%s: Diag.failure with fewer than 3 arguments" % path.name)
                continue
            module, code, reason = literal(args[0]), literal(args[1]), literal(args[2])
            require(module in modules, "%s: unknown module %r" % (path.name, module))
            require(code in codes, "%s: event code %r has no catalog title" % (path.name, code))
            if reason is not None:
                require(reason in reasons or reason == "UNKNOWN",
                        "%s: reason %r has no catalog text" % (path.name, reason))

    # The removed doors must stay removed: free-form lines, raw SDK payloads, hand-rolled masks.
    for path in list(PREVIEW.glob("*.java")):
        source = path.read_text(encoding="utf-8")
        for banned in ("Diag.step(", "Diag.raw(", "maskChain(", "maskSdk(", "setupLogCallback"):
            require(banned not in source, "%s: removed logging door %r is back" % (path.name, banned))
    require("maskCode(" not in (PREVIEW / "Diag.java").read_text(encoding="utf-8"),
            "Diag: the auth-code prefix mask is back; values are whitelisted instead")

    # The export path must be UTF-8 through the document picker, with no storage permission.
    page = (PREVIEW / "DiagActivity.java").read_text(encoding="utf-8")
    require("ACTION_CREATE_DOCUMENT" in page, "DiagActivity: export must use the system file creator")
    require("StandardCharsets.UTF_8" in page, "DiagActivity: export must be UTF-8")
    require("WRITE_EXTERNAL_STORAGE" not in page, "DiagActivity must not ask for storage access")
    report = (PREVIEW / "DiagReportActivity.java").read_text(encoding="utf-8")
    require("ACTION_CREATE_DOCUMENT" in report and "StandardCharsets.UTF_8" in report,
            "DiagReportActivity: report export must be UTF-8 through the system file creator")

    manifest = (ROOT / "client/android/AndroidManifest.xml").read_text(encoding="utf-8")
    for permission in re.findall(r'uses-permission[^>]*android:name="([^"]+)"', manifest):
        require("STORAGE" not in permission, "Manifest: diagnostics must not add %s" % permission)

    if failures:
        print("FAIL: %d of %d diagnostics privacy checks" % (len(failures), checks))
        for failure in failures:
            print("  - " + failure)
        raise SystemExit(1)
    print("PASS: %d diagnostics privacy checks (fields, catalogs, removed doors, export path)" % checks)


if __name__ == "__main__":
    main()
