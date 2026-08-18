package com.samsung.feature.extension.galaxyraw200mp;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Pair;

import com.samsung.feature.extension.LogSettingsProvider;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class GalaxyRaw200MpHook implements IXposedHookLoadPackage {
    private static final String TAG = "GalaxyRaw200MpLsp";
    private static final String TARGET_PACKAGE = "com.samsung.android.app.galaxyraw";
    private static final String SUPPORT_ULTRA = "SUPPORT_BACK_PRO_ULTRA_HIGH_RESOLUTION";
    private static final String SUPPORT_24MP_MENU = "BACK_CAMERA_PRO_24MP_HIGH_RESOLUTION";
    private static final String SUPPORT_HIGH = "SUPPORT_BACK_PRO_HIGH_RESOLUTION";
    private static final String SUPPORT_ADAPTIVE_PIXEL = "SUPPORT_ADAPTIVE_PIXEL";
    private static final String RES_ULTRA = "BACK_CAMERA_PRO_RESOLUTION_ULTRA_HIGH_RESOLUTION";
    private static final String RES_HIGH = "BACK_CAMERA_PRO_RESOLUTION_HIGH_RESOLUTION";
    private static final String RES_24MP = "BACK_CAMERA_PRO_24MP_HIGH_RESOLUTION";
    private static final String VALUE_ULTRA = "16320x12240";
    private static final String VALUE_HIGH = "8160x6120";
    private static final String VALUE_24MP = "5712x4284";
    private static final int PICTURE_SIZE_24MP = 1;
    private static final int PICTURE_SIZE_50MP = 2;
    private static final int PICTURE_SIZE_FAKE_24MP = 3;
    private static final int FALLBACK_HIGH_RESOLUTION_ID = 134;
    private static final int FALLBACK_24MP_RESOLUTION_ID = 146;
    private static final String TOKEN_24MP = "RESOLUTION_5712X4284";
    private static final String TOKEN_HIGH = "RESOLUTION_8160X6120";
    private static final String COMMAND_PICTURE_SIZE_MENU = "BACK_CAMERA_PRO_PICTURE_SIZE_MENU";
    private static final String COMMAND_PICTURE_SIZE_24MP = "BACK_CAMERA_PRO_PICTURE_SIZE_24MP";
    private static final String COMMAND_FAKE_PICTURE_SIZE_24MP = "BACK_CAMERA_PRO_FAKE_PICTURE_SIZE_24MP";
    private static final String COMMAND_PICTURE_SIZE_50MP = "BACK_CAMERA_PRO_PICTURE_SIZE_50MP";
    private static final String COMMAND_PICTURE_SIZE_12MP = "BACK_CAMERA_PRO_PICTURE_SIZE_12MP";
    private static final String KEY_BACK_CAMERA_PICTURE_RATIO = "BACK_CAMERA_PICTURE_RATIO";
    private static final FeatureApi[] FEATURE_APIS = {
            // Expert RAW 5.0.08.2 / One UI 8 and newer.
            new FeatureApi("R1.g", "R1.a", "R1.l", "B0.g", "x", "u"),
            // Expert RAW 4.x and older releases kept for backwards compatibility.
            new FeatureApi("H1.g", "H1.a", "H1.l", "B2.a", "p", "k")
    };
    private static final String[] COMMAND_MAP_CLASSES = {
            "B1.f", // Expert RAW 5.0.08.2+
            "r1.e"  // Expert RAW 4.x and older
    };
    private static final String[] COMMAND_RESOURCE_CLASSES = {
            "a2.h", // Expert RAW 5.0.08.2+
            "Q1.h", // Expert RAW 4.x and older
            "q1.h"  // Some older obfuscation maps use a lower-case package.
    };
    private static final String[] SUPPORTED_ULTRA_MODELS = {
            "SM-S918", // Galaxy S23 Ultra
            "SM-S928", // Galaxy S24 Ultra
            "SM-S938"  // Galaxy S25 Ultra
    };
    private static final String[] SUPPORTED_ULTRA_PRODUCTS = {
            "dm3", // Galaxy S23 Ultra
            "e3",  // Galaxy S24 Ultra
            "pa3"  // Galaxy S25 Ultra / newer app variation name
    };
    private static final String[] SUPPORTED_24MP_MODELS = {
            "SM-S928", // Galaxy S24 Ultra
            "SM-S938"  // Galaxy S25 Ultra
    };
    private static final String[] SUPPORTED_24MP_PRODUCTS = {
            "e3",
            "pa3"
    };
    private static volatile Context appContext;
    private static volatile int highResolutionId = -1;
    private static volatile int unsupported24MpResolutionId = -1;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        log("loaded for " + lpparam.packageName + " process=" + lpparam.processName + ", " + deviceSummary());
        installHook("feature loader map injection", new Installer() {
            @Override
            public void install() throws Throwable {
                hookFeatureLoader(lpparam.classLoader);
            }
        });
        installHook("feature accessor overrides", new Installer() {
            @Override
            public void install() throws Throwable {
                hookFeatureAccessors(lpparam.classLoader);
            }
        });
        installHook("S23 unsupported 24MP resolution guard", new Installer() {
            @Override
            public void install() throws Throwable {
                hookUnsupported24MpResolutionGuard(lpparam.classLoader);
            }
        });
        installHook("S23 unsupported 24MP command guard", new Installer() {
            @Override
            public void install() throws Throwable {
                hookUnsupported24MpCommandGuard(lpparam.classLoader);
            }
        });
        installHook("Application.onCreate reinjection", new Installer() {
            @Override
            public void install() throws Throwable {
                hookApplicationOnCreate(lpparam.classLoader);
            }
        });
    }

    private static void hookFeatureLoader(final ClassLoader classLoader) throws Throwable {
        int installed = 0;
        Throwable lastFailure = null;
        for (int i = 0; i < FEATURE_APIS.length; i++) {
            final FeatureApi api = FEATURE_APIS[i];
            try {
                Class<?> loaderClass = Class.forName(api.loaderClassName, false, classLoader);
                final Method loadFeature = loaderClass.getDeclaredMethod("f", Context.class);
                loadFeature.setAccessible(true);
                XposedBridge.hookMethod(loadFeature, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String source = api.loaderClassName + ".f";
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof Context) {
                            sanitizeUnsupported24MpState((Context) param.args[0], source);
                        }
                        injectFeatureMap(classLoader, source);
                    }
                });
                installed++;
                log("feature loader variant installed: " + api.loaderClassName);
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
        }
        if (installed == 0) {
            throw new ClassNotFoundException("No supported Expert RAW feature loader found", lastFailure);
        }
    }

    private static void hookFeatureAccessors(final ClassLoader classLoader) throws Throwable {
        int installed = 0;
        Throwable lastFailure = null;
        for (int i = 0; i < FEATURE_APIS.length; i++) {
            final FeatureApi api = FEATURE_APIS[i];
            try {
                final Class<?> booleanFeatureKey = Class.forName(api.booleanKeyClassName, false, classLoader);
                final Class<?> stringFeatureKey = Class.forName(api.stringKeyClassName, false, classLoader);
                Class<?> featureAccessor = Class.forName(api.accessorClassName, false, classLoader);

                Method booleanAccessor = featureAccessor.getDeclaredMethod(api.booleanAccessorMethod, booleanFeatureKey);
                booleanAccessor.setAccessible(true);
                XposedBridge.hookMethod(booleanAccessor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = enumName(param.args != null && param.args.length > 0 ? param.args[0] : null);
                        if (SUPPORT_24MP_MENU.equals(key)) {
                            param.setResult(Boolean.valueOf(shouldEnableProPictureSizeMenu()));
                            return;
                        }
                        if (SUPPORT_ULTRA.equals(key)) {
                            param.setResult(Boolean.valueOf(isUltraDevice()));
                            return;
                        }
                        if (SUPPORT_HIGH.equals(key) || SUPPORT_ADAPTIVE_PIXEL.equals(key)) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                });

                Method stringAccessor = featureAccessor.getDeclaredMethod(api.stringAccessorMethod, stringFeatureKey);
                stringAccessor.setAccessible(true);
                XposedBridge.hookMethod(stringAccessor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = enumName(param.args != null && param.args.length > 0 ? param.args[0] : null);
                        Object current = param.getResult();
                        String value = current instanceof String ? (String) current : "";
                        if (RES_24MP.equals(key) && !shouldEnable24MpFeature()) {
                            param.setResult(VALUE_HIGH);
                            return;
                        }
                        String forced = forcedResolution(key, value);
                        if (forced != null) {
                            param.setResult(forced);
                        }
                    }
                });

                installed++;
                log("feature accessor variant installed: " + api.accessorClassName);
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
        }
        if (installed == 0) {
            throw new ClassNotFoundException("No supported Expert RAW feature accessor found", lastFailure);
        }
    }

    private static void hookUnsupported24MpResolutionGuard(final ClassLoader classLoader)
            throws ClassNotFoundException {
        final Class<?> settingsClass = Class.forName(
                "com.samsung.android.app.galaxyraw.setting.repository.CameraSettingsImpl",
                false,
                classLoader);
        final Class<?> settingKeyClass = Class.forName(
                "com.samsung.android.app.galaxyraw.interfaces.CameraSettings$Key",
                false,
                classLoader);
        Method[] methods = settingsClass.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            String name = method.getName();
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getReturnType() == int.class && isResolutionGetterName(name)) {
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!shouldClampUnsupported24Mp()) {
                            return;
                        }
                        Object result = param.getResult();
                        if (result instanceof Integer
                                && isUnsupported24MpResolutionId(classLoader, ((Integer) result).intValue())) {
                            int fallback = highResolutionId(classLoader);
                            param.setResult(Integer.valueOf(fallback));
                            log("clamped unsupported S23 24MP getter "
                                    + param.method.getName() + " to id=" + fallback);
                        }
                    }
                });
            }
            if (method.getReturnType() == int.class
                    && parameterTypes.length == 1
                    && parameterTypes[0] == settingKeyClass) {
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!shouldClampUnsupported24Mp()
                                || param.args == null
                                || param.args.length == 0
                                || !KEY_BACK_CAMERA_PICTURE_RATIO.equals(enumName(param.args[0]))) {
                            return;
                        }
                        int value = intValue(param.getResult(), Integer.MIN_VALUE);
                        if (isUnsupportedPictureSizeValue(value)) {
                            param.setResult(Integer.valueOf(PICTURE_SIZE_50MP));
                            log("clamped unsupported S23 picture size getter "
                                    + param.method.getName() + " from value=" + value);
                        }
                    }
                });
            }
            if (isResolutionSetterName(name) && method.getParameterTypes().length > 0) {
                boolean hasIntParameter = false;
                for (int p = 0; p < parameterTypes.length; p++) {
                    if (parameterTypes[p] == int.class || parameterTypes[p] == Integer.class) {
                        hasIntParameter = true;
                        break;
                    }
                }
                if (!hasIntParameter) {
                    continue;
                }
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!shouldClampUnsupported24Mp() || param.args == null) {
                            return;
                        }
                        int fallback = highResolutionId(classLoader);
                        boolean changed = false;
                        for (int a = 0; a < param.args.length; a++) {
                            Object arg = param.args[a];
                            if (arg instanceof Integer
                                    && isUnsupported24MpResolutionId(classLoader, ((Integer) arg).intValue())) {
                                param.args[a] = Integer.valueOf(fallback);
                                changed = true;
                            }
                        }
                        if (changed) {
                            log("clamped unsupported S23 24MP setter "
                                    + param.method.getName() + " to id=" + fallback);
                        }
                    }
                });
            }
            if ((method.getReturnType() == void.class || method.getReturnType() == Void.TYPE)
                    && parameterTypes.length >= 2
                    && parameterTypes[0] == settingKeyClass
                    && (parameterTypes[1] == int.class || parameterTypes[1] == Integer.class)) {
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!shouldClampUnsupported24Mp()
                                || param.args == null
                                || param.args.length < 2
                                || !KEY_BACK_CAMERA_PICTURE_RATIO.equals(enumName(param.args[0]))) {
                            return;
                        }
                        int value = intValue(param.args[1], Integer.MIN_VALUE);
                        if (isUnsupportedPictureSizeValue(value)) {
                            param.args[1] = Integer.valueOf(PICTURE_SIZE_50MP);
                            log("clamped unsupported S23 picture size setter "
                                    + param.method.getName() + " from value=" + value);
                        }
                    }
                });
            }
        }
    }

    private static void hookUnsupported24MpCommandGuard(final ClassLoader classLoader)
            throws Throwable {
        final Class<?> commandIdClass = Class.forName(
                "com.samsung.android.app.galaxyraw.interfaces.CommandId",
                false,
                classLoader);
        final Class<?> settingKeyClass = Class.forName(
                "com.samsung.android.app.galaxyraw.interfaces.CameraSettings$Key",
                false,
                classLoader);
        Class<?> commandMapClass = findCommandMapClass(classLoader, settingKeyClass, commandIdClass, false);

        Method commandForValue = commandMapClass.getDeclaredMethod("b", int.class, settingKeyClass);
        XposedBridge.hookMethod(commandForValue, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!shouldClampUnsupported24Mp() || param.args == null || param.args.length < 2) {
                    return;
                }
                if (!KEY_BACK_CAMERA_PICTURE_RATIO.equals(enumName(param.args[1]))) {
                    return;
                }
                int value = intValue(param.args[0], Integer.MIN_VALUE);
                if (value == PICTURE_SIZE_24MP
                        || value == PICTURE_SIZE_FAKE_24MP
                        || isUnsupported24MpCommand(param.getResult())) {
                    Object replacement = replacementPictureSizeCommand(commandIdClass);
                    if (replacement != null) {
                        param.setResult(replacement);
                    }
                }
            }
        });

        Method subOptions = commandMapClass.getDeclaredMethod("e", commandIdClass);
        XposedBridge.hookMethod(subOptions, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!shouldClampUnsupported24Mp() || param.args == null || param.args.length == 0) {
                    return;
                }
                if (!COMMAND_PICTURE_SIZE_MENU.equals(enumName(param.args[0]))) {
                    return;
                }
                Object result = param.getResult();
                if (!(result instanceof ArrayList)) {
                    return;
                }
                ArrayList original = (ArrayList) result;
                ArrayList filtered = new ArrayList(original.size());
                boolean changed = false;
                for (int i = 0; i < original.size(); i++) {
                    Object item = original.get(i);
                    if (isUnsupported24MpCommand(item)) {
                        changed = true;
                    } else {
                        filtered.add(item);
                    }
                }
                if (changed) {
                    param.setResult(filtered);
                }
            }
        });

        Class<?> commandResourceClass = findCommandResourceClass(classLoader, commandIdClass, false);
        Method commandResource = commandResourceClass.getDeclaredMethod("a", commandIdClass);
        XposedBridge.hookMethod(commandResource, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!shouldClampUnsupported24Mp() || param.args == null || param.args.length == 0) {
                    return;
                }
                if (!isUnsupported24MpCommand(param.args[0])) {
                    return;
                }
                Object replacement = commandResourceFor(classLoader, commandIdClass, COMMAND_PICTURE_SIZE_50MP);
                if (replacement == null) {
                    replacement = commandResourceFor(classLoader, commandIdClass, COMMAND_PICTURE_SIZE_12MP);
                }
                if (replacement != null) {
                    param.setResult(replacement);
                }
            }
        });
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean isUnsupported24MpCommand(Object command) {
        String name = enumName(command);
        return COMMAND_PICTURE_SIZE_24MP.equals(name) || COMMAND_FAKE_PICTURE_SIZE_24MP.equals(name);
    }

    private static Object replacementPictureSizeCommand(Class<?> commandIdClass) {
        Object replacement = enumConstant(commandIdClass, COMMAND_PICTURE_SIZE_50MP);
        return replacement != null ? replacement : enumConstant(commandIdClass, COMMAND_PICTURE_SIZE_12MP);
    }

    private static Object commandResourceFor(ClassLoader classLoader, Class<?> commandIdClass, String commandName) {
        try {
            Object command = enumConstant(commandIdClass, commandName);
            if (command == null) {
                return null;
            }
            Class<?> commandResourceClass = findCommandResourceClass(classLoader, commandIdClass, true);
            Field[] fields = commandResourceClass.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object mapObject = field.get(null);
                if (mapObject instanceof Map) {
                    Object value = ((Map) mapObject).get(command);
                    if (value != null) {
                        return value;
                    }
                }
            }
        } catch (Throwable throwable) {
            log("command resource fallback failed for " + commandName + ": " + throwable);
        }
        return null;
    }

    private static Class<?> findCommandMapClass(
            ClassLoader classLoader,
            Class<?> settingKeyClass,
            Class<?> commandIdClass,
            boolean initialize) throws ClassNotFoundException {
        Throwable lastFailure = null;
        for (int i = 0; i < COMMAND_MAP_CLASSES.length; i++) {
            try {
                Class<?> candidate = Class.forName(COMMAND_MAP_CLASSES[i], initialize, classLoader);
                candidate.getDeclaredMethod("b", int.class, settingKeyClass);
                candidate.getDeclaredMethod("e", commandIdClass);
                return candidate;
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
        }
        throw new ClassNotFoundException("No supported Expert RAW command map found", lastFailure);
    }

    private static Class<?> findCommandResourceClass(
            ClassLoader classLoader,
            Class<?> commandIdClass,
            boolean initialize) throws ClassNotFoundException {
        Throwable lastFailure = null;
        for (int i = 0; i < COMMAND_RESOURCE_CLASSES.length; i++) {
            try {
                Class<?> candidate = Class.forName(COMMAND_RESOURCE_CLASSES[i], initialize, classLoader);
                candidate.getDeclaredMethod("a", commandIdClass);
                return candidate;
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
        }
        throw new ClassNotFoundException("No supported Expert RAW command resource map found", lastFailure);
    }

    private static boolean isResolutionGetterName(String name) {
        return "getBackCameraResolution".equals(name)
                || "getCameraResolution".equals(name)
                || "getBackCameraPictureSize".equals(name);
    }

    private static boolean isResolutionSetterName(String name) {
        return "setBackCameraResolution".equals(name)
                || "setCameraResolution".equals(name)
                || "setBackCameraPictureSize".equals(name);
    }

    private static boolean shouldClampUnsupported24Mp() {
        return isUltraDevice() && !shouldEnable24MpFeature();
    }

    private static boolean isUnsupported24MpResolutionId(ClassLoader classLoader, int id) {
        return id == unsupported24MpResolutionId(classLoader);
    }

    private static boolean isUnsupportedPictureSizeValue(int value) {
        return value == PICTURE_SIZE_24MP || value == PICTURE_SIZE_FAKE_24MP;
    }

    private static int unsupported24MpResolutionId(ClassLoader classLoader) {
        int cached = unsupported24MpResolutionId;
        if (cached > 0) {
            return cached;
        }
        cached = resolutionId(classLoader, VALUE_24MP, FALLBACK_24MP_RESOLUTION_ID);
        unsupported24MpResolutionId = cached;
        return cached;
    }

    private static int highResolutionId(ClassLoader classLoader) {
        int cached = highResolutionId;
        if (cached > 0) {
            return cached;
        }
        cached = resolutionId(classLoader, VALUE_HIGH, FALLBACK_HIGH_RESOLUTION_ID);
        highResolutionId = cached;
        return cached;
    }

    private static int resolutionId(ClassLoader classLoader, String resolution, int fallback) {
        try {
            Class<?> resolutionClass = Class.forName(
                    "com.samsung.android.app.galaxyraw.interfaces.Resolution",
                    false,
                    classLoader);
            Method getResolution = resolutionClass.getDeclaredMethod("getResolution", String.class);
            Object resolutionObject = getResolution.invoke(null, resolution);
            Method getId = resolutionClass.getDeclaredMethod("getId");
            Object id = getId.invoke(resolutionObject);
            if (id instanceof Number) {
                return ((Number) id).intValue();
            }
        } catch (Throwable throwable) {
            log("resolution id lookup failed for " + resolution + ": " + throwable);
        }
        return fallback;
    }

    private static void hookApplicationOnCreate(final ClassLoader classLoader) throws NoSuchMethodException {
        Method onCreate = Application.class.getDeclaredMethod("onCreate");
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                ClassLoader appClassLoader = classLoader;
                if (param.thisObject instanceof Application) {
                    Application application = (Application) param.thisObject;
                    appContext = application.getApplicationContext();
                    sanitizeUnsupported24MpState(application, "Application.onCreate");
                    appClassLoader = application.getClassLoader();
                }
                injectFeatureMap(appClassLoader, "Application.onCreate");
                sanitizeUnsupported24MpCommandMaps(appClassLoader, "Application.onCreate");
            }
        });
    }

    private static void injectFeatureMap(ClassLoader classLoader, String source) {
        int patched = 0;
        Throwable lastFailure = null;
        for (int i = 0; i < FEATURE_APIS.length; i++) {
            FeatureApi api = FEATURE_APIS[i];
            try {
                Class<?> featureLoader = Class.forName(api.loaderClassName, false, classLoader);
                Field mapField = featureLoader.getDeclaredField("b");
                if (!Modifier.isStatic(mapField.getModifiers()) || !Map.class.isAssignableFrom(mapField.getType())) {
                    continue;
                }
                mapField.setAccessible(true);
                Object mapObject = mapField.get(null);
                if (!(mapObject instanceof Map)) {
                    continue;
                }
                Map map = (Map) mapObject;
                putLocal(map, SUPPORT_HIGH, "true");
                putLocal(map, SUPPORT_ULTRA, Boolean.toString(isUltraDevice()));
                putLocal(map, SUPPORT_ADAPTIVE_PIXEL, "true");
                putLocal(map, RES_ULTRA, VALUE_ULTRA);
                putLocalIfBlank(map, RES_HIGH, VALUE_HIGH);
                if (shouldEnable24MpFeature()) {
                    putLocalIfBlank(map, RES_24MP, VALUE_24MP);
                } else {
                    putLocal(map, RES_24MP, VALUE_HIGH);
                }
                patched++;
                log("feature map patched via " + api.loaderClassName + " from " + source
                        + ", entries=" + map.size() + ", " + deviceSummary());
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
        }
        if (patched == 0) {
            log("feature map patch unavailable from " + source + ": " + lastFailure);
        }
    }

    private static void sanitizeUnsupported24MpCommandMaps(ClassLoader classLoader, String source) {
        if (!shouldClampUnsupported24Mp()) {
            return;
        }
        try {
            Class<?> commandIdClass = Class.forName(
                    "com.samsung.android.app.galaxyraw.interfaces.CommandId",
                    true,
                    classLoader);
            Class<?> settingKeyClass = Class.forName(
                    "com.samsung.android.app.galaxyraw.interfaces.CameraSettings$Key",
                    true,
                    classLoader);
            Class<?> commandMapClass = findCommandMapClass(classLoader, settingKeyClass, commandIdClass, true);
            Object replacement = replacementPictureSizeCommand(commandIdClass);
            Object menu = enumConstant(commandIdClass, COMMAND_PICTURE_SIZE_MENU);
            Object settingKey = enumConstant(settingKeyClass, KEY_BACK_CAMERA_PICTURE_RATIO);
            if (replacement == null || menu == null || settingKey == null) {
                return;
            }

            Field valueToCommandField = commandMapClass.getDeclaredField("b");
            valueToCommandField.setAccessible(true);
            Object valueToCommandObject = valueToCommandField.get(null);
            if (valueToCommandObject instanceof Map) {
                Map valueToCommand = (Map) valueToCommandObject;
                valueToCommand.put(Pair.create(settingKey, Integer.valueOf(PICTURE_SIZE_24MP)), replacement);
                valueToCommand.put(Pair.create(settingKey, Integer.valueOf(PICTURE_SIZE_FAKE_24MP)), replacement);
            }

            Field subOptionsField = commandMapClass.getDeclaredField("c");
            subOptionsField.setAccessible(true);
            Object subOptionsObject = subOptionsField.get(null);
            if (subOptionsObject instanceof Map) {
                Object listObject = ((Map) subOptionsObject).get(menu);
                if (listObject instanceof ArrayList) {
                    ArrayList list = (ArrayList) listObject;
                    boolean changed = false;
                    for (int i = list.size() - 1; i >= 0; i--) {
                        if (isUnsupported24MpCommand(list.get(i))) {
                            list.remove(i);
                            changed = true;
                        }
                    }
                    if (changed) {
                        log("sanitized unsupported S23 24MP command list from " + source);
                    }
                }
            }
        } catch (Throwable throwable) {
            log("sanitize unsupported 24MP command maps failed from " + source + ": " + throwable);
            log(throwable);
        }
    }

    private static void putLocalIfBlank(Map map, String name, String value) {
        Object existing = map.get(name);
        if (existing instanceof Map) {
            Object current = ((Map) existing).get("value");
            if (current instanceof String && ((String) current).length() > 0) {
                return;
            }
        }
        putLocal(map, name, value);
    }

    private static void putLocal(Map map, String name, String value) {
        Map entry;
        Object existing = map.get(name);
        if (existing instanceof Map) {
            entry = new HashMap((Map) existing);
        } else {
            entry = new HashMap();
        }
        entry.put("value", value);
        map.put(name, entry);
    }

    private static void removeLocal(Map map, String name) {
        map.remove(name);
    }

    private static String forcedResolution(String key, String current) {
        if (RES_ULTRA.equals(key)) {
            return VALUE_ULTRA;
        }
        if (RES_HIGH.equals(key) && isBlank(current)) {
            return VALUE_HIGH;
        }
        if (RES_24MP.equals(key) && shouldEnable24MpFeature() && isBlank(current)) {
            return VALUE_24MP;
        }
        if (RES_24MP.equals(key) && !shouldEnable24MpFeature()) {
            return VALUE_HIGH;
        }
        return null;
    }

    private static void sanitizeUnsupported24MpState(Context context, String source) {
        if (context == null || shouldEnable24MpFeature()) {
            return;
        }
        try {
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            File[] files = prefsDir.listFiles();
            if (files == null || files.length == 0) {
                return;
            }
            int changed = 0;
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file == null || !file.isFile() || !file.getName().endsWith(".xml")) {
                    continue;
                }
                if (file.length() > 1024 * 1024) {
                    continue;
                }
                String content = readUtf8(file);
                if (content.indexOf(VALUE_24MP) < 0 && content.indexOf(TOKEN_24MP) < 0) {
                    continue;
                }
                String updated = content.replace(VALUE_24MP, VALUE_HIGH).replace(TOKEN_24MP, TOKEN_HIGH);
                if (!updated.equals(content)) {
                    writeUtf8(file, updated);
                    changed++;
                }
            }
            if (changed > 0) {
                log("sanitized unsupported S23 Ultra 24MP state from " + source + ", files=" + changed);
            }
        } catch (Throwable throwable) {
            log("sanitize unsupported 24MP state failed from " + source + ": " + throwable);
            log(throwable);
        }
    }

    private static String readUtf8(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private static void writeUtf8(File file, String content) throws IOException {
        FileOutputStream output = new FileOutputStream(file, false);
        try {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } finally {
            output.close();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.length() == 0;
    }

    private static String deviceSummary() {
        String model = nullToEmpty(Build.MODEL);
        String device = nullToEmpty(Build.DEVICE);
        String product = nullToEmpty(Build.PRODUCT);
        String productName = getSystemProperty("ro.product.product.name");
        return "model=" + model
                + ", product=" + product
                + ", device=" + device
                + ", productName=" + productName
                + ", supportedUltraModel=" + isUltraDevice()
                + ", force24Mp=" + shouldEnable24MpFeature();
    }

    private static boolean isUltraDevice() {
        String model = nullToEmpty(Build.MODEL);
        String device = nullToEmpty(Build.DEVICE);
        String product = nullToEmpty(Build.PRODUCT);
        String productName = getSystemProperty("ro.product.product.name");
        return startsWithAny(model, SUPPORTED_ULTRA_MODELS)
                || startsWithAny(product, SUPPORTED_ULTRA_PRODUCTS)
                || startsWithAny(device, SUPPORTED_ULTRA_PRODUCTS)
                || startsWithAny(productName, SUPPORTED_ULTRA_PRODUCTS);
    }

    private static boolean shouldEnable24MpFeature() {
        String model = nullToEmpty(Build.MODEL);
        String device = nullToEmpty(Build.DEVICE);
        String product = nullToEmpty(Build.PRODUCT);
        String productName = getSystemProperty("ro.product.product.name");
        return startsWithAny(model, SUPPORTED_24MP_MODELS)
                || startsWithAny(product, SUPPORTED_24MP_PRODUCTS)
                || startsWithAny(device, SUPPORTED_24MP_PRODUCTS)
                || startsWithAny(productName, SUPPORTED_24MP_PRODUCTS);
    }

    private static boolean shouldEnableProPictureSizeMenu() {
        return isUltraDevice();
    }

    private static boolean startsWithAny(String value, String[] prefixes) {
        String lower = nullToEmpty(value).toLowerCase();
        for (int i = 0; i < prefixes.length; i++) {
            if (lower.startsWith(prefixes[i].toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String getSystemProperty(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getDeclaredMethod("get", String.class, String.class);
            Object value = get.invoke(null, key, "");
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String enumName(Object value) {
        if (value instanceof Enum) {
            return ((Enum) value).name();
        }
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> enumClass, String name) {
        if (enumClass == null || name == null || !Enum.class.isAssignableFrom(enumClass)) {
            return null;
        }
        try {
            return Enum.valueOf((Class) enumClass, name);
        } catch (Throwable ignored) {
            return null;
        }
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

    private static final class FeatureApi {
        final String loaderClassName;
        final String booleanKeyClassName;
        final String stringKeyClassName;
        final String accessorClassName;
        final String booleanAccessorMethod;
        final String stringAccessorMethod;

        FeatureApi(
                String loaderClassName,
                String booleanKeyClassName,
                String stringKeyClassName,
                String accessorClassName,
                String booleanAccessorMethod,
                String stringAccessorMethod) {
            this.loaderClassName = loaderClassName;
            this.booleanKeyClassName = booleanKeyClassName;
            this.stringKeyClassName = stringKeyClassName;
            this.accessorClassName = accessorClassName;
            this.booleanAccessorMethod = booleanAccessorMethod;
            this.stringAccessorMethod = stringAccessorMethod;
        }
    }

    private interface Installer {
        void install() throws Throwable;
    }
}
