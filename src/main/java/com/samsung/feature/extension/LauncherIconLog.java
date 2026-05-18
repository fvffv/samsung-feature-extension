package com.samsung.feature.extension;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class LauncherIconLog {
    private static final Object LOCK = new Object();
    private static final String TAG = "LauncherIconCustomizer";
    private static final String FILE_NAME = "MyFilesWebDavLsp.log";

    private static volatile Context appContext;
    private static volatile File logFile;

    private LauncherIconLog() {
    }

    static void init(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        ensureLogFile();
    }

    static void log(String message) {
        String line = now() + " " + TAG + ": " + message;
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
            // Keep diagnostics best-effort in non-standard app contexts.
        }
        File file = ensureLogFile();
        if (file == null) {
            return;
        }
        synchronized (LOCK) {
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(file, true);
                output.write(line.getBytes("UTF-8"));
                output.write('\n');
            } catch (Throwable ignored) {
                // The normal module process may not be allowed to write public Download.
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (Throwable ignored) {
                        // Ignore cleanup failure.
                    }
                }
            }
        }
    }

    static void log(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        log(writer.toString());
    }

    private static File ensureLogFile() {
        File current = logFile;
        if (current != null) {
            return current;
        }
        synchronized (LOCK) {
            if (logFile != null) {
                return logFile;
            }
            File publicFile = buildPublicDownloadFile();
            if (canUse(publicFile)) {
                logFile = publicFile;
                return logFile;
            }
            Context context = appContext;
            if (context != null) {
                File externalDir = context.getExternalFilesDir(null);
                File externalFile = externalDir != null ? new File(externalDir, FILE_NAME) : null;
                if (canUse(externalFile)) {
                    logFile = externalFile;
                    return logFile;
                }
                File filesDir = context.getFilesDir();
                File internalFile = filesDir != null ? new File(filesDir, FILE_NAME) : null;
                if (canUse(internalFile)) {
                    logFile = internalFile;
                    return logFile;
                }
            }
            return null;
        }
    }

    private static File buildPublicDownloadFile() {
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            return downloads != null ? new File(downloads, FILE_NAME) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean canUse(File file) {
        if (file == null) {
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file, true);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                    // Ignore cleanup failure.
                }
            }
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
