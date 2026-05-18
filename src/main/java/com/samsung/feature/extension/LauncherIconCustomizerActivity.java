package com.samsung.feature.extension;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class LauncherIconCustomizerActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 7301;
    private static final int REQUEST_CROP_IMAGE = 7302;
    private static final int REQUEST_PICK_FONT = 7303;
    private static final int FLAG_RECEIVER_INCLUDE_BACKGROUND_COMPAT = 0x01000000;
    private static final String STATE_PENDING_PACKAGE = "pendingPackageName";
    private static final int DEFAULT_LABEL_PREVIEW_COLOR = Color.rgb(24, 29, 36);
    private static final int[] LABEL_COLOR_PRESETS = new int[]{
            0xFFFFFFFF,
            0xFF111827,
            0xFFEF4444,
            0xFFF97316,
            0xFFFACC15,
            0xFF22C55E,
            0xFF14B8A6,
            0xFF38BDF8,
            0xFF6366F1,
            0xFFEC4899
    };
    private static final String[] LABEL_FONT_FAMILY_VALUES = new String[]{
            LauncherIconCustomizerStore.FONT_FAMILY_DEFAULT,
            LauncherIconCustomizerStore.FONT_FAMILY_SANS,
            LauncherIconCustomizerStore.FONT_FAMILY_SERIF,
            LauncherIconCustomizerStore.FONT_FAMILY_MONOSPACE,
            LauncherIconCustomizerStore.FONT_FAMILY_CONDENSED,
            LauncherIconCustomizerStore.FONT_FAMILY_MEDIUM
    };
    private static final String[] LABEL_FONT_FAMILY_LABELS = new String[]{
            "系统默认",
            "无衬线",
            "衬线",
            "等宽",
            "窄体",
            "中黑"
    };

    private final ArrayList<AppEntry> allApps = new ArrayList<AppEntry>();
    private final ArrayList<AppEntry> apps = new ArrayList<AppEntry>();
    private AppAdapter adapter;
    private String pendingPackageName;
    private String currentQuery = "";
    private FontDraft pendingFontDraft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingPackageName = savedInstanceState.getString(STATE_PENDING_PACKAGE);
        }
        LauncherIconLog.init(this);
        LauncherIconLog.log("settings activity opened");
        setTitle("桌面图标与名称自定义");
        loadApps();
        buildContentView();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_PACKAGE, pendingPackageName);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LauncherIconLog.log("onActivityResult request=" + requestCode
                + ", result=" + resultCode
                + ", pending=" + pendingPackageName
                + ", uri=" + (data != null ? data.getData() : null));

        if (requestCode == REQUEST_CROP_IMAGE) {
            if (resultCode == RESULT_OK) {
                String packageName = data != null
                        ? data.getStringExtra(LauncherIconCropActivity.EXTRA_PACKAGE)
                        : pendingPackageName;
                if (packageName == null || packageName.length() == 0) {
                    packageName = pendingPackageName;
                }
                LauncherIconLog.log("crop finished, package=" + packageName
                        + ", exists=" + LauncherIconCustomizerStore.hasCustomIcon(this, packageName)
                        + ", updatedAt=" + LauncherIconCustomizerStore.updatedAt(this, packageName));
                notifyIconChanged(packageName);
                refreshList();
                Toast.makeText(this, "已设置图标，返回桌面后生效", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == REQUEST_PICK_FONT) {
            FontDraft draft = pendingFontDraft;
            pendingFontDraft = null;
            if (draft == null) {
                return;
            }
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    if (flags != 0) {
                        try {
                            getContentResolver().takePersistableUriPermission(uri, flags);
                        } catch (Throwable ignored) {
                            // Some document providers only grant one-shot access.
                        }
                    }
                    draft.useFontFile = true;
                    draft.fontSourceUriString = uri.toString();
                    draft.fontFileLabel = fontFileLabelFromUri(uri);
                } catch (Throwable t) {
                    LauncherIconLog.log("pick font failed for " + draft.packageName);
                    LauncherIconLog.log(t);
                    Toast.makeText(this, "读取字体文件失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            AppEntry item = findAppEntry(draft.packageName);
            if (item != null) {
                editLabelFont(item, draft);
            }
            return;
        }

        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null || pendingPackageName == null) {
            return;
        }
        try {
            final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, flags);
                } catch (Throwable ignored) {
                    // Some gallery providers grant one-shot access only.
                }
            }
            Intent crop = new Intent(this, LauncherIconCropActivity.class);
            crop.setData(uri);
            crop.putExtra(LauncherIconCropActivity.EXTRA_PACKAGE, pendingPackageName);
            crop.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(crop, REQUEST_CROP_IMAGE);
        } catch (Throwable t) {
            LauncherIconLog.log("open crop failed for " + pendingPackageName);
            LauncherIconLog.log(t);
            Toast.makeText(this, "打开裁剪失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 250));
        root.setPadding(dp(20), dp(18), dp(20), dp(16));

        TextView title = new TextView(this);
        title.setText("桌面图标与名称自定义");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("为每个应用单独设置桌面图标和显示名称。名称会跟图标一样通过 One UI 主屏幕绑定链路应用。");
        subtitle.setTextColor(Color.rgb(98, 105, 117));
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(dp(2), 1.0f);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(15);
        searchBox.setHint("搜索应用名称、包名或自定义名称");
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setBackground(makeInputBackground());
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString();
                filterApps(currentQuery);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        root.addView(searchBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        TextView restart = new TextView(this);
        restart.setText("重启 One UI 主屏幕");
        restart.setGravity(Gravity.CENTER);
        restart.setTextSize(14);
        restart.setTextColor(Color.WHITE);
        restart.setIncludeFontPadding(false);
        restart.setPadding(dp(12), dp(10), dp(12), dp(10));
        restart.setBackground(makePillBackground(Color.rgb(35, 42, 54)));
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartLauncher();
            }
        });
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        restartParams.setMargins(0, dp(10), 0, dp(14));
        root.addView(restart, restartParams);

        ListView listView = new ListView(this);
        adapter = new AppAdapter();
        listView.setAdapter(adapter);
        listView.setDivider(null);
        listView.setDividerHeight(dp(10));
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                pickImage(apps.get(position).packageName);
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private void loadApps() {
        allApps.clear();
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        HashSet<String> seen = new HashSet<String>();
        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo info = resolveInfos.get(i);
            if (info == null || info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (!seen.add(packageName)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            allApps.add(new AppEntry(
                    packageName,
                    label == null ? packageName : label.toString(),
                    info.loadIcon(pm)
            ));
        }
        Collections.sort(allApps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry left, AppEntry right) {
                return left.label.compareToIgnoreCase(right.label);
            }
        });
        filterApps(currentQuery);
    }

    private void filterApps(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        apps.clear();
        for (int i = 0; i < allApps.size(); i++) {
            AppEntry entry = allApps.get(i);
            String customLabel = LauncherIconCustomizerStore.customLabel(this, entry.packageName);
            Integer customColor = LauncherIconCustomizerStore.customLabelColor(this, entry.packageName);
            LauncherIconCustomizerStore.LabelFont customFont =
                    LauncherIconCustomizerStore.customLabelFont(this, entry.packageName);
            String styleSummary = labelStyleSummary(customColor, customFont);
            if (needle.length() == 0
                    || entry.label.toLowerCase().contains(needle)
                    || entry.packageName.toLowerCase().contains(needle)
                    || (customLabel != null && customLabel.toLowerCase().contains(needle))
                    || styleSummary.toLowerCase().contains(needle)) {
                apps.add(entry);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void refreshList() {
        filterApps(currentQuery);
    }

    private void pickImage(String packageName) {
        pendingPackageName = packageName;
        LauncherIconLog.log("pickImage package=" + packageName);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (Throwable t) {
            Intent fallback = new Intent(Intent.ACTION_PICK);
            fallback.setType("image/*");
            startActivityForResult(fallback, REQUEST_PICK_IMAGE);
        }
    }

    private void editLabel(final AppEntry item) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(LauncherIconCustomizerStore.customLabel(this, item.packageName));
        input.setHint(item.label);
        input.setSelectAllOnFocus(true);
        int pad = dp(18);
        input.setPadding(pad, dp(10), pad, dp(10));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自定义桌面名称")
                .setMessage(item.label + "\n" + item.packageName)
                .setView(input)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveLabel(item.packageName, input.getText() == null ? "" : input.getText().toString());
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复名称", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        restoreLabel(item.packageName);
                    }
                })
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                input.requestFocus();
            }
        });
        dialog.show();
    }

    private void editLabelColor(final AppEntry item) {
        Integer currentColor = LauncherIconCustomizerStore.customLabelColor(this, item.packageName);
        final int[] selectedColor = new int[]{
                currentColor != null ? currentColor.intValue() : Color.WHITE
        };
        final ArrayList<TextView> swatchViews = new ArrayList<TextView>();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        content.setPadding(pad, dp(8), pad, 0);

        String customLabel = LauncherIconCustomizerStore.customLabel(this, item.packageName);
        final TextView preview = new TextView(this);
        preview.setText(customLabel != null ? customLabel : item.label);
        preview.setTextSize(20);
        preview.setGravity(Gravity.CENTER);
        preview.setSingleLine(true);
        preview.setTextColor(selectedColor[0]);
        preview.setPadding(dp(12), dp(14), dp(12), dp(14));
        preview.setBackground(makePillBackground(Color.rgb(35, 42, 54)));
        content.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        final EditText hexInput = new EditText(this);
        hexInput.setSingleLine(true);
        hexInput.setText(colorHex(selectedColor[0]));
        hexInput.setHint("#FFFFFF");
        hexInput.setSelectAllOnFocus(true);
        hexInput.setPadding(dp(14), 0, dp(14), 0);
        hexInput.setBackground(makeInputBackground());
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        inputParams.setMargins(0, dp(12), 0, dp(10));
        content.addView(hexInput, inputParams);

        LinearLayout swatchColumn = new LinearLayout(this);
        swatchColumn.setOrientation(LinearLayout.VERTICAL);
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 5; column++) {
                final int color = LABEL_COLOR_PRESETS[rowIndex * 5 + column];
                final TextView swatch = makeColorSwatch(color, color == selectedColor[0]);
                swatchViews.add(swatch);
                swatch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectedColor[0] = color;
                        hexInput.setText(colorHex(color));
                        preview.setTextColor(color);
                        for (int i = 0; i < swatchViews.size(); i++) {
                            TextView view = swatchViews.get(i);
                            Object tag = view.getTag();
                            int swatchColor = tag instanceof Integer ? ((Integer) tag).intValue() : 0;
                            view.setBackground(makeColorSwatchBackground(swatchColor, swatchColor == color));
                        }
                    }
                });
                LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
                swatchParams.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(swatch, swatchParams);
            }
            swatchColumn.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        content.addView(swatchColumn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        new AlertDialog.Builder(this)
                .setTitle("自定义名称颜色")
                .setMessage(item.label + "\n" + item.packageName)
                .setView(content)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            int color = parseLabelColorInput(hexInput.getText() == null ? "" : hexInput.getText().toString());
                            saveLabelColor(item.packageName, color);
                        } catch (IllegalArgumentException e) {
                            Toast.makeText(LauncherIconCustomizerActivity.this,
                                    "颜色格式无效，请使用 #RRGGBB", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认颜色", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        restoreLabelColor(item.packageName);
                    }
                })
                .show();
    }

    private void editLabelFont(final AppEntry item) {
        editLabelFont(item, FontDraft.fromStore(this, item.packageName));
    }

    private void editLabelFont(final AppEntry item, final FontDraft initialDraft) {
        final FontDraft draft = initialDraft != null
                ? initialDraft.copy()
                : FontDraft.fromStore(this, item.packageName);
        final int previewDefaultColor = currentPreviewColor(draft);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        content.setPadding(pad, dp(8), pad, 0);

        String customLabel = LauncherIconCustomizerStore.customLabel(this, item.packageName);
        final TextView preview = new TextView(this);
        preview.setText(customLabel != null ? customLabel : item.label);
        preview.setTextSize(20);
        preview.setGravity(Gravity.CENTER);
        preview.setSingleLine(true);
        preview.setPadding(dp(12), dp(14), dp(12), dp(14));
        preview.setBackground(makePillBackground(Color.rgb(35, 42, 54)));
        content.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        final TextView solidColorTitle = makeFieldTitle("纯色");
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(12), 0, dp(6));
        content.addView(solidColorTitle, titleParams);

        final EditText solidColorInput = makeColorInput(draft.solidColorText);
        solidColorInput.setHint("留空表示默认颜色");
        content.addView(solidColorInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        final CheckBox gradientCheck = makeFontCheckBox("启用渐变色");
        gradientCheck.setChecked(draft.gradient);
        LinearLayout.LayoutParams gradientCheckParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        gradientCheckParams.setMargins(0, dp(10), 0, 0);
        content.addView(gradientCheck, gradientCheckParams);

        final TextView gradientTitle = makeFieldTitle("渐变颜色");
        LinearLayout.LayoutParams gradientTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        gradientTitleParams.setMargins(0, dp(10), 0, dp(6));
        content.addView(gradientTitle, gradientTitleParams);

        final LinearLayout gradientRow = new LinearLayout(this);
        gradientRow.setOrientation(LinearLayout.HORIZONTAL);
        final EditText gradientStartInput = makeColorInput(draft.gradientStartText);
        gradientStartInput.setHint("#38BDF8");
        final EditText gradientEndInput = makeColorInput(draft.gradientEndText);
        gradientEndInput.setHint("#EC4899");
        LinearLayout.LayoutParams gradientInputParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        gradientInputParams.setMargins(0, 0, dp(5), 0);
        gradientRow.addView(gradientStartInput, gradientInputParams);
        LinearLayout.LayoutParams gradientEndParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        gradientEndParams.setMargins(dp(5), 0, 0, 0);
        gradientRow.addView(gradientEndInput, gradientEndParams);
        content.addView(gradientRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView familyTitle = makeFieldTitle("系统字体");
        LinearLayout.LayoutParams familyTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        familyTitleParams.setMargins(0, dp(12), 0, dp(6));
        content.addView(familyTitle, familyTitleParams);

        final Spinner familySpinner = new Spinner(this);
        ArrayAdapter<String> familyAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                LABEL_FONT_FAMILY_LABELS
        );
        familyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        familySpinner.setAdapter(familyAdapter);
        familySpinner.setSelection(fontFamilyIndex(draft.family));
        content.addView(familySpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        final TextView fileInfo = makeFieldTitle("");
        LinearLayout.LayoutParams fileInfoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fileInfoParams.setMargins(0, dp(12), 0, dp(6));
        content.addView(fileInfo, fileInfoParams);

        LinearLayout fileActionRow = new LinearLayout(this);
        fileActionRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView pickFontAction = makeDialogActionPill("选择字体文件", Color.rgb(48, 105, 240));
        final TextView clearFontAction = makeDialogActionPill("清除字体文件", Color.rgb(138, 78, 54));
        LinearLayout.LayoutParams fileButtonParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fileButtonParams.setMargins(0, 0, dp(5), 0);
        fileActionRow.addView(pickFontAction, fileButtonParams);
        LinearLayout.LayoutParams clearButtonParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearButtonParams.setMargins(dp(5), 0, 0, 0);
        fileActionRow.addView(clearFontAction, clearButtonParams);
        content.addView(fileActionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        final CheckBox boldCheck = makeFontCheckBox("加粗");
        final CheckBox italicCheck = makeFontCheckBox("斜体");
        boldCheck.setChecked(draft.bold);
        italicCheck.setChecked(draft.italic);
        styleRow.addView(boldCheck, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        styleRow.addView(italicCheck, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams styleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        styleParams.setMargins(0, dp(10), 0, 0);
        content.addView(styleRow, styleParams);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自定义名称样式")
                .setMessage(item.label + "\n" + item.packageName)
                .setView(content)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认样式", null)
                .create();

        TextWatcher previewWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshFontDialogState(item.packageName, draft, preview, solidColorTitle, solidColorInput,
                        gradientTitle, gradientRow, familySpinner, fileInfo, clearFontAction,
                        boldCheck, italicCheck, gradientCheck, gradientStartInput, gradientEndInput,
                        previewDefaultColor);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        solidColorInput.addTextChangedListener(previewWatcher);
        gradientStartInput.addTextChangedListener(previewWatcher);
        gradientEndInput.addTextChangedListener(previewWatcher);

        View.OnClickListener previewClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshFontDialogState(item.packageName, draft, preview, solidColorTitle, solidColorInput,
                        gradientTitle, gradientRow, familySpinner, fileInfo, clearFontAction,
                        boldCheck, italicCheck, gradientCheck, gradientStartInput, gradientEndInput,
                        previewDefaultColor);
            }
        };
        boldCheck.setOnClickListener(previewClick);
        italicCheck.setOnClickListener(previewClick);
        gradientCheck.setOnClickListener(previewClick);
        familySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshFontDialogState(item.packageName, draft, preview, solidColorTitle, solidColorInput,
                        gradientTitle, gradientRow, familySpinner, fileInfo, clearFontAction,
                        boldCheck, italicCheck, gradientCheck, gradientStartInput, gradientEndInput,
                        previewDefaultColor);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                refreshFontDialogState(item.packageName, draft, preview, solidColorTitle, solidColorInput,
                        gradientTitle, gradientRow, familySpinner, fileInfo, clearFontAction,
                        boldCheck, italicCheck, gradientCheck, gradientStartInput, gradientEndInput,
                        previewDefaultColor);
            }
        });

        pickFontAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingFontDraft = captureFontDraft(item.packageName, draft, familySpinner, boldCheck, italicCheck,
                        gradientCheck, solidColorInput, gradientStartInput, gradientEndInput);
                dialog.dismiss();
                pickFontFile();
            }
        });
        clearFontAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                draft.useFontFile = false;
                draft.fontSourceUriString = null;
                draft.fontFileLabel = "";
                refreshFontDialogState(item.packageName, draft, preview, solidColorTitle, solidColorInput,
                        gradientTitle, gradientRow, familySpinner, fileInfo, clearFontAction,
                        boldCheck, italicCheck, gradientCheck, gradientStartInput, gradientEndInput,
                        previewDefaultColor);
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface ignored) {
                refreshFontDialogState(item.packageName, draft, preview, solidColorTitle, solidColorInput,
                        gradientTitle, gradientRow, familySpinner, fileInfo, clearFontAction,
                        boldCheck, italicCheck, gradientCheck, gradientStartInput, gradientEndInput,
                        previewDefaultColor);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            FontDraft finalDraft = captureFontDraft(item.packageName, draft, familySpinner, boldCheck,
                                    italicCheck, gradientCheck, solidColorInput, gradientStartInput, gradientEndInput);
                            if (saveLabelStyle(item.packageName, finalDraft)) {
                                dialog.dismiss();
                            }
                        } catch (IllegalArgumentException e) {
                            Toast.makeText(LauncherIconCustomizerActivity.this,
                                    finalDraftErrorMessage(captureFontDraft(item.packageName, draft, familySpinner,
                                            boldCheck, italicCheck, gradientCheck, solidColorInput,
                                            gradientStartInput, gradientEndInput)),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        restoreLabelStyle(item.packageName);
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }

    private void saveLabel(String packageName, String label) {
        try {
            LauncherIconCustomizerStore.saveLabel(this, packageName, label);
            LauncherIconLog.log("saveLabel finished package=" + packageName + ", label=" + label);
            notifyIconChanged(packageName);
            refreshList();
            Toast.makeText(this, "已设置桌面名称，返回桌面后生效", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            LauncherIconLog.log("saveLabel failed package=" + packageName);
            LauncherIconLog.log(e);
            Toast.makeText(this, "设置名称失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveLabelColor(String packageName, int color) {
        try {
            LauncherIconCustomizerStore.saveLabelColor(this, packageName, color);
            LauncherIconLog.log("saveLabelColor finished package=" + packageName + ", color=" + colorHex(color));
            notifyIconChanged(packageName);
            refreshList();
            Toast.makeText(this, "已设置桌面名称颜色，返回桌面或重启主屏幕后生效", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            LauncherIconLog.log("saveLabelColor failed package=" + packageName);
            LauncherIconLog.log(e);
            Toast.makeText(this, "设置颜色失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean saveLabelStyle(String packageName, FontDraft draft) throws IllegalArgumentException {
        if (draft == null) {
            throw new IllegalArgumentException("draft missing");
        }
        Integer solidColor = null;
        if (!draft.gradient && draft.solidColorText != null && draft.solidColorText.trim().length() != 0) {
            solidColor = Integer.valueOf(parseLabelColorInput(draft.solidColorText));
        }
        int gradientStart = LauncherIconCustomizerStore.DEFAULT_GRADIENT_START;
        int gradientEnd = LauncherIconCustomizerStore.DEFAULT_GRADIENT_END;
        if (draft.gradient) {
            gradientStart = parseLabelColorInput(draft.gradientStartText);
            gradientEnd = parseLabelColorInput(draft.gradientEndText);
        }

        LauncherIconCustomizerStore.LabelFont font = new LauncherIconCustomizerStore.LabelFont(
                draft.family,
                draft.bold,
                draft.italic,
                draft.gradient,
                gradientStart,
                gradientEnd,
                draft.useFontFile,
                draft.fontFileLabel,
                0L
        );
        try {
            if (draft.useFontFile && draft.fontSourceUriString != null && draft.fontSourceUriString.length() != 0) {
                String label = LauncherIconCustomizerStore.saveLabelFontFile(
                        this,
                        packageName,
                        Uri.parse(draft.fontSourceUriString)
                );
                font = new LauncherIconCustomizerStore.LabelFont(
                        draft.family,
                        draft.bold,
                        draft.italic,
                        draft.gradient,
                        gradientStart,
                        gradientEnd,
                        true,
                        label != null && label.length() != 0 ? label : draft.fontFileLabel,
                        0L
                );
            } else if (!draft.useFontFile) {
                LauncherIconCustomizerStore.deleteLabelFontFile(this, packageName);
            }
            LauncherIconCustomizerStore.saveLabelFont(this, packageName, font);
            if (draft.gradient) {
                LauncherIconCustomizerStore.deleteLabelColor(this, packageName);
            } else if (solidColor != null) {
                LauncherIconCustomizerStore.saveLabelColor(this, packageName, solidColor.intValue());
            } else {
                LauncherIconCustomizerStore.deleteLabelColor(this, packageName);
            }
            LauncherIconLog.log("saveLabelStyle finished package=" + packageName
                    + ", color=" + (solidColor != null ? colorHex(solidColor.intValue()) : "default")
                    + ", font=" + labelFontSummary(font));
            notifyIconChanged(packageName);
            refreshList();
            Toast.makeText(this, "已设置桌面名称样式，返回桌面或重启主屏幕后生效", Toast.LENGTH_SHORT).show();
            return true;
        } catch (IOException e) {
            LauncherIconLog.log("saveLabelStyle failed package=" + packageName);
            LauncherIconLog.log(e);
            Toast.makeText(this, "设置名称样式失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void saveLabelFont(String packageName, LauncherIconCustomizerStore.LabelFont font) {
        try {
            LauncherIconCustomizerStore.saveLabelFont(this, packageName, font);
            LauncherIconLog.log("saveLabelFont finished package=" + packageName
                    + ", font=" + (font != null ? labelFontSummary(font) : "default"));
            notifyIconChanged(packageName);
            refreshList();
            Toast.makeText(this, "已设置桌面名称字体，返回桌面或重启主屏幕后生效", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            LauncherIconLog.log("saveLabelFont failed package=" + packageName);
            LauncherIconLog.log(e);
            Toast.makeText(this, "设置字体失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void restoreLabel(String packageName) {
        LauncherIconCustomizerStore.deleteLabel(this, packageName);
        LauncherIconLog.log("restoreLabel package=" + packageName);
        notifyIconChanged(packageName);
        refreshList();
        Toast.makeText(this, "已恢复默认名称", Toast.LENGTH_SHORT).show();
    }

    private void restoreLabelColor(String packageName) {
        LauncherIconCustomizerStore.deleteLabelColor(this, packageName);
        LauncherIconLog.log("restoreLabelColor package=" + packageName);
        notifyIconChanged(packageName);
        refreshList();
        Toast.makeText(this, "已恢复默认名称颜色", Toast.LENGTH_SHORT).show();
    }

    private void restoreLabelStyle(String packageName) {
        LauncherIconCustomizerStore.deleteLabelColor(this, packageName);
        LauncherIconCustomizerStore.deleteLabelFont(this, packageName);
        LauncherIconLog.log("restoreLabelStyle package=" + packageName);
        notifyIconChanged(packageName);
        refreshList();
        Toast.makeText(this, "已恢复默认名称样式", Toast.LENGTH_SHORT).show();
    }

    private void restoreLabelFont(String packageName) {
        LauncherIconCustomizerStore.deleteLabelFont(this, packageName);
        LauncherIconLog.log("restoreLabelFont package=" + packageName);
        notifyIconChanged(packageName);
        refreshList();
        Toast.makeText(this, "已恢复默认名称字体", Toast.LENGTH_SHORT).show();
    }

    private void clearIcon(String packageName) {
        LauncherIconCustomizerStore.deleteIcon(this, packageName);
        LauncherIconLog.log("clearIcon package=" + packageName);
        notifyIconChanged(packageName);
        refreshList();
        Toast.makeText(this, "已恢复默认图标", Toast.LENGTH_SHORT).show();
    }

    private void showRestoreMenu(final String packageName, boolean hasCustomIcon,
                                 boolean hasCustomLabel, boolean hasCustomLabelColor,
                                 boolean hasCustomLabelFont) {
        boolean hasCustomLabelStyle = hasCustomLabelColor || hasCustomLabelFont;
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<Integer> actions = new ArrayList<Integer>();
        if (hasCustomIcon) {
            labels.add("恢复图标");
            actions.add(Integer.valueOf(1));
        }
        if (hasCustomLabel) {
            labels.add("恢复名称");
            actions.add(Integer.valueOf(2));
        }
        if (hasCustomLabelStyle) {
            labels.add("恢复名称样式");
            actions.add(Integer.valueOf(4));
        }
        if (hasCustomIcon || hasCustomLabel || hasCustomLabelStyle) {
            labels.add("全部恢复");
            actions.add(Integer.valueOf(3));
        }
        if (labels.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("恢复默认")
                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int action = actions.get(which).intValue();
                        if (action == 1 || action == 3) {
                            LauncherIconCustomizerStore.deleteIcon(LauncherIconCustomizerActivity.this, packageName);
                        }
                        if (action == 2 || action == 3) {
                            LauncherIconCustomizerStore.deleteLabel(LauncherIconCustomizerActivity.this, packageName);
                        }
                        if (action == 4 || action == 3) {
                            LauncherIconCustomizerStore.deleteLabelColor(LauncherIconCustomizerActivity.this, packageName);
                            LauncherIconCustomizerStore.deleteLabelFont(LauncherIconCustomizerActivity.this, packageName);
                        }
                        LauncherIconLog.log("restore menu action=" + action + ", package=" + packageName);
                        notifyIconChanged(packageName);
                        refreshList();
                    }
                })
                .show();
    }

    private void pickFontFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "font/*",
                "application/font-sfnt",
                "application/x-font-ttf",
                "application/x-font-opentype",
                "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_FONT);
        } catch (Throwable t) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(fallback, REQUEST_PICK_FONT);
        }
    }

    private AppEntry findAppEntry(String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return null;
        }
        for (int i = 0; i < allApps.size(); i++) {
            AppEntry entry = allApps.get(i);
            if (packageName.equals(entry.packageName)) {
                return entry;
            }
        }
        return null;
    }

    private int currentPreviewColor(FontDraft draft) {
        return previewColorForDraft(draft, Color.WHITE);
    }

    private FontDraft captureFontDraft(String packageName,
                                       FontDraft baseDraft,
                                       Spinner familySpinner,
                                       CheckBox boldCheck,
                                       CheckBox italicCheck,
                                       CheckBox gradientCheck,
                                       EditText solidColorInput,
                                       EditText gradientStartInput,
                                       EditText gradientEndInput) {
        FontDraft draft = baseDraft != null ? baseDraft.copy() : FontDraft.fromStore(this, packageName);
        draft.packageName = packageName;
        draft.family = selectedFontFamily(familySpinner);
        draft.bold = boldCheck.isChecked();
        draft.italic = italicCheck.isChecked();
        draft.gradient = gradientCheck.isChecked();
        draft.solidColorText = solidColorInput.getText() == null ? "" : solidColorInput.getText().toString().trim();
        draft.gradientStartText = gradientStartInput.getText() == null
                ? ""
                : gradientStartInput.getText().toString().trim();
        draft.gradientEndText = gradientEndInput.getText() == null
                ? ""
                : gradientEndInput.getText().toString().trim();
        return draft;
    }

    private void refreshFontDialogState(String packageName,
                                        FontDraft baseDraft,
                                        TextView preview,
                                        TextView solidColorTitle,
                                        EditText solidColorInput,
                                        TextView gradientTitle,
                                        LinearLayout gradientRow,
                                        Spinner familySpinner,
                                        TextView fileInfo,
                                        TextView clearFontAction,
                                        CheckBox boldCheck,
                                        CheckBox italicCheck,
                                        CheckBox gradientCheck,
                                        EditText gradientStartInput,
                                        EditText gradientEndInput,
                                        int previewDefaultColor) {
        FontDraft current = captureFontDraft(packageName, baseDraft, familySpinner, boldCheck, italicCheck,
                gradientCheck, solidColorInput, gradientStartInput, gradientEndInput);
        boolean gradient = current.gradient;
        solidColorTitle.setVisibility(gradient ? View.GONE : View.VISIBLE);
        solidColorInput.setVisibility(gradient ? View.GONE : View.VISIBLE);
        gradientTitle.setVisibility(gradient ? View.VISIBLE : View.GONE);
        gradientRow.setVisibility(gradient ? View.VISIBLE : View.GONE);
        familySpinner.setEnabled(!current.useFontFile);
        fileInfo.setText(current.useFontFile
                ? "字体文件：" + (current.fontFileLabel.length() != 0 ? current.fontFileLabel : "已选择")
                + (current.fontSourceUriString != null && current.fontSourceUriString.length() != 0 ? "（保存后生效）" : "")
                : "字体文件：未选择");
        fileInfo.setTextColor(current.useFontFile ? Color.rgb(35, 42, 54) : Color.rgb(88, 96, 110));
        clearFontAction.setVisibility(current.useFontFile ? View.VISIBLE : View.GONE);
        applyLabelFontPreview(preview,
                labelFontFromDraft(current),
                previewColorForDraft(current, previewDefaultColor),
                packageName);
    }

    private LauncherIconCustomizerStore.LabelFont labelFontFromDraft(FontDraft draft) {
        int gradientStart = safeParseLabelColorInput(draft != null ? draft.gradientStartText : "",
                LauncherIconCustomizerStore.DEFAULT_GRADIENT_START);
        int gradientEnd = safeParseLabelColorInput(draft != null ? draft.gradientEndText : "",
                LauncherIconCustomizerStore.DEFAULT_GRADIENT_END);
        return new LauncherIconCustomizerStore.LabelFont(
                draft != null ? draft.family : LauncherIconCustomizerStore.FONT_FAMILY_DEFAULT,
                draft != null && draft.bold,
                draft != null && draft.italic,
                draft != null && draft.gradient,
                gradientStart,
                gradientEnd,
                draft != null && draft.useFontFile,
                draft != null ? draft.fontFileLabel : null,
                0L
        );
    }

    private int previewColorForDraft(FontDraft draft, int fallbackColor) {
        if (draft == null || draft.gradient) {
            return fallbackColor;
        }
        if (draft.solidColorText == null || draft.solidColorText.trim().length() == 0) {
            return fallbackColor;
        }
        return safeParseLabelColorInput(draft.solidColorText, fallbackColor);
    }

    private String finalDraftErrorMessage(FontDraft draft) {
        return draft != null && draft.gradient
                ? "渐变颜色格式无效，请使用 #RRGGBB"
                : "颜色格式无效，请使用 #RRGGBB";
    }

    private String fontFileLabelFromUri(Uri uri) {
        if (uri == null) {
            return "";
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    return value != null ? value.trim() : "";
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the URI tail when metadata is unavailable.
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
        return tail != null ? tail.trim() : "";
    }

    private void restartLauncher() {
        Toast.makeText(this, "正在重启 One UI 主屏幕", Toast.LENGTH_SHORT).show();
        LauncherIconLog.log("restart launcher requested");
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean shellOk = false;
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{
                            "su",
                            "-c",
                            "am force-stop com.sec.android.app.launcher"
                    });
                    shellOk = process.waitFor() == 0;
                    LauncherIconLog.log("restart launcher su force-stop result=" + shellOk);
                } catch (Throwable t) {
                    LauncherIconLog.log("restart launcher su force-stop failed");
                    LauncherIconLog.log(t);
                }
                try {
                    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    if (manager != null) {
                        manager.killBackgroundProcesses("com.sec.android.app.launcher");
                    }
                    LauncherIconLog.log("restart launcher killBackgroundProcesses called");
                } catch (Throwable t) {
                    LauncherIconLog.log("restart launcher killBackgroundProcesses failed");
                    LauncherIconLog.log(t);
                }
                final boolean finalShellOk = shellOk;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                LauncherIconCustomizerActivity.this,
                                finalShellOk ? "已重启 One UI 主屏幕" : "已请求重启，请返回桌面查看",
                                Toast.LENGTH_SHORT
                        ).show();
                        try {
                            Intent home = new Intent(Intent.ACTION_MAIN);
                            home.addCategory(Intent.CATEGORY_HOME);
                            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(home);
                        } catch (Throwable t) {
                            LauncherIconLog.log("open home after restart failed");
                            LauncherIconLog.log(t);
                        }
                    }
                });
            }
        }, "LauncherIconRestart").start();
    }

    private void notifyIconChanged(String packageName) {
        getContentResolver().notifyChange(LauncherIconCustomizerStore.BASE_URI, null);
        getContentResolver().notifyChange(LauncherIconCustomizerStore.iconUri(packageName), null);
        Intent targeted = new Intent(LauncherIconCustomizerStore.ACTION_CHANGED);
        targeted.setPackage("com.sec.android.app.launcher");
        targeted.putExtra(LauncherIconCustomizerStore.EXTRA_PACKAGE, packageName);
        targeted.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            targeted.addFlags(FLAG_RECEIVER_INCLUDE_BACKGROUND_COMPAT);
        }
        sendBroadcast(targeted);

        Intent broad = new Intent(LauncherIconCustomizerStore.ACTION_CHANGED);
        broad.putExtra(LauncherIconCustomizerStore.EXTRA_PACKAGE, packageName);
        broad.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            broad.addFlags(FLAG_RECEIVER_INCLUDE_BACKGROUND_COMPAT);
        }
        sendBroadcast(broad);
        LauncherIconLog.log("notifyIconChanged sent, package=" + packageName);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ImageView icon;
            TextView name;
            TextView pkg;
            TextView iconAction;
            TextView labelAction;
            TextView fontAction;
            TextView restore;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof ViewHolder) {
                row = (LinearLayout) convertView;
                ViewHolder holder = (ViewHolder) convertView.getTag();
                icon = holder.icon;
                name = holder.name;
                pkg = holder.packageName;
                iconAction = holder.iconAction;
                labelAction = holder.labelAction;
                fontAction = holder.fontAction;
                restore = holder.restore;
            } else {
                row = new LinearLayout(LauncherIconCustomizerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(14), dp(14), dp(14));
                row.setMinimumHeight(dp(96));

                icon = new ImageView(LauncherIconCustomizerActivity.this);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(46), dp(46));
                iconParams.setMargins(0, 0, dp(14), 0);
                row.addView(icon, iconParams);

                LinearLayout textColumn = new LinearLayout(LauncherIconCustomizerActivity.this);
                textColumn.setOrientation(LinearLayout.VERTICAL);

                name = new TextView(LauncherIconCustomizerActivity.this);
                name.setTextColor(Color.rgb(24, 29, 36));
                name.setTextSize(16);
                name.setSingleLine(true);
                textColumn.addView(name, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                pkg = new TextView(LauncherIconCustomizerActivity.this);
                pkg.setTextColor(Color.rgb(98, 105, 117));
                pkg.setTextSize(12);
                pkg.setSingleLine(false);
                pkg.setPadding(0, dp(5), 0, 0);
                textColumn.addView(pkg, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                row.addView(textColumn, new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                ));

                LinearLayout actions = new LinearLayout(LauncherIconCustomizerActivity.this);
                actions.setOrientation(LinearLayout.VERTICAL);
                actions.setGravity(Gravity.CENTER);

                iconAction = makePillText();
                actions.addView(iconAction, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                labelAction = makePillText();
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                labelParams.setMargins(0, dp(7), 0, 0);
                actions.addView(labelAction, labelParams);

                fontAction = makePillText();
                LinearLayout.LayoutParams fontParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                fontParams.setMargins(0, dp(7), 0, 0);
                actions.addView(fontAction, fontParams);

                restore = makePillText();
                LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                restoreParams.setMargins(0, dp(7), 0, 0);
                actions.addView(restore, restoreParams);

                row.addView(actions, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                row.setTag(new ViewHolder(icon, name, pkg, iconAction, labelAction, fontAction, restore));
            }

            final AppEntry item = apps.get(position);
            final String customLabel = LauncherIconCustomizerStore.customLabel(
                    LauncherIconCustomizerActivity.this,
                    item.packageName
            );
            final Integer customLabelColor = LauncherIconCustomizerStore.customLabelColor(
                    LauncherIconCustomizerActivity.this,
                    item.packageName
            );
            final LauncherIconCustomizerStore.LabelFont customLabelFont =
                    LauncherIconCustomizerStore.customLabelFont(
                            LauncherIconCustomizerActivity.this,
                            item.packageName
                    );
            final boolean hasCustomLabel = customLabel != null;
            final boolean hasCustomLabelColor = customLabelColor != null;
            final boolean hasCustomLabelFont = customLabelFont != null;
            final boolean hasCustomLabelStyle = hasCustomLabelColor || hasCustomLabelFont;
            final boolean hasCustomIcon = LauncherIconCustomizerStore.hasCustomIcon(
                    LauncherIconCustomizerActivity.this,
                    item.packageName
            );
            Bitmap custom = hasCustomIcon
                    ? LauncherIconCustomizerStore.loadStoredIcon(LauncherIconCustomizerActivity.this, item.packageName, dp(46), dp(46))
                    : null;
            if (custom != null) {
                icon.setImageBitmap(custom);
            } else {
                icon.setImageDrawable(item.icon);
            }
            name.setText(hasCustomLabel ? customLabel : item.label);
            applyLabelFontPreview(
                    name,
                    customLabelFont,
                    hasCustomLabelColor ? customLabelColor.intValue() : DEFAULT_LABEL_PREVIEW_COLOR,
                    item.packageName
            );
            StringBuilder detail = new StringBuilder();
            if (hasCustomLabel) {
                detail.append("原名：").append(item.label).append('\n');
            }
            detail.append(item.packageName);
            String styleSummary = labelStyleSummary(customLabelColor, customLabelFont);
            if (styleSummary.length() != 0) {
                detail.append('\n').append("名称样式：").append(styleSummary);
            }
            pkg.setText(detail.toString());
            row.setBackground(makeRowBackground(hasCustomIcon || hasCustomLabel || hasCustomLabelStyle));

            iconAction.setText(hasCustomIcon ? "更换图标" : "选图标");
            iconAction.setTextColor(Color.WHITE);
            iconAction.setBackground(makePillBackground(Color.rgb(48, 105, 240)));
            iconAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickImage(item.packageName);
                }
            });

            labelAction.setText(hasCustomLabel ? "改名字" : "设名字");
            labelAction.setTextColor(Color.WHITE);
            labelAction.setBackground(makePillBackground(Color.rgb(38, 139, 112)));
            labelAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editLabel(item);
                }
            });

            fontAction.setText(hasCustomLabelStyle ? "改样式" : "字体");
            fontAction.setTextColor(Color.WHITE);
            fontAction.setBackground(makePillBackground(Color.rgb(111, 82, 171)));
            fontAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editLabelFont(item);
                }
            });

            restore.setText("恢复");
            restore.setVisibility((hasCustomIcon || hasCustomLabel || hasCustomLabelStyle)
                    ? View.VISIBLE
                    : View.GONE);
            restore.setTextColor(Color.rgb(88, 96, 110));
            restore.setBackground(makePillBackground(Color.rgb(238, 241, 246)));
            restore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRestoreMenu(item.packageName, hasCustomIcon, hasCustomLabel,
                            hasCustomLabelColor, hasCustomLabelFont);
                }
            });
            return row;
        }

        private TextView makePillText() {
            TextView view = new TextView(LauncherIconCustomizerActivity.this);
            view.setGravity(Gravity.CENTER);
            view.setTextSize(13);
            view.setIncludeFontPadding(false);
            view.setMinWidth(dp(72));
            view.setPadding(dp(10), dp(7), dp(10), dp(7));
            return view;
        }
    }

    private Drawable makeRowBackground(boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(active ? 2 : 1), active ? Color.rgb(48, 105, 240) : Color.rgb(224, 228, 236));
        return drawable;
    }

    private Drawable makePillBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(16));
        return drawable;
    }

    private TextView makeColorSwatch(int color, boolean selected) {
        TextView view = new TextView(this);
        view.setTag(Integer.valueOf(color));
        view.setMinHeight(dp(38));
        view.setBackground(makeColorSwatchBackground(color, selected));
        return view;
    }

    private Drawable makeColorSwatchBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(selected ? 3 : 1), selected ? Color.rgb(48, 105, 240) : Color.rgb(210, 216, 226));
        return drawable;
    }

    private TextView makeFieldTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(88, 96, 110));
        view.setTextSize(13);
        view.setIncludeFontPadding(false);
        return view;
    }

    private CheckBox makeFontCheckBox(String text) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setTextColor(Color.rgb(35, 42, 54));
        checkBox.setTextSize(14);
        checkBox.setSingleLine(true);
        return checkBox;
    }

    private EditText makeColorInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setHint("#FFFFFF");
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(makeInputBackground());
        return input;
    }

    private void applyLabelFontPreview(final TextView view,
                                       final LauncherIconCustomizerStore.LabelFont font,
                                       final int fallbackColor,
                                       final String packageName) {
        if (view == null) {
            return;
        }
        view.getPaint().setShader(null);
        if (font == null) {
            view.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            view.setTextColor(fallbackColor);
            view.invalidate();
            return;
        }
        int style = fontStyle(font);
        view.setTypeface(typefaceForLabelFont(font, style, packageName), style);
        if (font.gradient) {
            view.setTextColor(Color.WHITE);
            applyGradientToTextView(view, font.gradientStart, font.gradientEnd);
        } else {
            view.setTextColor(fallbackColor);
        }
        view.invalidate();
    }

    private void applyGradientToTextView(TextView view, int startColor, int endColor) {
        int width = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
        if (width <= 0) {
            CharSequence text = view.getText();
            width = Math.max(1, (int) view.getPaint().measureText(text == null ? "" : text.toString()));
        }
        if (width <= 0) {
            width = Math.max(1, (int) (view.getTextSize() * 4));
        }
        view.getPaint().setShader(new LinearGradient(
                0,
                0,
                width,
                0,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
        ));
        view.invalidate();
    }

    private static int fontStyle(LauncherIconCustomizerStore.LabelFont font) {
        if (font == null) {
            return Typeface.NORMAL;
        }
        if (font.bold && font.italic) {
            return Typeface.BOLD_ITALIC;
        }
        if (font.bold) {
            return Typeface.BOLD;
        }
        if (font.italic) {
            return Typeface.ITALIC;
        }
        return Typeface.NORMAL;
    }

    private Typeface typefaceForLabelFont(LauncherIconCustomizerStore.LabelFont font,
                                          int style,
                                          String packageName) {
        Typeface base = null;
        if (font != null && font.useFile && packageName != null && packageName.length() != 0) {
            File storedFile = LauncherIconCustomizerStore.fontFile(this, packageName);
            if (storedFile != null && storedFile.isFile() && storedFile.length() > 0) {
                try {
                    base = Typeface.createFromFile(storedFile);
                } catch (Throwable ignored) {
                    base = null;
                }
            }
        }
        if (base == null) {
            if (font == null || LauncherIconCustomizerStore.FONT_FAMILY_DEFAULT.equals(font.family)) {
                base = Typeface.DEFAULT;
            } else {
                base = Typeface.create(font.family, Typeface.NORMAL);
            }
        }
        return Typeface.create(base, style);
    }

    private static int fontFamilyIndex(String family) {
        for (int i = 0; i < LABEL_FONT_FAMILY_VALUES.length; i++) {
            if (LABEL_FONT_FAMILY_VALUES[i].equals(family)) {
                return i;
            }
        }
        return 0;
    }

    private static String selectedFontFamily(Spinner spinner) {
        int index = spinner != null ? spinner.getSelectedItemPosition() : 0;
        if (index < 0 || index >= LABEL_FONT_FAMILY_VALUES.length) {
            index = 0;
        }
        return LABEL_FONT_FAMILY_VALUES[index];
    }

    private static String labelFontSummary(LauncherIconCustomizerStore.LabelFont font) {
        if (font == null) {
            return "默认";
        }
        ArrayList<String> parts = new ArrayList<String>();
        if (font.useFile && font.fileLabel != null && font.fileLabel.length() != 0) {
            parts.add("文件字体 " + font.fileLabel);
        }
        if (!LauncherIconCustomizerStore.FONT_FAMILY_DEFAULT.equals(font.family)) {
            parts.add(fontFamilyDisplayName(font.family));
        }
        if (font.bold) {
            parts.add("加粗");
        }
        if (font.italic) {
            parts.add("斜体");
        }
        if (font.gradient) {
            parts.add("渐变 " + colorHex(font.gradientStart) + "-" + colorHex(font.gradientEnd));
        }
        if (parts.isEmpty()) {
            return "默认";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append("，");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private static String labelStyleSummary(Integer color, LauncherIconCustomizerStore.LabelFont font) {
        ArrayList<String> parts = new ArrayList<String>();
        if (color != null && (font == null || !font.gradient)) {
            parts.add("纯色 " + colorHex(color.intValue()));
        }
        if (font != null) {
            String fontSummary = labelFontSummary(font);
            if (!"默认".equals(fontSummary)) {
                parts.add(fontSummary);
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append("，");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private TextView makeDialogActionPill(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(13);
        view.setTextColor(Color.WHITE);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setBackground(makePillBackground(color));
        return view;
    }

    private static String fontFamilyDisplayName(String family) {
        int index = fontFamilyIndex(family);
        return LABEL_FONT_FAMILY_LABELS[index];
    }

    private static String colorHex(int color) {
        return String.format(Locale.US, "#%06X", Integer.valueOf(color & 0x00FFFFFF));
    }

    private static int parseLabelColorInput(String value) {
        if (value == null) {
            throw new IllegalArgumentException("empty color");
        }
        String text = value.trim();
        if (text.length() == 0) {
            throw new IllegalArgumentException("empty color");
        }
        if (text.charAt(0) != '#') {
            text = "#" + text;
        }
        if (text.length() != 7 && text.length() != 9) {
            throw new IllegalArgumentException("bad color length");
        }
        return Color.parseColor(text) | 0xFF000000;
    }

    private static int safeParseLabelColorInput(String value, int fallback) {
        try {
            return parseLabelColorInput(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private Drawable makeInputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), Color.rgb(224, 228, 236));
        return drawable;
    }

    private static final class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;

        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final class FontDraft {
        String packageName;
        String family;
        boolean bold;
        boolean italic;
        boolean gradient;
        String solidColorText;
        String gradientStartText;
        String gradientEndText;
        boolean useFontFile;
        String fontFileLabel;
        String fontSourceUriString;

        static FontDraft fromStore(Context context, String packageName) {
            LauncherIconCustomizerStore.LabelFont font =
                    LauncherIconCustomizerStore.customLabelFont(context, packageName);
            Integer color = LauncherIconCustomizerStore.customLabelColor(context, packageName);
            FontDraft draft = new FontDraft();
            draft.packageName = packageName;
            draft.family = font != null ? font.family : LauncherIconCustomizerStore.FONT_FAMILY_DEFAULT;
            draft.bold = font != null && font.bold;
            draft.italic = font != null && font.italic;
            draft.gradient = font != null && font.gradient;
            draft.solidColorText = color != null ? colorHex(color.intValue()) : "";
            draft.gradientStartText = colorHex(font != null
                    ? font.gradientStart
                    : LauncherIconCustomizerStore.DEFAULT_GRADIENT_START);
            draft.gradientEndText = colorHex(font != null
                    ? font.gradientEnd
                    : LauncherIconCustomizerStore.DEFAULT_GRADIENT_END);
            draft.useFontFile = font != null && font.useFile;
            draft.fontFileLabel = font != null && font.fileLabel != null ? font.fileLabel : "";
            draft.fontSourceUriString = null;
            return draft;
        }

        FontDraft copy() {
            FontDraft draft = new FontDraft();
            draft.packageName = packageName;
            draft.family = family;
            draft.bold = bold;
            draft.italic = italic;
            draft.gradient = gradient;
            draft.solidColorText = solidColorText;
            draft.gradientStartText = gradientStartText;
            draft.gradientEndText = gradientEndText;
            draft.useFontFile = useFontFile;
            draft.fontFileLabel = fontFileLabel;
            draft.fontSourceUriString = fontSourceUriString;
            return draft;
        }
    }

    private static final class ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView packageName;
        final TextView iconAction;
        final TextView labelAction;
        final TextView fontAction;
        final TextView restore;

        ViewHolder(ImageView icon, TextView name, TextView packageName,
                   TextView iconAction, TextView labelAction, TextView fontAction, TextView restore) {
            this.icon = icon;
            this.name = name;
            this.packageName = packageName;
            this.iconAction = iconAction;
            this.labelAction = labelAction;
            this.fontAction = fontAction;
            this.restore = restore;
        }
    }
}
