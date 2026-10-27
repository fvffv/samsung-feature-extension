package com.samsung.feature.extension.spen;

import android.app.*;
import android.content.*;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.os.*;
import android.view.InputDevice;
import com.samsung.feature.extension.SpenMouseSettingsProvider;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Samsung S Pen SDK -> private pipe -> kernel EV_REL mouse -> Android native cursor. */
public final class SpenMouseHook implements IXposedHookLoadPackage {
    private static final String TARGET=SpenMouseSettingsProvider.TARGET;
    private static final String SERVICE=TARGET+".remotespen.external.RemoteSpenInternalBindingService";
    private static final String SDK="com.samsung.android.sdk.penremote.ISPenRemoteService";
    private static final String LISTENER="com.samsung.android.sdk.penremote.ISpenEventListener";
    private static Controller controller;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if(!TARGET.equals(p.packageName)||!TARGET.equals(p.processName)) return;
        final Class<?> service;
        try { service=Class.forName(SERVICE,false,p.classLoader); }
        catch(Throwable t) { log("unsupported firmware: "+t); return; }
        XposedBridge.hookMethod(Application.class.getDeclaredMethod("attach",Context.class),new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if(controller==null) {
                    controller=new Controller((Context)p.thisObject);
                    controller.main.post(controller::initialize);
                }
            }
        });
        XposedBridge.hookMethod(service.getDeclaredMethod("onCreate"),new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if(controller!=null&&p.getThrowable()==null)
                    controller.main.post(() -> controller.serviceCreated((Service)p.thisObject));
            }
        });
        XposedBridge.hookMethod(service.getDeclaredMethod("onDestroy"),new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if(controller!=null&&controller.service==p.thisObject) controller.serviceDestroyed();
            }
        });
        log("native mouse hook installed");
    }
    static void log(String s) { android.util.Log.i("SFE-SpenMouse",s); XposedBridge.log("[SFE-SpenMouse] "+s); }

    private static final class Controller {
        final Context context;
        final Handler main=new Handler(Looper.getMainLooper());
        final Handler io;
        final SpenMouseMotion motion=new SpenMouseMotion();
        final Set<Method> hooked=new HashSet<>();
        final Set<Object> lists=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        Service service;
        IBinder endpoint;
        Method sensorSwitch;
        Binder buttonListener,motionListener;
        InputManager input;
        DataOutputStream mouse;
        String mouseName;
        int nativeDeviceId=-1;
        long openingSince;
        volatile boolean active;
        boolean wanted,binding,registeredButton,registeredMotion,failed,inserted,opening;
        int epoch;
        float sensitivity=1;
        long motions,buttons;
        long lastMotionAt,lastSensorAttempt;
        long outputs,rateAt,rateMotions,rateOutputs;
        float inputHz,outputHz;
        boolean frameScheduled;
        final Runnable frame=new Runnable() {
            public void run() {
                frameScheduled=false;
                if(!active||inserted) return;
                motion.advance(SystemClock.elapsedRealtime());
                emitMotion();
                scheduleFrame();
            }
        };
        String message="模块已连接，模式关闭";
        final Runnable holdTimeout=() -> {
            if(active&&motion.pressed) {
                finishMotion();
                send(1,0,0); motion.pressed=false;
                message="按钮按住超过 10 秒，已安全释放";
            }
        };
        final ServiceConnection connection=new ServiceConnection() {
            public void onServiceConnected(ComponentName n,IBinder b) { apply(); }
            public void onServiceDisconnected(ComponentName n) { serviceDestroyed(); }
        };
        Controller(Context c) {
            context=c;
            HandlerThread t=new HandlerThread("SFE-native-mouse-writer"); t.start(); io=new Handler(t.getLooper());
        }
        void initialize() {
            try {
                input=(InputManager)context.getSystemService(Context.INPUT_SERVICE);
                input.registerInputDeviceListener(new InputManager.InputDeviceListener() {
                    public void onInputDeviceAdded(int id) { apply(); }
                    public void onInputDeviceChanged(int id) { apply(); }
                    public void onInputDeviceRemoved(int id) {
                        if(id==nativeDeviceId&&active) fail(new IOException("系统鼠标设备已断开"));
                    }
                },main);
                context.getContentResolver().registerContentObserver(SpenMouseSettingsProvider.URI,false,
                    new ContentObserver(main) { @Override public void onChange(boolean self) { reload(); } });
                IntentFilter f=new IntentFilter();
                f.addAction(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON);
                f.addAction(Intent.ACTION_USER_PRESENT); f.addAction("com.samsung.pen.INSERT");
                context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context c,Intent i) {
                        if("com.samsung.pen.INSERT".equals(i.getAction())) inserted=i.getBooleanExtra("penInsert",false);
                        lastSensorAttempt=SystemClock.elapsedRealtime();
                        apply();
                    }
                },f,Context.RECEIVER_EXPORTED);
                reload();
                main.post(new Runnable() {
                    public void run() { reload(); main.postDelayed(this,2000); }
                });
            } catch(Throwable t) { fail(t); }
        }
        void reload() {
            try {
                Bundle b=context.getContentResolver().call(SpenMouseSettingsProvider.URI,"get",null,null);
                if(b==null) return;
                boolean next=b.getBoolean("enabled",false);
                if(wanted!=next) { failed=false; wanted=next; }
                sensitivity=Math.max(25,Math.min(300,b.getInt("sensitivity",100)))/100f;
                motion.setSmoothing(b.getInt("smoothing",50));
                if(active) { emitMotion(); scheduleFrame(); }
                apply(); report();
            } catch(Throwable t) { fail(t); }
        }
        boolean unlocked() {
            PowerManager p=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
            KeyguardManager k=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
            return p!=null&&p.isInteractive()&&k!=null&&!k.isKeyguardLocked();
        }
        void apply() {
            if(!wanted||!unlocked()||inserted) {
                stop();
                if(!wanted) {
                    message="模块已连接，模式关闭";
                    if(binding) { binding=false; try { context.unbindService(connection); } catch(Throwable ignored){} }
                } else message=inserted?"笔已插入，暂停系统鼠标":"息屏或锁屏，暂停系统鼠标";
                return;
            }
            if(failed) return;
            if(active) {
                // The SDK service skips SENSOR_ON when registration precedes BLE
                // reconnection. Retry only after a quiet interval, never in parallel
                // with the initial 5-second GATT transaction or an insertion event.
                long now=SystemClock.elapsedRealtime();
                if(sensorSwitch!=null&&now-lastMotionAt>15000&&now-lastSensorAttempt>15000) {
                    lastSensorAttempt=now;
                    try { sensorSwitch.invoke(service,true); } catch(Throwable t) { log("sensor reconnect: "+t); }
                }
                return;
            }
            if(service==null) {
                message="正在连接 AirCommand S Pen 接口…";
                if(!binding) try {
                    binding=context.bindService(new Intent().setClassName(TARGET,SERVICE),connection,Context.BIND_AUTO_CREATE);
                    if(!binding) throw new IllegalStateException("bindService returned false");
                } catch(Throwable t) { fail(t); }
                return;
            }
            if(mouse==null) {
                if(!opening) openNativeMouse();
                return;
            }
            for(int id:input.getInputDeviceIds()) {
                InputDevice d=input.getInputDevice(id);
                if(d!=null&&mouseName.equals(d.getName())&&d.supportsSource(InputDevice.SOURCE_MOUSE)) {
                    nativeDeviceId=id;
                    try { startSdk(); } catch(Throwable t) { fail(t); }
                    return;
                }
            }
            if(SystemClock.elapsedRealtime()-openingSince>30000)
                fail(new IOException("未检测到系统鼠标。请允许三星功能扩展的 Root 授权后关闭开关重试"));
        }
        void openNativeMouse() {
            opening=true; openingSince=SystemClock.elapsedRealtime();
            final int session=++epoch;
            final String nonce=Long.toString(SystemClock.elapsedRealtimeNanos());
            mouseName="SFE S Pen Mouse "+nonce;
            message="正在创建系统鼠标；如有 Root 提示，请允许三星功能扩展";
            new Thread(() -> {
                try {
                    ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(
                        SpenMouseSettingsProvider.URI.buildUpon().appendPath("native").appendQueryParameter("session",nonce).build(),"w");
                    if(fd==null) throw new IOException("No native mouse pipe");
                    main.post(() -> {
                        if(session!=epoch||!wanted) { try { fd.close(); } catch(IOException ignored){} return; }
                        opening=false; mouse=new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(fd)); apply();
                    });
                } catch(Throwable t) { main.post(() -> { if(session==epoch) fail(t); }); }
            },"SFE-native-mouse-open").start();
        }
        void serviceCreated(Service s) {
            if(service==s) return;
            stop(); service=s; endpoint=null; sensorSwitch=null; lists.clear();
            try {
                for(Field f:s.getClass().getDeclaredFields()) {
                    if(Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true); Object v=f.get(s);
                    if(v instanceof IBinder&&SDK.equals(((IBinder)v).getInterfaceDescriptor())) endpoint=(IBinder)v;
                    if(v instanceof RemoteCallbackList) lists.add(v);
                }
                if(endpoint==null||lists.size()!=2) throw new IllegalStateException("Unsupported SDK endpoint/list layout");
                int sensorMethods=0;
                for(Method m:s.getClass().getDeclaredMethods()) {
                    if(!Modifier.isStatic(m.getModifiers())&&m.getReturnType()==void.class
                            &&Arrays.equals(m.getParameterTypes(),new Class<?>[]{boolean.class})) {
                        sensorSwitch=m; m.setAccessible(true); sensorMethods++;
                    }
                }
                if(sensorMethods!=1) sensorSwitch=null; // Optional reconnect helper; no guessing.
                for(Object list:lists) {
                    int checks=0;
                    for(Method m:list.getClass().getDeclaredMethods()) {
                        if(m.getReturnType()==boolean.class&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{String.class})) {
                            checks++;
                            if(hooked.add(m)) XposedBridge.hookMethod(m,new XC_MethodHook() {
                                @Override protected void beforeHookedMethod(MethodHookParam p) {
                                    if(active&&lists.contains(p.thisObject)&&unlocked()) p.setResult(true);
                                }
                            });
                        }
                    }
                    if(checks!=1) throw new IllegalStateException("Unsupported SDK ownership check");
                }
                log("SDK endpoint resolved: "+endpoint.getClass().getName()); apply();
            } catch(Throwable t) { fail(t); }
        }
        void serviceDestroyed() { stop(); service=null; endpoint=null; lists.clear(); message="AirCommand 服务已断开"; }
        void startSdk() throws Throwable {
            if(endpoint==null) throw new IllegalStateException("SDK unavailable");
            final int session=epoch;
            buttonListener=listener(false,session); motionListener=listener(true,session);
            transact(1,0,buttonListener); registeredButton=true;
            // The first motion registration itself requests SENSOR_ON. Never send it twice.
            transact(1,1,motionListener); registeredMotion=true;
            lastSensorAttempt=SystemClock.elapsedRealtime();
            active=true;
            rateAt=SystemClock.elapsedRealtime(); rateMotions=motions; rateOutputs=outputs;
            inputHz=0; outputHz=0;
            // A tiny relative event makes Android show its own pointer immediately.
            send(0,1,0);
            message="系统原生鼠标已连接，等待 S Pen 操作";
            log("native mouse started, Android device id="+nativeDeviceId);
        }
        Binder listener(final boolean movement,final int session) {
            return new Binder() {
                @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
                    if(code==IBinder.INTERFACE_TRANSACTION) { if(reply!=null) reply.writeString(LISTENER); return true; }
                    if(code!=1) return super.onTransact(code,data,reply,flags);
                    data.enforceInterface(LISTENER);
                    try {
                        if(data.readInt()!=0) {
                            data.readLong();
                            int count=data.readInt();
                            if(count<1||count>16) throw new IllegalArgumentException("event size");
                            float[] values=new float[count]; data.readFloatArray(values);
                            main.post(() -> {
                                if(!active||session!=epoch||!unlocked()||inserted) return;
                                if(movement&&values.length>=2) {
                                    motions++; lastMotionAt=SystemClock.elapsedRealtime();
                                    motion.move(values[0],values[1],sensitivity,lastMotionAt);
                                    emitMotion(); scheduleFrame();
                                } else if(!movement) button((int)values[0]);
                            });
                        }
                        if(reply!=null) reply.writeNoException();
                    } catch(Throwable t) { main.post(() -> fail(t)); if(reply!=null) reply.writeNoException(); }
                    return true;
                }
            };
        }
        void transact(int code,int unit,IBinder listener) throws RemoteException {
            Parcel data=Parcel.obtain(),reply=Parcel.obtain(); long identity=Binder.clearCallingIdentity();
            try {
                data.writeInterfaceToken(SDK); data.writeInt(unit); data.writeStrongBinder(listener);
                if(!endpoint.transact(code,data,reply,0)) throw new RemoteException("SDK transaction rejected");
                reply.readException();
            } finally { Binder.restoreCallingIdentity(identity); data.recycle(); reply.recycle(); }
        }
        void button(int action) {
            if(action!=0&&action!=1) return;
            buttons++;
            if(action==0) {
                if(motion.pressed) return;
                finishMotion();
                motion.pressed=true; send(1,1,0); main.postDelayed(holdTimeout,10000);
            } else {
                if(!motion.pressed) return;
                finishMotion();
                send(1,0,0); motion.pressed=false; main.removeCallbacks(holdTimeout);
            }
            message="系统原生鼠标运行中";
        }
        void scheduleFrame() {
            if(active&&motion.hasPending()&&!frameScheduled) {
                frameScheduled=true;
                // Target 8 ms (125 Hz scheduling), not a claim about BLE sampling rate.
                main.postDelayed(frame,8);
            }
        }
        void emitMotion() {
            int dx=motion.takeX(),dy=motion.takeY();
            if(dx!=0||dy!=0) { outputs++; send(0,dx,dy); }
        }
        void finishMotion() {
            main.removeCallbacks(frame); frameScheduled=false;
            motion.flush(); emitMotion();
        }
        void send(int command,int x,int y) {
            final DataOutputStream stream=mouse;
            final int session=epoch;
            if(stream==null) return;
            io.post(() -> {
                try { stream.writeInt(command); stream.writeInt(x); stream.writeInt(y); stream.flush(); }
                catch(Throwable t) { main.post(() -> { if(session==epoch) fail(t); }); }
            });
        }
        void stop() {
            active=false; epoch++; opening=false; nativeDeviceId=-1;
            main.removeCallbacks(holdTimeout); motion.reset();
            main.removeCallbacks(frame); frameScheduled=false;
            inputHz=0; outputHz=0;
            if(endpoint!=null) {
                try { if(registeredMotion) transact(2,1,motionListener); } catch(Throwable t) { log("unregister motion: "+t); }
                try { if(registeredButton) transact(2,0,buttonListener); } catch(Throwable t) { log("unregister button: "+t); }
            }
            registeredMotion=false; registeredButton=false; buttonListener=null; motionListener=null;
            final DataOutputStream stream=mouse; mouse=null;
            // Removing an input device cancels its active gesture and removes its cursor.
            if(stream!=null) io.post(() -> { try { stream.close(); } catch(IOException ignored){} });
        }
        void fail(Throwable t) {
            failed=true; stop();
            message="未启用 / 已安全停止："+t.getClass().getSimpleName()+" "+String.valueOf(t.getMessage())+"。关闭开关后重新开启可重试。";
            log(message); report();
        }
        void report() {
            try {
                Bundle b=new Bundle(); b.putString("message",message); b.putBoolean("active",active);
                long now=SystemClock.elapsedRealtime(),dt=now-rateAt;
                if(active&&dt>=1000) {
                    inputHz=(motions-rateMotions)*1000f/dt;
                    outputHz=(outputs-rateOutputs)*1000f/dt;
                    rateAt=now; rateMotions=motions; rateOutputs=outputs;
                }
                b.putFloat("input_hz",inputHz); b.putFloat("output_hz",outputHz);
                b.putLong("motions",motions); b.putLong("buttons",buttons); b.putInt("native_id",nativeDeviceId);
                context.getContentResolver().call(SpenMouseSettingsProvider.URI,"status",null,b);
            } catch(Throwable t) { android.util.Log.w("SFE-SpenMouse","Status unavailable",t); }
        }
    }
}
