package com.samsung.feature.extension;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

public final class CameraBitrateSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.samsung.feature.extension.camerabitrate";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";
    public static final String METHOD_SET = "set";
    public static final String METHOD_SET_OBSERVED = "setObserved";
    public static final String METHOD_SET_VIDEO_OBSERVED = "setVideoObserved";

    public static final String EXTRA_VIDEO_ENABLED = "videoEnabled";
    public static final String EXTRA_VIDEO_TARGET_MBPS = "videoTargetMbps";
    public static final String EXTRA_VIDEO_LAST_BPS = "videoLastBps";
    public static final String EXTRA_VIDEO_LAST_WIDTH = "videoLastWidth";
    public static final String EXTRA_VIDEO_LAST_HEIGHT = "videoLastHeight";
    public static final String EXTRA_VIDEO_LAST_FPS = "videoLastFps";
    public static final String EXTRA_VIDEO_LAST_TIME = "videoLastTime";

    public static final int VIDEO_8K = 0;
    public static final int VIDEO_4K = 1;
    public static final int VIDEO_FHD = 2;
    public static final int VIDEO_COUNT = 3;

    private static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    private static final String PREFS = "camera_bitrate";
    private static final int MAX_TARGET_MBPS = 1000;
    private static final long CACHE_MS = 2000L;

    private static volatile Settings cachedSettings;
    private static volatile long cacheTimeMs;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            updateCache(readLocalSettings(context), SystemClock.elapsedRealtime());
        }
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (METHOD_SET.equals(method)) {
            Settings current = readLocalSettings(context);
            boolean[] videoEnabled = extras != null
                    ? safeBooleanArray(extras.getBooleanArray(EXTRA_VIDEO_ENABLED), VIDEO_COUNT)
                    : current.videoEnabled;
            int[] videoTarget = extras != null
                    ? sanitizeVideoTargets(extras.getIntArray(EXTRA_VIDEO_TARGET_MBPS))
                    : current.videoTargetMbps;
            setSettings(context, videoEnabled, videoTarget);
        } else if (METHOD_SET_VIDEO_OBSERVED.equals(method) || METHOD_SET_OBSERVED.equals(method)) {
            long bitrate = extras != null ? extras.getLong(EXTRA_VIDEO_LAST_BPS, 0L) : 0L;
            int width = extras != null ? extras.getInt(EXTRA_VIDEO_LAST_WIDTH, 0) : 0;
            int height = extras != null ? extras.getInt(EXTRA_VIDEO_LAST_HEIGHT, 0) : 0;
            int fps = extras != null ? extras.getInt(EXTRA_VIDEO_LAST_FPS, 0) : 0;
            setVideoLastObserved(context, bitrate, width, height, fps);
        }
        return readLocalSettings(context).toBundle();
    }

    public static Settings getSettings(Context context) {
        if (context == null) {
            return Settings.empty();
        }
        long now = SystemClock.elapsedRealtime();
        Settings cached = cachedSettings;
        if (cached != null && now - cacheTimeMs < CACHE_MS) {
            return cached;
        }
        Settings settings = querySettings(context);
        updateCache(settings, now);
        return settings;
    }

    public static Settings getLocalSettings(Context context) {
        Settings settings = readLocalSettings(context);
        updateCache(settings, SystemClock.elapsedRealtime());
        return settings;
    }

    public static void setSettings(Context context, boolean[] videoEnabled, int[] videoTargetMbps) {
        if (context == null) {
            updateCache(Settings.empty(), SystemClock.elapsedRealtime());
            return;
        }
        boolean[] safeVideoEnabled = safeBooleanArray(videoEnabled, VIDEO_COUNT);
        int[] safeVideoTarget = sanitizeVideoTargets(videoTargetMbps);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int i = 0; i < VIDEO_COUNT; i++) {
            editor.putBoolean("video_enabled_" + i, safeVideoEnabled[i]);
            editor.putInt("video_target_mbps_" + i, safeVideoTarget[i]);
        }
        editor.apply();
        updateCache(readLocalSettings(context), SystemClock.elapsedRealtime());
        notifyChanged(context);
    }

    public static void reportVideoObserved(Context context, long bitrateBps, int width, int height, int fps) {
        if (context == null || bitrateBps <= 0L || videoCategoryForSize(width, height) < 0) {
            return;
        }
        try {
            if (MODULE_PACKAGE.equals(context.getPackageName())) {
                setVideoLastObserved(context, bitrateBps, width, height, fps);
                return;
            }
        } catch (Throwable ignored) {
            // Fall through to exported provider.
        }
        try {
            Bundle extras = new Bundle();
            extras.putLong(EXTRA_VIDEO_LAST_BPS, bitrateBps);
            extras.putInt(EXTRA_VIDEO_LAST_WIDTH, width);
            extras.putInt(EXTRA_VIDEO_LAST_HEIGHT, height);
            extras.putInt(EXTRA_VIDEO_LAST_FPS, fps);
            context.getContentResolver().call(URI, METHOD_SET_VIDEO_OBSERVED, null, extras);
        } catch (Throwable ignored) {
            // Diagnostic reporting must never break recording.
        }
    }

    public static int videoCategoryForSize(int width, int height) {
        int longSide = Math.max(width, height);
        int shortSide = Math.min(width, height);
        if (longSide >= 7600 && shortSide >= 3200) {
            return VIDEO_8K;
        }
        if (longSide >= 3800 && shortSide >= 1600) {
            return VIDEO_4K;
        }
        if (longSide >= 1800 && shortSide >= 800) {
            return VIDEO_FHD;
        }
        return -1;
    }

    public static String videoLabel(int category) {
        if (category == VIDEO_8K) {
            return "8K";
        }
        if (category == VIDEO_4K) {
            return "4K";
        }
        if (category == VIDEO_FHD) {
            return "FHD";
        }
        return "未知";
    }

    private static void setVideoLastObserved(Context context, long bitrateBps, int width, int height, int fps) {
        int category = videoCategoryForSize(width, height);
        if (context == null || bitrateBps <= 0L || category < 0) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("video_last_bps_" + category, bitrateBps)
                .putInt("video_last_width_" + category, width)
                .putInt("video_last_height_" + category, height)
                .putInt("video_last_fps_" + category, fps)
                .putLong("video_last_time_" + category, System.currentTimeMillis())
                .apply();
        updateCache(readLocalSettings(context), SystemClock.elapsedRealtime());
        notifyChanged(context);
    }

    private static Settings querySettings(Context context) {
        if (context == null) {
            return Settings.empty();
        }
        try {
            if (MODULE_PACKAGE.equals(context.getPackageName())) {
                return readLocalSettings(context);
            }
        } catch (Throwable ignored) {
            // Fall through to exported provider.
        }
        try {
            Bundle result = context.getContentResolver().call(URI, METHOD_GET, null, null);
            if (result != null) {
                return Settings.fromBundle(result);
            }
        } catch (Throwable ignored) {
            // Keep the camera process quiet if the provider is not ready.
        }
        return Settings.empty();
    }

    private static Settings readLocalSettings(Context context) {
        if (context == null) {
            return Settings.empty();
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            boolean[] videoEnabled = new boolean[VIDEO_COUNT];
            int[] videoTarget = new int[VIDEO_COUNT];
            long[] videoLastBps = new long[VIDEO_COUNT];
            int[] videoLastWidth = new int[VIDEO_COUNT];
            int[] videoLastHeight = new int[VIDEO_COUNT];
            int[] videoLastFps = new int[VIDEO_COUNT];
            long[] videoLastTime = new long[VIDEO_COUNT];
            for (int i = 0; i < VIDEO_COUNT; i++) {
                videoEnabled[i] = prefs.getBoolean("video_enabled_" + i, false);
                videoTarget[i] = sanitizeTargetMbps(prefs.getInt("video_target_mbps_" + i,
                        i == VIDEO_8K ? prefs.getInt("targetMbps", 0) : 0));
                videoLastBps[i] = prefs.getLong("video_last_bps_" + i,
                        i == VIDEO_8K ? prefs.getLong("lastDefaultBps", 0L) : 0L);
                videoLastWidth[i] = prefs.getInt("video_last_width_" + i,
                        i == VIDEO_8K ? prefs.getInt("lastWidth", 0) : 0);
                videoLastHeight[i] = prefs.getInt("video_last_height_" + i,
                        i == VIDEO_8K ? prefs.getInt("lastHeight", 0) : 0);
                videoLastFps[i] = prefs.getInt("video_last_fps_" + i,
                        i == VIDEO_8K ? prefs.getInt("lastFps", 0) : 0);
                videoLastTime[i] = prefs.getLong("video_last_time_" + i,
                        i == VIDEO_8K ? prefs.getLong("lastTime", 0L) : 0L);
            }
            if (prefs.getBoolean("enabled", false) && videoTarget[VIDEO_8K] > 0) {
                videoEnabled[VIDEO_8K] = true;
            }
            return new Settings(videoEnabled, videoTarget, videoLastBps, videoLastWidth, videoLastHeight,
                    videoLastFps, videoLastTime);
        } catch (Throwable ignored) {
            return Settings.empty();
        }
    }

    private static boolean[] safeBooleanArray(boolean[] input, int count) {
        boolean[] result = new boolean[count];
        if (input != null) {
            System.arraycopy(input, 0, result, 0, Math.min(input.length, count));
        }
        return result;
    }

    private static int[] sanitizeVideoTargets(int[] input) {
        int[] result = new int[VIDEO_COUNT];
        if (input != null) {
            for (int i = 0; i < Math.min(input.length, VIDEO_COUNT); i++) {
                result[i] = sanitizeTargetMbps(input[i]);
            }
        }
        return result;
    }

    private static int sanitizeTargetMbps(int targetMbps) {
        if (targetMbps <= 0) {
            return 0;
        }
        if (targetMbps > MAX_TARGET_MBPS) {
            return MAX_TARGET_MBPS;
        }
        return targetMbps;
    }

    private static void updateCache(Settings settings, long now) {
        cachedSettings = settings != null ? settings : Settings.empty();
        cacheTimeMs = now;
    }

    private static void notifyChanged(Context context) {
        try {
            context.getContentResolver().notifyChange(URI, null);
        } catch (Throwable ignored) {
            // Hooked processes also refresh through the short cache.
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

    public static final class Settings {
        public final boolean[] videoEnabled;
        public final int[] videoTargetMbps;
        public final long[] videoLastBitrateBps;
        public final int[] videoLastWidth;
        public final int[] videoLastHeight;
        public final int[] videoLastFps;
        public final long[] videoLastTimeMillis;

        Settings(boolean[] videoEnabled, int[] videoTargetMbps, long[] videoLastBitrateBps,
                 int[] videoLastWidth, int[] videoLastHeight, int[] videoLastFps,
                 long[] videoLastTimeMillis) {
            this.videoEnabled = safeBooleanArray(videoEnabled, VIDEO_COUNT);
            this.videoTargetMbps = sanitizeVideoTargets(videoTargetMbps);
            this.videoLastBitrateBps = safeLongArray(videoLastBitrateBps, VIDEO_COUNT);
            this.videoLastWidth = safeIntArray(videoLastWidth, VIDEO_COUNT);
            this.videoLastHeight = safeIntArray(videoLastHeight, VIDEO_COUNT);
            this.videoLastFps = safeIntArray(videoLastFps, VIDEO_COUNT);
            this.videoLastTimeMillis = safeLongArray(videoLastTimeMillis, VIDEO_COUNT);
        }

        public int videoTargetBitrateBps(int category) {
            if (category < 0 || category >= VIDEO_COUNT || !videoEnabled[category] || videoTargetMbps[category] <= 0) {
                return 0;
            }
            return videoTargetMbps[category] * 1000 * 1000;
        }

        Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putBooleanArray(EXTRA_VIDEO_ENABLED, videoEnabled);
            bundle.putIntArray(EXTRA_VIDEO_TARGET_MBPS, videoTargetMbps);
            bundle.putLongArray(EXTRA_VIDEO_LAST_BPS, videoLastBitrateBps);
            bundle.putIntArray(EXTRA_VIDEO_LAST_WIDTH, videoLastWidth);
            bundle.putIntArray(EXTRA_VIDEO_LAST_HEIGHT, videoLastHeight);
            bundle.putIntArray(EXTRA_VIDEO_LAST_FPS, videoLastFps);
            bundle.putLongArray(EXTRA_VIDEO_LAST_TIME, videoLastTimeMillis);
            return bundle;
        }

        static Settings fromBundle(Bundle bundle) {
            if (bundle == null) {
                return empty();
            }
            return new Settings(
                    bundle.getBooleanArray(EXTRA_VIDEO_ENABLED),
                    bundle.getIntArray(EXTRA_VIDEO_TARGET_MBPS),
                    bundle.getLongArray(EXTRA_VIDEO_LAST_BPS),
                    bundle.getIntArray(EXTRA_VIDEO_LAST_WIDTH),
                    bundle.getIntArray(EXTRA_VIDEO_LAST_HEIGHT),
                    bundle.getIntArray(EXTRA_VIDEO_LAST_FPS),
                    bundle.getLongArray(EXTRA_VIDEO_LAST_TIME));
        }

        static Settings empty() {
            return new Settings(null, null, null, null, null, null, null);
        }
    }

    private static int[] safeIntArray(int[] input, int count) {
        int[] result = new int[count];
        if (input != null) {
            System.arraycopy(input, 0, result, 0, Math.min(input.length, count));
        }
        return result;
    }

    private static long[] safeLongArray(long[] input, int count) {
        long[] result = new long[count];
        if (input != null) {
            System.arraycopy(input, 0, result, 0, Math.min(input.length, count));
        }
        return result;
    }
}
