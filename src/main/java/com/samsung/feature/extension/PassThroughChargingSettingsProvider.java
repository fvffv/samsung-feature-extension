package com.samsung.feature.extension;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public final class PassThroughChargingSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.samsung.feature.extension.passthroughcharging";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";
    public static final String METHOD_SET = "set";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_SYSTEM_VALUE = "systemValue";
    public static final String SYSTEM_SETTING = "pass_through";

    private static final String PREFS = "pass_through_charging";

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
        result.putInt(EXTRA_SYSTEM_VALUE, readSystemValue(getContext()));
        return result;
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(EXTRA_ENABLED, false);
        } catch (Throwable ignored) {
            return false;
        }
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

    public static int readSystemValue(Context context) {
        if (context == null) {
            return -1;
        }
        try {
            return Settings.System.getInt(context.getContentResolver(), SYSTEM_SETTING, 0);
        } catch (Throwable ignored) {
            return -1;
        }
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
