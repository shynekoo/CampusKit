package io.qzxy.lite.core;

/**
 * The timetable board's geometry, kept as arithmetic so it can be checked without a phone.
 *
 * <p>Seven day columns have to share whatever width the screen offers, and phones from 320dp to
 * 480dp are all in use. The board used to hard-code a 46dp gutter, 6dp cell padding, 12sp text and
 * no line limit, which on a narrow phone left under three characters per line and five stacked
 * fragments in a two-section block. Every number here is derived from the width instead, inside
 * bounds that keep the text readable: the gutter gives way first, then the padding, and the name
 * keeps at least {@link #MIN_CHARS} characters per line before the type is allowed to shrink.</p>
 *
 * <p>All values are dp (or sp for text); the view converts them. Nothing here reads a display.</p>
 */
public final class BoardFit {
    /** Gutter bounds: the section number and 08:30 still fit at 30dp. */
    public static final int MIN_AXIS = 30, MAX_AXIS = 46;
    /**
     * Course names stay legible at 9sp. The upper bound matters more than it looks: a name set at
     * 12sp on a 43dp column fits two characters a line, which turns every course into a vertical
     * strip of syllables and cuts the rest off with an ellipsis.
     */
    public static final int MIN_TITLE = 9, MAX_TITLE = 13;
    /** Beyond four lines a name reads as a vertical column of syllables rather than a word. */
    public static final int MAX_LINES = 4;
    /** Characters a name has to keep on one line; the type shrinks to hold this, then stops. */
    public static final int MIN_CHARS = 3;
    /** Space between two neighbouring cells; a block has its own copy of it and its own padding. */
    private static final int GAP = 4;
    /** Air between the name and the room lines, in dp. */
    private static final int ROOM_GAP = 2;
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
        return Math.max(2, Math.min(5, Math.round(Math.max(1, column) * 0.06f)));
    }

    /** Name size for a column: three characters a line on a 41dp column, never outside 9–13sp. */
    public static int titleSize(int column) {
        return Math.max(MIN_TITLE, Math.min(MAX_TITLE, Math.round(Math.max(1, column) * 0.24f)));
    }

    /** Room line size; two steps below the name so the name stays the first thing read. */
    public static int roomSize(int titleSize) {
        return Math.max(8, titleSize - 2);
    }

    /** Width a cell leaves for its own text: the column, less the gap and both paddings. */
    public static int textWidth(int column) {
        int width = Math.max(1, column);
        return Math.max(1, width - GAP - 2 * cellPad(width));
    }

    /** The gap a block keeps from its neighbours, in dp; it is taken off the column's width. */
    public static int gap() { return GAP; }

    /**
     * How many name lines a column can carry. Four is fine at three characters a line; below a
     * 42dp column it becomes a column of syllables, so the name is cut one line earlier instead.
     */
    public static int titleCap(int column) {
        return column >= 42 ? MAX_LINES : 3;
    }

    /**
     * Room lines a block keeps after its name.
     *
     * <p>The name is not optional and the room is: a room line is only shown while at least one line
     * of the name still fits beside it. Block height comes from the row height the board was given,
     * which shrinks when the page is short, so this is what stops a compressed block from spilling
     * its last line out of its own rounded corner.</p>
     *
     * @param space    height left inside the block, above and below its padding, in dp
     * @param nameLine measured height of one name line, in dp (line height, not point size)
     * @param roomLine measured height of one room line, in dp
     */
    public static int roomLines(float space, float nameLine, float roomLine) {
        if (space <= 0 || nameLine <= 0 || roomLine <= 0) return 0;
        if (space - (2 * roomLine + ROOM_GAP) >= nameLine) return 2;
        if (space - (roomLine + ROOM_GAP) >= nameLine) return 1;
        return 0;
    }

    /**
     * Trims a chosen text size (px) until {@link #MIN_CHARS} characters of the font actually in use
     * fit the cell, and never below {@code floorPx}.
     *
     * <p>This is the part the pure scale cannot answer: the nominal size comes from a column width,
     * but a phone theme can replace the font with one whose characters are far wider than the one the
     * scale was tuned on. Measuring the real font is the only way to keep three characters a line on
     * those phones instead of two.</p>
     *
     * @param contentPx width available inside the cell, in px
     * @param charPx width of one character at {@code textPx}, in px
     */
    public static int shrinkToFit(float contentPx, float charPx, int textPx, int floorPx) {
        if (contentPx <= 0 || charPx <= 0 || textPx <= 0) return textPx;
        float needed = MIN_CHARS * charPx;
        if (needed <= contentPx) return textPx;
        int scaled = Math.round(textPx * contentPx / needed);
        return Math.max(floorPx, Math.min(textPx, scaled));
    }
}
