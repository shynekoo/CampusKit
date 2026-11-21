# SPDX-License-Identifier: MPL-2.0
"""Read DEX objects from the explicitly authorized, account-free MuMu lab only."""
import argparse
import hashlib
import json
import re
import shlex
import struct
import subprocess
import time
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANAGER = Path('D:/Program Files/Netease/MuMu/nx_main/MuMuManager.exe')
PACKAGE = 'com.klcxkj.zqxy'
SERIAL = '127.0.0.1:16544'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pid', required=True, type=int)
    args = parser.parse_args()
    info = json.loads(subprocess.check_output([str(MANAGER), 'info', '--vmindex', '5'], timeout=15))
    if info.get('name') != 'QZXY-Login-Lab' or info.get('adb_port') != 16544:
        raise SystemExit('Authorized lab identity does not match')
    adb = [str(ROOT / 'tools/platform-tools/adb.exe'), '-s', SERIAL]
    def root(command):
        return subprocess.check_output(adb + ['exec-out', 'su -c ' + shlex.quote(command)], timeout=30)
    if root(f'cat /proc/{args.pid}/cmdline').split(b'\0')[0] != PACKAGE.encode():
        raise SystemExit('PID is not the original app in the lab')
    maps = root(f'cat /proc/{args.pid}/maps').decode()
    candidates = []
    for line in maps.splitlines():
        fields = line.split(None, 5)
        start, end = [int(x, 16) for x in fields[0].split('-')]
        name = fields[5] if len(fields) > 5 else ''
        if not fields[1].startswith('r') or not 262144 <= end-start <= 64*1024*1024:
            continue
        if not (not name or name.startswith('[anon:Mem_') or name.endswith('/base.vdex')):
            continue
        candidates.append((start, end-start))
    candidates.sort(key=lambda row: row[1], reverse=True)
    out = ROOT / 'analysis/private/lab-dex'
    out.mkdir(parents=True, exist_ok=True)
    results, seen, total = [], set(), 0
    deadline = time.monotonic() + 240
    for address, size in candidates:
        if total + size > 512*1024*1024 or time.monotonic() > deadline:
            break
        total += size
        data = root(f'dd if=/proc/{args.pid}/mem bs=4096 skip={address//4096} count={size//4096} 2>/dev/null')
        restored_prefix = bool(re.match(rb'0[0-9][0-9]\x00', data[:4]) and data[32:40] == b'\x70\x00\x00\x00\x78\x56\x34\x12')
        # This APK's observed in-memory objects omit the first four magic bytes;
        # the version and remaining header fields retain standard DEX offsets minus 4.
        if restored_prefix:
            data = b'dex\n' + data
        positions = {m.start() for m in re.finditer(rb'dex\n0[0-9][0-9]\x00', data)}
        positions |= {m.start()-36 for m in re.finditer(b'\x70\x00\x00\x00\x78\x56\x34\x12', data) if m.start() >= 36}
        for pos in sorted(positions):
            if pos+112 > len(data): continue
            length, header, endian = struct.unpack_from('<III', data, pos+32)
            if header != 112 or endian != 0x12345678 or length < 112 or pos+length > len(data): continue
            valid = True
            for off, width in [(56,4),(64,4),(72,12),(80,8),(88,8),(96,32)]:
                count, offset = struct.unpack_from('<II', data, pos+off)
                if count and (offset < 112 or offset+count*width > length): valid = False
            if not valid: continue
            dex = data[pos:pos+length]
            if b'Lcom/klcxkj/' not in dex: continue
            digest = hashlib.sha256(dex).hexdigest()
            if digest in seen: continue
            seen.add(digest)
            (out / (digest+'.raw.dex')).write_bytes(dex)
            repaired = bytearray(dex)
            if not repaired.startswith(b'dex\n'): repaired[:8] = b'dex\n035\x00'
            repaired[12:32] = hashlib.sha1(repaired[32:]).digest()
            struct.pack_into('<I', repaired, 8, zlib.adler32(repaired[12:]) & 0xffffffff)
            (out / (digest+'.dex')).write_bytes(repaired)
            result = {'sha256_raw':digest, 'bytes':length, 'address':hex(address+pos),
                      'restored_four_byte_magic':restored_prefix,
                      'login_activity_string': b'LoginCodeActivity;' in dex}
            results.append(result)
            print(json.dumps(result), flush=True)
        if size >= 4*1024*1024:
            print(json.dumps({'scanned_mb':total//1048576, 'business_dex':len(results)}), flush=True)
    (out/'inventory.json').write_text(json.dumps({'serial':SERIAL,'pid':args.pid,'scanned_bytes':total,'dex':results},indent=2))

if __name__ == '__main__': main()
