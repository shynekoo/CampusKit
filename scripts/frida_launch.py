# SPDX-License-Identifier: MPL-2.0
"""frida spawn 启动脚本：让易盾加固壳在 x86_64 模拟器上走 arm64 加载路径。

原理：壳在 MyApplication.attachBaseContext 里根据 MyJni.a()（读 os.arch 与
/proc/cpuinfo 判断 x86）选择 libnesec-x86（x86_64 ELF）。但应用主 ABI 是
arm64，x86_64 ELF 会被路由到 ndk_translation 而加载失败。真机上走的是
arm64 libnesec.so。本脚本把 MyJni.a() 改为 false，使加载路径与真机一致。

用法：
  python scripts/frida_launch.py            # 启动并保持会话（Ctrl+C 退出）
  python scripts/frida_launch.py --attach   # 只附加到已运行进程
"""

import argparse
import sys
import time

import frida

PKG = "com.klcxkj.zqxy"

HOOK_JS = """
Java.perform(function () {
    var MyJni = Java.use("com.netease.nis.wrapper.MyJni");
    MyJni.a.overload().implementation = function () {
        console.log("[hook] MyJni.a() -> false (force arm64 nesec path)");
        return false;
    };
    // 记录 dlopen 失败原因，壳本身吞掉了异常
    var Sys = Java.use("java.lang.System");
    Sys.loadLibrary.overload("java.lang.String").implementation = function (name) {
        try {
            console.log("[load] loadLibrary " + name);
            return this.loadLibrary(name);
        } catch (t) {
            console.log("[load] loadLibrary " + name + " FAILED: " + t);
            throw t;
        }
    };
    Sys.load.overload("java.lang.String").implementation = function (path) {
        try {
            console.log("[load] System.load " + path);
            return this.load(path);
        } catch (t) {
            console.log("[load] System.load " + path + " FAILED: " + t);
            throw t;
        }
    };
    console.log("[hook] installed");
});
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True, help="Exact Frida/ADB emulator ID; no automatic device selection")
    parser.add_argument("--attach", action="store_true")
    parser.add_argument("--force-arm64", action="store_true", help="Opt in only for the documented loader failure")
    parser.add_argument("--seconds", type=int, default=30)
    args = parser.parse_args()
    if args.device != "127.0.0.1:16544":
        parser.error("Current authorization covers only QZXY-Login-Lab at 127.0.0.1:16544")
    if not 1 <= args.seconds <= 120:
        parser.error("seconds must be between 1 and 120")
    device = frida.get_device(args.device, timeout=10)
    print("device:", device)
    if args.attach:
        session = device.attach(PKG)
    else:
        pid = device.spawn([PKG])
        print("spawned pid", pid)
        session = device.attach(pid)
    script = session.create_script(HOOK_JS if args.force_arm64 else 'send({status: "attached", loader_hook: false});')
    script.on("message", lambda m, data: print("[msg]", m))
    script.load()
    if not args.attach:
        device.resume(pid)
        print("resumed")
    try:
        time.sleep(args.seconds)
    finally:
        session.detach()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
