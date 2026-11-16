package io.qzxy.lite.preview;

import android.content.Context;
import android.content.SharedPreferences;
import io.qzxy.lite.core.TimetableIndex;
import java.util.List;

/**
 * Storage bridge for {@link TimetableIndex}: the list itself lives in one small preferences entry,
 * while each timetable's courses stay in an ordinary preferences file of their own.
 *
 * <p>The first table keeps the pre-1.5.5 storage name {@code timetable-v1}, so an existing install
 * opens with its old timetable already there and nothing to migrate.</p>
 */
final class TimetableStore {
    private static final String INDEX = "timetable-index-v1";
    private static final String KEY_INDEX = "index";
    static final String DEFAULT_NAME = TimetableIndex.DEFAULT_NAME;

    private TimetableStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(INDEX, Context.MODE_PRIVATE);
    }

    static TimetableIndex index(Context context) {
        return TimetableIndex.parse(prefs(context).getString(KEY_INDEX, ""));
    }

    private static void save(Context context, TimetableIndex index) {
        prefs(context).edit().putString(KEY_INDEX, index.toText()).apply();
    }

    static List<TimetableIndex.Table> tables(Context context) {
        return index(context).tables();
    }

    static String currentId(Context context) {
        return index(context).current();
    }

    static String currentName(Context context) {
        return index(context).currentName();
    }

    /** The name stored for one table, whatever the id; falls back to the current name. */
    static String name(Context context, String id) {
        for (TimetableIndex.Table table : index(context).tables()) {
            if (table.id.equals(id)) return table.name;
        }
        return currentName(context);
    }

    /** Preferences of the current table — the object every screen already knows how to use. */
    static SharedPreferences open(Context context) {
        return open(context, currentId(context));
    }

    static SharedPreferences open(Context context, String id) {
        return context.getSharedPreferences(storageName(id), Context.MODE_PRIVATE);
    }

    private static String storageName(String id) {
        return TimetableIndex.FIRST.equals(id) ? "timetable-v1" : "timetable-" + id;
    }

    static void select(Context context, String id) {
        TimetableIndex index = index(context);
        if (index.select(id)) save(context, index);
    }

    /** Creates an empty table, makes it current, and returns its id. */
    static String create(Context context, String name) {
        TimetableIndex index = index(context);
        String id = index.create(name, System.currentTimeMillis());
        save(context, index);
        return id;
    }

    static void rename(Context context, String id, String name) {
        TimetableIndex index = index(context);
        if (index.rename(id, name)) save(context, index);
    }

    /** Removes one table and its data; the last remaining table is never removed. */
    static boolean remove(Context context, String id) {
        TimetableIndex index = index(context);
        if (!index.remove(id)) return false;
        save(context, index);
        open(context, id).edit().clear().commit();
        return true;
    }

    /** Deletes every table and the index; the next read starts a fresh 我的课表. */
    static void wipe(Context context) {
        for (TimetableIndex.Table table : index(context).tables())
            open(context, table.id).edit().clear().commit();
        prefs(context).edit().clear().commit();
    }
}
