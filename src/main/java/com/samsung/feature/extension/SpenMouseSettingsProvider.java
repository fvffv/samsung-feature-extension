package com.samsung.feature.extension;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Readable configuration; only this module may change controls. No exported input channel. */
public final class SpenMouseSettingsProvider extends ContentProvider {
    public static final String TARGET = "com.samsung.android.service.aircommand";
    public static final Uri URI = Uri.parse("content://com.samsung.feature.extension.spenmouse");
    private static final String PREFS = "spen_mouse";
    private Bundle runtime = new Bundle();

    @Override public boolean onCreate() { return true; }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        if ("status".equals(method)) {
            if (!isAirCommand()) throw new SecurityException("AirCommand only");
            runtime = extras == null ? new Bundle() : new Bundle(extras);
            runtime.putLong("heartbeat", SystemClock.elapsedRealtime());
        } else if ("set".equals(method)) {
            if (Binder.getCallingUid() != android.os.Process.myUid()) throw new SecurityException("Module only");
            save(getContext(), extras);
        }
        Bundle result = read(getContext());
        result.putBundle("runtime", new Bundle(runtime));
        result.putString("status", runtime.getString("message", "AirCommand 未连接"));
        result.putBoolean("active", runtime.getBoolean("active", false));
        result.putLong("motions", runtime.getLong("motions", 0));
        result.putLong("buttons", runtime.getLong("buttons", 0));
        result.putString("native_error", SpenNativeMouseSession.lastError);
        return result;
    }

    private boolean isAirCommand() {
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages != null) for (String p : packages) if (TARGET.equals(p)) return true;
        return false;
    }

    public static Bundle read(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Bundle b = new Bundle();
        b.putBoolean("enabled", p.getBoolean("enabled", false));
        b.putInt("sensitivity", p.getInt("sensitivity", 100));
        b.putInt("smoothing", p.getInt("smoothing", 50));
        return b;
    }

    public static void save(Context c, Bundle b) {
        if (b == null) return;
        android.content.SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (b.containsKey("enabled")) e.putBoolean("enabled", b.getBoolean("enabled"));
        if (b.containsKey("sensitivity")) e.putInt("sensitivity", Math.max(25, Math.min(300, b.getInt("sensitivity"))));
        if (b.containsKey("smoothing")) e.putInt("smoothing", Math.max(0, Math.min(100, b.getInt("smoothing"))));
        e.apply();
        c.getContentResolver().notifyChange(URI, null);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        if (!isAirCommand()) throw new SecurityException("AirCommand only");
        if (!read(getContext()).getBoolean("enabled")) throw new SecurityException("Mouse mode is disabled");
        String session=uri.getQueryParameter("session");
        if (!"/native".equals(uri.getPath()) || !"w".equals(mode) || session==null || !session.matches("[0-9]{1,20}"))
            throw new java.io.FileNotFoundException("Invalid mouse session");
        try { return SpenNativeMouseSession.open("SFE S Pen Mouse "+session); }
        catch(java.io.IOException e) { throw new java.io.FileNotFoundException(e.toString()); }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
