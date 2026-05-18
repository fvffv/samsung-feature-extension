package com.codex.myfileswebdavpopup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

public final class LauncherIconCustomizerStore {
    public static final String AUTHORITY = "com.codex.myfileswebdavpopup.launchericons";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_HAS = "has";
    public static final String METHOD_DELETE = "delete";
    public static final String METHOD_LIST = "list";
    public static final String METHOD_LABEL = "label";
    public static final String METHOD_DELETE_LABEL = "delete_label";
    public static final String METHOD_DELETE_LABEL_COLOR = "delete_label_color";
    public static final String METHOD_DELETE_LABEL_FONT = "delete_label_font";
    public static final String EXTRA_EXISTS = "exists";
    public static final String EXTRA_HAS_LABEL = "hasLabel";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_HAS_LABEL_COLOR = "hasLabelColor";
    public static final String EXTRA_LABEL_COLOR = "labelColor";
    public static final String EXTRA_HAS_LABEL_FONT = "hasLabelFont";
    public static final String EXTRA_LABEL_FONT_FAMILY = "labelFontFamily";
    public static final String EXTRA_LABEL_FONT_BOLD = "labelFontBold";
    public static final String EXTRA_LABEL_FONT_ITALIC = "labelFontItalic";
    public static final String EXTRA_LABEL_FONT_GRADIENT = "labelFontGradient";
    public static final String EXTRA_LABEL_FONT_GRADIENT_START = "labelFontGradientStart";
    public static final String EXTRA_LABEL_FONT_GRADIENT_END = "labelFontGradientEnd";
    public static final String EXTRA_LABEL_FONT_USE_FILE = "labelFontUseFile";
    public static final String EXTRA_LABEL_FONT_FILE_LABEL = "labelFontFileLabel";
    public static final String EXTRA_LABEL_FONT_FILE_UPDATED_AT = "labelFontFileUpdatedAt";
    public static final String EXTRA_UPDATED_AT = "updatedAt";
    public static final String EXTRA_PACKAGES = "packages";
    public static final String ACTION_CHANGED = "com.codex.myfileswebdavpopup.LAUNCHER_ICON_CHANGED";
    public static final String EXTRA_PACKAGE = "packageName";

    private static final String DIR_NAME = "launcher_custom_icons";
    private static final String FONT_DIR_NAME = "launcher_custom_fonts";
    private static final String LABEL_PREFS_NAME = "launcher_custom_labels";
    private static final String LABEL_COLOR_PREFS_NAME = "launcher_custom_label_colors";
    private static final String LABEL_FONT_PREFS_NAME = "launcher_custom_label_fonts";
    public static final String FONT_FAMILY_DEFAULT = "default";
    public static final String FONT_FAMILY_SANS = "sans-serif";
    public static final String FONT_FAMILY_SERIF = "serif";
    public static final String FONT_FAMILY_MONOSPACE = "monospace";
    public static final String FONT_FAMILY_CONDENSED = "sans-serif-condensed";
    public static final String FONT_FAMILY_MEDIUM = "sans-serif-medium";
    public static final int DEFAULT_GRADIENT_START = 0xFF38BDF8;
    public static final int DEFAULT_GRADIENT_END = 0xFFEC4899;
    public static final int STORED_ICON_SIZE = 512;

    private LauncherIconCustomizerStore() {
    }

    public static Uri iconUri(String packageName) {
        return BASE_URI.buildUpon()
                .appendPath("icon")
                .appendPath(packageName == null ? "" : packageName)
                .build();
    }

    public static Uri fontUri(String packageName) {
        return BASE_URI.buildUpon()
                .appendPath("font")
                .appendPath(packageName == null ? "" : packageName)
                .build();
    }

    public static boolean hasCustomIcon(Context context, String packageName) {
        File file = iconFile(context, packageName);
        return file != null && file.isFile() && file.length() > 0;
    }

    public static long updatedAt(Context context, String packageName) {
        File file = iconFile(context, packageName);
        return file != null && file.isFile() ? file.lastModified() : 0L;
    }

    public static boolean deleteIcon(Context context, String packageName) {
        File file = iconFile(context, packageName);
        return file != null && (!file.exists() || file.delete());
    }

