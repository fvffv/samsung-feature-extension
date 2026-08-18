package com.samsung.feature.extension.camera;

import android.content.Context;
import android.app.Application;
import android.util.Range;

import com.samsung.feature.extension.CameraBitrateSettingsProvider;
import com.samsung.feature.extension.LogSettingsProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * One UI 8.5 camera compatibility branch.
 *
 * <p>Samsung Camera 16.5 migrated its feature accessor from x1.d/c0 to O1.f/Y and
 * its resolution chooser item from C2.u to i3.x. The older hook remains installed
 * for One UI 8.0 and earlier; this class only touches the newer signatures.</p>
 */
public final class CameraOneUi85VideoUnlockHook implements IXposedHookLoadPackage {
    private static final String TAG = "CameraOneUi85VideoUnlock";
    private static final String TARGET_PACKAGE = "com.sec.android.app.camera";
    private static final int ITEM_TYPE_FPS = 1;
    private static final int SLOW_MOTION_RECORDING_MODE = 12;
    private static final int SELECTION_NONE = 0;
    private static final int SELECTION_8K = 1;
    private static final int SELECTION_SLOW_MOTION = 2;

    private static final String[] FEATURE_KEYS = {
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X4320",
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X4320_24FPS",
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X4320_25FPS",
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X3296",
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X3296_24FPS",
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X3296_25FPS",
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X2160_120FPS",
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X1644_120FPS"
    };

    private static final Map<Object, Boolean> virtual8k60Items =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private static final Map<Object, Boolean> virtualUhd240Items =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private static final ThreadLocal<Integer> pendingSelection = new ThreadLocal<Integer>();

