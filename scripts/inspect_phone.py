"""Capture only the foreground QZXY app for local interoperability research."""

import argparse
import json
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.klcxkj.zqxy"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--name", default="home")
    parser.add_argument("--launch", action="store_true")
    parser.add_argument("--package", choices=[PACKAGE, "io.qzxy.lite.preview"], default=PACKAGE)
    args = parser.parse_args()
    package = args.package
    if not args.name.replace("-", "").replace("_", "").isalnum():
        parser.error("name must contain only letters, digits, hyphens or underscores")
    adb = [str(ROOT / "tools/platform-tools/adb.exe"), "-s", args.serial]

    def run(*command, timeout=20):
        return subprocess.check_output(adb + list(command), timeout=timeout)

    def foreground():
        output = run("shell", "dumpsys", "window").decode("utf-8", "replace")
        return any("mCurrentFocus=" in line and package + "/" in line for line in output.splitlines())

    if args.launch:
        activity = "/.ui.splash.SplashActivity" if package == PACKAGE else "/.MainActivity"
        print(run("shell", "am", "start", "-W", "-n", package + activity).decode())
        for _ in range(10):
            if foreground():
                break
            time.sleep(0.5)
    if not foreground():
        raise SystemExit("QZXY is not foreground; no screen captured")

    remote = "/data/local/tmp/qzxy-" + uuid.uuid4().hex + ".xml"
    try:
        run("shell", "uiautomator", "dump", "--compressed", remote, timeout=35)
        tree = run("exec-out", "cat", remote)
        try:
            parsed = ET.fromstring(tree)
        except ET.ParseError:
            raise SystemExit("UI dump unavailable; capture not saved")
        if parsed.tag != "hierarchy" or not any(True for _ in parsed.iter("node")):
            raise SystemExit("UI dump empty; capture not saved")
        if not foreground():
            raise SystemExit("QZXY left foreground; capture discarded")
        screenshot = run("exec-out", "screencap", "-p")
        if not foreground():
            raise SystemExit("QZXY left foreground; capture discarded")
        out = ROOT / "analysis/private/device"
        out.mkdir(parents=True, exist_ok=True)
        (out / (args.name + ".xml")).write_bytes(tree)
        (out / (args.name + ".png")).write_bytes(screenshot)
        print(json.dumps({"capture": args.name, "saved": True}))
    finally:
        subprocess.run(adb + ["shell", "rm", "-f", remote], timeout=10, capture_output=True)


if __name__ == "__main__":
    main()