    public static boolean hasCustomLabel(Context context, String packageName) {
        return customLabel(context, packageName) != null;
    }

    public static boolean hasCustomLabelColor(Context context, String packageName) {
        return customLabelColor(context, packageName) != null;
    }

    public static boolean hasCustomLabelFont(Context context, String packageName) {
        return customLabelFont(context, packageName) != null;
    }

    public static String customLabel(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        String value = labelPrefs(context).getString(packageName, null);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.length() == 0 ? null : value;
    }

    public static void saveLabel(Context context, String packageName, String label) throws IOException {
        if (context == null) {
            throw new IOException("Context is null");
        }
        if (packageName == null || packageName.length() == 0) {
            throw new IOException("Package name is empty");
        }
        if (label == null || label.trim().length() == 0) {
            deleteLabel(context, packageName);
            return;
        }
        String trimmed = label.trim();
        labelPrefs(context).edit().putString(packageName, trimmed).apply();
        LauncherIconLog.log("saveLabel package=" + packageName + ", label=" + trimmed);
    }

    public static Integer customLabelColor(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        SharedPreferences prefs = labelColorPrefs(context);
        return prefs.contains(packageName)
                ? Integer.valueOf(prefs.getInt(packageName, 0))
                : null;
    }

    public static void saveLabelColor(Context context, String packageName, int color) throws IOException {
        if (context == null) {
            throw new IOException("Context is null");
        }
        if (packageName == null || packageName.length() == 0) {
            throw new IOException("Package name is empty");
        }
        int opaqueColor = color | 0xFF000000;
        labelColorPrefs(context).edit().putInt(packageName, opaqueColor).apply();
        LauncherIconLog.log("saveLabelColor package=" + packageName + ", color=" + opaqueColor);
    }

    public static LabelFont customLabelFont(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        String value = labelFontPrefs(context).getString(packageName, null);
        LabelFont font = decodeLabelFont(value);
        if (font == null) {
            return null;
        }
        File file = fontFile(context, packageName);
        boolean useFile = font.useFile && file != null && file.isFile() && file.length() > 0;
        LabelFont resolved = new LabelFont(
                font.family,
                font.bold,
                font.italic,
                font.gradient,
                font.gradientStart,
                font.gradientEnd,
                useFile,
                useFile ? sanitizeFileLabel(font.fileLabel) : null,
                useFile ? file.lastModified() : 0L
        );
        return resolved.hasCustomEffect() ? resolved : null;
    }

    public static void saveLabelFont(Context context, String packageName, LabelFont font) throws IOException {
        if (context == null) {
            throw new IOException("Context is null");
        }
        if (packageName == null || packageName.length() == 0) {
            throw new IOException("Package name is empty");
        }
        LabelFont clean = sanitizeLabelFont(font);
        if (clean == null || !clean.hasCustomEffect()) {
            deleteLabelFont(context, packageName);
            LauncherIconLog.log("saveLabelFont cleared package=" + packageName);
            return;
        }
        labelFontPrefs(context).edit().putString(packageName, encodeLabelFont(clean)).apply();
        LauncherIconLog.log("saveLabelFont package=" + packageName
                + ", family=" + clean.family
                + ", bold=" + clean.bold
                + ", italic=" + clean.italic
                + ", gradient=" + clean.gradient
                + ", useFile=" + clean.useFile
                + ", fileLabel=" + clean.fileLabel);
    }

