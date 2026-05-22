package com.samsung.feature.extension;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Locale;

public final class TouchSamplingSettingsActivity extends Activity {
    private static final long SAMPLE_WINDOW_MS = 700L;

    private TextView statusView;
    private TextView samplingRateView;
    private final ArrayDeque<Long> touchSamples = new ArrayDeque<Long>();
    private long lastTouchEventMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("触控高采样率");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 250));
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("触控高采样率");
        title.setTextColor(Color.rgb(20, 24, 31));
        title.setTextSize(24);
        title.setIncludeFontPadding(false);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView description = new TextView(this);
        description.setText("开启后会通过三星原生 GOS TSP 策略给手指触控补发高扫描率命令。息屏后再次亮屏时，会先短暂发送关闭策略，再自动重新发送开启策略。该功能只影响触屏 TSP，不修改 S Pen 输入通道。");
        description.setTextColor(Color.rgb(92, 99, 111));
        description.setTextSize(15);
        description.setLineSpacing(dp(3), 1.0f);
        description.setPadding(0, dp(12), 0, dp(18));
        root.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        final Switch enableSwitch = new Switch(this);
        enableSwitch.setText("强制开启手指高采样");
        enableSwitch.setTextColor(Color.rgb(24, 29, 36));
        enableSwitch.setTextSize(16);
        enableSwitch.setPadding(0, 0, 0, dp(8));
        card.addView(enableSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setTextColor(Color.rgb(92, 99, 111));
        statusView.setLineSpacing(dp(2), 1.0f);
        card.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        boolean enabled = TouchSamplingSettingsProvider.isEnabled(this);
        enableSwitch.setChecked(enabled);
        updateStatus(enabled);
        enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TouchSamplingSettingsProvider.setEnabled(TouchSamplingSettingsActivity.this, isChecked);
                updateStatus(isChecked);
            }
        });

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout sampleCard = new LinearLayout(this);
        sampleCard.setOrientation(LinearLayout.VERTICAL);
        sampleCard.setBackgroundColor(Color.WHITE);
        sampleCard.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView sampleTitle = new TextView(this);
        sampleTitle.setText("实时触控刷新率检测");
        sampleTitle.setTextColor(Color.rgb(24, 29, 36));
        sampleTitle.setTextSize(16);
        sampleTitle.setIncludeFontPadding(false);
        sampleCard.addView(sampleTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        samplingRateView = new TextView(this);
        samplingRateView.setText("手指在下方区域连续滑动，显示最近触控刷新率。");
        samplingRateView.setTextColor(Color.rgb(92, 99, 111));
        samplingRateView.setTextSize(13);
        samplingRateView.setPadding(0, dp(8), 0, dp(10));
        sampleCard.addView(samplingRateView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView touchPad = new TextView(this);
        touchPad.setText("在这里滑动");
        touchPad.setGravity(android.view.Gravity.CENTER);
        touchPad.setTextColor(Color.rgb(45, 79, 145));
        touchPad.setTextSize(18);
        touchPad.setBackgroundColor(Color.rgb(232, 239, 252));
        touchPad.setPadding(0, dp(36), 0, dp(36));
        touchPad.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                handleSamplingEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    view.performClick();
                }
                return true;
            }
        });
        sampleCard.addView(touchPad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sampleParams.topMargin = dp(14);
        root.addView(sampleCard, sampleParams);

        TextView note = new TextView(this);
        note.setText("提示：切换后通常无需重启手机；开启后只会定时补发 TSP 广播，不再直接写触控节点，也不再修改 GOS 服务状态。限制：连接充电器充电时，系统会强制降低触控采样率，此功能在充电状态下无法生效。若没有立即变化，请确认 LSPosed 中本模块已勾选“设备健康管理”，并重启手机。");
        note.setTextColor(Color.rgb(120, 83, 0));
        note.setTextSize(13);
        note.setLineSpacing(dp(3), 1.0f);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private void updateStatus(boolean enabled) {
        if (statusView == null) {
            return;
        }
        statusView.setText(enabled
                ? "当前状态：已启用。系统会定时补发 TSP 广播，亮屏时会自动关开脉冲一次；充电状态下无法生效。"
                : "当前状态：已关闭。触控采样率使用系统默认策略。");
    }

    private void handleSamplingEvent(MotionEvent event) {
        if (samplingRateView == null || event == null) {
            return;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchSamples.clear();
            lastTouchEventMs = 0L;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            updateSamplingRateText(false);
            return;
        }
        int historySize = event.getHistorySize();
        for (int i = 0; i < historySize; i++) {
            addTouchSample(event.getHistoricalEventTime(i));
        }
        addTouchSample(event.getEventTime());
        updateSamplingRateText(true);
    }

    private void addTouchSample(long eventTimeMs) {
        if (eventTimeMs <= lastTouchEventMs) {
            return;
        }
        lastTouchEventMs = eventTimeMs;
        touchSamples.addLast(Long.valueOf(eventTimeMs));
        long cutoff = eventTimeMs - SAMPLE_WINDOW_MS;
        while (!touchSamples.isEmpty() && touchSamples.peekFirst().longValue() < cutoff) {
            touchSamples.removeFirst();
        }
    }

    private void updateSamplingRateText(boolean active) {
        if (touchSamples.size() < 2) {
            samplingRateView.setText(active
                    ? "继续滑动以计算实时触控刷新率..."
                    : "手指在下方区域连续滑动，显示最近触控刷新率。");
            return;
        }
        long first = touchSamples.peekFirst().longValue();
        long last = touchSamples.peekLast().longValue();
        long duration = Math.max(1L, last - first);
        double hz = (touchSamples.size() - 1) * 1000.0d / duration;
        samplingRateView.setText(String.format(Locale.US,
                "当前估算：%.0f Hz    样本：%d    窗口：%d ms",
                Double.valueOf(hz),
                Integer.valueOf(touchSamples.size()),
                Long.valueOf(duration)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
