package com.samsung.feature.extension.videoeditor;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.samsung.feature.extension.audioeraser.AudioEraserDeviceSupport;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Unlocks Samsung Video Editor Studio's Audio Eraser on Galaxy S devices below
 * S24 Ultra.
 *
 * In addition to the Java feature gates, older firmware can omit the native
 * multisource-separator payload used by VEKit.  The compatibility payload in
 * this module was extracted from an S24 Ultra running Video Editor 7.4.12.3.
 * It is verified before use and loaded into the target app's linker namespace.
 */
public final class VideoEditorAudioEraserHook
        implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String TAG = "SFE-AudioEraser";
    private static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    private static final String TARGET_PACKAGE = "com.sec.android.app.vepreload";

    private static final String FLOATING_FEATURE_CLASS =
            "com.samsung.android.feature.SemFloatingFeature";
    private static final String VEKIT_VERSION_READER =
            "com.sec.android.app.ve.vekit.common.VEKitVersionReader";
    private static final String NATIVE_LIB_SETUP =
            "com.samsung.vekit.External.NativeLibSetup";
    private static final String AUDIO_CONFIG_KEY =
            "SEC_FLOATING_FEATURE_AUDIO_CONFIG_MULTISOURCE_SEPARATOR";
    private static final String AI_VERSION_KEY =
            "SEC_FLOATING_FEATURE_COMMON_CONFIG_AI_VERSION";
    private static final String S24_ULTRA_AUDIO_CONFIG =
            "{FastScanning_6, SourceSeparator_4, Version_1.3.0}";
    private static final int MINIMUM_AI_VERSION = 20261;

    private static final String PAYLOAD_ASSET_ROOT =
            "assets/audio_eraser/s24u-oneui8/";
    private static final String PAYLOAD_DIRECTORY = "sfe_audio_eraser_1_3_14";
    private static final String LIB_DIRECTORY = "arm64-v8a";
    private static final String BRIDGE_LIBRARY = "libsfe_audio_eraser_bridge.so";
    private static final String SNAAC_LIBRARY = "libsnaace.so";
    private static final String MAIN_LIBRARY =
            "libveframework.videoeditor.samsung.so";

    private static final PayloadFile[] PAYLOAD_FILES = {
            new PayloadFile(
                    "arm64-v8a/android.media.audio.eraser.types-V1-ndk.so",
                    85624L,
                    "4d1650598ad5a1045e025e7c5b3a3a80ce87dcc615564cd4c197bdd97c073c64"),
            new PayloadFile(
                    "arm64-v8a/libmultisourceseparator.audio.samsung.so",
                    35424L,
                    "4b2e50d796f87a7f029d9ef7b766c145c630e7256fcc59f1a63748f6f4b3affe"),
            new PayloadFile(
                    "arm64-v8a/libmultisourceseparator.so",
                    35424L,
                    "3b501920a332e84054bac25de111e10288c14548195fdef3090de390c52c6dbe"),
            new PayloadFile(
                    "arm64-v8a/libsbs.so",
                    436320L,
                    "5642ef8b7cbea774c56fb9bc9baf92af18e7069a6abd7beee9cf360db447c35b"),
            new PayloadFile(
                    "arm64-v8a/libtensorflowlite_gpu_delegate.so",
                    11286776L,
                    "afa8ec0a43eb90b27dde38fc1b048fbe4dab0c0787d892edb3d9dc02410b1f63"),
            new PayloadFile(
                    "arm64-v8a/libvideo-highlight-arm64-v8a.so",
                    1210760L,
                    "b97f84b950b6e707bf56473e56d12fe57cfd3aa7da9c1602d33ff959c6fb31fe"),
            new PayloadFile(
                    "arm64-v8a/libmediacontextanalyzer.so",
                    218640L,
                    "4eb58f30c1afe6859240d5d92603f5e390fa80914b275cde57d6059d1a8609fe"),
            new PayloadFile(
                    "arm64-v8a/libveframework.videoeditor.samsung.so",
                    4716776L,
                    "85e470c0fca991366f09ff0b1c14cf4b97425d5806e28728a402766f1b0b7755"),
            new PayloadFile(
                    "arm64-v8a/libsfe_audio_eraser_bridge.so",
                    2296L,
                    "297982096254a97703c63f1f3c8b20e39c4701961a3ae0e95097ac35383f4047"),
            new PayloadFile(
                    "arm64-v8a/libsnaace.so",
                    399544L,
                    "430ede74006eada28200fbb28452035bc55d903938a01df012898f9e077c6f08"),
            new PayloadFile(
                    "system/etc/fastScanner.tflite",
                    11333084L,
                    "7008bfbd4d88e21fc55d4a3f6c4363589d76c6ef84d7b290acd2f236fadc0e9e"),
            new PayloadFile(
                    "system/etc/mss_v0.23.0_VMWO_2_fp32.sorione",
                    20962100L,
                    "5df3dbd60b6b455991184b62eaee2ad7f5c951a4b579671c0ed2890034ddad06"),
            new PayloadFile(
                    "system/etc/audio_ae_intervals.conf",
                    64L,
                    "1f705db7c16399ed9e83f5ba29c0f80edbccd800f0bedfc55339255c249a88d3")
    };

    /** Libraries are loaded leaf-first so every DT_NEEDED lookup can resolve. */
    private static final String[] PRELOAD_LIBRARIES = {
            "libtensorflowlite_gpu_delegate.so",
            "libsbs.so",
            "libmultisourceseparator.so",
            "libvideo-highlight-arm64-v8a.so",
            "libmediacontextanalyzer.so"
    };

    private static final String[] SUPPORT_METHODS = {
            "supportAudioEraser",
            "supportAudioEraserSolution",
            "supportStudioAudioEraser",
            "supportInstantAudioEraser"
    };

    private static final Object PAYLOAD_LOCK = new Object();
    private static volatile String modulePath;
    private static volatile boolean payloadAttempted;
    private static volatile Throwable payloadFailure;
    private static volatile File payloadRoot;
    private static volatile boolean packagedSbsLoaded;
    private static volatile boolean packagedMainLoaded;

    private static native int nativeActivateModelTree();

    @Override
    public void initZygote(StartupParam startupParam) {
        if (startupParam != null && startupParam.modulePath != null) {
            modulePath = startupParam.modulePath;
            log("module path captured for native payload");
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        if (!AudioEraserDeviceSupport.shouldHookCurrentDevice()) {
            log("skip model with native or unsupported Audio Eraser path model="
                    + safe(Build.MODEL)
                    + " device=" + safe(Build.DEVICE)
                    + " product=" + safe(Build.PRODUCT));
            return;
        }

        final String dataDir = lpparam.appInfo == null ? null : lpparam.appInfo.dataDir;
        log("enabling Audio Eraser compatibility in process=" + safe(lpparam.processName)
                + " model=" + safe(Build.MODEL)
                + " device=" + safe(Build.DEVICE));
        install("S24 native compatibility payload", new Installer() {
            @Override
            public void install() throws Throwable {
                hookNativeCompatibility(lpparam.classLoader, dataDir);
            }
        });
        install("Samsung floating features", new Installer() {
            @Override
            public void install() throws Throwable {
                hookFloatingFeatures(lpparam.classLoader);
            }
        });
        install("VEKit Audio Eraser checks", new Installer() {
            @Override
            public void install() throws Throwable {
                hookVekitChecks(lpparam.classLoader);
            }
        });
    }

    private static void hookNativeCompatibility(
            final ClassLoader classLoader, final String dataDir) throws Throwable {
        final Class<?> setupClass = Class.forName(NATIVE_LIB_SETUP, false, classLoader);
        Method[] methods = setupClass.getDeclaredMethods();
        int hooked = 0;
        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];
            if (!"loadNativeLibraries".equals(method.getName())
                    || method.getParameterTypes().length != 0
                    || method.getReturnType() != Void.TYPE) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    log("NativeLibSetup.loadNativeLibraries before");
                    try {
                        prepareAndLoadPayload(setupClass, dataDir);
                        loadBundledMainLibrary(setupClass);
                        // The bundled main framework and its S24 dependencies must be
                        // one matched set.  Do not let the original method load the
                        // smaller S23 system framework over the successful S24 load.
                        param.setResult(null);
                        log("bypassed S23 system VEFramework after verified S24 main load");
                    } catch (Throwable throwable) {
                        // Leave the original method untouched on failure.  This keeps
                        // older firmware bootable even if an S24 dependency is absent.
                        log("S24 main framework preload failed; keeping system fallback: "
                                + throwable);
                        logThrowable("S24 main framework preload", throwable);
                        XposedBridge.log(throwable);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Throwable originalFailure = param.getThrowable();
                    log("NativeLibSetup.loadNativeLibraries after throwable="
                            + describeThrowable(originalFailure)
                            + " packagedMainLoaded=" + packagedMainLoaded);
                    if (!(originalFailure instanceof UnsatisfiedLinkError)) {
                        return;
                    }
                    File root = payloadRoot;
                    if (root == null) {
                        return;
                    }
                    File fallback = new File(new File(root, LIB_DIRECTORY), MAIN_LIBRARY);
                    try {
                        loadIntoTargetNamespace(setupClass, fallback);
                        param.setResult(null);
                        log("system VEFramework was unavailable; loaded verified S24 fallback");
                    } catch (Throwable fallbackFailure) {
                        log("S24 VEFramework fallback failed after " + originalFailure
                                + ": " + fallbackFailure);
                        XposedBridge.log(fallbackFailure);
                        logThrowable("S24 VEFramework fallback", fallbackFailure);
                    }
                }
            });
            hooked++;
        }
        if (hooked == 0) {
            throw new NoSuchMethodException(NATIVE_LIB_SETUP + ".loadNativeLibraries()");
        }
        log("hooked native library setup methods=" + hooked);
    }

    private static void prepareAndLoadPayload(Class<?> anchorClass, String dataDir)
            throws Throwable {
        synchronized (PAYLOAD_LOCK) {
            if (payloadAttempted) {
                if (payloadFailure != null) {
                    throw payloadFailure;
                }
                return;
            }
            payloadAttempted = true;
            try {
                if (!isArm64Process()) {
                    throw new IllegalStateException("S24 payload requires arm64-v8a");
                }
                String apkPath = resolveModulePath();
                File root = resolvePayloadRoot(dataDir);
                extractAndVerifyPayload(apkPath, root);
                payloadRoot = root;

                File libRoot = new File(root, LIB_DIRECTORY);
                publishBundledSnaacLibrary(root, libRoot);
                loadIntoTargetNamespace(
                        VideoEditorAudioEraserHook.class,
                        new File(libRoot, BRIDGE_LIBRARY));
                log("loaded JNI cwd bridge " + BRIDGE_LIBRARY);
                for (int i = 0; i < PRELOAD_LIBRARIES.length; i++) {
                    String name = PRELOAD_LIBRARIES[i];
                    loadNativeDependency(anchorClass, libRoot, name);
                }
                activateCompatibleModelTree(root);
                log("S24 native compatibility payload ready at " + root.getAbsolutePath());
            } catch (Throwable throwable) {
                payloadFailure = throwable;
                logThrowable("prepareAndLoadPayload", throwable);
                throw throwable;
            }
        }
    }

    private static void loadBundledMainLibrary(Class<?> anchorClass) throws Throwable {
        synchronized (PAYLOAD_LOCK) {
            if (packagedMainLoaded) {
                log("verified S24 main VEFramework already loaded");
                return;
            }
            File root = payloadRoot;
            if (root == null) {
                throw new IllegalStateException("payload root is unavailable for S24 main");
            }
            File mainLibrary = new File(new File(root, LIB_DIRECTORY), MAIN_LIBRARY);
            PayloadFile expected = findPayloadFile(LIB_DIRECTORY + "/" + MAIN_LIBRARY);
            if (expected == null || !isVerified(mainLibrary, expected)) {
                throw new SecurityException("S24 main VEFramework verification failed: "
                        + mainLibrary);
            }
            String verifiedHash = expected.sha256;
            loadIntoTargetNamespace(anchorClass, mainLibrary);
            packagedMainLoaded = true;
            log("loaded verified S24 main VEFramework path="
                    + mainLibrary.getAbsolutePath()
                    + " length=" + mainLibrary.length()
                    + " sha256=" + verifiedHash);
        }
    }

    /**
     * VEFramework's SnaacWrapper tries "../system/lib64/libsnaace.so" after
     * the model-tree chdir.  On S23 the system copy is absent, so the original
     * dlopen leaves the function table null and CFI faults in snaac_init().
     * Publish the verified S24 copy at the exact relative location expected by
     * the framework.  This remains inside the app's private code-cache parent.
     */
    private static void publishBundledSnaacLibrary(File root, File libRoot)
            throws Throwable {
        PayloadFile expected = findPayloadFile(LIB_DIRECTORY + "/" + SNAAC_LIBRARY);
        File source = new File(libRoot, SNAAC_LIBRARY);
        if (expected == null || !isVerified(source, expected)) {
            throw new SecurityException("bundled SNAAC verification failed: " + source);
        }
        File parent = root.getParentFile();
        if (parent == null) {
            throw new IllegalStateException("payload root has no parent for SNAAC path");
        }
        File destination = new File(new File(parent, "system" + File.separator + "lib64"),
                SNAAC_LIBRARY);
        if (isVerified(destination, expected)) {
            log("verified bundled SNAAC relative path=" + destination.getAbsolutePath());
            return;
        }
        File destinationParent = destination.getParentFile();
        if (destinationParent == null
                || (!destinationParent.isDirectory() && !destinationParent.mkdirs())) {
            throw new IllegalStateException("cannot create SNAAC directory: " + destinationParent);
        }
        File temporary = new File(destinationParent, destination.getName() + ".tmp");
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException("cannot replace temporary SNAAC file");
        }
        FileInputStream input = new FileInputStream(source);
        FileOutputStream output = new FileOutputStream(temporary);
        try {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        } finally {
            try {
                input.close();
            } finally {
                output.close();
            }
        }
        if (!isVerified(temporary, expected)) {
            temporary.delete();
            throw new SecurityException("published SNAAC verification failed: " + temporary);
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IllegalStateException("cannot replace SNAAC destination");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("cannot publish SNAAC destination");
        }
        destination.setReadable(true, true);
        destination.setExecutable(true, true);
        destination.setWritable(false, true);
        log("published bundled SNAAC relative path=" + destination.getAbsolutePath()
                + " length=" + destination.length()
                + " sha256=" + sha256(destination));
    }

    private static PayloadFile findPayloadFile(String relativePath) {
        for (int i = 0; i < PAYLOAD_FILES.length; i++) {
            if (PAYLOAD_FILES[i].relativePath.equals(relativePath)) {
                return PAYLOAD_FILES[i];
            }
        }
        return null;
    }

    private static void loadNativeDependency(
            Class<?> anchorClass, File bundledLibraryRoot, String name) throws Throwable {
        File systemLibrary = findSystemLibrary(name);
        if (systemLibrary != null) {
            try {
                log("loading device-native dependency " + name
                        + " path=" + systemLibrary.getAbsolutePath()
                        + " length=" + systemLibrary.length());
                loadIntoTargetNamespace(anchorClass, systemLibrary);
                log("loaded device-native dependency " + name);
                return;
            } catch (Throwable systemFailure) {
                log("device-native dependency rejected, using bundled " + name
                        + ": " + systemFailure);
                logThrowable("device-native load " + name, systemFailure);
            }
        } else {
            log("device firmware omits " + name + "; using bundled S24 copy");
        }

        File bundled = new File(bundledLibraryRoot, name);
        loadIntoTargetNamespace(anchorClass, bundled);
        if ("libsbs.so".equals(name)) {
            packagedSbsLoaded = true;
        }
        log("loaded bundled dependency " + name + " length=" + bundled.length());
    }

    private static File findSystemLibrary(String name) {
        String[] roots = {
                "/system/lib64",
                "/system_ext/lib64",
                "/product/lib64",
                "/vendor/lib64"
        };
        for (int i = 0; i < roots.length; i++) {
            File candidate = new File(roots[i], name);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isArm64Process() {
        String[] abis = Build.SUPPORTED_64_BIT_ABIS;
        if (abis == null) {
            return false;
        }
        for (int i = 0; i < abis.length; i++) {
            if ("arm64-v8a".equalsIgnoreCase(abis[i])) {
                return true;
            }
        }
        return false;
    }

    private static String resolveModulePath() throws Throwable {
        String captured = modulePath;
        if (captured != null && new File(captured).isFile()) {
            return captured;
        }

        Application application = currentApplication();
        if (application != null) {
            ApplicationInfo info = application.getPackageManager()
                    .getApplicationInfo(MODULE_PACKAGE, 0);
            if (info != null && info.sourceDir != null && new File(info.sourceDir).isFile()) {
                modulePath = info.sourceDir;
                log("resolved module path through PackageManager");
                return info.sourceDir;
            }
        }
        throw new IllegalStateException("LSPosed did not provide the module APK path");
    }

    private static File resolvePayloadRoot(String dataDir) throws Throwable {
        File codeCache = null;
        Application application = currentApplication();
        if (application != null) {
            codeCache = application.getCodeCacheDir();
        }
        if (codeCache == null && dataDir != null && dataDir.length() != 0) {
            codeCache = new File(dataDir, "code_cache");
        }
        if (codeCache == null) {
            throw new IllegalStateException("target code_cache directory is unavailable");
        }
        if (!codeCache.isDirectory() && !codeCache.mkdirs()) {
            throw new IllegalStateException("cannot create " + codeCache);
        }

        File canonicalCache = codeCache.getCanonicalFile();
        File root = new File(canonicalCache, PAYLOAD_DIRECTORY).getCanonicalFile();
        String cachePrefix = canonicalCache.getPath() + File.separator;
        if (!root.getPath().startsWith(cachePrefix)) {
            throw new SecurityException("payload escaped code_cache: " + root);
        }
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("cannot create " + root);
        }
        return root;
    }

    private static Application currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object result = currentApplication.invoke(null);
            return result instanceof Application ? (Application) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void extractAndVerifyPayload(String apkPath, File root) throws Throwable {
        ZipFile apk = new ZipFile(apkPath);
        try {
            for (int i = 0; i < PAYLOAD_FILES.length; i++) {
                PayloadFile payload = PAYLOAD_FILES[i];
                File destination = new File(root, payload.relativePath);
                if (isVerified(destination, payload)) {
                    continue;
                }
                extractOne(apk, payload, destination);
            }
        } finally {
            apk.close();
        }
    }

    private static void extractOne(ZipFile apk, PayloadFile payload, File destination)
            throws Throwable {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IllegalStateException("cannot create payload directory for " + destination);
        }
        ZipEntry entry = apk.getEntry(PAYLOAD_ASSET_ROOT + payload.relativePath);
        if (entry == null) {
            throw new IllegalStateException("missing module asset "
                    + PAYLOAD_ASSET_ROOT + payload.relativePath);
        }

        File temporary = new File(parent, destination.getName() + ".tmp");
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException("cannot replace " + temporary);
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long length = 0L;
        InputStream input = apk.getInputStream(entry);
        FileOutputStream output = new FileOutputStream(temporary);
        try {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                length += read;
            }
            output.getFD().sync();
        } finally {
            try {
                input.close();
            } finally {
                output.close();
            }
        }

        String actualHash = toHex(digest.digest());
        if (length != payload.length || !payload.sha256.equals(actualHash)) {
            temporary.delete();
            throw new SecurityException("payload verification failed for " + payload.relativePath
                    + " length=" + length + " sha256=" + actualHash);
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IllegalStateException("cannot replace " + destination);
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("cannot publish " + destination);
        }
        destination.setReadable(true, true);
        destination.setExecutable(true, true);
        destination.setWritable(false, true);
        log("extracted and verified " + payload.relativePath);
    }

    private static boolean isVerified(File file, PayloadFile payload) throws Throwable {
        return file.isFile()
                && file.length() == payload.length
                && payload.sha256.equals(sha256(file));
    }

    private static String sha256(File file) throws Throwable {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = hex[value >>> 4];
            result[i * 2 + 1] = hex[value & 0x0f];
        }
        return new String(result);
    }

    private static void loadIntoTargetNamespace(Class<?> anchorClass, File library)
            throws Throwable {
        if (!library.isFile()) {
            throw new IllegalStateException("native library is missing: " + library);
        }
        String path = library.getAbsolutePath();
        Runtime runtime = Runtime.getRuntime();
        Throwable reflectionFailure = null;

        Method[] methods = Runtime.class.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] parameters = method.getParameterTypes();
            if (!"load0".equals(method.getName())
                    || parameters.length != 2
                    || parameters[1] != String.class) {
                continue;
            }
            Object first;
            if (parameters[0] == Class.class) {
                first = anchorClass;
            } else if (parameters[0] == ClassLoader.class) {
                first = anchorClass.getClassLoader();
            } else {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(runtime, first, path);
                return;
            } catch (InvocationTargetException invocationFailure) {
                throw invocationFailure.getCause();
            } catch (Throwable throwable) {
                reflectionFailure = throwable;
            }
        }

        // Android releases that expose nativeLoad instead of load0 still accept
        // an explicit target ClassLoader.  Keep this fallback for older VEKit builds.
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] parameters = method.getParameterTypes();
            if (!"nativeLoad".equals(method.getName())
                    || parameters.length < 2
                    || parameters[0] != String.class
                    || parameters[1] != ClassLoader.class) {
                continue;
            }
            Object[] arguments = new Object[parameters.length];
            arguments[0] = path;
            arguments[1] = anchorClass.getClassLoader();
            boolean supported = true;
            for (int j = 2; j < parameters.length; j++) {
                if (parameters[j] == Class.class) {
                    arguments[j] = anchorClass;
                } else {
                    supported = false;
                    break;
                }
            }
            if (!supported) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object error = method.invoke(Modifier.isStatic(method.getModifiers())
                        ? null : runtime, arguments);
                if (error instanceof String && ((String) error).length() != 0) {
                    throw new UnsatisfiedLinkError((String) error);
                }
                return;
            } catch (InvocationTargetException invocationFailure) {
                throw invocationFailure.getCause();
            } catch (Throwable throwable) {
                reflectionFailure = throwable;
            }
        }

        log("target-namespace Runtime loader unavailable; using System.load after "
                + reflectionFailure);
        System.load(path);
    }

    private static void activateCompatibleModelTree(File root) throws Throwable {
        File systemModel = new File("/system/etc/fastScanner.tflite");
        if (!packagedSbsLoaded && systemModel.isFile()) {
            log("device-native libsbs selected; keeping device fastScanner model");
            return;
        }
        try {
            File bundledModel = new File(root, "system/etc/fastScanner.tflite");
            File separatorModel = new File(
                    root, "system/etc/mss_v0.23.0_VMWO_2_fp32.sorione");
            File intervalConfig = new File(root, "system/etc/audio_ae_intervals.conf");
            if (!bundledModel.isFile()) {
                throw new IllegalStateException("bundled fastScanner model is missing");
            }
            if (!separatorModel.isFile()) {
                throw new IllegalStateException("bundled MSS separator model is missing");
            }
            if (!intervalConfig.isFile()) {
                throw new IllegalStateException("bundled Audio Eraser interval config is missing");
            }

            // VEFramework 7.4.12.3 passes the relative path "system/etc" to
            // libsbs.  A bundled S24 libsbs must use its matching S24 model even
            // when older S23 firmware happens to contain a same-named model.
            Class<?> os = Class.forName("android.system.Os");
            Method setenv = os.getMethod(
                    "setenv", String.class, String.class, Boolean.TYPE);
            String canonicalRoot = root.getCanonicalPath();
            String before = readCurrentWorkingDirectory();
            setenv.invoke(null, "SFE_AUDIO_ERASER_ROOT", canonicalRoot, Boolean.TRUE);
            int result = nativeActivateModelTree();
            String after = readCurrentWorkingDirectory();
            boolean directoryMatches = canonicalRoot.equals(after)
                    || after.endsWith("/" + PAYLOAD_DIRECTORY);
            if (result != 0 || !directoryMatches) {
                throw new IllegalStateException("native chdir failed result=" + result
                        + " expected=" + canonicalRoot + " actual=" + after);
            }
            log("activated bundled fastScanner model tree reason="
                    + (packagedSbsLoaded ? "bundled-libsbs" : "system-model-missing")
                    + " cwd=" + before + " -> " + after
                    + " fastScannerSha256=" + sha256(bundledModel)
                    + " mssModelLength=" + separatorModel.length()
                    + " mssModelSha256=" + sha256(separatorModel)
                    + " intervalConfigSha256=" + sha256(intervalConfig));
        } catch (Throwable throwable) {
            log("could not activate bundled fastScanner model: " + throwable);
            logThrowable("activate bundled fastScanner", throwable);
            XposedBridge.log(throwable);
            throw throwable;
        }
    }

    private static String readCurrentWorkingDirectory() {
        try {
            Class<?> os = Class.forName("android.system.Os");
            Method readlink = os.getMethod("readlink", String.class);
            Object result = readlink.invoke(null, "/proc/self/cwd");
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Throwable ignored) {
            // Fall through to java.io canonicalization.
        }
        try {
            return new File("/proc/self/cwd").getCanonicalPath();
        } catch (Throwable throwable) {
            return "unavailable:" + throwable;
        }
    }

    private static void hookFloatingFeatures(ClassLoader classLoader) throws Throwable {
        Class<?> featureClass = Class.forName(FLOATING_FEATURE_CLASS, false, classLoader);
        Method[] methods = featureClass.getDeclaredMethods();
        int hooked = 0;
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!"getString".equals(method.getName())
                    || method.getReturnType() != String.class
                    || parameterTypes.length < 1
                    || parameterTypes[0] != String.class) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length == 0
                            || !(param.args[0] instanceof String)) {
                        return;
                    }
                    String key = (String) param.args[0];
                    Object result = param.getResult();
                    String original = result instanceof String ? (String) result : null;
                    if (AUDIO_CONFIG_KEY.equals(key)) {
                        if (!S24_ULTRA_AUDIO_CONFIG.equals(original)) {
                            param.setResult(S24_ULTRA_AUDIO_CONFIG);
                            log("supplied S24 Ultra multisource-separator config; original="
                                    + safe(original));
                        }
                    } else if (AI_VERSION_KEY.equals(key)) {
                        int version = parsePositiveInt(original);
                        if (version < MINIMUM_AI_VERSION) {
                            param.setResult(String.valueOf(MINIMUM_AI_VERSION));
                            log("raised AI floating-feature version from "
                                    + safe(original) + " to " + MINIMUM_AI_VERSION);
                        }
                    }
                }
            });
            hooked++;
        }
        if (hooked == 0) {
            throw new NoSuchMethodException(FLOATING_FEATURE_CLASS + ".getString(String, ...)");
        }
        log("hooked SemFloatingFeature.getString overloads=" + hooked);
    }

    private static void hookVekitChecks(ClassLoader classLoader) throws Throwable {
        Class<?> readerClass = Class.forName(VEKIT_VERSION_READER, false, classLoader);
        Method[] methods = readerClass.getDeclaredMethods();
        int hooked = 0;
        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];
            if (!contains(SUPPORT_METHODS, method.getName())
                    || method.getParameterTypes().length != 0
                    || method.getReturnType() != Boolean.TYPE
                    || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(Boolean.TRUE);
                }
            });
            hooked++;
            log("hooked " + readerClass.getName() + "." + method.getName());
        }
        if (hooked == 0) {
            throw new NoSuchMethodException(VEKIT_VERSION_READER + " Audio Eraser support methods");
        }
        log("hooked VEKit Audio Eraser checks=" + hooked);
    }

    private static int parsePositiveInt(String value) {
        if (value == null) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean contains(String[] values, String expected) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static void install(String name, Installer installer) {
        try {
            installer.install();
        } catch (Throwable throwable) {
            log(name + " hook failed: " + throwable);
            XposedBridge.log(throwable);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void log(String message) {
        String tagged = TAG + ": " + message;
        XposedBridge.log(tagged);
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
            // Xposed logging is still available during very early process startup.
        }
    }

    private static void logThrowable(String stage, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        XposedBridge.log(TAG + ": " + stage + " failed");
        XposedBridge.log(throwable);
    }

    private static String describeThrowable(Throwable throwable) {
        if (throwable == null) {
            return "none";
        }
        return throwable.getClass().getName() + ":" + safe(throwable.getMessage());
    }

    private interface Installer {
        void install() throws Throwable;
    }

    private static final class PayloadFile {
        final String relativePath;
        final long length;
        final String sha256;

        PayloadFile(String relativePath, long length, String sha256) {
            this.relativePath = relativePath;
            this.length = length;
            this.sha256 = sha256;
        }
    }
}