    public static String saveLabelFontFile(Context context, String packageName, Uri uri) throws IOException {
        if (context == null) {
            throw new IOException("Context is null");
        }
        if (packageName == null || packageName.length() == 0) {
            throw new IOException("Package name is empty");
        }
        if (uri == null) {
            throw new IOException("Font uri is null");
        }
        File file = fontFile(context, packageName);
        if (file == null) {
            throw new IOException("Font file unavailable");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create font directory");
        }
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                throw new IOException("Font input is null");
            }
            output = new FileOutputStream(file, false);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            String label = queryDisplayName(context.getContentResolver(), uri);
            LauncherIconLog.log("saveLabelFontFile package=" + packageName
                    + ", file=" + file.getAbsolutePath()
                    + ", length=" + file.length()
                    + ", label=" + label);
            return label;
        } finally {
            closeQuietly(output);
            closeQuietly(input);
        }
    }

    public static boolean deleteLabel(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return false;
        }
        boolean existed = labelPrefs(context).contains(packageName);
        labelPrefs(context).edit().remove(packageName).apply();
        return existed;
    }

    public static boolean deleteLabelColor(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return false;
        }
        boolean existed = labelColorPrefs(context).contains(packageName);
        labelColorPrefs(context).edit().remove(packageName).apply();
        return existed;
    }

    public static boolean deleteLabelFont(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return false;
        }
        boolean existed = labelFontPrefs(context).contains(packageName);
        labelFontPrefs(context).edit().remove(packageName).apply();
        return deleteLabelFontFile(context, packageName) || existed;
    }

    public static boolean deleteLabelFontFile(Context context, String packageName) {
        File file = fontFile(context, packageName);
        return file != null && file.exists() && file.delete();
    }

    public static ArrayList<String> listPackages(Context context) {
        ArrayList<String> packages = new ArrayList<String>();
        if (context == null) {
            return packages;
        }
        File dir = new File(context.getFilesDir(), DIR_NAME);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file == null || !file.isFile() || file.length() <= 0) {
                    continue;
                }
                String name = file.getName();
                if (name == null || !name.endsWith(".png") || name.length() <= 4) {
                    continue;
                }
                packages.add(name.substring(0, name.length() - 4));
            }
        }
        Map<String, ?> labels = labelPrefs(context).getAll();
        for (String key : labels.keySet()) {
            if (key != null && key.length() != 0 && !packages.contains(key)) {
                packages.add(key);
            }
        }
        Map<String, ?> labelColors = labelColorPrefs(context).getAll();
        for (String key : labelColors.keySet()) {
            if (key != null && key.length() != 0 && !packages.contains(key)) {
                packages.add(key);
            }
        }
        Map<String, ?> labelFonts = labelFontPrefs(context).getAll();
        for (String key : labelFonts.keySet()) {
            if (key != null && key.length() != 0 && !packages.contains(key)) {
                packages.add(key);
            }
        }
        return packages;
    }

    public static Bitmap loadStoredIcon(Context context, String packageName, int targetWidth, int targetHeight) {
        File file = iconFile(context, packageName);
        if (file == null || !file.isFile()) {
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        return scaleToTarget(bitmap, targetWidth, targetHeight);
    }

    public static Bitmap loadIconFromProvider(Context context, String packageName, int targetWidth, int targetHeight) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(iconUri(packageName));
            if (input == null) {
                LauncherIconLog.log("loadIconFromProvider input null, package=" + packageName);
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            return scaleToTarget(bitmap, targetWidth, targetHeight);
        } catch (Throwable t) {
            LauncherIconLog.log("loadIconFromProvider failed, package=" + packageName);
            LauncherIconLog.log(t);
            return null;
        } finally {
            closeQuietly(input);
        }
    }

    public static void saveIcon(Context context, String packageName, Uri imageUri) throws IOException {
        if (context == null) {
            throw new IOException("Context is null");
        }
        if (packageName == null || packageName.length() == 0) {
            throw new IOException("Package name is empty");
        }
        if (imageUri == null) {
            throw new IOException("Image uri is null");
        }
        Bitmap decoded = decodeSampledBitmap(context.getContentResolver(), imageUri, STORED_ICON_SIZE * 2);
        if (decoded == null) {
            throw new IOException("Image decode failed");
        }
        Bitmap output = centerCrop(decoded, STORED_ICON_SIZE, STORED_ICON_SIZE);
        try {
            writeIconBitmap(context, packageName, output);
        } finally {
            if (output != decoded) {
                output.recycle();
            }
            decoded.recycle();
        }
    }

    public static void saveIconBitmap(Context context, String packageName, Bitmap bitmap) throws IOException {
        if (context == null) {
            throw new IOException("Context is null");
        }
        if (packageName == null || packageName.length() == 0) {
            throw new IOException("Package name is empty");
        }
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IOException("Bitmap is unavailable");
        }
        Bitmap output = bitmap;
        if (bitmap.getWidth() != STORED_ICON_SIZE || bitmap.getHeight() != STORED_ICON_SIZE) {
            output = Bitmap.createScaledBitmap(bitmap, STORED_ICON_SIZE, STORED_ICON_SIZE, true);
        }
        try {
            writeIconBitmap(context, packageName, output);
        } finally {
            if (output != bitmap) {
                output.recycle();
            }
        }
    }

    static File iconFile(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        File dir = new File(context.getFilesDir(), DIR_NAME);
        return new File(dir, sanitize(packageName) + ".png");
    }

    static File fontFile(Context context, String packageName) {
        if (context == null || packageName == null || packageName.length() == 0) {
            return null;
        }
        File dir = new File(context.getFilesDir(), FONT_DIR_NAME);
        return new File(dir, sanitize(packageName) + ".font");
    }

    private static void writeIconBitmap(Context context, String packageName, Bitmap output) throws IOException {
        File file = iconFile(context, packageName);
        if (file == null) {
            throw new IOException("Icon file unavailable");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create icon directory");
        }
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file, false);
            if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IOException("PNG compress failed");
            }
            LauncherIconLog.log("saveIcon wrote file=" + file.getAbsolutePath()
                    + ", package=" + packageName
                    + ", length=" + file.length());
        } finally {
            closeQuietly(stream);
        }
    }

    private static Bitmap decodeSampledBitmap(ContentResolver resolver, Uri uri, int maxSize) throws IOException {
        InputStream input = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            input = resolver.openInputStream(uri);
            BitmapFactory.decodeStream(input, null, bounds);
            closeQuietly(input);
            input = null;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            input = resolver.openInputStream(uri);
            return BitmapFactory.decodeStream(input, null, opts);
        } finally {
            closeQuietly(input);
        }
    }

    private static int calculateInSampleSize(int width, int height, int maxSize) {
        int sample = 1;
        while ((width / sample) > maxSize || (height / sample) > maxSize) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleToTarget(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap == null) {
            return null;
        }
        if (targetWidth <= 0) {
            targetWidth = STORED_ICON_SIZE;
        }
        if (targetHeight <= 0) {
            targetHeight = targetWidth;
        }
        if (bitmap.getWidth() == targetWidth && bitmap.getHeight() == targetHeight) {
            return bitmap;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        bitmap.recycle();
        return scaled;
    }

    private static Bitmap centerCrop(Bitmap source, int width, int height) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int side = Math.min(sourceWidth, sourceHeight);
        int left = Math.max(0, (sourceWidth - side) / 2);
        int top = Math.max(0, (sourceHeight - side) / 2);
        Bitmap cropped = Bitmap.createBitmap(source, left, top, side, side);
        if (cropped.getWidth() == width && cropped.getHeight() == height) {
            return cropped;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, width, height, true);
        if (scaled != cropped) {
            cropped.recycle();
        }
        return scaled;
    }

    private static SharedPreferences labelPrefs(Context context) {
        return context.getSharedPreferences(LABEL_PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences labelColorPrefs(Context context) {
        return context.getSharedPreferences(LABEL_COLOR_PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences labelFontPrefs(Context context) {
        return context.getSharedPreferences(LABEL_FONT_PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String encodeLabelFont(LabelFont font) {
        return font.family
                + "|" + (font.bold ? "1" : "0")
                + "|" + (font.italic ? "1" : "0")
                + "|" + (font.gradient ? "1" : "0")
                + "|" + font.gradientStart
                + "|" + font.gradientEnd
                + "|" + (font.useFile ? "1" : "0")
                + "|" + sanitizeFileLabel(font.fileLabel);
    }

    private static LabelFont decodeLabelFont(String value) {
        if (value == null || value.length() == 0) {
            return null;
        }
        try {
            String[] parts = value.split("\\|");
            String family = parts.length > 0 ? parts[0] : FONT_FAMILY_DEFAULT;
            boolean bold = parts.length > 1 && "1".equals(parts[1]);
            boolean italic = parts.length > 2 && "1".equals(parts[2]);
            boolean gradient = parts.length > 3 && "1".equals(parts[3]);
            int gradientStart = parts.length > 4
                    ? Integer.parseInt(parts[4])
                    : DEFAULT_GRADIENT_START;
            int gradientEnd = parts.length > 5
                    ? Integer.parseInt(parts[5])
                    : DEFAULT_GRADIENT_END;
            boolean useFile = parts.length > 6 && "1".equals(parts[6]);
            String fileLabel = parts.length > 7 ? parts[7] : null;
            return sanitizeLabelFont(new LabelFont(
                    family,
                    bold,
                    italic,
                    gradient,
                    gradientStart,
                    gradientEnd,
                    useFile,
                    fileLabel,
                    0L
            ));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LabelFont sanitizeLabelFont(LabelFont font) {
        if (font == null) {
            return null;
        }
        return new LabelFont(
                sanitizeFontFamily(font.family),
                font.bold,
                font.italic,
                font.gradient,
                font.gradientStart | 0xFF000000,
                font.gradientEnd | 0xFF000000,
                font.useFile,
                sanitizeFileLabel(font.fileLabel),
                font.fileUpdatedAt
        );
    }

    private static String sanitizeFontFamily(String family) {
        if (FONT_FAMILY_SANS.equals(family)
                || FONT_FAMILY_SERIF.equals(family)
                || FONT_FAMILY_MONOSPACE.equals(family)
                || FONT_FAMILY_CONDENSED.equals(family)
                || FONT_FAMILY_MEDIUM.equals(family)) {
            return family;
        }
        return FONT_FAMILY_DEFAULT;
    }

    private static String sanitizeFileLabel(String label) {
        if (label == null) {
            return "";
        }
        String trimmed = label.trim().replace('|', '_');
        return trimmed.length() == 0 ? "" : trimmed;
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null) {
            return "";
        }
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return sanitizeFileLabel(cursor.getString(index));
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the URI tail when the provider does not expose metadata.
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                    // Ignore cleanup failure.
                }
            }
        }
        String tail = uri.getLastPathSegment();
        return sanitizeFileLabel(tail != null ? tail : "");
    }

    private static String sanitize(String packageName) {
        StringBuilder builder = new StringBuilder(packageName.length());
        for (int i = 0; i < packageName.length(); i++) {
            char ch = packageName.charAt(i);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.'
                    || ch == '_'
                    || ch == '-') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private static void closeQuietly(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            if (closeable instanceof InputStream) {
                ((InputStream) closeable).close();
            } else if (closeable instanceof FileOutputStream) {
                ((FileOutputStream) closeable).close();
            }
        } catch (Throwable ignored) {
            // Ignore cleanup failure.
        }
    }

    public static final class LabelFont {
        public final String family;
        public final boolean bold;
        public final boolean italic;
        public final boolean gradient;
        public final int gradientStart;
        public final int gradientEnd;
        public final boolean useFile;
        public final String fileLabel;
        public final long fileUpdatedAt;

        public LabelFont(String family, boolean bold, boolean italic,
                         boolean gradient, int gradientStart, int gradientEnd) {
            this(family, bold, italic, gradient, gradientStart, gradientEnd, false, null, 0L);
        }

        public LabelFont(String family, boolean bold, boolean italic,
                         boolean gradient, int gradientStart, int gradientEnd,
                         boolean useFile, String fileLabel, long fileUpdatedAt) {
            this.family = family == null || family.length() == 0 ? FONT_FAMILY_DEFAULT : family;
            this.bold = bold;
            this.italic = italic;
            this.gradient = gradient;
            this.gradientStart = gradientStart | 0xFF000000;
            this.gradientEnd = gradientEnd | 0xFF000000;
            this.useFile = useFile;
            this.fileLabel = sanitizeFileLabel(fileLabel);
            this.fileUpdatedAt = fileUpdatedAt;
        }

        public boolean hasCustomEffect() {
            return !FONT_FAMILY_DEFAULT.equals(family) || bold || italic || gradient || useFile;
        }
    }
}
