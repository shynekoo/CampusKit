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
    public static final int SIZE = 12;
    private static final int[] FILLS = {
        0xffe0eeeb, 0xffe7e5f6, 0xfff8e8d7, 0xffddebf7, 0xfff4dfe7, 0xffe9edcf,
        0xffdceee2, 0xffeae4f7, 0xfffae3dc, 0xffd9ecf2, 0xfff2e2ee, 0xffe6eccf,
    };
    private static final int[] INKS = {
        0xff236d61, 0xff64518c, 0xff916135, 0xff37668b, 0xff914b67, 0xff607134,
        0xff1f6b45, 0xff5b4a8f, 0xff9c4a30, 0xff1f6274, 0xff8b3f72, 0xff556325,
    };
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
