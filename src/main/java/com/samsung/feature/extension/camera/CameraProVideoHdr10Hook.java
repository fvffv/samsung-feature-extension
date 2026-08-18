package com.samsung.feature.extension.camera;

import android.app.Application;
import android.content.Context;
import android.media.MediaFormat;
import android.media.MediaRecorder;

import com.samsung.feature.extension.CameraBitrateSettingsProvider;
import com.samsung.feature.extension.LogSettingsProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CameraProVideoHdr10Hook implements IXposedHookLoadPackage {
    private static final String TAG = "CameraProVideoHdr10Lsp";
    private static final String TARGET_PACKAGE = "com.sec.android.app.camera";
    private static final String FEATURE_UHD_120 =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X2160_120FPS";
    private static final String FEATURE_UHD_WIDE_120 =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X1644_120FPS";
    private static final String FEATURE_FHD_120 =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_1920X1080_120FPS";
    private static final String FEATURE_FHD_WIDE_120 =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_1920X824_120FPS";
    private static final String FEATURE_8K =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X4320";
    private static final String FEATURE_8K_24 =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X4320_24FPS";
    private static final String FEATURE_8K_WIDE =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X3296";
    private static final String FEATURE_8K_WIDE_24 =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_7680X3296_24FPS";
    private static final String[] TARGET_FEATURES = {
            FEATURE_UHD_120,
            FEATURE_UHD_WIDE_120,
            FEATURE_FHD_120,
            FEATURE_FHD_WIDE_120,
            FEATURE_8K,
            FEATURE_8K_24,
            FEATURE_8K_WIDE,
            FEATURE_8K_WIDE_24
    };
    private static final String KEY_BACK_PRO_RESOLUTION = "BACK_CAMCORDER_PRO_RESOLUTION";
    private static final int BACK_CAMERA_FACING = 0;
    private static final int VIRTUAL_ITEM_TYPE_FPS = 1;

    private static volatile boolean featureAccessorHooksInstalled;
    private static volatile boolean cameraResolutionHooksInstalled;
    private static volatile boolean cameraSettingsHooksInstalled;
    private static volatile boolean shootingModeFeatureHookInstalled;
    private static volatile boolean videoResolutionChooserHooksInstalled;
    private static volatile boolean resolutionHooksInstalled;
    private static volatile boolean bitrateHooksInstalled;
    private static volatile boolean force8k60Selection;
    private static volatile boolean forceUhdSlow240Selection;
    private static volatile Context appContext;
    private static final ThreadLocal pending8kFpsToggle = new ThreadLocal();
    private static final ThreadLocal pendingUhdSlowFpsToggle = new ThreadLocal();
    private static final Map virtual8k60Items =
            Collections.synchronizedMap(new WeakHashMap());
    private static final Map virtualUhdSlow240Items =
            Collections.synchronizedMap(new WeakHashMap());
    private static final Map recorderStates =
            Collections.synchronizedMap(new WeakHashMap());
    private static final Map mediaFormatStates =
            Collections.synchronizedMap(new WeakHashMap());

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        // Samsung Camera 16.5 (One UI 8.5) replaced C2.u with i3.x.  Do not
        // leave the legacy global FPS hooks active there: they change every
        // same-size menu item and make slow motion play at normal speed.
        if (!hasLegacyChooser(lpparam.classLoader)) {
            log("legacy chooser not found; One UI 8.5 branch owns this process");
            return;
        }

        log("loaded for " + lpparam.packageName + " process=" + lpparam.processName);
        installHook("feature map injection", new Installer() {
            @Override
            public void install() {
                patchFeatureMap(lpparam.classLoader, "handleLoadPackage");
            }
        });
        installHook("Application.onCreate reinjection", new Installer() {
            @Override
            public void install() throws Throwable {
                hookApplicationOnCreate(lpparam.classLoader);
            }
        });
        installHook("feature accessor overrides", new Installer() {
            @Override
            public void install() throws Throwable {
                hookFeatureAccessors(lpparam.classLoader);
            }
        });
        installHook("camera resolution overrides", new Installer() {
            @Override
            public void install() throws Throwable {
                hookCameraResolution(lpparam.classLoader);
            }
        });
        installHook("camera settings dimmer overrides", new Installer() {
            @Override
            public void install() throws Throwable {
                hookCameraSettings(lpparam.classLoader);
            }
        });
        installHook("shooting mode HDR10+ support", new Installer() {
            @Override
            public void install() throws Throwable {
                hookShootingModeFeature(lpparam.classLoader);
            }
        });
        installHook("experimental 8K60 chooser", new Installer() {
            @Override
            public void install() throws Throwable {
                hookVideoResolutionChooser(lpparam.classLoader);
            }
        });
        installHook("experimental 8K60 resolution fps", new Installer() {
            @Override
            public void install() throws Throwable {
                hookResolutionFps(lpparam.classLoader);
            }
        });
        installHook("video bitrate observer", new Installer() {
            @Override
            public void install() throws Throwable {
                hookVideoBitrateOverride();
            }
        });
    }

    private static boolean hasLegacyChooser(ClassLoader classLoader) {
        try {
            Class.forName("C2.u", false, classLoader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
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
                    appClassLoader = application.getClassLoader();
                }
                patchFeatureMap(appClassLoader, "Application.onCreate");
            }
        });
    }

    private static void hookFeatureAccessors(final ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        if (featureAccessorHooksInstalled) {
            return;
        }
        Class<?> resolutionFeatureKey = Class.forName("x1.i", false, classLoader);
        Class<?> featureAccessor = Class.forName("x1.d", false, classLoader);
        Method camcorderFeatureAccessor = featureAccessor.getDeclaredMethod("c0", resolutionFeatureKey);
        XposedBridge.hookMethod(camcorderFeatureAccessor, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = enumName(param.args != null && param.args.length > 0 ? param.args[0] : null);
                if (!isTargetFeatureName(key)) {
                    return;
                }
                Object current = param.getResult();
                Map patched = current instanceof Map ? new HashMap((Map) current) : defaultCamcorderFeature();
                patchCamcorderFeature(patched);
                param.setResult(patched);
            }
        });
        featureAccessorHooksInstalled = true;
        log("feature accessor hooks installed");
    }

    private static void hookCameraResolution(final ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        if (cameraResolutionHooksInstalled) {
            return;
        }
        Class<?> resolutionClass =
                Class.forName("com.sec.android.app.camera.interfaces.Resolution", false, classLoader);
        Class<?> cameraResolution =
                Class.forName("com.sec.android.app.camera.util.CameraResolution", false, classLoader);

        Method hdr10Feature = cameraResolution.getDeclaredMethod(
                "getCamcorderHdr10AvailableFeature", int.class, resolutionClass);
        XposedBridge.hookMethod(hdr10Feature, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (isBackTargetResolution(param.args)) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });

        Method hdrFeature = cameraResolution.getDeclaredMethod(
                "getCamcorderHDRAvailableFeature", int.class, resolutionClass);
        XposedBridge.hookMethod(hdrFeature, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (isBackTargetResolution(param.args)) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });

        cameraResolutionHooksInstalled = true;
        log("camera resolution hooks installed");
    }

    private static void hookVideoResolutionChooser(final ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException {
        if (videoResolutionChooserHooksInstalled) {
            return;
        }
        Class<?> resolutionClass =
                Class.forName("com.sec.android.app.camera.interfaces.Resolution", false, classLoader);
        Class<?> presenterClass = Class.forName(
                "com.sec.android.app.camera.layer.menu.chooser.VideoResolutionChooserMenuPresenter",
                false,
                classLoader);
        Class<?> adapterClass = Class.forName(
                "com.sec.android.app.camera.layer.menu.chooser.VideoResolutionChooserAdapter",
                false,
                classLoader);
        Class<?> keyClass =
                Class.forName("com.sec.android.app.camera.interfaces.CameraSettings$Key", false, classLoader);
        final Class<?> itemClass = Class.forName("C2.u", false, classLoader);
        final Field itemListField = findFieldByType(adapterClass, List.class, false, "mItemList");
        final Field presenterSettingKeyField =
                findFieldByType(presenterClass, keyClass, false, "mSettingKey");
        final Constructor<?> itemConstructor = itemClass.getDeclaredConstructor(resolutionClass, int.class);
        itemConstructor.setAccessible(true);
        final Field itemResolutionField =
                findFieldByType(itemClass, resolutionClass, false, "f293a", "a");
        final Field itemSelectedField = findFieldByType(itemClass, boolean.class, false, "c");
        final Method itemTypeMethod = itemClass.getDeclaredMethod("b");
        itemTypeMethod.setAccessible(true);
        final Method restartExpiredTimer = presenterClass.getDeclaredMethod("restartExpiredTimer");
        restartExpiredTimer.setAccessible(true);
        final Method handleResolutionChanged =
                presenterClass.getDeclaredMethod("handleResolutionChanged", resolutionClass);
        handleResolutionChanged.setAccessible(true);

        Method makeFpsAdapter = presenterClass.getDeclaredMethod("makeFpsAdapter", resolutionClass, HashMap.class);
        XposedBridge.hookMethod(makeFpsAdapter, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object resolution = param.args != null && param.args.length > 0 ? param.args[0] : null;
                boolean is8k4320 = is8k4320Resolution(resolution);
                boolean isUhdSlowMotion = isSlowMotionPresenter(param.thisObject, presenterSettingKeyField)
                        && isUhdSlowMotionResolution(resolution);
                if (!is8k4320 && !isUhdSlowMotion) {
                    return;
                }
                Object adapter = param.getResult();
                if (adapter == null) {
                    return;
                }
                try {
                    List items = (List) itemListField.get(adapter);
                    if (items == null) {
                        return;
                    }
                    if (is8k4320 && !hasVirtualItem(items, virtual8k60Items)) {
                        Object resolution8k = enumValue(
                                Class.forName("com.sec.android.app.camera.interfaces.Resolution", false, classLoader),
                                "RESOLUTION_7680X4320");
                        Object item = itemConstructor.newInstance(resolution8k, Integer.valueOf(VIRTUAL_ITEM_TYPE_FPS));
                        virtual8k60Items.put(item, Boolean.TRUE);
                        items.add(0, item);
                        log("experimental 8K60 item injected into fps chooser");
                    }
                    if (isUhdSlowMotion && !hasVirtualItem(items, virtualUhdSlow240Items)) {
                        Object resolutionUhd120 = enumValue(
                                Class.forName("com.sec.android.app.camera.interfaces.Resolution", false, classLoader),
                                "RESOLUTION_3840X2160_120FPS");
                        if (!hasItemResolution(items, itemResolutionField, resolutionUhd120)) {
                            log("experimental UHD slow motion 240 skipped, 120fps base item unavailable");
                        } else {
                            Object item = itemConstructor.newInstance(resolutionUhd120, Integer.valueOf(VIRTUAL_ITEM_TYPE_FPS));
                            virtualUhdSlow240Items.put(item, Boolean.TRUE);
                            items.add(0, item);
                            log("experimental UHD slow motion 240 item injected into fps chooser");
                        }
                    }
                } catch (Throwable throwable) {
                    log("inject experimental fps item failed: " + throwable);
                    log(throwable);
                }
            }
        });

        Method setSelected = adapterClass.getDeclaredMethod("setSelected", resolutionClass);
        XposedBridge.hookMethod(setSelected, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object resolution = param.args != null && param.args.length > 0 ? param.args[0] : null;
                boolean is8k4320 = is8k4320Resolution(resolution);
                boolean isUhdSlow = isUhdSlowMotionResolution(resolution);
                if (!is8k4320 && !isUhdSlow) {
                    return;
                }
                if (isUhdSlow && !isUhd120Resolution(resolution)) {
                    forceUhdSlow240Selection = false;
                }
                try {
                    List items = (List) itemListField.get(param.thisObject);
                    if (items == null) {
                        return;
                    }
                    if (is8k4320 && hasVirtualItem(items, virtual8k60Items)) {
                        for (int i = 0; i < items.size(); i++) {
                            Object item = items.get(i);
                            boolean isVirtual = virtual8k60Items.containsKey(item);
                            boolean isNormal8k30 = !isVirtual
                                    && isItemType(item, itemTypeMethod, VIRTUAL_ITEM_TYPE_FPS)
                                    && is8k4320Resolution(getItemResolution(item, itemResolutionField));
                            itemSelectedField.setBoolean(
                                    item,
                                    force8k60Selection ? isVirtual : isNormal8k30);
                        }
                    }
                    if (isUhdSlow && hasVirtualItem(items, virtualUhdSlow240Items)) {
                        for (int i = 0; i < items.size(); i++) {
                            Object item = items.get(i);
                            boolean isVirtual = virtualUhdSlow240Items.containsKey(item);
                            Object itemResolution = getItemResolution(item, itemResolutionField);
                            boolean isNormalSelected = !isVirtual
                                    && isItemType(item, itemTypeMethod, VIRTUAL_ITEM_TYPE_FPS)
                                    && safeEquals(itemResolution, resolution);
                            itemSelectedField.setBoolean(
                                    item,
                                    forceUhdSlow240Selection ? isVirtual : isNormalSelected);
                        }
                    }
                } catch (Throwable throwable) {
                    log("select experimental fps item failed: " + throwable);
                    log(throwable);
                }
            }
        });

        Method itemText = itemClass.getDeclaredMethod("a", android.content.Context.class);
        XposedBridge.hookMethod(itemText, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (virtual8k60Items.containsKey(param.thisObject)) {
                    param.setResult("60");
                    return;
                }
                if (virtualUhdSlow240Items.containsKey(param.thisObject)) {
                    param.setResult("240");
                    return;
                }
                try {
                    if (isItemType(param.thisObject, itemTypeMethod, VIRTUAL_ITEM_TYPE_FPS)
                            && is8k4320Resolution(getItemResolution(param.thisObject, itemResolutionField))) {
                        param.setResult("30");
                    } else if (isItemType(param.thisObject, itemTypeMethod, VIRTUAL_ITEM_TYPE_FPS)
                            && isUhd120Resolution(getItemResolution(param.thisObject, itemResolutionField))) {
                        param.setResult("120");
                    }
                } catch (Throwable throwable) {
                    log("normalize experimental fps text failed: " + throwable);
                }
            }
        });

        Class<?> clickListenerClass =
                Class.forName("com.sec.android.app.camera.layer.menu.chooser.t", false, classLoader);
        Method onItemClick = clickListenerClass.getDeclaredMethod("onItemClick", itemClass);
        XposedBridge.hookMethod(onItemClick, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object item = param.args != null && param.args.length > 0 ? param.args[0] : null;
                if (item == null) {
                    return;
                }
                if (virtual8k60Items.containsKey(item)) {
                    force8k60Selection = true;
                    pending8kFpsToggle.set(Boolean.TRUE);
                    log("experimental 8K60 selected");
                    return;
                }
                if (virtualUhdSlow240Items.containsKey(item)) {
                    forceUhdSlow240Selection = true;
                    pendingUhdSlowFpsToggle.set(Boolean.TRUE);
                    log("experimental UHD slow motion 240 selected");
                    return;
                }
                try {
                    Object resolution = itemResolutionField.get(item);
                    if (isItemType(item, itemTypeMethod, VIRTUAL_ITEM_TYPE_FPS)
                            && is8k4320Resolution(resolution)) {
                        force8k60Selection = false;
                        pending8kFpsToggle.set(Boolean.TRUE);
                        log("8K normal fps selected, experimental 8K60 disabled");
                    } else if (isItemType(item, itemTypeMethod, VIRTUAL_ITEM_TYPE_FPS)
                            && isUhd120Resolution(resolution)) {
                        forceUhdSlow240Selection = false;
                        pendingUhdSlowFpsToggle.set(Boolean.TRUE);
                        log("UHD slow motion 120 selected, experimental 240 disabled");
                    } else if (isUhdSlowMotionResolution(resolution)) {
                        forceUhdSlow240Selection = false;
                    } else if (!is8kResolution(resolution)) {
                        force8k60Selection = false;
                        forceUhdSlow240Selection = false;
                    }
                } catch (Throwable throwable) {
                    log("experimental fps selection check failed: " + throwable);
                }
            }
        });

        Method onResolutionClicked =
                presenterClass.getDeclaredMethod("onResolutionClicked", resolutionClass);
        XposedBridge.hookMethod(onResolutionClicked, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object resolution = param.args != null && param.args.length > 0 ? param.args[0] : null;
                boolean pending8k = consumePending8kFpsToggle();
                boolean pendingUhdSlow = consumePendingUhdSlowFpsToggle();
                if ((!pending8k || !is8k4320Resolution(resolution))
                        && (!pendingUhdSlow || !isUhd120Resolution(resolution))) {
                    return;
                }
                try {
                    restartExpiredTimer.invoke(param.thisObject);
                    handleResolutionChanged.invoke(param.thisObject, resolution);
                    param.setResult(null);
                    log("experimental fps toggle forced reconfigure, 8k60="
                            + force8k60Selection
                            + ", uhdSlow240="
                            + forceUhdSlow240Selection);
                } catch (Throwable throwable) {
                    log("experimental fps toggle reconfigure failed: " + throwable);
                    log(throwable);
                }
            }
        });

        videoResolutionChooserHooksInstalled = true;
        log("experimental video fps chooser hooks installed");
    }

    private static void hookResolutionFps(final ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        if (resolutionHooksInstalled) {
            return;
        }
        Class<?> resolutionClass =
                Class.forName("com.sec.android.app.camera.interfaces.Resolution", false, classLoader);
        Method getMaxFps = resolutionClass.getDeclaredMethod("getMaxFps");
        XposedBridge.hookMethod(getMaxFps, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (force8k60Selection && is8k4320Resolution(param.thisObject)) {
                    param.setResult(Integer.valueOf(60));
                } else if (forceUhdSlow240Selection && isUhd120Resolution(param.thisObject)) {
                    param.setResult(Integer.valueOf(240));
                }
            }
        });
        Method getMinFps = resolutionClass.getDeclaredMethod("getMinFps");
        XposedBridge.hookMethod(getMinFps, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (force8k60Selection && is8k4320Resolution(param.thisObject)) {
                    param.setResult(Integer.valueOf(60));
                } else if (forceUhdSlow240Selection && isUhd120Resolution(param.thisObject)) {
                    param.setResult(Integer.valueOf(240));
                }
            }
        });
        resolutionHooksInstalled = true;
        log("experimental video fps resolution hooks installed");
    }

    private static void hookVideoBitrateOverride() throws NoSuchMethodException {
        if (bitrateHooksInstalled) {
            return;
        }

        Method setVideoSize = MediaRecorder.class.getDeclaredMethod("setVideoSize", int.class, int.class);
        XposedBridge.hookMethod(setVideoSize, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                RecorderState state = recorderState(param.thisObject);
                state.width = intArg(param.args, 0);
                state.height = intArg(param.args, 1);
            }
        });

        Method setVideoFrameRate = MediaRecorder.class.getDeclaredMethod("setVideoFrameRate", int.class);
        XposedBridge.hookMethod(setVideoFrameRate, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                RecorderState state = recorderState(param.thisObject);
                state.fps = intArg(param.args, 0);
            }
        });

        Method setVideoEncodingBitRate = MediaRecorder.class.getDeclaredMethod(
                "setVideoEncodingBitRate", int.class);
        XposedBridge.hookMethod(setVideoEncodingBitRate, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                RecorderState state = recorderState(param.thisObject);
                int originalBitrate = intArg(param.args, 0);
                int replacement = maybeOverrideVideoBitrate(
                        "MediaRecorder",
                        originalBitrate,
                        state.width,
                        state.height,
                        state.fps);
                if (replacement > 0 && replacement != originalBitrate) {
                    param.args[0] = Integer.valueOf(replacement);
                    state.bitrate = replacement;
                } else {
                    state.bitrate = originalBitrate;
                }
            }
        });

        Method setInteger = MediaFormat.class.getDeclaredMethod("setInteger", String.class, int.class);
        XposedBridge.hookMethod(setInteger, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                handleMediaFormatInteger(param);
            }
        });

        try {
            Method setLong = MediaFormat.class.getDeclaredMethod("setLong", String.class, long.class);
            XposedBridge.hookMethod(setLong, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    handleMediaFormatLong(param);
                }
            });
        } catch (Throwable ignored) {
            // setInteger is the normal Android video bitrate path.
        }

        bitrateHooksInstalled = true;
        log("video bitrate observer hooks installed");
    }

    private static void handleMediaFormatInteger(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof MediaFormat) || param.args == null || param.args.length < 2) {
            return;
        }
        String key = param.args[0] instanceof String ? (String) param.args[0] : "";
        int value = intArg(param.args, 1);
        MediaFormat format = (MediaFormat) param.thisObject;
        FormatState state = formatState(format);
        if (MediaFormat.KEY_WIDTH.equals(key)) {
            state.width = value;
            return;
        }
        if (MediaFormat.KEY_HEIGHT.equals(key)) {
            state.height = value;
            return;
        }
        if (MediaFormat.KEY_FRAME_RATE.equals(key)) {
            state.fps = value;
            return;
        }
        if (!MediaFormat.KEY_BIT_RATE.equals(key)) {
            return;
        }
        int width = readFormatInt(format, MediaFormat.KEY_WIDTH, state.width);
        int height = readFormatInt(format, MediaFormat.KEY_HEIGHT, state.height);
        int fps = readFormatInt(format, MediaFormat.KEY_FRAME_RATE, state.fps);
        int replacement = maybeOverrideVideoBitrate("MediaFormat", value, width, height, fps);
        if (replacement > 0 && replacement != value) {
            param.args[1] = Integer.valueOf(replacement);
            state.bitrate = replacement;
        } else {
            state.bitrate = value;
        }
    }

    private static void handleMediaFormatLong(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof MediaFormat) || param.args == null || param.args.length < 2) {
            return;
        }
        String key = param.args[0] instanceof String ? (String) param.args[0] : "";
        if (!MediaFormat.KEY_BIT_RATE.equals(key) || !(param.args[1] instanceof Long)) {
            return;
        }
        long originalLong = ((Long) param.args[1]).longValue();
        if (originalLong <= 0L || originalLong > Integer.MAX_VALUE) {
            return;
        }
        MediaFormat format = (MediaFormat) param.thisObject;
        FormatState state = formatState(format);
        int width = readFormatInt(format, MediaFormat.KEY_WIDTH, state.width);
        int height = readFormatInt(format, MediaFormat.KEY_HEIGHT, state.height);
        int fps = readFormatInt(format, MediaFormat.KEY_FRAME_RATE, state.fps);
        int original = (int) originalLong;
        int replacement = maybeOverrideVideoBitrate("MediaFormatLong", original, width, height, fps);
        if (replacement > 0 && replacement != original) {
            param.args[1] = Long.valueOf(replacement);
            state.bitrate = replacement;
        } else {
            state.bitrate = original;
        }
    }

    private static int maybeOverrideVideoBitrate(
            String source, int originalBitrate, int width, int height, int fps) {
        int category = CameraBitrateSettingsProvider.videoCategoryForSize(width, height);
        if (originalBitrate <= 0 || category < 0) {
            return originalBitrate;
        }
        CameraBitrateSettingsProvider.Settings settings =
                CameraBitrateSettingsProvider.getSettings(appContext);
        int targetBitrate = settings.videoTargetBitrateBps(category);
        if (targetBitrate != originalBitrate) {
            CameraBitrateSettingsProvider.reportVideoObserved(appContext, originalBitrate, width, height, fps);
        }
        if (targetBitrate <= 0) {
            log(source + " observed " + CameraBitrateSettingsProvider.videoLabel(category)
                    + " default bitrate=" + originalBitrate
                    + ", size=" + width + "x" + height
                    + ", fps=" + fps);
            return originalBitrate;
        }
        log(source + " overriding " + CameraBitrateSettingsProvider.videoLabel(category)
                + " bitrate " + originalBitrate
                + " -> " + targetBitrate
                + ", size=" + width + "x" + height
                + ", fps=" + fps);
        return targetBitrate;
    }

    private static RecorderState recorderState(Object recorder) {
        synchronized (recorderStates) {
            RecorderState state = (RecorderState) recorderStates.get(recorder);
            if (state == null) {
                state = new RecorderState();
                recorderStates.put(recorder, state);
            }
            return state;
        }
    }

    private static FormatState formatState(Object format) {
        synchronized (mediaFormatStates) {
            FormatState state = (FormatState) mediaFormatStates.get(format);
            if (state == null) {
                state = new FormatState();
                mediaFormatStates.put(format, state);
            }
            return state;
        }
    }

    private static int readFormatInt(MediaFormat format, String key, int fallback) {
        if (format == null || key == null) {
            return fallback;
        }
        try {
            if (format.containsKey(key)) {
                return format.getInteger(key);
            }
        } catch (Throwable ignored) {
            // Keep the previously observed value.
        }
        return fallback;
    }

    private static int intArg(Object[] args, int index) {
        if (args == null || index < 0 || index >= args.length || !(args[index] instanceof Integer)) {
            return 0;
        }
        return ((Integer) args[index]).intValue();
    }

    private static void hookCameraSettings(final ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        if (cameraSettingsHooksInstalled) {
            return;
        }
        final Class<?> keyClass =
                Class.forName("com.sec.android.app.camera.interfaces.CameraSettings$Key", false, classLoader);
        Class<?> settingsImpl =
                Class.forName("com.sec.android.app.camera.setting.repository.CameraSettingsImpl", false, classLoader);

        Method getDimmers = settingsImpl.getDeclaredMethod("getDimmers", keyClass);
        XposedBridge.hookMethod(getDimmers, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (isHdrVideoSettingKey(param.args != null && param.args.length > 0 ? param.args[0] : null)
                        && isCurrentBackProVideoTargetResolution(param.thisObject, classLoader, keyClass)) {
                    param.setResult(null);
                }
            }
        });

        Method getOverriddenSettingValue = settingsImpl.getDeclaredMethod("getOverriddenSettingValue", keyClass);
        XposedBridge.hookMethod(getOverriddenSettingValue, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (isHdrVideoSettingKey(param.args != null && param.args.length > 0 ? param.args[0] : null)
                        && isCurrentBackProVideoTargetResolution(param.thisObject, classLoader, keyClass)) {
                    param.setResult(Integer.valueOf(Integer.MIN_VALUE));
                }
            }
        });

        cameraSettingsHooksInstalled = true;
        log("camera settings hooks installed");
    }

    private static void hookShootingModeFeature(final ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        if (shootingModeFeatureHookInstalled) {
            return;
        }
        Class<?> shootingModeFeature =
                Class.forName("com.sec.android.app.camera.interfaces.ShootingModeFeature", false, classLoader);
        Method hdr10Supported = shootingModeFeature.getDeclaredMethod("isHdr10PlusSupported");
        XposedBridge.hookMethod(hdr10Supported, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.TRUE);
            }
        });
        shootingModeFeatureHookInstalled = true;
        log("shooting mode feature hook installed");
    }

    private static void patchFeatureMap(ClassLoader classLoader, String source) {
        try {
            Class<?> featureLoader = Class.forName("x1.f", true, classLoader);
            Field mapField = findFieldByType(featureLoader, Map.class, true, "f11825a", "a");
            Object mapObject = mapField.get(null);
            if (!(mapObject instanceof Map)) {
                log("feature map unavailable from " + source);
                return;
            }
            Map map = (Map) mapObject;
            for (int i = 0; i < TARGET_FEATURES.length; i++) {
                putPatchedFeature(map, TARGET_FEATURES[i]);
            }
            log("feature map patched from " + source + ", entries=" + map.size());
        } catch (Throwable throwable) {
            log("feature map patch failed from " + source + ": " + throwable);
            log(throwable);
        }
    }

    private static void putPatchedFeature(Map map, String name) {
        Object existing = map.get(name);
        Map entry = existing instanceof Map ? new HashMap((Map) existing) : defaultCamcorderFeature();
        patchCamcorderFeature(entry);
        map.put(name, entry);
    }

    private static Map defaultCamcorderFeature() {
        Map entry = new HashMap();
        entry.put("value", "true");
        entry.put("snapshot-support", "false");
        entry.put("vdis", "false");
        entry.put("super-vdis", "false");
        entry.put("effect", "false");
        entry.put("object-tracking", "false");
        entry.put("seamless-zoom-support", "false");
        entry.put("physical-zoom-supported", "false");
        entry.put("external-storage-support", "false");
        entry.put("supported-mode", "pro_video");
        return entry;
    }

    private static void patchCamcorderFeature(Map entry) {
        entry.put("value", "true");
        entry.put("hdr", "true");
        entry.put("hdr10", "true");
        Object supportedMode = entry.get("supported-mode");
        String mode = supportedMode instanceof String ? (String) supportedMode : "";
        if (mode.length() == 0) {
            entry.put("supported-mode", "pro_video");
        } else if (mode.indexOf("pro_video") < 0) {
            entry.put("supported-mode", mode + ",pro_video");
        }
    }

    private static boolean isBackTargetResolution(Object[] args) {
        if (args == null || args.length < 2) {
            return false;
        }
        return isBackCameraFacing(args[0]) && isTargetResolution(args[1]);
    }

    private static boolean isBackCameraFacing(Object value) {
        return value instanceof Integer && ((Integer) value).intValue() == BACK_CAMERA_FACING;
    }

    private static boolean isTargetResolution(Object resolution) {
        String name = enumName(resolution);
        if (containsTargetResolutionToken(name)) {
            return true;
        }
        String value = callStringMethod(resolution, "getString");
        return containsTargetResolutionToken(value) || containsTargetResolutionToken(String.valueOf(resolution));
    }

    private static boolean containsTargetResolutionToken(String value) {
        if (value == null) {
            return false;
        }
        String upper = value.toUpperCase();
        return upper.indexOf("3840X2160_120FPS") >= 0
                || upper.indexOf("3840X1644_120FPS") >= 0
                || upper.indexOf("1920X1080_120FPS") >= 0
                || upper.indexOf("1920X824_120FPS") >= 0
                || upper.indexOf("7680X4320") >= 0
                || upper.indexOf("7680X3296") >= 0;
    }

    private static boolean is8k4320Resolution(Object resolution) {
        return "RESOLUTION_7680X4320".equals(enumName(resolution));
    }

    private static boolean is8kResolution(Object resolution) {
        String name = enumName(resolution);
        return name.indexOf("RESOLUTION_7680X4320") >= 0 || name.indexOf("RESOLUTION_7680X3296") >= 0;
    }

    private static boolean isUhdSlowMotionResolution(Object resolution) {
        return isUhd120Resolution(resolution);
    }

    private static boolean isUhd120Resolution(Object resolution) {
        return "RESOLUTION_3840X2160_120FPS".equals(enumName(resolution));
    }

    private static boolean hasVirtualItem(List items, Map virtualItems) {
        for (int i = 0; i < items.size(); i++) {
            if (virtualItems.containsKey(items.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasItemResolution(List items, Field itemResolutionField, Object resolution) {
        for (int i = 0; i < items.size(); i++) {
            try {
                if (safeEquals(getItemResolution(items.get(i), itemResolutionField), resolution)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean consumePending8kFpsToggle() {
        boolean pending = Boolean.TRUE.equals(pending8kFpsToggle.get());
        pending8kFpsToggle.remove();
        return pending;
    }

    private static boolean consumePendingUhdSlowFpsToggle() {
        boolean pending = Boolean.TRUE.equals(pendingUhdSlowFpsToggle.get());
        pendingUhdSlowFpsToggle.remove();
        return pending;
    }

    private static boolean isSlowMotionPresenter(Object presenter, Field settingKeyField) {
        if (presenter == null || settingKeyField == null) {
            return false;
        }
        try {
            String key = enumName(settingKeyField.get(presenter));
            return key.indexOf("SLOW_MOTION_RESOLUTION") >= 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isItemType(Object item, Method itemTypeMethod, int expectedType) {
        if (item == null || itemTypeMethod == null) {
            return false;
        }
        try {
            Object value = itemTypeMethod.invoke(item);
            return value instanceof Integer && ((Integer) value).intValue() == expectedType;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getItemResolution(Object item, Field itemResolutionField) throws IllegalAccessException {
        return item == null || itemResolutionField == null ? null : itemResolutionField.get(item);
    }

    private static boolean safeEquals(Object left, Object right) {
        return left == right || (left != null && left.equals(right));
    }

    private static boolean isCurrentBackProVideoTargetResolution(
            Object settings, ClassLoader classLoader, Class<?> keyClass) {
        if (settings == null) {
            return false;
        }
        try {
            Object key = enumValue(keyClass, KEY_BACK_PRO_RESOLUTION);
            Method getPreferenceValue = settings.getClass().getMethod("getPreferenceValue", keyClass);
            Object idObject = getPreferenceValue.invoke(settings, key);
            if (!(idObject instanceof Integer)) {
                return false;
            }
            int resolutionId = ((Integer) idObject).intValue();
            Class<?> resolutionClass =
                    Class.forName("com.sec.android.app.camera.interfaces.Resolution", false, classLoader);
            Method getResolution = resolutionClass.getDeclaredMethod("getResolution", int.class);
            Object resolution = getResolution.invoke(null, Integer.valueOf(resolutionId));
            return isTargetResolution(resolution);
        } catch (Throwable throwable) {
            log("current pro video resolution check failed: " + throwable);
            return false;
        }
    }

    private static boolean isHdrVideoSettingKey(Object key) {
        String name = enumName(key);
        return "HDR10_PLUS".equals(name)
                || "HDR10_PLUS_INDICATOR".equals(name)
                || "HDR10_RECORDING".equals(name)
                || "HDR10_RECORDING_INDICATOR".equals(name)
                || "HLG".equals(name)
                || "HLG_INDICATOR".equals(name);
    }

    private static boolean isTargetFeatureName(String name) {
        for (int i = 0; i < TARGET_FEATURES.length; i++) {
            if (TARGET_FEATURES[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Object enumValue(Class<?> enumClass, String name) throws Exception {
        Method valueOf = enumClass.getDeclaredMethod("valueOf", String.class);
        return valueOf.invoke(null, name);
    }

    private static String callStringMethod(Object object, String methodName) {
        if (object == null) {
            return "";
        }
        try {
            Method method = object.getClass().getMethod(methodName);
            Object value = method.invoke(object);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String enumName(Object value) {
        if (value instanceof Enum) {
            return ((Enum) value).name();
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static Field findFieldByType(
            Class<?> owner, Class<?> expectedType, boolean requireStatic, String... preferredNames)
            throws NoSuchFieldException {
        for (int i = 0; preferredNames != null && i < preferredNames.length; i++) {
            try {
                Field field = owner.getDeclaredField(preferredNames[i]);
                if (isExpectedField(field, expectedType, requireStatic)) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        Field[] fields = owner.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (isExpectedField(field, expectedType, requireStatic)) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new NoSuchFieldException(
                "No "
                        + (requireStatic ? "static " : "")
                        + expectedType.getName()
                        + " field in "
                        + owner.getName());
    }

    private static boolean isExpectedField(Field field, Class<?> expectedType, boolean requireStatic) {
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) != requireStatic) {
            return false;
        }
        Class<?> actualType = field.getType();
        if (expectedType.isPrimitive()) {
            return actualType == expectedType;
        }
        return expectedType.isAssignableFrom(actualType);
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

    private static final class RecorderState {
        int width;
        int height;
        int fps;
        int bitrate;
    }

    private static final class FormatState {
        int width;
        int height;
        int fps;
        int bitrate;
    }

    private interface Installer {
        void install() throws Throwable;
    }
}
