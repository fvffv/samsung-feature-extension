package de.robv.android.xposed;

public final class XposedHelpers {
    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static Object callMethod(Object object, String methodName, Object... args) {
        return null;
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return null;
    }

    public static Object getObjectField(Object object, String fieldName) {
        return null;
    }

    public static int getIntField(Object object, String fieldName) {
        return 0;
    }

    public static long getLongField(Object object, String fieldName) {
        return 0L;
    }

    public static void setIntField(Object object, String fieldName, int value) {
    }

    public static void setLongField(Object object, String fieldName, long value) {
    }

    public static void setObjectField(Object object, String fieldName, Object value) {
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        return null;
    }

    public static Object newInstance(Class<?> clazz, Object... args) {
        return null;
    }
}
