package de.robv.android.xposed;

import java.lang.reflect.Member;

public final class XposedBridge {
    private XposedBridge() {
    }

    public static void log(String text) {
    }

    public static void log(Throwable throwable) {
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }
}
