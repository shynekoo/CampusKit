package io.qzxy.lite.core;

/**
 * The timetable board's geometry, kept as arithmetic so it can be checked without a phone.
 *
 * <p>Seven day columns have to share whatever width the screen offers, and phones from 320dp to
 * 480dp are all in use. The board used to hard-code a 46dp gutter, 6dp cell padding, 12sp text and
 * no line limit, which on a narrow phone left under three characters per line and five stacked
 * fragments in a two-section block. Every number here is derived from the width instead, inside
 * bounds that keep the text readable: the gutter gives way first, then the padding, and the name
 * keeps at least two characters per line.</p>
 *
 * <p>All values are dp (or sp for text); the view converts them. Nothing here reads a display.</p>
 */
public final class BoardFit {
    /** Gutter bounds: the section number and 08:30 still fit at 30dp. */
    public static final int MIN_AXIS = 30, MAX_AXIS = 46;
    /** Course names stay legible at 10sp; above 13sp a long name loses more lines than it gains. */
    public static final int MIN_TITLE = 10, MAX_TITLE = 13;
    /** Beyond four lines a name reads as a vertical column of syllables rather than a word. */
    public static final int MAX_LINES = 4;
    private BoardFit() {}

    /** Width of the section-and-time gutter for a board of the given width. */
    public static int axis(int boardWidth) {
        if (boardWidth <= 0) return MIN_AXIS;
        return Math.max(MIN_AXIS, Math.min(MAX_AXIS, Math.round(boardWidth * 0.115f)));
    }

    /** Width of one day column; the seven columns plus the gutter always fit the board. */
    public static int column(int boardWidth) {
        int width = Math.max(1, boardWidth);
        return Math.max(1, (width - axis(width)) / 7);
    }

    /** Inner padding of a course block: proportionally smaller as the column narrows. */
    public static int cellPad(int column) {
        return Math.max(2, Math.min(6, Math.round(Math.max(1, column) * 0.1f)));
    }

    /** Name size for a column: 12sp on a 43dp column, never outside 10–13sp. */
    public static int titleSize(int column) {
        return Math.max(MIN_TITLE, Math.min(MAX_TITLE, Math.round(Math.max(1, column) * 0.28f)));
    }

    /** Room line size; two steps below the name so the name stays the first thing read. */
    public static int roomSize(int titleSize) {
        return Math.max(8, titleSize - 2);
    }

    /**
     * How many name lines a column can carry. Four is fine at three characters a line; below a
     * 42dp column it becomes a column of syllables, so the name is cut one line earlier instead.
     */
    public static int titleCap(int column) {
        return column >= 42 ? MAX_LINES : 3;
    }

    /**
     * Room lines a block of the given height can hold after its name: 2 normally, 1 in a short
     * block, none at all when even one line would leave the name unreadable.
     */
    public static int roomLines(int blockHeight, int column) {
        int pad = cellPad(column), title = titleSize(column), room = roomSize(title);
        int oneNameAndTwoRooms = 2 * pad + title + 2 * room + 6;
        int twoNamesAndTwoRooms = 2 * pad + 2 * title + 2 * room + 10;
        if (blockHeight < oneNameAndTwoRooms) return 0;
        if (blockHeight < twoNamesAndTwoRooms) return 1;
        return 2;
    }
}
