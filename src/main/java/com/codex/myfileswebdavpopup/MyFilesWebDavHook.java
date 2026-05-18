package com.codex.myfileswebdavpopup;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseArray;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MyFilesWebDavHook implements IXposedHookLoadPackage {
    static final int DOMAIN_WEBDAV = 206;
    private static final int DOMAIN_NATIVE_NETWORK = 205;

    private static final String MODULE_VERSION = "1.0";
    private static final String TARGET_PACKAGE = "com.sec.android.app.myfiles";
    private static final String TARGET_PACKAGE_ALT = "com.samsung.android.app.myfiles";
    private static final String TARGET_PACKAGE_NSM = "com.samsung.android.app.networkstoragemanager";
    private static final String DISPLAY_WEBDAV = "WebDAV";
    private static final String NETWORK_ROOT_PATH = "/Network Storage";
    private static final String WEBDAV_ROOT_PATH = NETWORK_ROOT_PATH + "/" + DISPLAY_WEBDAV;
    private static final String DIALOG_CLASS =
            "com.sec.android.app.myfiles.ui.dialog.AddNetworkStorageServerDialogFragment";
    private static final String SERVER_TYPE_CLASS =
            "com.sec.android.app.myfiles.ui.dialog.AddNetworkStorageServerDialogFragment$ServerType";
    private static final String LABEL_ENUM_CLASS = "w8.v";

    private static volatile ClassLoader targetClassLoader;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!isTargetPackage(lpparam.packageName)) {
            return;
        }

        targetClassLoader = lpparam.classLoader;
        XposedBridge.log("MyFilesWebDav: v" + MODULE_VERSION + " loading in " + lpparam.packageName);
        installHook("Application", new HookInstaller() {
            @Override
            public void install() {
                hookApplication(targetClassLoader);
            }
        });
        installHook("CleartextPolicy", new HookInstaller() {
            @Override
            public void install() {
                hookCleartextPolicy(targetClassLoader);
            }
        });
        installHook("AddNetworkStorageDialog", new HookInstaller() {
            @Override
            public void install() {
                hookAddNetworkStorageDialog(targetClassLoader);
            }
        });
        installHook("DialogBuilderFallback", new HookInstaller() {
            @Override
            public void install() {
                hookDialogBuilderFallback(targetClassLoader);
            }
        });
        installHook("WebDavSelectionFallback", new HookInstaller() {
            @Override
            public void install() {
                hookWebDavSelectionFallback(targetClassLoader);
            }
        });
        installHook("DomainHelpers", new HookInstaller() {
            @Override
            public void install() {
                hookDomainHelpers(targetClassLoader);
            }
        });
        installHook("StoragePathUtils", new HookInstaller() {
            @Override
            public void install() {
                hookStoragePathUtils(targetClassLoader);
            }
        });
        installHook("FileInfoFactory", new HookInstaller() {
            @Override
            public void install() {
                hookFileInfoFactory(targetClassLoader);
            }
        });
        installHook("ManageUi", new HookInstaller() {
            @Override
            public void install() {
                hookManageUi(targetClassLoader);
            }
        });
        installHook("RequestWrapper", new HookInstaller() {
            @Override
            public void install() {
                hookRequestWrapper(targetClassLoader);
            }
        });
        installHook("OperationRouting", new HookInstaller() {
            @Override
            public void install() {
                hookOperationRouting(targetClassLoader);
            }
        });
        installHook("Repositories", new HookInstaller() {
            @Override
            public void install() {
                hookRepositories(targetClassLoader);
            }
        });
        installHook("ServerClick", new HookInstaller() {
            @Override
            public void install() {
                hookServerClick(targetClassLoader);
            }
        });
        installHook("ListAdapters", new HookInstaller() {
            @Override
            public void install() throws Throwable {
                hookListAdapters(targetClassLoader);
            }
        });
        XposedBridge.log("MyFilesWebDav: hook installation pass finished");
    }

    private static void installHook(String name, HookInstaller installer) {
        try {
            installer.install();
            XposedBridge.log("MyFilesWebDav: " + name + " hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: " + name + " hooks failed");
            XposedBridge.log(t);
        }
    }

    private static void installOptionalHook(String name, HookInstaller installer) {
        try {
            installer.install();
            DiagnosticLogger.log(name + " installed");
        } catch (Throwable t) {
            DiagnosticLogger.log(name + " skipped");
            DiagnosticLogger.log(t);
        }
    }

    private interface HookInstaller {
        void install() throws Throwable;
    }

    private static Class<?> findAppClass(String className, ClassLoader classLoader) {
        ClassNotFoundException last = null;
        for (String candidate : classNameAliases(className)) {
            try {
                return Class.forName(candidate, false, classLoader);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw new IllegalStateException("App class not found: " + className, last);
    }

    private static String[] classNameAliases(String className) {
        if ("w8.AbstractC2015g".equals(className)) {
            return new String[]{className, "w8.g"};
        }
        if ("U7.AbstractC0263g".equals(className)) {
            return new String[]{className, "U7.g"};
        }
        if ("q8.C1747e".equals(className)) {
            return new String[]{className, "q8.e"};
        }
        if ("q8.EnumC1751i".equals(className)) {
            return new String[]{className, "q8.i"};
        }
        if ("x7.C2094a".equals(className)) {
            return new String[]{className, "x7.a"};
        }
        if ("e6.AbstractC1145d".equals(className)) {
            return new String[]{className, "e6.d", "E6.AbstractC1145d", "E6.d"};
        }
        if ("p8.AbstractC1705c".equals(className)) {
            return new String[]{className, "p8.c"};
        }
        return new String[]{className};
    }

    private static XC_MethodHook.Unhook hookAppMethod(
            String className,
            ClassLoader classLoader,
            String methodName,
            Object... parameterTypesAndCallback
    ) {
        return XposedHelpers.findAndHookMethod(
                findAppClass(className, classLoader),
                methodName,
                parameterTypesAndCallback
        );
    }

    static ClassLoader getTargetClassLoader() {
        return targetClassLoader;
    }

    private static void hookApplication(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "android.app.Application",
                classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        WebDavBackend.init((Context) param.thisObject);
                        registerWebDavStoragePath(classLoader);
                    }
                }
        );
    }

    private static void hookCleartextPolicy(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "android.security.NetworkSecurityPolicy",
                classLoader,
                "isCleartextTrafficPermitted",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                "android.security.NetworkSecurityPolicy",
                classLoader,
                "isCleartextTrafficPermitted",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                    }
                }
        );
    }

    private static void hookAddNetworkStorageDialog(final ClassLoader classLoader) {
        hookAppMethod(
                DIALOG_CLASS,
                classLoader,
                "initListItem",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            ensureWebDavListItem(param.thisObject, classLoader);
                        } catch (Throwable t) {
                            XposedBridge.log("MyFilesWebDav: append WebDAV item failed");
                            XposedBridge.log(t);
                        }
                    }
                }
        );

        hookAppMethod(
                DIALOG_CLASS,
                classLoader,
                "getItems",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            CharSequence[] items = (CharSequence[]) param.getResult();
                            if (items == null || items.length == 0) {
                                return;
                            }

                            ArrayList<?> listItems =
                                    (ArrayList<?>) XposedHelpers.getObjectField(param.thisObject, "listItems");
                            int limit = Math.min(items.length, listItems.size());
                            for (int i = 0; i < limit; i++) {
                                Object item = listItems.get(i);
                                if (readDomainType(item) == DOMAIN_WEBDAV) {
                                    items[i] = DISPLAY_WEBDAV;
                                    param.setResult(items);
                                    return;
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("MyFilesWebDav: rename WebDAV item failed");
                            XposedBridge.log(t);
                        }
                    }
                }
        );

        hookAppMethod(
                DIALOG_CLASS,
                classLoader,
                "createDialog",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            ensureWebDavListItem(param.thisObject, classLoader);
                            Dialog dialog = (Dialog) param.getResult();
                            if (dialog != null) {
                                replaceDialogListAdapter(param.thisObject, dialog);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("MyFilesWebDav: createDialog WebDAV fallback failed");
                            XposedBridge.log(t);
                        }
                    }
                }
        );
    }

    private static void hookDialogBuilderFallback(final ClassLoader classLoader) {
        hookAppMethod(
                "h.l",
                classLoader,
                "create",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object params = findBuilderParams(param.thisObject);
                            CharSequence[] items = findCharSequenceArray(params);
                            if (!looksLikeAddNetworkStorageItems(items)) {
                                return;
                            }

                            Object listener = findDialogListener(params, classLoader);
                            Object dialogFragment = findAddNetworkStorageDialog(listener, classLoader);
                            if (dialogFragment == null
                                    || !ensureWebDavListItem(dialogFragment, classLoader)) {
                                XposedBridge.log("MyFilesWebDav: builder fallback skipped, fragment not found");
                                return;
                            }

                            setCharSequenceArray(params, appendWebDavLabel(items));
                            XposedBridge.log("MyFilesWebDav: builder items appended");
                        } catch (Throwable t) {
                            XposedBridge.log("MyFilesWebDav: builder WebDAV fallback failed");
                            XposedBridge.log(t);
                        }
                    }
                }
        );
    }

    private static void hookWebDavSelectionFallback(final ClassLoader classLoader) {
        hookAppMethod(
                DIALOG_CLASS,
                classLoader,
                "itemClickListener$lambda$3",
                findAppClass(DIALOG_CLASS, classLoader),
                DialogInterface.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object dialogFragment = param.args[0];
                            int index = ((Integer) param.args[2]).intValue();
                            if (!isWebDavSelection(dialogFragment, index)) {
                                return;
                            }

                            DialogInterface clickedDialog = (DialogInterface) param.args[1];
                            if (openWebDavManageActivity(dialogFragment, clickedDialog)) {
                                param.setResult(null);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("MyFilesWebDav: WebDAV selection fallback failed");
                            XposedBridge.log(t);
                        }
                    }
                }
        );
    }

    private static void hookDomainHelpers(final ClassLoader classLoader) {
        hookIntBoolean("w8.AbstractC2015g", classLoader, "j0", true);
        hookIntBoolean("M5.h", classLoader, "l", true);
        hookIntBoolean("M5.h", classLoader, "q", true);

        hookAppMethod(
                "w8.AbstractC2015g",
                classLoader,
                "G",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(DISPLAY_WEBDAV);
                        }
                    }
                }
        );

        hookAppMethod(
                "w8.AbstractC2015g",
                classLoader,
                "E",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(getFtpPageType(classLoader));
                        }
                    }
                }
        );

        hookAppMethod(
                "w8.AbstractC2015g",
                classLoader,
                "F",
                int.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            String field = ((Boolean) param.args[1]) ? "f21296Q" : "f21292O";
                            param.setResult(getEnumField(classLoader, field));
                        }
                    }
                }
        );

        hookAppMethod(
                "U7.G",
                classLoader,
                "n",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(XposedHelpers.callStaticMethod(
                                    findAppClass("U7.G", classLoader),
                                    "n",
                                    202
                            ));
                        }
                    }
                }
        );

        hookAppMethod(
                "U7.AbstractC0263g",
                classLoader,
                "b",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(getFtpPageType(classLoader));
                        }
                    }
                }
        );

        hookAppMethod(
                "U7.AbstractC0263g",
                classLoader,
                "c",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(11);
                        }
                    }
                }
        );
    }

    private static void hookStoragePathUtils(final ClassLoader classLoader) {
        registerWebDavStoragePath(classLoader);

        installOptionalHook("StoragePathUtils.a", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w8.G",
                        classLoader,
                        "a",
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String path = (String) param.args[0];
                                if (isWebDavStoragePath(path)) {
                                    param.setResult(DOMAIN_WEBDAV);
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("StoragePathUtils.e", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w8.G",
                        classLoader,
                        "e",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (isWebDav(param.args[0])) {
                                    param.setResult(WEBDAV_ROOT_PATH);
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("StoragePathUtils.i", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w8.G",
                        classLoader,
                        "i",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (isWebDav(param.args[0])) {
                                    param.setResult(WEBDAV_ROOT_PATH);
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("StoragePathUtils.f", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w8.G",
                        classLoader,
                        "f",
                        int.class,
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (!isWebDav(param.args[0])) {
                                    return;
                                }
                                long id = parseWebDavServerId((String) param.args[1]);
                                param.setResult(id == Long.MIN_VALUE ? -1L : id);
                            }
                        }
                );
            }
        });

        installOptionalHook("StoragePathUtils.g", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w8.G",
                        classLoader,
                        "g",
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String path = (String) param.args[0];
                                if (isWebDavStoragePath(path)) {
                                    param.setResult(webDavPageDepth(path));
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("StoragePathUtils.C", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w8.G",
                        classLoader,
                        "C",
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String path = trimTrailingPathSlash((String) param.args[0]);
                                if (WEBDAV_ROOT_PATH.equals(path)) {
                                    param.setResult(true);
                                    return;
                                }
                                long id = parseWebDavServerId(path);
                                if (id > 0 && buildWebDavServerPath(id).equals(path)) {
                                    param.setResult(true);
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("NetworkStorageUtils.l", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w8.AbstractC2015g",
                        classLoader,
                        "l",
                        String.class,
                        int.class,
                        long.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (!isWebDav(param.args[1])) {
                                    return;
                                }
                                String path = (String) param.args[0];
                                long serverId = ((Number) param.args[2]).longValue();
                                param.setResult(detachWebDavServerPath(path, serverId));
                            }
                        }
                );
            }
        });
    }

    private static void hookFileInfoFactory(final ClassLoader classLoader) {
        hookAppMethod(
                "Y5.j",
                classLoader,
                "b",
                int.class,
                boolean.class,
                findAppClass("Y5.h", classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!isWebDav(param.args[0])) {
                            return;
                        }
                        int pattern = Integer.MIN_VALUE;
                        try {
                            boolean isFile = Boolean.TRUE.equals(param.args[1]);
                            Object argsPattern = param.args[2];
                            pattern = readFactoryPattern(argsPattern);
                            Object result = buildWebDavFactoryResult(classLoader, isFile, argsPattern);
                            if (result != null) {
                                param.setResult(result);
                                DiagnosticLogger.log("file factory handled domain=206, pattern="
                                        + pattern + ", result=" + result.getClass().getName());
                            }
                        } catch (Throwable t) {
                            DiagnosticLogger.log("file factory failed, using empty WebDAV file info, pattern=" + pattern);
                            DiagnosticLogger.log(t);
                            try {
                                param.setResult(buildEmptyWebDavFileInfo(classLoader, false));
                            } catch (Throwable fallback) {
                                DiagnosticLogger.log("file factory emergency fallback failed");
                                DiagnosticLogger.log(fallback);
                            }
                        }
                    }
                }
        );
    }

    private static Object buildWebDavFactoryResult(
            ClassLoader classLoader,
            boolean isFile,
            Object argsPattern
    ) {
        int pattern = readFactoryPattern(argsPattern);
        Object[] args = readFactoryArgs(argsPattern);

        Object nativeResult = tryBuildNativeNetworkFileInfo(classLoader, isFile, argsPattern, pattern);
        if (nativeResult != null) {
            normalizeWebDavFactoryResult(nativeResult, pattern, args, isFile);
            return nativeResult;
        }

        Object manualResult = buildManualWebDavFactoryResult(classLoader, isFile, pattern, args);
        if (manualResult != null) {
            normalizeWebDavFactoryResult(manualResult, pattern, args, isFile);
        }
        return manualResult;
    }

    private static Object tryBuildNativeNetworkFileInfo(
            ClassLoader classLoader,
            boolean isFile,
            Object argsPattern,
            int pattern
    ) {
        try {
            return XposedHelpers.callStaticMethod(
                    findAppClass("Y5.j", classLoader),
                    "b",
                    DOMAIN_NATIVE_NETWORK,
                    isFile,
                    argsPattern
            );
        } catch (Throwable t) {
            DiagnosticLogger.log("native network factory fallback unavailable, pattern="
                    + pattern + ", error=" + t.getClass().getName() + ": " + t.getMessage());
            return null;
        }
    }

    private static Object buildManualWebDavFactoryResult(
            ClassLoader classLoader,
            boolean isFile,
            int pattern,
            Object[] args
    ) {
        switch (pattern) {
            case -1:
                return buildWebDavFileInfoForPath(classLoader, firstString(args), isFile);
            case 1101:
                if (args != null && args.length > 1 && args[1] instanceof Bundle) {
                    return buildFileInfo(classLoader, (Bundle) args[1]);
                }
                return buildEmptyWebDavFileInfo(classLoader, !isFile);
            case 2003:
                return buildWebDavChildFileInfo(classLoader, isFile, args);
            case 2008:
                if (shouldCreateNetworkServerPlaceholder(args)) {
                    Object server = XposedHelpers.newInstance(findAppClass("V5.F", classLoader), DOMAIN_WEBDAV);
                    setServerIdByProbe(server, -1L);
                    return server;
                }
                return buildEmptyWebDavFileInfo(classLoader, false);
            case 2009:
                Object empty = buildEmptyWebDavFileInfo(classLoader, false);
                setFieldValueIfPresent(empty, "f7494E", true);
                return empty;
            default:
                return buildEmptyWebDavFileInfo(classLoader, !isFile);
        }
    }

    private static Object buildWebDavChildFileInfo(ClassLoader classLoader, boolean isFile, Object[] args) {
        if (args == null || args.length < 2 || args[0] == null) {
            return buildEmptyWebDavFileInfo(classLoader, !isFile);
        }
        String parentPath = readPathSafe(args[0]);
        String childName = String.valueOf(args[1]);
        String fullPath;
        if (isEmpty(parentPath)) {
            fullPath = childName;
        } else if (parentPath.endsWith("/") || parentPath.endsWith("\\")) {
            fullPath = parentPath + childName;
        } else {
            fullPath = parentPath + "/" + childName;
        }
        Object info = buildWebDavFileInfoForPath(classLoader, fullPath, isFile);
        long serverId = readServerIdSafe(args[0]);
        if (serverId != Long.MIN_VALUE) {
            setServerIdByProbe(info, serverId);
        }
        return info;
    }

    private static Object buildWebDavFileInfoForPath(ClassLoader classLoader, String fullPath, boolean isFile) {
        Object info = XposedHelpers.newInstance(findAppClass("V5.E", classLoader), fullPath == null ? "" : fullPath);
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);
        long serverId = parseWebDavServerId(fullPath);
        if (serverId != Long.MIN_VALUE) {
            setServerIdByProbe(info, serverId);
        }
        XposedHelpers.callMethod(info, "C0", !isFile);
        if (!isFile) {
            XposedHelpers.callMethod(info, "H", 12289);
        }
        return info;
    }

    private static Object buildEmptyWebDavFileInfo(ClassLoader classLoader, boolean isDirectory) {
        Object info = XposedHelpers.newInstance(findAppClass("V5.E", classLoader), "");
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);
        XposedHelpers.callMethod(info, "C0", isDirectory);
        if (isDirectory) {
            XposedHelpers.callMethod(info, "H", 12289);
        }
        return info;
    }

    private static void normalizeWebDavFactoryResult(
            Object result,
            int pattern,
            Object[] args,
            boolean isFile
    ) {
        setDomainTypeByMethodOrProbe(result, DOMAIN_WEBDAV);
        long serverId = Long.MIN_VALUE;
        if (pattern == 2003 && args != null && args.length > 0) {
            serverId = readServerIdSafe(args[0]);
        } else if (pattern == 1101 && args != null && args.length > 1 && args[1] instanceof Bundle) {
            serverId = ((Bundle) args[1]).getLong(WebDavBackend.KEY_SERVER_ID, Long.MIN_VALUE);
        }
        if (serverId == Long.MIN_VALUE) {
            serverId = parseWebDavServerId(readPathSafe(result));
        }
        if (serverId != Long.MIN_VALUE) {
            setServerIdByProbe(result, serverId);
        }
        if (!isFile) {
            tryCallMethod(result, "H", 12289);
        }
    }

    private static int readFactoryPattern(Object argsPattern) {
        try {
            Object value = getFieldValue(argsPattern, "f9110c");
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
            // Fall through to field scan.
        }
        for (Field field : allFields(argsPattern.getClass())) {
            if (!matchesFieldType(field, int.class)) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(argsPattern);
                if (value instanceof Number) {
                    int intValue = ((Number) value).intValue();
                    if (intValue == -1 || intValue >= 1000) {
                        return intValue;
                    }
                }
            } catch (Throwable ignored) {
                // Keep scanning.
            }
        }
        return Integer.MIN_VALUE;
    }

    private static Object[] readFactoryArgs(Object argsPattern) {
        try {
            Object value = getFieldValue(argsPattern, "f9109b");
            if (value instanceof Object[]) {
                return (Object[]) value;
            }
        } catch (Throwable ignored) {
            // Fall through to field scan.
        }
        for (Field field : allFields(argsPattern.getClass())) {
            if (!field.getType().isArray()) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(argsPattern);
                if (value instanceof Object[]) {
                    return (Object[]) value;
                }
            } catch (Throwable ignored) {
                // Keep scanning.
            }
        }
        return new Object[0];
    }

    private static boolean shouldCreateNetworkServerPlaceholder(Object[] args) {
        if (args == null || args.length < 2 || args[1] == null) {
            return false;
        }
        try {
            Object value = XposedHelpers.callMethod(args[1], "Y");
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void hookIntBoolean(
            String className,
            ClassLoader classLoader,
            String methodName,
            final boolean value
    ) {
        hookAppMethod(
                className,
                classLoader,
                methodName,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(value);
                        }
                    }
                }
        );
    }

    private static void hookManageUi(final ClassLoader classLoader) {
        hookAppMethod(
                "com.sec.android.app.myfiles.ui.NetworkStorageManageActivity",
                classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            int domainType = XposedHelpers.getIntField(param.thisObject, "domainType");
                            if (domainType == DOMAIN_WEBDAV) {
                                configureWebDavManageUi(param.thisObject);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("MyFilesWebDav: manage onCreate WebDAV UI failed");
                            XposedBridge.log(t);
                        }
                    }
                }
        );

        hookAppMethod(
                "com.sec.android.app.myfiles.ui.NetworkStorageManageActivity",
                classLoader,
                "onDestroy",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            WebDavBackend.clearCurrentManageActivity((Activity) param.thisObject);
                        } catch (Throwable ignored) {
                            // Activity teardown must never be blocked by the module.
                        }
                    }
                }
        );

        hookAppMethod(
                "com.sec.android.app.myfiles.ui.utils.NetworkStorageUiUtils",
                classLoader,
                "getTitle",
                Context.class,
                boolean.class,
                int.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[2])) {
                            param.setResult((Boolean) param.args[1] ? DISPLAY_WEBDAV : "添加 WebDAV");
                        }
                    }
                }
        );

        hookAppMethod(
                "com.sec.android.app.myfiles.ui.utils.NetworkStorageUiUtils",
                classLoader,
                "getDefaultPort",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(443);
                        }
                    }
                }
        );

        hookAppMethod(
                "com.sec.android.app.myfiles.ui.utils.NetworkStorageUiUtils",
                classLoader,
                "getAddServerBundle",
                findAppClass("X5.T0", classLoader),
                int.class,
                long.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!isWebDav(param.args[1])) {
                            return;
                        }
                        Bundle bundle = (Bundle) param.getResult();
                        if (bundle == null) {
                            return;
                        }
                        bundle.putString(WebDavBackend.KEY_ENCODING_TYPE, "UTF-8");
                        if (!bundle.containsKey(WebDavBackend.KEY_SERVER_NAME)
                                || isEmpty(bundle.getString(WebDavBackend.KEY_SERVER_NAME))) {
                            bundle.putString(WebDavBackend.KEY_SERVER_NAME,
                                    bundle.getString(WebDavBackend.KEY_SERVER_ADDRESS, DISPLAY_WEBDAV));
                        }
                        param.setResult(bundle);
                    }
                }
        );

        hookAppMethod(
                "com.sec.android.app.myfiles.ui.utils.NetworkStorageUiUtils",
                classLoader,
                "setNetworkEditText",
                findAppClass("X5.T0", classLoader),
                findAppClass("q8.C1747e", classLoader),
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[2])) {
                            restoreWebDavAddressText(param.args[0], param.args[1]);
                        }
                    }
                }
        );

        hookAppMethod(
                "com.sec.android.app.myfiles.ui.NetworkStorageManageActivity",
                classLoader,
                "setPageInfo",
                findAppClass("q8.C1747e", classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            int domainType = XposedHelpers.getIntField(param.thisObject, "domainType");
                            if (domainType != DOMAIN_WEBDAV) {
                                return;
                            }
                            configureWebDavManageUi(param.thisObject);
                        } catch (Throwable t) {
                            XposedBridge.log("MyFilesWebDav: set WebDAV manage UI failed");
                            XposedBridge.log(t);
                        }
                    }
                }
        );
    }

    private static void hookRequestWrapper(final ClassLoader classLoader) {
        installOptionalHook("RequestWrapper.serverCount", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "y8.f",
                        classLoader,
                        "e",
                        long.class,
                        int.class,
                        int.class,
                        Bundle.class,
                        findAppClass("J6.c", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                mergeWebDavServerCount(param);
                            }
                        }
                );
            }
        });

        hookAppMethod(
                "y8.f",
                classLoader,
                "a",
                int.class,
                int.class,
                Bundle.class,
                findAppClass("y8.g", classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Bundle args = (Bundle) param.args[2];
                        int reqCode = (Integer) param.args[1];
                        if (reqCode == 15) {
                            return;
                        }
                        if (!isWebDavRequest(param.args[0], args)) {
                            return;
                        }
                        args = normalizeWebDavRequestArgs(args);
                        Object callback = param.args[3];
                        DiagnosticLogger.log("request wrapper async WebDAV domain=" + param.args[0]
                                + ", reqCode=" + reqCode
                                + ", args=" + describeRequestBundle(args));
                        long requestId = WebDavBackend.handleAsync(reqCode, args, callback);
                        param.setResult(requestId);
                    }
                }
        );

        hookAppMethod(
                "y8.f",
                classLoader,
                "e",
                long.class,
                int.class,
                int.class,
                Bundle.class,
                findAppClass("J6.c", classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Bundle args = (Bundle) param.args[3];
                        int reqCode = (Integer) param.args[2];
                        if (reqCode == 15) {
                            return;
                        }
                        if (!isWebDavRequest(param.args[1], args)) {
                            return;
                        }
                        args = normalizeWebDavRequestArgs(args);
                        DiagnosticLogger.log("request wrapper sync WebDAV domain=" + param.args[1]
                                + ", reqCode=" + reqCode
                                + ", args=" + describeRequestBundle(args));
                        param.setResult(WebDavBackend.handleSync(reqCode, args, param.args[4]));
                    }
                }
        );
    }

    private static void mergeWebDavServerCount(XC_MethodHook.MethodHookParam param) {
        try {
            int domainType = ((Number) param.args[1]).intValue();
            int reqCode = ((Number) param.args[2]).intValue();
            if (reqCode != 15 || (domainType != -1 && domainType != DOMAIN_WEBDAV)) {
                return;
            }
            int webDavCount = WebDavBackend.getServerBundles().size();
            if (webDavCount <= 0 && domainType != DOMAIN_WEBDAV) {
                return;
            }

            Bundle result = param.getResult() instanceof Bundle
                    ? new Bundle((Bundle) param.getResult())
                    : new Bundle();
            int original = readBundleInt(result, WebDavBackend.KEY_RESULT, 0);
            int merged = domainType == DOMAIN_WEBDAV ? webDavCount : original + webDavCount;
            result.putBoolean(WebDavBackend.KEY_IS_SUCCESS, true);
            result.putBoolean(WebDavBackend.KEY_IS_VALID_REQUEST, true);
            result.putInt(WebDavBackend.KEY_RESULT, merged);
            param.setResult(result);
            DiagnosticLogger.log("server count merged domain=" + domainType
                    + ", original=" + original
                    + ", webdav=" + webDavCount
                    + ", merged=" + merged);
        } catch (Throwable t) {
            DiagnosticLogger.log("server count merge failed");
            DiagnosticLogger.log(t);
        }
    }

    private static void hookOperationRouting(final ClassLoader classLoader) {
        installOptionalHook("OperationFactory.map", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "F1.m",
                        classLoader,
                        "b",
                        findAppClass("e6.u", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object result = param.getResult();
                                if (result instanceof SparseArray) {
                                    ((SparseArray) result).put(DOMAIN_WEBDAV,
                                            newWebDavNetworkOperation(classLoader, param.args[0]));
                                    DiagnosticLogger.log("operation map added WebDAV -> J6.h");
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("OperationFactory.direct", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "p8.AbstractC1705c",
                        classLoader,
                        "a",
                        Context.class,
                        int.class,
                        findAppClass("e6.u", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (isWebDav(param.args[1])) {
                                    param.setResult(newWebDavNetworkOperation(classLoader, param.args[2]));
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("AbsFileOperator.l", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "e6.AbstractC1145d",
                        classLoader,
                        "l",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (isWebDav(param.args[0])) {
                                    param.setResult(newWebDavNetworkOperation(classLoader, param.thisObject));
                                    DiagnosticLogger.log("operation route WebDAV -> J6.h");
                                }
                            }
                        }
                );
            }
        });
    }

    private static Object newWebDavNetworkOperation(ClassLoader classLoader, Object collector) {
        return XposedHelpers.newInstance(findAppClass("J6.h", classLoader), collector);
    }

    private static Object newWebDavFileRepository(ClassLoader classLoader) {
        return XposedHelpers.newInstance(findAppClass("w6.H", classLoader));
    }

    private static void hookRepositories(final ClassLoader classLoader) {
        installOptionalHook("RepositoryFactory.file.WebDAV", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "dc.g",
                        classLoader,
                        "B",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (isWebDav(param.args[0])) {
                                    param.setResult(newWebDavFileRepository(classLoader));
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("NetworkFileRepository.k.WebDAV", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w6.H",
                        classLoader,
                        "k",
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String path = (String) param.args[0];
                                if (!isWebDavStoragePath(path)) {
                                    return;
                                }
                                param.setResult(buildWebDavFileInfoFromPath(classLoader, path, true));
                                DiagnosticLogger.log("network repository k() handled WebDAV path=" + path);
                            }
                        }
                );
            }
        });

        installOptionalHook("NetworkFileRepository.i.WebDAV", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "w6.H",
                        classLoader,
                        "i",
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String path = (String) param.args[0];
                                if (!isWebDavStoragePath(path)) {
                                    return;
                                }
                                Bundle query = new Bundle();
                                query.putInt("domainType", DOMAIN_WEBDAV);
                                query.putString("path", path);
                                long serverId = parseWebDavServerId(path);
                                if (serverId > 0) {
                                    query.putLong(WebDavBackend.KEY_SERVER_ID, serverId);
                                }
                                param.setResult(loadWebDavFileInfoList(classLoader, query));
                                DiagnosticLogger.log("network repository i() handled WebDAV path=" + path);
                            }
                        }
                );
            }
        });

        hookAppMethod(
                "w6.I",
                classLoader,
                "F",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!isWebDav(param.args[0])) {
                            return;
                        }
                        try {
                            Context context = findContextField(param.thisObject);
                            WebDavBackend.init(context);
                        } catch (Throwable ignored) {
                            // Application.onCreate normally initializes the store.
                        }
                        param.setResult(buildServerInfoList(classLoader));
                    }
                }
        );

        hookAppMethod(
                "w6.I",
                classLoader,
                "l",
                findAppClass("S5.j", classLoader),
                findAppClass("S5.h", classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object query = param.args[0];
                            Bundle bundle = findBundleField(query);
                            int requestType = bundle != null ? bundle.getInt("requestServerType", -1) : -1;
                            if (requestType != -1 && requestType != DOMAIN_WEBDAV && requestType != 202 && requestType != 205) {
                                return;
                            }
                            List<?> original = (List<?>) param.getResult();
                            ArrayList<Object> merged = new ArrayList<>();
                            if (original != null) {
                                merged.addAll(original);
                            }
                            ArrayList<Object> webDavServers = buildServerInfoList(classLoader);
                            for (Object server : webDavServers) {
                                if (!containsSameServer(merged, server)) {
                                    merged.add(server);
                                }
                            }
                            param.setResult(merged);
                            DiagnosticLogger.log("repository list merged requestServerType=" + requestType
                                    + ", original=" + (original != null ? original.size() : 0)
                                    + ", webdav=" + webDavServers.size()
                                    + ", merged=" + merged.size());
                        } catch (Throwable t) {
                            XposedBridge.log("MyFilesWebDav: append WebDAV server list failed");
                            XposedBridge.log(t);
                        }
                    }
                }
        );

        hookAppMethod(
                "w6.H",
                classLoader,
                "E",
                int.class,
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(buildFileInfo(classLoader, (Bundle) param.args[1]));
                        }
                    }
                }
        );

        hookAppMethod(
                "w6.H",
                classLoader,
                "l",
                findAppClass("S5.j", classLoader),
                findAppClass("S5.h", classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Bundle query = findBundleField(param.args[0]);
                            if (query == null || query.getInt("domainType", -1) != DOMAIN_WEBDAV) {
                                return;
                            }
                            param.setResult(loadWebDavFileInfoList(classLoader, query));
                        } catch (Throwable t) {
                            DiagnosticLogger.log("file repository WebDAV list failed");
                            DiagnosticLogger.log(t);
                            param.setResult(new ArrayList<Object>());
                        }
                    }
                }
        );
    }

    private static void hookServerClick(final ClassLoader classLoader) {
        hookAppMethod(
                "F7.e",
                classLoader,
                "z",
                findAppClass("x7.C2094a", classLoader),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object clickInfo = param.args[0];
                            Object serverInfo = getFieldValue(clickInfo, "f24160a");
                            int domainType = readDomainType(serverInfo);
                            long serverId = readServerIdSafe(serverInfo);
                            DiagnosticLogger.log("server click seen domain=" + domainType
                                    + ", id=" + serverId
                                    + ", class=" + (serverInfo != null
                                    ? serverInfo.getClass().getName() : "null"));
                            if (domainType != DOMAIN_WEBDAV) {
                                return;
                            }
                            if (openWebDavServerPage(classLoader, param.thisObject, serverInfo)) {
                                param.setResult(true);
                            }
                        } catch (Throwable t) {
                            DiagnosticLogger.log("server click WebDAV open failed");
                            DiagnosticLogger.log(t);
                        }
                    }
                }
        );
    }

    private static ArrayList<Object> loadWebDavFileInfoList(ClassLoader classLoader, Bundle query) {
        long serverId = query.getLong(WebDavBackend.KEY_SERVER_ID, -1L);
        String pagePath = query.getString("path", "/");
        String normalizedPagePath = trimTrailingPathSlash(pagePath);
        if (NETWORK_ROOT_PATH.equals(normalizedPagePath)) {
            ArrayList<Object> roots = new ArrayList<>();
            roots.add(buildVirtualFolderInfo(classLoader, WEBDAV_ROOT_PATH, DISPLAY_WEBDAV));
            DiagnosticLogger.log("file repository WebDAV virtual root path=" + pagePath
                    + ", count=" + roots.size());
            return roots;
        }
        if (WEBDAV_ROOT_PATH.equals(normalizedPagePath)) {
            ArrayList<Object> servers = buildServerInfoList(classLoader);
            DiagnosticLogger.log("file repository WebDAV server root path=" + pagePath
                    + ", count=" + servers.size());
            return servers;
        }
        if (serverId <= 0) {
            long parsed = parseWebDavServerId(pagePath);
            if (parsed > 0) {
                serverId = parsed;
            }
        }
        String webDavPath = detachWebDavServerPath(pagePath, serverId);

        Bundle args = new Bundle();
        args.putLong(WebDavBackend.KEY_SERVER_ID, serverId);
        args.putString(WebDavBackend.KEY_FILE_PATH, webDavPath);
        args.putString(WebDavBackend.KEY_SHARED_FOLDER,
                query.getString(WebDavBackend.KEY_SHARED_FOLDER, ""));
        args.putInt(WebDavBackend.KEY_REQUEST_PAGE_NUMBER,
                query.getInt(WebDavBackend.KEY_REQUEST_PAGE_NUMBER, 0));

        Bundle result = WebDavBackend.handleSync(9, args);
        ArrayList<Object> infos = new ArrayList<>();
        if (result == null || !result.getBoolean(WebDavBackend.KEY_IS_SUCCESS)) {
            DiagnosticLogger.log("file repository WebDAV list result failed, path=" + webDavPath
                    + ", result=" + String.valueOf(result));
            return infos;
        }

        ArrayList<Bundle> fileBundles = result.getParcelableArrayList(WebDavBackend.KEY_FILE_LIST);
        if (fileBundles != null) {
            for (Bundle bundle : fileBundles) {
                infos.add(buildFileInfo(classLoader, bundle));
            }
        }
        DiagnosticLogger.log("file repository WebDAV list loaded path=" + webDavPath
                + ", count=" + infos.size());
        return infos;
    }

    private static boolean openWebDavServerPage(
            ClassLoader classLoader,
            Object controller,
            Object serverInfo
    ) {
        long serverId = readServerIdSafe(serverInfo);
        Bundle serverBundle = findWebDavServerBundle(serverId);
        if (serverBundle == null) {
            DiagnosticLogger.log("server click skipped, WebDAV server bundle not found id=" + serverId);
            return false;
        }

        int instanceId = readControllerInstanceId(controller);
        Object currentPageInfo = getFieldValueOrNull(controller, "f23389p");
        Object pageInfo = XposedHelpers.newInstance(findAppClass("q8.C1747e", classLoader));
        Object pageType = getFtpPageType(classLoader);
        XposedHelpers.callMethod(pageInfo, "N", pageType);

        String name = serverBundle.getString(WebDavBackend.KEY_SERVER_NAME, "");
        String address = serverBundle.getString(WebDavBackend.KEY_SERVER_ADDRESS, "");
        if (isEmpty(name)) {
            name = isEmpty(address) ? DISPLAY_WEBDAV : address;
        }
        String rootPath = buildWebDavServerPath(serverId);

        XposedHelpers.callMethod(pageInfo, "H", WebDavBackend.KEY_SERVER_NAME, name);
        XposedHelpers.callMethod(pageInfo, "H", WebDavBackend.KEY_SERVER_ADDRESS, address);
        XposedHelpers.callMethod(pageInfo, "H", WebDavBackend.KEY_SHARED_FOLDER,
                serverBundle.getString(WebDavBackend.KEY_SHARED_FOLDER, ""));
        XposedHelpers.callMethod(pageInfo, "D",
                serverBundle.getInt(WebDavBackend.KEY_SERVER_PORT, 443),
                WebDavBackend.KEY_SERVER_PORT);
        XposedHelpers.callMethod(pageInfo, "F", WebDavBackend.KEY_SERVER_ID, serverId);
        XposedHelpers.callMethod(pageInfo, "I", WebDavBackend.KEY_IS_ANONYMOUS_MODE,
                serverBundle.getBoolean(WebDavBackend.KEY_IS_ANONYMOUS_MODE, false));
        XposedHelpers.callMethod(pageInfo, "O", rootPath);
        XposedHelpers.callMethod(pageInfo, "K", DOMAIN_WEBDAV);
        setFieldValueIfPresent(pageInfo, "f21254k", true);
        setFieldValueIfPresent(pageInfo, "f21261x", instanceId);
        copyNavigationMode(currentPageInfo, pageInfo);
        setNextDepth(currentPageInfo, pageInfo);

        Object fragment = XposedHelpers.callStaticMethod(findAppClass("D5.b", classLoader), "C", instanceId);
        Object fragmentActivity = XposedHelpers.callMethod(fragment, "c");
        Object navigator = XposedHelpers.callStaticMethod(findAppClass("B5.a", classLoader), "r", instanceId);
        XposedHelpers.callMethod(navigator, "d", fragmentActivity, pageInfo);
        DiagnosticLogger.log("server click opened WebDAV page id=" + serverId + ", path=" + rootPath);
        return true;
    }

    private static void hookListAdapters(final ClassLoader classLoader) {
        hookAppMethod(
                "com.sec.android.app.myfiles.ui.pages.adapter.FileListAdapter",
                classLoader,
                "updateItems",
                List.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!isNetworkServerAdapter(param.thisObject)) {
                                return;
                            }
                            List<?> original = (List<?>) param.args[0];
                            ArrayList<Object> merged = mergeWebDavServerItems(original, classLoader);
                            if (merged == null) {
                                return;
                            }
                            param.args[0] = merged;
                            DiagnosticLogger.log("adapter updateItems merged adapter="
                                    + param.thisObject.getClass().getName()
                                    + ", original=" + (original != null ? original.size() : 0)
                                    + ", merged=" + merged.size());
                        } catch (Throwable t) {
                            DiagnosticLogger.log("adapter updateItems merge failed");
                            DiagnosticLogger.log(t);
                        }
                    }
                }
        );
    }

    private static ArrayList<Object> mergeWebDavServerItems(List<?> original, ClassLoader classLoader) {
        ArrayList<Object> webDavServers = buildServerInfoList(classLoader);
        if (webDavServers.isEmpty()) {
            return null;
        }
        ArrayList<Object> merged = new ArrayList<>();
        if (original != null) {
            merged.addAll(original);
        }
        boolean changed = false;
        for (Object server : webDavServers) {
            if (!containsSameServer(merged, server)) {
                merged.add(server);
                changed = true;
            }
        }
        return changed ? merged : null;
    }

    private static boolean isNetworkServerAdapter(Object adapter) {
        if (adapter == null) {
            return false;
        }
        Class<?> current = adapter.getClass();
        while (current != null) {
            String name = current.getName();
            if ("com.sec.android.app.myfiles.ui.pages.adapter.NetworkStorageServerListAdapter".equals(name)
                    || "com.sec.android.app.myfiles.ui.pages.adapter.column.page.ColumnViewNetworkServerAdapter".equals(name)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static ArrayList<Object> buildServerInfoList(ClassLoader classLoader) {
        ArrayList<Object> result = new ArrayList<>();
        ArrayList<Bundle> serverBundles = WebDavBackend.getServerBundles();
        Class<?> serverInfoClass = findAppClass("V5.F", classLoader);
        for (Bundle bundle : serverBundles) {
            Object info = XposedHelpers.newInstance(serverInfoClass, DOMAIN_WEBDAV);
            populateServerInfo(info, bundle);
            result.add(info);
        }
        return result;
    }

    private static Object buildVirtualFolderInfo(ClassLoader classLoader, String path, String name) {
        Class<?> fileInfoClass = findAppClass("V5.E", classLoader);
        Object info = XposedHelpers.newInstance(fileInfoClass, path);
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);
        setServerIdByProbe(info, -1L);
        XposedHelpers.callMethod(info, "w0", path);
        XposedHelpers.callMethod(info, "C0", true);
        XposedHelpers.callMethod(info, "H", 12289);
        tryCallMethod(info, "k0", name);
        setStringFieldReturnedByMethod(info, "getName", name);
        return info;
    }

    private static Object buildWebDavFileInfoFromPath(
            ClassLoader classLoader,
            String path,
            boolean defaultDirectory
    ) {
        String normalized = trimTrailingPathSlash(path);
        if (WEBDAV_ROOT_PATH.equals(normalized)) {
            return buildVirtualFolderInfo(classLoader, WEBDAV_ROOT_PATH, DISPLAY_WEBDAV);
        }

        long serverId = parseWebDavServerId(normalized);
        if (serverId > 0) {
            try {
                Bundle args = new Bundle();
                args.putLong(WebDavBackend.KEY_SERVER_ID, serverId);
                args.putString(WebDavBackend.KEY_FILE_PATH, detachWebDavServerPath(normalized, serverId));
                Bundle result = WebDavBackend.handleSync(10, args);
                if (result != null && result.getBoolean(WebDavBackend.KEY_IS_SUCCESS)) {
                    Bundle fileObject = result.getBundle(WebDavBackend.KEY_FILE_OBJECT);
                    if (fileObject != null) {
                        return buildFileInfo(classLoader, fileObject);
                    }
                }
            } catch (Throwable t) {
                DiagnosticLogger.log("build WebDAV file info by stat failed, path=" + normalized);
                DiagnosticLogger.log(t);
            }
        }

        return buildWebDavFileInfoForPath(classLoader, normalized, !defaultDirectory);
    }

    private static void populateServerInfo(Object info, Bundle bundle) {
        long serverId = bundle.getLong(WebDavBackend.KEY_SERVER_ID, -1L);
        String address = bundle.getString(WebDavBackend.KEY_SERVER_ADDRESS, "");
        String displayName = bundle.getString(WebDavBackend.KEY_SERVER_NAME, "");
        if (isEmpty(displayName)) {
            displayName = isEmpty(address) ? DISPLAY_WEBDAV : address;
        }
        String path = buildWebDavServerPath(serverId);

        populateServerInfoFieldsByLayout(info, bundle, serverId, address, displayName);
        setServerIdByProbe(info, serverId);
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);

        XposedHelpers.callMethod(info, "f", path);
        XposedHelpers.callMethod(info, "w0", path);
        tryCallMethod(info, "M", bundle.getLong(WebDavBackend.KEY_SERVER_ADDED_TIME, 0L));
        XposedHelpers.callMethod(info, "C0", true);
        XposedHelpers.callMethod(info, "H", 12289);
    }

    private static void populateServerInfoFieldsByLayout(
            Object info,
            Bundle bundle,
            long serverId,
            String address,
            String displayName
    ) {
        setDeclaredFieldByTypeIndex(info, long.class, 0, serverId);
        setDeclaredFieldByTypeIndex(info, int.class, 0, bundle.getInt(WebDavBackend.KEY_SERVER_PORT, 443));
        setDeclaredFieldByTypeIndex(info, boolean.class, 0, true);
        setDeclaredFieldByTypeIndex(info, boolean.class, 1, true);
        setDeclaredFieldByTypeIndex(info, boolean.class, 2,
                bundle.getBoolean(WebDavBackend.KEY_IS_ANONYMOUS_MODE, false));

        setDeclaredFieldByTypeIndex(info, String.class, 0, DISPLAY_WEBDAV);
        setDeclaredFieldByTypeIndex(info, String.class, 1, address);
        setDeclaredFieldByTypeIndex(info, String.class, 2,
                bundle.getString(WebDavBackend.KEY_ACCOUNT_NAME, ""));
        setDeclaredFieldByTypeIndex(info, String.class, 3,
                bundle.getString(WebDavBackend.KEY_ACCOUNT_PASSWORD, ""));
        setDeclaredFieldByTypeIndex(info, String.class, 4,
                bundle.getString("private_key_file_path", ""));
        setDeclaredFieldByTypeIndex(info, String.class, 5,
                bundle.getString("passPhrase", ""));
        setDeclaredFieldByTypeIndex(info, String.class, 6,
                bundle.getString(WebDavBackend.KEY_ENCODING_TYPE, "UTF-8"));
        setDeclaredFieldByTypeIndex(info, String.class, 7, displayName);
        setDeclaredFieldByTypeIndex(info, String.class, 8,
                bundle.getString(WebDavBackend.KEY_SHARED_FOLDER, ""));
        setDeclaredFieldByTypeIndex(info, long.class, 1,
                bundle.getLong(WebDavBackend.KEY_SERVER_ADDED_TIME, 0L));
        setStringFieldReturnedByMethod(info, "getName", displayName);
    }

    static void notifyServerListChanged() {
        ClassLoader classLoader = targetClassLoader;
        if (classLoader == null) {
            DiagnosticLogger.log("server list refresh skipped, classLoader is null");
            return;
        }
        try {
            Class<?> listenersClass = findAppClass("U7.K", classLoader);
            Object listeners = findStaticIterableField(listenersClass);
            int count = 0;
            if (listeners instanceof Iterable) {
                ArrayList<Object> snapshot = new ArrayList<>();
                for (Object listener : (Iterable<?>) listeners) {
                    snapshot.add(listener);
                }
                for (Object listener : snapshot) {
                    try {
                        XposedHelpers.callMethod(listener, "onCountChanged");
                        count++;
                    } catch (Throwable t) {
                        DiagnosticLogger.log("server list listener refresh failed: "
                                + listener.getClass().getName());
                        DiagnosticLogger.log(t);
                    }
                }
            }
            DiagnosticLogger.log("server list refresh notified listeners=" + count);
        } catch (Throwable t) {
            DiagnosticLogger.log("server list refresh notify failed");
            DiagnosticLogger.log(t);
        }
    }

    private static Object buildFileInfo(ClassLoader classLoader, Bundle bundle) {
        Class<?> fileInfoClass = findAppClass("V5.E", classLoader);
        long serverId = bundle.getLong(WebDavBackend.KEY_SERVER_ID, -1L);
        String relativePath = bundle.getString(WebDavBackend.KEY_FILE_PATH, "/");
        String fullPath = trimTrailingPathSlash(attachWebDavServerPath(relativePath, serverId));
        String displayName = bundle.getString(WebDavBackend.KEY_FILE_NAME, "");
        if (isEmpty(displayName)) {
            displayName = WebDavClient.nameOf(relativePath);
        }
        Object info = XposedHelpers.newInstance(fileInfoClass, fullPath);
        boolean isDirectory = bundle.getBoolean(WebDavBackend.KEY_IS_DIRECTORY);
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);
        setServerIdByProbe(info, serverId);
        XposedHelpers.callMethod(info, "w0", fullPath);
        if (!isEmpty(displayName)) {
            tryCallMethod(info, "k0", displayName);
            setStringFieldReturnedByMethod(info, "getName", displayName);
        }
        XposedHelpers.callMethod(info, "C0", isDirectory);
        XposedHelpers.callMethod(info, "G", bundle.getLong(WebDavBackend.KEY_FILE_SIZE, 0L));
        XposedHelpers.callMethod(info, "M", bundle.getLong(WebDavBackend.KEY_FILE_DATE, 0L));
        XposedHelpers.callMethod(info, "m0", bundle.getString(WebDavBackend.KEY_MIME_TYPE, "application/octet-stream"));
        if (isDirectory) {
            XposedHelpers.callMethod(info, "H", 12289);
        } else {
            try {
                Class<?> fileTypeClass = findAppClass("U7.G", classLoader);
                String mime = (String) XposedHelpers.callStaticMethod(fileTypeClass, "m", fullPath);
                int type = (Integer) XposedHelpers.callStaticMethod(fileTypeClass, "g", fullPath, mime);
                XposedHelpers.callMethod(info, "H", type);
            } catch (Throwable ignored) {
                XposedHelpers.callMethod(info, "H", 0);
            }
        }
        return info;
    }

    private static String buildWebDavServerPath(long serverId) {
        return WEBDAV_ROOT_PATH + "/" + serverId;
    }

    private static String detachWebDavServerPath(String fullPath, long serverId) {
        String basePath = buildWebDavServerPath(serverId);
        if (isEmpty(fullPath) || "/".equals(fullPath) || basePath.equals(fullPath)) {
            return "/";
        }
        if (fullPath.startsWith(basePath + "/")) {
            String relative = fullPath.substring(basePath.length());
            return isEmpty(relative) ? "/" : relative;
        }
        if (fullPath.startsWith(WEBDAV_ROOT_PATH + "/")) {
            int start = (WEBDAV_ROOT_PATH + "/").length();
            int slash = fullPath.indexOf('/', start);
            if (slash >= 0 && slash + 1 < fullPath.length()) {
                return fullPath.substring(slash);
            }
            return "/";
        }
        return fullPath;
    }

    private static String attachWebDavServerPath(String relativePath, long serverId) {
        String basePath = buildWebDavServerPath(serverId);
        if (isEmpty(relativePath) || "/".equals(relativePath)) {
            return basePath;
        }
        if (relativePath.startsWith(basePath)) {
            return relativePath;
        }
        return basePath + (relativePath.startsWith("/") ? relativePath : "/" + relativePath);
    }

    private static String trimTrailingPathSlash(String path) {
        if (path == null || path.length() <= 1) {
            return path;
        }
        String value = path;
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean isWebDavRequest(Object domainValue, Bundle args) {
        if (isWebDav(domainValue)) {
            return true;
        }
        long serverId = readBundleServerId(args);
        if (serverId <= 0) {
            return false;
        }
        try {
            return findWebDavServerBundle(serverId) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long readBundleServerId(Bundle args) {
        if (args == null) {
            return Long.MIN_VALUE;
        }
        String[] idKeys = {
                WebDavBackend.KEY_SERVER_ID,
                "srcServerId",
                "dstServerId"
        };
        for (String key : idKeys) {
            if (args.containsKey(key)) {
                long value = args.getLong(key, Long.MIN_VALUE);
                if (value > 0) {
                    return value;
                }
            }
        }
        String[] pathKeys = {
                WebDavBackend.KEY_FILE_PATH,
                WebDavBackend.KEY_SOURCE_PATH,
                WebDavBackend.KEY_PARENT_PATH,
                WebDavBackend.KEY_DESTINATION_FOLDER_PATH,
                "path"
        };
        for (String key : pathKeys) {
            String value = args.getString(key);
            long parsed = parseWebDavServerId(value);
            if (parsed > 0) {
                return parsed;
            }
        }
        return Long.MIN_VALUE;
    }

    private static int readBundleInt(Bundle bundle, String key, int fallback) {
        if (bundle == null || key == null || !bundle.containsKey(key)) {
            return fallback;
        }
        Object value = bundle.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        return fallback;
    }

    private static Bundle normalizeWebDavRequestArgs(Bundle args) {
        if (args == null) {
            return null;
        }
        Bundle normalized = new Bundle(args);
        long serverId = readBundleServerId(normalized);
        if (serverId > 0 && !normalized.containsKey(WebDavBackend.KEY_SERVER_ID)) {
            normalized.putLong(WebDavBackend.KEY_SERVER_ID, serverId);
        }
        normalizePathKey(normalized, serverId, WebDavBackend.KEY_FILE_PATH);
        normalizePathKey(normalized, serverId, WebDavBackend.KEY_SOURCE_PATH);
        normalizePathKey(normalized, serverId, WebDavBackend.KEY_PARENT_PATH);
        normalizePathKey(normalized, serverId, WebDavBackend.KEY_DESTINATION_FOLDER_PATH);
        normalizePathKey(normalized, serverId, "path");
        return normalized;
    }

    private static void normalizePathKey(Bundle bundle, long serverId, String key) {
        if (bundle == null || !bundle.containsKey(key)) {
            return;
        }
        String value = bundle.getString(key);
        if (value == null) {
            return;
        }
        String normalized = normalizeWebDavPathValue(value, serverId);
        if (!value.equals(normalized)) {
            DiagnosticLogger.log("normalized WebDAV path key=" + key
                    + ", from=" + value + ", to=" + normalized);
            bundle.putString(key, normalized);
        }
    }

    private static String normalizeWebDavPathValue(String value, long serverId) {
        if (isEmpty(value)) {
            return "/";
        }
        String trimmed = value.trim();
        long effectiveServerId = serverId > 0 ? serverId : parseWebDavServerId(trimmed);
        if (effectiveServerId > 0) {
            String webDavBase = buildWebDavServerPath(effectiveServerId);
            if (NETWORK_ROOT_PATH.equals(trimmed) || WEBDAV_ROOT_PATH.equals(trimmed)) {
                return "/";
            }
            if (trimmed.equals(webDavBase) || trimmed.equals(webDavBase + "/")) {
                return "/";
            }
            if (trimmed.startsWith(webDavBase + "/")) {
                return normalizeRelativeWebDavPath(trimmed.substring(webDavBase.length()));
            }

            String networkPrefix = "/Network Storage/";
            if (trimmed.startsWith(networkPrefix)) {
                int typeStart = networkPrefix.length();
                int typeEnd = trimmed.indexOf('/', typeStart);
                if (typeEnd > typeStart && typeEnd + 1 < trimmed.length()) {
                    int idStart = typeEnd + 1;
                    int idEnd = idStart;
                    while (idEnd < trimmed.length() && Character.isDigit(trimmed.charAt(idEnd))) {
                        idEnd++;
                    }
                    if (idEnd > idStart) {
                        try {
                            long parsedId = Long.parseLong(trimmed.substring(idStart, idEnd));
                            if (parsedId == effectiveServerId) {
                                return idEnd < trimmed.length()
                                        ? normalizeRelativeWebDavPath(trimmed.substring(idEnd))
                                        : "/";
                            }
                        } catch (NumberFormatException ignored) {
                            // Keep original relative normalization below.
                        }
                    }
                }
            }
        }
        return normalizeRelativeWebDavPath(trimmed);
    }

    private static String normalizeRelativeWebDavPath(String path) {
        if (isEmpty(path)) {
            return "/";
        }
        String value = path.trim();
        if (isEmpty(value)) {
            return "/";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.contains("//")) {
            value = value.replace("//", "/");
        }
        return trimTrailingPathSlash(value);
    }

    private static String describeRequestBundle(Bundle bundle) {
        if (bundle == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("{");
        for (String key : bundle.keySet()) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(key).append('=');
            if (WebDavBackend.KEY_ACCOUNT_PASSWORD.equals(key)) {
                String value = bundle.getString(key);
                builder.append(value != null && value.length() > 0 ? "***" : "");
            } else if (WebDavBackend.KEY_FILE_DESCRIPTOR.equals(key)) {
                builder.append(bundle.get(key) != null ? "<pfd>" : "null");
            } else {
                builder.append(String.valueOf(bundle.get(key)));
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private static Bundle findWebDavServerBundle(long serverId) {
        ArrayList<Bundle> bundles = WebDavBackend.getServerBundles();
        for (Bundle bundle : bundles) {
            if (bundle != null && bundle.getLong(WebDavBackend.KEY_SERVER_ID, -1L) == serverId) {
                return bundle;
            }
        }
        return null;
    }

    private static int readControllerInstanceId(Object controller) {
        try {
            Object value = XposedHelpers.callMethod(controller, "k");
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
            // Fall through to current page info.
        }
        Object pageInfo = getFieldValueOrNull(controller, "f23389p");
        try {
            Object value = getFieldValue(pageInfo, "f21261x");
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
            // Keep defaulting.
        }
        return -1;
    }

    private static void copyNavigationMode(Object currentPageInfo, Object targetPageInfo) {
        if (currentPageInfo == null) {
            return;
        }
        try {
            Object navigationMode = getFieldValue(currentPageInfo, "f21255n");
            XposedHelpers.callMethod(targetPageInfo, "M", navigationMode);
        } catch (Throwable ignored) {
            // Default navigation mode is fine.
        }
        try {
            Bundle currentExtras = (Bundle) getFieldValue(currentPageInfo, "f21256p");
            int menuType = currentExtras.getInt("menuType", -1);
            XposedHelpers.callMethod(targetPageInfo, "D", menuType, "menuType");
        } catch (Throwable ignored) {
            // menuType is optional.
        }
    }

    private static void setNextDepth(Object currentPageInfo, Object targetPageInfo) {
        int depth = 1;
        try {
            Object value = getFieldValue(currentPageInfo, "f21258r");
            if (value instanceof Number) {
                depth = ((Number) value).intValue() + 1;
            }
        } catch (Throwable ignored) {
            // Keep default depth.
        }
        setFieldValueIfPresent(targetPageInfo, "f21258r", depth);
    }

    private static boolean isWebDavSelection(Object dialogFragment, int index) {
        if (dialogFragment == null || index < 0) {
            return false;
        }
        try {
            ArrayList<?> listItems =
                    (ArrayList<?>) XposedHelpers.getObjectField(dialogFragment, "listItems");
            if (listItems != null && index < listItems.size()) {
                return readDomainType(listItems.get(index)) == DOMAIN_WEBDAV;
            }
            return listItems != null && index == listItems.size() && listItems.size() >= 3;
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: read WebDAV selection failed");
            XposedBridge.log(t);
            return false;
        }
    }

    private static boolean openWebDavManageActivity(Object dialogFragment, DialogInterface clickedDialog) {
        try {
            Context context = (Context) XposedHelpers.callMethod(dialogFragment, "getBaseContext");
            int instanceId = ((Integer) XposedHelpers.callMethod(dialogFragment, "getInstanceId")).intValue();
            if (openWebDavManageActivityBySamsungRouter(dialogFragment, context, instanceId)) {
                if (clickedDialog != null) {
                    clickedDialog.dismiss();
                }
                XposedBridge.log("MyFilesWebDav: WebDAV manage activity started by Samsung router");
                return true;
            }
            Intent intent = new Intent("com.sec.android.app.myfiles.MANAGE_NETWORK_STORAGE_MANAGE");
            intent.setComponent(new ComponentName(
                    context.getPackageName(),
                    "com.sec.android.app.myfiles.ui.NetworkStorageManageActivity"
            ));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("instanceId", instanceId);
            intent.putExtra("domainType", DOMAIN_WEBDAV);
            intent.putExtra(WebDavBackend.KEY_SERVER_PORT, 443);
            context.startActivity(intent);
            if (clickedDialog != null) {
                clickedDialog.dismiss();
            }
            XposedBridge.log("MyFilesWebDav: WebDAV manage activity started");
            return true;
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: start WebDAV manage activity failed");
            XposedBridge.log(t);
            return false;
        }
    }

    private static boolean openWebDavManageActivityBySamsungRouter(
            Object dialogFragment,
            Context context,
            int instanceId
    ) {
        ClassLoader classLoader = targetClassLoader;
        if (classLoader == null) {
            return false;
        }
        try {
            Object activity = XposedHelpers.callMethod(dialogFragment, "c");
            Object decorView = XposedHelpers.callMethod(dialogFragment, "getDialogDecorView");
            Class<?> uiUtilsClass = findAppClass(
                    "com.sec.android.app.myfiles.ui.utils.NetworkStorageUiUtils",
                    classLoader
            );
            Object uiUtils = XposedHelpers.getStaticObjectField(uiUtilsClass, "INSTANCE");
            Object pageInfo = XposedHelpers.callMethod(
                    uiUtils,
                    "getNetworkManagementPageInfo",
                    DOMAIN_WEBDAV,
                    instanceId
            );
            Class<?> dialogUtilsClass = findAppClass(
                    "com.sec.android.app.myfiles.ui.dialog.utils.NetworkStorageDialogUtils",
                    classLoader
            );
            Object dialogUtils = XposedHelpers.getStaticObjectField(dialogUtilsClass, "INSTANCE");
            XposedHelpers.callMethod(
                    dialogUtils,
                    "enterNetworkManagement",
                    activity,
                    pageInfo,
                    context,
                    instanceId,
                    decorView
            );
            return true;
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: Samsung router start failed, falling back");
            XposedBridge.log(t);
            return false;
        }
    }

    private static void configureWebDavManageUi(Object activity) {
        WebDavBackend.init((Context) activity);
        if (activity instanceof Activity) {
            WebDavBackend.setCurrentManageActivity((Activity) activity);
        }
        Object controller = XposedHelpers.callMethod(activity, "getNsmController");
        setObservableBoolean(controller, "f2254x", true);
        setObservableBoolean(controller, "f2255y", false);
        setObservableBoolean(controller, "f2256z", false);
        setObservableBoolean(controller, "f2252B", false);
        Object binding = XposedHelpers.callMethod(activity, "getBinding");
        setPortTextIfNeeded(binding, "443");
        XposedHelpers.callMethod(binding, "v0", controller);
        XposedBridge.log("MyFilesWebDav: WebDAV manage UI configured");
    }

    private static void setPortTextIfNeeded(Object binding, String port) {
        try {
            Object portRow = XposedHelpers.getObjectField(binding, "f8456J");
            Object editText = XposedHelpers.getObjectField(portRow, "f8436D");
            Object current = XposedHelpers.callMethod(editText, "getText");
            String value = String.valueOf(current);
            if (isEmpty(value) || "21".equals(value) || "445".equals(value)) {
                XposedHelpers.callMethod(editText, "setText", port);
            }
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: set WebDAV default port failed");
            XposedBridge.log(t);
        }
    }

    private static void restoreWebDavAddressText(Object binding, Object pageInfo) {
        try {
            String address = null;
            String shared = null;
            Object serverInfo = XposedHelpers.getObjectField(pageInfo, "t");
            if (serverInfo != null) {
                address = (String) XposedHelpers.getObjectField(serverInfo, "f7353J");
                shared = (String) XposedHelpers.getObjectField(serverInfo, "f7363U");
            }
            if (isEmpty(address)) {
                Bundle bundle = (Bundle) XposedHelpers.getObjectField(pageInfo, "f21256p");
                address = bundle.getString(WebDavBackend.KEY_SERVER_ADDRESS);
                shared = bundle.getString(WebDavBackend.KEY_SHARED_FOLDER);
            }
            if (isEmpty(address)) {
                return;
            }
            String text = address;
            if (!isEmpty(shared)) {
                text = address.endsWith("/") ? address + shared : address + "/" + shared;
            }
            Object addressRow = XposedHelpers.getObjectField(binding, "f8448B");
            Object editText = XposedHelpers.getObjectField(addressRow, "f8436D");
            XposedHelpers.callMethod(editText, "setText", text);
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: restore edit address failed");
            XposedBridge.log(t);
        }
    }

    private static void setObservableBoolean(Object owner, String fieldName, boolean value) {
        Object observable = XposedHelpers.getObjectField(owner, fieldName);
        XposedHelpers.callMethod(observable, "P", value);
    }

    private static Object getFtpPageType(ClassLoader classLoader) {
        return getEnumField(classLoader, "f21300S");
    }

    private static Object getEnumField(ClassLoader classLoader, String fieldName) {
        Class<?> pageTypeClass = findAppClass("q8.EnumC1751i", classLoader);
        return getStaticFieldValue(pageTypeClass, fieldName);
    }

    private static boolean ensureWebDavListItem(Object dialogFragment, ClassLoader classLoader) {
        ArrayList<?> listItems =
                (ArrayList<?>) XposedHelpers.getObjectField(dialogFragment, "listItems");
        if (containsDomain(listItems, DOMAIN_WEBDAV)) {
            return true;
        }
        if (listItems == null) {
            return false;
        }

        Class<?> serverTypeClass = findAppClass(SERVER_TYPE_CLASS, classLoader);
        Object label = findFtpServerLabel(classLoader);
        if (label == null) {
            XposedBridge.log("MyFilesWebDav: FTP label enum not found");
            return false;
        }
        Object webDavItem = XposedHelpers.newInstance(serverTypeClass, DOMAIN_WEBDAV, label);

        @SuppressWarnings("unchecked")
        ArrayList<Object> writableList = (ArrayList<Object>) listItems;
        writableList.add(webDavItem);
        XposedBridge.log("MyFilesWebDav: WebDAV item ensured");
        return true;
    }

    private static void replaceDialogListAdapter(Object dialogFragment, Dialog dialog) {
        ArrayList<?> listItems =
                (ArrayList<?>) XposedHelpers.getObjectField(dialogFragment, "listItems");
        if (!containsDomain(listItems, DOMAIN_WEBDAV)) {
            return;
        }

        CharSequence[] items = labelsFromListItems(listItems);
        ArrayAdapter<CharSequence> adapter =
                new ArrayAdapter<>(dialog.getContext(), android.R.layout.simple_list_item_1, items);

        Object controller = XposedHelpers.getObjectField(dialog, "f17923q");
        XposedHelpers.setObjectField(controller, "f17917y", adapter);
        Object listView = XposedHelpers.getObjectField(controller, "f17902f");
        if (listView instanceof ListView) {
            ((ListView) listView).setAdapter((ListAdapter) adapter);
        }
        XposedBridge.log("MyFilesWebDav: dialog adapter replaced, count=" + items.length);
    }

    private static CharSequence[] labelsFromListItems(ArrayList<?> listItems) {
        if (listItems == null) {
            return new CharSequence[0];
        }
        CharSequence[] labels = new CharSequence[listItems.size()];
        for (int i = 0; i < listItems.size(); i++) {
            Object item = listItems.get(i);
            try {
                if (readDomainType(item) == DOMAIN_WEBDAV) {
                    labels[i] = DISPLAY_WEBDAV;
                } else {
                    labels[i] = readDisplayName(item);
                }
            } catch (Throwable ignored) {
                labels[i] = String.valueOf(item);
            }
        }
        return labels;
    }

    private static boolean looksLikeAddNetworkStorageItems(CharSequence[] items) {
        if (items == null || items.length < 3) {
            return false;
        }
        boolean hasFtp = false;
        boolean hasSftp = false;
        boolean hasSmb = false;
        for (CharSequence item : items) {
            if (item == null) {
                continue;
            }
            String label = item.toString().toUpperCase();
            if (label.contains(DISPLAY_WEBDAV.toUpperCase())) {
                return false;
            }
            if (label.contains("SFTP")) {
                hasSftp = true;
            } else if (label.contains("FTP")) {
                hasFtp = true;
            }
            if (label.contains("SMB")) {
                hasSmb = true;
            }
        }
        return hasFtp && hasSftp && hasSmb;
    }

    private static CharSequence[] appendWebDavLabel(CharSequence[] items) {
        CharSequence[] result = new CharSequence[items.length + 1];
        System.arraycopy(items, 0, result, 0, items.length);
        result[items.length] = DISPLAY_WEBDAV;
        return result;
    }

    private static Object findAddNetworkStorageDialog(Object listener, ClassLoader classLoader) {
        if (listener == null) {
            return null;
        }
        Class<?> dialogClass = findAppClass(DIALOG_CLASS, classLoader);
        Class<?> current = listener.getClass();
        while (current != null) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(listener);
                    if (dialogClass.isInstance(value)) {
                        return value;
                    }
                } catch (Throwable ignored) {
                    // Keep scanning synthetic listener fields.
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object findFtpServerLabel(ClassLoader classLoader) {
        try {
            Class<?> labelEnumClass = findAppClass(LABEL_ENUM_CLASS, classLoader);
            Object valuesObject = XposedHelpers.callStaticMethod(labelEnumClass, "values");
            if (!(valuesObject instanceof Object[])) {
                return null;
            }

            Object fallback = null;
            Object[] values = (Object[]) valuesObject;
            for (Object value : values) {
                if (value == null) {
                    continue;
                }
                if (fallback == null) {
                    fallback = value;
                }
                String text = objectStringPayload(value).toLowerCase();
                if (text.contains("ftp_server")) {
                    return value;
                }
            }
            return fallback;
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: find FTP enum by reflection failed");
            XposedBridge.log(t);
            return null;
        }
    }

    private static Object findBuilderParams(Object builder) {
        if (builder == null) {
            return null;
        }
        for (Field field : allFields(builder.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(builder);
                if (findCharSequenceArray(value) != null) {
                    return value;
                }
            } catch (Throwable ignored) {
                // Keep scanning builder fields.
            }
        }
        return null;
    }

    private static CharSequence[] findCharSequenceArray(Object owner) {
        if (owner == null) {
            return null;
        }
        for (Field field : allFields(owner.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof CharSequence[]) {
                    return (CharSequence[]) value;
                }
            } catch (Throwable ignored) {
                // Keep scanning params fields.
            }
        }
        return null;
    }

    private static void setCharSequenceArray(Object owner, CharSequence[] items) {
        if (owner == null) {
            return;
        }
        for (Field field : allFields(owner.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof CharSequence[]) {
                    field.set(owner, items);
                    return;
                }
            } catch (Throwable ignored) {
                // Keep scanning params fields.
            }
        }
    }

    private static Object findDialogListener(Object params, ClassLoader classLoader) {
        if (params == null) {
            return null;
        }
        for (Field field : allFields(params.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(params);
                if (findAddNetworkStorageDialog(value, classLoader) != null) {
                    return value;
                }
            } catch (Throwable ignored) {
                // Keep scanning params fields.
            }
        }
        return null;
    }

    private static Bundle findBundleField(Object owner) {
        if (owner == null) {
            return null;
        }
        for (Field field : allFields(owner.getClass())) {
            try {
                if (!Bundle.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof Bundle) {
                    return (Bundle) value;
                }
            } catch (Throwable ignored) {
                // Keep scanning obfuscated holder fields.
            }
        }
        return null;
    }

    private static Context findContextField(Object owner) {
        if (owner == null) {
            return null;
        }
        for (Field field : allFields(owner.getClass())) {
            try {
                if (!Context.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof Context) {
                    return (Context) value;
                }
            } catch (Throwable ignored) {
                // Keep scanning repository fields.
            }
        }
        return null;
    }

    private static Object findStaticIterableField(Class<?> ownerClass) {
        if (ownerClass == null) {
            return null;
        }
        for (Field field : allFields(ownerClass)) {
            try {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof Iterable) {
                    return value;
                }
            } catch (Throwable ignored) {
                // Keep scanning static fields.
            }
        }
        return null;
    }

    private static int readDomainType(Object item) {
        if (item == null) {
            return Integer.MIN_VALUE;
        }

        String[] knownFields = {"mDomainType", "domainType", "f7491B"};
        for (String fieldName : knownFields) {
            int value = readIntFieldIfPresent(item, fieldName);
            if (value != Integer.MIN_VALUE) {
                return value;
            }
        }

        try {
            Object value = XposedHelpers.callMethod(item, "b0");
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
            // Fall through to runtime-field scan.
        }

        Integer fallback = null;
        for (Field field : allFields(item.getClass())) {
            try {
                if (field.getType() == int.class || field.getType() == Integer.class) {
                    field.setAccessible(true);
                    Object value = field.get(item);
                    if (value instanceof Integer) {
                        int intValue = (Integer) value;
                        if (intValue == DOMAIN_WEBDAV || isNetworkDomainValue(intValue)) {
                            return intValue;
                        }
                        if (fallback == null) {
                            fallback = intValue;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Keep scanning fields.
            }
        }
        return fallback != null ? fallback : Integer.MIN_VALUE;
    }

    private static CharSequence readDisplayName(Object item) {
        if (item == null) {
            return "";
        }
        try {
            Object value = XposedHelpers.getObjectField(item, "mDisplayName");
            if (value instanceof CharSequence) {
                return (CharSequence) value;
            }
        } catch (Throwable ignored) {
            // Fall through to runtime-field scan.
        }

        for (Field field : allFields(item.getClass())) {
            try {
                if (CharSequence.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(item);
                    if (value instanceof CharSequence) {
                        return (CharSequence) value;
                    }
                }
            } catch (Throwable ignored) {
                // Keep scanning fields.
            }
        }
        return String.valueOf(item);
    }

    private static String objectStringPayload(Object object) {
        StringBuilder builder = new StringBuilder(String.valueOf(object));
        for (Field field : allFields(object.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(object);
                if (value instanceof CharSequence) {
                    builder.append('|').append(value);
                }
            } catch (Throwable ignored) {
                // Keep scanning enum fields.
            }
        }
        return builder.toString();
    }

    private static boolean tryCallMethod(Object object, String methodName, Object... args) {
        try {
            XposedHelpers.callMethod(object, methodName, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String firstString(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return "";
        }
        return String.valueOf(args[0]);
    }

    private static String readPathSafe(Object item) {
        if (item == null) {
            return "";
        }
        try {
            Object value = XposedHelpers.callMethod(item, "getPath");
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Throwable ignored) {
            // Fall through to field scan.
        }
        try {
            Object value = getFieldValue(item, "f7499k");
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Throwable ignored) {
            // No path-like field found by its known runtime name.
        }
        return "";
    }

    private static void registerWebDavStoragePath(ClassLoader classLoader) {
        try {
            Object value = getStaticFieldValue(findAppClass("w8.G", classLoader), "f23454e");
            if (value instanceof SparseArray) {
                SparseArray paths = (SparseArray) value;
                Object old = paths.get(DOMAIN_WEBDAV);
                if (!WEBDAV_ROOT_PATH.equals(old)) {
                    paths.put(DOMAIN_WEBDAV, WEBDAV_ROOT_PATH);
                }
                DiagnosticLogger.log("storage path registered domain=" + DOMAIN_WEBDAV
                        + ", path=" + WEBDAV_ROOT_PATH);
            }
        } catch (Throwable t) {
            DiagnosticLogger.log("storage path register failed");
            DiagnosticLogger.log(t);
        }
    }

    private static boolean isWebDavStoragePath(String path) {
        String normalized = trimTrailingPathSlash(path);
        return WEBDAV_ROOT_PATH.equals(normalized)
                || (normalized != null && normalized.startsWith(WEBDAV_ROOT_PATH + "/"));
    }

    private static int webDavPageDepth(String path) {
        String normalized = trimTrailingPathSlash(path);
        if (normalized == null || WEBDAV_ROOT_PATH.equals(normalized)) {
            return 0;
        }
        if (!normalized.startsWith(WEBDAV_ROOT_PATH + "/")) {
            return -1;
        }
        String rest = normalized.substring(WEBDAV_ROOT_PATH.length() + 1);
        if (isEmpty(rest)) {
            return 0;
        }
        int depth = 1;
        for (int i = 0; i < rest.length(); i++) {
            if (rest.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    private static long parseWebDavServerId(String path) {
        if (isEmpty(path)) {
            return Long.MIN_VALUE;
        }
        String marker = "/WebDAV/";
        int markerIndex = path.indexOf(marker);
        if (markerIndex < 0) {
            return Long.MIN_VALUE;
        }
        int start = markerIndex + marker.length();
        int end = start;
        while (end < path.length() && Character.isDigit(path.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(path.substring(start, end));
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static void setDomainTypeByMethodOrProbe(Object object, int domainType) {
        if (tryCallMethod(object, "setDomainType", domainType)
                && readDomainTypeSafe(object) == domainType) {
            return;
        }
        setIntFieldReturnedByMethod(object, "b0", domainType);
    }

    private static void setServerIdByProbe(Object object, long serverId) {
        if (setLongFieldReturnedByMethod(object, "b", serverId)) {
            return;
        }
        setDeclaredFieldByTypeIndex(object, long.class, 0, serverId);
    }

    private static boolean setLongFieldReturnedByMethod(Object object, String methodName, long expectedValue) {
        try {
            Object result = XposedHelpers.callMethod(object, methodName);
            if (result instanceof Number && ((Number) result).longValue() == expectedValue) {
                return true;
            }
        } catch (Throwable ignored) {
            // Probe fields below.
        }
        for (Field field : allFields(object.getClass())) {
            if (!matchesFieldType(field, long.class)) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object oldValue = field.get(object);
                field.set(object, expectedValue);
                Object result = XposedHelpers.callMethod(object, methodName);
                if (result instanceof Number && ((Number) result).longValue() == expectedValue) {
                    return true;
                }
                field.set(object, oldValue);
            } catch (Throwable ignored) {
                // Keep probing matching long fields.
            }
        }
        return false;
    }

    private static boolean setIntFieldReturnedByMethod(Object object, String methodName, int expectedValue) {
        try {
            Object result = XposedHelpers.callMethod(object, methodName);
            if (result instanceof Number && ((Number) result).intValue() == expectedValue) {
                return true;
            }
        } catch (Throwable ignored) {
            // Probe fields below.
        }
        for (Field field : allFields(object.getClass())) {
            if (!matchesFieldType(field, int.class)) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object oldValue = field.get(object);
                field.set(object, expectedValue);
                Object result = XposedHelpers.callMethod(object, methodName);
                if (result instanceof Number && ((Number) result).intValue() == expectedValue) {
                    return true;
                }
                field.set(object, oldValue);
            } catch (Throwable ignored) {
                // Keep probing matching int fields.
            }
        }
        return false;
    }

    private static boolean setStringFieldReturnedByMethod(Object object, String methodName, String expectedValue) {
        try {
            Object result = XposedHelpers.callMethod(object, methodName);
            if (expectedValue.equals(result)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Probe fields below.
        }
        for (Field field : allFields(object.getClass())) {
            if (!matchesFieldType(field, String.class)) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object oldValue = field.get(object);
                field.set(object, expectedValue);
                Object result = XposedHelpers.callMethod(object, methodName);
                if (expectedValue.equals(result)) {
                    return true;
                }
                field.set(object, oldValue);
            } catch (Throwable ignored) {
                // Keep probing matching string fields.
            }
        }
        return false;
    }

    private static boolean setDeclaredFieldByTypeIndex(
            Object object,
            Class<?> fieldType,
            int typeIndex,
            Object value
    ) {
        int index = 0;
        Field[] fields = object.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (!matchesFieldType(field, fieldType)) {
                continue;
            }
            if (index == typeIndex) {
                try {
                    field.setAccessible(true);
                    field.set(object, value);
                    return true;
                } catch (Throwable t) {
                    DiagnosticLogger.log("layout field set failed: class="
                            + object.getClass().getName()
                            + ", type=" + fieldType.getName()
                            + ", index=" + typeIndex
                            + ", field=" + field.getName());
                    DiagnosticLogger.log(t);
                    return false;
                }
            }
            index++;
        }
        DiagnosticLogger.log("layout field not found: class="
                + object.getClass().getName()
                + ", type=" + fieldType.getName()
                + ", index=" + typeIndex);
        return false;
    }

    private static boolean matchesFieldType(Field field, Class<?> expectedType) {
        if (Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        Class<?> actualType = field.getType();
        if (actualType == expectedType) {
            return true;
        }
        if (expectedType == int.class) {
            return actualType == Integer.class;
        }
        if (expectedType == long.class) {
            return actualType == Long.class;
        }
        if (expectedType == boolean.class) {
            return actualType == Boolean.class;
        }
        return false;
    }

    private static void setFieldValue(Object object, String fieldName, Object value) {
        try {
            Field field = findFieldByName(object.getClass(), fieldName);
            if (field == null) {
                throw new NoSuchFieldException(object.getClass().getName() + "#" + fieldName);
            }
            field.setAccessible(true);
            field.set(object, value);
        } catch (Throwable t) {
            throw new IllegalStateException("Set field failed: " + fieldName, t);
        }
    }

    private static boolean setFieldValueIfPresent(Object object, String fieldName, Object value) {
        try {
            Field field = findFieldByName(object.getClass(), fieldName);
            if (field == null) {
                return false;
            }
            field.setAccessible(true);
            field.set(object, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getFieldValue(Object object, String fieldName) throws Throwable {
        Field field = findFieldByName(object.getClass(), fieldName);
        if (field == null) {
            throw new NoSuchFieldException(object.getClass().getName() + "#" + fieldName);
        }
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object getFieldValueOrNull(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            return getFieldValue(object, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findFieldByName(Class<?> type, String fieldName) {
        Field exact = findFieldByExactName(type, fieldName);
        if (exact != null) {
            return exact;
        }
        String alias = decompiledFieldAlias(fieldName);
        if (alias != null) {
            return findFieldByExactName(type, alias);
        }
        return null;
    }

    private static Field findFieldByExactName(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String decompiledFieldAlias(String fieldName) {
        if (fieldName == null || fieldName.length() < 3 || fieldName.charAt(0) != 'f') {
            return null;
        }
        int index = 1;
        while (index < fieldName.length() && Character.isDigit(fieldName.charAt(index))) {
            index++;
        }
        if (index <= 1 || index >= fieldName.length()) {
            return null;
        }
        return fieldName.substring(index);
    }

    private static Object getStaticFieldValue(Class<?> type, String fieldName) {
        try {
            Field field = findFieldByName(type, fieldName);
            if (field == null) {
                throw new NoSuchFieldException(type.getName() + "#" + fieldName);
            }
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable t) {
            throw new IllegalStateException("Get static field failed: " + fieldName, t);
        }
    }

    private static int readIntFieldIfPresent(Object item, String fieldName) {
        try {
            Object value = getFieldValue(item, fieldName);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
            // Try the next known field.
        }
        return Integer.MIN_VALUE;
    }

    private static ArrayList<Field> allFields(Class<?> type) {
        ArrayList<Field> result = new ArrayList<>();
        Class<?> current = type;
        while (current != null) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                result.add(field);
            }
            current = current.getSuperclass();
        }
        return result;
    }

    private static boolean containsDomain(ArrayList<?> listItems, int domainType) {
        if (listItems == null) {
            return false;
        }

        for (Object item : listItems) {
            if (item == null) {
                continue;
            }

            try {
                if (readDomainType(item) == domainType) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Keep scanning; this is only a duplicate guard.
            }
        }
        return false;
    }

    private static boolean containsSameServer(List<?> items, Object candidate) {
        if (items == null || candidate == null) {
            return false;
        }
        int candidateDomain = readDomainTypeSafe(candidate);
        long candidateId = readServerIdSafe(candidate);
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            if (readDomainTypeSafe(item) == candidateDomain
                    && candidateDomain == DOMAIN_WEBDAV
                    && readServerIdSafe(item) == candidateId) {
                return true;
            }
        }
        return false;
    }

    private static int readDomainTypeSafe(Object item) {
        try {
            return readDomainType(item);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static long readServerIdSafe(Object item) {
        try {
            Object value = getFieldValue(item, "f7351H");
            if (value instanceof Long) {
                return (Long) value;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Throwable ignored) {
            // Fall through to file-item server id.
        }
        try {
            Object value = getFieldValue(item, "f7350H");
            if (value instanceof Long) {
                return (Long) value;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Throwable ignored) {
            // No server id on this object.
        }
        try {
            Object value = XposedHelpers.callMethod(item, "b");
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Throwable ignored) {
            // Fall through to long-field scan.
        }
        for (Field field : allFields(item.getClass())) {
            if (!matchesFieldType(field, long.class)) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(item);
                if (value instanceof Number) {
                    long longValue = ((Number) value).longValue();
                    if (longValue > 0L) {
                        return longValue;
                    }
                }
            } catch (Throwable ignored) {
                // Keep scanning long fields.
            }
        }
        return Long.MIN_VALUE;
    }

    private static boolean isWebDav(Object value) {
        return value instanceof Integer && ((Integer) value) == DOMAIN_WEBDAV;
    }

    private static boolean isNetworkDomainValue(int value) {
        return value >= 200 && value <= 299;
    }

    private static boolean isTargetPackage(String packageName) {
        return TARGET_PACKAGE.equals(packageName)
                || TARGET_PACKAGE_ALT.equals(packageName)
                || TARGET_PACKAGE_NSM.equals(packageName)
                || (packageName != null && packageName.contains(".myfiles"));
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
