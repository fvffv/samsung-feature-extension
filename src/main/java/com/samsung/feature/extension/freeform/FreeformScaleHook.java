package com.samsung.feature.extension.freeform;

import android.content.*;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.*;
import com.samsung.feature.extension.FreeformScaleSettingsProvider;
import java.lang.reflect.*;
import java.util.function.Consumer;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Changes only resolved per-task freeform density, never the user's display setting. */
public final class FreeformScaleHook implements IXposedHookLoadPackage {
    private static volatile boolean enabled;
    private static volatile String fault="";
    private static volatile long changes;
    private static volatile int lastDensity;
    private static final ThreadLocal<Integer> scaling=new ThreadLocal<>();
    private static Handler worker;
    private static Context context;
    private static Object atm;
    private static Method compute;
    private static final Runnable poll=new Runnable() {
        public void run() {
            reload();
            PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if(power!=null&&power.isInteractive()) worker.postDelayed(this,30000);
        }
    };
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if(!"android".equals(p.packageName)||!"android".equals(p.processName)) return;
        try {
            Class<?> fragment=Class.forName("com.android.server.wm.TaskFragment",false,p.classLoader);
            for(Method m:fragment.getDeclaredMethods()) {
                Class<?>[] t=m.getParameterTypes();
                if(m.getName().equals("computeConfigResourceOverrides")&&m.getReturnType()==void.class
                    &&t.length==3&&t[0]==Configuration.class&&t[1]==Configuration.class) {
                    if(compute!=null) throw new IllegalStateException("Ambiguous configuration method");
                    compute=m;
                }
            }
            if(compute==null) throw new NoSuchMethodException("TaskFragment.computeConfigResourceOverrides");
            XposedBridge.hookMethod(compute,new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if(!enabled||p.args[2]!=null) return; // Preserve existing size-compat policies.
                    try {
                        Object task=XposedHelpers.callMethod(p.thisObject,"asTask");
                        if(task==null||!Boolean.TRUE.equals(XposedHelpers.callMethod(task,"isLeafTask"))) return;
                        if(((Integer)XposedHelpers.callMethod(task,"getDisplayId"))!=0
                            ||((Integer)XposedHelpers.callMethod(task,"getActivityType"))!=1) return;
                        Object root=XposedHelpers.callMethod(task,"getRootTask");
                        if(Boolean.TRUE.equals(XposedHelpers.callMethod(root,"isDeskRootTask"))) return;
                        Configuration out=(Configuration)p.args[0],parent=(Configuration)p.args[1];
                        int mode=out.windowConfiguration.getWindowingMode();
                        if(mode==0) mode=parent.windowConfiguration.getWindowingMode();
                        if(mode!=5) return;
                        Rect bounds=out.windowConfiguration.getBounds();
                        if(bounds.isEmpty()) bounds=parent.windowConfiguration.getBounds();
                        Object display=XposedHelpers.callMethod(task,"getDisplayContent");
                        if(display==null) return;
                        Configuration base=(Configuration)XposedHelpers.callMethod(display,"getConfiguration");
                        Rect full=base.windowConfiguration.getBounds();
                        int dpi=FreeformScaleMath.density(base.densityDpi,full.width(),full.height(),bounds.width(),bounds.height());
                        if(dpi<=0) return;
                        p.setObjectExtra("sfeScaling",Boolean.TRUE);
                        p.setObjectExtra("sfePrevious",scaling.get()); scaling.set(dpi);
                        out.densityDpi=dpi;
                        out.screenWidthDp=0; out.screenHeightDp=0; out.smallestScreenWidthDp=0; out.screenLayout=0;
                        lastDensity=dpi; changes++;
                    } catch(Throwable t) { fail("配置接口不兼容",t); }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if(Boolean.TRUE.equals(p.getObjectExtra("sfeScaling"))) {
                        Integer previous=(Integer)p.getObjectExtra("sfePrevious");
                        if(previous==null) scaling.remove(); else scaling.set(previous);
                    }
                }
            });
            // Samsung may apply a fixed density reduction inside the same calculation.
            // Suppress that reduction only for our scoped calculation, to avoid double scaling.
            Class<?> utils=Class.forName("com.samsung.android.multiwindow.MultiWindowUtils",false,p.classLoader);
            XposedBridge.hookAllMethods(utils,"needToUpdateDensity",new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if(scaling.get()!=null) p.setResult(false);
                }
            });
            Class<?> service=Class.forName("com.android.server.wm.ActivityTaskManagerService",false,p.classLoader);
            Method ready=service.getDeclaredMethod("onSystemReady");
            XposedBridge.hookMethod(ready,new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { start(p.thisObject); }
            });
            XposedBridge.log("[SFE-FreeformScale] hooks installed");
        } catch(Throwable t) { fail("此固件不支持小窗缩放接口",t); }
    }
    private static synchronized void start(Object service) {
        if(worker!=null) return;
        try {
            atm=service; context=(Context)XposedHelpers.getObjectField(service,"mContext");
            HandlerThread thread=new HandlerThread("SFE-freeform-config"); thread.start(); worker=new Handler(thread.getLooper());
            worker.post(poll);
            context.getContentResolver().registerContentObserver(FreeformScaleSettingsProvider.URI,false,
                new ContentObserver(worker) { @Override public void onChange(boolean self) { reload(); } });
            IntentFilter screen=new IntentFilter(); screen.addAction(Intent.ACTION_SCREEN_ON); screen.addAction(Intent.ACTION_SCREEN_OFF);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c,Intent i) {
                    worker.removeCallbacks(poll);
                    if(Intent.ACTION_SCREEN_ON.equals(i.getAction())) worker.post(poll);
                }
            },screen,Context.RECEIVER_EXPORTED);
        } catch(Throwable t) { fail("配置同步初始化失败",t); }
    }
    private static void reload() {
        try {
            // No ContentProvider/Binder calls from the window-manager locked hook.
            if(!"1".equals(XposedHelpers.callStaticMethod(Class.forName("android.os.SystemProperties"),"get","sys.boot_completed","0"))) return;
            Bundle b=context.getContentResolver().call(FreeformScaleSettingsProvider.URI,"get",null,null);
            if(b==null) return;
            boolean next=fault.isEmpty()&&b.getBoolean("enabled",false);
            if(next!=enabled) { enabled=next; refreshTasks(); }
            Bundle r=new Bundle(); r.putLong("changes",changes); r.putInt("density",lastDensity);
            r.putString("message",!fault.isEmpty()?fault:enabled?"小窗等比例缩放已开启":"系统接口已连接，缩放关闭");
            context.getContentResolver().call(FreeformScaleSettingsProvider.URI,"status",null,r);
        } catch(Throwable t) { android.util.Log.w("SFE-FreeformScale","Configuration not available",t); }
    }
    private static void refreshTasks() {
        try {
            Handler h=(Handler)XposedHelpers.getObjectField(atm,"mH");
            h.post(()->{
                try {
                    Object lock=XposedHelpers.getObjectField(atm,"mGlobalLock");
                    synchronized(lock) {
                        Object root=XposedHelpers.getObjectField(atm,"mRootWindowContainer");
                        Consumer<Object> refresh=task->{
                            if(Boolean.TRUE.equals(XposedHelpers.callMethod(task,"isLeafTask"))
                                &&Boolean.TRUE.equals(XposedHelpers.callMethod(task,"inFreeformWindowingMode"))
                                &&((Integer)XposedHelpers.callMethod(task,"getDisplayId"))==0) {
                                Object parent=XposedHelpers.callMethod(task,"getParent");
                                if(parent!=null) XposedHelpers.callMethod(task,"onConfigurationChanged",
                                    XposedHelpers.callMethod(parent,"getConfiguration"));
                            }
                        };
                        XposedHelpers.callMethod(root,"forAllTasks",refresh,true);
                    }
                } catch(Throwable t) { android.util.Log.w("SFE-FreeformScale","Reopen or resize existing windows",t); }
            });
        } catch(Throwable t) { android.util.Log.w("SFE-FreeformScale","Refresh unavailable",t); }
    }
    private static void fail(String reason,Throwable t) {
        enabled=false; fault=reason+"："+t.getClass().getSimpleName();
        XposedBridge.log("[SFE-FreeformScale] "+fault+" "+t);
    }
}

