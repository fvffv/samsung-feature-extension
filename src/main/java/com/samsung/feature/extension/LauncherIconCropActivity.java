package com.samsung.feature.extension;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

public final class LauncherIconCropActivity extends Activity {
    public static final String EXTRA_PACKAGE = "packageName";
    private static final int SHAPE_SQUARE = 0;
    private static final int SHAPE_ROUNDED = 1;
    private static final int SHAPE_CIRCLE = 2;

    private CropImageView cropView;
    private TextView squareButton;
    private TextView roundedButton;
    private TextView circleButton;
    private TextView radiusValue;
    private SeekBar radiusSeekBar;
    private String packageName;
    private Bitmap sourceBitmap;
    private int selectedShape = SHAPE_SQUARE;
    private int cornerPercent = 24;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherIconLog.init(this);
        setTitle("裁剪图标");

        packageName = getIntent() != null ? getIntent().getStringExtra(EXTRA_PACKAGE) : "";
        Uri uri = getIntent() != null ? getIntent().getData() : null;
        try {
            sourceBitmap = decodeBitmap(uri, 2048);
        } catch (Throwable t) {
            LauncherIconLog.log("crop decode failed for " + packageName);
            LauncherIconLog.log(t);
        }
        if (sourceBitmap == null || packageName == null || packageName.length() == 0) {
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildContentView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
        }
    }

    private void buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 250));
        root.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView title = new TextView(this);
        title.setText("裁剪桌面图标");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(22);
        title.setIncludeFontPadding(false);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tip = new TextView(this);
        tip.setText("拖动图片调整位置，双指缩放；保存前可选择方形、圆角或圆形图标。");
        tip.setTextColor(Color.rgb(98, 105, 117));
        tip.setTextSize(14);
        tip.setPadding(0, dp(8), 0, dp(12));
        root.addView(tip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        cropView = new CropImageView(this, sourceBitmap);
        cropView.setShape(selectedShape, cornerPercent);
        cropView.setBackgroundColor(Color.rgb(18, 22, 29));
        root.addView(cropView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        addShapeControls(root);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, dp(14), 0, 0);

        TextView cancel = makeButton("取消", Color.rgb(238, 241, 246), Color.rgb(70, 78, 92));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
        ));

        TextView save = makeButton("保存", Color.rgb(48, 105, 240), Color.WHITE);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCrop();
            }
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
        );
        saveParams.setMargins(dp(12), 0, 0, 0);
        buttons.addView(save, saveParams);

        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
        LanguageManager.applyToActivity(this);
    }

    private void addShapeControls(LinearLayout root) {
        TextView section = new TextView(this);
        section.setText("图标形状");
        section.setTextColor(Color.rgb(20, 24, 31));
        section.setTextSize(15);
        section.setIncludeFontPadding(false);
        section.setPadding(0, dp(12), 0, dp(8));
        root.addView(section, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout shapeRow = new LinearLayout(this);
        shapeRow.setOrientation(LinearLayout.HORIZONTAL);
        shapeRow.setGravity(Gravity.CENTER_VERTICAL);

        squareButton = makeShapeButton("方形", SHAPE_SQUARE);
        roundedButton = makeShapeButton("圆角", SHAPE_ROUNDED);
        circleButton = makeShapeButton("圆形", SHAPE_CIRCLE);
        shapeRow.addView(squareButton, new LinearLayout.LayoutParams(0, dp(40), 1f));
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        middleParams.setMargins(dp(8), 0, dp(8), 0);
        shapeRow.addView(roundedButton, middleParams);
        shapeRow.addView(circleButton, new LinearLayout.LayoutParams(0, dp(40), 1f));

        root.addView(shapeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout radiusRow = new LinearLayout(this);
        radiusRow.setOrientation(LinearLayout.HORIZONTAL);
        radiusRow.setGravity(Gravity.CENTER_VERTICAL);
        radiusRow.setPadding(0, dp(10), 0, 0);

        TextView label = new TextView(this);
        label.setText("圆角度");
        label.setTextColor(Color.rgb(70, 78, 92));
        label.setTextSize(14);
        radiusRow.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        radiusSeekBar = new SeekBar(this);
        radiusSeekBar.setMax(50);
        radiusSeekBar.setProgress(cornerPercent);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seekParams.setMargins(dp(10), 0, dp(10), 0);
        radiusRow.addView(radiusSeekBar, seekParams);

        radiusValue = new TextView(this);
        radiusValue.setTextColor(Color.rgb(70, 78, 92));
        radiusValue.setTextSize(14);
        radiusValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        radiusRow.addView(radiusValue, new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));

        radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cornerPercent = progress;
                updateShapeControls();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(radiusRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        updateShapeControls();
    }

    private TextView makeShapeButton(String text, final int shape) {
        TextView view = makeButton(text, Color.WHITE, Color.rgb(70, 78, 92));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedShape = shape;
                updateShapeControls();
            }
        });
        return view;
    }

    private void updateShapeControls() {
        updateShapeButton(squareButton, selectedShape == SHAPE_SQUARE);
        updateShapeButton(roundedButton, selectedShape == SHAPE_ROUNDED);
        updateShapeButton(circleButton, selectedShape == SHAPE_CIRCLE);
        if (radiusSeekBar != null) {
            radiusSeekBar.setEnabled(selectedShape == SHAPE_ROUNDED);
            radiusSeekBar.setAlpha(selectedShape == SHAPE_ROUNDED ? 1.0f : 0.45f);
        }
        if (radiusValue != null) {
            radiusValue.setText(selectedShape == SHAPE_ROUNDED ? cornerPercent + "%" : "--");
            radiusValue.setAlpha(selectedShape == SHAPE_ROUNDED ? 1.0f : 0.45f);
        }
        if (cropView != null) {
            cropView.setShape(selectedShape, cornerPercent);
        }
    }

    private void updateShapeButton(TextView view, boolean active) {
        if (view == null) {
            return;
        }
        view.setTextColor(active ? Color.WHITE : Color.rgb(70, 78, 92));
        view.setBackground(makeRoundedBackground(
                active ? Color.rgb(48, 105, 240) : Color.WHITE,
                dp(8),
                Color.rgb(224, 228, 236),
                dp(1)
        ));
    }

    private TextView makeButton(String text, int background, int textColor) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(textColor);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setBackground(makeRoundedBackground(background, dp(8), 0, 0));
        return view;
    }

    private void saveCrop() {
        try {
            Bitmap bitmap = cropView.createCroppedBitmap(LauncherIconCustomizerStore.STORED_ICON_SIZE);
            Bitmap shaped = applyShape(bitmap, selectedShape, cornerPercent);
            try {
                LauncherIconCustomizerStore.saveIconBitmap(this, packageName, shaped);
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                if (shaped != bitmap && shaped != null && !shaped.isRecycled()) {
                    shaped.recycle();
                }
            }
            LauncherIconLog.log("crop save finished, package=" + packageName
                    + ", shape=" + selectedShape + ", cornerPercent=" + cornerPercent);
            Intent result = new Intent();
            result.putExtra(EXTRA_PACKAGE, packageName);
            setResult(RESULT_OK, result);
            finish();
        } catch (Throwable t) {
            LauncherIconLog.log("crop save failed for " + packageName);
            LauncherIconLog.log(t);
            Toast.makeText(this, "保存失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap applyShape(Bitmap bitmap, int shape, int cornerPercent) {
        if (bitmap == null || bitmap.isRecycled() || shape == SHAPE_SQUARE) {
            return bitmap;
        }
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);
        RectF rect = new RectF(0, 0, size, size);
        if (shape == SHAPE_CIRCLE) {
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        } else {
            float radius = size * Math.max(0, Math.min(50, cornerPercent)) / 100f;
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
        return output;
    }

    private Drawable makeRoundedBackground(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private Bitmap decodeBitmap(Uri uri, int maxSize) throws IOException {
        if (uri == null) {
            return null;
        }
        InputStream input = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            input = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(input, null, bounds);
            closeQuietly(input);
            input = null;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            input = getContentResolver().openInputStream(uri);
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

    private static void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (Throwable ignored) {
            // Ignore cleanup failure.
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class CropImageView extends View {
        private static final int MODE_NONE = 0;
        private static final int MODE_DRAG = 1;
        private static final int MODE_ZOOM = 2;

        private final Bitmap bitmap;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cropRect = new RectF();
        private final RectF dstRect = new RectF();

        private float scale;
        private float minScale;
        private float offsetX;
        private float offsetY;
        private float lastX;
        private float lastY;
        private float lastDistance;
        private float lastFocusX;
        private float lastFocusY;
        private int mode = MODE_NONE;
        private int shape = SHAPE_SQUARE;
        private int cornerPercent;
        private boolean initialized;

        CropImageView(Context context, Bitmap bitmap) {
            super(context);
            this.bitmap = bitmap;
            dimPaint.setColor(0x99000000);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(context, 2));
            borderPaint.setColor(Color.WHITE);
            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setStrokeWidth(dp(context, 2));
            shapePaint.setColor(Color.rgb(77, 140, 255));
        }

        void setShape(int shape, int cornerPercent) {
            this.shape = shape;
            this.cornerPercent = cornerPercent;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            initialized = false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ensureTransform();
            dstRect.set(
                    offsetX,
                    offsetY,
                    offsetX + bitmap.getWidth() * scale,
                    offsetY + bitmap.getHeight() * scale
            );
            canvas.drawBitmap(bitmap, null, dstRect, bitmapPaint);
            canvas.drawRect(0, 0, getWidth(), cropRect.top, dimPaint);
            canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), dimPaint);
            canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
            canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, dimPaint);
            canvas.drawRect(cropRect, borderPaint);
            drawShapePreview(canvas);
        }

        private void drawShapePreview(Canvas canvas) {
            if (shape == SHAPE_CIRCLE) {
                canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width() / 2f, shapePaint);
            } else if (shape == SHAPE_ROUNDED) {
                float radius = cropRect.width() * Math.max(0, Math.min(50, cornerPercent)) / 100f;
                canvas.drawRoundRect(cropRect, radius, radius, shapePaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            ensureTransform();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                mode = MODE_DRAG;
                lastX = event.getX();
                lastY = event.getY();
                return true;
            }
            if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
                mode = MODE_ZOOM;
                lastDistance = distance(event);
                lastFocusX = focusX(event);
                lastFocusY = focusY(event);
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (mode == MODE_ZOOM && event.getPointerCount() >= 2) {
                    float distance = distance(event);
                    float focusX = focusX(event);
                    float focusY = focusY(event);
                    if (lastDistance > 0f && distance > 0f) {
                        float factor = distance / lastDistance;
                        zoomAt(factor, focusX, focusY);
                    }
                    offsetX += focusX - lastFocusX;
                    offsetY += focusY - lastFocusY;
                    lastDistance = distance;
                    lastFocusX = focusX;
                    lastFocusY = focusY;
                    clampOffset();
                    invalidate();
                    return true;
                }
                if (mode == MODE_DRAG) {
                    float x = event.getX();
                    float y = event.getY();
                    offsetX += x - lastX;
                    offsetY += y - lastY;
                    lastX = x;
                    lastY = y;
                    clampOffset();
                    invalidate();
                    return true;
                }
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mode = MODE_NONE;
                return true;
            }
            if (action == MotionEvent.ACTION_POINTER_UP) {
                mode = event.getPointerCount() > 2 ? MODE_ZOOM : MODE_DRAG;
                if (event.getPointerCount() > 0) {
                    lastX = event.getX(0);
                    lastY = event.getY(0);
                }
                return true;
            }
            return true;
        }

        Bitmap createCroppedBitmap(int outputSize) {
            ensureTransform();
            float sourceLeft = (cropRect.left - offsetX) / scale;
            float sourceTop = (cropRect.top - offsetY) / scale;
            int side = Math.round(cropRect.width() / scale);
            side = Math.max(1, Math.min(side, Math.min(bitmap.getWidth(), bitmap.getHeight())));
            int left = clamp(Math.round(sourceLeft), 0, bitmap.getWidth() - side);
            int top = clamp(Math.round(sourceTop), 0, bitmap.getHeight() - side);
            Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, side, side);
            if (cropped.getWidth() == outputSize && cropped.getHeight() == outputSize) {
                return cropped;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true);
            cropped.recycle();
            return scaled;
        }

        private void ensureTransform() {
            if (initialized || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            int padding = dp(getContext(), 20);
            int side = Math.min(getWidth() - padding * 2, getHeight() - padding * 2);
            side = Math.max(1, side);
            float left = (getWidth() - side) / 2f;
            float top = (getHeight() - side) / 2f;
            cropRect.set(left, top, left + side, top + side);
            minScale = Math.max(cropRect.width() / bitmap.getWidth(), cropRect.height() / bitmap.getHeight());
            scale = minScale;
            offsetX = cropRect.centerX() - bitmap.getWidth() * scale / 2f;
            offsetY = cropRect.centerY() - bitmap.getHeight() * scale / 2f;
            clampOffset();
            initialized = true;
        }

        private void zoomAt(float factor, float focusX, float focusY) {
            float oldScale = scale;
            float newScale = scale * factor;
            float maxScale = minScale * 8f;
            if (newScale < minScale) {
                newScale = minScale;
            } else if (newScale > maxScale) {
                newScale = maxScale;
            }
            if (newScale == oldScale) {
                return;
            }
            float sourceX = (focusX - offsetX) / oldScale;
            float sourceY = (focusY - offsetY) / oldScale;
            scale = newScale;
            offsetX = focusX - sourceX * scale;
            offsetY = focusY - sourceY * scale;
        }

        private void clampOffset() {
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            if (width <= cropRect.width()) {
                offsetX = cropRect.centerX() - width / 2f;
            } else {
                if (offsetX > cropRect.left) {
                    offsetX = cropRect.left;
                }
                if (offsetX + width < cropRect.right) {
                    offsetX = cropRect.right - width;
                }
            }
            if (height <= cropRect.height()) {
                offsetY = cropRect.centerY() - height / 2f;
            } else {
                if (offsetY > cropRect.top) {
                    offsetY = cropRect.top;
                }
                if (offsetY + height < cropRect.bottom) {
                    offsetY = cropRect.bottom - height;
                }
            }
        }

        private static float distance(MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private static float focusX(MotionEvent event) {
            return (event.getX(0) + event.getX(1)) / 2f;
        }

        private static float focusY(MotionEvent event) {
            return (event.getY(0) + event.getY(1)) / 2f;
        }

        private static int clamp(int value, int min, int max) {
            if (max < min) {
                return min;
            }
            if (value < min) {
                return min;
            }
            if (value > max) {
                return max;
            }
            return value;
        }

        private static int dp(Context context, int value) {
            return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
