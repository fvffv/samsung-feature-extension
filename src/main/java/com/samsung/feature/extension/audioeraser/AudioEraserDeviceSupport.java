package com.samsung.feature.extension.audioeraser;

import android.os.Build;

import java.util.Locale;

/** Shared device gate for the Video Editor and SystemUI Audio Eraser hooks. */
public final class AudioEraserDeviceSupport {
    private static final int S24_ULTRA_FAMILY_NUMBER = 928;

    private static final String[] LEGACY_GALAXY_S_CODENAMES = {
            "dm",       // Galaxy S23 series
            "e1", "e2", // Galaxy S24 / S24+
            "r0", "g0", "b0", // Galaxy S22 series
            "o1", "t2", "p3", // Galaxy S21 series
            "x1", "y2", "z3", // Galaxy S20 series
            "r8", "r11", "r12", // Galaxy S FE variants through S24 FE
            "beyond"    // Galaxy S10 series
    };

    private AudioEraserDeviceSupport() {
    }

    public static boolean shouldHookCurrentDevice() {
        return isGalaxySBelowS24Ultra(Build.MODEL, Build.DEVICE, Build.PRODUCT);
    }

    static boolean isGalaxySBelowS24Ultra(
            String modelValue, String deviceValue, String productValue) {
        String model = safe(modelValue).trim().toUpperCase(Locale.ROOT);
        if (model.startsWith("SM-G")) {
            return true;
        }
        if (model.startsWith("SM-S")) {
            // Only the first three digits identify the device family. The last
            // digit in models such as SM-S9180 is a regional suffix.
            int familyNumber = parseFamilyNumber(model.substring(4));
            return familyNumber > 0 && familyNumber < S24_ULTRA_FAMILY_NUMBER;
        }

        return hasLegacyCodename(deviceValue) || hasLegacyCodename(productValue);
    }

    private static int parseFamilyNumber(String suffix) {
        if (suffix == null || suffix.length() < 3) {
            return -1;
        }
        for (int i = 0; i < 3; i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return -1;
            }
        }
        try {
            return Integer.parseInt(suffix.substring(0, 3));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean hasLegacyCodename(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        for (int i = 0; i < LEGACY_GALAXY_S_CODENAMES.length; i++) {
            if (normalized.startsWith(LEGACY_GALAXY_S_CODENAMES[i])) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
