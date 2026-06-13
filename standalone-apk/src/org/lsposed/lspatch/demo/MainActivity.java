package org.lsposed.lspatch.demo;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements View.OnClickListener {

    private Button btnPatch;
    private Button btnModules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("LSPatch 2.0");
        title.setTextSize(32);
        title.setTextColor(0xFF212121);
        layout.addView(title,
            new LinearLayout.LayoutParams(-2, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("无需 Root 的 Xposed 框架");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF757575);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.bottomMargin = 48;
        layout.addView(subtitle, lp);

        TextView info = new TextView(this);
        info.setText("版本: 2.0.0-beta1\n"
            + "Android SDK: " + Build.VERSION.SDK_INT
            + "\n架构: " + Build.SUPPORTED_ABIS[0]
            + "\nHook 引擎状态: 就绪\n"
            + "模块: 0 个已加载");
        info.setTextSize(14);
        info.setTextColor(0xFF1976D2);
        info.setBackgroundColor(0xFFE3F2FD);
        info.setPadding(32, 32, 32, 32);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = 48;
        layout.addView(info, lp);

        btnPatch = new Button(this);
        btnPatch.setText("选择 APK 修补");
        btnPatch.setTextColor(0xFFFFFFFF);
        btnPatch.setBackgroundColor(0xFF1976D2);
        btnPatch.setOnClickListener(this);
        lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = 16;
        layout.addView(btnPatch, lp);

        btnModules = new Button(this);
        btnModules.setText("管理模块");
        btnModules.setTextColor(0xFFFFFFFF);
        btnModules.setBackgroundColor(0xFF43A047);
        btnModules.setOnClickListener(this);
        layout.addView(btnModules, new LinearLayout.LayoutParams(-1, -2));

        setContentView(layout);
    }

    @Override
    public void onClick(View v) {
        if (v == btnPatch) {
            Toast.makeText(this, "选择 APK 文件以启用 Hook 框架", Toast.LENGTH_SHORT).show();
        } else if (v == btnModules) {
            Toast.makeText(this, "已加载 0 个 Xposed 模块", Toast.LENGTH_SHORT).show();
        }
    }
}