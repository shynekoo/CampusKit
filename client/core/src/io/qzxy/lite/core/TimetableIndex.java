// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The list of timetables and which one is current, as one small text document.
 *
 * <p>The document is only an index: each timetable's courses and background live in its own storage,
 * keyed by the id kept here. That keeps this object tiny enough to rewrite on every change and stops
 * one table's data from ever being touched while editing another.</p>
 *
 * <p>An empty, missing or corrupted document falls back to a single {@code main} table named
 * 我的课表 — which is exactly what an install from before multiple timetables looks like, so the old
 * data needs no migration.</p>
 *
 * <p>The format is deliberately plain text rather than JSON: this class is part of the Android-free
 * core, and names are user input, so each field is percent-encoded instead of being pasted between
 * quotes. One table per line, tab separated, versioned on the first line.</p>
 */
public final class TimetableIndex {
    /** Id of the timetable that pre-dates multiple tables; its storage keeps the old name. */
    public static final String FIRST = "main";
    public static final String DEFAULT_NAME = "我的课表";
    public static final int NAME_LIMIT = 24;
    private static final String HEADER = "CampusKit.timetables/1";
    private static final String KEY_CURRENT = "current=";
    private static final String KEY_TABLE = "table=";

    public static final class Table {
        public final String id, name;

        public Table(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final List<Table> tables = new ArrayList<>();
    private String current = FIRST;

    private TimetableIndex() {}

    public static TimetableIndex parse(String text) {
        TimetableIndex index = new TimetableIndex();
        String current = FIRST;
        if (text != null && text.startsWith(HEADER)) {
            for (String line : text.split("\n")) {
                line = line.trim();
                if (line.startsWith(KEY_CURRENT)) {
                    current = decode(line.substring(KEY_CURRENT.length()));
                } else if (line.startsWith(KEY_TABLE)) {
                    String value = line.substring(KEY_TABLE.length());
                    int cut = value.indexOf('\t');
                    if (cut < 0) continue;
                    String id = decode(value.substring(0, cut));
                    String name = decode(value.substring(cut + 1)).trim();
                    if (id.isEmpty() || name.isEmpty() || index.has(id)) continue;
                    index.tables.add(new Table(id, name));
                }
            }
        }
        if (index.tables.isEmpty()) {
            index.tables.add(new Table(FIRST, DEFAULT_NAME));
            current = FIRST;
        }
        index.current = index.has(current) ? current : index.tables.get(0).id;
        return index;
    }

    public List<Table> tables() {
        return Collections.unmodifiableList(new ArrayList<>(tables));
    }

    public int size() {
        return tables.size();
    }

    public String current() {
        return current;
    }

    public String currentName() {
        for (Table table : tables) if (table.id.equals(current)) return table.name;
        return DEFAULT_NAME;
    }

    public boolean has(String id) {
        return find(id) != null;
    }

    public boolean select(String id) {
        if (id == null || !has(id)) return false;
        current = id;
        return true;
    }

    /** Adds an empty table, makes it current, and returns its id; id collisions grow an "x" tail. */
    public String create(String name, long stamp) {
        String id = Long.toString(Math.max(1L, stamp), 36);
        while (has(id)) id += "x";
        tables.add(new Table(id, label(name)));
        current = id;
        return id;
    }

    public boolean rename(String id, String name) {
        Table found = find(id);
        if (found == null) return false;
        tables.set(tables.indexOf(found), new Table(found.id, label(name)));
        return true;
    }

    /** Removes one table; the last remaining table is never removed. */
    public boolean remove(String id) {
        Table found = find(id);
        if (found == null || tables.size() <= 1) return false;
        tables.remove(found);
        if (id.equals(current)) current = tables.get(0).id;
        return true;
    }

    public String toText() {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        out.append(KEY_CURRENT).append(encode(current)).append('\n');
        for (Table table : tables) {
            out.append(KEY_TABLE).append(encode(table.id)).append('\t').append(encode(table.name)).append('\n');
        }
        return out.toString();
    }

    private Table find(String id) {
        if (id == null) return null;
        for (Table table : tables) if (table.id.equals(id)) return table;
        return null;
    }

    /** Trims, caps the length, and never leaves an unnamed entry in the picker. */
    private static String label(String name) {
        String value = name == null ? "" : name.trim();
        if (value.length() > NAME_LIMIT) value = value.substring(0, NAME_LIMIT);
        return value.isEmpty() ? DEFAULT_NAME : value;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            return "";
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception broken) {
            return "";
        }
    }
}
