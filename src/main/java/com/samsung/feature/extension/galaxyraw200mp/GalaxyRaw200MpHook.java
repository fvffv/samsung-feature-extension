package com.samsung.feature.extension.galaxyraw200mp;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private static volatile boolean accessorsInstalled;

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
        installHook("Application.onCreate reinjection", new Installer() {
            @Override
            public void install() throws Throwable {
                hookApplicationOnCreate(lpparam.classLoader);
            }
        });
    }

    private static void hookFeatureLoader(final ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        Method loadFeature = Class.forName("H1.g", false, classLoader).getDeclaredMethod("f", Context.class);
        XposedBridge.hookMethod(loadFeature, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                injectFeatureMap(classLoader, "H1.g.f");
            }
        });
    }

    private static void hookFeatureAccessors(final ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        if (accessorsInstalled) {
            return;
        }
        final Class<?> booleanFeatureKey = Class.forName("H1.a", false, classLoader);
        final Class<?> stringFeatureKey = Class.forName("H1.l", false, classLoader);
        Class<?> featureAccessor = Class.forName("B2.a", false, classLoader);

        Method booleanAccessor = featureAccessor.getDeclaredMethod("p", booleanFeatureKey);
        XposedBridge.hookMethod(booleanAccessor, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = enumName(param.args != null && param.args.length > 0 ? param.args[0] : null);
                if (SUPPORT_ULTRA.equals(key)
                        || SUPPORT_24MP_MENU.equals(key)
                        || SUPPORT_HIGH.equals(key)
                        || SUPPORT_ADAPTIVE_PIXEL.equals(key)) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });

        Method stringAccessor = featureAccessor.getDeclaredMethod("k", stringFeatureKey);
        XposedBridge.hookMethod(stringAccessor, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = enumName(param.args != null && param.args.length > 0 ? param.args[0] : null);
                Object current = param.getResult();
                String value = current instanceof String ? (String) current : "";
                String forced = forcedResolution(key, value);
                if (forced != null) {
                    param.setResult(forced);
                }
            }
        });

        accessorsInstalled = true;
        log("feature accessor hooks installed");
    }

    private static void hookApplicationOnCreate(final ClassLoader classLoader) throws NoSuchMethodException {
        Method onCreate = Application.class.getDeclaredMethod("onCreate");
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                ClassLoader appClassLoader = classLoader;
                if (param.thisObject instanceof Application) {
                    appClassLoader = ((Application) param.thisObject).getClassLoader();
                }
                injectFeatureMap(appClassLoader, "Application.onCreate");
            }
        });
    }

    private static void injectFeatureMap(ClassLoader classLoader, String source) {
        try {
            Class<?> featureLoader = Class.forName("H1.g", false, classLoader);
            Field mapField = featureLoader.getDeclaredField("b");
            mapField.setAccessible(true);
            Object mapObject = mapField.get(null);
            if (!(mapObject instanceof Map)) {
                log("feature map not available from " + source);
                return;
            }
            Map map = (Map) mapObject;
            putLocal(map, SUPPORT_HIGH, "true");
            putLocal(map, SUPPORT_ULTRA, "true");
            putLocal(map, SUPPORT_ADAPTIVE_PIXEL, "true");
            putLocal(map, RES_ULTRA, "16320x12240");
            putLocalIfBlank(map, RES_HIGH, "8160x6120");
            putLocalIfBlank(map, RES_24MP, "5712x4284");
            log("feature map patched from " + source + ", entries=" + map.size() + ", " + deviceSummary());
        } catch (Throwable throwable) {
            log("feature map patch failed from " + source + ": " + throwable);
            XposedBridge.log(throwable);
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

    private static String forcedResolution(String key, String current) {
        if (RES_ULTRA.equals(key)) {
            return "16320x12240";
        }
        if (RES_HIGH.equals(key) && isBlank(current)) {
            return "8160x6120";
        }
        if (RES_24MP.equals(key) && isBlank(current)) {
            return "5712x4284";
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.length() == 0;
    }

    private static String deviceSummary() {
        String model = nullToEmpty(Build.MODEL);
        String device = nullToEmpty(Build.DEVICE);
        String product = nullToEmpty(Build.PRODUCT);
        String productName = getSystemProperty("ro.product.product.name");
        boolean supported = startsWithAny(model, SUPPORTED_ULTRA_MODELS)
                || startsWithAny(product, SUPPORTED_ULTRA_PRODUCTS)
                || startsWithAny(device, SUPPORTED_ULTRA_PRODUCTS)
                || startsWithAny(productName, SUPPORTED_ULTRA_PRODUCTS);
        return "model=" + model
                + ", product=" + product
                + ", device=" + device
                + ", productName=" + productName
                + ", supportedUltraModel=" + supported;
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

    private static void installHook(String name, Installer installer) {
        try {
            installer.install();
            log(name + " installed");
        } catch (Throwable throwable) {
            log(name + " failed: " + throwable);
            XposedBridge.log(throwable);
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private interface Installer {
        void install() throws Throwable;
    }
}
