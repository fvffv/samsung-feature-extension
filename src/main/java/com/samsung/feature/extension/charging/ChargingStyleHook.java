package com.samsung.feature.extension.charging;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import com.samsung.feature.extension.ChargingStyleSettingsProvider;
import com.samsung.feature.extension.LogSettingsProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ChargingStyleHook implements IXposedHookLoadPackage {
    private static final String TAG = "ChargingStyle";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final long CACHE_MS = 1200L;

    private static volatile Context appContext;
    private static volatile boolean receiverRegistered;
    private static volatile boolean observerRegistered;
    private static volatile long lastReadAt;
    private static volatile ChargingStyleSettingsProvider.SettingsSnapshot cachedSettings =
            ChargingStyleSettingsProvider.SettingsSnapshot.empty();
    private static volatile Handler mainHandler;
    private static volatile boolean keyguardTextHookInstalled;
    private static volatile boolean aodTextHookInstalled;
    private static volatile boolean aodClassLoaderHookInstalled;
    private static final Set<String> hookedAodChargingClasses =
            Collections.synchronizedSet(new HashSet<String>());

    private static final String[] AOD_CHARGING_VIEW_CLASSES = {
            "com.samsung.android.uniform.widget.charginginfo.ChargingInfoView",
            "com.samsung.android.uniform.widget.charginginfo.BottomChargingInfoView",
            "com.samsung.android.uniform.widget.charginginfo.SubUiChargingInfoView",
            "com.samsung.android.uniform.widget.charginginfo.SViewWalletCoverClockChargingInfoView",
            "com.samsung.android.uniform.widget.charginginfo.StripeCoverChargingInfoView"
    };

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !SYSTEMUI_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        try {
            hookApplicationOnCreate();
            hookChargingNotification(lpparam.classLoader);
            hookKeyguardChargingText(lpparam.classLoader);
            hookAodChargingText(lpparam.classLoader);
            hookChargingSound(lpparam.classLoader);
            log("hooks installed for " + lpparam.packageName + ", process=" + lpparam.processName);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": hook failed " + throwable);
            XposedBridge.log(throwable);
        }
    }

    private static void hookApplicationOnCreate() throws Exception {
        Method onCreate = Application.class.getDeclaredMethod("onCreate");
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Application)) {
                    return;
                }
                appContext = ((Application) param.thisObject).getApplicationContext();
                registerSettingsObserver(appContext);
                registerPowerReceiver(appContext);
                lastReadAt = 0L;
                readSettings(appContext);
            }
        });
    }

    private static void hookChargingNotification(ClassLoader classLoader) {
        try {
            Class<?> notificationClass = Class.forName(
                    "com.android.systemui.power.notification.ChargingNotification",
                    false,
                    classLoader);
            Method getTitle = notificationClass.getDeclaredMethod("getTitle");
            XposedBridge.hookMethod(getTitle, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    replaceChargingText(param, true);
                }
            });
            Method getContentText = notificationClass.getDeclaredMethod("getContentText");
            XposedBridge.hookMethod(getContentText, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    replaceChargingText(param, false);
                }
            });
        } catch (Throwable throwable) {
            log("ChargingNotification hook skipped: " + throwable);
        }
    }

    private static void hookKeyguardChargingText(ClassLoader classLoader) {
        Class<?> indicationClass = null;
        try {
            indicationClass = Class.forName(
                    "com.android.systemui.keyguard.KeyguardIndication",
                    false,
                    classLoader);
            Class<?> rotateClass = Class.forName(
                    "com.android.systemui.keyguard.KeyguardIndicationRotateTextViewController",
                    false,
                    classLoader);
            Method updateIndication = rotateClass.getDeclaredMethod(
                    "updateIndication", int.class, indicationClass, boolean.class);
            XposedBridge.hookMethod(updateIndication, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 2
                            || !(param.args[0] instanceof Integer)
                            || param.args[1] == null) {
                        return;
                    }
                    int type = ((Integer) param.args[0]).intValue();
                    if (type != 3) {
                        return;
                    }
                    replaceKeyguardIndication(param.args[1], contextFromViewController(param.thisObject), true);
                }
            });
        } catch (Throwable throwable) {
            log("Keyguard battery indication hook skipped: " + throwable);
        }

        try {
            Class<?> safeIndicationClass = indicationClass != null ? indicationClass : Class.forName(
                    "com.android.systemui.keyguard.KeyguardIndication",
                    false,
                    classLoader);
            Class<?> textViewClass = Class.forName(
                    "com.android.systemui.statusbar.phone.KeyguardIndicationTextView",
                    false,
                    classLoader);
            Method switchIndication = textViewClass.getDeclaredMethod(
                    "switchIndication", CharSequence.class, safeIndicationClass);
            XposedBridge.hookMethod(switchIndication, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 2) {
                        return;
                    }
                    CharSequence text = param.args[0] instanceof CharSequence
                            ? (CharSequence) param.args[0] : null;
                    if (!looksLikeChargingIndication(text)) {
                        return;
                    }
                    Context context = param.thisObject instanceof View
                            ? ((View) param.thisObject).getContext() : appContext;
                    String replacement = replacementForKeyguard(context, text);
                    if (replacement.length() == 0 || replacement.contentEquals(text)) {
                        return;
                    }
                    param.args[0] = replacement;
                    if (param.args[1] != null) {
                        setIndicationMessage(param.args[1], replacement);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("Keyguard text fallback hook skipped: " + throwable);
        }
    }

    private static void hookAodChargingText(ClassLoader classLoader) {
        for (String className : AOD_CHARGING_VIEW_CLASSES) {
            try {
                hookAodChargingInfoClass(Class.forName(className, false, classLoader));
            } catch (Throwable ignored) {
                // On current One UI builds these classes can arrive through a plugin class loader.
            }
        }

        try {
            hookTextViewSetTextForCharging(true);
        } catch (Throwable throwable) {
            log("AOD TextView setText hook skipped: " + throwable);
        }

        try {
            hookAodChargingClassLoading();
        } catch (Throwable throwable) {
            log("AOD class loader hook skipped: " + throwable);
        }
    }

    private static void hookAodChargingClassLoading() throws Exception {
        if (aodClassLoaderHookInstalled) {
            return;
        }
        aodClassLoaderHookInstalled = true;
        Method loadClass = ClassLoader.class.getDeclaredMethod(
                "loadClass",
                String.class,
                boolean.class);
        XposedBridge.hookMethod(loadClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof String)
                        || !(param.getResult() instanceof Class)) {
                    return;
                }
                String className = (String) param.args[0];
                if (!isAodChargingViewClass(className)) {
                    return;
                }
                hookAodChargingInfoClass((Class<?>) param.getResult());
            }
        });
    }

    private static boolean isAodChargingViewClass(String className) {
        for (String candidate : AOD_CHARGING_VIEW_CLASSES) {
            if (candidate.equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static void hookAodChargingInfoClass(Class<?> viewClass) {
        if (viewClass == null) {
            return;
        }
        String className = viewClass.getName();
        if (!hookedAodChargingClasses.add(className)) {
            return;
        }
        boolean hooked = false;
        try {
            Method[] methods = viewClass.getDeclaredMethods();
            for (Method method : methods) {
                if (!"j".equals(method.getName())
                        || method.getParameterTypes().length != 1
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        refreshAodChargingInfoView(param.thisObject, "updateContents");
                    }
                });
                hooked = true;
            }
        } catch (Throwable throwable) {
            hookedAodChargingClasses.remove(className);
            log("AOD charging view hook failed for " + className + ": " + throwable);
            return;
        }
        if (hooked) {
            log("AOD charging view hook installed for " + className);
        }
    }

    private static void hookChargingSound(ClassLoader classLoader) {
        try {
            Class<?> soundPathFinder = Class.forName(
                    "com.android.systemui.power.sound.SoundPathFinder",
                    false,
                    classLoader);
            Method getSoundPath = soundPathFinder.getDeclaredMethod("getSoundPath", int.class, Context.class);
            XposedBridge.hookMethod(getSoundPath, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 2
                            || !(param.args[0] instanceof Integer)
                            || !(param.args[1] instanceof Context)) {
                        return;
                    }
                    int soundType = ((Integer) param.args[0]).intValue();
                    if (soundType != 1 && soundType != 2) {
                        return;
                    }
                    Context context = (Context) param.args[1];
                    ChargingStyleSettingsProvider.SettingsSnapshot settings = readSettings(context);
                    if (settings.plugSoundMode != ChargingStyleSettingsProvider.SOUND_MODE_CUSTOM
                            || !settings.plugSoundAvailable) {
                        return;
                    }
                    String uri = ChargingStyleSettingsProvider.customSoundUriString(
                            settings, ChargingStyleSettingsProvider.SOUND_PLUG);
                    if (uri.length() > 0) {
                        param.setResult(uri);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("SoundPathFinder hook skipped: " + throwable);
        }

        try {
            Class<?> chargingSound = Class.forName(
                    "com.android.systemui.power.sound.ChargingSound",
                    false,
                    classLoader);
            Method playSoundAndVibration = chargingSound.getDeclaredMethod("playSoundAndVibration");
            XposedBridge.hookMethod(playSoundAndVibration, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Context context = contextFromObject(param.thisObject);
                    ChargingStyleSettingsProvider.SettingsSnapshot settings = readSettings(context);
                    if (settings.plugSoundMode == ChargingStyleSettingsProvider.SOUND_MODE_OFF) {
                        log("skip plug sound by user setting");
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("ChargingSound hook skipped: " + throwable);
        }
    }

    private static void replaceKeyguardIndication(Object indication, Context context, boolean batteryTypeOnly) {
        if (indication == null) {
            return;
        }
        CharSequence original = getIndicationMessage(indication);
        if (original == null || original.length() == 0) {
            return;
        }
        if (!batteryTypeOnly && !looksLikeChargingIndication(original)) {
            return;
        }
        String replacement = replacementForKeyguard(context, original);
        if (replacement.length() == 0 || replacement.contentEquals(original)) {
            return;
        }
        setIndicationMessage(indication, replacement);
    }

    private static String replacementForKeyguard(Context context, CharSequence original) {
        return replacementForChargingDisplay(context, original, false);
    }

    private static String replacementForChargingDisplay(Context context, CharSequence original, boolean title) {
        if (title) {
            return "";
        }
        ChargingStyleSettingsProvider.SettingsSnapshot settings = readSettings(context);
        if (!settings.displayEnabled) {
            return "";
        }
        String template = settings.contentTemplate;
        if (template == null || template.trim().length() == 0) {
            return "";
        }
        ChargingStyleSettingsProvider.BatteryValues values =
                ChargingStyleSettingsProvider.currentBatteryValues(context != null ? context : appContext);
        long parsedSeconds = parseRemainingSeconds(original);
        if (values.remainingSeconds <= 0L && parsedSeconds > 0L) {
            values.remainingSeconds = parsedSeconds;
        }
        String systemText = original != null ? original.toString() : "";
        return ChargingStyleSettingsProvider.formatTemplate(template, values, systemText);
    }

    private static void hookTextViewSetTextForCharging(final boolean aodServiceProcess) throws Exception {
        if (aodServiceProcess) {
            if (aodTextHookInstalled) {
                return;
            }
            aodTextHookInstalled = true;
        } else {
            if (keyguardTextHookInstalled) {
                return;
            }
            keyguardTextHookInstalled = true;
        }
        Method setText = TextView.class.getDeclaredMethod(
                "setText",
                CharSequence.class,
                TextView.BufferType.class);
        XposedBridge.hookMethod(setText, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0
                        || !(param.thisObject instanceof TextView)) {
                    return;
                }
                TextView textView = (TextView) param.thisObject;
                String entryName = resourceEntryName(textView);
                if (entryName.length() == 0) {
                    return;
                }
                boolean titleTarget;
                if (aodServiceProcess) {
                    if ("common_charging_info_remaining_time_text_view".equals(entryName)) {
                        titleTarget = false;
                    } else {
                        return;
                    }
                } else {
                    if (!"keyguard_indication_text".equals(entryName)) {
                        return;
                    }
                    titleTarget = false;
                }
                CharSequence original = param.args[0] instanceof CharSequence
                        ? (CharSequence) param.args[0] : null;
                if (original == null || original.length() == 0) {
                    return;
                }
                if (!aodServiceProcess && !looksLikeChargingIndication(original)) {
                    return;
                }
                String replacement = replacementForChargingDisplay(
                        textView.getContext(),
                        original,
                        titleTarget);
                if (replacement.length() == 0 || replacement.contentEquals(original)) {
                    return;
                }
                param.args[0] = replacement;
                log((aodServiceProcess ? "AOD" : "SystemUI AOD")
                        + " charging text replaced: " + entryName);
            }
        });
    }

    private static void refreshAodChargingInfoView(Object view, String reason) {
        if (view == null) {
            return;
        }
        try {
            Object contentView = XposedHelpers.getObjectField(view, "k");
            boolean changed = replaceAodTextView(contentView, false);
            if (changed) {
                log("AOD charging info refreshed by " + reason);
            }
        } catch (Throwable throwable) {
            log("refresh AOD charging info failed: " + throwable);
        }
    }

    private static boolean replaceAodTextView(Object object, boolean title) {
        if (!(object instanceof TextView)) {
            return false;
        }
        TextView textView = (TextView) object;
        CharSequence original = textView.getText();
        if (original == null || original.length() == 0) {
            return false;
        }
        String replacement = replacementForChargingDisplay(textView.getContext(), original, title);
        if (replacement.length() == 0 || replacement.contentEquals(original)) {
            return false;
        }
        textView.setText(replacement);
        return true;
    }

    private static void replaceChargingText(XC_MethodHook.MethodHookParam param, boolean title) {
        if (title) {
            return;
        }
        Context context = contextFromObject(param.thisObject);
        ChargingStyleSettingsProvider.SettingsSnapshot settings = readSettings(context);
        if (!settings.displayEnabled) {
            return;
        }
        String template = settings.contentTemplate;
        if (template == null || template.trim().length() == 0) {
            return;
        }
        int level = getIntField(param.thisObject, "mBatteryLevel", -1);
        long chargingTime = getLongField(param.thisObject, "mChargingTime", 0L);
        int chargingType = getIntField(param.thisObject, "mChargingType", 0);
        ChargingStyleSettingsProvider.BatteryValues values =
                ChargingStyleSettingsProvider.notificationValues(context, level, chargingTime, chargingType);
        String systemText = param.getResult() instanceof String ? (String) param.getResult() : "";
        String replacement = ChargingStyleSettingsProvider.formatTemplate(template, values, systemText);
        if (replacement.length() > 0) {
            param.setResult(replacement);
        }
    }

    private static void registerSettingsObserver(Context context) {
        if (context == null || observerRegistered) {
            return;
        }
        try {
            observerRegistered = true;
            context.getContentResolver().registerContentObserver(
                    ChargingStyleSettingsProvider.URI,
                    true,
                    new ContentObserver(mainHandler()) {
                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            lastReadAt = 0L;
                        }
                    });
        } catch (Throwable throwable) {
            observerRegistered = false;
            log("settings observer failed: " + throwable);
        }
    }

    private static void registerPowerReceiver(final Context context) {
        if (context == null || receiverRegistered) {
            return;
        }
        try {
            receiverRegistered = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ignored, Intent intent) {
                    if (intent == null || !Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                        return;
                    }
                    mainHandler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            playUnplugSound(context);
                        }
                    }, 120L);
                }
            }, filter);
        } catch (Throwable throwable) {
            receiverRegistered = false;
            log("power receiver failed: " + throwable);
        }
    }

    private static void playUnplugSound(Context context) {
        ChargingStyleSettingsProvider.SettingsSnapshot settings = readSettings(context);
        if (settings.unplugSoundMode == ChargingStyleSettingsProvider.SOUND_MODE_OFF) {
            return;
        }
        if (!isChargingSoundEnabled(context)) {
            log("skip unplug sound because charging sound setting is disabled");
            return;
        }
        String source;
        if (settings.unplugSoundMode == ChargingStyleSettingsProvider.SOUND_MODE_CUSTOM
                && settings.unplugSoundAvailable) {
            source = ChargingStyleSettingsProvider.customSoundUriString(
                    settings, ChargingStyleSettingsProvider.SOUND_UNPLUG);
        } else {
            source = ChargingStyleSettingsProvider.currentSystemUnplugSound(context);
        }
        playSound(context, source);
    }

    private static void playSound(final Context context, String source) {
        if (context == null || source == null || source.trim().length() == 0) {
            return;
        }
        MediaPlayer player = null;
        try {
            player = new MediaPlayer();
            final MediaPlayer finalPlayer = player;
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            String trimmed = source.trim();
            if (trimmed.startsWith("/")) {
                player.setDataSource(trimmed);
            } else {
                player.setDataSource(context, Uri.parse(trimmed));
            }
            player.setOnCompletionListener(mp -> releaseQuietly(finalPlayer));
            player.setOnErrorListener((mp, what, extra) -> {
                releaseQuietly(finalPlayer);
                return true;
            });
            player.prepare();
            player.start();
            log("played unplug sound " + trimmed);
        } catch (Throwable throwable) {
            releaseQuietly(player);
            log("play sound failed: " + throwable);
        }
    }

    private static boolean isChargingSoundEnabled(Context context) {
        try {
            return Settings.Secure.getInt(
                    context.getContentResolver(), "charging_sounds_enabled", 1) == 1;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void releaseQuietly(MediaPlayer player) {
        if (player == null) {
            return;
        }
        try {
            player.release();
        } catch (Throwable ignored) {
            // Ignore.
        }
    }

    private static Handler mainHandler() {
        Handler handler = mainHandler;
        if (handler != null) {
            return handler;
        }
        Looper looper = Looper.getMainLooper();
        if (looper == null) {
            looper = Looper.myLooper();
        }
        if (looper == null) {
            throw new IllegalStateException("No looper available");
        }
        handler = new Handler(looper);
        mainHandler = handler;
        return handler;
    }

    private static ChargingStyleSettingsProvider.SettingsSnapshot readSettings(Context context) {
        long now = SystemClock.elapsedRealtime();
        ChargingStyleSettingsProvider.SettingsSnapshot settings = cachedSettings;
        if (settings != null && now - lastReadAt < CACHE_MS) {
            return settings;
        }
        settings = ChargingStyleSettingsProvider.getSettings(context != null ? context : appContext);
        cachedSettings = settings;
        lastReadAt = now;
        return settings;
    }

    private static Context contextFromObject(Object object) {
        try {
            Object context = XposedHelpers.getObjectField(object, "mContext");
            if (context instanceof Context) {
                return (Context) context;
            }
        } catch (Throwable ignored) {
            // Try the cached application context below.
        }
        return appContext;
    }

    private static Context contextFromViewController(Object object) {
        try {
            Object view = XposedHelpers.getObjectField(object, "mView");
            if (view instanceof View) {
                return ((View) view).getContext();
            }
        } catch (Throwable ignored) {
            // Use cached context below.
        }
        return appContext;
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) {
            return "";
        }
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static CharSequence getIndicationMessage(Object indication) {
        try {
            Object value = XposedHelpers.getObjectField(indication, "mMessage");
            return value instanceof CharSequence ? (CharSequence) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setIndicationMessage(Object indication, CharSequence message) {
        try {
            XposedHelpers.setObjectField(indication, "mMessage", message);
        } catch (Throwable throwable) {
            log("set keyguard indication message failed: " + throwable);
        }
    }

    private static boolean looksLikeChargingIndication(CharSequence text) {
        if (text == null) {
            return false;
        }
        String value = text.toString();
        return value.contains("\u5145\u6ee1\u7535")
                || value.contains("\u5145\u6eff\u96fb")
                || value.contains("\u5145\u7535")
                || value.contains("\u5145\u96fb")
                || value.toLowerCase().contains("charging");
    }

    private static long parseRemainingSeconds(CharSequence text) {
        if (text == null) {
            return 0L;
        }
        String value = text.toString();
        int hours = Math.max(
                numberBefore(value, "\u5c0f\u65f6"),
                numberBefore(value, "\u5c0f\u6642"));
        int minutes = Math.max(
                numberBefore(value, "\u5206\u949f"),
                numberBefore(value, "\u5206\u9418"));
        if (hours < 0 && minutes < 0) {
            return 0L;
        }
        long totalMinutes = Math.max(0, hours) * 60L + Math.max(0, minutes);
        return Math.max(1L, totalMinutes) * 60L;
    }

    private static int numberBefore(String value, String marker) {
        int markerIndex = value.indexOf(marker);
        if (markerIndex < 0) {
            return -1;
        }
        int end = markerIndex;
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && Character.isDigit(value.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return -1;
        }
        try {
            return Integer.parseInt(value.substring(start, end));
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static int getIntField(Object object, String name, int fallback) {
        try {
            return XposedHelpers.getIntField(object, name);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static long getLongField(Object object, String name, long fallback) {
        try {
            return XposedHelpers.getLongField(object, name);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void log(String message) {
        try {
            Context context = appContext;
            if (context != null && !LogSettingsProvider.isLogEnabled(context)) {
                return;
            }
        } catch (Throwable ignored) {
            // Keep hook diagnostics best-effort.
        }
        XposedBridge.log(TAG + ": " + message);
    }
}
