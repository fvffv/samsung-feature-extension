package com.samsung.feature.extension;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

public final class LogSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.samsung.feature.extension.logsettings";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";
    public static final String METHOD_SET = "set";
    public static final String EXTRA_ENABLED = "enabled";

    private static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    private static final String PREFS = "module_log_settings";
    private static final boolean DEFAULT_ENABLED = false;
    private static final long CACHE_MS = 5000L;

    private static volatile boolean cachedEnabled = DEFAULT_ENABLED;
    private static volatile boolean cacheReady;
    private static volatile long cacheTimeMs;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            updateCache(readLocalEnabled(context), SystemClock.elapsedRealtime());
        }
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_SET.equals(method)) {
            boolean enabled = extras != null && extras.getBoolean(EXTRA_ENABLED, DEFAULT_ENABLED);
            setEnabled(getContext(), enabled);
        }
        Bundle result = new Bundle();
        result.putBoolean(EXTRA_ENABLED, getLocalEnabled(getContext()));
        return result;
    }

    public static boolean getLocalEnabled(Context context) {
        if (context == null) {
            return DEFAULT_ENABLED;
        }
        boolean enabled = readLocalEnabled(context);
        updateCache(enabled, SystemClock.elapsedRealtime());
        return enabled;
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) {
            updateCache(enabled, SystemClock.elapsedRealtime());
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(EXTRA_ENABLED, enabled)
                .apply();
        updateCache(enabled, SystemClock.elapsedRealtime());
        try {
            context.getContentResolver().notifyChange(URI, null);
        } catch (Throwable ignored) {
            // Notification is best-effort; hooked processes also refresh through a short cache.
        }
    }

    public static boolean isLogEnabled() {
        return cacheReady ? cachedEnabled : DEFAULT_ENABLED;
    }

    public static boolean isLogEnabled(Context context) {
        if (context == null) {
            return isLogEnabled();
        }
        long now = SystemClock.elapsedRealtime();
        if (cacheReady && now - cacheTimeMs < CACHE_MS) {
            return cachedEnabled;
        }
        boolean enabled = queryProviderEnabled(context);
        updateCache(enabled, now);
        return enabled;
    }

    private static boolean queryProviderEnabled(Context context) {
        if (context == null) {
            return DEFAULT_ENABLED;
        }
        try {
            String packageName = context.getPackageName();
            if (MODULE_PACKAGE.equals(packageName)) {
                return readLocalEnabled(context);
            }
        } catch (Throwable ignored) {
            // Fall through to the exported provider.
        }
        try {
            Bundle result = context.getContentResolver().call(URI, METHOD_GET, null, null);
            if (result != null && result.containsKey(EXTRA_ENABLED)) {
                return result.getBoolean(EXTRA_ENABLED, DEFAULT_ENABLED);
            }
        } catch (Throwable ignored) {
            // If the provider is unavailable, keep logging disabled to avoid repeated overhead.
        }
        return DEFAULT_ENABLED;
    }

    private static boolean readLocalEnabled(Context context) {
        if (context == null) {
            return DEFAULT_ENABLED;
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return prefs.getBoolean(EXTRA_ENABLED, DEFAULT_ENABLED);
        } catch (Throwable ignored) {
            return DEFAULT_ENABLED;
        }
    }

    private static void updateCache(boolean enabled, long now) {
        cachedEnabled = enabled;
        cacheReady = true;
        cacheTimeMs = now;
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
