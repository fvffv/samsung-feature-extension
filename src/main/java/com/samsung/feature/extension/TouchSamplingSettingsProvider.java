package com.samsung.feature.extension;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class TouchSamplingSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.samsung.feature.extension.touchsampling";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";
    public static final String METHOD_SET = "set";
    public static final String EXTRA_ENABLED = "enabled";

    private static final String PREFS = "touch_sampling";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_SET.equals(method)) {
            boolean enabled = extras != null && extras.getBoolean(EXTRA_ENABLED, false);
            setEnabled(getContext(), enabled);
        }
        Bundle result = new Bundle();
        result.putBoolean(EXTRA_ENABLED, isEnabled(getContext()));
        return result;
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(EXTRA_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(EXTRA_ENABLED, enabled)
                .apply();
        context.getContentResolver().notifyChange(URI, null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
