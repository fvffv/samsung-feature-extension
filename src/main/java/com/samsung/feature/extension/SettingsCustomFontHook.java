package com.samsung.feature.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class SettingsCustomFontHook implements IXposedHookLoadPackage {
    private static final String TAG = "SamsungFeatureExt/SettingsFont";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String FONT_FRAGMENT =
            "com.samsung.android.settings.display.SecFontStylePreferenceFragment";
    private static final String FONT_ADAPTER =
            "com.samsung.android.settings.flipfont.FontListAdapter";
    private static final String CUSTOM_FONT_ID = "SamsungFeatureExtensionCustomTtf";
    private static final String PREFS_NAME = "sfe_custom_font";
    private static final String PREF_IDS = "ids";
    private static final String PREF_TITLE = "title";
    private static final String PREF_TITLE_PREFIX = "title_";
    private static final String STORAGE_DIR = "sfe-custom-font";
    private static final String STORAGE_FONTS_DIR = "fonts";
    private static final String LEGACY_SOURCE_FILE = "saved.ttf";
    private static final int REQUEST_PICK_TTF = 0x5346;
    private static final int VIRTUAL_NONE = 0;
    private static final int VIRTUAL_SAVED_FONT = 1;
    private static final int VIRTUAL_PICKER = 2;
    private static final long DUPLICATE_RESULT_WINDOW_MS = 5000L;
    private static final String[] FONT_COPY_TARGETS = new String[]{
            "DroidSans.ttf",
            "DroidSans-Bold.ttf",
            "DroidSansFallback.ttf",
            "Roboto-Regular.ttf",
            "Roboto-Bold.ttf",
            "SamsungOneUI-Regular.ttf",
            "SamsungOneUI-Bold.ttf"
    };

    private static WeakReference<Object> sLastFontFragment = new WeakReference<>(null);
    private static String sLastResultToken;
    private static long sLastResultTime;
    private static final ThreadLocal<Boolean> sInOriginalFontItemClick =
            new ThreadLocal<Boolean>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SETTINGS_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        hookFontStyleFragment(lpparam.classLoader);
        hookFontListAdapter(lpparam.classLoader);
        hookActivityResult(lpparam.classLoader);
    }

    private static void hookFontStyleFragment(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    FONT_FRAGMENT,
                    classLoader,
                    "onCreateView",
                    LayoutInflater.class,
                    ViewGroup.class,
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sLastFontFragment = new WeakReference<>(param.thisObject);
                        }
                    });
            log("hooked font style fragment");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hookFontStyleFragment failed");
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    FONT_FRAGMENT,
                    classLoader,
                    "setFontStyleList",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            refreshCustomFontItem(param.thisObject);
                            sLastFontFragment = new WeakReference<>(param.thisObject);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook setFontStyleList failed");
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    FONT_FRAGMENT,
                    classLoader,
                    "onItemClick",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            sInOriginalFontItemClick.set(Boolean.TRUE);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sInOriginalFontItemClick.remove();
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook font item click failed");
            XposedBridge.log(t);
        }
    }

    private static void hookFontListAdapter(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    FONT_ADAPTER,
                    classLoader,
                    "getItemCount",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int originalCount = ((Integer) param.getResult()).intValue();
                            param.setResult(originalCount + getVirtualItemCount(param.thisObject));
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook FontListAdapter.getItemCount failed");
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    FONT_ADAPTER,
                    classLoader,
                    "getItemViewType",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(sInOriginalFontItemClick.get())) {
                                return;
                            }
                            int index = ((Integer) param.args[0]).intValue();
                            int customIndex = getCustomDisplayIndex(param.thisObject);
                            if (customIndex < 0) {
                                return;
                            }
                            int virtualKind = getVirtualItemKind(param.thisObject, index);
                            if (virtualKind == VIRTUAL_SAVED_FONT) {
                                param.setResult(0);
                            } else if (virtualKind == VIRTUAL_PICKER) {
                                param.setResult(1);
                            } else {
                                int originalIndex = toOriginalIndex(param.thisObject, index);
                                if (originalIndex != index) {
                                    param.args[0] = originalIndex;
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook FontListAdapter.getItemViewType failed");
            XposedBridge.log(t);
        }

        try {
            Class<?> viewHolderClass = XposedHelpers.findClass(
                    "androidx.recyclerview.widget.RecyclerView$ViewHolder", classLoader);
            XposedHelpers.findAndHookMethod(
                    FONT_ADAPTER,
                    classLoader,
                    "onBindViewHolder",
                    viewHolderClass,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int index = ((Integer) param.args[1]).intValue();
                            int customIndex = getCustomDisplayIndex(param.thisObject);
                            if (customIndex < 0) {
                                return;
                            }
                            int virtualKind = getVirtualItemKind(param.thisObject, index);
                            if (virtualKind == VIRTUAL_SAVED_FONT) {
                                bindSavedFontItem(param.thisObject, param.args[0], index);
                                param.setResult(null);
                            } else if (virtualKind == VIRTUAL_PICKER) {
                                bindFontPickerItem(param.thisObject, param.args[0]);
                                param.setResult(null);
                            } else {
                                int originalIndex = toOriginalIndex(param.thisObject, index);
                                if (originalIndex != index) {
                                    param.args[1] = originalIndex;
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook FontListAdapter.onBindViewHolder failed");
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    FONT_ADAPTER,
                    classLoader,
                    "setItemChecked",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            refreshCustomFontItem(param.thisObject);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook FontListAdapter.setItemChecked failed");
            XposedBridge.log(t);
        }
    }

    private static void hookActivityResult(ClassLoader classLoader) {
        try {
            Class<?> fragmentClass = XposedHelpers.findClass("androidx.fragment.app.Fragment", classLoader);
            XposedHelpers.findAndHookMethod(
                    fragmentClass,
                    "onActivityResult",
                    int.class,
                    int.class,
                    Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (isFontStyleFragment(param.thisObject)) {
                                handleActivityResult(param.thisObject, param.args);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook fragment onActivityResult failed");
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.settings.SettingsActivity",
                    classLoader,
                    "onActivityResult",
                    int.class,
                    int.class,
                    Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object fragment = sLastFontFragment.get();
                            if (fragment != null) {
                                handleActivityResult(fragment, param.args);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook settings activity onActivityResult failed");
            XposedBridge.log(t);
        }
    }

    private static void openFontPicker(Object fragment) {
        Context context = getContext(fragment);
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "font/ttf",
                    "application/x-font-ttf",
                    "application/font-sfnt",
                    "application/octet-stream"
            });
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            Method method = fragment.getClass().getMethod(
                    "startActivityForResult", Intent.class, int.class);
            method.invoke(fragment, intent, REQUEST_PICK_TTF);
            sLastFontFragment = new WeakReference<>(fragment);
        } catch (ActivityNotFoundException e) {
            toast(context, "\u6ca1\u6709\u53ef\u7528\u7684\u6587\u4ef6\u9009\u62e9\u5668");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " openFontPicker failed");
            XposedBridge.log(t);
            toast(context, "\u6253\u5f00\u6587\u4ef6\u9009\u62e9\u5668\u5931\u8d25");
        }
    }

    private static void handleActivityResult(Object fragment, Object[] args) {
        if (args == null || args.length < 3) {
            return;
        }
        int requestCode = ((Integer) args[0]).intValue();
        int resultCode = ((Integer) args[1]).intValue();
        if (requestCode != REQUEST_PICK_TTF || resultCode != Activity.RESULT_OK) {
            return;
        }
        Intent data = (Intent) args[2];
        if (data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (isDuplicateResult(uri)) {
            return;
        }
        Context context = getContext(fragment);
        if (context == null) {
            return;
        }
        try {
            tryTakeReadPermission(context, data, uri);
            String displayName = getDisplayName(context, uri);
            if (!isTtfName(displayName)) {
                toast(context, "\u8bf7\u9009\u62e9 .ttf \u683c\u5f0f\u7684\u5b57\u4f53\u6587\u4ef6");
                return;
            }
            File tempFile = copyUriToTempFile(context, uri);
            Typeface.createFromFile(tempFile);
            CustomFontEntry entry = saveCustomFontSource(context, tempFile, displayName);
            Object adapter = getFontListAdapter(fragment);
            int customIndex = getDisplayIndexForEntry(adapter, entry.id);
            File fontDir = installCustomFont(context, entry);
            writeFlipFontSettings(context, customIndex);
            updateGlobalFlipFont(getTypefaceFileName(entry));
            refreshCustomFontItem(adapter);
            toast(context, "\u672c\u5730\u5b57\u4f53\u5df2\u5e94\u7528");
            finishFragment(fragment);
            log("custom font applied from " + displayName + " to " + fontDir);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " apply custom font failed");
            XposedBridge.log(t);
            toast(context, "\u5e94\u7528\u672c\u5730\u5b57\u4f53\u5931\u8d25\uff1a" + safeMessage(t));
        }
    }

    private static File installCustomFont(Context context, CustomFontEntry entry) throws Throwable {
        ClassLoader loader = context.getClassLoader();
        Class<?> fontWriterClass = XposedHelpers.findClass(
                "com.samsung.android.fontutil.FontWriter", loader);
        Object fontWriter = XposedHelpers.newInstance(fontWriterClass);
        File fontDir = (File) XposedHelpers.callMethod(
                fontWriter, "createFontDirectory", entry.id);
        if (fontDir == null) {
            throw new IOException("cannot create font directory");
        }

        boolean copyError = false;
        for (String target : FONT_COPY_TARGETS) {
            InputStream inputStream = null;
            try {
                inputStream = new FileInputStream(entry.file);
                Object result = XposedHelpers.callMethod(
                        fontWriter, "copyFontFile", fontDir, inputStream, target);
                if (Boolean.TRUE.equals(result)) {
                    copyError = true;
                }
            } finally {
                closeQuietly(inputStream);
            }
        }
        if (copyError) {
            throw new IOException("copyFontFile reported no space or copy error");
        }

        XposedHelpers.callMethod(fontWriter, "deleteFontDirectory", entry.id);
        XposedHelpers.callMethod(fontWriter, "writeLoc",
                fontDir.getAbsolutePath() + "#" + entry.title);
        return fontDir;
    }

    private static void writeFlipFontSettings(Context context, int index) {
        Settings.Global.putInt(context.getContentResolver(), "font_style_index", index);
        Settings.Global.putInt(context.getContentResolver(), "flip_font_style", index);
    }

    private static void updateGlobalFlipFont(String typefaceFileName) throws Throwable {
        Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
        Method getService = activityManagerClass.getDeclaredMethod("getService");
        getService.setAccessible(true);
        Object service = getService.invoke(null);
        Method getGlobalConfiguration = service.getClass().getMethod("getGlobalConfiguration");
        Object configuration = getGlobalConfiguration.invoke(service);
        Field flipFontField = configuration.getClass().getField("FlipFont");
        int flipFontValue = Math.abs(typefaceFileName.hashCode()) + 1;
        flipFontField.setInt(configuration, flipFontValue);
        Method updateConfiguration = service.getClass().getMethod(
                "updateConfiguration", Configuration.class);
        updateConfiguration.invoke(service, configuration);
    }

    private static void updateGlobalFlipFontValue(int value) throws Throwable {
        Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
        Method getService = activityManagerClass.getDeclaredMethod("getService");
        getService.setAccessible(true);
        Object service = getService.invoke(null);
        Method getGlobalConfiguration = service.getClass().getMethod("getGlobalConfiguration");
        Object configuration = getGlobalConfiguration.invoke(service);
        Field flipFontField = configuration.getClass().getField("FlipFont");
        flipFontField.setInt(configuration, value);
        Method updateConfiguration = service.getClass().getMethod(
                "updateConfiguration", Configuration.class);
        updateConfiguration.invoke(service, configuration);
    }

    private static CustomFontEntry saveCustomFontSource(Context context, File sourceFile,
                                                        String displayName) throws IOException {
        migrateLegacyFontIfNeeded(context);
        String id = createFontId(context);
        String title = stripTtfExtension(displayName);
        File target = getSavedFontFile(context, id);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create saved font directory");
        }
        copyFile(sourceFile, target);

        ArrayList ids = getSavedFontIds(context);
        ids.add(id);
        getPrefs(context)
                .edit()
                .putString(PREF_IDS, joinIds(ids))
                .putString(PREF_TITLE_PREFIX + id, title)
                .apply();
        return new CustomFontEntry(id, title, target);
    }

    private static File getSavedFontFile(Context context, String id) {
        return new File(getSavedFontsDir(context), id + ".ttf");
    }

    private static File getLegacySavedFontFile(Context context) {
        return new File(new File(context.getFilesDir(), STORAGE_DIR), LEGACY_SOURCE_FILE);
    }

    private static File getSavedFontsDir(Context context) {
        return new File(new File(context.getFilesDir(), STORAGE_DIR), STORAGE_FONTS_DIR);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static boolean hasSavedCustomFont(Context context) {
        return context != null && !getSavedFontEntries(context).isEmpty();
    }

    private static String getSavedFontListTitle(CustomFontEntry entry) {
        return "\u672c\u5730\u5b57\u4f53\uff1a" + entry.title;
    }

    private static String getTypefaceFileName(CustomFontEntry entry) {
        return entry.id + ".xml";
    }

    private static ArrayList getSavedFontEntries(Context context) {
        ArrayList result = new ArrayList();
        if (context == null) {
            return result;
        }
        migrateLegacyFontIfNeeded(context);
        SharedPreferences prefs = getPrefs(context);
        ArrayList ids = getSavedFontIds(context);
        boolean changed = false;
        for (int i = 0; i < ids.size(); i++) {
            String id = (String) ids.get(i);
            File file = getSavedFontFile(context, id);
            if (!file.isFile()) {
                changed = true;
                continue;
            }
            String title = prefs.getString(PREF_TITLE_PREFIX + id, null);
            if (TextUtils.isEmpty(title)) {
                title = "\u672c\u5730\u5b57\u4f53";
            }
            result.add(new CustomFontEntry(id, title, file));
        }
        if (changed || result.size() != ids.size()) {
            ArrayList keptIds = new ArrayList();
            for (int i = 0; i < result.size(); i++) {
                keptIds.add(((CustomFontEntry) result.get(i)).id);
            }
            prefs.edit().putString(PREF_IDS, joinIds(keptIds)).apply();
        }
        return result;
    }

    private static ArrayList getSavedFontIds(Context context) {
        ArrayList result = new ArrayList();
        if (context == null) {
            return result;
        }
        String value = getPrefs(context).getString(PREF_IDS, "");
        if (TextUtils.isEmpty(value)) {
            return result;
        }
        String[] pieces = value.split(",");
        for (int i = 0; i < pieces.length; i++) {
            String id = pieces[i].trim();
            if (isSafeFontId(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private static void migrateLegacyFontIfNeeded(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = getPrefs(context);
        if (!TextUtils.isEmpty(prefs.getString(PREF_IDS, ""))) {
            return;
        }
        File legacyFile = getLegacySavedFontFile(context);
        if (!legacyFile.isFile()) {
            return;
        }
        try {
            String id = CUSTOM_FONT_ID;
            File target = getSavedFontFile(context, id);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            copyFile(legacyFile, target);
            String title = prefs.getString(PREF_TITLE, null);
            if (TextUtils.isEmpty(title)) {
                title = "\u672c\u5730\u5b57\u4f53";
            }
            prefs.edit()
                    .putString(PREF_IDS, id)
                    .putString(PREF_TITLE_PREFIX + id, title)
                    .apply();
        } catch (Throwable t) {
            XposedBridge.log(TAG + " migrate legacy custom font failed");
            XposedBridge.log(t);
        }
    }

    private static String createFontId(Context context) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            String id = CUSTOM_FONT_ID + "_" + (now + i);
            if (!getSavedFontFile(context, id).exists()) {
                return id;
            }
        }
        return CUSTOM_FONT_ID + "_" + now + "_x";
    }

    private static boolean isSafeFontId(String id) {
        if (TextUtils.isEmpty(id)) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String joinIds(ArrayList ids) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append((String) ids.get(i));
        }
        return builder.toString();
    }

    private static void copyFile(File source, File target) throws IOException {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(source);
            outputStream = new FileOutputStream(target);
            byte[] buffer = new byte[16384];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
        }
    }

    private static int getCustomDisplayIndex(Object adapter) {
        return getInsertBaseIndex(adapter);
    }

    private static int getInsertBaseIndex(Object adapter) {
        if (adapter == null) {
            return -1;
        }
        List fontNames = getListField(adapter, "mFontNames");
        if (fontNames == null) {
            return -1;
        }
        int originalCount = fontNames.size();
        if (originalCount < 0) {
            return -1;
        }
        boolean downloadEnabled = getBooleanField(adapter, "mDownloadFontEnabled", true);
        return downloadEnabled && originalCount > 0 ? originalCount - 1 : originalCount;
    }

    private static int getVirtualItemCount(Object adapter) {
        Context context = getAdapterContext(adapter);
        return getSavedFontEntries(context).size() + 1;
    }

    private static int getPickerDisplayIndex(Object adapter) {
        int base = getInsertBaseIndex(adapter);
        if (base < 0) {
            return -1;
        }
        return base + getSavedFontEntries(getAdapterContext(adapter)).size();
    }

    private static int getVirtualItemKind(Object adapter, int displayIndex) {
        int base = getInsertBaseIndex(adapter);
        if (base < 0) {
            return VIRTUAL_NONE;
        }
        int savedCount = getSavedFontEntries(getAdapterContext(adapter)).size();
        if (displayIndex >= base && displayIndex < base + savedCount) {
            return VIRTUAL_SAVED_FONT;
        }
        if (displayIndex == base + savedCount) {
            return VIRTUAL_PICKER;
        }
        return VIRTUAL_NONE;
    }

    private static int getDisplayIndexForEntry(Object adapter, String id) {
        int base = getInsertBaseIndex(adapter);
        if (base < 0) {
            return 0;
        }
        ArrayList entries = getSavedFontEntries(getAdapterContext(adapter));
        for (int i = 0; i < entries.size(); i++) {
            if (id.equals(((CustomFontEntry) entries.get(i)).id)) {
                return base + i;
            }
        }
        return base;
    }

    private static CustomFontEntry getEntryForDisplayIndex(Object adapter, int displayIndex) {
        int base = getInsertBaseIndex(adapter);
        if (base < 0 || displayIndex < base) {
            return null;
        }
        int entryIndex = displayIndex - base;
        ArrayList entries = getSavedFontEntries(getAdapterContext(adapter));
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return null;
        }
        return (CustomFontEntry) entries.get(entryIndex);
    }

    private static int toOriginalIndex(Object adapter, int displayIndex) {
        int base = getInsertBaseIndex(adapter);
        if (base < 0 || displayIndex < base) {
            return displayIndex;
        }
        int virtualCount = getVirtualItemCount(adapter);
        if (displayIndex >= base + virtualCount) {
            return displayIndex - virtualCount;
        }
        return displayIndex;
    }

    private static void bindSavedFontItem(final Object adapter, Object holder, final int displayIndex) {
        final Context context = getAdapterContext(adapter);
        if (context == null) {
            return;
        }
        final CustomFontEntry entry = getEntryForDisplayIndex(adapter, displayIndex);
        if (entry == null) {
            return;
        }
        final Object fragment = sLastFontFragment.get();
        String title = getSavedFontListTitle(entry);
        try {
            Object titleView = XposedHelpers.getObjectField(holder, "mTitleView");
            if (titleView instanceof TextView) {
                TextView textView = (TextView) titleView;
                textView.setText(title);
                textView.setTypeface(Typeface.createFromFile(entry.file), 0);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " bind title failed");
            XposedBridge.log(t);
        }
        try {
            Object checkBox = XposedHelpers.getObjectField(holder, "mCheckBoxView");
            if (checkBox instanceof RadioButton) {
                ((RadioButton) checkBox).setChecked(isCustomFontActive(context, entry));
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " bind checkbox failed");
            XposedBridge.log(t);
        }
        try {
            Field itemViewField = holder.getClass().getField("itemView");
            Object itemViewObject = itemViewField.get(holder);
            if (itemViewObject instanceof View) {
                View itemView = (View) itemViewObject;
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        applySavedCustomFontFromList(fragment, adapter, entry, displayIndex);
                    }
                });
                itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        showSavedFontActions(context, fragment, adapter, entry);
                        return true;
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " bind item click failed");
            XposedBridge.log(t);
        }
    }

    private static void bindFontPickerItem(final Object adapter, Object holder) {
        final Context context = getAdapterContext(adapter);
        if (context == null) {
            return;
        }
        final Object fragment = sLastFontFragment.get();
        try {
            View itemView = getItemView(holder);
            if (itemView instanceof TextView) {
                ((TextView) itemView).setText(hasSavedCustomFont(context)
                        ? "\u6dfb\u52a0\u672c\u5730\u5b57\u4f53\u6587\u4ef6"
                        : "\u9009\u62e9\u672c\u5730\u5b57\u4f53\u6587\u4ef6");
            } else if (itemView != null) {
                TextView textView = itemView.findViewById(android.R.id.text1);
                if (textView != null) {
                    textView.setText(hasSavedCustomFont(context)
                            ? "\u6dfb\u52a0\u672c\u5730\u5b57\u4f53\u6587\u4ef6"
                            : "\u9009\u62e9\u672c\u5730\u5b57\u4f53\u6587\u4ef6");
                }
            }
            if (itemView != null) {
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openFontPicker(fragment);
                    }
                });
                itemView.setOnLongClickListener(null);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " bind picker failed");
            XposedBridge.log(t);
        }
    }

    private static View getItemView(Object holder) throws IllegalAccessException, NoSuchFieldException {
        Field itemViewField = holder.getClass().getField("itemView");
        Object itemViewObject = itemViewField.get(holder);
        return itemViewObject instanceof View ? (View) itemViewObject : null;
    }

    private static void refreshCustomFontItem(Object object) {
        Object adapter = isFontStyleFragment(object) ? getFontListAdapter(object) : object;
        if (adapter == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(adapter, "notifyDataSetChanged");
        } catch (Throwable ignored) {
        }
    }

    private static void handleCustomFontItemClick(Object fragment, int index) {
        Object adapter = getFontListAdapter(fragment);
        CustomFontEntry entry = getEntryForDisplayIndex(adapter, index);
        if (entry != null) {
            applySavedCustomFontFromList(fragment, adapter, entry, index);
            return;
        }
        openFontPicker(fragment);
    }

    private static void applySavedCustomFontFromList(Object fragment, Object adapter,
                                                     CustomFontEntry entry, int index) {
        Context context = getContext(fragment);
        if (context == null) {
            return;
        }
        try {
            if (entry == null || !entry.file.isFile()) {
                toast(context, "\u672c\u5730\u5b57\u4f53\u6587\u4ef6\u5df2\u4e0d\u5b58\u5728");
                return;
            }
            Typeface.createFromFile(entry.file);
            installCustomFont(context, entry);
            writeFlipFontSettings(context, index);
            updateGlobalFlipFont(getTypefaceFileName(entry));
            setFragmentFontIndex(fragment, index);
            if (adapter != null) {
                XposedHelpers.callMethod(adapter, "setItemChecked", index);
            }
            toast(context, "\u5df2\u5207\u6362\u5230\u672c\u5730\u5b57\u4f53");
            finishFragment(fragment);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " applySavedCustomFontFromList failed");
            XposedBridge.log(t);
            toast(context, "\u5207\u6362\u672c\u5730\u5b57\u4f53\u5931\u8d25\uff1a" + safeMessage(t));
        }
    }

    private static void showSavedFontActions(final Context context, final Object fragment,
                                             final Object adapter, final CustomFontEntry entry) {
        if (context == null || entry == null) {
            return;
        }
        try {
            new AlertDialog.Builder(context)
                    .setTitle(getSavedFontListTitle(entry))
                    .setItems(new CharSequence[]{
                            "\u5220\u9664\u672c\u5730\u5b57\u4f53"
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            confirmDeleteSavedFont(context, fragment, adapter, entry);
                        }
                    })
                    .show();
        } catch (Throwable t) {
            XposedBridge.log(TAG + " showSavedFontActions failed");
            XposedBridge.log(t);
        }
    }

    private static void confirmDeleteSavedFont(final Context context, final Object fragment,
                                              final Object adapter, final CustomFontEntry entry) {
        if (context == null) {
            return;
        }
        try {
            new AlertDialog.Builder(context)
                    .setTitle("\u5220\u9664\u672c\u5730\u5b57\u4f53")
                    .setMessage("\u5220\u9664\u540e\uff0c\u5b57\u4f53\u98ce\u683c\u5217\u8868\u4e2d\u5c06\u4e0d\u518d\u663e\u793a\u8fd9\u4e2a\u672c\u5730\u5b57\u4f53\u3002")
                    .setPositiveButton("\u5220\u9664", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            deleteSavedCustomFont(context, fragment, adapter, entry);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Throwable t) {
            XposedBridge.log(TAG + " show delete dialog failed");
            XposedBridge.log(t);
            deleteSavedCustomFont(context, fragment, adapter, entry);
        }
    }

    private static void deleteSavedCustomFont(Context context, Object fragment, Object adapter,
                                              CustomFontEntry entry) {
        try {
            boolean wasActive = isCustomFontActive(context, entry);
            removeSavedFontEntry(context, entry);
            if (entry.file.isFile()) {
                entry.file.delete();
            }
            deleteFontDirectory(context, entry.id);
            if (wasActive) {
                applyDefaultFont(context);
                if (fragment != null) {
                    setFragmentFontIndex(fragment, 0);
                }
                if (adapter != null) {
                    XposedHelpers.callMethod(adapter, "setItemChecked", 0);
                }
            }
            refreshCustomFontItem(adapter);
            toast(context, "\u5df2\u5220\u9664\u672c\u5730\u5b57\u4f53");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " deleteSavedCustomFont failed");
            XposedBridge.log(t);
            toast(context, "\u5220\u9664\u672c\u5730\u5b57\u4f53\u5931\u8d25\uff1a" + safeMessage(t));
        }
    }

    private static void applyDefaultFont(Context context) throws Throwable {
        Class<?> fontWriterClass = XposedHelpers.findClass(
                "com.samsung.android.fontutil.FontWriter", context.getClassLoader());
        Object fontWriter = XposedHelpers.newInstance(fontWriterClass);
        XposedHelpers.callMethod(fontWriter, "deleteFontDirectory", " ");
        XposedHelpers.callMethod(fontWriter, "writeLoc", "default#default");
        writeFlipFontSettings(context, 0);
        updateGlobalFlipFontValue(1);
    }

    private static void deleteFontDirectory(Context context, String name) {
        try {
            Class<?> fontWriterClass = XposedHelpers.findClass(
                    "com.samsung.android.fontutil.FontWriter", context.getClassLoader());
            Object fontWriter = XposedHelpers.newInstance(fontWriterClass);
            XposedHelpers.callMethod(fontWriter, "deleteFontDirectory", name);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " deleteFontDirectory failed");
            XposedBridge.log(t);
        }
    }

    private static void removeSavedFontEntry(Context context, CustomFontEntry entry) {
        if (context == null || entry == null) {
            return;
        }
        ArrayList ids = getSavedFontIds(context);
        ids.remove(entry.id);
        getPrefs(context)
                .edit()
                .putString(PREF_IDS, joinIds(ids))
                .remove(PREF_TITLE_PREFIX + entry.id)
                .apply();
    }

    private static boolean isCustomFontActive(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Method method = Typeface.class.getMethod(
                    "semGetFontPathOfCurrentFontStyle", Context.class, int.class);
            Object result = method.invoke(null, context, 1);
            return result != null && result.toString().contains(CUSTOM_FONT_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isCustomFontActive(Context context, CustomFontEntry entry) {
        if (context == null || entry == null) {
            return false;
        }
        try {
            Method method = Typeface.class.getMethod(
                    "semGetFontPathOfCurrentFontStyle", Context.class, int.class);
            Object result = method.invoke(null, context, 1);
            return result != null && result.toString().contains(entry.id);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void setFragmentFontIndex(Object fragment, int index) {
        try {
            XposedHelpers.setIntField(fragment, "mPreviousFontIndex",
                    XposedHelpers.getIntField(fragment, "mCurrentFontIndex"));
            XposedHelpers.setIntField(fragment, "mCurrentFontIndex", index);
        } catch (Throwable ignored) {
        }
    }

    private static Object getFontListAdapter(Object fragment) {
        if (fragment == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(fragment, "mFontListAdapter");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context getAdapterContext(Object adapter) {
        if (adapter == null) {
            return null;
        }
        try {
            Object context = XposedHelpers.getObjectField(adapter, "mContext");
            return context instanceof Context ? (Context) context : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List getListField(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            Object value = XposedHelpers.getObjectField(object, fieldName);
            return value instanceof List ? (List) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean getBooleanField(Object object, String fieldName, boolean fallback) {
        if (object == null) {
            return fallback;
        }
        try {
            Object value = XposedHelpers.getObjectField(object, fieldName);
            return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static File copyUriToTempFile(Context context, Uri uri) throws IOException {
        File dir = new File(context.getCacheDir(), "sfe-custom-font");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create cache directory");
        }
        File[] oldFiles = dir.listFiles();
        if (oldFiles != null) {
            for (File file : oldFiles) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
        File target = new File(dir, "selected.ttf");
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new IOException("cannot open selected font");
            }
            outputStream = new FileOutputStream(target);
            byte[] buffer = new byte[16384];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
        }
        return target;
    }

    private static void tryTakeReadPermission(Context context, Intent data, Uri uri) {
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags == 0) {
            return;
        }
        try {
            context.getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Throwable ignored) {
        }
    }

    private static String getDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String lastPathSegment = uri.getLastPathSegment();
        return TextUtils.isEmpty(lastPathSegment) ? "custom.ttf" : lastPathSegment;
    }

    private static boolean isTtfName(String displayName) {
        return displayName != null
                && displayName.toLowerCase(Locale.US).trim().endsWith(".ttf");
    }

    private static String stripTtfExtension(String displayName) {
        String name = TextUtils.isEmpty(displayName) ? "Custom TTF" : displayName.trim();
        if (name.toLowerCase(Locale.US).endsWith(".ttf")) {
            name = name.substring(0, name.length() - 4);
        }
        return TextUtils.isEmpty(name) ? "Custom TTF" : name;
    }

    private static boolean isDuplicateResult(Uri uri) {
        long now = SystemClock.elapsedRealtime();
        String token = uri.toString();
        synchronized (SettingsCustomFontHook.class) {
            if (token.equals(sLastResultToken) && now - sLastResultTime < DUPLICATE_RESULT_WINDOW_MS) {
                return true;
            }
            sLastResultToken = token;
            sLastResultTime = now;
            return false;
        }
    }

    private static boolean isFontStyleFragment(Object object) {
        return object != null && FONT_FRAGMENT.equals(object.getClass().getName());
    }

    private static Context getContext(Object fragment) {
        try {
            Object context = XposedHelpers.getObjectField(fragment, "mContext");
            if (context instanceof Context) {
                return (Context) context;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object context = XposedHelpers.callMethod(fragment, "getContext");
            return context instanceof Context ? (Context) context : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Activity getActivity(Object fragment) {
        try {
            Object activity = XposedHelpers.getObjectField(fragment, "mActivity");
            if (activity instanceof Activity) {
                return (Activity) activity;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object activity = XposedHelpers.callMethod(fragment, "getActivity");
            return activity instanceof Activity ? (Activity) activity : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void finishFragment(Object fragment) {
        try {
            XposedHelpers.callMethod(fragment, "finish");
            return;
        } catch (Throwable ignored) {
        }
        Activity activity = getActivity(fragment);
        if (activity != null) {
            activity.finish();
        }
    }

    private static void closeQuietly(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.getClass().getMethod("close").invoke(closeable);
        } catch (Throwable ignored) {
        }
    }

    private static void toast(Context context, final String text) {
        if (context == null) {
            return;
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        return TextUtils.isEmpty(message) ? t.getClass().getSimpleName() : message;
    }

    private static void log(String message) {
        XposedBridge.log(TAG + " " + message);
    }

    private static final class CustomFontEntry {
        final String id;
        final String title;
        final File file;

        CustomFontEntry(String id, String title, File file) {
            this.id = id;
            this.title = title;
            this.file = file;
        }
    }
}
