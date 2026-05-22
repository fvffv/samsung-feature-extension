package com.samsung.feature.extension.fingerprint;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;

import com.samsung.feature.extension.FingerprintStyleSettingsProvider;
import com.samsung.feature.extension.LogSettingsProvider;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class FingerprintStyleHook implements IXposedHookLoadPackage {
    private static final String TAG = "FingerprintStyle";
    private static final String TARGET_PACKAGE = "com.samsung.android.biometrics.app.setting";
    private static final long CACHE_MS = 1500L;

    private static volatile Context appContext;
    private static volatile boolean observerRegistered;
    private static volatile long lastReadAt;
    private static volatile FingerprintStyleSettingsProvider.Settings cachedSettings =
            FingerprintStyleSettingsProvider.Settings.empty();
    private static final Map<Object, Boolean> customIconViews =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        try {
            hookApplicationOnCreate();
            hookLottieAnimationView(lpparam.classLoader);
            hookUdfpsColorFilter(lpparam.classLoader);
            hookImageResource();
            hookImageViewColorFilter();
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
                lastReadAt = 0L;
                readSettings(appContext);
            }
        });
    }

    private static void hookUdfpsColorFilter(ClassLoader classLoader) {
        try {
            Class<?> sensorWindowClass = Class.forName(
                    "com.samsung.android.biometrics.app.setting.fingerprint.UdfpsSensorWindow",
                    false,
                    classLoader);
            Method method = sensorWindowClass.getDeclaredMethod("setLottieViewColorFilter", int.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    FingerprintStyleSettingsProvider.Settings settings = readSettings(appContext);
                    if (settings.enabled && settings.available) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("UDFPS color filter hook skipped: " + throwable);
        }
    }

    private static void hookImageViewColorFilter() {
        hookColorFilterMethod("setColorFilter", int.class);
        hookColorFilterMethod("setColorFilter", ColorFilter.class);
        hookColorFilterMethod("setColorFilter", int.class, PorterDuff.Mode.class);
    }

    private static void hookImageResource() {
        try {
            Method method = ImageView.class.getMethod("setImageResource", int.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof ImageView)
                            || param.args == null
                            || param.args.length == 0
                            || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    ImageView imageView = (ImageView) param.thisObject;
                    Context context = imageView.getContext();
                    String resourceName = resourceEntryName(context, ((Integer) param.args[0]).intValue());
                    if (!isFingerprintImageResource(resourceName)) {
                        return;
                    }
                    FingerprintStyleSettingsProvider.Settings settings = readSettings(context);
                    if (!settings.enabled || !settings.available) {
                        return;
                    }
                    if (applyReplacement(imageView, context, settings, "drawable:" + resourceName)) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("ImageView resource hook skipped: " + throwable);
        }
    }

    private static void hookColorFilterMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = ImageView.class.getMethod(name, parameterTypes);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!isCustomIconView(param.thisObject)) {
                        return;
                    }
                    FingerprintStyleSettingsProvider.Settings settings = readSettings(appContext);
                    if (settings.enabled && settings.available && hasNonNullArgument(param.args)) {
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("ImageView color filter hook skipped: " + name + " " + throwable);
        }
    }

    private static void hookLottieAnimationView(ClassLoader classLoader) throws Exception {
        Class<?> lottieClass = Class.forName("com.airbnb.lottie.LottieAnimationView", false, classLoader);
        Method setAnimation = lottieClass.getDeclaredMethod("setAnimation", String.class);
        XposedBridge.hookMethod(setAnimation, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof String)) {
                    return;
                }
                String assetName = (String) param.args[0];
                if (!isFingerprintIconAsset(assetName) || !(param.thisObject instanceof View)) {
                    return;
                }
                Context context = ((View) param.thisObject).getContext();
                FingerprintStyleSettingsProvider.Settings settings = readSettings(context);
                if (!settings.enabled || !settings.available) {
                    return;
                }
                if (applyReplacement(param.thisObject, context, settings, assetName)) {
                    param.setResult(null);
                }
            }
        });
        Method setProgress = lottieClass.getDeclaredMethod("setProgress", float.class);
        XposedBridge.hookMethod(setProgress, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isCustomIconView(param.thisObject)
                        || param.args == null
                        || param.args.length == 0
                        || !(param.args[0] instanceof Float)) {
                    return;
                }
                FingerprintStyleSettingsProvider.Settings settings = readSettings(appContext);
                float progress = ((Float) param.args[0]).floatValue();
                if (settings.enabled && settings.available && progress >= 0.99f) {
                    param.setResult(null);
                }
            }
        });
    }

    private static void registerSettingsObserver(Context context) {
        if (context == null || observerRegistered) {
            return;
        }
        try {
            observerRegistered = true;
            context.getContentResolver().registerContentObserver(
                    FingerprintStyleSettingsProvider.URI,
                    true,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            lastReadAt = 0L;
                        }
                    }
            );
        } catch (Throwable throwable) {
            observerRegistered = false;
            log("observer failed: " + throwable);
        }
    }

    private static FingerprintStyleSettingsProvider.Settings readSettings(Context context) {
        long now = SystemClock.elapsedRealtime();
        FingerprintStyleSettingsProvider.Settings settings = cachedSettings;
        if (settings != null && now - lastReadAt < CACHE_MS) {
            return settings;
        }
        settings = FingerprintStyleSettingsProvider.getSettings(context != null ? context : appContext);
        cachedSettings = settings;
        lastReadAt = now;
        return settings;
    }

    private static boolean applyReplacement(Object view, Context context,
                                            FingerprintStyleSettingsProvider.Settings settings,
                                            String assetName) {
        if (FingerprintStyleSettingsProvider.TYPE_PNG.equals(settings.type)) {
            return applyPng(view, context, settings, assetName);
        }
        return applyAnimation(view, context, settings, assetName);
    }

    private static boolean applyAnimation(Object view, Context context,
                                          FingerprintStyleSettingsProvider.Settings settings,
                                          String assetName) {
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(
                    FingerprintStyleSettingsProvider.fileUri(FingerprintStyleSettingsProvider.TYPE_ANIMATION));
            if (input == null) {
                return false;
            }
            removeCompositionListeners(view);
            Method method = view.getClass().getMethod("setAnimation", InputStream.class, String.class);
            method.invoke(view, input, "sfe_fingerprint_icon_" + settings.updatedAt);
            markCustomIconView(view);
            clearViewColorFilter(view);
            playCustomAnimation(view, settings.loop);
            log("applied custom animation for " + assetName);
            return true;
        } catch (Throwable throwable) {
            closeQuietly(input);
            log("animation replacement failed: " + throwable);
            return false;
        }
    }

    private static boolean applyPng(Object view, Context context,
                                    FingerprintStyleSettingsProvider.Settings settings,
                                    String assetName) {
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(
                    FingerprintStyleSettingsProvider.fileUri(FingerprintStyleSettingsProvider.TYPE_PNG));
            Bitmap bitmap = input != null ? BitmapFactory.decodeStream(input) : null;
            if (bitmap == null || !(view instanceof ImageView)) {
                return false;
            }
            try {
                view.getClass().getMethod("cancelAnimation").invoke(view);
            } catch (Throwable ignored) {
            }
            ImageView imageView = (ImageView) view;
            markCustomIconView(view);
            clearViewColorFilter(view);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setImageBitmap(bitmap);
            imageView.invalidate();
            log("applied custom png for " + assetName + ", updatedAt=" + settings.updatedAt);
            return true;
        } catch (Throwable throwable) {
            log("png replacement failed: " + throwable);
            return false;
        } finally {
            closeQuietly(input);
        }
    }

    private static boolean isFingerprintIconAsset(String name) {
        return "ic_fingerprint_prompt.json".equals(name)
                || "ic_fingerprint_aod.json".equals(name)
                || "ic_fingerprint_dark_theme.json".equals(name)
                || "ic_fingerprint_dark_theme_non_alpha.json".equals(name)
                || "ic_fingerprint_light_theme.json".equals(name)
                || "ic_fingerprint_light_theme_non_alpha.json".equals(name)
                || "prompt_btn_fingerprint.json".equals(name);
    }

    private static boolean isFingerprintImageResource(String name) {
        return "sem_fingerprint_icon_bg_white".equals(name)
                || "sem_fingerprint_icon_bg_black".equals(name)
                || "sem_fingerprint_icon_bg".equals(name)
                || "sem_biometric_prompt_dialog_fingerprint".equals(name);
    }

    private static String resourceEntryName(Context context, int resId) {
        if (context == null || resId == 0) {
            return "";
        }
        try {
            return context.getResources().getResourceEntryName(resId);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean hasNonNullArgument(Object[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        for (Object arg : args) {
            if (arg != null) {
                return true;
            }
        }
        return false;
    }

    private static void markCustomIconView(Object view) {
        if (view != null) {
            customIconViews.put(view, Boolean.TRUE);
        }
    }

    private static boolean isCustomIconView(Object view) {
        return view != null && customIconViews.containsKey(view);
    }

    private static void removeCompositionListeners(Object view) {
        try {
            view.getClass().getMethod("removeAllLottieOnCompositionLoadedListener").invoke(view);
        } catch (Throwable ignored) {
        }
    }

    private static void clearViewColorFilter(Object view) {
        if (!(view instanceof ImageView)) {
            return;
        }
        try {
            ((ImageView) view).setColorFilter((ColorFilter) null);
        } catch (Throwable ignored) {
        }
    }

    private static void playCustomAnimation(Object view, boolean loop) {
        try {
            view.getClass().getMethod("setProgress", float.class).invoke(view, Float.valueOf(0f));
        } catch (Throwable ignored) {
        }
        try {
            view.getClass().getMethod("setRepeatCount", int.class).invoke(view, Integer.valueOf(loop ? -1 : 0));
        } catch (Throwable ignored) {
        }
        try {
            view.getClass().getMethod("playAnimation").invoke(view);
        } catch (Throwable ignored) {
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void log(String message) {
        Context context = appContext;
        boolean enabled = true;
        try {
            if (context != null) {
                enabled = LogSettingsProvider.isLogEnabled(context);
            }
        } catch (Throwable ignored) {
            enabled = false;
        }
        if (enabled) {
            XposedBridge.log(TAG + ": " + message);
        }
    }
}
