package com.codex.myfileswebdavpopup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class BixbyOpenAiSettingsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (BixbyOpenAiConfig.METHOD_SET.equals(method) && extras != null) {
            BixbyOpenAiConfig.saveLocal(context, BixbyOpenAiConfig.fromBundle(extras));
        }
        return BixbyOpenAiConfig.loadLocal(context).toBundle();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
