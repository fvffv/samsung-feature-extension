package com.codex.myfileswebdavpopup;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class LauncherIconCustomizerHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.sec.android.app.launcher";
    private static final String MODULE_PACKAGE = "com.codex.myfileswebdavpopup";
    private static final String SETTINGS_PREF_KEY = "codex_custom_launcher_icon";
    private static final long CACHE_TTL_MS = 3000L;

    private static volatile Context appContext;
    private static volatile boolean receiverRegistered;
    private static volatile boolean observerRegistered;
    private static volatile long lastResumeRefreshAt;
    private static final Map<String, CacheEntry> CACHE = new HashMap<String, CacheEntry>();
    private static final Map<String, LabelCacheEntry> LABEL_CACHE = new HashMap<String, LabelCacheEntry>();
    private static final Map<String, TypefaceCacheEntry> TYPEFACE_CACHE = new HashMap<String, TypefaceCacheEntry>();
    private static final Map<String, ArrayList<WeakReference<Object>>> KNOWN_APP_ITEMS =
            new HashMap<String, ArrayList<WeakReference<Object>>>();
    private static final Map<String, ArrayList<WeakReference<Object>>> KNOWN_ICON_VIEWS =
            new HashMap<String, ArrayList<WeakReference<Object>>>();
    private static final Map<Object, String> ICON_VIEW_PACKAGES = new WeakHashMap<Object, String>();
    private static final Map<Object, Boolean> ICON_VIEW_FONT_APPLIED = new WeakHashMap<Object, Boolean>();
    private static final Map<Object, String> ICON_LIVEDATA_PACKAGES = new WeakHashMap<Object, String>();
    private static final Map<Object, String> LABEL_LIVEDATA_PACKAGES = new WeakHashMap<Object, String>();
    private static final ThreadLocal<Boolean> APPLYING_ICON_VIEW = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> APPLYING_LABEL_LIVEDATA = new ThreadLocal<Boolean>();
    private static volatile boolean settingsTreeDumped;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        DiagnosticLogger.log("LauncherIconCustomizer: loading in " + lpparam.packageName);
        hookApplicationAttach(lpparam.classLoader);
        hookLauncherActivityResume(lpparam.classLoader);
        hookSettingsFragment(lpparam.classLoader);
        hookLauncherActivityInfo();
        hookPackageManagerIcons(lpparam.classLoader);
        hookIconData(lpparam.classLoader);
        hookAppItem(lpparam.classLoader);
        hookIconBinding(lpparam.classLoader);
        hookMappedIconViewLabelAppearance(lpparam.classLoader);
        hookIconItemCreators(lpparam.classLoader);
        hookHoneyPot(lpparam.classLoader);
        DiagnosticLogger.log("LauncherIconCustomizer: recycled IconView/LiveData fallback hooks disabled");
    }

    private void hookApplicationAttach(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Application", classLoader, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = (Context) param.args[0];
                    appContext = context.getApplicationContext();
                    DiagnosticLogger.init(appContext);
                    registerRefreshReceiver(appContext);
                    registerProviderObserver(appContext);
                    DiagnosticLogger.log("LauncherIconCustomizer: Application context installed");
                    scheduleKnownAppItemRefresh(appContext);
                }
            });
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: Application.attach hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookLauncherActivityResume(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", classLoader, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Context)) {
                        return;
                    }
                    Context activityContext = (Context) param.thisObject;
                    if (!TARGET_PACKAGE.equals(activityContext.getPackageName())) {
                        return;
                    }
                    long now = SystemClock.uptimeMillis();
                    if (now - lastResumeRefreshAt < 1200L) {
                        return;
                    }
                    lastResumeRefreshAt = now;
                    final Context context = activityContext.getApplicationContext();
                    appContext = context;
                    DiagnosticLogger.init(context);
                    registerRefreshReceiver(context);
                    registerProviderObserver(context);
                    synchronized (CACHE) {
                        CACHE.clear();
                    }
                    synchronized (LABEL_CACHE) {
                        LABEL_CACHE.clear();
                    }
                    synchronized (TYPEFACE_CACHE) {
                        TYPEFACE_CACHE.clear();
                    }
                    DiagnosticLogger.log("LauncherIconCustomizer: Activity.onResume refresh, activity="
                            + param.thisObject.getClass().getName());
                    runScheduledRefresh(context, -1L);
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            runScheduledRefresh(context, -2L);
                        }
                    }, 900L);
                }
            });
            DiagnosticLogger.log("LauncherIconCustomizer: Activity.onResume refresh hook installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: Activity.onResume refresh hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookSettingsFragment(final ClassLoader classLoader) {
        try {
            Class<?> fragmentClass = XposedHelpers.findClass("com.android.homescreen.settings.SettingsFragment", classLoader);
            XposedHelpers.findAndHookMethod(fragmentClass, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    injectPreference(param.thisObject, classLoader);
                }
            });
            XposedHelpers.findAndHookMethod(fragmentClass, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    injectPreference(param.thisObject, classLoader);
                }
            });
            try {
                XposedHelpers.findAndHookMethod(fragmentClass, "onCreateView",
                        LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                injectPreference(param.thisObject, classLoader);
                            }
                        });
                DiagnosticLogger.log("LauncherIconCustomizer: SettingsFragment onCreateView hook installed");
            } catch (Throwable t) {
                DiagnosticLogger.log("LauncherIconCustomizer: SettingsFragment onCreateView hook skipped");
                DiagnosticLogger.log(t);
            }
            DiagnosticLogger.log("LauncherIconCustomizer: SettingsFragment preference hook installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: SettingsFragment hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookPackageManagerIcons(final ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", classLoader,
                    "getApplicationIcon", String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            replaceDrawableResult(param, stringValue(param.args[0]));
                        }
                    });
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", classLoader,
                    "getApplicationIcon", ApplicationInfo.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ApplicationInfo info = param.args[0] instanceof ApplicationInfo
                                    ? (ApplicationInfo) param.args[0]
                                    : null;
                            replaceDrawableResult(param, info != null ? info.packageName : "");
                        }
                    });
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", classLoader,
                    "getActivityIcon", ComponentName.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ComponentName componentName = param.args[0] instanceof ComponentName
                                    ? (ComponentName) param.args[0]
                                    : null;
                            replaceDrawableResult(param, componentName != null ? componentName.getPackageName() : "");
                        }
                    });
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", classLoader,
                    "getApplicationLabel", ApplicationInfo.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ApplicationInfo info = param.args[0] instanceof ApplicationInfo
                                    ? (ApplicationInfo) param.args[0]
                                    : null;
                            replaceLabelResult(param, info != null ? info.packageName : "", "PackageManager.getApplicationLabel");
                        }
                    });
            DiagnosticLogger.log("LauncherIconCustomizer: PackageManager icon/label hooks installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: PackageManager icon/label hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookLauncherActivityInfo() {
        try {
            XposedHelpers.findAndHookMethod(LauncherActivityInfo.class, "getIcon", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    replaceLauncherActivityDrawable(param);
                }
            });
            XposedHelpers.findAndHookMethod(LauncherActivityInfo.class, "getBadgedIcon", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    replaceLauncherActivityDrawable(param);
                }
            });
            XposedHelpers.findAndHookMethod(LauncherActivityInfo.class, "getLabel", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof LauncherActivityInfo)) {
                        return;
                    }
                    ComponentName componentName = ((LauncherActivityInfo) param.thisObject).getComponentName();
                    replaceLabelResult(param,
                            componentName != null ? componentName.getPackageName() : "",
                            "LauncherActivityInfo.getLabel");
                }
            });
            DiagnosticLogger.log("LauncherIconCustomizer: LauncherActivityInfo icon/label hooks installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: LauncherActivityInfo hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookIconData(final ClassLoader classLoader) {
        try {
            Class<?> iconDataClass = XposedHelpers.findClass("com.honeyspace.sdk.database.entity.IconData", classLoader);
            XposedHelpers.findAndHookMethod(iconDataClass, "getIcon", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Bitmap original = param.getResult() instanceof Bitmap ? (Bitmap) param.getResult() : null;
                    String componentName = stringValue(XposedHelpers.callMethod(param.thisObject, "getComponentName"));
                    String packageName = packageFromComponentString(componentName);
                    Bitmap custom = loadCustomBitmap(appContext, packageName, widthOf(original), heightOf(original));
                    if (custom != null) {
                        param.setResult(custom);
                    }
                }
            });
            try {
                XposedHelpers.findAndHookMethod(iconDataClass, "getLabel", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String componentName = stringValue(XposedHelpers.callMethod(param.thisObject, "getComponentName"));
                        replaceLabelResult(param, packageFromComponentString(componentName), "IconData.getLabel");
                    }
                });
                DiagnosticLogger.log("LauncherIconCustomizer: IconData.getLabel hook installed");
            } catch (Throwable labelError) {
                DiagnosticLogger.log("LauncherIconCustomizer: IconData.getLabel hook skipped");
                DiagnosticLogger.log(labelError);
            }
            DiagnosticLogger.log("LauncherIconCustomizer: IconData.getIcon hook installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: IconData hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookAppItem(final ClassLoader classLoader) {
        try {
            Class<?> appItemClass = XposedHelpers.findClass("com.honeyspace.sdk.source.entity.AppItem", classLoader);
            final Class<?> iconAndLabelClass = XposedHelpers.findClass("com.honeyspace.sdk.source.entity.IconAndLabel", classLoader);
            final Class<?> mutableLiveDataClass = XposedHelpers.findClass("androidx.lifecycle.MutableLiveData", classLoader);
            hookAppItemConstructors(appItemClass);
            XposedHelpers.findAndHookMethod(appItemClass, "updateIconAndLabel", Context.class, iconAndLabelClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Context context = (Context) param.args[0];
                    Object iconAndLabel = param.args[1];
                    String packageName = packageFromAppItem(param.thisObject);
                    rememberAppItem(packageName, param.thisObject);
                    rememberIconLiveData(packageName, XposedHelpers.callMethod(param.thisObject, "getIcon"));
                    rememberLabelLiveData(packageName, XposedHelpers.callMethod(param.thisObject, "getLabel"));
                    Bitmap original = (Bitmap) XposedHelpers.callMethod(iconAndLabel, "getIcon");
                    Bitmap custom = loadCustomBitmap(context, packageName, widthOf(original), heightOf(original));
                    CharSequence label = (CharSequence) XposedHelpers.callMethod(iconAndLabel, "getLabel");
                    String customLabel = loadCustomLabel(context, packageName);
                    if (custom == null && customLabel == null) {
                        return;
                    }
                    param.args[1] = XposedHelpers.newInstance(
                            iconAndLabelClass,
                            custom != null ? custom : original,
                            customLabel != null ? customLabel : label
                    );
                    DiagnosticLogger.log("LauncherIconCustomizer: AppItem icon/label replaced for " + packageName
                            + ", icon=" + (custom != null) + ", label=" + (customLabel != null));
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = (Context) param.args[0];
                    String packageName = packageFromAppItem(param.thisObject);
                    Object liveData = XposedHelpers.callMethod(param.thisObject, "getIcon");
                    Object labelLiveData = XposedHelpers.callMethod(param.thisObject, "getLabel");
                    rememberAppItem(packageName, param.thisObject);
                    rememberIconLiveData(packageName, liveData);
                    rememberLabelLiveData(packageName, labelLiveData);
                    applyIconToLiveData(context, packageName, liveData);
                    applyLabelToLiveData(context, packageName, labelLiveData);
                }
            });
            XposedHelpers.findAndHookMethod(appItemClass, "setIcon", mutableLiveDataClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String packageName = packageFromAppItem(param.thisObject);
                    rememberAppItem(packageName, param.thisObject);
                    rememberIconLiveData(packageName, param.args[0]);
                    applyIconToLiveData(appContext, packageName, param.args[0]);
                }
            });
            XposedHelpers.findAndHookMethod(appItemClass, "setLabel", mutableLiveDataClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String packageName = packageFromAppItem(param.thisObject);
                    rememberAppItem(packageName, param.thisObject);
                    rememberLabelLiveData(packageName, param.args[0]);
                    applyLabelToLiveData(appContext, packageName, param.args[0]);
                }
            });
            XposedHelpers.findAndHookMethod(appItemClass, "getIcon", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = appContext;
                    String packageName = packageFromAppItem(param.thisObject);
                    rememberAppItem(packageName, param.thisObject);
                    rememberIconLiveData(packageName, param.getResult());
                    applyIconToLiveData(context, packageName, param.getResult());
                }
            });
            XposedHelpers.findAndHookMethod(appItemClass, "getLabel", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = appContext;
                    String packageName = packageFromAppItem(param.thisObject);
                    rememberAppItem(packageName, param.thisObject);
                    rememberLabelLiveData(packageName, param.getResult());
                    applyLabelToLiveData(context, packageName, param.getResult());
                }
            });
            DiagnosticLogger.log("LauncherIconCustomizer: AppItem icon/label hooks installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: AppItem hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookAppItemConstructors(Class<?> appItemClass) {
        try {
            Constructor<?>[] constructors = appItemClass.getDeclaredConstructors();
            for (int i = 0; i < constructors.length; i++) {
                Constructor<?> constructor = constructors[i];
                constructor.setAccessible(true);
                XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String packageName = packageFromAppItem(param.thisObject);
                        Object liveData = XposedHelpers.callMethod(param.thisObject, "getIcon");
                        Object labelLiveData = XposedHelpers.callMethod(param.thisObject, "getLabel");
                        rememberAppItem(packageName, param.thisObject);
                        rememberIconLiveData(packageName, liveData);
                        rememberLabelLiveData(packageName, labelLiveData);
                        applyIconToLiveData(appContext, packageName, liveData);
                        applyLabelToLiveData(appContext, packageName, labelLiveData);
                    }
                });
            }
            DiagnosticLogger.log("LauncherIconCustomizer: AppItem constructor hooks installed, count=" + constructors.length);
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: AppItem constructor hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookIconBinding(final ClassLoader classLoader) {
        try {
            final Class<?> bindingClass = XposedHelpers.findClass("L6.b", classLoader);
            final Class<?> iconItemClass = XposedHelpers.findClass("com.honeyspace.sdk.source.entity.IconItem", classLoader);
            XC_MethodHook bindHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object item = param.args != null && param.args.length > 0 ? param.args[0] : null;
                    applyBoundIconItem(param.thisObject, item, "d");
                }
            };
            boolean installed = false;
            Method[] methods = bindingClass.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                Class<?>[] parameterTypes = method.getParameterTypes();
                if ("d".equals(method.getName())
                        && parameterTypes.length == 1
                        && iconItemClass.isAssignableFrom(parameterTypes[0])) {
                    method.setAccessible(true);
                    XposedBridge.hookMethod(method, bindHook);
                    installed = true;
                }
            }
            if (!installed) {
                XposedHelpers.findAndHookMethod(bindingClass, "d", iconItemClass, bindHook);
            }
            XposedHelpers.findAndHookMethod(bindingClass, "executeBindings", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object item = null;
                    try {
                        item = XposedHelpers.getObjectField(param.thisObject, "c");
                    } catch (Throwable ignored) {
                        // Some launcher builds may rename the data-binding backing field.
                    }
                    applyBoundIconItem(param.thisObject, item, "executeBindings");
                }
            });
            DiagnosticLogger.log("LauncherIconCustomizer: layout_appicon binding hooks installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: layout_appicon binding hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookIconItemCreators(final ClassLoader classLoader) {
        hookCreatorMethods(classLoader,
                "com.honeyspace.ui.common.model.IconItemDataCreator",
                "createAppItem",
                "IconItemDataCreator.createAppItem");
        hookCreatorMethods(classLoader,
                "com.honeyspace.ui.common.model.AppItemCreator",
                "create",
                "AppItemCreator.create");
    }

    private void hookCreatorMethods(ClassLoader classLoader, String className, String methodName, final String label) {
        try {
            Class<?> creatorClass = XposedHelpers.findClass(className, classLoader);
            Method[] methods = creatorClass.getDeclaredMethods();
            int count = 0;
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        rememberCreatedIconItem(param.getResult(), label);
                    }
                });
                count++;
            }
            DiagnosticLogger.log("LauncherIconCustomizer: " + label + " hooks installed, count=" + count);
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: " + label + " hook skipped");
            DiagnosticLogger.log(t);
        }
    }

    private void hookHoneyPot(final ClassLoader classLoader) {
        try {
            Class<?> honeyPotClass = XposedHelpers.findClass("com.honeyspace.common.entity.HoneyPot", classLoader);
            Class<?> listClass = XposedHelpers.findClass("java.util.List", classLoader);
            XposedHelpers.findAndHookMethod(honeyPotClass,
                    "createHoney",
                    String.class,
                    String.class,
                    int.class,
                    listClass,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object data = param.args != null && param.args.length > 3 ? param.args[3] : null;
                            Object item = findIconItemInData(data);
                            String packageName = packageFromIconItem(item);
                            if (packageName.length() == 0) {
                                return;
                            }
                            rememberCreatedIconItem(item, "HoneyPot.createHoney.data");
                            Object honey = param.getResult();
                            Object view = null;
                            try {
                                view = XposedHelpers.callMethod(honey, "getView");
                            } catch (Throwable ignored) {
                                // Some honey implementations may not expose a view yet.
                            }
                            bindIconViewToPackage(packageName, view, "HoneyPot.createHoney");
                        }
                    });
            DiagnosticLogger.log("LauncherIconCustomizer: HoneyPot.createHoney hook installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: HoneyPot.createHoney hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookMappedIconViewLabelAppearance(final ClassLoader classLoader) {
        try {
            final Class<?> iconViewClass = XposedHelpers.findClass("com.honeyspace.ui.common.iconview.IconViewImpl", classLoader);
            final Class<?> itemStyleClass = XposedHelpers.findClass("com.honeyspace.sdk.source.entity.ItemStyle", classLoader);
            XposedHelpers.findAndHookMethod(iconViewClass, "setItemStyle", itemStyleClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyMappedLabelAppearanceToIconView(param.thisObject, "IconViewImpl.setItemStyle.after");
                }
            });
            XposedHelpers.findAndHookMethod(iconViewClass, "setLabel", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyMappedLabelAppearanceToIconView(param.thisObject, "IconViewImpl.setLabel.after");
                }
            });
            DiagnosticLogger.log("LauncherIconCustomizer: mapped IconView label appearance hooks installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: mapped IconView label appearance hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookIconViewImpl(final ClassLoader classLoader) {
        try {
            final Class<?> iconViewClass = XposedHelpers.findClass("com.honeyspace.ui.common.iconview.IconViewImpl", classLoader);
            XposedHelpers.findAndHookMethod(iconViewClass, "setIcon", Drawable.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(APPLYING_ICON_VIEW.get())) {
                        return;
                    }
                    String packageName = mappedPackageForIconView(param.thisObject);
                    if (packageName.length() == 0) {
                        return;
                    }
                    Drawable original = param.args != null && param.args.length > 0 && param.args[0] instanceof Drawable
                            ? (Drawable) param.args[0]
                            : null;
                    Drawable custom = customDrawableForIconView(appContext, packageName, original);
                    if (custom != null) {
                        param.args[0] = custom;
                        DiagnosticLogger.log("LauncherIconCustomizer: IconViewImpl.setIcon argument replaced for " + packageName);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(APPLYING_ICON_VIEW.get())) {
                        return;
                    }
                    String packageName = mappedPackageForIconView(param.thisObject);
                    if (packageName.length() != 0) {
                        applyIconToIconView(appContext, packageName, param.thisObject, "IconViewImpl.setIcon.after");
                    }
                }
            });
            XposedHelpers.findAndHookMethod(iconViewClass, "setLabel", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String packageName = mappedPackageForIconView(param.thisObject);
                    if (packageName.length() == 0) {
                        return;
                    }
                    String customLabel = loadCustomLabel(appContext, packageName);
                    if (customLabel != null) {
                        param.args[0] = customLabel;
                        DiagnosticLogger.log("LauncherIconCustomizer: IconViewImpl.setLabel argument replaced for " + packageName);
                    }
                }
            });
            DiagnosticLogger.log("LauncherIconCustomizer: IconViewImpl hooks installed, draw refresh disabled");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: IconViewImpl hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void hookMutableLiveData(final ClassLoader classLoader) {
        try {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    replaceMappedLiveDataDrawable(param);
                    replaceMappedLiveDataLabel(param);
                }
            };
            XposedHelpers.findAndHookMethod("androidx.lifecycle.MutableLiveData", classLoader, "setValue", Object.class, hook);
            XposedHelpers.findAndHookMethod("androidx.lifecycle.MutableLiveData", classLoader, "postValue", Object.class, hook);
            DiagnosticLogger.log("LauncherIconCustomizer: MutableLiveData icon/label hooks installed");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: MutableLiveData hook failed");
            DiagnosticLogger.log(t);
        }
    }

    private void injectPreference(Object fragment, ClassLoader classLoader) {
        try {
            final Context context = (Context) XposedHelpers.callMethod(fragment, "requireContext");
            Object screen = XposedHelpers.callMethod(fragment, "getPreferenceScreen");
            if (context == null || screen == null) {
                return;
            }
            Object existing = XposedHelpers.callMethod(fragment, "findPreference", SETTINGS_PREF_KEY);
            if (existing != null) {
                configurePreference(context, existing, classLoader);
                logPreferencePresence(fragment, screen, existing, "existing");
                return;
            }

            Object parent = findPreferenceParent(fragment, screen);
            Class<?> preferenceClass = XposedHelpers.findClass("androidx.preference.Preference", classLoader);
            Object preference = XposedHelpers.newInstance(preferenceClass, context);
            XposedHelpers.callMethod(preference, "setKey", SETTINGS_PREF_KEY);
            XposedHelpers.callMethod(preference, "setOrder", Integer.valueOf(preferenceCount(parent)));
            configurePreference(context, preference, classLoader);
            int before = preferenceCount(parent);
            try {
                XposedHelpers.callMethod(parent, "addPreference", preference);
                DiagnosticLogger.log("LauncherIconCustomizer: preference injected into "
                        + parent.getClass().getName()
                        + ", parentKey=" + safePreferenceKey(parent)
                        + ", before=" + before
                        + ", after=" + preferenceCount(parent));
            } catch (Throwable addToCategoryError) {
                XposedHelpers.callMethod(preference, "setOrder", Integer.valueOf(preferenceCount(screen)));
                XposedHelpers.callMethod(screen, "addPreference", preference);
                DiagnosticLogger.log("LauncherIconCustomizer: preference injected into root after category add failed");
                DiagnosticLogger.log(addToCategoryError);
            }
            configurePreference(context, preference, classLoader);
            logPreferencePresence(fragment, screen, preference, "new");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: preference injection failed");
            DiagnosticLogger.log(t);
        }
    }

    private Object findPreferenceParent(Object fragment, Object screen) {
        String[] categoryKeys = {
                "pref_category_basic_settings",
                "pref_category_additional_settings",
                "pref_category_Icon_Widget__Style_settings"
        };
        for (String key : categoryKeys) {
            try {
                Object candidate = XposedHelpers.callMethod(fragment, "findPreference", key);
                if (candidate != null) {
                    DiagnosticLogger.log("LauncherIconCustomizer: native preference parent=" + key);
                    return candidate;
                }
            } catch (Throwable ignored) {
                // Fall through to the next native settings group.
            }
        }
        DiagnosticLogger.log("LauncherIconCustomizer: native preference parent=root");
        return screen;
    }

    private void configurePreference(final Context context, Object preference, ClassLoader classLoader) {
        try {
            XposedHelpers.callMethod(preference, "setTitle", "桌面图标与名称自定义");
            XposedHelpers.callMethod(preference, "setSummary", "从相册设置应用图标，也可以单独修改桌面显示名称");
            XposedHelpers.callMethod(preference, "setSelectable", Boolean.TRUE);
            try {
                XposedHelpers.callMethod(preference, "setVisible", Boolean.TRUE);
            } catch (Throwable ignored) {
                // Older preference builds do not expose setVisible.
            }
            trySetPreferenceIcon(context, preference);

            Class<?> listenerClass = XposedHelpers.findClass("androidx.preference.Preference$OnPreferenceClickListener", classLoader);
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("onPreferenceClick".equals(method.getName())) {
                                openModuleSettings(context);
                                return Boolean.TRUE;
                            }
                            return Boolean.FALSE;
                        }
                    }
            );
            XposedHelpers.callMethod(preference, "setOnPreferenceClickListener", listener);
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: configure preference failed");
            DiagnosticLogger.log(t);
        }
    }

    private void logPreferencePresence(Object fragment, Object screen, Object preference, String phase) {
        try {
            Object found = XposedHelpers.callMethod(fragment, "findPreference", SETTINGS_PREF_KEY);
            Object parent = null;
            try {
                parent = XposedHelpers.callMethod(preference, "getParent");
            } catch (Throwable ignored) {
                // Parent is only diagnostic.
            }
            DiagnosticLogger.log("LauncherIconCustomizer: preference presence phase=" + phase
                    + ", found=" + (found != null)
                    + ", parent=" + (parent != null ? parent.getClass().getName() : "null")
                    + ", parentKey=" + safePreferenceKey(parent)
                    + ", rootCount=" + preferenceCount(screen)
                    + ", visible=" + safePreferenceVisible(preference));
            if (!settingsTreeDumped) {
                settingsTreeDumped = true;
                dumpPreferenceTree(screen, 0, 80);
            }
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: preference presence log failed");
            DiagnosticLogger.log(t);
        }
    }

    private void dumpPreferenceTree(Object preference, int depth, int remaining) {
        if (preference == null || remaining <= 0 || depth > 4) {
            return;
        }
        DiagnosticLogger.log("LauncherIconCustomizer: prefTree "
                + treeIndent(depth)
                + preference.getClass().getName()
                + " key=" + safePreferenceKey(preference)
                + " title=" + safePreferenceTitle(preference)
                + " visible=" + safePreferenceVisible(preference)
                + " count=" + preferenceCount(preference));
        int count = preferenceCount(preference);
        for (int i = 0; i < count && remaining > 1; i++) {
            try {
                Object child = XposedHelpers.callMethod(preference, "getPreference", Integer.valueOf(i));
                dumpPreferenceTree(child, depth + 1, remaining - 1);
                remaining--;
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static String treeIndent(int depth) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        return builder.toString();
    }

    private static String safePreferenceKey(Object preference) {
        if (preference == null) {
            return "null";
        }
        try {
            return stringValue(XposedHelpers.callMethod(preference, "getKey"));
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static String safePreferenceTitle(Object preference) {
        if (preference == null) {
            return "null";
        }
        try {
            return stringValue(XposedHelpers.callMethod(preference, "getTitle"));
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static String safePreferenceVisible(Object preference) {
        if (preference == null) {
            return "null";
        }
        try {
            return stringValue(XposedHelpers.callMethod(preference, "isVisible"));
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static int preferenceCount(Object preferenceGroup) {
        try {
            Object count = XposedHelpers.callMethod(preferenceGroup, "getPreferenceCount");
            if (count instanceof Integer) {
                return ((Integer) count).intValue();
            }
        } catch (Throwable ignored) {
            // The default order is fine if this launcher version differs.
        }
        return 0;
    }

    private void trySetPreferenceIcon(Context context, Object preference) {
        try {
            Drawable drawable = context.getPackageManager().getApplicationIcon(MODULE_PACKAGE);
            XposedHelpers.callMethod(preference, "setIcon", drawable);
        } catch (Throwable ignored) {
            // Text is enough if the module icon cannot be loaded.
        }
    }

    private void openModuleSettings(Context context) {
        try {
            Intent intent = new Intent();
            intent.setClassName(MODULE_PACKAGE, MODULE_PACKAGE + ".LauncherIconCustomizerActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: open settings failed");
            DiagnosticLogger.log(t);
        }
    }

    private void replaceLauncherActivityDrawable(XC_MethodHook.MethodHookParam param) {
        Context context = appContext;
        if (context == null || !(param.thisObject instanceof LauncherActivityInfo)) {
            return;
        }
        LauncherActivityInfo info = (LauncherActivityInfo) param.thisObject;
        ComponentName componentName = info.getComponentName();
        if (componentName == null) {
            return;
        }
        Drawable original = param.getResult() instanceof Drawable ? (Drawable) param.getResult() : null;
        int width = original != null && original.getIntrinsicWidth() > 0 ? original.getIntrinsicWidth() : 0;
        int height = original != null && original.getIntrinsicHeight() > 0 ? original.getIntrinsicHeight() : width;
        Bitmap custom = loadCustomBitmap(context, componentName.getPackageName(), width, height);
        if (custom != null) {
            param.setResult(newCustomDrawable(context, custom));
        }
    }

    private static void replaceDrawableResult(XC_MethodHook.MethodHookParam param, String packageName) {
        Context context = appContext;
        if (context == null || packageName == null || packageName.length() == 0) {
            return;
        }
        Drawable original = param.getResult() instanceof Drawable ? (Drawable) param.getResult() : null;
        int width = original != null && original.getIntrinsicWidth() > 0 ? original.getIntrinsicWidth() : 0;
        int height = original != null && original.getIntrinsicHeight() > 0 ? original.getIntrinsicHeight() : width;
        Bitmap custom = loadCustomBitmap(context, packageName, width, height);
        if (custom != null) {
            param.setResult(newCustomDrawable(context, custom));
            DiagnosticLogger.log("LauncherIconCustomizer: PackageManager icon replaced for " + packageName);
        }
    }

    private static void replaceLabelResult(XC_MethodHook.MethodHookParam param, String packageName, String source) {
        Context context = appContext;
        if (context == null || packageName == null || packageName.length() == 0) {
            return;
        }
        String customLabel = loadCustomLabel(context, packageName);
        if (customLabel == null) {
            return;
        }
        if (customLabel.contentEquals(stringValue(param.getResult()))) {
            return;
        }
        param.setResult(customLabel);
        DiagnosticLogger.log("LauncherIconCustomizer: label result replaced for " + packageName
                + ", source=" + source + ", label=" + customLabel);
    }

    private static void replaceMappedLiveDataDrawable(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof Drawable)) {
            return;
        }
        String packageName;
        synchronized (ICON_LIVEDATA_PACKAGES) {
            packageName = ICON_LIVEDATA_PACKAGES.get(param.thisObject);
        }
        if (packageName == null || packageName.length() == 0) {
            return;
        }
        Context context = appContext;
        if (context == null) {
            return;
        }
        Drawable original = (Drawable) param.args[0];
        int width = original.getIntrinsicWidth() > 0 ? original.getIntrinsicWidth() : dp(context, 56);
        int height = original.getIntrinsicHeight() > 0 ? original.getIntrinsicHeight() : width;
        Bitmap custom = loadCustomBitmap(context, packageName, width, height);
        if (custom != null) {
            param.args[0] = newCustomDrawable(context, custom);
        }
    }

    private static void replaceMappedLiveDataLabel(XC_MethodHook.MethodHookParam param) {
        if (Boolean.TRUE.equals(APPLYING_LABEL_LIVEDATA.get())
                || param.args == null
                || param.args.length == 0
                || !(param.args[0] instanceof CharSequence)) {
            return;
        }
        String packageName;
        synchronized (LABEL_LIVEDATA_PACKAGES) {
            packageName = LABEL_LIVEDATA_PACKAGES.get(param.thisObject);
        }
        if (packageName == null || packageName.length() == 0) {
            return;
        }
        String customLabel = loadCustomLabel(appContext, packageName);
        if (customLabel != null) {
            param.args[0] = customLabel;
        }
    }

    private static String packageFromAppItem(Object appItem) {
        try {
            Object component = XposedHelpers.callMethod(appItem, "getComponent");
            Object packageName = XposedHelpers.callMethod(component, "getPackageName");
            return stringValue(packageName);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String packageFromIconItem(Object item) {
        if (item == null) {
            return "";
        }
        String packageName = packageFromAppItem(item);
        if (packageName.length() != 0) {
            return packageName;
        }
        try {
            Object componentName = XposedHelpers.callMethod(item, "getComponentName");
            packageName = packageFromComponentString(stringValue(componentName));
            if (packageName.length() != 0) {
                return packageName;
            }
        } catch (Throwable ignored) {
            // Not every IconItem exposes a flattened component string.
        }
        try {
            Object component = XposedHelpers.callMethod(item, "component15");
            Object name = XposedHelpers.callMethod(component, "getPackageName");
            return stringValue(name);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Object findIconItemInData(Object data) {
        if (data == null) {
            return null;
        }
        if (packageFromIconItem(data).length() != 0) {
            return data;
        }
        if (data instanceof Iterable) {
            Iterator<?> iterator = ((Iterable<?>) data).iterator();
            while (iterator.hasNext()) {
                Object item = findIconItemInData(iterator.next());
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
        if (data.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(data);
            for (int i = 0; i < length; i++) {
                Object item = findIconItemInData(java.lang.reflect.Array.get(data, i));
                if (item != null) {
                    return item;
                }
            }
        }
        return null;
    }

    private static void applyBoundIconItem(Object binding, Object item, String phase) {
        Context context = appContext;
        String packageName = packageFromIconItem(item);
        if (context == null || packageName.length() == 0) {
            return;
        }
        rememberCreatedIconItem(item, "binding." + phase);
        try {
            Object iconView = iconViewFromBinding(binding);
            bindIconViewToPackage(packageName, iconView, "binding." + phase);
            applyLabelToIconView(context, packageName, iconView, "binding." + phase);
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: binding IconView apply failed for " + packageName
                    + ", phase=" + phase);
            DiagnosticLogger.log(t);
        }
    }

    private static Object iconViewFromBinding(Object binding) {
        if (binding == null) {
            return null;
        }
        Object named = getFieldValueRecursive(binding, "f4374b");
        if (named != null) {
            return named;
        }
        Class<?> type = binding.getClass();
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                try {
                    field.setAccessible(true);
                    Object value = field.get(binding);
                    if (value != null && value.getClass().getName().contains("IconView")) {
                        return value;
                    }
                } catch (Throwable ignored) {
                    // Continue scanning other binding fields.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object getFieldValueRecursive(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static void rememberCreatedIconItem(Object item, String source) {
        if (item == null) {
            return;
        }
        if (item instanceof Iterable) {
            Iterator<?> iterator = ((Iterable<?>) item).iterator();
            while (iterator.hasNext()) {
                rememberCreatedIconItem(iterator.next(), source);
            }
            return;
        }
        if (item.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(item);
            for (int i = 0; i < length; i++) {
                rememberCreatedIconItem(java.lang.reflect.Array.get(item, i), source);
            }
            return;
        }
        String packageName = packageFromIconItem(item);
        if (packageName.length() == 0) {
            return;
        }
        rememberAppItem(packageName, item);
        try {
            Object liveData = XposedHelpers.callMethod(item, "getIcon");
            Object labelLiveData = XposedHelpers.callMethod(item, "getLabel");
            rememberIconLiveData(packageName, liveData);
            rememberLabelLiveData(packageName, labelLiveData);
            applyIconToLiveData(appContext, packageName, liveData);
            applyLabelToLiveData(appContext, packageName, labelLiveData);
            DiagnosticLogger.log("LauncherIconCustomizer: IconItem remembered from " + source
                    + ", package=" + packageName);
        } catch (Throwable ignored) {
            // Non-AppItem IconItem implementations may not carry a mutable icon.
        }
    }

    private static void bindIconViewToPackage(String packageName, Object view, String source) {
        if (packageName == null || packageName.length() == 0 || view == null) {
            return;
        }
        removeIconViewFromOtherPackages(packageName, view);
        synchronized (ICON_VIEW_PACKAGES) {
            ICON_VIEW_PACKAGES.put(view, packageName);
        }
        rememberIconView(packageName, view);
        applyIconToIconView(appContext, packageName, view, source);
        applyLabelToIconView(appContext, packageName, view, source);
        DiagnosticLogger.log("LauncherIconCustomizer: IconView bound source=" + source
                + ", package=" + packageName
                + ", view=" + view.getClass().getName());
    }

    private static void removeIconViewFromOtherPackages(String packageName, Object iconView) {
        synchronized (KNOWN_ICON_VIEWS) {
            Iterator<Map.Entry<String, ArrayList<WeakReference<Object>>>> mapIterator =
                    KNOWN_ICON_VIEWS.entrySet().iterator();
            while (mapIterator.hasNext()) {
                Map.Entry<String, ArrayList<WeakReference<Object>>> entry = mapIterator.next();
                ArrayList<WeakReference<Object>> list = entry.getValue();
                Iterator<WeakReference<Object>> iterator = list.iterator();
                while (iterator.hasNext()) {
                    Object existing = iterator.next().get();
                    if (existing == null || (!entry.getKey().equals(packageName) && existing == iconView)) {
                        iterator.remove();
                    }
                }
                if (list.isEmpty()) {
                    mapIterator.remove();
                }
            }
        }
    }

    private static String mappedPackageForIconView(Object iconView) {
        if (iconView == null) {
            return "";
        }
        synchronized (ICON_VIEW_PACKAGES) {
            String packageName = ICON_VIEW_PACKAGES.get(iconView);
            return packageName != null ? packageName : "";
        }
    }

    private static void rememberIconView(String packageName, Object iconView) {
        if (packageName == null || packageName.length() == 0 || iconView == null) {
            return;
        }
        synchronized (KNOWN_ICON_VIEWS) {
            ArrayList<WeakReference<Object>> list = KNOWN_ICON_VIEWS.get(packageName);
            if (list == null) {
                list = new ArrayList<WeakReference<Object>>();
                KNOWN_ICON_VIEWS.put(packageName, list);
            }
            Iterator<WeakReference<Object>> iterator = list.iterator();
            while (iterator.hasNext()) {
                Object existing = iterator.next().get();
                if (existing == null) {
                    iterator.remove();
                } else if (existing == iconView) {
                    return;
                }
            }
            list.add(new WeakReference<Object>(iconView));
        }
    }

    private static void applyIconToIconView(Context context, String packageName, Object iconView, String source) {
        if (context == null || iconView == null || packageName == null || packageName.length() == 0) {
            return;
        }
        Drawable current = null;
        try {
            Object icon = XposedHelpers.callMethod(iconView, "getIcon");
            if (icon instanceof Drawable) {
                current = (Drawable) icon;
            }
        } catch (Throwable ignored) {
            // Use the standard launcher icon size when the view does not expose the current drawable yet.
        }
        int width = current != null && current.getIntrinsicWidth() > 0
                ? current.getIntrinsicWidth()
                : dp(context, 56);
        int height = current != null && current.getIntrinsicHeight() > 0
                ? current.getIntrinsicHeight()
                : width;
        Bitmap custom = loadCustomBitmap(context, packageName, width, height);
        if (custom == null) {
            return;
        }
        APPLYING_ICON_VIEW.set(Boolean.TRUE);
        try {
            XposedHelpers.callMethod(iconView, "setIcon", newCustomDrawable(context, custom));
            if (iconView instanceof View) {
                ((View) iconView).invalidate();
            }
            DiagnosticLogger.log("LauncherIconCustomizer: binding icon applied for " + packageName
                    + ", source=" + source
                    + ", size=" + width + "x" + height);
        } finally {
            APPLYING_ICON_VIEW.set(Boolean.FALSE);
        }
    }

    private static void applyLabelToIconView(Context context, String packageName, Object iconView, String source) {
        if (context == null || iconView == null || packageName == null || packageName.length() == 0) {
            return;
        }
        LabelState state = loadCustomLabelState(context, packageName);
        if (state == null) {
            clearAppliedLabelFontIfNeeded(iconView, source);
            return;
        }
        try {
            if (state.label != null) {
                XposedHelpers.callMethod(iconView, "setLabel", state.label);
            }
            if (state.color != null) {
                applyLabelColorToIconView(iconView, state.color.intValue());
            }
            applyLabelFontToIconView(context, packageName, iconView, state.font, source);
            if (iconView instanceof View) {
                ((View) iconView).invalidate();
            }
            DiagnosticLogger.log("LauncherIconCustomizer: binding label applied for " + packageName
                    + ", source=" + source
                    + ", label=" + state.label
                    + ", color=" + state.color
                    + ", font=" + describeLabelFont(state.font));
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: binding label apply failed for " + packageName
                    + ", source=" + source);
            DiagnosticLogger.log(t);
        }
    }

    private static void applyLabelColorToIconView(Object iconView, int color) {
        if (iconView instanceof TextView) {
            ((TextView) iconView).setTextColor(color);
            return;
        }
        XposedHelpers.callMethod(iconView, "setTextColor", Integer.valueOf(color));
    }

    private static void applyMappedLabelAppearanceToIconView(Object iconView, String source) {
        String packageName = mappedPackageForIconView(iconView);
        if (packageName.length() == 0) {
            return;
        }
        LabelState state = loadCustomLabelState(appContext, packageName);
        if (state == null) {
            clearAppliedLabelFontIfNeeded(iconView, source);
            return;
        }
        try {
            if (state.color != null) {
                applyLabelColorToIconView(iconView, state.color.intValue());
            }
            applyLabelFontToIconView(appContext, packageName, iconView, state.font, source);
            if (iconView instanceof View) {
                ((View) iconView).invalidate();
            }
            DiagnosticLogger.log("LauncherIconCustomizer: mapped label appearance applied for "
                    + packageName
                    + ", source=" + source
                    + ", color=" + state.color
                    + ", font=" + describeLabelFont(state.font));
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: mapped label appearance apply failed for "
                    + packageName + ", source=" + source);
            DiagnosticLogger.log(t);
        }
    }

    private static void applyLabelFontToIconView(Context context,
                                                 String packageName,
                                                 Object iconView,
                                                 LauncherIconCustomizerStore.LabelFont font,
                                                 String source) {
        if (font == null) {
            clearAppliedLabelFontIfNeeded(iconView, source);
            return;
        }
        if (!(iconView instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) iconView;
        int style = fontStyle(font);
        textView.getPaint().setShader(null);
        textView.setTypeface(typefaceForLabelFont(context, packageName, font, style), style);
        if (font.gradient) {
            applyLabelGradientToTextView(textView, font.gradientStart, font.gradientEnd);
        }
        synchronized (ICON_VIEW_FONT_APPLIED) {
            ICON_VIEW_FONT_APPLIED.put(iconView, Boolean.TRUE);
        }
        textView.invalidate();
    }

    private static void clearAppliedLabelFontIfNeeded(Object iconView, String source) {
        if (iconView == null) {
            return;
        }
        boolean shouldClear;
        synchronized (ICON_VIEW_FONT_APPLIED) {
            shouldClear = ICON_VIEW_FONT_APPLIED.remove(iconView) != null;
        }
        if (!shouldClear || !(iconView instanceof TextView)) {
            return;
        }
        try {
            TextView textView = (TextView) iconView;
            textView.getPaint().setShader(null);
            textView.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            textView.invalidate();
            DiagnosticLogger.log("LauncherIconCustomizer: label font cleared from reused IconView, source=" + source);
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: label font clear failed, source=" + source);
            DiagnosticLogger.log(t);
        }
    }

    private static void applyLabelGradientToTextView(TextView textView, int startColor, int endColor) {
        int width = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
        if (width <= 0) {
            CharSequence text = textView.getText();
            width = Math.max(1, (int) textView.getPaint().measureText(text == null ? "" : text.toString()));
        }
        if (width <= 0) {
            width = Math.max(1, (int) (textView.getTextSize() * 4));
        }
        textView.getPaint().setShader(new LinearGradient(
                0,
                0,
                width,
                0,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
        ));
    }

    private static int fontStyle(LauncherIconCustomizerStore.LabelFont font) {
        if (font == null) {
            return Typeface.NORMAL;
        }
        if (font.bold && font.italic) {
            return Typeface.BOLD_ITALIC;
        }
        if (font.bold) {
            return Typeface.BOLD;
        }
        if (font.italic) {
            return Typeface.ITALIC;
        }
        return Typeface.NORMAL;
    }

    private static Typeface typefaceForLabelFont(Context context,
                                                 String packageName,
                                                 LauncherIconCustomizerStore.LabelFont font,
                                                 int style) {
        Typeface base = null;
        if (font != null && font.useFile) {
            base = loadTypefaceFromProvider(context, packageName, font);
        }
        if (base == null) {
            if (font == null || LauncherIconCustomizerStore.FONT_FAMILY_DEFAULT.equals(font.family)) {
                base = Typeface.DEFAULT;
            } else {
                base = Typeface.create(font.family, Typeface.NORMAL);
            }
        }
        return Typeface.create(base, style);
    }

    private static Typeface loadTypefaceFromProvider(Context context,
                                                     String packageName,
                                                     LauncherIconCustomizerStore.LabelFont font) {
        if (context == null
                || packageName == null || packageName.length() == 0
                || font == null || !font.useFile) {
            return null;
        }
        String key = packageName + "#" + font.fileUpdatedAt;
        synchronized (TYPEFACE_CACHE) {
            TypefaceCacheEntry entry = TYPEFACE_CACHE.get(key);
            if (entry != null) {
                return entry.typeface;
            }
        }
        InputStream input = null;
        FileOutputStream output = null;
        try {
            File cacheFile = new File(context.getCacheDir(), "launcher-font-" + packageName.replace('.', '_') + ".tmp");
            input = context.getContentResolver().openInputStream(LauncherIconCustomizerStore.fontUri(packageName));
            if (input == null) {
                return null;
            }
            output = new FileOutputStream(cacheFile, false);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            Typeface typeface = Typeface.createFromFile(cacheFile);
            synchronized (TYPEFACE_CACHE) {
                TYPEFACE_CACHE.put(key, new TypefaceCacheEntry(typeface));
            }
            return typeface;
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: load typeface failed for " + packageName
                    + ", font=" + describeLabelFont(font));
            DiagnosticLogger.log(t);
            return null;
        } finally {
            closeQuietly(output);
            closeQuietly(input);
        }
    }

    private static String describeLabelFont(LauncherIconCustomizerStore.LabelFont font) {
        if (font == null) {
            return "null";
        }
        return font.family
                + ",bold=" + font.bold
                + ",italic=" + font.italic
                + ",gradient=" + font.gradient
                + ",useFile=" + font.useFile
                + ",fileLabel=" + font.fileLabel;
    }

    private static Drawable customDrawableForIconView(Context context, String packageName, Drawable original) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        int width = original != null && original.getIntrinsicWidth() > 0
                ? original.getIntrinsicWidth()
                : dp(context, 56);
        int height = original != null && original.getIntrinsicHeight() > 0
                ? original.getIntrinsicHeight()
                : width;
        Bitmap custom = loadCustomBitmap(context, packageName, width, height);
        return custom != null ? newCustomDrawable(context, custom) : null;
    }

    private static void rememberAppItem(String packageName, Object appItem) {
        if (packageName == null || packageName.length() == 0 || appItem == null) {
            return;
        }
        synchronized (KNOWN_APP_ITEMS) {
            ArrayList<WeakReference<Object>> list = KNOWN_APP_ITEMS.get(packageName);
            if (list == null) {
                list = new ArrayList<WeakReference<Object>>();
                KNOWN_APP_ITEMS.put(packageName, list);
            }
            Iterator<WeakReference<Object>> iterator = list.iterator();
            while (iterator.hasNext()) {
                Object existing = iterator.next().get();
                if (existing == null) {
                    iterator.remove();
                } else if (existing == appItem) {
                    return;
                }
            }
            list.add(new WeakReference<Object>(appItem));
        }
    }

    private static void rememberIconLiveData(String packageName, Object liveData) {
        if (packageName == null || packageName.length() == 0 || liveData == null) {
            return;
        }
        synchronized (ICON_LIVEDATA_PACKAGES) {
            ICON_LIVEDATA_PACKAGES.put(liveData, packageName);
        }
    }

    private static void rememberLabelLiveData(String packageName, Object liveData) {
        if (packageName == null || packageName.length() == 0 || liveData == null) {
            return;
        }
        synchronized (LABEL_LIVEDATA_PACKAGES) {
            LABEL_LIVEDATA_PACKAGES.put(liveData, packageName);
        }
    }

    private static String packageFromComponentString(String componentName) {
        if (componentName == null || componentName.length() == 0) {
            return "";
        }
        ComponentName flattened = ComponentName.unflattenFromString(componentName);
        if (flattened != null) {
            return flattened.getPackageName();
        }
        int brace = componentName.indexOf('{');
        int slash = componentName.indexOf('/');
        if (brace >= 0 && slash > brace) {
            return componentName.substring(brace + 1, slash);
        }
        if (slash > 0) {
            return componentName.substring(0, slash);
        }
        return componentName;
    }

    private static Bitmap loadCustomBitmap(Context context, String packageName, int width, int height) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        if (width <= 0) {
            width = dp(context, 56);
        }
        if (height <= 0) {
            height = width;
        }
        String key = packageName + "#" + width + "x" + height;
        long now = SystemClock.uptimeMillis();
        synchronized (CACHE) {
            CacheEntry entry = CACHE.get(key);
            if (entry != null && now - entry.loadedAt < CACHE_TTL_MS) {
                return entry.bitmap;
            }
        }
        Boolean providerHasIcon = providerHasIcon(context, packageName);
        Bitmap bitmap = null;
        if (!Boolean.FALSE.equals(providerHasIcon)) {
            bitmap = LauncherIconCustomizerStore.loadIconFromProvider(context, packageName, width, height);
        }
        if (bitmap != null) {
            DiagnosticLogger.log("LauncherIconCustomizer: loaded custom icon for " + packageName
                    + ", size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
        } else if (Boolean.TRUE.equals(providerHasIcon)) {
            DiagnosticLogger.log("LauncherIconCustomizer: provider reported icon but decode returned null for " + packageName);
        }
        synchronized (CACHE) {
            CACHE.put(key, new CacheEntry(bitmap, now));
        }
        return bitmap;
    }

    private static String loadCustomLabel(Context context, String packageName) {
        LabelState state = loadCustomLabelState(context, packageName);
        return state != null ? state.label : null;
    }

    private static Integer loadCustomLabelColor(Context context, String packageName) {
        LabelState state = loadCustomLabelState(context, packageName);
        return state != null ? state.color : null;
    }

    private static LauncherIconCustomizerStore.LabelFont loadCustomLabelFont(Context context, String packageName) {
        LabelState state = loadCustomLabelState(context, packageName);
        return state != null ? state.font : null;
    }

    private static LabelState loadCustomLabelState(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        long now = SystemClock.uptimeMillis();
        synchronized (LABEL_CACHE) {
            LabelCacheEntry entry = LABEL_CACHE.get(packageName);
            if (entry != null && now - entry.loadedAt < CACHE_TTL_MS) {
                return new LabelState(entry.label, entry.color, entry.font);
            }
        }
        LabelState state = providerCustomLabelState(context, packageName);
        synchronized (LABEL_CACHE) {
            LABEL_CACHE.put(packageName, new LabelCacheEntry(
                    state != null ? state.label : null,
                    state != null ? state.color : null,
                    state != null ? state.font : null,
                    now
            ));
        }
        return state;
    }

    private static LabelState providerCustomLabelState(Context context, String packageName) {
        try {
            Bundle extras = new Bundle();
            extras.putString(LauncherIconCustomizerStore.EXTRA_PACKAGE, packageName);
            Bundle result = context.getContentResolver().call(
                    LauncherIconCustomizerStore.BASE_URI,
                    LauncherIconCustomizerStore.METHOD_LABEL,
                    null,
                    extras
            );
            if (result == null) {
                return null;
            }
            String label = null;
            if (result.getBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL, false)) {
                label = result.getString(LauncherIconCustomizerStore.EXTRA_LABEL);
                if (label != null) {
                    label = label.trim();
                }
                if (label == null || label.length() == 0) {
                    label = null;
                }
            }
            Integer labelColor = result.getBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL_COLOR, false)
                    ? Integer.valueOf(result.getInt(LauncherIconCustomizerStore.EXTRA_LABEL_COLOR))
                    : null;
            LauncherIconCustomizerStore.LabelFont labelFont = null;
            if (result.getBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL_FONT, false)) {
                labelFont = new LauncherIconCustomizerStore.LabelFont(
                        result.getString(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_FAMILY),
                        result.getBoolean(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_BOLD, false),
                        result.getBoolean(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_ITALIC, false),
                        result.getBoolean(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_GRADIENT, false),
                        result.getInt(
                                LauncherIconCustomizerStore.EXTRA_LABEL_FONT_GRADIENT_START,
                                LauncherIconCustomizerStore.DEFAULT_GRADIENT_START
                        ),
                        result.getInt(
                                LauncherIconCustomizerStore.EXTRA_LABEL_FONT_GRADIENT_END,
                                LauncherIconCustomizerStore.DEFAULT_GRADIENT_END
                        ),
                        result.getBoolean(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_USE_FILE, false),
                        result.getString(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_FILE_LABEL),
                        result.getLong(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_FILE_UPDATED_AT, 0L)
                );
                if (!labelFont.hasCustomEffect()) {
                    labelFont = null;
                }
            }
            if (label == null && labelColor == null && labelFont == null) {
                return null;
            }
            DiagnosticLogger.log("LauncherIconCustomizer: provider custom label for " + packageName
                    + "=" + label + ", color=" + labelColor + ", font=" + describeLabelFont(labelFont));
            return new LabelState(label, labelColor, labelFont);
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: provider label check failed for " + packageName);
            DiagnosticLogger.log(t);
            return null;
        }
    }

    private static Drawable newCustomDrawable(Context context, Bitmap bitmap) {
        if (context == null || bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    private static Boolean providerHasIcon(Context context, String packageName) {
        try {
            Bundle extras = new Bundle();
            extras.putString(LauncherIconCustomizerStore.EXTRA_PACKAGE, packageName);
            Bundle result = context.getContentResolver().call(
                    LauncherIconCustomizerStore.BASE_URI,
                    LauncherIconCustomizerStore.METHOD_HAS,
                    null,
                    extras
            );
            if (result == null) {
                DiagnosticLogger.log("LauncherIconCustomizer: provider has returned null for " + packageName);
                return null;
            }
            boolean exists = result.getBoolean(LauncherIconCustomizerStore.EXTRA_EXISTS, false);
            if (exists) {
                DiagnosticLogger.log("LauncherIconCustomizer: provider has icon for " + packageName
                        + ", updatedAt=" + result.getLong(LauncherIconCustomizerStore.EXTRA_UPDATED_AT, 0L));
            }
            return Boolean.valueOf(exists);
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: provider has check failed for " + packageName);
            DiagnosticLogger.log(t);
            return null;
        }
    }

    private static boolean applyIconToLiveData(Context context, String packageName, Object liveData) {
        if (context == null || liveData == null || packageName == null || packageName.length() == 0) {
            return false;
        }
        rememberIconLiveData(packageName, liveData);
        Bitmap custom = loadCustomBitmap(context, packageName, dp(context, 56), dp(context, 56));
        if (custom == null) {
            return false;
        }
        try {
            Drawable drawable = newCustomDrawable(context, custom);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                XposedHelpers.callMethod(liveData, "setValue", drawable);
            } else {
                XposedHelpers.callMethod(liveData, "postValue", drawable);
            }
            DiagnosticLogger.log("LauncherIconCustomizer: LiveData icon applied for " + packageName);
            return true;
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: LiveData icon apply failed for " + packageName);
            DiagnosticLogger.log(t);
            return false;
        }
    }

    private static boolean applyLabelToLiveData(Context context, String packageName, Object liveData) {
        if (context == null || liveData == null || packageName == null || packageName.length() == 0) {
            return false;
        }
        rememberLabelLiveData(packageName, liveData);
        String customLabel = loadCustomLabel(context, packageName);
        if (customLabel == null) {
            return false;
        }
        try {
            Object current = XposedHelpers.callMethod(liveData, "getValue");
            if (customLabel.contentEquals(stringValue(current))) {
                return true;
            }
        } catch (Throwable ignored) {
            // Some lifecycle builds may not expose getValue through reflection.
        }
        try {
            APPLYING_LABEL_LIVEDATA.set(Boolean.TRUE);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                XposedHelpers.callMethod(liveData, "setValue", customLabel);
            } else {
                XposedHelpers.callMethod(liveData, "postValue", customLabel);
            }
            DiagnosticLogger.log("LauncherIconCustomizer: LiveData label applied for " + packageName
                    + ", label=" + customLabel);
            return true;
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: LiveData label apply failed for " + packageName);
            DiagnosticLogger.log(t);
            return false;
        } finally {
            APPLYING_LABEL_LIVEDATA.set(Boolean.FALSE);
        }
    }

    private static void refreshKnownAppItems(Context context, String changedPackageName) {
        if (context == null) {
            return;
        }
        int packageBuckets = 0;
        int refs = 0;
        int applied = 0;
        int stale = 0;
        synchronized (KNOWN_APP_ITEMS) {
            Iterator<Map.Entry<String, ArrayList<WeakReference<Object>>>> mapIterator =
                    KNOWN_APP_ITEMS.entrySet().iterator();
            while (mapIterator.hasNext()) {
                Map.Entry<String, ArrayList<WeakReference<Object>>> entry = mapIterator.next();
                String packageName = entry.getKey();
                if (changedPackageName != null && changedPackageName.length() != 0
                        && !changedPackageName.equals(packageName)) {
                    continue;
                }
                packageBuckets++;
                ArrayList<WeakReference<Object>> list = entry.getValue();
                Iterator<WeakReference<Object>> iterator = list.iterator();
                while (iterator.hasNext()) {
                    refs++;
                    Object appItem = iterator.next().get();
                    if (appItem == null) {
                        iterator.remove();
                        stale++;
                        continue;
                    }
                    try {
                        Object liveData = XposedHelpers.callMethod(appItem, "getIcon");
                        Object labelLiveData = XposedHelpers.callMethod(appItem, "getLabel");
                        rememberIconLiveData(packageName, liveData);
                        rememberLabelLiveData(packageName, labelLiveData);
                        if (applyIconToLiveData(context, packageName, liveData)) {
                            applied++;
                        }
                        if (applyLabelToLiveData(context, packageName, labelLiveData)) {
                            applied++;
                        }
                    } catch (Throwable t) {
                        DiagnosticLogger.log("LauncherIconCustomizer: known AppItem refresh failed for " + packageName);
                        DiagnosticLogger.log(t);
                    }
                }
                if (list.isEmpty()) {
                    mapIterator.remove();
                }
            }
        }
        DiagnosticLogger.log("LauncherIconCustomizer: known AppItem refresh changedPackage="
                + changedPackageName
                + ", packages=" + packageBuckets
                + ", refs=" + refs
                + ", stale=" + stale
                + ", applied=" + applied);
        DiagnosticLogger.log("LauncherIconCustomizer: direct IconView refresh skipped to avoid recycled folder view bleed");
    }

    private static void refreshKnownIconViews(Context context, String changedPackageName) {
        if (context == null) {
            return;
        }
        int packageBuckets = 0;
        int refs = 0;
        int stale = 0;
        int applied = 0;
        synchronized (KNOWN_ICON_VIEWS) {
            Iterator<Map.Entry<String, ArrayList<WeakReference<Object>>>> mapIterator =
                    KNOWN_ICON_VIEWS.entrySet().iterator();
            while (mapIterator.hasNext()) {
                Map.Entry<String, ArrayList<WeakReference<Object>>> entry = mapIterator.next();
                String packageName = entry.getKey();
                if (changedPackageName != null && changedPackageName.length() != 0
                        && !changedPackageName.equals(packageName)) {
                    continue;
                }
                packageBuckets++;
                ArrayList<WeakReference<Object>> list = entry.getValue();
                Iterator<WeakReference<Object>> iterator = list.iterator();
                while (iterator.hasNext()) {
                    refs++;
                    Object iconView = iterator.next().get();
                    if (iconView == null) {
                        iterator.remove();
                        stale++;
                        continue;
                    }
                    String mappedPackageName = mappedPackageForIconView(iconView);
                    if (!packageName.equals(mappedPackageName)) {
                        iterator.remove();
                        stale++;
                        continue;
                    }
                    try {
                        applyIconToIconView(context, packageName, iconView, "knownIconView.refresh");
                        applyLabelToIconView(context, packageName, iconView, "knownIconView.refresh");
                        applied++;
                    } catch (Throwable t) {
                        DiagnosticLogger.log("LauncherIconCustomizer: known IconView refresh failed for " + packageName);
                        DiagnosticLogger.log(t);
                    }
                }
                if (list.isEmpty()) {
                    mapIterator.remove();
                }
            }
        }
        DiagnosticLogger.log("LauncherIconCustomizer: known IconView refresh changedPackage="
                + changedPackageName
                + ", packages=" + packageBuckets
                + ", refs=" + refs
                + ", stale=" + stale
                + ", attempted=" + applied);
    }

    private static void scheduleKnownAppItemRefresh(final Context context) {
        if (context == null) {
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        long[] delays = new long[]{0L, 1200L};
        for (int i = 0; i < delays.length; i++) {
            final long delay = delays[i];
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    runScheduledRefresh(context, delay);
                }
            }, delay);
        }
    }

    private static void runScheduledRefresh(Context context, long delayMs) {
        synchronized (CACHE) {
            CACHE.clear();
        }
        synchronized (LABEL_CACHE) {
            LABEL_CACHE.clear();
        }
        synchronized (TYPEFACE_CACHE) {
            TYPEFACE_CACHE.clear();
        }
        ArrayList<String> packages = listCustomIconPackages(context);
        DiagnosticLogger.log("LauncherIconCustomizer: scheduled refresh delayMs=" + delayMs
                + ", customPackages=" + packages);
        for (int i = 0; i < packages.size(); i++) {
            refreshKnownAppItems(context, packages.get(i));
        }
    }

    private static ArrayList<String> listCustomIconPackages(Context context) {
        ArrayList<String> packages = new ArrayList<String>();
        if (context == null) {
            return packages;
        }
        try {
            Bundle result = context.getContentResolver().call(
                    LauncherIconCustomizerStore.BASE_URI,
                    LauncherIconCustomizerStore.METHOD_LIST,
                    null,
                    null
            );
            if (result == null) {
                DiagnosticLogger.log("LauncherIconCustomizer: provider list returned null");
                return packages;
            }
            ArrayList<String> providerPackages =
                    result.getStringArrayList(LauncherIconCustomizerStore.EXTRA_PACKAGES);
            if (providerPackages != null) {
                packages.addAll(providerPackages);
            }
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: provider list failed");
            DiagnosticLogger.log(t);
        }
        return packages;
    }

    private static void registerRefreshReceiver(Context context) {
        if (context == null || receiverRegistered) {
            return;
        }
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    synchronized (CACHE) {
                        CACHE.clear();
                    }
                    synchronized (LABEL_CACHE) {
                        LABEL_CACHE.clear();
                    }
                    synchronized (TYPEFACE_CACHE) {
                        TYPEFACE_CACHE.clear();
                    }
                    String packageName = intent != null
                            ? intent.getStringExtra(LauncherIconCustomizerStore.EXTRA_PACKAGE)
                            : "";
                    if (packageName != null && packageName.length() != 0) {
                        refreshKnownAppItems(context, packageName);
                    }
                    scheduleKnownAppItemRefresh(context);
                    DiagnosticLogger.log("LauncherIconCustomizer: cache cleared by change broadcast, package=" + packageName);
                }
            };
            IntentFilter filter = new IntentFilter(LauncherIconCustomizerStore.ACTION_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            receiverRegistered = true;
            DiagnosticLogger.log("LauncherIconCustomizer: refresh receiver registered");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: refresh receiver failed");
            DiagnosticLogger.log(t);
        }
    }

    private static void registerProviderObserver(Context context) {
        if (context == null || observerRegistered) {
            return;
        }
        try {
            context.getContentResolver().registerContentObserver(
                    LauncherIconCustomizerStore.BASE_URI,
                    true,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            synchronized (CACHE) {
                                CACHE.clear();
                            }
                            synchronized (LABEL_CACHE) {
                                LABEL_CACHE.clear();
                            }
                            synchronized (TYPEFACE_CACHE) {
                                TYPEFACE_CACHE.clear();
                            }
                            String packageName = packageNameFromIconUri(uri);
                            Context refreshContext = appContext != null ? appContext : context;
                            if (packageName != null && packageName.length() != 0) {
                                refreshKnownAppItems(refreshContext, packageName);
                            }
                            scheduleKnownAppItemRefresh(refreshContext);
                            DiagnosticLogger.log("LauncherIconCustomizer: cache cleared by provider observer, uri="
                                    + uri + ", package=" + packageName);
                        }

                        @Override
                        public void onChange(boolean selfChange) {
                            onChange(selfChange, LauncherIconCustomizerStore.BASE_URI);
                        }
                    }
            );
            observerRegistered = true;
            DiagnosticLogger.log("LauncherIconCustomizer: provider observer registered");
        } catch (Throwable t) {
            DiagnosticLogger.log("LauncherIconCustomizer: provider observer failed");
            DiagnosticLogger.log(t);
        }
    }

    private static String packageNameFromIconUri(Uri uri) {
        if (uri == null) {
            return "";
        }
        try {
            if (!LauncherIconCustomizerStore.AUTHORITY.equals(uri.getAuthority())) {
                return "";
            }
            if (uri.getPathSegments().size() >= 2
                    && "icon".equals(uri.getPathSegments().get(0))) {
                return uri.getPathSegments().get(1);
            }
        } catch (Throwable ignored) {
            return "";
        }
        return "";
    }

    private static int widthOf(Bitmap bitmap) {
        return bitmap != null ? bitmap.getWidth() : 0;
    }

    private static int heightOf(Bitmap bitmap) {
        return bitmap != null ? bitmap.getHeight() : 0;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void closeQuietly(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            if (closeable instanceof InputStream) {
                ((InputStream) closeable).close();
            } else if (closeable instanceof FileOutputStream) {
                ((FileOutputStream) closeable).close();
            }
        } catch (Throwable ignored) {
            // Ignore cleanup failure.
        }
    }

    private static final class CacheEntry {
        final Bitmap bitmap;
        final long loadedAt;

        CacheEntry(Bitmap bitmap, long loadedAt) {
            this.bitmap = bitmap;
            this.loadedAt = loadedAt;
        }
    }

    private static final class TypefaceCacheEntry {
        final Typeface typeface;

        TypefaceCacheEntry(Typeface typeface) {
            this.typeface = typeface;
        }
    }

    private static final class LabelCacheEntry {
        final String label;
        final Integer color;
        final LauncherIconCustomizerStore.LabelFont font;
        final long loadedAt;

        LabelCacheEntry(String label, Integer color, LauncherIconCustomizerStore.LabelFont font, long loadedAt) {
            this.label = label;
            this.color = color;
            this.font = font;
            this.loadedAt = loadedAt;
        }
    }

    private static final class LabelState {
        final String label;
        final Integer color;
        final LauncherIconCustomizerStore.LabelFont font;

        LabelState(String label, Integer color, LauncherIconCustomizerStore.LabelFont font) {
            this.label = label;
            this.color = color;
            this.font = font;
        }
    }
}
