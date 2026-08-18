package com.samsung.feature.extension;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.util.SparseArray;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

    private static final String MODULE_VERSION = "1.3.17-webdav-15.4.09.5";
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

    private static Class<?> findExactAppClass(String className, ClassLoader classLoader) throws ClassNotFoundException {
        return Class.forName(className, false, classLoader);
    }

    private static String[] classNameAliases(String className) {
        if ("w8.G".equals(className)) {
            // My Files 15.4 moved StoragePathUtils from w8.G to ha.P.  ha.O is
            // only the storage-path constants interface in that build, so using
            // it here silently leaves every derived WebDAV path as UNKNOWN.
            return new String[]{className, "ha.P"};
        }
        if ("w8.AbstractC2015g".equals(className)) {
            return new String[]{className, "w8.g"};
        }
        if ("U7.G".equals(className)) {
            return new String[]{className, "D9.L"};
        }
        if ("U7.AbstractC0263g".equals(className)) {
            return new String[]{className, "U7.g", "D9.AbstractC0165g", "D9.g"};
        }
        if ("D9.AbstractC0165g".equals(className)) {
            return new String[]{className, "D9.g"};
        }
        if ("Y5.j".equals(className)) {
            return new String[]{className, "u7.j", "u7.AbstractC2492j"};
        }
        if ("Y5.h".equals(className)) {
            return new String[]{className, "A0.d"};
        }
        if ("Y5.g".equals(className)) {
            return new String[]{className, "u7.InterfaceC2490h"};
        }
        if ("V5.E".equals(className)) {
            return new String[]{className, "r7.H"};
        }
        if ("V5.F".equals(className)) {
            return new String[]{className, "r7.I"};
        }
        if ("y8.f".equals(className)) {
            return new String[]{className, "ka.f"};
        }
        if ("y8.g".equals(className)) {
            return new String[]{className, "ka.g"};
        }
        if ("J6.c".equals(className)) {
            return new String[]{className, "A8.b"};
        }
        if ("w6.H".equals(className)) {
            return new String[]{className, "T7.N"};
        }
        if ("w6.I".equals(className)) {
            return new String[]{className, "T7.O"};
        }
        if ("dc.g".equals(className)) {
            return new String[]{className, "sc.AbstractC2295H", "sc.H"};
        }
        if ("sc.AbstractC2295H".equals(className)) {
            return new String[]{className, "sc.H"};
        }
        if ("ha.AbstractC1537i".equals(className)) {
            return new String[]{className, "ha.i"};
        }
        if ("F1.m".equals(className)) {
            return new String[]{className, "K7.a"};
        }
        if ("J6.h".equals(className)) {
            return new String[]{className, "h8.d", "h8.C1521d"};
        }
        if ("e6.u".equals(className)) {
            return new String[]{className, "A7.w"};
        }
        if ("S5.j".equals(className)) {
            return new String[]{className, "o7.i"};
        }
        if ("S5.h".equals(className)) {
            return new String[]{className, "o7.g"};
        }
        if ("X5.T0".equals(className)) {
            return new String[]{className, "t7.AbstractC2401g1"};
        }
        if ("a.AbstractC0577a".equals(className)) {
            return new String[]{className, "a.a", "A.AbstractC0577a"};
        }
        if ("q8.C1747e".equals(className)) {
            return new String[]{className, "q8.e", "aa.e", "aa.C0617e"};
        }
        if ("q8.EnumC1751i".equals(className)) {
            return new String[]{className, "q8.i", "aa.i"};
        }
        if ("x7.C2094a".equals(className)) {
            return new String[]{className, "x7.a"};
        }
        if ("e6.AbstractC1145d".equals(className)) {
            return new String[]{className, "e6.d", "E6.AbstractC1145d", "E6.d", "A7.AbstractC0044d"};
        }
        if ("p8.AbstractC1705c".equals(className)) {
            return new String[]{className, "p8.c", "Z9.c"};
        }
        if (LABEL_ENUM_CLASS.equals(className)) {
            return new String[]{"ha.E", "ha.EnumC1527E", "Ha.E", "Ha.EnumC1527E", className};
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

    private static XC_MethodHook.Unhook hookExactAppMethod(
            String className,
            ClassLoader classLoader,
            String methodName,
            Object... parameterTypesAndCallback
    ) {
        return XposedHelpers.findAndHookMethod(
                className,
                classLoader,
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
        installOptionalHook("AddNetworkStorageDialog.initListItem", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        DIALOG_CLASS,
                        classLoader,
                        "initListItem",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    if (ensureWebDavListItem(param.thisObject, classLoader)) {
                                        DiagnosticLogger.log("add dialog initListItem ensured WebDAV");
                                    }
                                } catch (Throwable t) {
                                    DiagnosticLogger.log("append WebDAV item failed");
                                    DiagnosticLogger.log(t);
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("AddNetworkStorageDialog.getItems", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        DIALOG_CLASS,
                        classLoader,
                        "getItems",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    ensureWebDavListItem(param.thisObject, classLoader);
                                    ArrayList<?> listItems =
                                            (ArrayList<?>) XposedHelpers.getObjectField(param.thisObject, "listItems");
                                    if (containsDomain(listItems, DOMAIN_WEBDAV)) {
                                        CharSequence[] labels = labelsFromListItems(listItems);
                                        param.setResult(labels);
                                        DiagnosticLogger.log("add dialog getItems replaced count=" + labels.length);
                                    }
                                } catch (Throwable t) {
                                    DiagnosticLogger.log("rename WebDAV item failed");
                                    DiagnosticLogger.log(t);
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("AddNetworkStorageDialog.createDialog", new HookInstaller() {
            @Override
            public void install() {
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
                                    DiagnosticLogger.log("createDialog WebDAV fallback failed");
                                    DiagnosticLogger.log(t);
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("AddNetworkStorageDialog.staticGetDialog", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        DIALOG_CLASS,
                        classLoader,
                        "getDialog",
                        findAppClass("q8.C1747e", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                ensureDialogResultHasWebDav(param.getResult(), classLoader, "staticGetDialog");
                            }
                        }
                );
            }
        });

        installOptionalHook("AddNetworkStorageDialog.companionGetDialog", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        DIALOG_CLASS + "$Companion",
                        classLoader,
                        "getDialog",
                        findAppClass("q8.C1747e", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                ensureDialogResultHasWebDav(param.getResult(), classLoader, "companionGetDialog");
                            }
                        }
                );
            }
        });

        installOptionalHook("AddNetworkStorageDialog.dialogManager", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "com.sec.android.app.myfiles.ui.dialog.DialogManager",
                        classLoader,
                        "getAddNetworkStorageServerDialog",
                        findAppClass("q8.C1747e", classLoader),
                        findAppClass("R8.a", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                ensureDialogResultHasWebDav(param.getResult(), classLoader, "dialogManager");
                            }
                        }
                );
            }
        });
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
        installOptionalHook("DomainHelpers.networkStorage.old", new HookInstaller() {
            @Override
            public void install() {
                hookIntBoolean("w8.AbstractC2015g", classLoader, "j0", true);
            }
        });
        installOptionalHook("DomainHelpers.networkStorage.new", new HookInstaller() {
            @Override
            public void install() {
                hookIntBoolean("ha.AbstractC1537i", classLoader, "l0", true);
            }
        });
        installOptionalHook("DomainHelpers.domainNetwork.old", new HookInstaller() {
            @Override
            public void install() {
                hookIntBoolean("M5.h", classLoader, "l", true);
            }
        });
        installOptionalHook("DomainHelpers.domainCloudNetwork.old", new HookInstaller() {
            @Override
            public void install() {
                hookIntBoolean("M5.h", classLoader, "q", true);
            }
        });
        installOptionalHook("DomainHelpers.domainNetwork.new", new HookInstaller() {
            @Override
            public void install() {
                hookIntBoolean("j7.e", classLoader, "i", true);
            }
        });
        installOptionalHook("DomainHelpers.domainCloudNetwork.new", new HookInstaller() {
            @Override
            public void install() {
                hookIntBoolean("j7.e", classLoader, "o", true);
            }
        });

        installOptionalHook("NetworkStorageUtils.name.old", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStorageName(classLoader, "w8.AbstractC2015g", "G");
            }
        });
        installOptionalHook("NetworkStorageUtils.name.new", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStorageName(classLoader, "ha.AbstractC1537i", "I");
            }
        });
        installOptionalHook("NetworkStorageUtils.page.old", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStoragePageType(classLoader, "w8.AbstractC2015g", "E");
            }
        });
        installOptionalHook("NetworkStorageUtils.page.new", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStoragePageType(classLoader, "ha.AbstractC1537i", "G");
            }
        });
        installOptionalHook("NetworkStorageUtils.pageVariant.old", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStoragePageVariant(classLoader, "w8.AbstractC2015g", "F");
            }
        });
        installOptionalHook("NetworkStorageUtils.pageVariant.new", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStoragePageVariant(classLoader, "ha.AbstractC1537i", "H");
            }
        });
        installOptionalHook("MediaFileIcon.old", new HookInstaller() {
            @Override
            public void install() {
                hookStorageIcon(classLoader, "U7.G", "n", "n", 202);
            }
        });
        installOptionalHook("MediaFileIcon.new", new HookInstaller() {
            @Override
            public void install() {
                hookStorageIcon(classLoader, "D9.L", "o", "o", 202);
            }
        });
        installOptionalHook("PageTypeMapper.old.b", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStoragePageType(classLoader, "U7.AbstractC0263g", "b");
            }
        });
        installOptionalHook("PageTypeMapper.old.c", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStorageConstantInt("U7.AbstractC0263g", classLoader, "c", 11);
            }
        });
        installOptionalHook("PageTypeMapper.new.b", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStoragePageType(classLoader, "D9.AbstractC0165g", "b");
            }
        });
        installOptionalHook("PageTypeMapper.new.c", new HookInstaller() {
            @Override
            public void install() {
                hookNetworkStorageConstantInt("D9.AbstractC0165g", classLoader, "c", 11);
            }
        });
    }

    private static void hookNetworkStorageName(
            final ClassLoader classLoader,
            String className,
            String methodName
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
                            param.setResult(DISPLAY_WEBDAV);
                        }
                    }
                }
        );
    }

    private static void hookNetworkStoragePageType(
            final ClassLoader classLoader,
            String className,
            String methodName
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
                            param.setResult(getFtpPageType(classLoader));
                        }
                    }
                }
        );
    }

    private static void hookNetworkStoragePageVariant(
            final ClassLoader classLoader,
            String className,
            String methodName
    ) {
        hookAppMethod(
                className,
                classLoader,
                methodName,
                int.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isWebDav(param.args[0])) {
                            param.setResult(getNetworkStoragePageVariant(
                                    classLoader,
                                    Boolean.TRUE.equals(param.args[1])
                            ));
                        }
                    }
                }
        );
    }

    private static void hookStorageIcon(
            final ClassLoader classLoader,
            String className,
            String methodName,
            final String fallbackMethodName,
            final int fallbackDomain
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
                            param.setResult(XposedHelpers.callStaticMethod(
                                    findAppClass(className, classLoader),
                                    fallbackMethodName,
                                    fallbackDomain
                            ));
                        }
                    }
                }
        );
    }

    private static void hookNetworkStorageConstantInt(
            String className,
            ClassLoader classLoader,
            String methodName,
            final int value
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

        installOptionalHook("StoragePathUtils.E.new", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "ha.P",
                        classLoader,
                        "E",
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

        installOptionalHook("NetworkStorageUtils.m.new", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "ha.AbstractC1537i",
                        classLoader,
                        "m",
                        long.class,
                        int.class,
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (!isWebDav(param.args[1])) {
                                    return;
                                }
                                long serverId = ((Number) param.args[0]).longValue();
                                String path = (String) param.args[2];
                                param.setResult(detachWebDavServerPath(path, serverId));
                            }
                        }
                );
            }
        });
    }

    private static void hookFileInfoFactory(final ClassLoader classLoader) {
        installOptionalHook("FileInfoFactory.old", new HookInstaller() {
            @Override
            public void install() {
                hookFileInfoFactoryMethod(classLoader, "Y5.j", "Y5.h");
            }
        });
        installOptionalHook("FileInfoFactory.new", new HookInstaller() {
            @Override
            public void install() {
                hookFileInfoFactoryMethod(classLoader, "u7.j", "A0.d");
            }
        });
        installOptionalHook("FileInfoFactory.jadxAlias", new HookInstaller() {
            @Override
            public void install() {
                hookFileInfoFactoryMethod(classLoader, "u7.AbstractC2492j", "A0.d");
            }
        });
    }

    private static void hookFileInfoFactoryMethod(
            final ClassLoader classLoader,
            String factoryClass,
            String argsPatternClass
    ) {
        hookExactAppMethod(
                factoryClass,
                classLoader,
                "b",
                int.class,
                boolean.class,
                findAppClass(argsPatternClass, classLoader),
                createFileInfoFactoryHook(classLoader)
        );
    }

    private static XC_MethodHook createFileInfoFactoryHook(final ClassLoader classLoader) {
        return new XC_MethodHook() {
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
        };
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
                    Object server = newServerInfo(classLoader);
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
        String path = fullPath == null ? "" : fullPath;
        Object info = newFileInfo(classLoader, path);
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);
        setFilePath(info, path);
        setFileUniqueId(info, path);
        String name = WebDavClient.nameOf(path);
        if (!isEmpty(name)) {
            setFileName(info, name);
        }
        long serverId = parseWebDavServerId(fullPath);
        if (serverId != Long.MIN_VALUE) {
            setServerIdByProbe(info, serverId);
        }
        setFileDirectory(info, !isFile);
        if (!isFile) {
            setFileType(info, 12289);
        }
        return info;
    }

    private static Object buildEmptyWebDavFileInfo(ClassLoader classLoader, boolean isDirectory) {
        Object info = newFileInfo(classLoader, "");
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);
        setFileDirectory(info, isDirectory);
        if (isDirectory) {
            setFileType(info, 12289);
        }
        return info;
    }

    private static Object newFileInfo(ClassLoader classLoader, String fullPath) {
        return XposedHelpers.newInstance(
                findAppClass("V5.E", classLoader),
                fullPath == null ? "" : fullPath
        );
    }

    private static Object newServerInfo(ClassLoader classLoader) {
        return XposedHelpers.newInstance(findAppClass("V5.F", classLoader), DOMAIN_WEBDAV);
    }

    private static void setFilePath(Object info, String path) {
        if (tryCallMethod(info, "f", path) || tryCallMethod(info, "g", path)) {
            return;
        }
        setStringFieldReturnedByMethod(info, "j", path);
    }

    private static void setFileUniqueId(Object info, String uniqueId) {
        if (tryCallMethod(info, "w0", uniqueId) || tryCallMethod(info, "x0", uniqueId)) {
            return;
        }
        setStringFieldReturnedByMethod(info, "getUniqueId", uniqueId);
    }

    private static void setFileName(Object info, String name) {
        if (tryCallMethod(info, "k0", name) || tryCallMethod(info, "p0", name)) {
            return;
        }
        setStringFieldReturnedByMethod(info, "getName", name);
    }

    private static void setFileDirectory(Object info, boolean isDirectory) {
        if (tryCallMethod(info, "C0", isDirectory) || tryCallMethod(info, "E0", isDirectory)) {
            return;
        }
        setFileType(info, isDirectory ? 12289 : 0);
    }

    private static void setFileMime(Object info, String mimeType) {
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        if (tryCallMethod(info, "m0", mimeType) || tryCallMethod(info, "q0", mimeType)) {
            return;
        }
        setStringFieldReturnedByMethod(info, "O0", mimeType);
    }

    private static void setFileType(Object info, int type) {
        tryCallMethod(info, "H", type);
    }

    private static int resolveFileType(ClassLoader classLoader, String fullPath) {
        try {
            Object mime = tryCallStaticMethod("D9.L", classLoader, "n", fullPath);
            if (mime instanceof String) {
                Object type = tryCallStaticMethod("D9.L", classLoader, "h", fullPath, mime);
                if (type instanceof Number) {
                    return ((Number) type).intValue();
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the old media helper.
        }
        try {
            Class<?> fileTypeClass = findAppClass("U7.G", classLoader);
            Object mime = XposedHelpers.callStaticMethod(fileTypeClass, "m", fullPath);
            if (mime instanceof String) {
                Object type = XposedHelpers.callStaticMethod(fileTypeClass, "g", fullPath, mime);
                if (type instanceof Number) {
                    return ((Number) type).intValue();
                }
            }
        } catch (Throwable ignored) {
            // Unknown file types can safely fall back to the generic icon.
        }
        return 0;
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
            setFileType(result, 12289);
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
        installOptionalHook("RequestWrapper.legacy.serverCount", new HookInstaller() {
            @Override
            public void install() throws Throwable {
                hookExactAppMethod(
                        "y8.f",
                        classLoader,
                        "e",
                        long.class,
                        int.class,
                        int.class,
                        Bundle.class,
                        findExactAppClass("J6.c", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                mergeWebDavServerCount(param);
                            }
                        }
                );
            }
        });

        installOptionalHook("RequestWrapper.legacy.async", new HookInstaller() {
            @Override
            public void install() throws Throwable {
                hookRequestWrapperAsync(classLoader, "y8.f", findExactAppClass("y8.g", classLoader),
                        "legacy");
            }
        });

        installOptionalHook("RequestWrapper.legacy.sync", new HookInstaller() {
            @Override
            public void install() throws Throwable {
                hookRequestWrapperSync(classLoader, "y8.f", findExactAppClass("J6.c", classLoader),
                        "legacy");
            }
        });

        installOptionalHook("RequestWrapper.new.serverCount", new HookInstaller() {
            @Override
            public void install() throws Throwable {
                hookExactAppMethod(
                        "ka.f",
                        classLoader,
                        "e",
                        long.class,
                        int.class,
                        int.class,
                        Bundle.class,
                        findExactAppClass("A8.b", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                mergeWebDavServerCount(param);
                            }
                        }
                );
            }
        });

        installOptionalHook("RequestWrapper.new.async", new HookInstaller() {
            @Override
            public void install() throws Throwable {
                hookRequestWrapperAsync(classLoader, "ka.f", findExactAppClass("ka.g", classLoader),
                        "new");
            }
        });

        installOptionalHook("RequestWrapper.new.sync", new HookInstaller() {
            @Override
            public void install() throws Throwable {
                hookRequestWrapperSync(classLoader, "ka.f", findExactAppClass("A8.b", classLoader),
                        "new");
            }
        });
    }

    private static void hookRequestWrapperAsync(
            ClassLoader classLoader,
            String wrapperClassName,
            Class<?> callbackClass,
            final String source
    ) {
        hookExactAppMethod(
                wrapperClassName,
                classLoader,
                "a",
                int.class,
                int.class,
                Bundle.class,
                callbackClass,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Bundle args = (Bundle) param.args[2];
                        int reqCode = (Integer) param.args[1];
                        if (reqCode == 15) {
                            return;
                        }
                        if (!isWebDavRequest(param.args[0], args)) {
                            logRequestWrapperMiss(source, "async", param.args[0], reqCode, args);
                            return;
                        }
                        args = normalizeWebDavRequestArgs(args);
                        Object callback = param.args[3];
                        DiagnosticLogger.log("request wrapper " + source + " async WebDAV domain="
                                + param.args[0] + ", reqCode=" + reqCode
                                + ", args=" + describeRequestBundle(args));
                        long requestId = WebDavBackend.handleAsync(reqCode, args, callback);
                        param.setResult(requestId);
                    }
                }
        );
    }

    private static void hookRequestWrapperSync(
            ClassLoader classLoader,
            String wrapperClassName,
            Class<?> progressCallbackClass,
            final String source
    ) {
        hookExactAppMethod(
                wrapperClassName,
                classLoader,
                "e",
                long.class,
                int.class,
                int.class,
                Bundle.class,
                progressCallbackClass,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Bundle args = (Bundle) param.args[3];
                        int reqCode = (Integer) param.args[2];
                        if (reqCode == 15) {
                            return;
                        }
                        if (!isWebDavRequest(param.args[1], args)) {
                            logRequestWrapperMiss(source, "sync", param.args[1], reqCode, args);
                            return;
                        }
                        args = normalizeWebDavRequestArgs(args);
                        DiagnosticLogger.log("request wrapper " + source + " sync WebDAV domain="
                                + param.args[1] + ", reqCode=" + reqCode
                                + ", args=" + describeRequestBundle(args));
                        param.setResult(WebDavBackend.handleSync(reqCode, args, param.args[4]));
                    }
                }
        );
    }

    private static void logRequestWrapperMiss(
            String source,
            String mode,
            Object domainValue,
            int reqCode,
            Bundle args
    ) {
        if (!shouldTraceRequestWrapperMiss(domainValue, reqCode, args)) {
            return;
        }
        DiagnosticLogger.log("request wrapper " + source + " " + mode
                + " pass-through domain=" + domainValue
                + ", reqCode=" + reqCode
                + ", serverId=" + readBundleServerId(args)
                + ", args=" + describeRequestBundle(args));
    }

    private static boolean shouldTraceRequestWrapperMiss(Object domainValue, int reqCode, Bundle args) {
        if (isWebDav(domainValue) || isWebDavOperationRequestCode(reqCode)) {
            return true;
        }
        return readBundleServerId(args) > 0;
    }

    private static boolean isWebDavOperationRequestCode(int reqCode) {
        return reqCode == 121
                || reqCode == 122
                || reqCode == 123
                || reqCode == 124
                || reqCode == 125
                || reqCode == 126
                || reqCode == 127
                || reqCode == 130;
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

        installOptionalHook("OperationFactory.map.new", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "K7.a",
                        classLoader,
                        "a",
                        findAppClass("A7.w", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object result = param.getResult();
                                if (result instanceof SparseArray) {
                                    ((SparseArray) result).put(DOMAIN_WEBDAV,
                                            newWebDavNetworkOperation(classLoader, param.args[0]));
                                    DiagnosticLogger.log("operation map added WebDAV -> h8.C1521d");
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

        installOptionalHook("OperationFactory.direct.new", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "Z9.c",
                        classLoader,
                        "a",
                        Context.class,
                        int.class,
                        findAppClass("A7.w", classLoader),
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

        installOptionalHook("AbsFileOperator.k.new", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "A7.AbstractC0044d",
                        classLoader,
                        "k",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (isWebDav(param.args[0])) {
                                    param.setResult(newWebDavNetworkOperation(classLoader, param.thisObject));
                                    DiagnosticLogger.log("operation route WebDAV -> h8.C1521d");
                                }
                            }
                        }
                );
            }
        });
    }

    private static Object newWebDavNetworkOperation(ClassLoader classLoader, Object collector) {
        Object operation = XposedHelpers.newInstance(findAppClass("J6.h", classLoader), collector);
        DiagnosticLogger.log("operation route WebDAV -> " + operation.getClass().getName());
        return operation;
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
                                    DiagnosticLogger.log("repository factory route WebDAV -> T7.N");
                                    param.setResult(newWebDavFileRepository(classLoader));
                                }
                            }
                        }
                );
            }
        });

        installOptionalHook("RepositoryFactory.file.WebDAV.new", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "sc.AbstractC2295H",
                        classLoader,
                        "G",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (isWebDav(param.args[0])) {
                                    DiagnosticLogger.log("repository factory route WebDAV -> T7.N");
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

        installOptionalHook("NetworkServerRepository.F.WebDAV", new HookInstaller() {
            @Override
            public void install() {
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
            }
        });

        installOptionalHook("NetworkServerRepository.l.WebDAV", new HookInstaller() {
            @Override
            public void install() {
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
            }
        });

        installOptionalHook("NetworkFileRepository.E.WebDAV", new HookInstaller() {
            @Override
            public void install() {
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
            }
        });

        installOptionalHook("NetworkFileRepository.l.WebDAV", new HookInstaller() {
            @Override
            public void install() {
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
        });
    }

    private static void hookServerClick(final ClassLoader classLoader) {
        installOptionalHook("ServerClick.old", new HookInstaller() {
            @Override
            public void install() {
                hookAppMethod(
                        "F7.e",
                        classLoader,
                        "z",
                        findAppClass("x7.C2094a", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                handleServerClick(classLoader, param, "f24160a");
                            }
                        }
                );
            }
        });

        installOptionalHook("ServerClick.new", new HookInstaller() {
            @Override
            public void install() {
                hookExactAppMethod(
                        "d9.e",
                        classLoader,
                        "x",
                        findAppClass("V8.a", classLoader),
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                handleServerClick(classLoader, param, "f9866a");
                            }
                        }
                );
            }
        });
    }

    private static void handleServerClick(
            ClassLoader classLoader,
            XC_MethodHook.MethodHookParam param,
            String serverFieldName
    ) {
        try {
            Object clickInfo = param.args[0];
            Object serverInfo = getFieldValue(clickInfo, serverFieldName);
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
        Object currentPageInfo = getCurrentPageInfo(controller);
        Object pageInfo = XposedHelpers.newInstance(findAppClass("q8.C1747e", classLoader));
        Object pageType = getFtpPageType(classLoader);
        setPageInfoPageType(pageInfo, pageType);

        String name = serverBundle.getString(WebDavBackend.KEY_SERVER_NAME, "");
        String address = serverBundle.getString(WebDavBackend.KEY_SERVER_ADDRESS, "");
        if (isEmpty(name)) {
            name = isEmpty(address) ? DISPLAY_WEBDAV : address;
        }
        String rootPath = buildWebDavServerPath(serverId);

        putPageInfoString(pageInfo, WebDavBackend.KEY_SERVER_NAME, name);
        putPageInfoString(pageInfo, WebDavBackend.KEY_SERVER_ADDRESS, address);
        putPageInfoString(pageInfo, WebDavBackend.KEY_SHARED_FOLDER,
                serverBundle.getString(WebDavBackend.KEY_SHARED_FOLDER, ""));
        putPageInfoInt(pageInfo,
                serverBundle.getInt(WebDavBackend.KEY_SERVER_PORT, 443),
                WebDavBackend.KEY_SERVER_PORT);
        putPageInfoLong(pageInfo, WebDavBackend.KEY_SERVER_ID, serverId);
        putPageInfoBoolean(pageInfo, WebDavBackend.KEY_IS_ANONYMOUS_MODE,
                serverBundle.getBoolean(WebDavBackend.KEY_IS_ANONYMOUS_MODE, false));
        setPageInfoPath(pageInfo, rootPath);
        setPageInfoDomain(pageInfo, DOMAIN_WEBDAV);
        setFieldValueIfPresent(pageInfo, "f21254k", true);
        setFieldValueIfPresent(pageInfo, "f21261x", instanceId);
        copyNavigationMode(currentPageInfo, pageInfo);
        setNextDepth(currentPageInfo, pageInfo);

        if (openWebDavServerPageByPageManager(classLoader, instanceId, pageInfo, serverId, rootPath)) {
            return true;
        }

        Object fragment = XposedHelpers.callStaticMethod(findAppClass("D5.b", classLoader), "C", instanceId);
        Object fragmentActivity = XposedHelpers.callMethod(fragment, "c");
        Object navigator = XposedHelpers.callStaticMethod(findAppClass("B5.a", classLoader), "r", instanceId);
        XposedHelpers.callMethod(navigator, "d", fragmentActivity, pageInfo);
        DiagnosticLogger.log("server click opened WebDAV page id=" + serverId + ", path=" + rootPath);
        return true;
    }

    private static boolean openWebDavServerPageByPageManager(
            ClassLoader classLoader,
            int instanceId,
            Object pageInfo,
            long serverId,
            String rootPath
    ) {
        try {
            Object activityHolder = callStaticIntMethodExact(
                    "a.AbstractC0577a",
                    classLoader,
                    "Y",
                    instanceId
            );
            Object fragmentActivity = XposedHelpers.callMethod(activityHolder, "d");
            Object pageManager = callStaticIntMethodExact("Eb.c", classLoader, "v", instanceId);
            XposedHelpers.callMethod(pageManager, "d", fragmentActivity, pageInfo);
            DiagnosticLogger.log("server click opened WebDAV page via PageManager id="
                    + serverId + ", path=" + rootPath);
            return true;
        } catch (Throwable t) {
            DiagnosticLogger.log("server click PageManager open skipped");
            DiagnosticLogger.log(t);
            return false;
        }
    }

    private static Object callStaticIntMethodExact(
            String className,
            ClassLoader classLoader,
            String methodName,
            int arg
    ) throws Throwable {
        Class<?> clazz = findAppClass(className, classLoader);
        Method method = clazz.getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        return method.invoke(null, arg);
    }

    private static void setPageInfoPageType(Object pageInfo, Object pageType) {
        if (pageInfo == null || pageType == null) {
            return;
        }
        if (tryCallMethod(pageInfo, "Q", pageType)) {
            return;
        }
        tryCallMethod(pageInfo, "N", pageType);
    }

    private static void putPageInfoString(Object pageInfo, String key, String value) {
        if (value == null) {
            value = "";
        }
        if (tryCallMethod(pageInfo, "H", key, value)) {
            return;
        }
        tryCallMethod(pageInfo, "J", key, value);
    }

    private static void putPageInfoInt(Object pageInfo, int value, String key) {
        if (tryCallMethod(pageInfo, "D", value, key)) {
            return;
        }
        tryCallMethod(pageInfo, "G", value, key);
    }

    private static void putPageInfoLong(Object pageInfo, String key, long value) {
        if (tryCallMethod(pageInfo, "F", key, value)) {
            return;
        }
        tryCallMethod(pageInfo, "H", value, key);
    }

    private static void putPageInfoBoolean(Object pageInfo, String key, boolean value) {
        if (tryCallMethod(pageInfo, "I", key, value)) {
            return;
        }
        tryCallMethod(pageInfo, "K", key, value);
    }

    private static void setPageInfoPath(Object pageInfo, String path) {
        if (path == null) {
            path = "";
        }
        if (tryCallMethod(pageInfo, "O", path)) {
            return;
        }
        tryCallMethod(pageInfo, "R", path);
    }

    private static void setPageInfoDomain(Object pageInfo, int domainType) {
        if (tryCallMethod(pageInfo, "K", domainType)) {
            return;
        }
        tryCallMethod(pageInfo, "M", domainType);
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
        installOptionalHook("NetworkStorageServerListAdapter.WebDAVIcon", new HookInstaller() {
            @Override
            public void install() throws Throwable {
                hookNetworkStorageServerListIcon(classLoader);
            }
        });
    }

    /**
     * My Files maps all unknown network-storage domains to its FTP resource.  WebDAV uses a
     * private domain (206), so replace only the rendered icon after the stock adapter has bound
     * the server row.  This leaves the native FTP, SFTP, and SMB icons untouched.
     */
    private static void hookNetworkStorageServerListIcon(final ClassLoader classLoader) throws Throwable {
        Class<?> adapterClass = findExactAppClass(
                "com.sec.android.app.myfiles.ui.pages.adapter.NetworkStorageServerListAdapter",
                classLoader
        );
        int hooked = 0;
        for (Method method : adapterClass.getDeclaredMethods()) {
            if (!"onBindChildViewHolder".equals(method.getName())
                    || method.getParameterTypes().length != 4) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object fileInfo = findWebDavFileInfoArgument(param.args);
                        if (fileInfo == null) {
                            return;
                        }
                        ImageView icon = findServerIconView(param.args != null && param.args.length > 0
                                ? param.args[0] : null);
                        if (icon != null) {
                            icon.setImageDrawable(new WebDavServerIconDrawable());
                        }
                    } catch (Throwable t) {
                        DiagnosticLogger.log("server list WebDAV icon replacement failed");
                        DiagnosticLogger.log(t);
                    }
                }
            });
            hooked++;
        }
        if (hooked == 0) {
            throw new NoSuchMethodException(adapterClass.getName() + ".onBindChildViewHolder");
        }
        DiagnosticLogger.log("server list WebDAV icon hook methods=" + hooked);
    }

    private static Object findWebDavFileInfoArgument(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object argument : args) {
            if (readDomainTypeSafe(argument) == DOMAIN_WEBDAV) {
                return argument;
            }
        }
        return null;
    }

    private static ImageView findServerIconView(Object holder) {
        if (holder == null) {
            return null;
        }
        try {
            Object view = XposedHelpers.callMethod(holder, "getServerIcon");
            if (view instanceof ImageView) {
                return (ImageView) view;
            }
        } catch (Throwable ignored) {
            // Older builds may expose the icon only as a holder field.
        }

        ImageView fallback = null;
        for (Field field : allFields(holder.getClass())) {
            if (!ImageView.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(holder);
                if (!(value instanceof ImageView)) {
                    continue;
                }
                String name = field.getName().toLowerCase();
                if (name.contains("server") && name.contains("icon")) {
                    return (ImageView) value;
                }
                if (fallback == null) {
                    fallback = (ImageView) value;
                }
            } catch (Throwable ignored) {
                // Continue scanning holder fields.
            }
        }
        return fallback;
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
        for (Bundle bundle : serverBundles) {
            Object info = newServerInfo(classLoader);
            populateServerInfo(info, bundle);
            result.add(info);
        }
        return result;
    }

    private static Object buildVirtualFolderInfo(ClassLoader classLoader, String path, String name) {
        Object info = newFileInfo(classLoader, path);
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);
        setServerIdByProbe(info, -1L);
        setFilePath(info, path);
        setFileUniqueId(info, path);
        setFileDirectory(info, true);
        setFileType(info, 12289);
        setFileName(info, name);
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
        if (serverId > 0 && Looper.myLooper() != Looper.getMainLooper()) {
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
        populateServerInfoFieldsByName(info, bundle, serverId, address, displayName);
        setServerIdByProbe(info, serverId);
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);

        setFilePath(info, path);
        setFileUniqueId(info, path);
        setFileName(info, displayName);
        tryCallMethod(info, "M", bundle.getLong(WebDavBackend.KEY_SERVER_ADDED_TIME, 0L));
        setFileDirectory(info, true);
        setFileType(info, 12289);
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

    private static void populateServerInfoFieldsByName(
            Object info,
            Bundle bundle,
            long serverId,
            String address,
            String displayName
    ) {
        setFieldValueIfPresent(info, "f26636J", serverId);
        setFieldValueIfPresent(info, "f26637K", bundle.getString("securityMode", "None"));
        setFieldValueIfPresent(info, "f26638L", address);
        setFieldValueIfPresent(info, "f26639M", bundle.getInt(WebDavBackend.KEY_SERVER_PORT, 443));
        setFieldValueIfPresent(info, "f26640N", true);
        setFieldValueIfPresent(info, "f26641O", true);
        setFieldValueIfPresent(info, "f26642P", bundle.getString(WebDavBackend.KEY_ACCOUNT_NAME, ""));
        setFieldValueIfPresent(info, "f26643Q", bundle.getString(WebDavBackend.KEY_ACCOUNT_PASSWORD, ""));
        setFieldValueIfPresent(info, "f26644R", bundle.getString("private_key_file_path", ""));
        setFieldValueIfPresent(info, "S", bundle.getString("passPhrase", ""));
        setFieldValueIfPresent(info, "f26645T",
                bundle.getBoolean(WebDavBackend.KEY_IS_ANONYMOUS_MODE, false));
        setFieldValueIfPresent(info, "f26646U", bundle.getString(WebDavBackend.KEY_ENCODING_TYPE, "UTF-8"));
        setFieldValueIfPresent(info, "f26647V", displayName);
        setFieldValueIfPresent(info, "f26648W", bundle.getString(WebDavBackend.KEY_SHARED_FOLDER, ""));
        setFieldValueIfPresent(info, "f26649X",
                bundle.getLong(WebDavBackend.KEY_SERVER_ADDED_TIME, 0L));
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
        long serverId = bundle.getLong(WebDavBackend.KEY_SERVER_ID, -1L);
        String relativePath = bundle.getString(WebDavBackend.KEY_FILE_PATH, "/");
        String fullPath = trimTrailingPathSlash(attachWebDavServerPath(relativePath, serverId));
        String displayName = bundle.getString(WebDavBackend.KEY_FILE_NAME, "");
        if (isEmpty(displayName)) {
            displayName = WebDavClient.nameOf(relativePath);
        }
        Object info = newFileInfo(classLoader, fullPath);
        boolean isDirectory = bundle.getBoolean(WebDavBackend.KEY_IS_DIRECTORY);
        setDomainTypeByMethodOrProbe(info, DOMAIN_WEBDAV);
        setServerIdByProbe(info, serverId);
        setFilePath(info, fullPath);
        setFileUniqueId(info, fullPath);
        if (!isEmpty(displayName)) {
            setFileName(info, displayName);
        }
        setFileDirectory(info, isDirectory);
        XposedHelpers.callMethod(info, "G", bundle.getLong(WebDavBackend.KEY_FILE_SIZE, 0L));
        XposedHelpers.callMethod(info, "M", bundle.getLong(WebDavBackend.KEY_FILE_DATE, 0L));
        setFileMime(info, bundle.getString(WebDavBackend.KEY_MIME_TYPE, "application/octet-stream"));
        if (isDirectory) {
            setFileType(info, 12289);
        } else {
            setFileType(info, resolveFileType(classLoader, fullPath));
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
            // Fall through to the newer controller getter.
        }
        try {
            Object value = XposedHelpers.callMethod(controller, "l");
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
            // Fall through to direct fields.
        }
        Object direct = getFirstFieldValue(controller, "f9111e", "f23381e");
        if (direct instanceof Number) {
            return ((Number) direct).intValue();
        }
        Object pageInfo = getCurrentPageInfo(controller);
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

    private static Object getCurrentPageInfo(Object controller) {
        Object pageInfo = getFieldValueOrNull(controller, "f9114p");
        if (pageInfo != null) {
            return pageInfo;
        }
        pageInfo = getFieldValueOrNull(controller, "f23389p");
        if (pageInfo != null) {
            return pageInfo;
        }
        try {
            return XposedHelpers.callMethod(controller, "getPageInfo");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void copyNavigationMode(Object currentPageInfo, Object targetPageInfo) {
        if (currentPageInfo == null) {
            return;
        }
        try {
            Object navigationMode = getFieldValue(currentPageInfo, "f21255n");
            if (!tryCallMethod(targetPageInfo, "M", navigationMode)) {
                tryCallMethod(targetPageInfo, "P", navigationMode);
            }
        } catch (Throwable ignored) {
            // Default navigation mode is fine.
        }
        try {
            Bundle currentExtras = (Bundle) getFieldValue(currentPageInfo, "f21256p");
            int menuType = currentExtras.getInt("menuType", -1);
            putPageInfoInt(targetPageInfo, menuType, "menuType");
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
        setObservableBooleanIfPresent(controller, "f2254x", true);
        setObservableBooleanIfPresent(controller, "f2255y", false);
        setObservableBooleanIfPresent(controller, "f2256z", false);
        setObservableBooleanIfPresent(controller, "f2252B", false);
        Object binding = XposedHelpers.callMethod(activity, "getBinding");
        setPortTextIfNeeded(binding, "443");
        if (!tryCallMethod(binding, "v0", controller)) {
            tryCallMethod(binding, "o0", controller);
        }
        XposedBridge.log("MyFilesWebDav: WebDAV manage UI configured");
    }

    private static void setPortTextIfNeeded(Object binding, String port) {
        try {
            if (setRowEditTextIfEmpty(binding, "f8456J", "f8436D", port, "21", "445")
                    || setRowEditTextIfEmpty(binding, "f27868K", "f27817D", port, "21", "445")) {
                return;
            }
            DiagnosticLogger.log("set WebDAV default port skipped, port row not found");
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: set WebDAV default port failed");
            XposedBridge.log(t);
        }
    }

    private static void restoreWebDavAddressText(Object binding, Object pageInfo) {
        try {
            String address = null;
            String shared = null;
            Object serverInfo = getFieldValueOrNull(pageInfo, "t");
            if (serverInfo == null) {
                serverInfo = getFieldValueOrNull(pageInfo, "f12720t");
            }
            if (serverInfo != null) {
                address = readStringFieldIfPresent(serverInfo, "f7353J", "f26638L");
                shared = readStringFieldIfPresent(serverInfo, "f7363U", "f26648W");
            }
            if (isEmpty(address)) {
                Bundle bundle = (Bundle) getFirstFieldValue(pageInfo, "f21256p", "f12717p");
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
            if (!setRowEditText(binding, "f8448B", "f8436D", text)
                    && !setRowEditText(binding, "f27859B", "f27817D", text)) {
                DiagnosticLogger.log("restore edit address skipped, address row not found");
            }
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: restore edit address failed");
            XposedBridge.log(t);
        }
    }

    private static boolean setRowEditTextIfEmpty(
            Object binding,
            String rowFieldName,
            String editFieldName,
            String text,
            String defaultValue1,
            String defaultValue2
    ) {
        Object editText = findRowEditText(binding, rowFieldName, editFieldName);
        if (editText == null) {
            return false;
        }
        Object current = XposedHelpers.callMethod(editText, "getText");
        String value = String.valueOf(current);
        if (isEmpty(value) || defaultValue1.equals(value) || defaultValue2.equals(value)) {
            XposedHelpers.callMethod(editText, "setText", text);
        }
        return true;
    }

    private static boolean setRowEditText(
            Object binding,
            String rowFieldName,
            String editFieldName,
            String text
    ) {
        Object editText = findRowEditText(binding, rowFieldName, editFieldName);
        if (editText == null) {
            return false;
        }
        XposedHelpers.callMethod(editText, "setText", text);
        return true;
    }

    private static Object findRowEditText(Object binding, String rowFieldName, String editFieldName) {
        try {
            Object row = getFieldValue(binding, rowFieldName);
            return getFieldValue(row, editFieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readStringFieldIfPresent(Object object, String... fieldNames) {
        Object value = getFirstFieldValue(object, fieldNames);
        return value instanceof String ? (String) value : null;
    }

    private static Object getFirstFieldValue(Object object, String... fieldNames) {
        if (object == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            try {
                Object value = getFieldValue(object, fieldName);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
                // Try the next known field.
            }
        }
        return null;
    }

    private static void setObservableBoolean(Object owner, String fieldName, boolean value) {
        Object observable = XposedHelpers.getObjectField(owner, fieldName);
        XposedHelpers.callMethod(observable, "P", value);
    }

    private static boolean setObservableBooleanIfPresent(Object owner, String fieldName, boolean value) {
        try {
            setObservableBoolean(owner, fieldName, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getFtpPageType(ClassLoader classLoader) {
        Object pageType = tryCallStaticMethod("ha.AbstractC1537i", classLoader, "G", 202);
        if (pageType != null) {
            return pageType;
        }
        pageType = tryCallStaticMethod("w8.AbstractC2015g", classLoader, "E", 202);
        if (pageType != null) {
            return pageType;
        }
        pageType = getEnumFieldIfPresent(classLoader, "f12765U");
        if (pageType != null) {
            return pageType;
        }
        return getEnumField(classLoader, "f21300S");
    }

    private static Object getNetworkStoragePageVariant(ClassLoader classLoader, boolean editMode) {
        Object pageType = tryCallStaticMethod("ha.AbstractC1537i", classLoader, "H", 202, editMode);
        if (pageType != null) {
            return pageType;
        }
        pageType = tryCallStaticMethod("w8.AbstractC2015g", classLoader, "F", 202, editMode);
        if (pageType != null) {
            return pageType;
        }
        String oldField = editMode ? "f21296Q" : "f21292O";
        pageType = getEnumFieldIfPresent(classLoader, oldField);
        if (pageType != null) {
            return pageType;
        }
        return getFtpPageType(classLoader);
    }

    private static Object getEnumField(ClassLoader classLoader, String fieldName) {
        Class<?> pageTypeClass = findAppClass("q8.EnumC1751i", classLoader);
        return getStaticFieldValue(pageTypeClass, fieldName);
    }

    private static Object getEnumFieldIfPresent(ClassLoader classLoader, String fieldName) {
        try {
            return getEnumField(classLoader, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tryCallStaticMethod(
            String className,
            ClassLoader classLoader,
            String methodName,
            Object... args
    ) {
        try {
            return XposedHelpers.callStaticMethod(findAppClass(className, classLoader), methodName, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void ensureDialogResultHasWebDav(
            Object dialogFragment,
            ClassLoader classLoader,
            String source
    ) {
        try {
            if (dialogFragment != null && ensureWebDavListItem(dialogFragment, classLoader)) {
                DiagnosticLogger.log("add dialog " + source + " ensured WebDAV");
            }
        } catch (Throwable t) {
            DiagnosticLogger.log("add dialog " + source + " ensure failed");
            DiagnosticLogger.log(t);
        }
    }

    private static boolean ensureWebDavListItem(Object dialogFragment, ClassLoader classLoader) {
        ArrayList<?> listItems =
                (ArrayList<?>) XposedHelpers.getObjectField(dialogFragment, "listItems");
        if (listItems == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        ArrayList<Object> writableList = (ArrayList<Object>) listItems;
        int existingIndex = findDomainIndex(listItems, DOMAIN_WEBDAV);
        if (existingIndex >= 0) {
            Object item = writableList.get(existingIndex);
            forceWebDavDisplayName(item);
            if (existingIndex != writableList.size() - 1) {
                writableList.remove(existingIndex);
                writableList.add(item);
                DiagnosticLogger.log("add dialog WebDAV item moved to end, count=" + writableList.size());
            }
            return true;
        }

        Class<?> serverTypeClass = findAppClass(SERVER_TYPE_CLASS, classLoader);
        Object label = findFtpServerLabel(serverTypeClass, classLoader);
        if (label == null) {
            DiagnosticLogger.log("FTP label enum not found");
            return false;
        }
        Object webDavItem = XposedHelpers.newInstance(serverTypeClass, DOMAIN_WEBDAV, label);
        forceWebDavDisplayName(webDavItem);

        writableList.add(webDavItem);
        DiagnosticLogger.log("add dialog WebDAV item added, count=" + writableList.size());
        return true;
    }

    private static int findDomainIndex(ArrayList<?> listItems, int domainType) {
        if (listItems == null) {
            return -1;
        }
        for (int i = 0; i < listItems.size(); i++) {
            Object item = listItems.get(i);
            try {
                if (readDomainType(item) == domainType) {
                    return i;
                }
            } catch (Throwable ignored) {
                // Keep scanning.
            }
        }
        return -1;
    }

    private static void forceWebDavDisplayName(Object item) {
        if (item == null) {
            return;
        }
        if (setFieldValueIfPresent(item, "mDisplayName", DISPLAY_WEBDAV)) {
            return;
        }
        for (Field field : allFields(item.getClass())) {
            if (!CharSequence.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(item);
                if (value instanceof CharSequence) {
                    field.set(item, DISPLAY_WEBDAV);
                    return;
                }
            } catch (Throwable ignored) {
                // Keep scanning possible display fields.
            }
        }
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

        ListView listView = null;
        try {
            listView = dialog.findViewById(android.R.id.list);
        } catch (Throwable ignored) {
            // Fall through to the obfuscated AlertController field scan.
        }
        if (listView == null) {
            listView = findListViewField(dialog);
        }
        if (listView != null) {
            listView.setAdapter((ListAdapter) adapter);
        }

        Object controller = findObjectWithListAdapterField(dialog);
        if (controller != null) {
            setFirstAssignableField(controller, ListAdapter.class, adapter);
        }
        DiagnosticLogger.log("dialog adapter replaced, count=" + items.length);
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

    private static ListView findListViewField(Object owner) {
        if (owner == null) {
            return null;
        }
        for (Field field : allFields(owner.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof ListView) {
                    return (ListView) value;
                }
                if (value != null && value != owner) {
                    ListView nested = findDirectListViewField(value);
                    if (nested != null) {
                        return nested;
                    }
                }
            } catch (Throwable ignored) {
                // Keep scanning obfuscated dialog/controller fields.
            }
        }
        return null;
    }

    private static ListView findDirectListViewField(Object owner) {
        if (owner == null) {
            return null;
        }
        for (Field field : allFields(owner.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof ListView) {
                    return (ListView) value;
                }
            } catch (Throwable ignored) {
                // Keep scanning.
            }
        }
        return null;
    }

    private static Object findObjectWithListAdapterField(Object owner) {
        if (owner == null) {
            return null;
        }
        for (Field field : allFields(owner.getClass())) {
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value != null && hasAssignableField(value.getClass(), ListAdapter.class)) {
                    return value;
                }
            } catch (Throwable ignored) {
                // Keep scanning.
            }
        }
        return null;
    }

    private static boolean hasAssignableField(Class<?> ownerClass, Class<?> fieldType) {
        for (Field field : allFields(ownerClass)) {
            if (fieldType.isAssignableFrom(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean setFirstAssignableField(Object owner, Class<?> fieldType, Object value) {
        if (owner == null) {
            return false;
        }
        for (Field field : allFields(owner.getClass())) {
            if (!fieldType.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.set(owner, value);
                return true;
            } catch (Throwable ignored) {
                // Try the next field.
            }
        }
        return false;
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
            return findFtpServerLabelInClass(labelEnumClass);
        } catch (Throwable t) {
            XposedBridge.log("MyFilesWebDav: find FTP enum by reflection failed");
            XposedBridge.log(t);
            return null;
        }
    }

    private static Object findFtpServerLabel(Class<?> serverTypeClass, ClassLoader classLoader) {
        Object label = findFtpServerLabelInClass(findServerTypeLabelClass(serverTypeClass));
        if (label != null) {
            return label;
        }
        return findFtpServerLabel(classLoader);
    }

    private static Class<?> findServerTypeLabelClass(Class<?> serverTypeClass) {
        if (serverTypeClass == null) {
            return null;
        }
        Constructor<?>[] constructors = serverTypeClass.getDeclaredConstructors();
        for (int i = 0; i < constructors.length; i++) {
            Class<?>[] parameterTypes = constructors[i].getParameterTypes();
            if (parameterTypes.length == 2
                    && (parameterTypes[0] == int.class || parameterTypes[0] == Integer.class)) {
                return parameterTypes[1];
            }
        }
        return null;
    }

    private static Object findFtpServerLabelInClass(Class<?> labelEnumClass) {
        if (labelEnumClass == null) {
            return null;
        }
        Object direct = getStaticFieldValueIfPresent(labelEnumClass, "FTP_SERVER");
        if (direct != null) {
            return direct;
        }
        Object valuesObject = null;
        try {
            valuesObject = XposedHelpers.callStaticMethod(labelEnumClass, "values");
        } catch (Throwable ignored) {
            // Try enumConstants below.
        }
        Object[] values = valuesObject instanceof Object[]
                ? (Object[]) valuesObject
                : labelEnumClass.getEnumConstants();
        if (values == null || values.length == 0) {
            return null;
        }

        Object fallback = null;
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value == null) {
                continue;
            }
            if (fallback == null) {
                fallback = value;
            }
            String text = objectStringPayload(value).toLowerCase();
            if (value instanceof Enum) {
                text = text + "|" + ((Enum<?>) value).name().toLowerCase();
            }
            if (text.contains("ftp_server")) {
                return value;
            }
        }
        return fallback;
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

        String[] knownFields = {"mDomainType", "domainType", "f7491B", "f26830B"};
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
        try {
            Object value = XposedHelpers.callMethod(item, "c0");
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
            Object value = XposedHelpers.callMethod(item, "j");
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Throwable ignored) {
            // Fall through to the older path getter.
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
            Class<?> storagePathClass = findAppClass("w8.G", classLoader);
            if (putWebDavStoragePath(getStaticFieldValueIfPresent(storagePathClass, "f23454e"))
                    || putWebDavStoragePath(getStaticFieldValueIfPresent(storagePathClass, "f21272g"))
                    || putWebDavStoragePath(getStaticFieldValueIfPresent(storagePathClass, "f21281g"))
                    || putWebDavStoragePathByScanning(storagePathClass)) {
                return;
            }
            DiagnosticLogger.log("storage path register skipped, path map not found");
        } catch (Throwable t) {
            DiagnosticLogger.log("storage path register failed");
            DiagnosticLogger.log(t);
        }
    }

    private static boolean putWebDavStoragePath(Object value) {
        if (!(value instanceof SparseArray)) {
            return false;
        }
        SparseArray paths = (SparseArray) value;
        Object old = paths.get(DOMAIN_WEBDAV);
        if (!WEBDAV_ROOT_PATH.equals(old)) {
            paths.put(DOMAIN_WEBDAV, WEBDAV_ROOT_PATH);
        }
        DiagnosticLogger.log("storage path registered domain=" + DOMAIN_WEBDAV
                + ", path=" + WEBDAV_ROOT_PATH);
        return true;
    }

    private static boolean putWebDavStoragePathByScanning(Class<?> storagePathClass) {
        for (Field field : allFields(storagePathClass)) {
            try {
                if (!Modifier.isStatic(field.getModifiers())
                        || !SparseArray.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(null);
                if (looksLikeNetworkStoragePathMap(value) && putWebDavStoragePath(value)) {
                    DiagnosticLogger.log("storage path map found by scan field=" + field.getName());
                    return true;
                }
            } catch (Throwable ignored) {
                // Keep scanning static SparseArray fields.
            }
        }
        return false;
    }

    private static boolean looksLikeNetworkStoragePathMap(Object value) {
        if (!(value instanceof SparseArray)) {
            return false;
        }
        SparseArray paths = (SparseArray) value;
        for (int i = 0; i < paths.size(); i++) {
            Object entry = paths.valueAt(i);
            if (entry instanceof String && ((String) entry).startsWith(NETWORK_ROOT_PATH + "/")) {
                return true;
            }
        }
        return false;
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
        setFieldValueIfPresent(object, "f26830B", domainType);
        setFieldValueIfPresent(object, "f7491B", domainType);
        if (readDomainTypeSafe(object) == domainType) {
            return;
        }
        if (setIntFieldReturnedByMethod(object, "b0", domainType)) {
            return;
        }
        setIntFieldReturnedByMethod(object, "c0", domainType);
    }

    private static void setServerIdByProbe(Object object, long serverId) {
        setFieldValueIfPresent(object, "f26632J", serverId);
        setFieldValueIfPresent(object, "f26636J", serverId);
        setFieldValueIfPresent(object, "f7351H", serverId);
        setFieldValueIfPresent(object, "f7350H", serverId);
        if (readServerIdSafe(object) == serverId) {
            return;
        }
        if (setLongFieldReturnedByMethod(object, "b", serverId)) {
            return;
        }
        if (setLongFieldReturnedByMethod(object, "c", serverId)) {
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

    private static Object getStaticFieldValueIfPresent(Class<?> type, String fieldName) {
        try {
            return getStaticFieldValue(type, fieldName);
        } catch (Throwable ignored) {
            return null;
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
        String[] knownFields = {"f26636J", "f26632J", "f7351H", "f7350H"};
        for (String fieldName : knownFields) {
            try {
                Object value = getFieldValue(item, fieldName);
                if (value instanceof Long) {
                    return (Long) value;
                }
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
            } catch (Throwable ignored) {
                // Try the next known field.
            }
        }
        try {
            Object value = XposedHelpers.callMethod(item, "b");
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Throwable ignored) {
            // Fall through to the newer network-server interface.
        }
        try {
            Object value = XposedHelpers.callMethod(item, "c");
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

    private static final class WebDavServerIconDrawable extends Drawable {
        private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

        WebDavServerIconDrawable() {
            background.setColor(Color.rgb(35, 108, 235));
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float width = bounds.width();
            float height = bounds.height();
            if (width <= 0f || height <= 0f) {
                return;
            }
            float side = Math.min(width, height);
            float inset = side * 0.06f;
            float left = bounds.left + (width - side) / 2f + inset;
            float top = bounds.top + (height - side) / 2f + inset;
            float right = left + side - inset * 2f;
            float bottom = top + side - inset * 2f;
            float radius = side * 0.22f;
            canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, background);

            text.setTextSize(side * 0.31f);
            Paint.FontMetrics metrics = text.getFontMetrics();
            float baseline = (top + bottom - metrics.top - metrics.bottom) / 2f;
            canvas.drawText("WEB", (left + right) / 2f, baseline, text);
        }

        @Override
        public void setAlpha(int alpha) {
            background.setAlpha(alpha);
            text.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            background.setColorFilter(colorFilter);
            text.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
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
