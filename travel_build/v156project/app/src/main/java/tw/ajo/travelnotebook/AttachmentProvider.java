package tw.ajo.travelnotebook;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class AttachmentProvider extends ContentProvider {
    private static final String AUTHORITY = "tw.ajo.travelnotebook.files";
    private static final int FILE = 1;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static { MATCHER.addURI(AUTHORITY, "file/*", FILE); }

    public static Uri uriForFile(Context context, File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath("file").appendPath(file.getName()).build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (MATCHER.match(uri) != FILE) throw new FileNotFoundException("Bad uri");
        String name = uri.getLastPathSegment();
        if (name == null) name = "";
        if (name.contains("/") || name.contains("\\") || name.contains("..")) throw new FileNotFoundException("Bad name");
        File f = new File(providerContext().getCacheDir(), "shared/" + name);
        if (!f.exists()) throw new FileNotFoundException(name);
        return f;
    }

    private Context providerContext() { Context c = getContext(); if (c == null) throw new IllegalStateException(); return c; }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        try {
            String n = resolve(uri).getName().toLowerCase();
            if (n.endsWith(".pdf")) return "application/pdf";
            if (n.endsWith(".png")) return "image/png";
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
            if (n.endsWith(".webp")) return "image/webp";
            if (n.endsWith(".gif")) return "image/gif";
        } catch (Exception ignored) {}
        return "application/octet-stream";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f = resolve(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (Exception e) { return null; }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
