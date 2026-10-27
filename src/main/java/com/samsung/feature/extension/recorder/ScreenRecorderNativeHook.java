package com.samsung.feature.extension.recorder;

import android.content.*;
import android.graphics.Point;
import android.hardware.display.VirtualDisplayConfig;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Adds Original to Samsung Capture's own Video quality selector. No background polling. */
public final class ScreenRecorderNativeHook implements IXposedHookLoadPackage {
    private static final String TARGET="com.samsung.android.app.smartcapture";
    private static final String ROOT=TARGET+".screenrecorder.";
    private static final String PREFS="screenrecorder_preferences",KEY="sfe_native_quality";
    private static final Map<Object,NativeRecordingProfile> PLANS=Collections.synchronizedMap(new WeakHashMap<Object,NativeRecordingProfile>());
    private static final ThreadLocal<NativeRecordingProfile> ENCODING=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> UI=new ThreadLocal<>();
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); }
    private static boolean original(Context c) { return c!=null&&prefs(c).getBoolean(KEY,false); }
    private static String label(Context c) {
        return c.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh")?"原画":"Original";
    }
    private static Point size(Context c) {
        Point p=new Point(); ((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p); return p;
    }
    private static float maxFps(Context c) {
        Display display=((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        float max=display.getRefreshRate();
        for(Display.Mode mode:display.getSupportedModes()) max=Math.max(max,mode.getRefreshRate());
        return max;
    }
    private static Method method(Class<?> c,String name,Class<?>...types) throws Exception {
        Method m=c.getDeclaredMethod(name,types); m.setAccessible(true); return m;
    }
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if(!TARGET.equals(p.packageName)) return;
        try {
            Class<?> settings=Class.forName("com.samsung.android.app.settings.SettingsFragment",false,p.classLoader);
            for(Method m:settings.getDeclaredMethods()) {
                if(m.getName().equals("onCreate")||m.getName().equals("onResume")||m.getName().equals("updateScreenRecorderSettings"))
                    XposedBridge.hookMethod(m,new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if(p.getThrowable()==null) installOption(p.thisObject);
                        }
                    });
                if(m.getName().equals("onPreferenceChange")&&m.getParameterTypes().length==2)
                    XposedBridge.hookMethod(m,new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                Object pref=XposedHelpers.getObjectField(p.thisObject,"mScreenRecorderVideoSettings");
                                if(p.args[0]!=pref) return;
                                Context c=(Context)XposedHelpers.getObjectField(p.thisObject,"mContext");
                                if(label(c).contentEquals(String.valueOf(p.args[1]))) {
                                    if(Boolean.TRUE.equals(UI.get())) { p.setResult(false); return; }
                                    prefs(c).edit().putBoolean(KEY,true).apply();
                                    UI.set(true);
                                    try { select(pref,3); XposedHelpers.callMethod(pref,"setSummary",label(c)); }
                                    finally { UI.remove(); }
                                    p.setResult(true);
                                } else if(!Boolean.TRUE.equals(UI.get())) prefs(c).edit().putBoolean(KEY,false).apply();
                            } catch(Throwable t) { log("quality selection",t); }
                        }
                    });
            }
            // Choosing a standard preset in Samsung's quick panel also exits Original mode.
            Class<?> preferences=Class.forName(ROOT+"util.ScreenRecorderSharePreference",false,p.classLoader);
            XposedBridge.hookMethod(method(preferences,"saveStringState",Context.class,String.class,String.class),new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if("video_quality".equals(p.args[1])&&!Boolean.TRUE.equals(UI.get()))
                        prefs((Context)p.args[0]).edit().putBoolean(KEY,false).apply();
                }
            });
            Class<?> rc=Class.forName(ROOT+"recorder.ScreenRecorderContext",false,p.classLoader);
            XposedBridge.hookMethod(method(rc,"setRecordingResolutionAndBitrate"),new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Context c=(Context)XposedHelpers.getObjectField(p.thisObject,"context");
                    if(!original(c)) return;
                    Point full=size(c);
                    XposedHelpers.setObjectField(p.thisObject,"screenSize",full);
                    XposedHelpers.setIntField(p.thisObject,"recordingResolution",Math.min(full.x,full.y));
                    XposedHelpers.setIntField(p.thisObject,"displayFrameRate",(int)maxFps(c));
                }
            });
            Class<?> recorder=Class.forName(ROOT+"recorder.ScreenRecorder",false,p.classLoader);
            XposedBridge.hookMethod(method(recorder,"prepareScreenSize",Point.class),new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Context c=(Context)XposedHelpers.getObjectField(p.thisObject,"mContext");
                    if(!original(c)) return;
                    Point full=size(c);
                    XposedHelpers.setIntField(p.thisObject,"mRecordWidth",full.x);
                    XposedHelpers.setIntField(p.thisObject,"mRecordHeight",full.y); p.setResult(null);
                }
            });
            Class<?> mp4=Class.forName(ROOT+"recorder.core.MP4Recorder",false,p.classLoader);
            XposedBridge.hookMethod(method(mp4,"initVideo"),new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Context c=(Context)XposedHelpers.getObjectField(p.thisObject,"mContext");
                    if(!original(c)) return;
                    try {
                        int width=XposedHelpers.getIntField(p.thisObject,"mWidth");
                        int height=XposedHelpers.getIntField(p.thisObject,"mHeight");
                        Class<?> utils=Class.forName(ROOT+"util.ScreenRecorderUtils",false,p.thisObject.getClass().getClassLoader());
                        if(Boolean.TRUE.equals(XposedHelpers.callStaticMethod(utils,"isPartialAreaRecording"))) {
                            android.graphics.RectF area=(android.graphics.RectF)XposedHelpers.getObjectField(p.thisObject,"mSelectedAreaRect");
                            width=Math.round(width*area.width()); height=Math.round(height*area.height());
                        } else if(Boolean.TRUE.equals(XposedHelpers.getObjectField(p.thisObject,"mIsRecTopOnly"))) height/=2;
                        NativeRecordingProfile plan=NativeRecordingProfile.choose(width,height,maxFps(c));
                        PLANS.put(p.thisObject,plan);
                        XposedHelpers.setIntField(p.thisObject,"mVideoBitrate",plan.bitrate);
                        XposedHelpers.setIntField(p.thisObject,"mVideoFrameRate",plan.fps);
                    } catch(Throwable t) { log("native profile unavailable",t); p.setThrowable(t); }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Context c=(Context)XposedHelpers.getObjectField(p.thisObject,"mContext");
                    if(!original(c)||p.getThrowable()!=null) return;
                    try {
                        MediaFormat format=(MediaFormat)XposedHelpers.getObjectField(p.thisObject,"mFormat");
                        NativeRecordingProfile plan=PLANS.get(p.thisObject);
                        if(plan==null) throw new IllegalStateException("Missing native profile");
                        plan.apply(format); PLANS.put(p.thisObject,plan);
                        XposedHelpers.setIntField(p.thisObject,"mVideoBitrate",plan.bitrate);
                        XposedHelpers.setIntField(p.thisObject,"mVideoFrameRate",plan.fps);
                        android.util.Log.i("SFE-NativeRecord","Original: "+plan);
                    } catch(Throwable t) { log("native profile unavailable",t); p.setThrowable(t); }
                }
            });
            XposedBridge.hookMethod(method(mp4,"startRecording"),new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    p.setObjectExtra("sfePrevious",ENCODING.get());
                    NativeRecordingProfile plan=PLANS.get(p.thisObject); if(plan!=null) ENCODING.set(plan);
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    NativeRecordingProfile previous=(NativeRecordingProfile)p.getObjectExtra("sfePrevious");
                    if(previous==null) ENCODING.remove(); else ENCODING.set(previous);
                }
            });
            XposedBridge.hookMethod(MediaCodec.class.getDeclaredMethod("createEncoderByType",String.class),new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    NativeRecordingProfile plan=ENCODING.get();
                    if(plan!=null&&"video/avc".equals(p.args[0])) p.setResult(MediaCodec.createByCodecName(plan.codec));
                }
            });
            XposedBridge.hookMethod(VirtualDisplayConfig.Builder.class.getDeclaredMethod("build"),new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    NativeRecordingProfile plan=ENCODING.get();
                    if(plan!=null) ((VirtualDisplayConfig.Builder)p.thisObject).setRequestedRefreshRate(plan.fps);
                }
            });
            android.util.Log.i("SFE-NativeRecord","Original quality hooks installed in "+p.processName);
        } catch(Throwable t) { log("Unsupported Samsung Capture build",t); }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void installOption(Object fragment) {
        try {
            Context c=(Context)XposedHelpers.getObjectField(fragment,"mContext");
            Object pref=XposedHelpers.getObjectField(fragment,"mScreenRecorderVideoSettings");
            if(c==null||pref==null) return;
            ArrayList<String> values=(ArrayList<String>)XposedHelpers.getObjectField(fragment,"mScreenRecorderVideoSettingValue");
            String nativeLabel=label(c);
            UI.set(true);
            try {
                if(values.size()==3) {
                    ArrayList<Field> arrays=new ArrayList<>();
                    for(Class<?> k=pref.getClass();k!=null;k=k.getSuperclass())
                        for(Field f:k.getDeclaredFields()) if(f.getType()==CharSequence[].class) {
                            f.setAccessible(true); Object v=f.get(pref);
                            if(v!=null&&Arrays.equals((CharSequence[])v,values.toArray(new CharSequence[0]))) arrays.add(f);
                        }
                    if(arrays.size()!=2) throw new IllegalStateException("Unsupported quality list layout");
                    values.add(nativeLabel);
                    for(Field f:arrays) f.set(pref,values.toArray(new CharSequence[0]));
                    for(Field f:pref.getClass().getDeclaredFields()) {
                        f.setAccessible(true); Object v=f.get(pref);
                        if(v instanceof ArrayAdapter) {
                            ArrayAdapter a=(ArrayAdapter)v; a.clear(); a.addAll(values); a.notifyDataSetChanged();
                        }
                    }
                }
                if(values.size()==4&&nativeLabel.equals(values.get(3))&&original(c)) {
                    select(pref,3); XposedHelpers.callMethod(pref,"setSummary",nativeLabel);
                }
            } finally { UI.remove(); }
        } catch(Throwable t) { log("quality option",t); }
    }
    private static void select(Object pref,int index) throws Throwable {
        Method selected=null;
        for(Method m:pref.getClass().getDeclaredMethods())
            if(!Modifier.isStatic(m.getModifiers())&&m.getReturnType()==void.class
                &&Arrays.equals(m.getParameterTypes(),new Class<?>[]{int.class})) {
                if(selected!=null) throw new IllegalStateException("Ambiguous selection method"); selected=m;
            }
        if(selected==null) throw new NoSuchMethodException("quality selection");
        selected.setAccessible(true); selected.invoke(pref,index);
    }
    private static void log(String text,Throwable t) { android.util.Log.w("SFE-NativeRecord",text,t); }
}

