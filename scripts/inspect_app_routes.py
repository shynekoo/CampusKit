# SPDX-License-Identifier: MPL-2.0
"""Read bounded QZXY process logs; retain only operator URL paths, without queries."""
import argparse
import collections
import json
import re
import subprocess
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.klcxkj.zqxy"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    adb = [str(ROOT / "tools/platform-tools/adb.exe"), "-s", args.serial]
    processes = subprocess.check_output(adb + ["shell", "ps", "-A", "-o", "PID,NAME"], timeout=15).decode("utf-8", "replace")
    candidates = [row.split()[0] for row in processes.splitlines()
                  if len(row.split()) == 2 and row.split()[1] == PACKAGE]
    if not candidates:
        print("QZXY main process not running; no logs read")
        return
    if len(candidates) > 4:
        raise SystemExit("Unexpected number of matching app processes; no logs read")
    logs = [subprocess.check_output(adb + ["logcat", "-d", "--pid=" + pid, "-t", "750"], timeout=20).decode("utf-8", "replace")
            for pid in candidates]
    log = "\n".join(logs)
    counts = collections.Counter()
    for match in re.finditer(r'https?://[^\s<>"\x00]+', log):
        try:
            url = urllib.parse.urlsplit(match.group().rstrip("',)}]"))
            host = url.hostname or ""
            if not (host == "china-qzxy.cn" or host.endswith(".china-qzxy.cn") or
                    host == "klcxkj-qzxy.cn" or host.endswith(".klcxkj-qzxy.cn")):
                continue
            # Keep route shape only; no credentials, query parameters, or numeric identifiers.
            path = re.sub(r"/\d+(?=/|$)", "/:id", url.path)
            if re.fullmatch(r"[A-Za-z0-9_/:.\-]*", path):
                counts[host + path] += 1
        except ValueError:
            continue
    result = {"scope": "Exact QZXY process names, latest 750 records per PID", "process_count": len(candidates), "raw_logs_saved": False,
              "routes": [{"route": route, "occurrences": count} for route, count in sorted(counts.items())]}
    output = ROOT / "analysis/private/device/operator-routes.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"operator_route_count": len(counts), "raw_logs_saved": False}))


if __name__ == "__main__":
    main()
