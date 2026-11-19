package io.qzxy.lite.preview;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * 日志文件的落点：一个当前文件、三个历史文件，总量封顶。
 *
 * <p>为什么不在一个文件里一直追加：文件越大，读一次越慢，而排查要的往往只是最后几十行。
 * 当前文件写满 256 KB 就整体挪成 {@code diag.1.log}，历史依次后退，第四个最老的文件删掉；
 * 四个文件合计恰好 1 MB，是「够定位最近几次故障」与「不占用户空间」之间的折中。</p>
 *
 * <p>轮转必须整文件改名，不能截断：用户可能正打开日志页准备复制，截断会把要发出去的那几行
 * 抹掉。改名是原子的，读到的永远是完整的某一代文件。</p>
 *
 * <p>这个类只做文件，不做线程、不做格式。写失败一律吞掉——排查手段本身不能变成新的故障点。</p>
 */
final class DiagFiles {
    /** 单个文件上限。 */
    static final long MAX_BYTES = 256 * 1024L;
    /** 历史文件个数；连同当前文件，总量约 1 MB。 */
    static final int ARCHIVES = 3;
    /** 历史文件保留天数。 */
    static final long KEEP_MS = 7L * 24 * 60 * 60 * 1000;

    private static final String CURRENT = "diag.log";

    private final File dir;
    private final File current;
    private final File[] older = new File[ARCHIVES];
    private boolean ready;

    DiagFiles(File dir) {
        this.dir = dir;
        this.current = new File(dir, CURRENT);
        for (int i = 0; i < ARCHIVES; i++) older[i] = new File(dir, "diag." + (i + 1) + ".log");
    }

    static DiagFiles open(Context context) {
        DiagFiles files = new DiagFiles(new File(context.getFilesDir(), "diag"));
        return files.prepare() ? files : null;
    }

    /** 建目录、清过期历史、必要时先轮转一次。失败返回 false，调用方据此关掉整个功能。 */
    synchronized boolean prepare() {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            prune();
            if (current.isFile() && current.length() > MAX_BYTES) {
                // 上次退出前写超了（一次超长写入、或旧版本留下的文件）。直接改名会把一个超限文件
                // 变成历史文件，「单文件 256 KB」就不成立了，所以先只留尾部再归档。
                byte[] tail = tail(current, MAX_BYTES);
                try (FileOutputStream output = new FileOutputStream(current, false)) {
                    output.write(tail);
                }
                rotate();
            }
            ready = true;
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    boolean ready() { return ready; }

    /** 追加一段（可能多行）。写满就轮转，历史最老的一份被删掉。 */
    synchronized void append(String block) {
        if (!ready || block == null || block.isEmpty()) return;
        try {
            byte[] bytes = (block.endsWith("\n") ? block : block + "\n").getBytes(StandardCharsets.UTF_8);
            // 一次写入本身超过上限时只留尾部：轮转只能处理「写之前」的超限，管不了「这一笔」。
            if (bytes.length > MAX_BYTES) {
                byte[] tail = new byte[(int) MAX_BYTES];
                System.arraycopy(bytes, bytes.length - tail.length, tail, 0, tail.length);
                bytes = tail;
            }
            if (current.isFile() && current.length() > 0 && current.length() + bytes.length > MAX_BYTES) rotate();
            try (FileOutputStream output = new FileOutputStream(current, true)) {
                output.write(bytes);
            }
        } catch (Throwable ignored) {
            // 写不进去就放弃：日志不该影响任何正常流程。
        }
    }

    /**
     * 当前 + 历史，按时间顺序。读的时候最多取 1 MB 出头，再多也没有排查价值。
     */
    synchronized String read() {
        if (!ready) return "";
        StringBuilder out = new StringBuilder();
        for (int i = ARCHIVES - 1; i >= 0; i--) append(out, older[i]);
        append(out, current);
        return out.toString();
    }

    private static void append(StringBuilder out, File file) {
        if (!file.isFile()) return;
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > MAX_BYTES) break;
            }
            out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
        } catch (Throwable ignored) {
        }
    }

    /** 磁盘上实际占用的字节数：日志页显示的「日志大小」就是它。 */
    synchronized long bytes() {
        long total = 0;
        if (current.isFile()) total += current.length();
        for (File file : older) if (file.isFile()) total += file.length();
        return total;
    }

    /** 清空全部日志。删掉历史而不是留着，否则页面清空后翻页还能看到旧记录。 */
    synchronized void clear() {
        try (FileOutputStream output = new FileOutputStream(current, false)) {
            output.write(new byte[0]);
        } catch (Throwable ignored) {
        }
        for (File file : older) {
            try { if (file.isFile()) file.delete(); } catch (Throwable ignored) { }
        }
    }

    /** 文件末尾至多 {@code limit} 字节。截断点可能落在多字节字符中间，那一行会被解析器跳过。 */
    private static byte[] tail(File file, long limit) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            long from = Math.max(0, length - limit);
            input.seek(from);
            byte[] bytes = new byte[(int) (length - from)];
            input.readFully(bytes);
            return bytes;
        }
    }

    /** 过去的第四代文件已经没用，过期的历史同样。 */
    private void rotate() {
        try {
            File oldest = older[ARCHIVES - 1];
            if (oldest.isFile()) oldest.delete();
            for (int i = ARCHIVES - 2; i >= 0; i--) {
                if (older[i].isFile()) older[i].renameTo(older[i + 1]);
            }
            current.renameTo(older[0]);
        } catch (Throwable ignored) {
        }
    }

    /** 7 天前的历史没有定位价值，也不该一直占着空间。 */
    private void prune() {
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        for (File file : older) {
            try {
                if (file.isFile() && file.lastModified() < cutoff) file.delete();
            } catch (Throwable ignored) {
            }
        }
    }
}
