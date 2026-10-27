package com.samsung.feature.extension;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Private setting writes and system-only runtime reports. */
public final class FreeformScaleSettingsProvider extends ContentProvider {
    public static final Uri URI=Uri.parse("content://com.samsung.feature.extension.freeformscale");
    private static final String PREFS="freeform_scale";
    private Bundle runtime=new Bundle();
    @Override public boolean onCreate() { return true; }
    @Override public synchronized Bundle call(String method,String arg,Bundle extras) {
        int uid=Binder.getCallingUid();
        if("set".equals(method)) {
            if(uid!=android.os.Process.myUid()&&uid!=0) throw new SecurityException("Module only");
            if(extras!=null&&extras.containsKey("enabled")) setEnabled(getContext(),extras.getBoolean("enabled"));
        } else if("status".equals(method)) {
            if(uid!=android.os.Process.SYSTEM_UID) throw new SecurityException("System only");
            runtime=extras==null?new Bundle():new Bundle(extras);
            runtime.putLong("heartbeat",SystemClock.elapsedRealtime());
        }
        Bundle b=new Bundle(); b.putBoolean("enabled",isEnabled(getContext()));
        b.putBundle("runtime",new Bundle(runtime)); return b;
    }
    public static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("enabled",false);
    }
    public static void setEnabled(Context c,boolean enabled) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("enabled",enabled).apply();
        c.getContentResolver().notifyChange(URI,null);
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u,ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u,String s,String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u,ContentValues v,String s,String[] a) { throw new UnsupportedOperationException(); }
}

