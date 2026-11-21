# SPDX-License-Identifier: MPL-2.0
"""Fetch a minimal Android build toolchain from Google's SDK repository."""

import hashlib
import json
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / "tools/android-sdk"
BASE = "https://dl.google.com/android/repository/"
PACKAGES = {"platforms;android-36": SDK / "platforms/android-36",
            "build-tools;36.0.0": SDK / "build-tools/36.0.0"}


def main():
    with urllib.request.urlopen(BASE + "repository2-3.xml", timeout=40) as response:
        repository = ET.fromstring(response.read())
    manifest = []
    for package_name, destination in PACKAGES.items():
        package = next(p for p in repository if p.tag.endswith("remotePackage") and p.get("path") == package_name)
        candidates = package.findall("archives/archive")
        archive = next(a for a in candidates if a.findtext("host-os") in (None, "windows"))
        complete = archive.find("complete")
        url = BASE + complete.findtext("url")
        checksum = complete.find("checksum")
        algorithm = checksum.get("type", "sha1")
        expected = checksum.text.strip().lower()
        if algorithm not in ("sha1", "sha256"):
            raise ValueError("Unsupported checksum algorithm")
        entry = {"package": package_name, "url": url, "checksum_type": algorithm, "checksum": expected}
        manifest.append(entry)
        marker = destination / ".verified-source.json"
        if marker.exists() and json.loads(marker.read_text()) == entry:
            print("Already installed:", package_name, flush=True)
            continue
        destination.mkdir(parents=True, exist_ok=True)
        download = SDK / (package_name.replace(";", "-") + ".zip")
        print("Downloading:", package_name, flush=True)
        with urllib.request.urlopen(url, timeout=40) as response, download.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        with download.open("rb") as source:
            actual = hashlib.file_digest(source, algorithm).hexdigest()
        if actual != expected:
            raise ValueError("SDK archive checksum mismatch: " + package_name)
        with zipfile.ZipFile(download) as bundle:
            for member in bundle.infolist():
                parts = member.filename.replace("\\", "/").split("/")[1:]
                if not parts or not any(parts):
                    continue
                target = destination.joinpath(*parts).resolve()
                if not target.is_relative_to(destination.resolve()):
                    raise ValueError("Unsafe SDK archive path")
                if member.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with bundle.open(member) as source, target.open("wb") as output:
                        while chunk := source.read(1024 * 1024):
                            output.write(chunk)
        marker.write_text(json.dumps(entry, indent=2) + "\n", encoding="utf-8")
        download.unlink()
        print("Installed and checksum verified:", package_name, flush=True)
    (SDK / "sources.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