    private static volatile boolean featureAccessorHookInstalled;
    private static volatile boolean horizontalLockFeatureHookInstalled;
    private static volatile boolean chooserHookInstalled;
    private static volatile boolean profileHookInstalled;
    private static volatile boolean slowMotionProfileHookInstalled;
    private static volatile boolean recordingSessionFpsHookInstalled;
    private static volatile boolean recordingFpsRangeHookInstalled;
    private static volatile boolean recordingResolutionFpsHookInstalled;
    private static volatile boolean applicationHookInstalled;
    private static volatile boolean force8k60;
    private static volatile boolean forceUhdSlow240;
    private static volatile Context appContext;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        install("One UI 8.5 application", new Installer() {
            @Override
            public void install() throws Throwable {
                hookApplicationOnCreate();
            }
        });
        install("One UI 8.5 feature accessor", new Installer() {
            @Override
            public void install() throws Throwable {
                hookFeatureAccessor(lpparam.classLoader);
            }
        });
        install("One UI 8.5 horizontal lock feature", new Installer() {
            @Override
            public void install() throws Throwable {
                hookHorizontalLockFeature(lpparam.classLoader);
            }
        });
        install("One UI 8.5 chooser", new Installer() {
            @Override
            public void install() throws Throwable {
                hookChooser(lpparam.classLoader);
            }
        });
        install("One UI 8.5 recorder profile", new Installer() {
            @Override
            public void install() throws Throwable {
                hookRecorderProfile(lpparam.classLoader);
            }
        });
        install("One UI 8.5 slow-motion profile", new Installer() {
            @Override
            public void install() throws Throwable {
                hookSlowMotionProfile(lpparam.classLoader);
            }
        });
        install("One UI 8.5 recording-session fps", new Installer() {
            @Override
            public void install() throws Throwable {
                hookRecordingSessionFps(lpparam.classLoader);
            }
        });
        install("One UI 8.5 recording fps range", new Installer() {
            @Override
            public void install() throws Throwable {
                hookRecordingFpsRange(lpparam.classLoader);
            }
        });
        install("One UI 8.5 recording resolution fps", new Installer() {
            @Override
            public void install() throws Throwable {
                hookRecordingResolutionFps(lpparam.classLoader);
            }
        });
    }

    private static void hookApplicationOnCreate() throws Throwable {
        if (applicationHookInstalled) {
            return;
        }
        Method onCreate = Application.class.getDeclaredMethod("onCreate");
        XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Application) {
                    appContext = ((Application) param.thisObject).getApplicationContext();
                }
            }
        });
        applicationHookInstalled = true;
    }

    private static void hookFeatureAccessor(final ClassLoader classLoader) throws Throwable {
        if (featureAccessorHookInstalled) {
            return;
        }
        Class<?> keyClass = Class.forName("O1.k", false, classLoader);
        Class<?> accessorClass = Class.forName("O1.f", false, classLoader);
        Method getFeature = accessorClass.getDeclaredMethod("Y", keyClass);
        XposedBridge.hookMethod(getFeature, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = enumName(firstArg(param));
                if (!isTargetFeatureKey(key)) {
                    return;
                }
                Map patched = copyFeatureMap(param.getResult());
                patched.put("value", "true");
                patched.put("hdr", "true");
                patched.put("hdr10", "true");
                ensureSupportedModes(key, patched);
                param.setResult(patched);
            }
        });
        featureAccessorHookInstalled = true;
    }

    /**
     * Samsung Camera gates the horizontal-lock variant of Super Steady with
     * {@code SUPPORT_SUPER_STEADY_HORIZONTAL_LOCK}.  In Camera 16.5 this gate
     * is based on the device launch SDK (S24 and later), rather than the video
     * resolution's Super VDIS capability.  The camera already contains the
     * complete UI, command, and recording-engine mode for this option.
     *
     * <p>Keep this as a narrow result override.  Older camera builds that do
     * not expose the {@code C.e / O1.d} API simply skip it and continue using
     * {@link CameraProVideoHdr10Hook}; current Camera 16.5 builds only receive
     * a {@code true} result for this one feature key.</p>
     */
    private static void hookHorizontalLockFeature(final ClassLoader classLoader) throws Throwable {
        if (horizontalLockFeatureHookInstalled) {
            return;
        }
        Class<?> keyClass = Class.forName("O1.d", false, classLoader);
        Class<?> featureClass = Class.forName("C.e", false, classLoader);
        Method isFeatureSupported = featureClass.getDeclaredMethod("V", keyClass);
        isFeatureSupported.setAccessible(true);
        XposedBridge.hookMethod(isFeatureSupported, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if ("SUPPORT_SUPER_STEADY_HORIZONTAL_LOCK".equals(enumName(firstArg(param)))) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
        horizontalLockFeatureHookInstalled = true;
    }

    private static void hookChooser(final ClassLoader classLoader) throws Throwable {
        if (chooserHookInstalled) {
            return;
        }
        final Class<?> resolutionClass = Class.forName(
                "com.sec.android.app.camera.interfaces.Resolution", false, classLoader);
        final Class<?> keyClass = Class.forName(
                "com.sec.android.app.camera.interfaces.CameraSettings$Key", false, classLoader);
        final Class<?> presenterClass = Class.forName(
                "com.sec.android.app.camera.layer.menu.chooser.VideoResolutionChooserMenuPresenter",
                false,
                classLoader);
        final Class<?> adapterClass = Class.forName(
                "com.sec.android.app.camera.layer.menu.chooser.VideoResolutionChooserAdapter",
                false,
                classLoader);
        final Class<?> itemClass = Class.forName("i3.x", false, classLoader);
        final Class<?> listenerClass = Class.forName(
                "com.sec.android.app.camera.layer.menu.chooser.w", false, classLoader);

        final Field itemListField = findField(adapterClass, ArrayList.class, "mItemList");
        final Field itemResolutionField = findField(itemClass, resolutionClass, "a");
        final Field itemTypeField = findField(itemClass, Integer.TYPE, "b");
        final Field itemSelectedField = findField(itemClass, Boolean.TYPE, "c");
        final Field settingKeyField = findField(presenterClass, keyClass, "mSettingKey");
        final Field listenerViewField = findField(listenerClass, Object.class, "a");
        final Constructor<?> itemConstructor = itemClass.getDeclaredConstructor(resolutionClass, Integer.TYPE);
        itemConstructor.setAccessible(true);
        final Method itemText = itemClass.getDeclaredMethod("a", Context.class);
        final Method makeFpsAdapter = presenterClass.getDeclaredMethod(
                "makeFpsAdapter", resolutionClass, HashMap.class);
        final Method setSelected = adapterClass.getDeclaredMethod("setSelected", resolutionClass);
        final Method onItemClick = listenerClass.getDeclaredMethod("onItemClick", itemClass);
        final Method onResolutionClicked = presenterClass.getDeclaredMethod(
                "onResolutionClicked", resolutionClass);
        final Method restartExpiredTimer = presenterClass.getDeclaredMethod("restartExpiredTimer");
        final Method handleResolutionChanged = presenterClass.getDeclaredMethod(
                "handleResolutionChanged", resolutionClass);
        restartExpiredTimer.setAccessible(true);
        handleResolutionChanged.setAccessible(true);

        XposedBridge.hookMethod(makeFpsAdapter, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object resolution = firstArg(param);
                boolean slowMotion = isSlowMotionKey(readField(settingKeyField, param.thisObject));
                boolean is8k = !slowMotion && is8kResolution(resolution);
                boolean isSlowUhd = slowMotion
                        && isUhd120Resolution(resolution);
                if (!is8k && !isSlowUhd) {
                    return;
                }
                try {
                    Object adapter = param.getResult();
                    Object rawItems = readField(itemListField, adapter);
                    if (!(rawItems instanceof List)) {
                        return;
                    }
                    List items = (List) rawItems;
                    if (is8k && !hasVirtualItem(items, virtual8k60Items)) {
                        Object item = itemConstructor.newInstance(resolution, Integer.valueOf(ITEM_TYPE_FPS));
                        virtual8k60Items.put(item, Boolean.TRUE);
                        items.add(0, item);
                        log("8K60 option added");
                    }
                    if (isSlowUhd && !hasVirtualItem(items, virtualUhd240Items)) {
                        Object item = itemConstructor.newInstance(resolution, Integer.valueOf(ITEM_TYPE_FPS));
                        virtualUhd240Items.put(item, Boolean.TRUE);
                        items.add(0, item);
                        log("UHD240 slow-motion option added");
                    }
                } catch (Throwable throwable) {
                    log("fps option injection failed: " + throwable);
                }
            }
        });

        XposedBridge.hookMethod(itemText, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (virtual8k60Items.containsKey(param.thisObject)) {
                    param.setResult("60");
                } else if (virtualUhd240Items.containsKey(param.thisObject)) {
                    param.setResult("240");
                }
            }
        });

        XposedBridge.hookMethod(onItemClick, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object item = firstArg(param);
                if (item == null || getInt(itemTypeField, item) != ITEM_TYPE_FPS) {
                    return;
                }
                Object resolution = readField(itemResolutionField, item);
                if (virtual8k60Items.containsKey(item)) {
                    force8k60 = true;
                    forceUhdSlow240 = false;
                    pendingSelection.set(Integer.valueOf(SELECTION_8K));
                } else if (virtualUhd240Items.containsKey(item)) {
                    force8k60 = false;
                    forceUhdSlow240 = true;
                    pendingSelection.set(Integer.valueOf(SELECTION_SLOW_MOTION));
                } else if (is8kResolution(resolution)) {
                    force8k60 = false;
                    forceUhdSlow240 = false;
                    pendingSelection.set(Integer.valueOf(SELECTION_8K));
                } else if (isUhd120Resolution(resolution)
                        && isSlowMotionListener(listenerViewField, param.thisObject)) {
                    force8k60 = false;
                    forceUhdSlow240 = false;
                    pendingSelection.set(Integer.valueOf(SELECTION_SLOW_MOTION));
                }
            }
        });

        XposedBridge.hookMethod(onResolutionClicked, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int selection = consumePendingSelection();
                Object resolution = firstArg(param);
                boolean isExpected = (selection == SELECTION_8K && is8kResolution(resolution))
                        || (selection == SELECTION_SLOW_MOTION && isUhd120Resolution(resolution));
                if (!isExpected) {
                    return;
                }
                try {
                    restartExpiredTimer.invoke(param.thisObject);
                    handleResolutionChanged.invoke(param.thisObject, resolution);
                    param.setResult(null);
                } catch (Throwable throwable) {
                    log("fps selection reconfigure failed: " + throwable);
                }
            }
        });

        XposedBridge.hookMethod(setSelected, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object rawItems = readField(itemListField, param.thisObject);
                    if (!(rawItems instanceof List)) {
                        return;
                    }
                    updateVirtualSelection((List) rawItems, itemSelectedField);
                } catch (Throwable throwable) {
                    log("fps selection display update failed: " + throwable);
                }
            }
        });

        chooserHookInstalled = true;
    }

    private static void hookRecorderProfile(final ClassLoader classLoader) throws Throwable {
        if (profileHookInstalled) {
            return;
        }
        final Class<?> profileClass = Class.forName(
                "com.sec.android.app.camera.engine.recording.session.MediaRecorderProfile$Profile",
                false,
                classLoader);
        final Field widthField = findField(profileClass, Integer.TYPE, "mVideoWidth");
        final Field heightField = findField(profileClass, Integer.TYPE, "mVideoHeight");
        final Field frameRateField = findField(profileClass, Integer.TYPE, "mVideoFrameRate");
        final Field recordingModeField = findField(profileClass, Integer.TYPE, "mRecordingMode");
        XC_MethodHook videoFrameRateOverride = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                int width = getInt(widthField, param.thisObject);
                int height = getInt(heightField, param.thisObject);
                if (force8k60 && is8kSize(width, height)) {
                    param.setResult(Integer.valueOf(60));
                }
            }
        };
        XC_MethodHook captureRateOverride = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                int width = getInt(widthField, param.thisObject);
                int height = getInt(heightField, param.thisObject);
                int recordingMode = getInt(recordingModeField, param.thisObject);
                int fps = forcedCaptureRateForSize(width, height, recordingMode);
                if (fps > 0) {
                    param.setResult(Integer.valueOf(fps));
                }
            }
        };
        XposedBridge.hookMethod(profileClass.getDeclaredMethod("getVideoFrameRate"), videoFrameRateOverride);
        XposedBridge.hookMethod(profileClass.getDeclaredMethod("getCaptureRate"), captureRateOverride);

        XC_MethodHook videoBitrateOverride = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                long originalBitrate = longResult(param.getResult());
                int width = getInt(widthField, param.thisObject);
                int height = getInt(heightField, param.thisObject);
                int fps = getInt(frameRateField, param.thisObject);
                int recordingMode = getInt(recordingModeField, param.thisObject);
                if (force8k60 && is8kSize(width, height)) {
                    fps = 60;
                } else if (forceUhdSlow240 && recordingMode == SLOW_MOTION_RECORDING_MODE
                        && isUhdSize(width, height)) {
                    fps = 240;
                }
                long bitrate = getConfiguredVideoBitrate(originalBitrate, width, height, fps);
                if (bitrate != originalBitrate) {
                    param.setResult(Long.valueOf(bitrate));
                }
            }
        };
        // Camera 16.5 reads these two accessors before it configures either a
        // MediaCodec or MediaRecorder session. Hook both so the visible target
        // setting and the storage estimate always agree.
        XposedBridge.hookMethod(
                profileClass.getDeclaredMethod("getVideoEncodingBitrate"), videoBitrateOverride);
        XposedBridge.hookMethod(
                profileClass.getDeclaredMethod("getVideoEncodingBitrateForStorage"), videoBitrateOverride);
        profileHookInstalled = true;
    }

    private static void hookSlowMotionProfile(final ClassLoader classLoader) throws Throwable {
        if (slowMotionProfileHookInstalled) {
            return;
        }
        final Class<?> resolutionClass = Class.forName(
                "com.sec.android.app.camera.interfaces.Resolution", false, classLoader);
        Class<?> profileFactoryClass = Class.forName(
                "com.sec.android.app.camera.engine.recording.session.MediaRecorderProfile",
                false,
                classLoader);
        Method captureRate = profileFactoryClass.getDeclaredMethod(
                "getSlowMotionCaptureRate", Integer.TYPE, resolutionClass);
        Method recordingMode = profileFactoryClass.getDeclaredMethod(
                "getSlowMotionAvcRecordingMode", Integer.TYPE, resolutionClass);
        captureRate.setAccessible(true);
        recordingMode.setAccessible(true);
        XposedBridge.hookMethod(captureRate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (forceUhdSlow240 && isUhd120Resolution(secondArg(param))) {
                    param.setResult(Integer.valueOf(240));
                }
            }
        });
        XposedBridge.hookMethod(recordingMode, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (forceUhdSlow240 && isUhd120Resolution(secondArg(param))) {
                    // Samsung uses recording mode 12 for 240fps slow-motion capture.
                    param.setResult(Integer.valueOf(12));
                }
            }
        });
        slowMotionProfileHookInstalled = true;
    }

    /**
     * Samsung Camera 16.5 captures the resolution's maximum frame rate when it
     * creates SettingInfo, then the maker reads that stored value to configure
     * the camera stream.  Profile-only overrides leave the maker at 30fps and
     * make the later 60fps encoder setup fail.  This hook is deliberately on
     * the recording-session object rather than Resolution, so menu labels and
     * unrelated shooting modes retain their native frame rates.
     */
    private static void hookRecordingSessionFps(final ClassLoader classLoader) throws Throwable {
        if (recordingSessionFpsHookInstalled) {
            return;
        }
        final Class<?> settingInfoClass = Class.forName(
                "com.sec.android.app.camera.engine.recording.session.SettingInfo",
                false,
                classLoader);
        final Method getResolution = settingInfoClass.getDeclaredMethod("getResolution");
        Method getMaxFps = settingInfoClass.getDeclaredMethod("getMaxFps");
        XposedBridge.hookMethod(getMaxFps, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object resolution = getResolution.invoke(param.thisObject);
                    if (force8k60 && is8kResolution(resolution)) {
                        param.setResult(Integer.valueOf(60));
                    } else if (forceUhdSlow240 && isUhd120Resolution(resolution)) {
                        param.setResult(Integer.valueOf(240));
                    }
                } catch (Throwable throwable) {
                    log("recording-session fps override failed: " + throwable);
                }
            }
        });
        recordingSessionFpsHookInstalled = true;
    }

    /**
     * The 8.5 recording manager creates the camera2 AE target range directly
     * from {@link Resolution}, even after the 60fps value has been selected in
     * the recording session. Keep this override on that one recording API so
     * camera menus and all non-recording modes retain their stock fps values.
     */
    private static void hookRecordingFpsRange(final ClassLoader classLoader) throws Throwable {
        if (recordingFpsRangeHookInstalled) {
            return;
        }
        final Class<?> resolutionClass = Class.forName(
                "com.sec.android.app.camera.interfaces.Resolution", false, classLoader);
        final Class<?> capabilityClass = Class.forName(
                "com.sec.android.app.camera.engine.interfaces.Capability", false, classLoader);
        final Class<?> recordingManagerClass = Class.forName(
                "com.sec.android.app.camera.engine.recording.RecordingManagerImpl", false, classLoader);
        Method getRecordingFpsRange = recordingManagerClass.getDeclaredMethod(
                "getRecordingFpsRange", capabilityClass, resolutionClass);
        XposedBridge.hookMethod(getRecordingFpsRange, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object resolution = secondArg(param);
                if (force8k60 && is8kResolution(resolution)) {
                    param.setResult(new Range<Integer>(Integer.valueOf(60), Integer.valueOf(60)));
                }
            }
        });
        recordingFpsRangeHookInstalled = true;
    }

    /**
     * RecordingSession uses Resolution directly to configure the encoder's
     * frame-rate and operating-rate. Only replace those calls while a Samsung
     * recording implementation is on the stack; the resolution chooser keeps
     * reporting its native 24/30fps entries plus the virtual 60fps item.
     */
    private static void hookRecordingResolutionFps(final ClassLoader classLoader) throws Throwable {
        if (recordingResolutionFpsHookInstalled) {
            return;
        }
        final Class<?> resolutionClass = Class.forName(
                "com.sec.android.app.camera.interfaces.Resolution", false, classLoader);
        XC_MethodHook override = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (force8k60 && is8kResolution(param.thisObject) && isRecordingFpsCall()) {
                    param.setResult(Integer.valueOf(60));
                }
            }
        };
        XposedBridge.hookMethod(resolutionClass.getDeclaredMethod("getMaxFps"), override);
        XposedBridge.hookMethod(resolutionClass.getDeclaredMethod("getMinFps"), override);
        recordingResolutionFpsHookInstalled = true;
    }

    private static void updateVirtualSelection(List items, Field selectedField) throws IllegalAccessException {
        boolean contains8k = hasVirtualItem(items, virtual8k60Items);
        boolean containsUhd = hasVirtualItem(items, virtualUhd240Items);
        if (!contains8k && !containsUhd) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (virtual8k60Items.containsKey(item)) {
                selectedField.setBoolean(item, force8k60);
            } else if (virtualUhd240Items.containsKey(item)) {
                selectedField.setBoolean(item, forceUhdSlow240);
            } else if ((contains8k && is8kResolutionFromItem(item))
                    || (containsUhd && isUhd120ResolutionFromItem(item))) {
                selectedField.setBoolean(item, false);
            }
        }
    }

    private static boolean is8kResolutionFromItem(Object item) {
        return resolutionNameFromItem(item).indexOf("RESOLUTION_7680X") == 0;
    }

    private static boolean isUhd120ResolutionFromItem(Object item) {
        return "RESOLUTION_3840X2160_120FPS".equals(resolutionNameFromItem(item))
                || "RESOLUTION_3840X1644_120FPS".equals(resolutionNameFromItem(item));
    }

    private static String resolutionNameFromItem(Object item) {
        if (item == null) {
            return "";
        }
        try {
            Field field = item.getClass().getDeclaredField("a");
            field.setAccessible(true);
            return enumName(field.get(item));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isSlowMotionListener(Field viewField, Object listener) {
        try {
            Object view = readField(viewField, listener);
            if (view == null) {
                return false;
            }
            Field menuId = findField(view.getClass(), Object.class, "mMenuId");
            return isSlowMotionKey(readField(menuId, view));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int forcedCaptureRateForSize(int width, int height, int recordingMode) {
        int longSide = Math.max(width, height);
        int shortSide = Math.min(width, height);
        if (force8k60 && is8kSize(width, height)) {
            return 60;
        }
        if (forceUhdSlow240 && recordingMode == SLOW_MOTION_RECORDING_MODE
                && longSide >= 3800 && shortSide >= 1600) {
            return 240;
        }
        return 0;
    }

    private static long getConfiguredVideoBitrate(long originalBitrate, int width, int height, int fps) {
        int category = CameraBitrateSettingsProvider.videoCategoryForSize(width, height);
        if (category < 0) {
            return originalBitrate;
        }
        if (originalBitrate > 0L) {
            CameraBitrateSettingsProvider.reportVideoObserved(
                    appContext, originalBitrate, width, height, fps);
        }
        CameraBitrateSettingsProvider.Settings settings =
                CameraBitrateSettingsProvider.getSettings(appContext);
        int targetBitrate = settings.videoTargetBitrateBps(category);
        if (targetBitrate <= 0) {
            return originalBitrate;
        }
        log("overriding " + CameraBitrateSettingsProvider.videoLabel(category)
                + " bitrate " + originalBitrate
                + " -> " + targetBitrate
                + ", size=" + width + "x" + height
                + ", fps=" + fps);
        return targetBitrate;
    }

    private static boolean is8kSize(int width, int height) {
        int longSide = Math.max(width, height);
        int shortSide = Math.min(width, height);
        return longSide >= 7600 && shortSide >= 3200;
    }

    private static boolean isUhdSize(int width, int height) {
        int longSide = Math.max(width, height);
        int shortSide = Math.min(width, height);
        return longSide >= 3800 && shortSide >= 1600;
    }

    private static boolean hasVirtualItem(List items, Map<Object, Boolean> virtualItems) {
        for (int i = 0; i < items.size(); i++) {
            if (virtualItems.containsKey(items.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static Map copyFeatureMap(Object source) {
        return source instanceof Map ? new HashMap((Map) source) : new HashMap();
    }

    private static void ensureSupportedModes(String featureKey, Map feature) {
        Object value = feature.get("supported-mode");
        String modes = value instanceof String ? (String) value : "";
        if (featureKey.indexOf("7680X") >= 0) {
            modes = appendMode(modes, "video");
            modes = appendMode(modes, "pro_video");
        } else if (featureKey.indexOf("3840X") >= 0) {
            modes = appendMode(modes, "slow_motion");
        }
        feature.put("supported-mode", modes);
    }

    private static String appendMode(String modes, String required) {
        if (modes == null || modes.length() == 0) {
            return required;
        }
        String[] parts = modes.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (required.equals(parts[i].trim())) {
                return modes;
            }
        }
        return modes + "," + required;
    }

    private static boolean isTargetFeatureKey(String key) {
        for (int i = 0; i < FEATURE_KEYS.length; i++) {
            if (FEATURE_KEYS[i].equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean is8kResolution(Object resolution) {
        String name = enumName(resolution);
        return name.indexOf("RESOLUTION_7680X4320") == 0 || name.indexOf("RESOLUTION_7680X3296") == 0;
    }

    private static boolean isUhd120Resolution(Object resolution) {
        String name = enumName(resolution);
        return "RESOLUTION_3840X2160_120FPS".equals(name)
                || "RESOLUTION_3840X1644_120FPS".equals(name);
    }

    private static boolean isSlowMotionKey(Object key) {
        return enumName(key).indexOf("SLOW_MOTION") >= 0;
    }

    private static boolean isRecordingFpsCall() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String className = stack[i].getClassName();
            if (className.startsWith("com.sec.android.app.camera.engine.recording.")) {
                return true;
            }
        }
        return false;
    }

    private static int consumePendingSelection() {
        Integer value = pendingSelection.get();
        pendingSelection.remove();
        return value == null ? SELECTION_NONE : value.intValue();
    }

    private static Object firstArg(XC_MethodHook.MethodHookParam param) {
        return param == null || param.args == null || param.args.length == 0 ? null : param.args[0];
    }

    private static Object secondArg(XC_MethodHook.MethodHookParam param) {
        return param == null || param.args == null || param.args.length < 2 ? null : param.args[1];
    }

    private static long longResult(Object result) {
        return result instanceof Number ? ((Number) result).longValue() : 0L;
    }

    private static int getInt(Field field, Object owner) {
        try {
            return field == null || owner == null ? 0 : field.getInt(owner);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Object readField(Field field, Object owner) {
        try {
            return field == null || owner == null ? null : field.get(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, Class<?> expectedType, String preferredName)
            throws NoSuchFieldException {
        if (preferredName != null) {
            try {
                Field field = owner.getDeclaredField(preferredName);
                if (matchesType(field, expectedType)) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // Fall through to a type-based lookup for minor Samsung revisions.
            }
        }
        Field[] fields = owner.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (matchesType(field, expectedType)) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new NoSuchFieldException("No " + expectedType.getName() + " field in " + owner.getName());
    }

    private static boolean matchesType(Field field, Class<?> expectedType) {
        if (expectedType.isPrimitive()) {
            return field.getType() == expectedType;
        }
        return expectedType.isAssignableFrom(field.getType());
    }

    private static String enumName(Object value) {
        return value instanceof Enum ? ((Enum) value).name() : value == null ? "" : String.valueOf(value);
    }

    private static void install(String name, Installer installer) {
        try {
            installer.install();
            log(name + " installed");
        } catch (Throwable throwable) {
            log(name + " unavailable: " + throwable);
        }
    }

    private static void log(String message) {
        if (LogSettingsProvider.isLogEnabled(appContext)) {
            XposedBridge.log(TAG + ": " + message);
        }
    }

    private interface Installer {
        void install() throws Throwable;
    }
}
