package com.example.campuscompanion.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;

import com.example.campuscompanion.data.local.AppDatabase;

/**
 * Exposes the deadlines table through content://com.example.campuscompanion.provider/deadlines
 * It is not exported, so only this app can use it. To share it with another app (for the demo),
 * set android:exported="true" in the manifest and protect it with a permission.
 */
public class DeadlineProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.campuscompanion.provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/deadlines");

    private static final String TABLE = "deadlines";
    private static final int DEADLINES = 1;
    private static final int DEADLINE_ID = 2;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "deadlines", DEADLINES);
        MATCHER.addURI(AUTHORITY, "deadlines/#", DEADLINE_ID);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private SupportSQLiteDatabase db(boolean writable) {
        AppDatabase room = AppDatabase.get(getContext());
        return writable
                ? room.getOpenHelper().getWritableDatabase()
                : room.getOpenHelper().getReadableDatabase();
    }

    /** For /deadlines/5 adds "id = 5" to the caller's own WHERE clause. */
    private String where(Uri uri, String selection) {
        if (MATCHER.match(uri) == DEADLINE_ID) {
            String idPart = "id = " + ContentUris.parseId(uri);
            return selection == null || selection.isEmpty()
                    ? idPart : idPart + " AND (" + selection + ")";
        }
        return selection;
    }

    private void notifyChange(Uri uri) {
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (MATCHER.match(uri) == UriMatcher.NO_MATCH) throw new IllegalArgumentException("Unknown URI " + uri);

        SupportSQLiteQueryBuilder qb = SupportSQLiteQueryBuilder.builder(TABLE)
                .columns(projection)
                .selection(where(uri, selection), selectionArgs)
                .orderBy(sortOrder == null ? "dueAt ASC" : sortOrder);

        Cursor c = db(false).query(qb.create());
        if (getContext() != null) c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (MATCHER.match(uri) != DEADLINES) throw new IllegalArgumentException("Insert needs " + CONTENT_URI);
        ContentValues v = values == null ? new ContentValues() : new ContentValues(values);

        // The table has NOT NULL columns, so fill sensible defaults when the caller omits them.
        if (!v.containsKey("title")) v.put("title", "Untitled");
        if (!v.containsKey("priority")) v.put("priority", "MEDIUM");
        if (!v.containsKey("dueAt")) v.put("dueAt", System.currentTimeMillis());
        if (!v.containsKey("remindBeforeMinutes")) v.put("remindBeforeMinutes", -1);
        if (!v.containsKey("done")) v.put("done", 0);
        if (!v.containsKey("isShared")) v.put("isShared", 0);

        long id = db(true).insert(TABLE, SQLiteDatabase.CONFLICT_NONE, v);
        if (id < 0) return null;
        notifyChange(CONTENT_URI);
        return ContentUris.withAppendedId(CONTENT_URI, id);
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        if (values == null || MATCHER.match(uri) == UriMatcher.NO_MATCH) return 0;
        int n = db(true).update(TABLE, SQLiteDatabase.CONFLICT_NONE, values,
                where(uri, selection), selectionArgs);
        if (n > 0) notifyChange(uri);
        return n;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        if (MATCHER.match(uri) == UriMatcher.NO_MATCH) return 0;
        int n = db(true).delete(TABLE, where(uri, selection), selectionArgs);
        if (n > 0) notifyChange(uri);
        return n;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case DEADLINES:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".deadline";
            case DEADLINE_ID:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".deadline";
            default:
                return null;
        }
    }
}
