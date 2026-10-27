package com.samsung.feature.extension;

import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Root uinput process owned by the module. A private Binder-delivered pipe carries
 * only bounded relative movement and left-button state, never arbitrary shell input.
 * EOF (including AirCommand death) closes stdin and unregisters the kernel device.
 */
final class SpenNativeMouseSession {
    static volatile String lastError = "";
    private final java.lang.Process process;
    private final ParcelFileDescriptor readEnd;
    private volatile boolean closed;

    static ParcelFileDescriptor open(String name) throws IOException {
        ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
        try {
            SpenNativeMouseSession session=new SpenNativeMouseSession(pipe[0],name);
            session.run();
            // openFile's Binder return transfers/closes the provider's copy.
            return pipe[1];
        } catch(IOException e) {
            pipe[0].close(); pipe[1].close(); throw e;
        }
    }

    private SpenNativeMouseSession(ParcelFileDescriptor reader,String name) throws IOException {
        readEnd=reader; lastError="";
        process=new ProcessBuilder("su","-c","exec /system/bin/uinput -").redirectErrorStream(true).start();
        this.name=name;
    }
    private final String name;

    private void run() {
        Thread diagnostics=new Thread(() -> {
            try(BufferedReader r=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) {
                String line;
                while((line=r.readLine())!=null) {
                    lastError=line.length()>300?line.substring(0,300):line;
                    Log.i("SFE-NativeMouse",line);
                }
            } catch(IOException ignored) {}
            if(!closed) {
                lastError="uinput 已退出，请检查模块 Root 授权；"+lastError;
                try { readEnd.close(); } catch(IOException ignored) {}
            }
        },"SFE-uinput-output");
        diagnostics.setDaemon(true); diagnostics.start();
        Thread bridge=new Thread(() -> {
            try(DataInputStream in=new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(readEnd));
                BufferedWriter out=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8))) {
                // UI_SET_EVBIT / UI_SET_KEYBIT / UI_SET_RELBIT. A real EV_REL mouse.
                out.write("{\"id\":1,\"command\":\"register\",\"name\":\""+name
                    +"\",\"vid\":21318,\"pid\":17744,\"bus\":\"usb\",\"configuration\":["
                    +"{\"type\":100,\"data\":[1,2]},{\"type\":101,\"data\":[272]},"
                    +"{\"type\":102,\"data\":[0,1]}]}\n");
                out.flush();
                while(true) {
                    int command=in.readInt(), x=in.readInt(), y=in.readInt();
                    if(command==0) {
                        x=Math.max(-4096,Math.min(4096,x)); y=Math.max(-4096,Math.min(4096,y));
                        out.write("{\"id\":1,\"command\":\"inject\",\"events\":[2,0,"+x+",2,1,"+y+",0,0,0]}\n");
                    } else if(command==1 && (x==0 || x==1)) {
                        out.write("{\"id\":1,\"command\":\"inject\",\"events\":[1,272,"+x+",0,0,0]}\n");
                    } else {
                        throw new IOException("Invalid mouse pipe command");
                    }
                    out.flush();
                }
            } catch(EOFException ignored) {
                // Normal mode shutdown or the remote process went away.
            } catch(Throwable t) {
                lastError=t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());
                Log.w("SFE-NativeMouse",lastError);
            } finally {
                closed=true;
                try { process.getOutputStream().close(); } catch(IOException ignored) {}
                try {
                    if(!process.waitFor(2,TimeUnit.SECONDS)) process.destroy();
                } catch(InterruptedException e) { Thread.currentThread().interrupt(); process.destroy(); }
            }
        },"SFE-uinput-bridge");
        bridge.setDaemon(true); bridge.start();
    }
}

