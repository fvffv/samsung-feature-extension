package com.samsung.feature.extension;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class FingerprintStyleSettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.samsung.feature.extension.fingerprintstyle";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";
    public static final String METHOD_SET_ENABLED = "setEnabled";
    public static final String METHOD_SET_LOOP = "setLoop";
    public static final String METHOD_CLEAR = "clear";

    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_LOOP = "loop";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_UPDATED_AT = "updatedAt";
    public static final String EXTRA_AVAILABLE = "available";

    public static final String TYPE_ANIMATION = "animation";
    public static final String TYPE_PNG = "png";
    public static final String SOURCE_PNG = "png";

    private static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    private static final String PREFS = "fingerprint_style";
    private static final String DIR = "fingerprint_style";
    private static final String CUSTOM_DIR = "custom_lottie";
    private static final String FILE_JSON = "fingerprint_icon.json";
    private static final String FILE_PNG = "fingerprint_icon.png";
    private static final String SOURCE_DEFAULT_PREFIX = "default:";
    private static final String SOURCE_CUSTOM_PREFIX = "custom:";
    private static final BuiltInMaterial[] BUILT_IN_MATERIALS = new BuiltInMaterial[]{
            new BuiltInMaterial("default:01", "rainbow cat remix", R.raw.fingerprint_material_01),
            new BuiltInMaterial("default:02", "Poop Emoji", R.raw.fingerprint_material_02),
            new BuiltInMaterial("default:03", "Money", R.raw.fingerprint_material_03),
            new BuiltInMaterial("default:04", "fingerprint", R.raw.fingerprint_material_04),
            new BuiltInMaterial("default:05", "Fake 3D vector coin", R.raw.fingerprint_material_05),
            new BuiltInMaterial("default:06", "Cat in Box", R.raw.fingerprint_material_06),
            new BuiltInMaterial("default:07", "cat Mark loading", R.raw.fingerprint_material_07),
            new BuiltInMaterial("default:08", "Bulldog and Bull", R.raw.fingerprint_material_08),
            new BuiltInMaterial("default:09", "black rainbow cat", R.raw.fingerprint_material_09)
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (METHOD_SET_ENABLED.equals(method)) {
            setEnabled(context, extras != null && extras.getBoolean(EXTRA_ENABLED, false));
        } else if (METHOD_SET_LOOP.equals(method)) {
            setLoop(context, extras == null || extras.getBoolean(EXTRA_LOOP, true));
        } else if (METHOD_CLEAR.equals(method)) {
            clear(context);
        }
        return getLocalSettings(context).toBundle();
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.indexOf('w') >= 0) {
            throw new FileNotFoundException("Read only");
        }
        String type = typeFromUri(uri);
        File file = fileForType(getContext(), type);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("No fingerprint style file for " + type);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return TYPE_PNG.equals(typeFromUri(uri)) ? "image/png" : "application/json";
    }

    public static Settings getSettings(Context context) {
        if (context == null) {
            return Settings.empty();
        }
        try {
            if (MODULE_PACKAGE.equals(context.getPackageName())) {
                return getLocalSettings(context);
            }
        } catch (Throwable ignored) {
            // Fall through to the exported provider.
        }
        try {
            Bundle result = context.getContentResolver().call(URI, METHOD_GET, null, null);
            if (result != null) {
                return Settings.fromBundle(result);
            }
        } catch (Throwable ignored) {
            // Keep biometric UI stable if the module provider is not ready.
        }
        return Settings.empty();
    }

    public static Settings getLocalSettings(Context context) {
        if (context == null) {
            return Settings.empty();
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String type = sanitizeType(prefs.getString(EXTRA_TYPE, TYPE_ANIMATION));
            File file = fileForType(context, type);
            boolean available = file != null && file.isFile();
            boolean enabled = prefs.getBoolean(EXTRA_ENABLED, false) && available;
            boolean loop = prefs.getBoolean(EXTRA_LOOP, true);
            String label = prefs.getString(EXTRA_LABEL, "");
            String source = prefs.getString(EXTRA_SOURCE, "");
            long updatedAt = prefs.getLong(EXTRA_UPDATED_AT, 0L);
            return new Settings(enabled, available, loop, type, label, source, updatedAt);
        } catch (Throwable ignored) {
            return Settings.empty();
        }
    }

    public static boolean saveCustomFile(Context context, Uri uri, String type, String fallbackLabel) {
        if (context == null || uri == null) {
            return false;
        }
        String safeType = sanitizeType(type);
        if (TYPE_ANIMATION.equals(safeType)) {
            return saveCustomAnimationFile(context, uri, fallbackLabel);
        }
        File target = fileForType(context, safeType);
        if (target == null) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        InputStream input = null;
        OutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                return false;
            }
            output = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
            output.flush();
            File other = fileForType(context, TYPE_ANIMATION.equals(safeType) ? TYPE_PNG : TYPE_ANIMATION);
            if (other != null && other.isFile()) {
                other.delete();
            }
            String label = queryDisplayName(context.getContentResolver(), uri);
            if (label == null || label.length() == 0) {
                label = fallbackLabel != null ? fallbackLabel : "";
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(EXTRA_ENABLED, true)
                    .putString(EXTRA_TYPE, safeType)
                    .putString(EXTRA_LABEL, label)
                    .putString(EXTRA_SOURCE, SOURCE_PNG)
                    .putLong(EXTRA_UPDATED_AT, System.currentTimeMillis())
                    .apply();
            notifyChanged(context);
            return true;
        } catch (Throwable ignored) {
            if (target.isFile()) {
                target.delete();
            }
            return false;
        } finally {
            closeQuietly(output);
            closeQuietly(input);
        }
    }

    public static List<MaterialItem> listMaterials(Context context) {
        ArrayList<MaterialItem> items = new ArrayList<MaterialItem>();
        for (BuiltInMaterial material : BUILT_IN_MATERIALS) {
            items.add(new MaterialItem(material.source, material.label, false));
        }
        File dir = customDir(context);
        File[] files = dir != null && dir.isDirectory() ? dir.listFiles() : null;
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File left, File right) {
                    long delta = left.lastModified() - right.lastModified();
                    if (delta < 0) {
                        return -1;
                    }
                    if (delta > 0) {
                        return 1;
                    }
                    return left.getName().compareTo(right.getName());
                }
            });
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    items.add(new MaterialItem(SOURCE_CUSTOM_PREFIX + file.getName(),
                            labelFromCustomFile(file), true));
                }
            }
        }
        return items;
    }

    public static boolean selectMaterial(Context context, String source) {
        if (context == null || source == null) {
            return false;
        }
        BuiltInMaterial material = findBuiltInMaterial(source);
        if (material != null) {
            InputStream input = null;
            try {
                input = context.getResources().openRawResource(material.resId);
                return activateAnimation(context, input, material.label, material.source);
            } catch (Throwable ignored) {
                return false;
            } finally {
                closeQuietly(input);
            }
        }
        File custom = customFileFromSource(context, source);
        if (custom == null || !custom.isFile()) {
            return false;
        }
        InputStream input = null;
        try {
            input = new FileInputStream(custom);
            return activateAnimation(context, input, labelFromCustomFile(custom), source);
        } catch (Throwable ignored) {
            return false;
        } finally {
            closeQuietly(input);
        }
    }

    public static boolean deleteMaterial(Context context, String source) {
        if (context == null || source == null || !source.startsWith(SOURCE_CUSTOM_PREFIX)) {
            return false;
        }
        File custom = customFileFromSource(context, source);
        if (custom == null || !custom.isFile() || !custom.delete()) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (source.equals(prefs.getString(EXTRA_SOURCE, ""))) {
            clear(context);
        } else {
            notifyChanged(context);
        }
        return true;
    }

    public static InputStream openMaterialInputStream(Context context, String source) throws FileNotFoundException {
        if (context == null || source == null) {
            return null;
        }
        BuiltInMaterial material = findBuiltInMaterial(source);
        if (material != null) {
            return context.getResources().openRawResource(material.resId);
        }
        File custom = customFileFromSource(context, source);
        if (custom != null && custom.isFile()) {
            return new FileInputStream(custom);
        }
        return null;
    }

    public static String materialCacheKey(String source) {
        return "fingerprint-material-" + (source != null ? source : "");
    }

    public static int materialRawResId(String source) {
        BuiltInMaterial material = findBuiltInMaterial(source);
        return material != null ? material.resId : 0;
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(EXTRA_ENABLED, enabled)
                .apply();
        notifyChanged(context);
    }

    public static void setLoop(Context context, boolean loop) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(EXTRA_LOOP, loop)
                .apply();
        notifyChanged(context);
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        File json = fileForType(context, TYPE_ANIMATION);
        File png = fileForType(context, TYPE_PNG);
        if (json != null && json.isFile()) {
            json.delete();
        }
        if (png != null && png.isFile()) {
            png.delete();
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        notifyChanged(context);
    }

    public static Uri fileUri(String type) {
        return URI.buildUpon().appendPath("icon").appendPath(sanitizeType(type)).build();
    }

    private static File fileForType(Context context, String type) {
        if (context == null) {
            return null;
        }
        File dir = new File(context.getFilesDir(), DIR);
        return new File(dir, TYPE_PNG.equals(sanitizeType(type)) ? FILE_PNG : FILE_JSON);
    }

    private static File customDir(Context context) {
        if (context == null) {
            return null;
        }
        return new File(new File(context.getFilesDir(), DIR), CUSTOM_DIR);
    }

    private static boolean saveCustomAnimationFile(Context context, Uri uri, String fallbackLabel) {
        String label = queryDisplayName(context.getContentResolver(), uri);
        if (label == null || label.length() == 0) {
            label = fallbackLabel != null ? fallbackLabel : "fingerprint_icon.json";
        }
        File dir = customDir(context);
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            return false;
        }
        File target = new File(dir, buildCustomFileName(label));
        InputStream input = null;
        OutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                return false;
            }
            output = new FileOutputStream(target);
            copyStream(input, output);
            output.flush();
        } catch (Throwable ignored) {
            if (target.isFile()) {
                target.delete();
            }
            return false;
        } finally {
            closeQuietly(output);
            closeQuietly(input);
        }
        String source = SOURCE_CUSTOM_PREFIX + target.getName();
        if (!selectMaterial(context, source)) {
            target.delete();
            return false;
        }
        return true;
    }

    private static boolean activateAnimation(Context context, InputStream input, String label, String source) {
        if (context == null || input == null) {
            return false;
        }
        File target = fileForType(context, TYPE_ANIMATION);
        if (target == null) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        File temp = new File(parent, FILE_JSON + ".tmp");
        OutputStream output = null;
        try {
            output = new FileOutputStream(temp);
            copyStream(input, output);
            output.flush();
            closeQuietly(output);
            output = null;
            if (target.isFile() && !target.delete()) {
                temp.delete();
                return false;
            }
            if (!temp.renameTo(target)) {
                temp.delete();
                return false;
            }
            File png = fileForType(context, TYPE_PNG);
            if (png != null && png.isFile()) {
                png.delete();
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(EXTRA_ENABLED, true)
                    .putString(EXTRA_TYPE, TYPE_ANIMATION)
                    .putString(EXTRA_LABEL, label != null ? label : "")
                    .putString(EXTRA_SOURCE, source != null ? source : "")
                    .putLong(EXTRA_UPDATED_AT, System.currentTimeMillis())
                    .apply();
            notifyChanged(context);
            return true;
        } catch (Throwable ignored) {
            temp.delete();
            return false;
        } finally {
            closeQuietly(output);
        }
    }

    private static BuiltInMaterial findBuiltInMaterial(String source) {
        for (BuiltInMaterial material : BUILT_IN_MATERIALS) {
            if (material.source.equals(source)) {
                return material;
            }
        }
        return null;
    }

    private static File customFileFromSource(Context context, String source) {
        if (source == null || !source.startsWith(SOURCE_CUSTOM_PREFIX)) {
            return null;
        }
        String name = source.substring(SOURCE_CUSTOM_PREFIX.length());
        if (name.length() == 0 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            return null;
        }
        File dir = customDir(context);
        return dir != null ? new File(dir, name) : null;
    }

    private static String buildCustomFileName(String label) {
        String safe = safeFilePart(label);
        if (safe.endsWith(".json")) {
            safe = safe.substring(0, safe.length() - 5);
        }
        if (safe.length() == 0) {
            safe = "lottie";
        }
        return "custom_" + System.currentTimeMillis() + "_" + safe + ".json";
    }

    private static String safeFilePart(String label) {
        if (label == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < label.length() && builder.length() < 80; i++) {
            char ch = label.charAt(i);
            if (ch < 32 || ch == '/' || ch == '\\' || ch == ':' || ch == '*' || ch == '?'
                    || ch == '"' || ch == '<' || ch == '>' || ch == '|') {
                builder.append('_');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString().trim();
    }

    private static String labelFromCustomFile(File file) {
        if (file == null) {
            return "";
        }
        String name = file.getName();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        if (name.startsWith("custom_")) {
            int next = name.indexOf('_', "custom_".length());
            if (next >= 0 && next + 1 < name.length()) {
                name = name.substring(next + 1);
            }
        }
        return name.length() > 0 ? name : file.getName();
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static String sanitizeType(String type) {
        return TYPE_PNG.equals(type) ? TYPE_PNG : TYPE_ANIMATION;
    }

    private static String typeFromUri(Uri uri) {
        if (uri == null) {
            return TYPE_ANIMATION;
        }
        List<String> segments = uri.getPathSegments();
        if (segments == null || segments.isEmpty()) {
            return TYPE_ANIMATION;
        }
        return sanitizeType(segments.get(segments.size() - 1));
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && name.length() > 0) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
            // Fall back below.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return uri.getLastPathSegment();
    }

    private static void notifyChanged(Context context) {
        try {
            context.getContentResolver().notifyChange(URI, null);
        } catch (Throwable ignored) {
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeQuietly(OutputStream output) {
        if (output != null) {
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        clear(getContext());
        return 1;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    public static final class Settings {
        public final boolean enabled;
        public final boolean available;
        public final boolean loop;
        public final String type;
        public final String label;
        public final String source;
        public final long updatedAt;

        Settings(boolean enabled, boolean available, boolean loop, String type, String label,
                 String source, long updatedAt) {
            this.enabled = enabled;
            this.available = available;
            this.loop = loop;
            this.type = sanitizeType(type);
            this.label = label != null ? label : "";
            this.source = source != null ? source : "";
            this.updatedAt = updatedAt;
        }

        Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putBoolean(EXTRA_ENABLED, enabled);
            bundle.putBoolean(EXTRA_AVAILABLE, available);
            bundle.putBoolean(EXTRA_LOOP, loop);
            bundle.putString(EXTRA_TYPE, type);
            bundle.putString(EXTRA_LABEL, label);
            bundle.putString(EXTRA_SOURCE, source);
            bundle.putLong(EXTRA_UPDATED_AT, updatedAt);
            return bundle;
        }

        static Settings fromBundle(Bundle bundle) {
            if (bundle == null) {
                return empty();
            }
            return new Settings(
                    bundle.getBoolean(EXTRA_ENABLED, false),
                    bundle.getBoolean(EXTRA_AVAILABLE, false),
                    bundle.getBoolean(EXTRA_LOOP, true),
                    bundle.getString(EXTRA_TYPE, TYPE_ANIMATION),
                    bundle.getString(EXTRA_LABEL, ""),
                    bundle.getString(EXTRA_SOURCE, ""),
                    bundle.getLong(EXTRA_UPDATED_AT, 0L)
            );
        }

        public static Settings empty() {
            return new Settings(false, false, true, TYPE_ANIMATION, "", "", 0L);
        }
    }

    public static final class MaterialItem {
        public final String source;
        public final String label;
        public final boolean custom;

        MaterialItem(String source, String label, boolean custom) {
            this.source = source != null ? source : "";
            this.label = label != null ? label : "";
            this.custom = custom;
        }
    }

    private static final class BuiltInMaterial {
        final String source;
        final String label;
        final int resId;

        BuiltInMaterial(String source, String label, int resId) {
            this.source = source;
            this.label = label;
            this.resId = resId;
        }
    }
}
