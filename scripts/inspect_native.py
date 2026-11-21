# SPDX-License-Identifier: MPL-2.0
"""Disassemble named ARM64 JNI functions without executing the APK."""

import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/pythonlib"))
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM
from elftools.elf.elffile import ELFFile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--symbol", action="append", required=True)
    args = parser.parse_args()
    with zipfile.ZipFile(ROOT / "inputs/qzxy-original.apk") as apk:
        payload = apk.read("lib/arm64-v8a/libklcxkjencry.so")
    elf = ELFFile(io.BytesIO(payload))
    if elf.header.e_machine != "EM_AARCH64":
        raise SystemExit("Expected ARM64 ELF")
    all_symbols = {}
    for name in (".dynsym", ".symtab"):
        section = elf.get_section_by_name(name)
        if section is not None:
            for symbol in section.iter_symbols():
                if symbol.name and symbol["st_value"]:
                    all_symbols[symbol.name] = symbol
    by_address = {symbol["st_value"]: name for name, symbol in all_symbols.items()}
    plt = elf.get_section_by_name(".plt")
    relocations = elf.get_section_by_name(".rela.plt")
    if plt is not None and relocations is not None:
        dynamic_symbols = elf.get_section(relocations["sh_link"])
        for index, relocation in enumerate(relocations.iter_relocations()):
            imported = dynamic_symbols.get_symbol(relocation["r_info_sym"]).name
            by_address[plt["sh_addr"] + 32 + index * 16] = imported + "@plt"
    disasm = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
    out = ROOT / "analysis/private/native"
    out.mkdir(parents=True, exist_ok=True)
    summaries = []
    for name in args.symbol:
        symbol = all_symbols.get(name)
        if symbol is None:
            raise SystemExit("Symbol not found: " + name)
        section = elf.get_section(symbol["st_shndx"])
        start = symbol["st_value"] - section["sh_addr"]
        body = section.data()[start:start + symbol["st_size"]]
        lines = []
        for instruction in disasm.disasm(body, symbol["st_value"]):
            comment = ""
            if instruction.mnemonic in ("bl", "b") and instruction.op_str.startswith("#0x"):
                target = int(instruction.op_str[1:], 16)
                comment = by_address.get(target, "")
            lines.append(f"{instruction.address:08x}  {instruction.mnemonic:8} {instruction.op_str:40} {comment}")
        (out / (name + ".asm")).write_text("\n".join(lines) + "\n", encoding="utf-8")
        summaries.append({"symbol": name, "size": symbol["st_size"], "instructions": len(lines)})
    (out / "symbols.json").write_text(json.dumps({name: {"address": s["st_value"], "size": s["st_size"]}
                                                for name, s in all_symbols.items()}, indent=2), encoding="utf-8")
    print(json.dumps(summaries, indent=2))


if __name__ == "__main__":
    main()
