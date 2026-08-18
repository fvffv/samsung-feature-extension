package com.samsung.feature.extension.audioeraser;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Enables SystemUI's native, playback-aware Audio Eraser banner. */
public final class SystemUiAudioEraserHook implements IXposedHookLoadPackage {
    private static final String TAG = "SFE-SystemUiAudioEraser";
    private static final String TARGET_PACKAGE = "com.android.systemui";
    private static final String FLOATING_FEATURE_CLASS =
            "com.samsung.android.feature.SemFloatingFeature";
    private static final String AUDIO_CONFIG_KEY =
            "SEC_FLOATING_FEATURE_AUDIO_CONFIG_MULTISOURCE_SEPARATOR";
    private static final String AI_VERSION_KEY =
            "SEC_FLOATING_FEATURE_COMMON_CONFIG_AI_VERSION";
    private static final String S24_ULTRA_AUDIO_CONFIG =
            "{FastScanning_6, SourceSeparator_4, Version_1.3.0}";
    private static final int MINIMUM_AI_VERSION = 20261;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        if (!AudioEraserDeviceSupport.shouldHookCurrentDevice()) {
            log("native or unsupported device; SystemUI Audio Eraser hook skipped");
            return;
        }

        try {
            Class<?> featureClass = Class.forName(
                    FLOATING_FEATURE_CLASS, false, lpparam.classLoader);
            int stringHooks = hookAudioConfiguration(featureClass);
            int integerHooks = hookAiVersion(featureClass);
            if (stringHooks == 0 || integerHooks == 0) {
                throw new NoSuchMethodException(
                        "SemFloatingFeature audio config hooks=" + stringHooks
                                + ", AI version hooks=" + integerHooks);
            }
            log("enabled playback-aware quick-panel Audio Eraser; stringHooks="
                    + stringHooks + ", integerHooks=" + integerHooks);
        } catch (Throwable throwable) {
            log("SystemUI Audio Eraser hook failed: " + throwable);
            XposedBridge.log(throwable);
        }
    }

    private static int hookAudioConfiguration(Class<?> featureClass) {
        Method[] methods = featureClass.getDeclaredMethods();
        int hooked = 0;
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] parameters = method.getParameterTypes();
            if (!"getString".equals(method.getName())
                    || method.getReturnType() != String.class
                    || parameters.length == 0
                    || parameters[0] != String.class) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!hasKey(param, AUDIO_CONFIG_KEY)) {
                        return;
                    }
                    Object result = param.getResult();
                    if (!(result instanceof String)
                            || ((String) result).trim().length() == 0) {
                        param.setResult(S24_ULTRA_AUDIO_CONFIG);
                    }
                }
            });
            hooked++;
        }
        return hooked;
    }

    private static int hookAiVersion(Class<?> featureClass) {
        Method[] methods = featureClass.getDeclaredMethods();
        int hooked = 0;
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] parameters = method.getParameterTypes();
            if (!("getInt".equals(method.getName())
                    || "getInteger".equals(method.getName()))
                    || method.getReturnType() != Integer.TYPE
                    || parameters.length == 0
                    || parameters[0] != String.class) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!hasKey(param, AI_VERSION_KEY)) {
                        return;
                    }
                    Object result = param.getResult();
                    int version = result instanceof Number
                            ? ((Number) result).intValue() : -1;
                    if (version < MINIMUM_AI_VERSION) {
                        param.setResult(Integer.valueOf(MINIMUM_AI_VERSION));
                    }
                }
            });
            hooked++;
        }
        return hooked;
    }

    private static boolean hasKey(XC_MethodHook.MethodHookParam param, String expected) {
        return param.args != null
                && param.args.length != 0
                && expected.equals(param.args[0]);
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
