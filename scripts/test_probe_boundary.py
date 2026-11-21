# SPDX-License-Identifier: MPL-2.0
"""把实验性协议探测挡在发布包之外。

Run with: python scripts/test_probe_boundary.py

协议探测会真的向设备发一次带控制效果的开启请求（`cnt=1`），所以它不是发布功能。
这里检查三件事，任何一件破了都直接失败：

1. 源码边界——探测类只存在于 `client/fieldtest/experimental`，正式 source root 里没有它，
   正常用水（`LifeWaterProtocol`）只走 `LifeDeviceHttp`，不依赖探测类。
2. 入口边界——正式构建用的 `LifeProbeEntry` 只有两项菜单，不含探测文案；用水页自身也没有。
3. 构建边界——`build_preview.py` 必须由 `--experimental-probe` 控制实验源集，并对产物做
   双向断言（发布包没有、实验包必须有）。这条是产物级的，静态检查只是它的补充。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIELD = ROOT / "client/fieldtest/src/io/qzxy/lite/preview"
EXPERIMENTAL = ROOT / "client/fieldtest/experimental/src/io/qzxy/lite/preview"
BUILD = ROOT / "scripts/build_preview.py"

PROBE = "LifeProtocolProbe"
ENTRY = "LifeProbeEntry"
LABEL = "协议探测"


def source(path):
    assert path.is_file(), "缺少文件：%s" % path.relative_to(ROOT)
    return path.read_text(encoding="utf-8")


def main():
    checked = 0

    # 1. 探测类不在正式源集里，只在隔离目录里。
    assert not (FIELD / (PROBE + ".java")).exists(), \
        "正式源集出现了 %s.java：它只能放在 client/fieldtest/experimental" % PROBE
    probe = source(EXPERIMENTAL / (PROBE + ".java"))
    assert "LifeDeviceHttp.get" in probe, "探测类应复用 LifeDeviceHttp，不要自带一份传输实现"
    assert "HttpURLConnection" not in probe, "探测类里不应再出现自己的 HTTP 传输代码"
    checked += 3

    # 2. 正常用水只依赖传输层，不依赖探测类。
    protocol = source(FIELD / "LifeWaterProtocol.java")
    assert PROBE not in protocol, "LifeWaterProtocol 仍引用 %s：正常用水会被探测代码绑住" % PROBE
    assert "LifeDeviceHttp.get" in protocol, "LifeWaterProtocol 应通过 LifeDeviceHttp 发请求"
    checked += 2

    transport = source(FIELD / "LifeDeviceHttp.java")
    assert PROBE not in transport, "LifeDeviceHttp 不应反向依赖探测类"
    for needle, reason in (("setInstanceFollowRedirects(false)", "不跟随重定向"),
                           ("setConnectTimeout(15000)", "连接超时"),
                           ("setReadTimeout(15000)", "读取超时"),
                           ("1024*1024", "应答上限")):
        assert needle in transport, "LifeDeviceHttp 缺少%s（%s）" % (reason, needle)
        checked += 1

    # 3. 正式入口只有两项，且用水页不含探测逻辑与文案。
    entry = source(FIELD / (ENTRY + ".java"))
    assert PROBE not in entry and LABEL not in entry, "正式版 LifeProbeEntry 不应包含探测入口"
    assert entry.count('"') == 4, "正式版 LifeProbeEntry 应只有两项菜单文案（4 个引号）"
    page = source(FIELD / "LifeWaterActivity.java")
    for needle in (PROBE, LABEL, "mode/start"):
        assert needle not in page, "LifeWaterActivity 仍包含 %s" % needle
    assert "LifeProbeEntry.items()" in page, "用水页应通过 LifeProbeEntry 取菜单项"
    checked += 5

    # 4. 实验实现自带三项菜单，且只在隔离目录里。
    experimental_entry = source(EXPERIMENTAL / (ENTRY + ".java"))
    assert LABEL in experimental_entry, "实验版 LifeProbeEntry 应保留探测入口（本地实验用）"
    checked += 1

    # 5. 构建脚本必须守住这条边界。
    build = BUILD.read_text(encoding="utf-8")
    for needle, reason in (('"--experimental-probe"', "实验开关"),
                           ("FIELD_EXPERIMENTAL", "实验源集路径"),
                           ("FORMAL_PROBE_ENTRY", "同 FQCN 二选一"),
                           ("PROBE_ONLY", "产物指纹"),
                           ("Release build contains the experimental protocol probe", "发布包反向断言"),
                           ("was given but the build lacks", "实验包正向断言")):
        assert needle in build, "build_preview.py 缺少%s（%s）" % (reason, needle)
        checked += 1

    # 6. 全仓库（除隔离目录外）不得再出现对该类的引用。
    offenders = []
    for path in sorted((ROOT / "client").rglob("*.java")):
        if EXPERIMENTAL in path.parents:
            continue
        if PROBE in path.read_text(encoding="utf-8", errors="ignore"):
            offenders.append(path.relative_to(ROOT).as_posix())
    assert not offenders, "以下文件仍在引用 %s：%s" % (PROBE, ", ".join(offenders))
    checked += 1

    print("PASS: %d 项实验性协议探测边界检查（源码 / 入口 / 构建断言）" % checked)


if __name__ == "__main__":
    main()
