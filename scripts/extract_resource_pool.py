"""Extract the global string pool without decoding individual Android resources."""

import argparse
import json
import struct
import zipfile
from pathlib import Path


def strings(data: bytes) -> list[str]:
    if struct.unpack_from("<H", data)[0] != 2:
        raise ValueError("Not an Android resource table")
    offset = struct.unpack_from("<H", data, 2)[0]
    kind, header, size = struct.unpack_from("<HHI", data, offset)
    if kind != 1 or offset + size > len(data) or header < 28:
        raise ValueError("Invalid global string pool")
    count, _, flags, start = struct.unpack_from("<IIII", data, offset + 8)
    if header + count * 4 > size:
        raise ValueError("Invalid string offsets")
    utf8 = bool(flags & 0x100)

    def length(pos: int, wide: bool) -> tuple[int, int]:
        width = 2 if wide else 1
        code = "<H" if wide else "<B"
        mask = 0x8000 if wide else 0x80
        shift = 16 if wide else 8
        first = struct.unpack_from(code, data, pos)[0]
        pos += width
        if first & mask:
            first = ((first & (mask - 1)) << shift) | struct.unpack_from(code, data, pos)[0]
            pos += width
        return first, pos

    result = []
    for index in range(count):
        relative = struct.unpack_from("<I", data, offset + header + index * 4)[0]
        pos = offset + start + relative
        if not offset <= pos < offset + size:
            raise ValueError("String outside pool")
        n, pos = length(pos, not utf8)
        if utf8:
            n, pos = length(pos, False)
        else:
            n *= 2
        if pos + n > offset + size:
            raise ValueError("String exceeds pool")
        result.append(data[pos:pos + n].decode("utf-8" if utf8 else "utf-16-le"))
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as apk:
        extracted = strings(apk.read("resources.arsc"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(extracted, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Extracted {len(extracted)} resource strings")
