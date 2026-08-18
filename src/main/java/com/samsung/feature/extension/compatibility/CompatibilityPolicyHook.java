package com.samsung.feature.extension.compatibility;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.samsung.feature.extension.CompatibilityPolicySettingsProvider;
import com.samsung.feature.extension.LogSettingsProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Keeps the global developer compatibility-policy value at 1 when requested. */
public final class CompatibilityPolicyHook implements IXposedHookLoadPackage {
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SYSTEM_PACKAGE = "android";
    private static final String GLOBAL_KEY = "package_policy_disabled";
    private static final Uri CONTROL_URI = CompatibilityPolicySettingsProvider.URI;
    private static final String METHOD_GET = CompatibilityPolicySettingsProvider.METHOD_GET;
    private static final long KEEPALIVE_MS = 5000L;

    private static volatile Context settingsContext;
    private static volatile boolean observerRegistered;
    private static volatile Handler keepAliveHandler;
    private static volatile ContentResolver lastResolver;
    private static volatile boolean globalHooksInstalled;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null) {
            return;
        }
        if (SETTINGS_PACKAGE.equals(lpparam.packageName)) {
            hookSettingsApplication();
            hookGlobalSettingsMethods();
        } else if (SYSTEM_PACKAGE.equals(lpparam.packageName)) {
            hookGlobalSettingsMethods();
        }
    }

    private static void hookSettingsApplication() throws Exception {
        Method onCreate = Application.class.getDeclaredMethod("onCreate");
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Application)) {
                    return;
                }
                settingsContext = ((Application) param.thisObject).getApplicationContext();
                registerObserver(settingsContext);
                enforce(settingsContext.getContentResolver(), "settings-start");
            }
        });
    }

    private static void registerObserver(final Context context) {
        if (context == null || observerRegistered) {
            return;
        }
        try {
            observerRegistered = true;
            context.getContentResolver().registerContentObserver(CONTROL_URI, false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            enforce(context.getContentResolver(), "module-setting-changed");
                        }
                    });
        } catch (Throwable throwable) {
            observerRegistered = false;
            log("observer failed: " + throwable);
        }
    }

    private static void hookGlobalSettingsMethods() {
        if (globalHooksInstalled) {
            return;
        }
        synchronized (CompatibilityPolicyHook.class) {
            if (globalHooksInstalled) {
                return;
            }
            try {
                Method[] methods = Settings.Global.class.getDeclaredMethods();
                for (int i = 0; i < methods.length; i++) {
                    Method method = methods[i];
                    if (!Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    Class<?>[] types = method.getParameterTypes();
                    if ("putInt".equals(method.getName()) && matches(types,
                            ContentResolver.class, String.class, Integer.TYPE)) {
                        hookPut(method, 2, false);
                    } else if ("putLong".equals(method.getName()) && matches(types,
                            ContentResolver.class, String.class, Long.TYPE)) {
                        hookPut(method, 2, true);
                    } else if ("putString".equals(method.getName()) && matches(types,
                            ContentResolver.class, String.class, String.class)) {
                        hookPut(method, 2, false);
                    } else if ("getInt".equals(method.getName())
                            && (matches(types, ContentResolver.class, String.class)
                            || matches(types, ContentResolver.class, String.class, Integer.TYPE))) {
                        hookGet(method);
                    } else if ("getLong".equals(method.getName())
                            && (matches(types, ContentResolver.class, String.class)
                            || matches(types, ContentResolver.class, String.class, Long.TYPE))) {
                        hookGet(method);
                    }
                }
                globalHooksInstalled = true;
                log("global compatibility-policy hooks installed");
            } catch (Throwable throwable) {
                log("global settings hooks failed: " + throwable);
            }
        }
    }

    private static boolean matches(Class<?>[] actual, Class<?>... expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static void hookPut(Method method, final int valueIndex, final boolean longValue) {
        method.setAccessible(true);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                ContentResolver resolver = resolver(param.args);
                if (resolver == null || !GLOBAL_KEY.equals(param.args[1])) {
                    return;
                }
                lastResolver = resolver;
                startKeepAlive(resolver);
                if (!isEnabled(resolver)) {
                    return;
                }
                param.args[valueIndex] = longValue ? Long.valueOf(1L) :
                        (param.args[valueIndex] instanceof String ? "1" : Integer.valueOf(1));
            }
        });
    }

    private static void hookGet(Method method) {
        method.setAccessible(true);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                ContentResolver resolver = resolver(param.args);
                if (resolver == null || param.args.length < 2
                        || !GLOBAL_KEY.equals(param.args[1])) {
                    return;
                }
                lastResolver = resolver;
                startKeepAlive(resolver);
                if (isEnabled(resolver)) {
                    Object result = param.getResult();
                    if (result instanceof Long) {
                        param.setResult(Long.valueOf(1L));
                    } else {
                        param.setResult(Integer.valueOf(1));
                    }
                }
            }
        });
    }

    private static ContentResolver resolver(Object[] args) {
        return args != null && args.length > 0 && args[0] instanceof ContentResolver
                ? (ContentResolver) args[0] : null;
    }

    private static void startKeepAlive(final ContentResolver resolver) {
        lastResolver = resolver;
        Handler handler = keepAliveHandler;
        if (handler == null) {
            synchronized (CompatibilityPolicyHook.class) {
                handler = keepAliveHandler;
                if (handler == null) {
                    try {
                        handler = new Handler(Looper.getMainLooper());
                        keepAliveHandler = handler;
                    } catch (Throwable throwable) {
                        log("keepalive handler failed: " + throwable);
                        return;
                    }
                }
            }
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isEnabled(resolver)) {
                    enforce(resolver, "keepalive");
                    startKeepAlive(resolver);
                }
            }
        }, KEEPALIVE_MS);
    }

    private static void enforce(ContentResolver resolver, String reason) {
        if (resolver == null) {
            return;
        }
        boolean enabled = isEnabled(resolver);
        try {
            Settings.Global.putInt(resolver, GLOBAL_KEY, enabled ? 1 : 0);
            log("package_policy_disabled=" + (enabled ? 1 : 0) + ", reason=" + reason);
        } catch (Throwable throwable) {
            log("enforce failed, reason=" + reason + ": " + throwable);
        }
        if (enabled) {
            startKeepAlive(resolver);
        }
    }

    private static boolean isEnabled(ContentResolver resolver) {
        try {
            Bundle result = resolver.call(CONTROL_URI, METHOD_GET, null, null);
            return result != null && result.getBoolean(
                    CompatibilityPolicySettingsProvider.EXTRA_ENABLED, false);
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static void log(String message) {
        Context context = settingsContext;
        if (context == null || !LogSettingsProvider.isLogEnabled(context)) {
            return;
        }
        XposedBridge.log("CompatibilityPolicy: " + message);
    }
}
