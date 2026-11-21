# -*- coding: utf-8 -*-
# SPDX-License-Identifier: MPL-2.0
"""解码浴室设备二维码，并按 DeviceQr 规则离线解析。只读，不改图片。"""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
import zxingcpp
from PIL import Image

IMG = r"C:\Users\SunHeyue\Desktop\BEF3E312BE3468D29E33C492FB2B1517.jpg"

img = Image.open(IMG).convert("L")
print(f"image size: {img.size[0]}x{img.size[1]}")

results = []
for attempt, im in enumerate([
    img,
    img.resize((img.width * 2, img.height * 2), Image.LANCZOS),
]):
    res = zxingcpp.read_barcodes(im)
    if res:
        results = res
        print(f"decoded on attempt {attempt + 1}")
        break

if not results:
    print("NO_BARCODE_FOUND")
    sys.exit(1)

raw = results[0].text
fmt = results[0].format
print(f"format: {fmt}")
print(f"raw length: {len(raw)}")
print("---RAW START---")
print(raw)
print("---RAW END---")

# 按 DeviceQr.parse 逻辑离线解析（不联网）
fields = raw.split(",")
print()
print("=== DeviceQr 离线解析 ===")
print(f"字段数: {len(fields)}")
for i, f in enumerate(fields[:8]):
    print(f"  [{i}] {f!r}"[:120])

if fields[0].startswith("KLCXKJ-Recharge"):
    print(">> 这是充值码（KLCXKJ-Recharge），不是设备码")
elif fields[0].startswith("KLCXKJ-Water") or fields[0].startswith("https://qzxy.ink/"):
    addr = fields[2].upper() if len(fields) >= 3 else ""
    import re
    if re.fullmatch(r"[0-9A-F]{12}|(?:[0-9A-F]{2}:){5}[0-9A-F]{2}", addr):
        compact = addr.replace(":", "")
        bt = ":".join(compact[i:i+2] for i in range(0, 12, 2))
        query = "00" + compact[2:] if compact.startswith("C0") else compact
        ble = not compact.startswith("00")
        print(f">> 设备类型: {fields[1]}")
        print(f">> 蓝牙地址: {bt}")
        print(f">> 查询地址: {query}")
        print(f">> 传输提示(BLE?): {ble}")
    else:
        print(f">> 地址字段不匹配预期格式: {addr!r}")
else:
    print(">> 未知前缀，客户端暂不支持")
