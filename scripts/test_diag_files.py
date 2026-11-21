# SPDX-License-Identifier: MPL-2.0
"""Run the log-retention rules for real: rotation, the 1 MB cap, ordering, clear, pruning.

Run with: python scripts/test_diag_files.py

`test_ui_contract.py` only checks that the constants and the rename call are still in the source.
That cannot tell whether an archive actually gets dropped, whether the oldest line survives a
rotation, or whether clearing leaves a stale `diag.1.log` behind - all of which are the difference
between "the log is 1 MB" and "the phone quietly filled up".

`DiagFiles` touches Android only through `open(Context)`; every retention rule works on plain
`File`. So the class is compiled against `android.jar` and driven here on the JVM, with no device.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / "tools/android-sdk"

HARNESS = r'''package io.qzxy.lite.preview;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Drives the retention rules of DiagFiles: rotation, cap, ordering, clear, pruning. */
public final class DiagFilesBehaviour {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("diagfiles");
        rotation(root.resolve("rotation"));
        oversizedOnPrepare(root.resolve("oversized"));
        hugeSingleWrite(root.resolve("huge"));
        clearing(root.resolve("clear"));
        pruning(root.resolve("prune"));
        System.out.println("All " + checks + " diag file assertions passed.");
    }

    /** Filling a file rotates it; four files cap the total; reads stay in order without gaps. */
    private static void rotation(Path dir) throws Exception {
        DiagFiles files = new DiagFiles(dir.toFile());
        check(files.prepare(), "fresh directory prepares");
        check(files.ready(), "ready is true after prepare");
        check(files.read().isEmpty(), "fresh directory reads empty");
        check(files.bytes() == 0, "fresh directory occupies nothing");

        char[] pad = new char[180];
        java.util.Arrays.fill(pad, 'x');
        String filler = new String(pad);

        // ~187 bytes per line, so 1400 lines cross the 256 KB per-file limit and rotate once.
        for (int i = 0; i < 1400; i++) files.append(String.format("L%05d %s", i, filler));
        check(new File(dir.toFile(), "diag.1.log").isFile(), "a full file becomes diag.1.log");
        check(files.bytes() <= DiagFiles.MAX_BYTES * 4, "total stays under 1 MB");
        everyFileWithinLimit(dir);
        assertContinuous(files.read(), "after the first rotation");

        // Push towards 1.5 MB: the fourth generation has to go, but nothing in the window is lost.
        for (int i = 1400; i < 8000; i++) files.append(String.format("L%05d %s", i, filler));
        check(!new File(dir.toFile(), "diag.4.log").isFile(), "no fifth file appears");
        check(files.bytes() <= DiagFiles.MAX_BYTES * 4, "total still capped after more writes");
        everyFileWithinLimit(dir);
        String text = files.read();
        String[] lines = text.split("\n");
        check(lines.length > 0, "content survives rotation");
        check(number(lines[0]) > 0, "the oldest lines are dropped once past the cap");
        check(number(lines[lines.length - 1]) == 7999, "the newest line is always readable");
        assertContinuous(text, "after passing 1 MB");
    }

    /** The current file was already over the limit when the process died: trim, then archive. */
    private static void oversizedOnPrepare(Path dir) throws Exception {
        Files.createDirectories(dir);
        File current = new File(dir.toFile(), "diag.log");
        byte[] big = new byte[(int) DiagFiles.MAX_BYTES + 1024];
        java.util.Arrays.fill(big, (byte) 'z');
        try (FileOutputStream out = new FileOutputStream(current)) {
            out.write(big);
        }
        DiagFiles files = new DiagFiles(dir.toFile());
        check(files.prepare(), "an oversized leftover does not break prepare");
        check(new File(dir.toFile(), "diag.1.log").isFile(), "the leftover is archived on start");
        check(new File(dir.toFile(), "diag.log").length() == 0, "the new current file starts empty");
        check(files.bytes() <= DiagFiles.MAX_BYTES, "the archive is trimmed to the per-file limit");
    }

    /** One write larger than the limit must not produce an oversized file either. */
    private static void hugeSingleWrite(Path dir) throws Exception {
        DiagFiles files = new DiagFiles(dir.toFile());
        check(files.prepare(), "fresh directory prepares for a huge write");
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 3000; i++) huge.append(String.format("H%05d %s\n", i, "0123456789"));
        files.append(huge.toString());
        everyFileWithinLimit(dir);
        check(files.bytes() <= DiagFiles.MAX_BYTES, "a single huge write is trimmed, not stored whole");
        check(files.read().contains("H02999"), "the tail of the huge write survives");
    }

    /** Clearing must remove the archives too, or the next page still shows "cleared" records. */
    private static void clearing(Path dir) throws Exception {
        DiagFiles files = new DiagFiles(dir.toFile());
        check(files.prepare(), "clear case prepares the directory");
        for (int i = 0; i < 1400; i++) files.append("line " + i);
        check(files.bytes() > 0, "records exist before clearing");

        files.clear();
        check(files.bytes() == 0, "nothing is left on disk");
        check(files.read().isEmpty(), "cleared records are unreadable");
        check(!new File(dir.toFile(), "diag.1.log").exists(), "clearing deletes the archives");

        files.append("after clear");
        check(files.read().trim().equals("after clear"), "writing still works after clearing");

        // The read path must still work after a clear: fill again and expect a rotation.
        char[] pad = new char[180];
        java.util.Arrays.fill(pad, 'y');
        for (int i = 0; i < 1400; i++) files.append(String.format("R%05d %s", i, new String(pad)));
        check(new File(dir.toFile(), "diag.1.log").isFile(), "rotation still works after clearing");
    }

    /** Seven-day-old history has no triage value; prepare drops it and keeps the fresh file. */
    private static void pruning(Path dir) throws Exception {
        Files.createDirectories(dir);
        File stale = new File(dir.toFile(), "diag.2.log");
        Files.write(stale.toPath(), "stale\n".getBytes(StandardCharsets.UTF_8));
        long eightDaysAgo = System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000;
        if (!stale.setLastModified(eightDaysAgo)) throw new AssertionError("cannot backdate a file");
        File fresh = new File(dir.toFile(), "diag.1.log");
        Files.write(fresh.toPath(), "fresh\n".getBytes(StandardCharsets.UTF_8));

        DiagFiles files = new DiagFiles(dir.toFile());
        check(files.prepare(), "prune case prepares the directory");
        check(!stale.exists(), "history older than 7 days is deleted");
        check(fresh.exists(), "fresh history stays");
        check(files.read().contains("fresh"), "the kept history is still readable");
    }

    private static void everyFileWithinLimit(Path dir) {
        File[] all = dir.toFile().listFiles();
        if (all == null) throw new AssertionError("directory is unreadable");
        for (File file : all) {
            check(file.length() <= DiagFiles.MAX_BYTES, file.getName() + " stays within 256 KB");
        }
    }

    private static void assertContinuous(String text, String when) {
        String[] lines = text.split("\n");
        check(lines.length > 0, when + ": at least one line");
        int expected = number(lines[0]);
        for (String line : lines) {
            int actual = number(line);
            check(actual == expected, when + ": line numbers stay contiguous at " + actual);
            expected++;
        }
    }

    private static int number(String line) {
        String tag = line.substring(0, Math.min(6, line.length()));
        if (tag.length() < 6 || !tag.startsWith("L") && !tag.startsWith("R") && !tag.startsWith("H")) {
            throw new AssertionError("unexpected line: " + line);
        }
        return Integer.parseInt(tag.substring(1));
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError("FAILED: " + what);
    }
}
'''


def jdk_home():
    properties = subprocess.run(["java", "-XshowSettings:properties", "-version"],
                                capture_output=True, text=True, errors="replace", check=True)
    found = re.search(r"java.home\s*=\s*(.+)", properties.stderr)
    if found is None:
        raise RuntimeError("JDK location not found")
    return Path(found.group(1).strip())


def main():
    android = SDK / "platforms/android-36/android.jar"
    if not android.exists():
        raise RuntimeError("missing %s; this script needs the Android SDK to compile DiagFiles" % android)
    jdk = jdk_home()
    harness = ROOT / "client/build/diag-files-tests/src/io/qzxy/lite/preview/DiagFilesBehaviour.java"
    harness.parent.mkdir(parents=True, exist_ok=True)
    harness.write_text(HARNESS, encoding="utf-8")
    classes = ROOT / "client/build/diag-files-tests/classes"
    classes.mkdir(parents=True, exist_ok=True)
    sources = [ROOT / "client/android/src/io/qzxy/lite/preview/DiagFiles.java", harness]
    subprocess.run([str(jdk / "bin/javac"), "-J-Duser.language=en", "--release", "8", "-encoding", "UTF-8",
                    "-Xlint:-options", "-proc:none", "-classpath", str(android), "-d", str(classes)]
                   + [str(p) for p in sources], check=True)
    subprocess.run([str(jdk / "bin/java"), "-cp", str(classes) + ";" + str(android),
                    "io.qzxy.lite.preview.DiagFilesBehaviour"], check=True)


if __name__ == "__main__":
    main()
