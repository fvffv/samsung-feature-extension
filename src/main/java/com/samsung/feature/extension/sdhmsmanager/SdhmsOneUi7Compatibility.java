package com.samsung.feature.extension.sdhmsmanager;

import android.content.Context;
import android.os.Build;

import java.lang.reflect.Method;

/**
 * Explicit compatibility branch for the Android 15 / One UI 7 SDHMS build.
 *
 * <p>The older and newer SDHMS implementations keep using the existing
 * discovery path in {@link SdhmsManagerHook}.  This class only recognizes the
 * API 35 implementation whose thermal limiter moved to {@code O1.o2}.</p>
 */
final class SdhmsOneUi7Compatibility {
    private static final int ANDROID_15_API_LEVEL = 35;
    private static final String LIMITER_CLASS_NAME = "O1.o2";

    private SdhmsOneUi7Compatibility() {
    }

    static Object resolveLimiter(Context context) throws Exception {
        if (context == null || Build.VERSION.SDK_INT != ANDROID_15_API_LEVEL) {
            return null;
        }

        Class<?> limiterClass = Class.forName(
                LIMITER_CLASS_NAME,
                false,
                context.getClassLoader()
        );
        requireApi(limiterClass);
        Method factory = limiterClass.getDeclaredMethod("g", Context.class);
        factory.setAccessible(true);
        return factory.invoke(null, context);
    }

    private static void requireApi(Class<?> limiterClass) throws NoSuchMethodException {
        limiterClass.getDeclaredMethod("d", boolean.class);
        limiterClass.getDeclaredMethod("h");
        limiterClass.getDeclaredMethod("i");
        limiterClass.getDeclaredMethod("j");
        limiterClass.getDeclaredMethod("k", boolean.class);
        limiterClass.getDeclaredMethod("l", boolean.class);
    }
}
