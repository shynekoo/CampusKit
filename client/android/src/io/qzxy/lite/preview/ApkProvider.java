// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.preview;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Hands one downloaded package to the system installer.
 *
 * <p>Android refuses a {@code file://} URI handed to another app ({@code FileUriExposedException}),
 * so the installer needs a {@code content://} address. This provider exposes exactly one folder —
 * the app's own updates directory — and only names ending in {@code .apk}, so a URI minted here
 * cannot be steered at a database, a preference file, or anything else on the phone.</p>
 *
 * <p>It is declared {@code exported="false"} and reachable only through a one-shot read grant that
 * travels with the install intent, which is the same arrangement FileProvider uses.</p>
 */
public final class ApkProvider extends ContentProvider {
    static final String AUTHORITY = "io.qzxy.lite.preview.apk";
    private static final String DIRECTORY = "updates";

    /** Where downloads live: per-app external storage when it exists, internal storage otherwise. */
    static File directory(Context context) {
        File external = context.getExternalFilesDir(null);
        return new File(external != null ? external : context.getFilesDir(), DIRECTORY);
    }

    static Uri uriFor(Context context, File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.getName()).build();
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // Read-only is not a formality: an installer never writes, and refusing writes keeps a
        // malformed URI from becoming a way to alter a package that is about to be installed.
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        String name = uri.getLastPathSegment();
        if (name == null || name.indexOf('/') >= 0 || name.indexOf("..") >= 0
            || !name.matches("[A-Za-z0-9._-]{1,64}\\.apk")) throw new FileNotFoundException("not a package");
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("no context");
        File file = new File(directory(context), name);
        if (!file.isFile()) throw new FileNotFoundException("missing " + name);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

    /** Nothing reads the provider as a table; the installer only streams the file. */
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }

    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
