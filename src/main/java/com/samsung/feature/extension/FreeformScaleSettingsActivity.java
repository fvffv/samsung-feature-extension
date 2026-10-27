package com.samsung.feature.extension;

import android.app.Activity;
import android.graphics.Color;
import android.os.*;
import android.widget.*;

public final class FreeformScaleSettingsActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;
    private boolean createdEnglish;
    private final Runnable refresh=new Runnable() {
        public void run() {
            try {
                Bundle b=getContentResolver().call(FreeformScaleSettingsProvider.URI,"get",null,null);
                Bundle r=b==null?null:b.getBundle("runtime");
                if(r==null||SystemClock.elapsedRealtime()-r.getLong("heartbeat")>75000) {
                    status.setText(tr("系统缩放接口尚未连接。请在 LSPosed 作用域勾选“系统框架 / android”，安装此版本后重启手机一次。"));
                } else {
                    status.setText(tr(r.getString("message","系统接口已连接"))+tr("\n已处理配置次数：")+r.getLong("changes")
                        +(r.getInt("density")>0?tr("\n最近的小窗显示密度：")+r.getInt("density")+" dpi":""));
                }
            } catch(Throwable t) { status.setText(tr("状态读取失败：")+t.getClass().getSimpleName()); }
            handler.postDelayed(this,2000);
        }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); createdEnglish=LanguageManager.isEnglish(this); setTitle(tr("小窗等比例缩放"));
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(20*getResources().getDisplayMetrics().density+.5f);
        root.setPadding(pad,pad,pad,pad); root.setBackgroundColor(Color.rgb(246,247,250)); scroll.addView(root);
        text(root,"小窗等比例缩放",24);
        text(root,"缩小弹出式小窗时，让应用内的文字、图片和控件一起等比例缩小，减少界面挤压和大面积裁切。",16);
        Switch toggle=new Switch(this); toggle.setText("开启小窗内容缩放"); toggle.setTextSize(18);
        toggle.setChecked(FreeformScaleSettingsProvider.isEnabled(this));
        toggle.setOnCheckedChangeListener((v,on)->FreeformScaleSettingsProvider.setEnabled(this,on)); root.addView(toggle);
        text(root,"仅作用于手机主屏幕上的普通弹出式小窗。全屏、分屏、画中画及 DeX 不受此开关影响。改变窗口宽高比时，应用仍可能重新排版。部分自行绘制或使用像素固定布局的应用可能有适配差异。",14);
        text(root,"首次安装需要勾选 LSPosed 的“系统框架 / android”作用域并重启手机；之后开关会自动同步。已有小窗可拖动边角调整一次尺寸，或重新打开。关闭后恢复系统默认显示密度。",14);
        status=text(root,"读取系统状态…",14);
        setContentView(scroll); LanguageManager.applyToActivity(this);
    }
    private TextView text(LinearLayout root,String s,int size) {
        TextView v=new TextView(this); v.setText(tr(s)); v.setTextSize(size); v.setTextColor(Color.rgb(24,29,36));
        int p=(int)(10*getResources().getDisplayMetrics().density+.5f); v.setPadding(0,p,0,p); root.addView(v); return v;
    }
    @Override protected void onResume() {
        super.onResume();
        if(createdEnglish!=LanguageManager.isEnglish(this)) { recreate(); return; }
        handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private String tr(String s) { return LanguageManager.text(this,s); }
}

