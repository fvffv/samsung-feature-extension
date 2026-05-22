package com.samsung.feature.extension.dualappextender;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

import com.samsung.feature.extension.LogSettingsProvider;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class DualAppExtenderHook implements IXposedHookLoadPackage {
    private static final String TAG = "DualAppExtender";
    private static final String TARGET_PACKAGE = "com.samsung.android.da.daagent";
    private static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    private static final String RAW_MODULE_PACKAGE = "com.samsung.feature.extension.galaxyraw200mp";
    private static volatile Context appContext;
    private static volatile long lastWhitelistPushMs;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        log("loading in " + lpparam.packageName + " process=" + lpparam.processName);
        installHook("Application context", new Installer() {
            @Override
            public void install() {
                hookApplication(lpparam.classLoader);
            }
        });
        installHook("candidate app list", new Installer() {
            @Override
            public void install() {
                hookCandidateList(lpparam.classLoader);
            }
        });
        installHook("whitelist checks", new Installer() {
            @Override
            public void install() {
                hookWhitelistChecks(lpparam.classLoader);
            }
        });
        installHook("system whitelist sync", new Installer() {
            @Override
            public void install() {
                hookWhitelistSync(lpparam.classLoader);
            }
        });
        installHook("provider whitelist", new Installer() {
            @Override
            public void install() {
                hookProviderWhitelist(lpparam.classLoader);
            }
        });
    }

    private static void hookApplication(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "android.app.Application",
                classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof Context) {
                            Context context = ((Context) param.thisObject).getApplicationContext();
                            appContext = context;
                            pushAllEligibleToSystemServer(context, false);
                        }
                    }
                }
        );
    }

    private static void hookCandidateList(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "com.samsung.android.da.daagent.fwwrapper.PmWrapper",
                classLoader,
                "getPossibleDualAppPackages",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = param.args != null && param.args.length > 0
                                && param.args[0] instanceof Context
                                ? (Context) param.args[0]
                                : appContext;
                        ArrayList<String> packages = getEligiblePackages(context);
                        if (!packages.isEmpty()) {
                            param.setResult(packages);
                            log("candidate list replaced, count=" + packages.size());
                        }
                    }
                }
        );
    }

    private static void hookWhitelistChecks(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "com.samsung.android.app.SemDualAppManager",
                classLoader,
                "isWhitelistedPackage",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String pkg = param.args != null && param.args.length > 0
                                ? String.valueOf(param.args[0])
                                : "";
                        if (isEligiblePackage(appContext, pkg)) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                }
        );
    }

    private static void hookWhitelistSync(final ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "com.samsung.android.da.daagent.utils.DAUtility",
                classLoader,
                "updateWhitelistAppsInSystemServer",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = param.args != null && param.args.length > 0
                                && param.args[0] instanceof Context
                                ? (Context) param.args[0]
                                : appContext;
                        pushAllEligibleToSystemServer(context, true);
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                "com.samsung.android.da.daagent.utils.DAUtility",
                classLoader,
                "getWhiteListPkgs",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        boolean availableOnly = param.args != null
                                && param.args.length > 0
                                && Boolean.TRUE.equals(param.args[0]);
                        if (!availableOnly) {
                            return;
                        }
                        ArrayList<String> packages = getEligiblePackages(appContext);
                        if (!packages.isEmpty()) {
                            param.setResult(packages);
                        }
                    }
                }
        );
    }

    private static void hookProviderWhitelist(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "com.samsung.android.da.daagent.provider.DualAppProvider",
                classLoader,
                "query",
                Uri.class,
                String[].class,
                String.class,
                String[].class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Uri uri = param.args != null && param.args.length > 0
                                && param.args[0] instanceof Uri
                                ? (Uri) param.args[0]
                                : null;
                        if (uri == null || !String.valueOf(uri).contains("getWhitelistApps")) {
                            return;
                        }
                        ArrayList<String> packages = getEligiblePackages(appContext);
                        if (packages.isEmpty()) {
                            return;
                        }
                        MatrixCursor cursor = new MatrixCursor(new String[]{"pkgName"}, packages.size());
                        for (int i = 0; i < packages.size(); i++) {
                            cursor.addRow(new Object[]{packages.get(i)});
                        }
                        param.setResult(cursor);
                        log("provider whitelist replaced, count=" + packages.size());
                    }
                }
        );
    }

    private static void pushAllEligibleToSystemServer(Context context, boolean force) {
        if (context == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastWhitelistPushMs < 30000L) {
            return;
        }
        lastWhitelistPushMs = now;
        ArrayList<String> packages = getEligiblePackages(context);
        if (packages.isEmpty()) {
            return;
        }
        try {
            HashMap<String, Integer> map = new HashMap<>();
            for (int i = 0; i < packages.size(); i++) {
                map.put(packages.get(i), Integer.valueOf(0));
            }
            Bundle bundle = new Bundle();
            bundle.putString("command", "updateWhitelistPkgs");
            bundle.putSerializable("packageList", map);
            Class<?> daWrapper = XposedHelpers.findClass(
                    "com.samsung.android.da.daagent.fwwrapper.DaWrapper",
                    context.getClassLoader()
            );
            XposedHelpers.callStaticMethod(daWrapper, "updateDualAppData", context, bundle);
            log("system whitelist pushed, count=" + map.size());
        } catch (Throwable t) {
            log("system whitelist push failed: " + t);
            log(t);
        }
    }

    private static ArrayList<String> getEligiblePackages(Context context) {
        ArrayList<String> result = new ArrayList<>();
        if (context == null) {
            return result;
        }
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> applications = pm.getInstalledApplications(0);
            HashSet<String> seen = new HashSet<>();
            for (int i = 0; applications != null && i < applications.size(); i++) {
                ApplicationInfo info = applications.get(i);
                if (info == null) {
                    continue;
                }
                String pkg = info.packageName;
                if (seen.contains(pkg) || !isEligiblePackage(context, pkg)) {
                    continue;
                }
                seen.add(pkg);
                result.add(pkg);
            }
            sortPackagesByLabel(pm, result);
        } catch (Throwable t) {
            log("build eligible package list failed: " + t);
            log(t);
        }
        return result;
    }

    private static boolean isEligiblePackage(Context context, String pkg) {
        if (context == null || pkg == null || pkg.length() == 0) {
            return false;
        }
        if (TARGET_PACKAGE.equals(pkg) || MODULE_PACKAGE.equals(pkg) || RAW_MODULE_PACKAGE.equals(pkg)) {
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            if (info == null || !info.enabled) {
                return false;
            }
            int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            if ((info.flags & systemFlags) != 0) {
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void sortPackagesByLabel(final PackageManager pm, ArrayList<String> packages) {
        final Collator collator = Collator.getInstance(Locale.getDefault());
        packages.sort((left, right) -> collator.compare(labelFor(pm, left), labelFor(pm, right)));
    }

    private static String labelFor(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null) {
                return label.toString();
            }
        } catch (Throwable ignored) {
            // Fall through to package name sorting.
        }
        return pkg;
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

    private interface Installer {
        void install() throws Throwable;
    }
}
