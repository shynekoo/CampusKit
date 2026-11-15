package io.qzxy.lite.core;

import java.util.List;

/**
 * Column detection for the academic-system tables read through the WebView.
 *
 * <p>School pages change every semester, so nothing here assumes a fixed column order: a header
 * cell is matched by its label and the index is reported back. When either the course name or the
 * score column cannot be identified the caller must fall back to a plain table instead of guessing
 * which number is a score.</p>
 */
public final class EduTableColumns {
    public static final int NAME = 0, SCORE = 1, CREDIT = 2, GPA = 3;

    private EduTableColumns() { }

    /** Returns {name, score, credit, gpa} indexes, or -1 for a column that is not present. */
    public static int[] grade(List<String> headers) {
        int[] found = {-1, -1, -1, -1};
        if (headers == null) return found;
        for (int i = 0; i < headers.size(); i++) {
            String label = compact(headers.get(i));
            if (label.isEmpty()) continue;
            if (found[NAME] < 0 && isName(label)) found[NAME] = i;
            else if (found[SCORE] < 0 && isScore(label)) found[SCORE] = i;
            else if (found[CREDIT] < 0 && label.contains("学分")) found[CREDIT] = i;
            else if (found[GPA] < 0 && label.contains("绩点")) found[GPA] = i;
        }
        return found;
    }

    /** A grade table needs a course column and a score column; everything else is optional. */
    public static boolean usable(int[] columns) {
        return columns != null && columns.length >= 2 && columns[NAME] >= 0 && columns[SCORE] >= 0;
    }

    private static boolean isName(String label) {
        return label.contains("课程名称") || label.equals("课程") || label.contains("科目")
            || label.contains("考试名称");
    }

    private static boolean isScore(String label) {
        // "学分" is a different column and must never be read as a score.
        if (label.contains("学分") || label.contains("绩点")) return false;
        return label.contains("成绩") || label.contains("总分") || label.contains("分数")
            || label.equals("得分");
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
