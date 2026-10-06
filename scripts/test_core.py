"""Compile and test platform-independent client logic using the local JDK."""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    output = ROOT / "client/build/core-tests"
    output.mkdir(parents=True, exist_ok=True)
    sources = sorted((ROOT / "client/core").rglob("*.java"))
    subprocess.run(["javac", "-J-Duser.language=en", "--release", "8", "-encoding", "UTF-8", "-Xlint:all,-options", "-Werror",
                    "-d", str(output)] + [str(p) for p in sources], check=True)
    subprocess.run(["java", "-cp", str(output), "io.qzxy.lite.core.CoreTests"], check=True)


if __name__ == "__main__":
    main()
