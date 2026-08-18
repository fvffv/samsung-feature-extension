package com.samsung.feature.extension;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class FingerprintStyleSettingsActivity extends Activity {
    private static final String TAG = "FingerprintStyleUI";
    private static final int REQUEST_JSON = 1001;
    private static final int REQUEST_PNG = 1002;

    private Switch enabledSwitch;
    private Switch loopSwitch;
    private TextView fileValue;
    private TextView statusValue;
    private LinearLayout materialList;
    private final List<LottiePreviewView> previewViews = new ArrayList<LottiePreviewView>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("自定义指纹图标");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(246, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("自定义指纹图标");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, matchWrapParams());

        TextView description = new TextView(this);
        description.setText("替换系统指纹验证位置显示的指纹图标，支持 Lottie JSON 动画或 PNG 静态图。");
        description.setTextColor(Color.rgb(92, 99, 111));
        description.setTextSize(15);
        description.setLineSpacing(dp(3), 1.0f);
        description.setPadding(0, dp(12), 0, dp(18));
        root.addView(description, matchWrapParams());

        root.addView(buildIconCard(), cardParams());
        root.addView(buildMaterialCard(), cardParams());
        setContentView(scrollView);
        refreshState();
        LanguageManager.applyToActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
        LanguageManager.applyToActivity(this);
    }

    @Override
    protected void onPause() {
        stopPreviewAnimations();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopPreviewAnimations();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String type = requestCode == REQUEST_PNG
                ? FingerprintStyleSettingsProvider.TYPE_PNG
                : FingerprintStyleSettingsProvider.TYPE_ANIMATION;
        boolean saved = FingerprintStyleSettingsProvider.saveCustomFile(this, uri, type,
                requestCode == REQUEST_PNG ? "fingerprint_icon.png" : "fingerprint_icon.json");
        refreshState();
        Toast.makeText(this, saved ? "已保存，重启 BiometricSetting 后生效" : "保存失败，请换一个文件再试",
                Toast.LENGTH_SHORT).show();
    }

    private View buildIconCard() {
        LinearLayout card = newCard();
        card.addView(sectionTitle("指纹图标"), matchWrapParams());

        enabledSwitch = new Switch(this);
        enabledSwitch.setText("启用自定义指纹图标");
        enabledSwitch.setTextColor(Color.rgb(24, 29, 36));
        enabledSwitch.setTextSize(16);
        enabledSwitch.setPadding(0, dp(12), 0, dp(4));
        enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isPressed()) {
                    FingerprintStyleSettingsProvider.setEnabled(FingerprintStyleSettingsActivity.this, isChecked);
                    refreshState();
                }
            }
        });
        card.addView(enabledSwitch, matchWrapParams());

        loopSwitch = new Switch(this);
        loopSwitch.setText("循环播放动画");
        loopSwitch.setTextColor(Color.rgb(24, 29, 36));
        loopSwitch.setTextSize(16);
        loopSwitch.setPadding(0, dp(6), 0, dp(4));
        loopSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isPressed()) {
                    FingerprintStyleSettingsProvider.setLoop(FingerprintStyleSettingsActivity.this, isChecked);
                    refreshState();
                }
            }
        });
        card.addView(loopSwitch, matchWrapParams());

        fileValue = new TextView(this);
        fileValue.setTextColor(Color.rgb(68, 76, 88));
        fileValue.setTextSize(14);
        fileValue.setLineSpacing(dp(3), 1.0f);
        fileValue.setPadding(0, dp(8), 0, dp(12));
        card.addView(fileValue, matchWrapParams());

        TextView lottieHint = new TextView(this);
        lottieHint.setText("可以在 LottieFiles 免费动画页查找 Lottie JSON 动画：https://lottiefiles.com/free-animations");
        lottieHint.setTextColor(Color.rgb(92, 99, 111));
        lottieHint.setTextSize(13);
        lottieHint.setLineSpacing(dp(3), 1.0f);
        lottieHint.setPadding(0, 0, 0, dp(10));
        card.addView(lottieHint, matchWrapParams());

        Button openLottieFiles = new Button(this);
        openLottieFiles.setText("打开 LottieFiles");
        openLottieFiles.setAllCaps(false);
        openLottieFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLottieFiles();
            }
        });
        LinearLayout.LayoutParams openLottieParams = wrapWrapParams();
        openLottieParams.setMargins(0, 0, 0, dp(10));
        card.addView(openLottieFiles, openLottieParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button chooseJson = new Button(this);
        chooseJson.setText("选择 Lottie JSON");
        chooseJson.setAllCaps(false);
        chooseJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker(REQUEST_JSON);
            }
        });
        buttons.addView(chooseJson, wrapWrapParams());

        Button choosePng = new Button(this);
        choosePng.setText("选择 PNG");
        choosePng.setAllCaps(false);
        choosePng.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker(REQUEST_PNG);
            }
        });
        LinearLayout.LayoutParams choosePngParams = wrapWrapParams();
        choosePngParams.setMargins(dp(10), 0, 0, 0);
        buttons.addView(choosePng, choosePngParams);
        card.addView(buttons, matchWrapParams());

        Button reset = new Button(this);
        reset.setText("恢复默认");
        reset.setAllCaps(false);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FingerprintStyleSettingsProvider.clear(FingerprintStyleSettingsActivity.this);
                refreshState();
                Toast.makeText(FingerprintStyleSettingsActivity.this, "已恢复默认", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams resetParams = wrapWrapParams();
        resetParams.setMargins(0, dp(10), 0, 0);
        card.addView(reset, resetParams);

        statusValue = new TextView(this);
        statusValue.setTextColor(Color.rgb(92, 99, 111));
        statusValue.setTextSize(13);
        statusValue.setLineSpacing(dp(3), 1.0f);
        statusValue.setPadding(0, dp(12), 0, 0);
        card.addView(statusValue, matchWrapParams());
        return card;
    }

    private View buildMaterialCard() {
        LinearLayout card = newCard();
        card.addView(sectionTitle("Lottie 素材库"), matchWrapParams());

        TextView hint = new TextView(this);
        hint.setText("内置 9 个默认 Lottie JSON 素材，可直接点选启用；自己添加的素材会显示在下面，长按可删除。");
        hint.setTextColor(Color.rgb(92, 99, 111));
        hint.setTextSize(13);
        hint.setLineSpacing(dp(3), 1.0f);
        hint.setPadding(0, dp(10), 0, dp(10));
        card.addView(hint, matchWrapParams());

        Button addLocal = new Button(this);
        addLocal.setText("添加本地 Lottie JSON");
        addLocal.setAllCaps(false);
        addLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker(REQUEST_JSON);
            }
        });
        LinearLayout.LayoutParams addParams = wrapWrapParams();
        addParams.setMargins(0, 0, 0, dp(12));
        card.addView(addLocal, addParams);

        materialList = new LinearLayout(this);
        materialList.setOrientation(LinearLayout.VERTICAL);
        card.addView(materialList, matchWrapParams());
        return card;
    }

    private void openLottieFiles() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://lottiefiles.com/free-animations"));
            startActivity(intent);
        } catch (Throwable throwable) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (requestCode == REQUEST_PNG) {
            intent.setType("image/png");
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/json",
                    "text/json",
                    "text/plain",
                    "application/octet-stream"
            });
        }
        try {
            startActivityForResult(intent, requestCode);
        } catch (Throwable throwable) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshState() {
        FingerprintStyleSettingsProvider.Settings settings =
                FingerprintStyleSettingsProvider.getLocalSettings(this);
        if (enabledSwitch != null) {
            enabledSwitch.setChecked(settings.enabled);
            enabledSwitch.setEnabled(settings.available);
        }
        if (loopSwitch != null) {
            loopSwitch.setChecked(settings.loop);
            loopSwitch.setEnabled(settings.available
                    && FingerprintStyleSettingsProvider.TYPE_ANIMATION.equals(settings.type));
        }
        if (fileValue != null) {
            if (settings.available) {
                fileValue.setText("当前文件：" + settings.label + "\n类型：" + labelForType(settings.type));
            } else {
                fileValue.setText("当前未选择文件。推荐使用 Lottie JSON；PNG 也可用于静态图案替换。");
            }
        }
        if (statusValue != null) {
            if (settings.enabled) {
                statusValue.setText("状态：已启用。修改后建议强制停止或重启 BiometricSetting / 重启手机后测试。");
            } else if (settings.available) {
                statusValue.setText("状态：已有文件，但当前未启用。");
            } else {
                statusValue.setText("状态：使用系统默认指纹图标。");
            }
        }
        refreshMaterialList(settings);
    }

    private void refreshMaterialList(FingerprintStyleSettingsProvider.Settings settings) {
        if (materialList == null) {
            return;
        }
        stopPreviewAnimations();
        materialList.removeAllViews();
        List<FingerprintStyleSettingsProvider.MaterialItem> items =
                FingerprintStyleSettingsProvider.listMaterials(this);
        for (FingerprintStyleSettingsProvider.MaterialItem item : items) {
            boolean selected = item.source.equals(settings.source)
                    && FingerprintStyleSettingsProvider.TYPE_ANIMATION.equals(settings.type);
            materialList.addView(buildMaterialRow(item, selected));
        }
    }

    private View buildMaterialRow(final FingerprintStyleSettingsProvider.MaterialItem item,
                                  boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? Color.rgb(229, 241, 255) : Color.rgb(249, 250, 252));
        background.setCornerRadius(dp(8));
        background.setStroke(1, selected ? Color.rgb(58, 129, 255) : Color.rgb(226, 230, 238));
        row.setBackground(background);

        LottiePreviewView preview = new LottiePreviewView(this);
        preview.setSource(item.source);
        preview.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable previewBackground = new GradientDrawable();
        previewBackground.setColor(Color.WHITE);
        previewBackground.setCornerRadius(dp(8));
        previewBackground.setStroke(1, Color.rgb(226, 230, 238));
        preview.setBackground(previewBackground);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        previewParams.setMargins(0, 0, dp(12), 0);
        row.addView(preview, previewParams);
        previewViews.add(preview);
        loadMaterialPreview(preview, item);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        TextView name = new TextView(this);
        name.setText((selected ? "已选择： " : "") + item.label);
        name.setTextColor(Color.rgb(20, 24, 31));
        name.setTextSize(15);
        name.setIncludeFontPadding(false);
        texts.addView(name, matchWrapParams());

        TextView type = new TextView(this);
        type.setText(item.custom ? "自定义素材，循环预览，长按删除" : "默认素材，不能删除");
        type.setTextColor(Color.rgb(92, 99, 111));
        type.setTextSize(12);
        type.setPadding(0, dp(5), 0, 0);
        texts.addView(type, matchWrapParams());

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean selected = FingerprintStyleSettingsProvider.selectMaterial(
                        FingerprintStyleSettingsActivity.this, item.source);
                refreshState();
                Toast.makeText(FingerprintStyleSettingsActivity.this,
                        selected ? "已启用素材：" + item.label : "素材启用失败",
                        Toast.LENGTH_SHORT).show();
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!item.custom) {
                    Toast.makeText(FingerprintStyleSettingsActivity.this,
                            "默认素材不能删除", Toast.LENGTH_SHORT).show();
                    return true;
                }
                confirmDeleteMaterial(item);
                return true;
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private void loadMaterialPreview(final LottiePreviewView preview,
                                     final FingerprintStyleSettingsProvider.MaterialItem item) {
        InputStream input = null;
        try {
            int rawResId = FingerprintStyleSettingsProvider.materialRawResId(item.source);
            LottieResult<LottieComposition> result;
            if (rawResId != 0) {
                result = LottieCompositionFactory.fromRawResSync(getApplicationContext(), rawResId,
                        FingerprintStyleSettingsProvider.materialCacheKey(item.source));
                showPreviewResult(preview, item, result);
                return;
            }
            input = FingerprintStyleSettingsProvider.openMaterialInputStream(this, item.source);
            if (input == null) {
                preview.showFallback(android.R.drawable.ic_menu_gallery);
                return;
            }
            result = LottieCompositionFactory.fromJsonInputStreamSync(input,
                    FingerprintStyleSettingsProvider.materialCacheKey(item.source));
            input = null;
            showPreviewResult(preview, item, result);
        } catch (Throwable throwable) {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
            showPreviewFailure(preview, item, throwable);
        }
    }

    private void showPreviewResult(LottiePreviewView preview,
                                   FingerprintStyleSettingsProvider.MaterialItem item,
                                   LottieResult<LottieComposition> result) {
        if (result != null && result.getValue() != null) {
            showPreviewComposition(preview, item, result.getValue());
        } else {
            showPreviewFailure(preview, item,
                    result != null ? result.getException() : null);
        }
    }

    private void showPreviewComposition(LottiePreviewView preview,
                                        FingerprintStyleSettingsProvider.MaterialItem item,
                                        LottieComposition composition) {
        if (preview.getParent() == null || !item.source.equals(preview.getSource())) {
            return;
        }
        preview.setComposition(composition);
    }

    private void showPreviewFailure(LottiePreviewView preview,
                                    FingerprintStyleSettingsProvider.MaterialItem item,
                                    Throwable throwable) {
        if (LogSettingsProvider.getLocalEnabled(this)) {
            Log.w(TAG, "Lottie preview failed: " + item.source + " / " + item.label, throwable);
        }
        preview.showFallback(android.R.drawable.ic_menu_report_image);
    }

    private void stopPreviewAnimations() {
        for (LottiePreviewView preview : previewViews) {
            try {
                preview.clearPreview();
            } catch (Throwable ignored) {
            }
        }
        previewViews.clear();
    }

    private static final class LottiePreviewView extends View {
        private String source = "";
        private LottieDrawable animationDrawable;
        private Drawable fallbackDrawable;

        LottiePreviewView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        void setSource(String source) {
            this.source = source != null ? source : "";
        }

        String getSource() {
            return source;
        }

        void setComposition(LottieComposition composition) {
            clearPreview();
            if (composition == null) {
                showFallback(android.R.drawable.ic_menu_report_image);
                return;
            }
            LottieDrawable drawable = new LottieDrawable();
            drawable.setComposition(composition);
            drawable.setRepeatCount(LottieDrawable.INFINITE);
            drawable.setIgnoreDisabledSystemAnimations(true);
            drawable.setCallback(this);
            animationDrawable = drawable;
            updateDrawableBounds();
            drawable.playAnimation();
            invalidate();
        }

        void showFallback(int resId) {
            clearPreview();
            try {
                fallbackDrawable = getResources().getDrawable(resId);
                fallbackDrawable.setCallback(this);
                updateDrawableBounds();
            } catch (Throwable ignored) {
                fallbackDrawable = null;
            }
            invalidate();
        }

        void clearPreview() {
            if (animationDrawable != null) {
                try {
                    animationDrawable.cancelAnimation();
                    animationDrawable.clearComposition();
                    animationDrawable.setCallback(null);
                } catch (Throwable ignored) {
                }
                animationDrawable = null;
            }
            if (fallbackDrawable != null) {
                try {
                    fallbackDrawable.setCallback(null);
                } catch (Throwable ignored) {
                }
                fallbackDrawable = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Drawable drawable = animationDrawable != null ? animationDrawable : fallbackDrawable;
            if (drawable != null) {
                updateDrawableBounds();
                drawable.draw(canvas);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            updateDrawableBounds();
        }

        @Override
        protected boolean verifyDrawable(Drawable who) {
            return who == animationDrawable || who == fallbackDrawable || super.verifyDrawable(who);
        }

        @Override
        protected void onDetachedFromWindow() {
            clearPreview();
            super.onDetachedFromWindow();
        }

        private void updateDrawableBounds() {
            int left = getPaddingLeft();
            int top = getPaddingTop();
            int right = Math.max(left, getWidth() - getPaddingRight());
            int bottom = Math.max(top, getHeight() - getPaddingBottom());
            if (animationDrawable != null) {
                animationDrawable.setBounds(left, top, right, bottom);
            }
            if (fallbackDrawable != null) {
                fallbackDrawable.setBounds(left, top, right, bottom);
            }
        }
    }

    private void confirmDeleteMaterial(final FingerprintStyleSettingsProvider.MaterialItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除素材")
                .setMessage("确定删除这个自定义素材吗？\n" + item.label)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean deleted = FingerprintStyleSettingsProvider.deleteMaterial(
                                FingerprintStyleSettingsActivity.this, item.source);
                        refreshState();
                        Toast.makeText(FingerprintStyleSettingsActivity.this,
                                deleted ? "已删除素材" : "删除失败",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private String labelForType(String type) {
        return FingerprintStyleSettingsProvider.TYPE_PNG.equals(type) ? "PNG 静态图" : "Lottie JSON 动画";
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(20, 24, 31));
        view.setTextSize(18);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(8));
        background.setStroke(1, Color.rgb(224, 228, 235));
        card.setBackground(background);
        return card;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapWrapParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER_VERTICAL;
        return params;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
