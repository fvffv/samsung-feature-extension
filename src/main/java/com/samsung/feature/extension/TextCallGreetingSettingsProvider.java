package com.samsung.feature.extension;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Stores the optional Text Call opening messages and exposes them to the
 * Samsung InCallUI process through a small exported provider.
 */
public final class TextCallGreetingSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.samsung.feature.extension.textcallgreeting";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";
    public static final String METHOD_SET = "set";
    public static final String EXTRA_CONSENT_GREETING = "consentGreeting";
    public static final String EXTRA_NON_CONSENT_GREETING = "nonConsentGreeting";
    public static final String EXTRA_PLAY_REMOTE_AUDIO = "playRemoteAudio";

    private static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    private static final String PREFS = "text_call_greeting";
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final long CACHE_MS = 2000L;

    private static volatile Settings cachedSettings = Settings.empty();
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
            String consent = extras != null && extras.containsKey(EXTRA_CONSENT_GREETING)
                    ? extras.getString(EXTRA_CONSENT_GREETING) : current.consentGreeting;
            String nonConsent = extras != null && extras.containsKey(EXTRA_NON_CONSENT_GREETING)
                    ? extras.getString(EXTRA_NON_CONSENT_GREETING) : current.nonConsentGreeting;
            boolean playRemoteAudio = extras != null && extras.containsKey(EXTRA_PLAY_REMOTE_AUDIO)
                    ? extras.getBoolean(EXTRA_PLAY_REMOTE_AUDIO) : current.playRemoteAudio;
            setSettings(context, consent, nonConsent, playRemoteAudio);
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

    public static void setSettings(Context context, String consentGreeting, String nonConsentGreeting) {
        Settings current = readLocalSettings(context);
        setSettings(context, consentGreeting, nonConsentGreeting, current.playRemoteAudio);
    }

    public static void setSettings(Context context, String consentGreeting, String nonConsentGreeting,
                                   boolean playRemoteAudio) {
        if (context == null) {
            updateCache(Settings.empty(), SystemClock.elapsedRealtime());
            return;
        }
        Settings settings = new Settings(
                sanitizeGreeting(consentGreeting),
                sanitizeGreeting(nonConsentGreeting),
                playRemoteAudio);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(EXTRA_CONSENT_GREETING, settings.consentGreeting)
                .putString(EXTRA_NON_CONSENT_GREETING, settings.nonConsentGreeting)
                .putBoolean(EXTRA_PLAY_REMOTE_AUDIO, settings.playRemoteAudio)
                .apply();
        updateCache(settings, SystemClock.elapsedRealtime());
        try {
            context.getContentResolver().notifyChange(URI, null);
        } catch (Throwable ignored) {
            // Hooked processes also refresh from the short-lived cache.
        }
    }

    private static Settings querySettings(Context context) {
        try {
            if (MODULE_PACKAGE.equals(context.getPackageName())) {
                return readLocalSettings(context);
            }
        } catch (Throwable ignored) {
            // Fall through to the provider call.
        }
        try {
            Bundle result = context.getContentResolver().call(URI, METHOD_GET, null, null);
            if (result != null) {
                return Settings.fromBundle(result);
            }
        } catch (Throwable ignored) {
            // Preserve Samsung's built-in greeting if the module is unavailable.
        }
        return Settings.empty();
    }

    private static Settings readLocalSettings(Context context) {
        if (context == null) {
            return Settings.empty();
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return new Settings(
                    sanitizeGreeting(prefs.getString(EXTRA_CONSENT_GREETING, "")),
                    sanitizeGreeting(prefs.getString(EXTRA_NON_CONSENT_GREETING, "")),
                    prefs.getBoolean(EXTRA_PLAY_REMOTE_AUDIO, false));
        } catch (Throwable ignored) {
            return Settings.empty();
        }
    }

    private static String sanitizeGreeting(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= MAX_MESSAGE_LENGTH ? safe : safe.substring(0, MAX_MESSAGE_LENGTH);
    }

    private static void updateCache(Settings settings, long now) {
        cachedSettings = settings != null ? settings : Settings.empty();
        cacheTimeMs = now;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
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
        public final String consentGreeting;
        public final String nonConsentGreeting;
        public final boolean playRemoteAudio;

        Settings(String consentGreeting, String nonConsentGreeting, boolean playRemoteAudio) {
            this.consentGreeting = consentGreeting == null ? "" : consentGreeting;
            this.nonConsentGreeting = nonConsentGreeting == null ? "" : nonConsentGreeting;
            this.playRemoteAudio = playRemoteAudio;
        }

        Bundle toBundle() {
            Bundle result = new Bundle();
            result.putString(EXTRA_CONSENT_GREETING, consentGreeting);
            result.putString(EXTRA_NON_CONSENT_GREETING, nonConsentGreeting);
            result.putBoolean(EXTRA_PLAY_REMOTE_AUDIO, playRemoteAudio);
            return result;
        }

        static Settings fromBundle(Bundle bundle) {
            if (bundle == null) {
                return empty();
            }
            return new Settings(
                    sanitizeGreeting(bundle.getString(EXTRA_CONSENT_GREETING, "")),
                    sanitizeGreeting(bundle.getString(EXTRA_NON_CONSENT_GREETING, "")),
                    bundle.getBoolean(EXTRA_PLAY_REMOTE_AUDIO, false));
        }

        static Settings empty() {
            return new Settings("", "", false);
        }
    }
}
