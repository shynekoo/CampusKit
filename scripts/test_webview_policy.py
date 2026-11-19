"""Guard WebView navigation admission so plaintext cannot slip through one page.

Run with: python scripts/test_webview_policy.py

Every in-app WebView that intercepts navigation must gate its "load here" decision on an
HTTPS check, not merely a host suffix. The manifest's usesCleartextTraffic=false is a backstop,
not the policy: a login page that only checks the host is one refactor away from loading over
HTTP, and the school-domain check itself can be tricked by a bare-host test. The shared
TrustedUrlPolicy (core) already enforces scheme + host + port; this guard catches a page that
silently stops using it.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WEBVIEW_DIR = ROOT / "client/android/src/io/qzxy/lite/preview"


def main():
    pages = sorted(WEBVIEW_DIR.glob("*Activity.java"))
    checked = 0
    for page in pages:
        source = page.read_text(encoding="utf-8")
        if "shouldOverrideUrlLoading" not in source:
            continue
        checked += 1
        # "Load here" must be decided by HTTPS-aware logic: the shared policy, or an explicit
        # scheme check. A host-only check (host.equals / host.endsWith with no scheme) fails.
        https_aware = ("allowsInApp(" in source) or ('"https".equals' in source)
        if not https_aware:
            raise AssertionError(
                "%s intercepts navigation without an HTTPS check in its admission rule"
                % page.name)
        print("PASS: %s gates navigation on HTTPS" % page.name)
    if checked < 5:
        raise AssertionError("expected at least 5 WebView navigation guards, found %d" % checked)
    print("PASS: all %d WebView navigation guards are HTTPS-aware" % checked)


if __name__ == "__main__":
    main()
