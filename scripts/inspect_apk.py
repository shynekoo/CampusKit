# SPDX-License-Identifier: MPL-2.0
"""Read-only APK inventory. Does not execute code from the APK."""

import argparse
import hashlib
import json
import struct
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path


def inspect(path: Path) -> dict:
    with path.open("rb") as apk_file:
        digest = hashlib.file_digest(apk_file, "sha256").hexdigest()
    result = {
        "filename": path.name,
        "size_bytes": path.stat().st_size,
        "sha256": digest,
    }
    with zipfile.ZipFile(path) as apk:
        entries = apk.infolist()
        names = {entry.filename for entry in entries}
        result["zip_entry_count"] = len(entries)
        result["duplicate_entry_count"] = len(entries) - len(names)
        result["manifest_present"] = "AndroidManifest.xml" in names
        result["native_abis"] = sorted({n.split("/")[1] for n in names if n.startswith("lib/")})
        result["native_library_count"] = sum(n.startswith("lib/") and n.endswith(".so") for n in names)
        result["packing_indicators"] = sorted(n for n in names if n in {
            "assets/nedata.db", "assets/nedig.properties", "assets/nsecmetadata.a"
        } or n.endswith(("/libnesec.so", "/libnshelper.so")))
        result["dex_files"] = []
        for name in sorted(names):
            if not name.endswith(".dex"):
                continue
            data = apk.read(name)
            item = {"name": name, "size_bytes": len(data), "magic": data[:8].hex()}
            if len(data) >= 112 and data.startswith(b"dex\n"):
                for field, offset in [("declared_size", 32), ("string_count", 56),
                                      ("type_count", 64), ("method_count", 88),
                                      ("class_count", 96)]:
                    item[field] = struct.unpack_from("<I", data, offset)[0]
            result["dex_files"].append(item)
        result["signature_files"] = sorted(n for n in names if n.startswith("META-INF/")
                                           and n.endswith((".RSA", ".DSA", ".EC")))
    return result


def inspect_manifest(path: Path) -> dict:
    android = "{http://schemas.android.com/apk/res/android}"
    root = ET.parse(path).getroot()
    app = root.find("application")
    sdk = root.find("uses-sdk")
    package = root.get("package")
    activities = []
    if app is not None:
        for activity in app.findall("activity"):
            name = activity.get(android + "name", "")
            if name.startswith(package + "."):
                activities.append({"name": name, "exported": activity.get(android + "exported")})
    return {
        "package": package,
        "version_name": root.get(android + "versionName"),
        "version_code": root.get(android + "versionCode"),
        "min_sdk": sdk.get(android + "minSdkVersion") if sdk is not None else None,
        "target_sdk": sdk.get(android + "targetSdkVersion") if sdk is not None else None,
        "application_class": app.get(android + "name") if app is not None else None,
        "permissions": [p.get(android + "name") for p in root.findall("uses-permission")],
        "app_activities": activities,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, help="Manifest XML already decoded by JADX")
    args = parser.parse_args()
    inventory = inspect(args.apk)
    if args.manifest:
        inventory["manifest"] = inspect_manifest(args.manifest)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(inventory, ensure_ascii=False, indent=2))
