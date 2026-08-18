package com.samsung.feature.extension.sdhmsmanager;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.samsung.feature.extension.LogSettingsProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class SdhmsManagerHook implements IXposedHookLoadPackage {
    private static final String TAG = "SdhmsManager";
    private static final Uri SMART_BATTERY_URI = Uri.parse("content://com.sec.smartmanager.provider/batterystat_ext/all_power");
    private static final Uri SMART_ANOMALY_URI = Uri.parse("content://com.sec.smartmanager.provider/anomaly_list");
    private static final Uri BARTENDER_HIGH_CPU_URI = Uri.parse("content://com.sec.bartender.provider/high_cpu_processes");
    private static final Uri FAS_URI = Uri.parse("content://com.sec.android.sdhms.fasprovider/ForcedAppStandby");
    private static final String[] LIMITER_CLASS_CANDIDATES = {
            "k5.m5",  // SDHMS 1.0.0 versionCode 36 / Android 16
            "Q1.j2"  // Older SDHMS builds
    };
    private static final String[] STATUS_CLASS_CANDIDATES = {
            "e5.b",  // SDHMS 1.0.0 versionCode 36 / Android 16
            "N1.b"  // Older SDHMS builds
    };

    private static volatile Context appContext;
    private static volatile Object appObject;
    private static volatile Object sdhmsBinder;
    private static volatile boolean receiverInstalled;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !SdhmsBridge.TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        log("loading in " + lpparam.packageName + " process=" + lpparam.processName);
        installHook("ServiceManager.addService", new Installer() {
            @Override
            public void install() throws Throwable {
                hookAddService(lpparam.classLoader);
            }
        });
        installHook("Application.onCreate", new Installer() {
            @Override
            public void install() throws Throwable {
                hookApplication(lpparam.classLoader);
            }
        });
    }

    private static void hookAddService(ClassLoader classLoader) throws Throwable {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager", false, classLoader);
        Method[] methods = serviceManager.getDeclaredMethods();
        int installed = 0;
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!"addService".equals(method.getName())
                    || parameterTypes.length < 2
                    || parameterTypes[0] != String.class
                    || !IBinder.class.isAssignableFrom(parameterTypes[1])) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args != null
                                && param.args.length >= 2
                                && "sdhms".equals(param.args[0])) {
                            sdhmsBinder = param.args[1];
                            log("captured sdhms binder: " + className(sdhmsBinder));
                        }
                    }
                });
            installed++;
        }
        if (installed == 0) {
            throw new NoSuchMethodException("No compatible ServiceManager.addService overload found");
        }
        log("hooked ServiceManager.addService overloads=" + installed);
    }

    private static void hookApplication(ClassLoader classLoader) throws Throwable {
        Method onCreate = Application.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof Application)) {
                            return;
                        }
                        Application application = (Application) param.thisObject;
                        if (!SdhmsBridge.TARGET_PACKAGE.equals(application.getPackageName())) {
                            return;
                        }
                        appObject = application;
                        appContext = application.getApplicationContext();
                        captureBinderFromApplication(application);
                        installCommandReceiver(application);
                    }
                });
    }

    private static void installCommandReceiver(Context context) {
        if (context == null || receiverInstalled) {
            return;
        }
        receiverInstalled = true;
        IntentFilter filter = new IntentFilter(SdhmsBridge.ACTION_REQUEST);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleCommand(context, intent);
                    }
                });
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        log("command receiver installed");
    }

    private static void handleCommand(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String requestId = intent.getStringExtra(SdhmsBridge.EXTRA_REQUEST_ID);
        String command = intent.getStringExtra(SdhmsBridge.EXTRA_COMMAND);
        Bundle data = new Bundle();
        data.putBoolean("hookAlive", true);
        data.putString("command", command == null ? "" : command);
        try {
            if (SdhmsBridge.CMD_SET_THERMAL_MASTER.equals(command)) {
                setThermalMaster(intent.getBooleanExtra(SdhmsBridge.EXTRA_ENABLED, false));
                data.putString("message", "温控总开关已下发，原生逻辑可能会触发重启。");
            } else if (SdhmsBridge.CMD_SET_BRIGHTNESS_LIMIT_OFF.equals(command)) {
                setBrightnessLimitOff(intent.getBooleanExtra(SdhmsBridge.EXTRA_ENABLED, false));
                data.putString("message", "亮度温控限制开关已更新。");
            } else if (SdhmsBridge.CMD_SET_CP_TM_OFF.equals(command)) {
                setCpThermalMitigationOff(intent.getBooleanExtra(SdhmsBridge.EXTRA_ENABLED, false));
                data.putString("message", "CP/蜂窝温控限制开关已更新。");
            } else if (SdhmsBridge.CMD_SET_FAS_RESTRICTED.equals(command)) {
                String pkg = intent.getStringExtra(SdhmsBridge.EXTRA_PACKAGE_NAME);
                boolean restricted = intent.getBooleanExtra(SdhmsBridge.EXTRA_ENABLED, false);
                setFasRestricted(pkg, restricted);
                data.putString("message", (restricted ? "已限制后台: " : "已解除限制: ") + pkg);
            }
            data.putBundle("snapshot", buildSnapshot());
            data.putBoolean("success", true);
        } catch (Throwable throwable) {
            data.putBoolean("success", false);
            data.putString("error", throwable.getClass().getName() + ": " + throwable.getMessage());
            log("command failed: " + throwable);
            log(throwable);
            try {
                data.putBundle("snapshot", buildSnapshot());
            } catch (Throwable ignored) {
                // Keep the original command error.
            }
        }
        sendResponse(requestId, data);
    }

    private static Bundle buildSnapshot() {
        Bundle snapshot = new Bundle();
        snapshot.putLong("time", System.currentTimeMillis());
        snapshot.putString("targetPackage", SdhmsBridge.TARGET_PACKAGE);
        snapshot.putString("binderClass", className(resolveSdhmsBinder()));
        snapshot.putBundle("thermal", buildThermalSnapshot());
        snapshot.putStringArrayList("temperatures", readTemperatures());
        snapshot.putStringArrayList("batteryRows", queryRows(SMART_BATTERY_URI, null, 12));
        snapshot.putStringArrayList("anomalyRows", queryRows(SMART_ANOMALY_URI, null, 30));
        snapshot.putStringArrayList("highCpuRows", queryRows(BARTENDER_HIGH_CPU_URI, null, 20));
        snapshot.putStringArrayList("fasRows", queryRows(
                FAS_URI,
                new String[]{"package_name", "uid", "mode", "reason", "level", "disableReason", "current"},
                80
        ));
        return snapshot;
    }

    private static Bundle buildThermalSnapshot() {
        Bundle thermal = new Bundle();
        Object limiter = getLimiter();
        thermal.putBoolean("limiterAvailable", limiter != null);
        thermal.putBoolean("brightnessLimitOff", callBoolean(limiter, "h", false));
        thermal.putBoolean("cpTmOff", callBoolean(limiter, "i", false));
        thermal.putBoolean("thermalMasterOff", callBoolean(limiter, "j", false));
        Object status = getThermalStatus();
        thermal.putBoolean("statusAvailable", status != null);
        thermal.putBoolean("brightnessLimited", callBoolean(status, "a", false));
        thermal.putBoolean("cpLowMode", callBoolean(status, "b", false));
        thermal.putBoolean("cpCoolingDown", callBoolean(status, "c", false));
        thermal.putBoolean("hrrLimited", callBoolean(status, "e", false));
        thermal.putStringArrayList("history", readStringArray(status, "d", 20));
        thermal.putInt("thermalControlFlag", callInt(resolveSdhmsBinder(), "getThermalControlFlag", -1));
        thermal.putInt("thermalThrottlingDelta", callInt(resolveSdhmsBinder(), "getThermalThrottlingDelta", 0));
        thermal.putInt("supportedThermalThrottlingDelta", callInt(resolveSdhmsBinder(), "getSupportedThermalThrottlingDelta", 0));
        return thermal;
    }

    private static ArrayList<String> readTemperatures() {
        ArrayList<String> rows = new ArrayList<>();
        try {
            Class<?> temperatureClass = findClass("com.sec.android.sdhms.thermal.siop.Temperature");
            Object[] constants = temperatureClass != null ? temperatureClass.getEnumConstants() : null;
            if (constants != null) {
                for (int i = 0; i < constants.length; i++) {
                    Object item = constants[i];
                    String name = item instanceof Enum ? ((Enum) item).name() : String.valueOf(item);
                    int raw = callIntAny(item, new String[]{"k", "l"}, Integer.MIN_VALUE);
                    if (raw == Integer.MIN_VALUE) {
                        throw new NoSuchMethodException("Temperature value accessor k/l is unavailable");
                    }
                    rows.add(name + "=" + formatTemperature(raw));
                }
            }
        } catch (Throwable throwable) {
            rows.add("Temperature enum failed: " + throwable.getClass().getSimpleName());
        }
        if (rows.isEmpty()) {
            Object binder = resolveSdhmsBinder();
            for (int i = 0; i < 20; i++) {
                try {
                    int value = callIntWithIntArg(binder, "getTemperature", i, Integer.MIN_VALUE);
                    if (value == Integer.MIN_VALUE) {
                        break;
                    }
                    rows.add("sensor[" + i + "]=" + formatTemperature(value));
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        return rows;
    }

    private static String formatTemperature(int raw) {
        if (raw == -999 || raw == -888) {
            return String.valueOf(raw);
        }
        return String.format(Locale.US, "%.1f C", raw / 10.0f);
    }

    private static ArrayList<String> queryRows(Uri uri, String[] projection, int limit) {
        ArrayList<String> rows = new ArrayList<>();
        Context context = appContext;
        if (context == null) {
            rows.add("SDHMS context not ready");
            return rows;
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor == null) {
                rows.add("无数据: " + uri);
                return rows;
            }
            String[] columns = cursor.getColumnNames();
            int count = 0;
            while (cursor.moveToNext() && count < limit) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < columns.length; i++) {
                    if (i > 0) {
                        builder.append(" | ");
                    }
                    builder.append(columns[i]).append('=').append(cursor.getString(i));
                }
                rows.add(builder.toString());
                count++;
            }
            if (count == 0) {
                rows.add("暂无记录");
            } else if (cursor.moveToNext()) {
                rows.add("...");
            }
        } catch (Throwable throwable) {
            rows.add("读取失败: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return rows;
    }

    private static void setThermalMaster(boolean disabled) {
        Object limiter = requireLimiter();
        callBooleanSetter(limiter, "d", disabled);
    }

    private static void setBrightnessLimitOff(boolean disabled) {
        Object limiter = requireLimiter();
        callBooleanSetter(limiter, "k", disabled);
    }

    private static void setCpThermalMitigationOff(boolean disabled) {
        Object limiter = requireLimiter();
        callBooleanSetter(limiter, "l", disabled);
    }

    private static void setFasRestricted(String pkg, boolean restricted) throws Exception {
        Context context = appContext;
        if (context == null) {
            throw new IllegalStateException("SDHMS context not ready");
        }
        if (pkg == null || pkg.trim().length() == 0) {
            throw new IllegalArgumentException("packageName is empty");
        }
        String packageName = pkg.trim();
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
        int uid = info.uid;
        int mode = restricted ? 1 : 0;
        Object appOps = context.getSystemService("appops");
        Method setMode = appOps.getClass().getMethod("setMode", int.class, int.class, String.class, int.class);
        setMode.invoke(appOps, Integer.valueOf(70), Integer.valueOf(uid), packageName, Integer.valueOf(mode));

        ContentValues values = new ContentValues();
        values.put("package_name", packageName);
        values.put("uid", Integer.valueOf(uid));
        values.put("mode", Integer.valueOf(mode));
        values.put("reason", "manual");
        values.put("dozeWhiteListed", Integer.valueOf(0));
        values.put("level", Integer.valueOf(mode == 1 ? 2 : 1));
        int updated = 0;
        try {
            updated = context.getContentResolver().update(
                    FAS_URI,
                    values,
                    "package_name=? AND uid=?",
                    new String[]{packageName, String.valueOf(uid)}
            );
        } catch (Throwable throwable) {
            log("FAS update failed, trying insert: " + throwable);
        }
        if (updated <= 0) {
            try {
                context.getContentResolver().insert(FAS_URI, values);
            } catch (Throwable throwable) {
                log("FAS insert failed: " + throwable);
            }
        }
        context.getContentResolver().notifyChange(FAS_URI, null);
    }

    private static Object requireLimiter() {
        Object limiter = getLimiter();
        if (limiter == null) {
            throw new IllegalStateException("SDHMS thermal limiter is not available (k5.m5 / Q1.j2)");
        }
        return limiter;
    }

    private static Object getLimiter() {
        Context context = appContext;
        if (context == null) {
            return null;
        }
        Throwable lastFailure = null;
        Class<?> limiterClass = findLimiterClass();
        if (limiterClass != null) {
            try {
                Method factory = findMethod(limiterClass, "g", Context.class);
                return factory.invoke(null, context);
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
        }
        log("get limiter failed: " + lastFailure);
        return null;
    }

    private static Object resolveSdhmsBinder() {
        if (sdhmsBinder != null) {
            return sdhmsBinder;
        }
        Object application = appObject;
        if (application != null) {
            captureBinderFromApplication(application);
        }
        return sdhmsBinder;
    }

    private static void captureBinderFromApplication(Object application) {
        if (application == null || sdhmsBinder != null) {
            return;
        }
        try {
            Field[] fields = application.getClass().getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                field.setAccessible(true);
                Object value = field.get(application);
                if (value != null && "com.sec.android.sdhms.b".equals(value.getClass().getName())) {
                    sdhmsBinder = value;
                    log("captured binder from application field " + field.getName());
                    return;
                }
            }
        } catch (Throwable throwable) {
            log("scan binder failed: " + throwable);
        }
    }

    private static void sendResponse(String requestId, Bundle data) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        Intent response = new Intent(SdhmsBridge.ACTION_RESPONSE);
        response.setPackage(SdhmsBridge.MODULE_PACKAGE);
        response.putExtra(SdhmsBridge.EXTRA_REQUEST_ID, requestId);
        response.putExtra(SdhmsBridge.EXTRA_DATA, data);
        context.sendBroadcast(response);
    }

    private static Class<?> findLimiterClass() {
        for (int i = 0; i < LIMITER_CLASS_CANDIDATES.length; i++) {
            Class<?> candidate = findClass(LIMITER_CLASS_CANDIDATES[i]);
            if (isLimiterApi(candidate)) {
                return candidate;
            }
        }
        // Keep a structural fallback so a package-only obfuscation rename does not
        // immediately break the three switches again.
        Class<?> settingsActivity = findClass("com.sec.android.sdhms.debugger.ThermalLimitSettingActivity");
        if (settingsActivity != null) {
            Field[] fields = settingsActivity.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Class<?> candidate = fields[i].getType();
                if (isLimiterApi(candidate)) {
                    log("resolved limiter structurally from debugger activity: " + candidate.getName());
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isLimiterApi(Class<?> candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            findMethod(candidate, "g", Context.class);
            findMethod(candidate, "d", boolean.class);
            findMethod(candidate, "h");
            findMethod(candidate, "i");
            findMethod(candidate, "j");
            findMethod(candidate, "k", boolean.class);
            findMethod(candidate, "l", boolean.class);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getThermalStatus() {
        for (int i = 0; i < STATUS_CLASS_CANDIDATES.length; i++) {
            try {
                Class<?> candidate = findClass(STATUS_CLASS_CANDIDATES[i]);
                if (candidate != null) {
                    Object value = findMethod(candidate, "f").invoke(null);
                    if (value != null) {
                        return value;
                    }
                }
            } catch (Throwable ignored) {
                // Try the next known app generation.
            }
        }
        return null;
    }

    private static void callBooleanSetter(Object target, String methodName, boolean value) {
        if (target == null) {
            throw new IllegalStateException("Target is null for " + methodName);
        }
        try {
            findMethod(target.getClass(), methodName, boolean.class).invoke(target, Boolean.valueOf(value));
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to invoke " + target.getClass().getName()
                    + "." + methodName + "(boolean)", throwable);
        }
    }

    private static boolean callBoolean(Object target, String methodName, boolean fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Object value = findMethod(target.getClass(), methodName).invoke(target);
            return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int callInt(Object target, String methodName, int fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Object value = findMethod(target.getClass(), methodName).invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int callIntAny(Object target, String[] methodNames, int fallback) {
        if (target == null || methodNames == null) {
            return fallback;
        }
        for (int i = 0; i < methodNames.length; i++) {
            int value = callInt(target, methodNames[i], Integer.MIN_VALUE);
            if (value != Integer.MIN_VALUE) {
                return value;
            }
        }
        return fallback;
    }

    private static int callIntWithIntArg(Object target, String methodName, int argument, int fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Object value = findMethod(target.getClass(), methodName, int.class)
                    .invoke(target, Integer.valueOf(argument));
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static ArrayList<String> readStringArray(Object target, String methodName, int limit) {
        ArrayList<String> result = new ArrayList<>();
        if (target == null) {
            return result;
        }
        try {
            Object value = findMethod(target.getClass(), methodName).invoke(target);
            if (value instanceof String[]) {
                String[] strings = (String[]) value;
                for (int i = 0; i < strings.length && i < limit; i++) {
                    result.add(strings[i]);
                }
            }
        } catch (Throwable ignored) {
            // Optional diagnostic data.
        }
        return result;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        Method method = type.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Class<?> findClass(String name) {
        try {
            Context context = appContext;
            ClassLoader loader = context != null ? context.getClassLoader() : SdhmsManagerHook.class.getClassLoader();
            return Class.forName(name, false, loader);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String className(Object object) {
        return object == null ? "null" : object.getClass().getName();
    }

    private static void installHook(String name, Installer installer) {
        try {
            installer.install();
            log(name + " installed");
        } catch (Throwable throwable) {
            log(name + " failed: " + throwable);
            log(throwable);
        }
    }

    private static void log(String message) {
        if (!LogSettingsProvider.isLogEnabled(appContext)) {
            return;
        }
        XposedBridge.log(TAG + ": " + message);
    }

    private static void log(Throwable throwable) {
        if (!LogSettingsProvider.isLogEnabled(appContext)) {
            return;
        }
        XposedBridge.log(throwable);
    }

    private interface Installer {
        void install() throws Throwable;
    }
}
