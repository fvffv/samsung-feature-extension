package com.samsung.feature.extension;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class ChargingStyleSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.samsung.feature.extension.chargingstyle";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);

    public static final String METHOD_GET = "get";
    public static final String METHOD_SET = "set";

    public static final String SOUND_PLUG = "plug";
    public static final String SOUND_UNPLUG = "unplug";

    public static final int SOUND_MODE_SYSTEM = 0;
    public static final int SOUND_MODE_CUSTOM = 1;
    public static final int SOUND_MODE_OFF = 2;

    public static final String EXTRA_DISPLAY_ENABLED = "displayEnabled";
    public static final String EXTRA_TITLE_TEMPLATE = "titleTemplate";
    public static final String EXTRA_CONTENT_TEMPLATE = "contentTemplate";
    public static final String EXTRA_PLUG_SOUND_MODE = "plugSoundMode";
    public static final String EXTRA_UNPLUG_SOUND_MODE = "unplugSoundMode";
    public static final String EXTRA_PLUG_SOUND_NAME = "plugSoundName";
    public static final String EXTRA_UNPLUG_SOUND_NAME = "unplugSoundName";
    public static final String EXTRA_PLUG_SOUND_AVAILABLE = "plugSoundAvailable";
    public static final String EXTRA_UNPLUG_SOUND_AVAILABLE = "unplugSoundAvailable";
    public static final String EXTRA_UPDATED_AT = "updatedAt";

    public static final String DEFAULT_TITLE_TEMPLATE = "";
    public static final String DEFAULT_CONTENT_TEMPLATE =
            "\u8fd8\u5269 {time_min} \u5206\u949f\u5145\u6ee1\u7535";
    private static final String OLD_DEFAULT_TITLE_TEMPLATE = "{type} {level}%";
    private static final String OLD_DEFAULT_CONTENT_TEMPLATE =
            "\u5269\u4f59 {time} \u00b7 {power}W \u00b7 {temp}\u00b0C";

    private static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    private static final String PREFS = "charging_style";
    private static final String KEY_DISPLAY_ENABLED = "display_enabled";
    private static final String KEY_TITLE_TEMPLATE = "title_template";
    private static final String KEY_CONTENT_TEMPLATE = "content_template";
    private static final String KEY_PLUG_SOUND_MODE = "plug_sound_mode";
    private static final String KEY_UNPLUG_SOUND_MODE = "unplug_sound_mode";
    private static final String KEY_PLUG_SOUND_NAME = "plug_sound_name";
    private static final String KEY_UNPLUG_SOUND_NAME = "unplug_sound_name";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final long CACHE_MS = 1500L;

    private static volatile SettingsSnapshot cachedSettings;
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
        if (METHOD_SET.equals(method) && extras != null) {
            setSettings(context, SettingsSnapshot.fromBundle(extras));
        }
        return readLocalSettings(context).toBundle();
    }

    public static SettingsSnapshot getSettings(Context context) {
        if (context == null) {
            return SettingsSnapshot.empty();
        }
        long now = SystemClock.elapsedRealtime();
        SettingsSnapshot cached = cachedSettings;
        if (cached != null && now - cacheTimeMs < CACHE_MS) {
            return cached;
        }
        SettingsSnapshot settings = querySettings(context);
        updateCache(settings, now);
        return settings;
    }

    public static SettingsSnapshot getLocalSettings(Context context) {
        SettingsSnapshot settings = readLocalSettings(context);
        updateCache(settings, SystemClock.elapsedRealtime());
        return settings;
    }

    public static void setSettings(Context context, SettingsSnapshot settings) {
        if (context == null || settings == null) {
            updateCache(SettingsSnapshot.empty(), SystemClock.elapsedRealtime());
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        String contentTemplate = sanitizeTemplate(settings.contentTemplate, DEFAULT_CONTENT_TEMPLATE);
        editor.putBoolean(KEY_DISPLAY_ENABLED, settings.displayEnabled);
        editor.remove(KEY_TITLE_TEMPLATE);
        editor.putString(KEY_CONTENT_TEMPLATE, contentTemplate);
        editor.putInt(KEY_PLUG_SOUND_MODE, sanitizeSoundMode(settings.plugSoundMode, SOUND_MODE_SYSTEM));
        editor.putInt(KEY_UNPLUG_SOUND_MODE, sanitizeSoundMode(settings.unplugSoundMode, SOUND_MODE_OFF));
        editor.putString(KEY_PLUG_SOUND_NAME, safeText(settings.plugSoundName));
        editor.putString(KEY_UNPLUG_SOUND_NAME, safeText(settings.unplugSoundName));
        editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis());
        editor.apply();
        updateCache(readLocalSettings(context), SystemClock.elapsedRealtime());
        notifyChanged(context);
    }

    public static boolean saveSoundFile(Context context, Uri source, String soundType) {
        if (context == null || source == null || !isKnownSoundType(soundType)) {
            return false;
        }
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(source);
            if (input == null) {
                return false;
            }
            File target = soundFile(context, soundType);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            output = new FileOutputStream(target, false);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            String displayName = queryDisplayName(context, source);
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            if (SOUND_PLUG.equals(soundType)) {
                editor.putString(KEY_PLUG_SOUND_NAME, displayName);
                editor.putInt(KEY_PLUG_SOUND_MODE, SOUND_MODE_CUSTOM);
            } else {
                editor.putString(KEY_UNPLUG_SOUND_NAME, displayName);
                editor.putInt(KEY_UNPLUG_SOUND_MODE, SOUND_MODE_CUSTOM);
            }
            editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis());
            editor.apply();
            updateCache(readLocalSettings(context), SystemClock.elapsedRealtime());
            notifyChanged(context);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            closeQuietly(output);
            closeQuietly(input);
        }
    }

    public static void clearSoundFile(Context context, String soundType) {
        if (context == null || !isKnownSoundType(soundType)) {
            return;
        }
        File file = soundFile(context, soundType);
        if (file.exists()) {
            file.delete();
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (SOUND_PLUG.equals(soundType)) {
            editor.remove(KEY_PLUG_SOUND_NAME);
            editor.putInt(KEY_PLUG_SOUND_MODE, SOUND_MODE_SYSTEM);
        } else {
            editor.remove(KEY_UNPLUG_SOUND_NAME);
            editor.putInt(KEY_UNPLUG_SOUND_MODE, SOUND_MODE_OFF);
        }
        editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis());
        editor.apply();
        updateCache(readLocalSettings(context), SystemClock.elapsedRealtime());
        notifyChanged(context);
    }

    public static Uri soundUri(String soundType) {
        return Uri.withAppendedPath(Uri.withAppendedPath(URI, "sound"), soundType);
    }

    public static String customSoundUriString(SettingsSnapshot settings, String soundType) {
        if (settings == null || !isKnownSoundType(soundType)) {
            return "";
        }
        if (SOUND_PLUG.equals(soundType) && !settings.plugSoundAvailable) {
            return "";
        }
        if (SOUND_UNPLUG.equals(soundType) && !settings.unplugSoundAvailable) {
            return "";
        }
        return soundUri(soundType).toString() + "?t=" + settings.updatedAt;
    }

    public static String currentSystemPlugSound(Context context) {
        return currentSystemPlugSound(context, false);
    }

    public static String currentSystemFastPlugSound(Context context) {
        return currentSystemPlugSound(context, true);
    }

    public static String currentSystemPlugSound(Context context, boolean fast) {
        String fileName = fast ? "ChargingStarted_Fast" : "ChargingStarted";
        String theme = readSystemString(context, "system_sound");
        String previousTheme = readSystemString(context, "prev_system_sound");
        if (isDefaultSystemSoundTheme(theme)) {
            return systemUiSoundPath(fileName, "");
        }
        if ("Open_theme".equals(theme)) {
            return isDefaultSystemSoundTheme(previousTheme)
                    ? systemUiSoundPath(fileName, "")
                    : systemUiSoundPath(fileName, previousTheme);
        }
        return systemUiSoundPath(fileName, theme);
    }

    public static String currentSystemUnplugSound(Context context) {
        return "";
    }

    public static BatteryValues currentBatteryValues(Context context) {
        BatteryValues values = BatteryValues.empty();
        if (context == null) {
            return values;
        }
        Intent battery = null;
        try {
            battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Throwable ignored) {
            // Keep preview and hooks quiet.
        }
        if (battery == null) {
            return values;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level >= 0 && scale > 0 && scale != 100) {
            level = Math.round((level * 100f) / scale);
        }
        values.level = Math.max(0, level);
        values.status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, 1);
        values.plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        values.voltageMv = readVoltageMv(battery);
        values.tempTenthsC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        values.currentMa = readCurrentMa(battery);
        values.chargingType = readChargingType(battery);
        values.remainingSeconds = readRemainingSeconds(context);
        return values;
    }

    public static BatteryValues notificationValues(Context context, int level, long chargingTime, int chargingType) {
        BatteryValues values = currentBatteryValues(context);
        if (level >= 0) {
            values.level = level;
        }
        if (chargingTime > 0) {
            values.remainingSeconds = chargingTime;
        }
        values.chargingType = chargingType;
        return values;
    }

    public static String formatTemplate(String template, BatteryValues values, String systemText) {
        if (template == null) {
            return "";
        }
        BatteryValues safeValues = values != null ? values : BatteryValues.empty();
        String text = template;
        text = text.replace("{level}", safeNumber(safeValues.level));
        text = text.replace("{time}", formatDuration(safeValues.remainingSeconds));
        text = text.replace("{time_min}", safeValues.remainingSeconds > 0
                ? String.valueOf(Math.max(1L, safeValues.remainingSeconds / 60L)) : "");
        text = text.replace("{type}", chargingTypeLabel(safeValues.chargingType));
        text = text.replace("{plug}", plugLabel(safeValues.plugged));
        text = text.replace("{status}", statusLabel(safeValues.status));
        text = text.replace("{current}", safeCurrent(safeValues.currentMa));
        text = text.replace("{voltage}", safeVoltage(safeValues.voltageMv));
        text = text.replace("{power}", safePower(safeValues.currentMa, safeValues.voltageMv));
        text = text.replace("{temp}", safeTemperature(safeValues.tempTenthsC));
        text = text.replace("{system}", systemText != null ? systemText : "");
        return text.trim();
    }

    private static SettingsSnapshot querySettings(Context context) {
        if (context == null) {
            return SettingsSnapshot.empty();
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
                return SettingsSnapshot.fromBundle(result);
            }
        } catch (Throwable ignored) {
            // SystemUI must stay quiet if the provider is not ready.
        }
        return SettingsSnapshot.empty();
    }

    private static SettingsSnapshot readLocalSettings(Context context) {
        if (context == null) {
            return SettingsSnapshot.empty();
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            boolean plugAvailable = soundFile(context, SOUND_PLUG).isFile();
            boolean unplugAvailable = soundFile(context, SOUND_UNPLUG).isFile();
            String contentTemplate = sanitizeTemplate(
                    prefs.getString(KEY_CONTENT_TEMPLATE, DEFAULT_CONTENT_TEMPLATE),
                    DEFAULT_CONTENT_TEMPLATE);
            return new SettingsSnapshot(
                    prefs.getBoolean(KEY_DISPLAY_ENABLED, false),
                    DEFAULT_TITLE_TEMPLATE,
                    contentTemplate,
                    sanitizeSoundMode(prefs.getInt(KEY_PLUG_SOUND_MODE, SOUND_MODE_SYSTEM), SOUND_MODE_SYSTEM),
                    sanitizeSoundMode(prefs.getInt(KEY_UNPLUG_SOUND_MODE, SOUND_MODE_OFF), SOUND_MODE_OFF),
                    prefs.getString(KEY_PLUG_SOUND_NAME, ""),
                    prefs.getString(KEY_UNPLUG_SOUND_NAME, ""),
                    plugAvailable,
                    unplugAvailable,
                    prefs.getLong(KEY_UPDATED_AT, 0L));
        } catch (Throwable ignored) {
            return SettingsSnapshot.empty();
        }
    }

    private static void updateCache(SettingsSnapshot settings, long now) {
        cachedSettings = settings != null ? settings : SettingsSnapshot.empty();
        cacheTimeMs = now;
    }

    private static void notifyChanged(Context context) {
        try {
            context.getContentResolver().notifyChange(URI, null);
        } catch (Throwable ignored) {
            // Optional.
        }
    }

    private static File soundFile(Context context, String soundType) {
        return new File(new File(context.getFilesDir(), "charging_sounds"), soundType + ".audio");
    }

    private static String queryDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (!TextUtils.isEmpty(name)) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
            // Use URI fallback below.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return TextUtils.isEmpty(last) ? "custom_audio" : last;
    }

    private static String readGlobalString(Context context, String key) {
        try {
            return Settings.Global.getString(context.getContentResolver(), key);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readSystemString(Context context, String key) {
        try {
            return Settings.System.getString(context.getContentResolver(), key);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isDefaultSystemSoundTheme(String value) {
        return TextUtils.isEmpty(value) || "Galaxy".equals(value);
    }

    private static String systemUiSoundPath(String name, String theme) {
        if (TextUtils.isEmpty(theme)) {
            return "/system/media/audio/ui/" + name + ".ogg";
        }
        return "/system/media/audio/ui/" + name + "_" + theme + ".ogg";
    }

    private static long readRemainingSeconds(Context context) {
        if (context == null) {
            return 0L;
        }
        try {
            Intent intent = context.registerReceiver(null, new IntentFilter(
                    "com.samsung.server.BatteryService.action.SEC_BATTERY_REMAINING_CHARGING_TIME_CHANGED"));
            return intent != null ? intent.getLongExtra("remaining_charging_time", 0L) : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static int readChargingType(Intent battery) {
        int type = battery.getIntExtra("charge_type", 0);
        if (type <= 0) {
            String text = battery.getStringExtra("charge_type");
            if ("Fast".equalsIgnoreCase(text)) {
                type = 2;
            }
        }
        return type;
    }

    private static int readVoltageMv(Intent battery) {
        int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        if (voltage <= 0) {
            voltage = battery.getIntExtra("voltage", 0);
        }
        if (voltage > 100000) {
            voltage /= 1000;
        }
        return voltage;
    }

    private static int readCurrentMa(Intent battery) {
        int current = battery.getIntExtra("current_avg", Integer.MIN_VALUE);
        if (current == Integer.MIN_VALUE) {
            current = battery.getIntExtra("current_now", Integer.MIN_VALUE);
        }
        if (current == Integer.MIN_VALUE) {
            current = battery.getIntExtra("max_charging_current", Integer.MIN_VALUE);
            if (current != Integer.MIN_VALUE) {
                current /= 1000;
            }
        }
        if (current == Integer.MIN_VALUE) {
            return 0;
        }
        if (Math.abs(current) > 100000) {
            current /= 1000;
        }
        return Math.abs(current);
    }

    private static String chargingTypeLabel(int type) {
        switch (type) {
            case 2:
                return "快速充电";
            case 3:
                return "超级快充";
            case 4:
                return "超级快充 2.0";
            case 6:
                return "无线充电";
            case 7:
                return "快速无线充电";
            case 8:
                return "慢速充电";
            case 9:
                return "连接不完整";
            case 10:
                return "无线充电";
            case 11:
                return "充电";
            case 12:
                return "智能充电";
            default:
                return "充电";
        }
    }

    private static String plugLabel(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) {
            return "无线";
        }
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) {
            return "USB";
        }
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) {
            return "AC";
        }
        return "";
    }

    private static String statusLabel(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "放电中";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "未充电";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "已充满";
            default:
                return "";
        }
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0L) {
            return "";
        }
        long minutes = Math.max(1L, seconds / 60L);
        long hours = minutes / 60L;
        long mins = minutes % 60L;
        if (hours > 0L && mins > 0L) {
            return hours + "小时" + mins + "分钟";
        }
        if (hours > 0L) {
            return hours + "小时";
        }
        return mins + "分钟";
    }

    private static String safeNumber(int value) {
        return value >= 0 ? String.valueOf(value) : "";
    }

    private static String safeCurrent(int currentMa) {
        return currentMa > 0 ? String.valueOf(currentMa) : "";
    }

    private static String safeVoltage(int voltageMv) {
        return voltageMv > 0 ? String.format(Locale.US, "%.2f", voltageMv / 1000f) : "";
    }

    private static String safePower(int currentMa, int voltageMv) {
        if (currentMa <= 0 || voltageMv <= 0) {
            return "";
        }
        return String.format(Locale.US, "%.1f", (currentMa * voltageMv) / 1000000f);
    }

    private static String safeTemperature(int tempTenthsC) {
        if (tempTenthsC == Integer.MIN_VALUE) {
            return "";
        }
        return String.format(Locale.US, "%.1f", tempTenthsC / 10f);
    }

    private static String sanitizeTemplate(String value, String fallback) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > 160) {
            safe = safe.substring(0, 160);
        }
        if (OLD_DEFAULT_TITLE_TEMPLATE.equals(safe)
                || OLD_DEFAULT_CONTENT_TEMPLATE.equals(safe)) {
            return DEFAULT_CONTENT_TEMPLATE;
        }
        return TextUtils.isEmpty(safe) ? fallback : safe;
    }

    private static int sanitizeSoundMode(int mode, int fallback) {
        return mode == SOUND_MODE_SYSTEM || mode == SOUND_MODE_CUSTOM || mode == SOUND_MODE_OFF
                ? mode : fallback;
    }

    private static boolean isKnownSoundType(String soundType) {
        return SOUND_PLUG.equals(soundType) || SOUND_UNPLUG.equals(soundType);
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static void closeQuietly(FileOutputStream output) {
        if (output != null) {
            try {
                output.close();
            } catch (IOException ignored) {
                // Ignore.
            }
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
                // Ignore.
            }
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null || uri == null || !"r".equals(mode)) {
            throw new FileNotFoundException();
        }
        String soundType = uri.getLastPathSegment();
        if (!isKnownSoundType(soundType)) {
            throw new FileNotFoundException();
        }
        File file = soundFile(context, soundType);
        if (!file.isFile()) {
            throw new FileNotFoundException();
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"key", "value"});
        SettingsSnapshot settings = readLocalSettings(getContext());
        cursor.addRow(new Object[]{EXTRA_DISPLAY_ENABLED, settings.displayEnabled ? "1" : "0"});
        cursor.addRow(new Object[]{EXTRA_TITLE_TEMPLATE, settings.titleTemplate});
        cursor.addRow(new Object[]{EXTRA_CONTENT_TEMPLATE, settings.contentTemplate});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        if (uri != null && "sound".equals(uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0))) {
            return "audio/*";
        }
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

    public static final class SettingsSnapshot {
        public final boolean displayEnabled;
        public final String titleTemplate;
        public final String contentTemplate;
        public final int plugSoundMode;
        public final int unplugSoundMode;
        public final String plugSoundName;
        public final String unplugSoundName;
        public final boolean plugSoundAvailable;
        public final boolean unplugSoundAvailable;
        public final long updatedAt;

        public SettingsSnapshot(boolean displayEnabled, String titleTemplate, String contentTemplate,
                                int plugSoundMode, int unplugSoundMode, String plugSoundName,
                                String unplugSoundName, boolean plugSoundAvailable,
                                boolean unplugSoundAvailable, long updatedAt) {
            this.displayEnabled = displayEnabled;
            this.titleTemplate = titleTemplate;
            this.contentTemplate = contentTemplate;
            this.plugSoundMode = plugSoundMode;
            this.unplugSoundMode = unplugSoundMode;
            this.plugSoundName = plugSoundName;
            this.unplugSoundName = unplugSoundName;
            this.plugSoundAvailable = plugSoundAvailable;
            this.unplugSoundAvailable = unplugSoundAvailable;
            this.updatedAt = updatedAt;
        }

        public static SettingsSnapshot empty() {
            return new SettingsSnapshot(false, DEFAULT_TITLE_TEMPLATE, DEFAULT_CONTENT_TEMPLATE,
                    SOUND_MODE_SYSTEM, SOUND_MODE_OFF, "", "", false, false, 0L);
        }

        public Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putBoolean(EXTRA_DISPLAY_ENABLED, displayEnabled);
            bundle.putString(EXTRA_TITLE_TEMPLATE, titleTemplate);
            bundle.putString(EXTRA_CONTENT_TEMPLATE, contentTemplate);
            bundle.putInt(EXTRA_PLUG_SOUND_MODE, plugSoundMode);
            bundle.putInt(EXTRA_UNPLUG_SOUND_MODE, unplugSoundMode);
            bundle.putString(EXTRA_PLUG_SOUND_NAME, plugSoundName);
            bundle.putString(EXTRA_UNPLUG_SOUND_NAME, unplugSoundName);
            bundle.putBoolean(EXTRA_PLUG_SOUND_AVAILABLE, plugSoundAvailable);
            bundle.putBoolean(EXTRA_UNPLUG_SOUND_AVAILABLE, unplugSoundAvailable);
            bundle.putLong(EXTRA_UPDATED_AT, updatedAt);
            return bundle;
        }

        public static SettingsSnapshot fromBundle(Bundle bundle) {
            if (bundle == null) {
                return empty();
            }
            SettingsSnapshot defaults = empty();
            String contentTemplate = sanitizeTemplate(
                    bundle.getString(EXTRA_CONTENT_TEMPLATE, defaults.contentTemplate),
                    DEFAULT_CONTENT_TEMPLATE);
            return new SettingsSnapshot(
                    bundle.getBoolean(EXTRA_DISPLAY_ENABLED, defaults.displayEnabled),
                    DEFAULT_TITLE_TEMPLATE,
                    contentTemplate,
                    sanitizeSoundMode(bundle.getInt(EXTRA_PLUG_SOUND_MODE, defaults.plugSoundMode),
                            SOUND_MODE_SYSTEM),
                    sanitizeSoundMode(bundle.getInt(EXTRA_UNPLUG_SOUND_MODE, defaults.unplugSoundMode),
                            SOUND_MODE_OFF),
                    bundle.getString(EXTRA_PLUG_SOUND_NAME, defaults.plugSoundName),
                    bundle.getString(EXTRA_UNPLUG_SOUND_NAME, defaults.unplugSoundName),
                    bundle.getBoolean(EXTRA_PLUG_SOUND_AVAILABLE, defaults.plugSoundAvailable),
                    bundle.getBoolean(EXTRA_UNPLUG_SOUND_AVAILABLE, defaults.unplugSoundAvailable),
                    bundle.getLong(EXTRA_UPDATED_AT, defaults.updatedAt));
        }
    }

    public static final class BatteryValues {
        public int level;
        public int status;
        public int plugged;
        public int chargingType;
        public int voltageMv;
        public int currentMa;
        public int tempTenthsC;
        public long remainingSeconds;

        public static BatteryValues empty() {
            BatteryValues values = new BatteryValues();
            values.level = -1;
            values.status = 1;
            values.plugged = 0;
            values.chargingType = 0;
            values.voltageMv = 0;
            values.currentMa = 0;
            values.tempTenthsC = Integer.MIN_VALUE;
            values.remainingSeconds = 0L;
            return values;
        }
    }
}
