package com.samsung.feature.extension;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;

public final class SpenMouseSettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private boolean createdEnglish;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            try {
                Bundle b = getContentResolver().call(SpenMouseSettingsProvider.URI, "get", null, null);
                Bundle r = b == null ? null : b.getBundle("runtime");
                if (r == null || SystemClock.elapsedRealtime() - r.getLong("heartbeat") > 6000) {
                    status.setText(tr("AirCommand 尚未连接模块。请在 LSPosed 作用域勾选“Air command”，重启 AirCommand 或手机后再试。"));
                } else {
                    status.setText(tr(r.getString("message", "等待 S Pen")) + tr("\n移动事件：") + r.getLong("motions")
                            + tr("　按钮事件：") + r.getLong("buttons")
                            + String.format(java.util.Locale.ROOT,tr("\n近期平均：SDK 输入 %.1f 次/秒 · 位移输出 %.1f 次/秒"),
                                r.getFloat("input_hz"),r.getFloat("output_hz"))
                            + tr("\n请持续移动时观察；静止和小数位移会降低计数。输出频率不等于传感器采样率。"));
                }
            } catch (Throwable t) { status.setText(tr("读取状态失败：") + t.getClass().getSimpleName()); }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        createdEnglish=LanguageManager.isEnglish(this);
        setTitle(tr("S Pen 空中鼠标"));
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(246,247,250));
        scroll.addView(root);
        text(root, "S Pen 空中鼠标", 24);
        text(root, "使用系统原生鼠标光标。需要带蓝牙隔空操作的 S Pen（如 S22/S23/S24 Ultra），并允许三星功能扩展使用 Root。首次使用需将 Air command 加入 LSPosed 作用域。", 15);
        Bundle current = SpenMouseSettingsProvider.read(this);
        Switch enabled = new Switch(this);
        enabled.setText("开启空中鼠标");
        enabled.setTextSize(18);
        enabled.setPadding(0, dp(16), 0, dp(16));
        enabled.setChecked(current.getBoolean("enabled"));
        enabled.setOnCheckedChangeListener((view, checked) -> {
            Bundle b = new Bundle(); b.putBoolean("enabled", checked);
            SpenMouseSettingsProvider.save(this, b);
        });
        root.addView(enabled);
        text(root, "转动笔身或手腕移动系统鼠标；笔按钮相当于鼠标左键，短按点击、按住长按，按住时移动可拖拽。指针样式和加速由系统管理。插笔、息屏、锁屏时移除虚拟鼠标，关闭后恢复隔空操作。持续使用会增加笔的耗电。", 15);
        TextView speed = text(root, "灵敏度：" + current.getInt("sensitivity") + "%", 17);
        SeekBar seek = new SeekBar(this); seek.setMax(275); seek.setProgress(current.getInt("sensitivity") - 25);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                speed.setText(tr("灵敏度：") + (v + 25) + "%");
                if (user) { Bundle b = new Bundle(); b.putInt("sensitivity", v + 25); SpenMouseSettingsProvider.save(SpenMouseSettingsActivity.this,b); }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(seek);
        TextView smoothLabel = text(root, "防抖 / 平滑强度：" + current.getInt("smoothing") + "%", 17);
        SeekBar smooth = new SeekBar(this); smooth.setMax(100); smooth.setProgress(current.getInt("smoothing"));
        smooth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                smoothLabel.setText(tr("防抖 / 平滑强度：") + v + "%" + (v == 0 ? tr("（关闭）") : ""));
                if(user) { Bundle b = new Bundle(); b.putInt("smoothing",v); SpenMouseSettingsProvider.save(SpenMouseSettingsActivity.this,b); }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(smooth);
        text(root, "默认 50%。小幅移动加强抑抖，快速移动降低延迟，并将位移拆分为更细的系统鼠标事件。强度越高越稳，但跟手性会下降；设为 0 可对比原始效果。按住按钮仍可拖动。", 14);
        Button settings = new Button(this); settings.setText("打开系统 S Pen 设置");
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent().setClassName(SpenMouseSettingsProvider.TARGET,
                "com.samsung.android.service.aircommand.ui.settings.spen.SpenSettingsActivity")); }
            catch (Throwable t) { Toast.makeText(this,tr("请从系统设置 → 高级功能 → S Pen 打开"),Toast.LENGTH_LONG).show(); }
        });
        root.addView(settings);
        status = text(root, "正在读取运行状态…", 14);
        TextView testResult = text(root, "可在下方验证按钮点击和长按（不会执行其他操作）", 14);
        Button test = new Button(this); test.setText("点击 / 长按测试区");
        test.setOnClickListener(v -> testResult.setText(tr("收到点击：") + SystemClock.uptimeMillis()));
        test.setOnLongClickListener(v -> { testResult.setText(tr("收到长按：") + SystemClock.uptimeMillis()); return true; });
        root.addView(test);
        setContentView(scroll);
        LanguageManager.applyToActivity(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if(createdEnglish!=LanguageManager.isEnglish(this)) { recreate(); return; }
        handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private TextView text(LinearLayout r, String s, int size) {
        TextView v = new TextView(this); v.setText(tr(s)); v.setTextSize(size);
        v.setTextColor(Color.rgb(24,29,36)); v.setPadding(0,dp(8),0,dp(8)); r.addView(v); return v;
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private String tr(String s) { return LanguageManager.text(this,s); }
}
