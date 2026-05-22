package com.samsung.feature.extension.nfcscreenofftap;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.samsung.feature.extension.LogSettingsProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class NfcScreenOffTapHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.android.nfc";
    private static final String AUTHORITY = "com.samsung.feature.extension.nfcsettings";
    private static final Uri SETTINGS_URI = Uri.parse("content://" + AUTHORITY);
    private static final String METHOD_GET = "get";
    private static final String EXTRA_ENABLED = "enabled";
    private static final String EXTRA_MODE = "mode";
    private static final int MODE_OFF = 0;
    private static final int MODE_SCREEN_ON_UNLOCKED = 1;
    private static final int MODE_SCREEN_OFF_UNLOCKED = 2;
    private static final long CACHE_MS = 2000L;

    private static volatile Context appContext;
    private static volatile Object nfcService;
    private static volatile boolean observerRegistered;
    private static volatile long lastReadAt;
    private static volatile boolean lastEnabled;
    private static volatile int lastMode;
    private static volatile int onUnlockedState = 8;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        try {
            hookApplicationContext();
        } catch (Throwable throwable) {
            log("context hook failed: " + throwable);
        }
        try {
            hookScreenStateHelper(lpparam.classLoader);
        } catch (Throwable throwable) {
            log("screen state hook failed: " + throwable);
        }
        try {
            hookNfcService(lpparam.classLoader);
        } catch (Throwable throwable) {
            log("nfc service hook failed: " + throwable);
        }
        log("installed in " + lpparam.packageName + " process=" + lpparam.processName);
    }

    private static void hookApplicationContext() throws Exception {
        Method onCreate = Application.class.getDeclaredMethod("onCreate");
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Application) {
                    appContext = ((Application) param.thisObject).getApplicationContext();
                    registerSettingObserver(appContext);
                    log("context ready");
                }
            }
        });
    }

    private static void hookNfcService(ClassLoader classLoader) throws Exception {
        Class<?> serviceClass = Class.forName("com.android.nfc.NfcService", false, classLoader);
        Constructor<?>[] constructors = serviceClass.getDeclaredConstructors();
        int count = 0;
        for (int i = 0; i < constructors.length; i++) {
            Constructor<?> constructor = constructors[i];
            constructor.setAccessible(true);
            XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    nfcService = param.thisObject;
                    Context context = findContext(param.thisObject);
                    if (context != null) {
                        appContext = context;
                        registerSettingObserver(context);
                    }
                    refreshNfcScreenState("service-created");
                    log("NfcService captured " + className(param.thisObject));
                }
            });
            count++;
        }
        log("NfcService constructors hooked=" + count);
    }

    private static void hookScreenStateHelper(ClassLoader classLoader) throws Exception {
        Class<?> helper = Class.forName("com.android.nfc.ScreenStateHelper", false, classLoader);
        onUnlockedState = resolveOnUnlockedState(helper);
        int count = 0;
        Method[] methods = helper.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!"checkScreenState".equals(method.getName())) {
                continue;
            }
            if (method.getReturnType() != Integer.TYPE) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = appContext;
                    if (context == null) {
                        context = findContext(param.thisObject);
                    }
                    int mode = getMode(context);
                    if (mode == MODE_SCREEN_OFF_UNLOCKED) {
                        param.setResult(Integer.valueOf(onUnlockedState));
                    } else if (mode == MODE_SCREEN_ON_UNLOCKED) {
                        Object result = param.getResult();
                        int originalState = result instanceof Integer ? ((Integer) result).intValue() : 0;
                        if (originalState == 4) {
                            param.setResult(Integer.valueOf(onUnlockedState));
                        }
                    }
                }
            });
            count++;
        }
        if (count == 0) {
            log("checkScreenState hook skipped: no compatible method");
        } else {
            log("checkScreenState hooks=" + count + ", onUnlockedState=" + onUnlockedState);
        }
    }

    private static int resolveOnUnlockedState(Class<?> helper) {
        try {
            Field[] fields = helper.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType() == Integer.TYPE
                        && Modifier.isStatic(field.getModifiers())
                        && field.getName().toUpperCase().contains("ON_UNLOCKED")) {
                    field.setAccessible(true);
                    return field.getInt(null);
                }
            }
        } catch (Throwable throwable) {
            log("resolve state failed: " + throwable);
        }
        return 8;
    }

    private static void registerSettingObserver(final Context context) {
        if (context == null || observerRegistered) {
            return;
        }
        try {
            observerRegistered = true;
            Handler handler = new Handler(Looper.getMainLooper());
            context.getContentResolver().registerContentObserver(SETTINGS_URI, false, new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    lastReadAt = 0L;
                    refreshNfcScreenState("setting-changed");
                }
            });
            lastReadAt = 0L;
            log("setting observer registered");
        } catch (Throwable throwable) {
            observerRegistered = false;
            log("setting observer failed: " + throwable);
        }
    }

    private static void refreshNfcScreenState(String reason) {
        Object service = nfcService;
        if (service == null) {
            log("refresh skipped, service=null, reason=" + reason);
            return;
        }
        lastReadAt = 0L;
        try {
            Method method = findMethod(service.getClass(), "handleScreenStateChanged");
            method.invoke(service);
            log("requested screen state refresh, reason=" + reason + ", mode=" + lastMode + ", enabled=" + lastEnabled);
            return;
        } catch (Throwable throwable) {
            log("handleScreenStateChanged failed: " + throwable);
        }
        try {
            int state = computeScreenState(service);
            Method method = findMethod(service.getClass(), "applyScreenState", Integer.TYPE);
            method.invoke(service, Integer.valueOf(state));
            log("fallback applyScreenState=" + state + ", reason=" + reason + ", mode=" + lastMode + ", enabled=" + lastEnabled);
        } catch (Throwable throwable) {
            log("fallback refresh failed: " + throwable);
        }
    }

    private static int computeScreenState(Object service) throws Exception {
        Context context = appContext != null ? appContext : findContext(service);
        int mode = getMode(context);
        if (mode == MODE_SCREEN_OFF_UNLOCKED) {
            return onUnlockedState;
        }
        Object helper = getField(service, "mScreenStateHelper");
        boolean checkDisplay = getBooleanField(service, "mCheckDisplayStateForScreenState");
        Method method = findMethod(helper.getClass(), "checkScreenState", Boolean.TYPE);
        Object state = method.invoke(helper, Boolean.valueOf(checkDisplay));
        return state instanceof Integer ? ((Integer) state).intValue() : onUnlockedState;
    }

    private static Context findContext(Object object) {
        if (object == null) {
            return null;
        }
        Class<?> current = object.getClass();
        while (current != null) {
            Field[] fields = current.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (Context.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(object);
                        if (value instanceof Context) {
                            return ((Context) value).getApplicationContext();
                        }
                    } catch (Throwable ignored) {
                        // Try another field.
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object getField(Object object, String name) throws Exception {
        Class<?> current = object.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean getBooleanField(Object object, String name) throws Exception {
        Object value = getField(object, name);
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) throws Exception {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static String className(Object object) {
        return object == null ? "null" : object.getClass().getName();
    }

    private static boolean isEnabled(Context context) {
        return getMode(context) != MODE_OFF;
    }

    private static int getMode(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastReadAt < CACHE_MS) {
            return lastMode;
        }
        lastReadAt = now;
        if (context == null) {
            lastMode = MODE_OFF;
            lastEnabled = false;
            return MODE_OFF;
        }
        try {
            Bundle result = context.getContentResolver().call(SETTINGS_URI, METHOD_GET, null, null);
            if (result != null && result.containsKey(EXTRA_MODE)) {
                lastMode = sanitizeMode(result.getInt(EXTRA_MODE, MODE_OFF));
            } else {
                lastMode = result != null && result.getBoolean(EXTRA_ENABLED, false)
                        ? MODE_SCREEN_OFF_UNLOCKED
                        : MODE_OFF;
            }
            lastEnabled = lastMode != MODE_OFF;
        } catch (Throwable throwable) {
            lastMode = MODE_OFF;
            lastEnabled = false;
            log("read setting failed: " + throwable);
        }
        return lastMode;
    }

    private static int sanitizeMode(int mode) {
        if (mode == MODE_SCREEN_ON_UNLOCKED || mode == MODE_SCREEN_OFF_UNLOCKED) {
            return mode;
        }
        return MODE_OFF;
    }

    private static void log(String message) {
        if (!LogSettingsProvider.isLogEnabled(appContext)) {
            return;
        }
        XposedBridge.log("NfcScreenOffTap: " + message);
    }
}
