package com.samsung.feature.extension.icons;

import android.graphics.drawable.Drawable;
import java.lang.reflect.Method;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Covers app-drawer, home and custom icons at the final drawable binding point. */
public final class LauncherIconShapeHook implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if(!"com.sec.android.app.launcher".equals(p.packageName)
            ||!"com.sec.android.app.launcher".equals(p.processName)) return;
        try {
            Class<?> view=Class.forName("com.honeyspace.ui.common.iconview.IconViewImpl",false,p.classLoader);
            Method bind=view.getDeclaredMethod("setCompoundDrawable",Drawable.class);
            XposedBridge.hookMethod(bind,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try { p.args[0]=SystemIconMask.normalize((Drawable)p.args[0]); }
                    catch(Throwable t) { android.util.Log.w("SFE-IconShape","Icon kept unchanged",t); }
                }
            });
            android.util.Log.i("SFE-IconShape","System icon-mask fallback installed");
        } catch(Throwable t) {
            android.util.Log.w("SFE-IconShape","Unsupported launcher; icons kept unchanged",t);
        }
    }
}

