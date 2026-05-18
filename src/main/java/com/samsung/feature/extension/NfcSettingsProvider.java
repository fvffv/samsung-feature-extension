package com.samsung.feature.extension;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class NfcSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.samsung.feature.extension.nfcsettings";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";
    public static final String METHOD_SET = "set";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_MODE = "mode";
    public static final int MODE_OFF = 0;
    public static final int MODE_SCREEN_ON_UNLOCKED = 1;
    public static final int MODE_SCREEN_OFF_UNLOCKED = 2;

    private static final String PREFS = "nfc_screen_off_tap";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (METHOD_SET.equals(method)) {
            int mode;
            if (extras != null && extras.containsKey(EXTRA_MODE)) {
                mode = sanitizeMode(extras.getInt(EXTRA_MODE, MODE_OFF));
                setMode(getContext(), mode);
            } else {
                boolean enabled = extras != null && extras.getBoolean(EXTRA_ENABLED, false);
                mode = enabled ? MODE_SCREEN_OFF_UNLOCKED : MODE_OFF;
                setMode(getContext(), mode);
            }
            result.putInt(EXTRA_MODE, mode);
            result.putBoolean(EXTRA_ENABLED, mode != MODE_OFF);
            return result;
        }
        int mode = getMode(getContext());
        result.putInt(EXTRA_MODE, mode);
        result.putBoolean(EXTRA_ENABLED, mode != MODE_OFF);
        return result;
    }

    public static boolean isEnabled(Context context) {
        return getMode(context) != MODE_OFF;
    }

    public static int getMode(Context context) {
        if (context == null) {
            return MODE_OFF;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.contains(EXTRA_MODE)) {
            return sanitizeMode(prefs.getInt(EXTRA_MODE, MODE_OFF));
        }
        return prefs.getBoolean(EXTRA_ENABLED, false) ? MODE_SCREEN_OFF_UNLOCKED : MODE_OFF;
    }

    public static void setEnabled(Context context, boolean enabled) {
        setMode(context, enabled ? MODE_SCREEN_OFF_UNLOCKED : MODE_OFF);
    }

    public static void setMode(Context context, int mode) {
        if (context == null) {
            return;
        }
        int safeMode = sanitizeMode(mode);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(EXTRA_MODE, safeMode)
                .putBoolean(EXTRA_ENABLED, safeMode != MODE_OFF)
                .apply();
        context.getContentResolver().notifyChange(URI, null);
    }

    private static int sanitizeMode(int mode) {
        if (mode == MODE_SCREEN_ON_UNLOCKED || mode == MODE_SCREEN_OFF_UNLOCKED) {
            return mode;
        }
        return MODE_OFF;
    }

    private SharedPreferences prefs() {
        Context context = getContext();
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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
