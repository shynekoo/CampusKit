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
    /** One picture for the whole app; it used to live inside each table's file. */
    private static final String ART = "timetable-art-v1";
    private static final String KEY_BACKGROUND = "background";
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

    private static SharedPreferences art(Context context) {
        return context.getSharedPreferences(ART, Context.MODE_PRIVATE);
    }

    /**
     * The board background, shared by every timetable: it is a look, not course data, and deleting a
     * timetable must not take the picture with it. Installs from before this split kept the picture
     * inside the table's own file, so it is lifted out on first read — and again right before a table
     * is deleted, which is the one case where the old file disappears before anyone reads it.
     */
    static String background(Context context) {
        String shared = art(context).getString(KEY_BACKGROUND, "");
        if (!shared.isEmpty()) return shared;
        SharedPreferences table = open(context);
        String legacy = table.getString(KEY_BACKGROUND, "");
        if (legacy.isEmpty()) return "";
        art(context).edit().putString(KEY_BACKGROUND, legacy).commit();
        table.edit().remove(KEY_BACKGROUND).commit();
        return legacy;
    }

    /** Stores the encoded picture; an empty value puts the default board back. */
    static boolean background(Context context, String encoded) {
        SharedPreferences.Editor edit = art(context).edit();
        if (encoded == null || encoded.isEmpty()) edit.remove(KEY_BACKGROUND);
        else edit.putString(KEY_BACKGROUND, encoded);
        return edit.commit();
    }

    private static void liftBackground(Context context, String id) {
        SharedPreferences table = open(context, id);
        String legacy = table.getString(KEY_BACKGROUND, "");
        if (legacy.isEmpty()) return;
        if (art(context).getString(KEY_BACKGROUND, "").isEmpty())
            art(context).edit().putString(KEY_BACKGROUND, legacy).commit();
        table.edit().remove(KEY_BACKGROUND).commit();
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

    /** Removes one table and its courses; the last remaining table is never removed. */
    static boolean remove(Context context, String id) {
        TimetableIndex index = index(context);
        if (!index.remove(id)) return false;
        liftBackground(context, id);
        save(context, index);
        open(context, id).edit().clear().commit();
        return true;
    }

    /** Deletes every table and the index; the next read starts a fresh 我的课表. */
    static void wipe(Context context) {
        for (TimetableIndex.Table table : index(context).tables())
            open(context, table.id).edit().clear().commit();
        prefs(context).edit().clear().commit();
        art(context).edit().clear().commit();
    }
}
