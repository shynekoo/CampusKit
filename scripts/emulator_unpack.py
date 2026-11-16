"""模拟器动态解包编排脚本。

用法：
  python scripts/emulator_unpack.py setup    # 创建 AVD（仅一次）
  python scripts/emulator_unpack.py boot     # 无头启动模拟器并等待开机完成
  python scripts/emulator_unpack.py install  # 安装原版 APK 并启动到启动页
  python scripts/emulator_unpack.py launch   # 启动/重启应用进程
  python scripts/emulator_unpack.py dump     # 推送 frida-server 并 dump 内存中的 DEX
  python scripts/emulator_unpack.py stop     # 关闭模拟器

仅供名为 qzxy-unpack 的 Android AVD 使用；并非当前授权的 MuMu 测试目标。
此脚本没有实现断网，原版启动可能自行发起网络请求。当前暂停其变更操作，
避免无设备选择的旧命令误操作其他模拟器或手机。
"""

import os
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SDK = os.path.join(ROOT, "tools", "android-sdk")
ADB = os.path.join(ROOT, "tools", "platform-tools", "adb.exe")
AVDMANAGER = os.path.join(ROOT, "tools", "downloads", "cmdline-tools", "bin", "avdmanager.bat")
EMU = os.path.join(SDK, "emulator", "emulator.exe")
APK = os.path.join(ROOT, "inputs", "qzxy-original.apk")
FRIDA_SERVER = os.path.join(ROOT, "tools", "downloads", "frida-server")
DUMP_DIR = os.path.join(ROOT, "analysis", "private", "dumped-dex")

AVD_NAME = "qzxy-unpack"
SYSIMG = "system-images;android-34;google_apis;x86_64"
PKG = "com.klcxkj.zqxy"
LAUNCH_ACTIVITY = "com.klcxkj.zqxy/ui.splash.SplashActivity"

# 无头启动；Hyper-V/WHPX 环境默认硬件加速。
EMU_FLAGS = [
    "-avd", AVD_NAME,
    "-no-window",
    "-gpu", "swiftshader_indirect",
    "-no-audio",
    "-no-boot-anim",
    "-no-snapshot",
    "-memory", "4096",
    "-cores", "2",
]


def run(cmd, **kw):
    print("+", " ".join(str(c) for c in cmd))
    return subprocess.run([str(c) for c in cmd], **kw)


def adb(*args, **kw):
    return run([ADB, *args], **kw)


def adb_out(*args):
    return subprocess.run([str(a) for a in [ADB, *args]],
                          capture_output=True, text=True).stdout.strip()


def stage_setup():
    res = subprocess.run([EMU, "-list-avds"], capture_output=True)
    listed = (res.stdout or b"").decode("utf-8", errors="replace")
    if AVD_NAME in listed:
        print(f"AVD {AVD_NAME} 已存在")
        return 0
    env = dict(os.environ, ANDROID_SDK_ROOT=SDK, ANDROID_HOME=SDK)
    proc = subprocess.run(
        [AVDMANAGER, "create", "avd", "-n", AVD_NAME, "-k", SYSIMG, "--device", "pixel_6"],
        input=b"no\n", capture_output=True, env=env)
    print((proc.stdout or b"").decode("utf-8", errors="replace"),
          (proc.stderr or b"").decode("utf-8", errors="replace"))
    return proc.returncode


def stage_boot():
    proc = subprocess.Popen([str(c) for c in [EMU, *EMU_FLAGS]],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"emulator pid={proc.pid}，等待 boot 完成...")
    adb("wait-for-device")
    deadline = time.time() + 900
    while time.time() < deadline:
        state = adb_out("shell", "getprop", "sys.boot_completed")
        if state.strip() == "1":
            print("boot 完成")
            return 0
        time.sleep(5)
    print("等待 boot 超时", file=sys.stderr)
    return 1


def stage_install():
    adb("install", "-r", "-t", APK)
    return stage_launch()


def stage_launch():
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)
    adb("shell", "am", "start", "-n", LAUNCH_ACTIVITY)
    time.sleep(8)
    print(adb_out("shell", "pidof", PKG) or "进程未运行")
    return 0


def stage_dump():
    adb("root")
    time.sleep(2)
    adb("wait-for-device")
    adb("push", FRIDA_SERVER, "/data/local/tmp/frida-server")
    adb("shell", "chmod", "755", "/data/local/tmp/frida-server")
    adb("shell", "killall", "-9", "frida-server")
    subprocess.run(["cmd", "/c", "start", "/b", "",
                    ADB, "shell", "/data/local/tmp/frida-server"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(3)
    pid = adb_out("shell", "pidof", PKG)
    if not pid:
        stage_launch()
    os.makedirs(DUMP_DIR, exist_ok=True)
    # frida-dexdump 安装在 Python Scripts 目录
    import shutil
    exe = shutil.which("frida-dexdump")
    if not exe:
        base = os.path.dirname(sys.executable)
        exe = os.path.join(base, "Scripts", "frida-dexdump.exe")
    res = run([exe, "-U", "-n", PKG, "-d", DUMP_DIR, "-f"])
    print("dump 输出目录:", DUMP_DIR)
    return res.returncode


def stage_stop():
    adb("emu", "kill")
    return 0


STAGES = {
    "setup": stage_setup,
    "boot": stage_boot,
    "install": stage_install,
    "launch": stage_launch,
    "dump": stage_dump,
    "stop": stage_stop,
}

if __name__ == "__main__":
    sys.exit("Legacy AVD script disabled: target identity and scoped authorization must be implemented before use. Use the explicitly authorized MuMu lab workflow.")
    if len(sys.argv) != 2 or sys.argv[1] not in STAGES:
        print(__doc__)
        sys.exit(2)
    sys.exit(STAGES[sys.argv[1]]())
