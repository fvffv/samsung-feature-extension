package com.samsung.feature.extension.phone;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;

import com.samsung.feature.extension.LogSettingsProvider;
import com.samsung.feature.extension.TextCallGreetingSettingsProvider;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Replaces only Samsung InCallUI's built-in Text Call greeting strings.  The
 * app resolves all consent/non-consent, dialing, and in-call switch greetings
 * through Resources#getString, so this stays compatible with obfuscated
 * implementation classes and does not alter messages typed during a call.
 */
public final class TextCallGreetingHook implements IXposedHookLoadPackage {
    private static final String TAG = "TextCallGreeting";
    private static final String TARGET_PACKAGE = "com.samsung.android.incallui";

    private static volatile boolean applicationHookInstalled;
    private static volatile boolean resourceHookInstalled;
    private static volatile boolean audioHookInstalled;
    private static volatile Context appContext;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        install("application", new Installer() {
            @Override
            public void install() throws Throwable {
                hookApplicationOnCreate();
            }
        });
        install("greeting resources", new Installer() {
            @Override
            public void install() throws Throwable {
                hookGreetingResources();
            }
        });
        install("receive audio", new Installer() {
            @Override
            public void install() throws Throwable {
                hookTextCallReceiveAudio();
            }
        });
    }

    private static void hookApplicationOnCreate() throws Throwable {
        if (applicationHookInstalled) {
            return;
        }
        Throwable lastFailure = null;
        boolean installed = false;
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            XposedBridge.hookMethod(attach, new ApplicationContextHook());
            installed = true;
        } catch (Throwable throwable) {
            lastFailure = throwable;
        }
        try {
            Method onCreate = Application.class.getDeclaredMethod("onCreate");
            XposedBridge.hookMethod(onCreate, new ApplicationContextHook());
            installed = true;
        } catch (Throwable throwable) {
            lastFailure = throwable;
        }
        if (!installed) {
            throw lastFailure != null ? lastFailure : new IllegalStateException("Application hooks unavailable");
        }
        applicationHookInstalled = true;
    }

    private static void hookGreetingResources() throws Throwable {
        if (resourceHookInstalled) {
            return;
        }
        Method getString = Resources.class.getDeclaredMethod("getString", Integer.TYPE);
        XposedBridge.hookMethod(getString, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Resources)
                        || param.args == null
                        || param.args.length == 0
                        || !(param.args[0] instanceof Integer)) {
                    return;
                }
                String replacement = resolveReplacement(
                        (Resources) param.thisObject,
                        ((Integer) param.args[0]).intValue());
                if (replacement.length() > 0) {
                    param.setResult(replacement);
                }
            }
        });
        resourceHookInstalled = true;
    }

    /**
     * Samsung's Text Call manager mutes received call audio with this exact
     * AudioManager parameter. Rewriting only the mute value preserves text
     * transcription, outgoing TTS, normal calls, and all unrelated routes.
     */
    private static void hookTextCallReceiveAudio() throws Throwable {
        if (audioHookInstalled) {
            return;
        }
        Method setParameters = AudioManager.class.getDeclaredMethod("setParameters", String.class);
        XposedBridge.hookMethod(setParameters, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof String)
                        || !isPlayRemoteAudioEnabled()) {
                    return;
                }
                String parameters = (String) param.args[0];
                if (parameters != null && parameters.contains("l_voice_rx_control_mode=1")) {
                    param.args[0] = parameters.replace(
                            "l_voice_rx_control_mode=1", "l_voice_rx_control_mode=0");
                }
            }
        });
        audioHookInstalled = true;
    }

    private static String resolveReplacement(Resources resources, int resourceId) {
        Context context = appContext;
        if (context == null || resources == null) {
            return "";
        }
        try {
            if (!TARGET_PACKAGE.equals(resources.getResourcePackageName(resourceId))) {
                return "";
            }
            String name = resources.getResourceEntryName(resourceId);
            if (name == null || name.indexOf("text_call_") != 0
                    || name.indexOf("_greeting_") < 0 && name.indexOf("_switch_") < 0) {
                return "";
            }
            TextCallGreetingSettingsProvider.Settings settings =
                    TextCallGreetingSettingsProvider.getSettings(context);
            if (name.indexOf("text_call_com_consent_") == 0) {
                return settings.consentGreeting;
            }
            if (name.indexOf("text_call_non_consent_") == 0) {
                return settings.nonConsentGreeting;
            }
        } catch (Throwable ignored) {
            // Never interfere with the stock call flow.
        }
        return "";
    }

    private static boolean isPlayRemoteAudioEnabled() {
        Context context = appContext;
        return context != null
                && TextCallGreetingSettingsProvider.getSettings(context).playRemoteAudio;
    }

    private static void install(String name, Installer installer) {
        try {
            installer.install();
            log(name + " hook installed");
        } catch (Throwable throwable) {
            log(name + " hook unavailable: " + throwable);
        }
    }

    private static void log(String message) {
        Context context = appContext;
        if (LogSettingsProvider.isLogEnabled(context)) {
            XposedBridge.log(TAG + ": " + message);
        }
    }

    private interface Installer {
        void install() throws Throwable;
    }

    private static final class ApplicationContextHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.thisObject instanceof Application) {
                appContext = (Application) param.thisObject;
            }
        }
    }
}
