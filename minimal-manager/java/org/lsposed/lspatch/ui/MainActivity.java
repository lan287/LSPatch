package org.lsposed.lspatch.ui;
import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(32, 32, 32, 32);
        
        TextView title = new TextView(this);
        title.setText("LSPatch Manager v0.6");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);
        
        TextView info = new TextView(this);
        info.setTextSize(14);
        info.setPadding(0, 0, 0, 24);
        info.setText("LSPatch 管理器\n\n使用方法:\n1. 下载 lspatch.jar\n2. 运行: java -jar lspatch.jar target.apk --embed module.apk\n3. 安装生成的 *-lspatched.apk\n\n支持 4 种 ABI:\narm64-v8a, armeabi-v7a, x86, x86_64");
        root.addView(info);
        
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }
}
