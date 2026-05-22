package com.samsung.feature.extension;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

final class DiagnosticLogger {
    private static final Object LOCK = new Object();
    private static final String FILE_NAME = "MyFilesWebDavLsp.log";
    private static volatile Context appContext;
    private static volatile File logFile;

    private DiagnosticLogger() {
    }

    static void init(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        if (isEnabled()) {
            ensureLogFile();
            log("logger init, path=" + path());
        }
    }

    static String path() {
        File file = ensureLogFile();
        return file != null ? file.getAbsolutePath() : "LSPosed log only";
    }

    static void log(String message) {
        if (!isEnabled()) {
            return;
        }
        String line = now() + " " + message;
        xposedLog("MyFilesWebDav: " + message);
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
            } catch (Throwable t) {
                xposedLog("MyFilesWebDav: diagnostic file write failed");
                xposedLog(t);
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
        if (!isEnabled()) {
            return;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        log(writer.toString());
        xposedLog(throwable);
    }

    static boolean isEnabled() {
        return LogSettingsProvider.isLogEnabled(appContext);
    }

    static String mask(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        if (value.length() <= 2) {
            return "**";
        }
        return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
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
                File dir = context.getExternalFilesDir(null);
                File privateFile = dir != null ? new File(dir, FILE_NAME) : null;
                if (canUse(privateFile)) {
                    logFile = privateFile;
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

    private static void xposedLog(String message) {
        try {
            XposedBridge.log(message);
        } catch (Throwable ignored) {
            // The module app process does not provide XposedBridge.
        }
    }

    private static void xposedLog(Throwable throwable) {
        try {
            XposedBridge.log(throwable);
        } catch (Throwable ignored) {
            // The module app process does not provide XposedBridge.
        }
    }
}
