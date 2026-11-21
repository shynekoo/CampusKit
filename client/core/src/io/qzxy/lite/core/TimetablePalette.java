// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Colours for timetable blocks. Kept out of the Android layer so the assignment rule — in particular
 * "two different courses never look the same" — can be tested without a device.
 *
 * Each entry pairs a fill with an ink chosen for contrast, so a caller never has to invent a text
 * colour for a user-picked background. Index 0 is reserved for "automatic"; explicit choices are
 * 1..SIZE.
 */
public final class TimetablePalette {
    public static final int SIZE = 18;
    private static final int[] FILLS = {
        0xffdcefe9,0xffe8e1f5,0xfff7e5ce,0xffdce9fa,0xfff5dfe8,0xffe9edcf,
        0xffddefdb,0xffe0e2f8,0xfff8dfd3,0xffd8eef3,0xfff0dff1,0xffe3ead8,
        0xffd9eaf0,0xfff6edcf,0xffecdeda,0xfff7e0dc,0xffe2e8ef,0xffd4efef
    };
    private static final int[] INKS = {
        0xff225f50,0xff57417d,0xff795026,0xff315982,0xff803d59,0xff566127,
        0xff386333,0xff454d86,0xff874328,0xff255d70,0xff754078,0xff4a5f35,
        0xff35586b,0xff73601e,0xff6f4d43,0xff853e3c,0xff46566c,0xff235f60
    };
    private static final String[] NAMES={"青瓷","藤紫","杏仁","雾蓝","玫瑰","青柠","豆绿","鸢尾","蜜桃","晴空","藕荷","鼠尾草","湖蓝","麦黄","可可","珊瑚","石板","薄荷"};
    public static String name(int choice){return choice>=1&&choice<=SIZE?NAMES[choice-1]:"自动";}
    /** Neutral swatch for the "automatic" choice in the editor. */
    public static final int AUTO_FILL = 0xffeef1ef, AUTO_INK = 0xff6b778c;

    private TimetablePalette() { }

    /** Fill for an explicit choice (1..SIZE); index 0 and out-of-range give the neutral swatch. */
    public static int fill(int choice) {
        return choice >= 1 && choice <= SIZE ? FILLS[choice - 1] : AUTO_FILL;
    }

    /** Ink for an explicit choice (1..SIZE); index 0 and out-of-range give the neutral ink. */
    public static int ink(int choice) {
        return choice >= 1 && choice <= SIZE ? INKS[choice - 1] : AUTO_INK;
    }

    /** Palette index (0-based) for a course that has no explicit colour. */
    public static int automatic(int hash) {
        int mixed = hash ^ (hash >>> 16);
        return (mixed & 0x7fffffff) % SIZE;
    }

    /**
     * Assigns a palette index to every distinct name, probing forward on collision so that up to
     * {@link #SIZE} different courses all get visibly different colours. Names are visited in sorted
     * order, so the result depends only on the set of names — not on the order they appear in, and not
     * on which week is currently on screen.
     *
     * @return one index per distinct name, in the same order as {@code names} would be after sorting
     *         and de-duplication; callers should key their own map by name
     */
    public static List<String> distinctNames(List<String> names) {
        SortedSet<String> sorted = new TreeSet<>();
        for (String name : names) if (name != null && !name.isEmpty()) sorted.add(name);
        return new ArrayList<>(sorted);
    }

    /**
     * @param names distinct names, normally from {@link #distinctNames}
     * @return palette index per name, aligned with {@code names}
     */
    public static int[] assign(List<String> names) {
        int[] result = new int[names.size()];
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < names.size(); i++) {
            int start = automatic(names.get(i).hashCode()), chosen = -1;
            for (int k = 0; k < SIZE; k++) {
                int candidate = (start + k) % SIZE;
                if (!used.contains(candidate)) { chosen = candidate; break; }
            }
            if (chosen < 0) chosen = start;   // More distinct courses than colours; reuse is unavoidable.
            used.add(chosen);
            result[i] = chosen;
        }
        return result;
    }
}
