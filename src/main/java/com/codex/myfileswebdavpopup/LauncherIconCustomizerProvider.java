package com.codex.myfileswebdavpopup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public final class LauncherIconCustomizerProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        LauncherIconLog.init(getContext());
        LauncherIconLog.log("provider created");
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        String packageName = arg;
        if ((packageName == null || packageName.length() == 0) && extras != null) {
            packageName = extras.getString(LauncherIconCustomizerStore.EXTRA_PACKAGE);
        }
        Bundle result = new Bundle();
        if (LauncherIconCustomizerStore.METHOD_LIST.equals(method)) {
            ArrayList<String> packages = LauncherIconCustomizerStore.listPackages(getContext());
            result.putStringArrayList(LauncherIconCustomizerStore.EXTRA_PACKAGES, packages);
            LauncherIconLog.log("provider call method=list, count=" + packages.size());
            return result;
        }
        if (LauncherIconCustomizerStore.METHOD_LABEL.equals(method)) {
            String label = LauncherIconCustomizerStore.customLabel(getContext(), packageName);
            Integer labelColor = LauncherIconCustomizerStore.customLabelColor(getContext(), packageName);
            LauncherIconCustomizerStore.LabelFont labelFont =
                    LauncherIconCustomizerStore.customLabelFont(getContext(), packageName);
            result.putBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL, label != null);
            result.putBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL_COLOR, labelColor != null);
            result.putBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL_FONT, labelFont != null);
            if (label != null) {
                result.putString(LauncherIconCustomizerStore.EXTRA_LABEL, label);
            }
            if (labelColor != null) {
                result.putInt(LauncherIconCustomizerStore.EXTRA_LABEL_COLOR, labelColor.intValue());
            }
            putLabelFont(result, labelFont);
            if (label != null || labelColor != null || labelFont != null) {
                LauncherIconLog.log("provider call method=label, package=" + packageName
                        + ", label=" + label + ", color=" + labelColor
                        + ", font=" + (labelFont != null ? labelFont.family : null));
            }
            return result;
        }
        if (LauncherIconCustomizerStore.METHOD_DELETE_LABEL.equals(method)) {
            LauncherIconCustomizerStore.deleteLabel(getContext(), packageName);
            notifyPackageChanged(packageName);
        }
        if (LauncherIconCustomizerStore.METHOD_DELETE_LABEL_COLOR.equals(method)) {
            LauncherIconCustomizerStore.deleteLabelColor(getContext(), packageName);
            notifyPackageChanged(packageName);
        }
        if (LauncherIconCustomizerStore.METHOD_DELETE_LABEL_FONT.equals(method)) {
            LauncherIconCustomizerStore.deleteLabelFont(getContext(), packageName);
            notifyPackageChanged(packageName);
        }
        if (LauncherIconCustomizerStore.METHOD_DELETE.equals(method)) {
            LauncherIconCustomizerStore.deleteIcon(getContext(), packageName);
            notifyPackageChanged(packageName);
        }
        boolean exists = LauncherIconCustomizerStore.hasCustomIcon(getContext(), packageName);
        result.putBoolean(LauncherIconCustomizerStore.EXTRA_EXISTS, exists);
        String label = LauncherIconCustomizerStore.customLabel(getContext(), packageName);
        Integer labelColor = LauncherIconCustomizerStore.customLabelColor(getContext(), packageName);
        LauncherIconCustomizerStore.LabelFont labelFont =
                LauncherIconCustomizerStore.customLabelFont(getContext(), packageName);
        result.putBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL, label != null);
        result.putBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL_COLOR, labelColor != null);
        result.putBoolean(LauncherIconCustomizerStore.EXTRA_HAS_LABEL_FONT, labelFont != null);
        if (label != null) {
            result.putString(LauncherIconCustomizerStore.EXTRA_LABEL, label);
        }
        if (labelColor != null) {
            result.putInt(LauncherIconCustomizerStore.EXTRA_LABEL_COLOR, labelColor.intValue());
        }
        putLabelFont(result, labelFont);
        result.putLong(LauncherIconCustomizerStore.EXTRA_UPDATED_AT,
                LauncherIconCustomizerStore.updatedAt(getContext(), packageName));
        if (exists || LauncherIconCustomizerStore.METHOD_DELETE.equals(method)) {
            LauncherIconLog.log("provider call method=" + method
                    + ", package=" + packageName
                    + ", exists=" + exists
                    + ", updatedAt=" + result.getLong(LauncherIconCustomizerStore.EXTRA_UPDATED_AT));
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.indexOf('w') >= 0) {
            throw new FileNotFoundException("Read only");
        }
        String packageName = packageNameFromUri(uri);
        boolean fontRequest = isFontUri(uri);
        File file = fontRequest
                ? LauncherIconCustomizerStore.fontFile(getContext(), packageName)
                : LauncherIconCustomizerStore.iconFile(getContext(), packageName);
        if (file == null || !file.isFile()) {
            LauncherIconLog.log("provider openFile missing package=" + packageName + ", font=" + fontRequest);
            throw new FileNotFoundException((fontRequest ? "No custom font for " : "No custom icon for ") + packageName);
        }
        LauncherIconLog.log("provider openFile package=" + packageName
                + ", font=" + fontRequest + ", length=" + file.length());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return isFontUri(uri) ? "font/*" : "image/png";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        String packageName = packageNameFromUri(uri);
        boolean deleted = LauncherIconCustomizerStore.deleteIcon(getContext(), packageName);
        if (deleted) {
            notifyPackageChanged(packageName);
        }
        return deleted ? 1 : 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private String packageNameFromUri(Uri uri) {
        if (uri == null) {
            return "";
        }
        List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() < 2) {
            return "";
        }
        return segments.get(1);
    }

    private boolean isFontUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        List<String> segments = uri.getPathSegments();
        return segments != null && segments.size() >= 2 && "font".equals(segments.get(0));
    }

    private void putLabelFont(Bundle result, LauncherIconCustomizerStore.LabelFont font) {
        if (result == null || font == null) {
            return;
        }
        result.putString(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_FAMILY, font.family);
        result.putBoolean(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_BOLD, font.bold);
        result.putBoolean(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_ITALIC, font.italic);
        result.putBoolean(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_GRADIENT, font.gradient);
        result.putInt(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_GRADIENT_START, font.gradientStart);
        result.putInt(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_GRADIENT_END, font.gradientEnd);
        result.putBoolean(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_USE_FILE, font.useFile);
        result.putString(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_FILE_LABEL, font.fileLabel);
        result.putLong(LauncherIconCustomizerStore.EXTRA_LABEL_FONT_FILE_UPDATED_AT, font.fileUpdatedAt);
    }

    private void notifyPackageChanged(String packageName) {
        if (getContext() == null) {
            return;
        }
        getContext().getContentResolver().notifyChange(
                LauncherIconCustomizerStore.iconUri(packageName),
                null
        );
        getContext().getContentResolver().notifyChange(
                LauncherIconCustomizerStore.BASE_URI,
                null
        );
        LauncherIconLog.log("provider notify changed package=" + packageName);
    }
}
