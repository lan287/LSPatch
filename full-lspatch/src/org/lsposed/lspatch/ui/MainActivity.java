package org.lsposed.lspatch.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout.LayoutParams;

import org.lsposed.lspatch.patch.ApkPatchEngine;
import org.lsposed.lspatch.patch.ApkSigner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private File targetFile;
    private List<File> moduleFiles = new ArrayList<>();
    private TextView tvStatus, tvLog;
    private StringBuilder logs = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestStoragePermission();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFFAFAFA);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("LSPatch v0.6");
        title.setTextSize(26);
        title.setTextColor(0xFFFF5722);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("无需 Root 的 Xposed 框架 - 在设备上修补目标 APK");
        sub.setTextSize(13);
        sub.setTextColor(0xFF757575);
        sub.setPadding(0, 0, 0, dp(16));
        root.addView(sub);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(13);
        tvStatus.setTextColor(0xFF212121);
        tvStatus.setPadding(0, dp(8), 0, dp(8));
        updateStatus();
        root.addView(tvStatus);

        Button btnTarget = new Button(this);
        btnTarget.setText("1. 选择目标 APK");
        btnTarget.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pickTarget(); } });
        root.addView(btnTarget, fillWidth());

        Button btnAddMod = new Button(this);
        btnAddMod.setText("2. 添加模块 APK (可选)");
        btnAddMod.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pickModule(); } });
        root.addView(btnAddMod, fillWidth());

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setBackgroundColor(0xFFFFFFFF);
        options.setPadding(dp(12), dp(12), dp(12), dp(12));
        LayoutParams olp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        olp.topMargin = dp(12);
        olp.bottomMargin = dp(12);
        root.addView(options, olp);

        TextView optTitle = new TextView(this);
        optTitle.setText("选项");
        optTitle.setTextSize(14);
        optTitle.getPaint().setFakeBoldText(true);
        optTitle.setPadding(0, 0, 0, dp(8));
        options.addView(optTitle);

        final CheckBox cbDebug = new CheckBox(this);
        cbDebug.setText("设置为 debuggable");
        cbDebug.setTextSize(14);
        options.addView(cbDebug);

        final CheckBox cbOverride = new CheckBox(this);
        cbOverride.setText("允许降级安装 (覆盖 versionCode)");
        cbOverride.setTextSize(14);
        options.addView(cbOverride);

        Button btnPatch = new Button(this);
        btnPatch.setText("3. 开始修补并签名");
        btnPatch.setTextSize(17);
        btnPatch.getPaint().setFakeBoldText(true);
        btnPatch.setPadding(0, dp(14), 0, dp(14));
        btnPatch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doPatch(cbDebug.isChecked(), cbOverride.isChecked()); }
        });
        root.addView(btnPatch, fillWidth());

        tvLog = new TextView(this);
        tvLog.setTextSize(12);
        tvLog.setTextColor(0xFF212121);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setMaxHeight(dp(300));
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setPadding(0, dp(16), 0, 0);
        root.addView(tvLog);

        log("LSPatch v0.6 就绪 - 请选择目标 APK");
    }

    private void doPatch(final boolean debug, final boolean override) {
        if (targetFile == null) { toast("请先选择目标 APK"); return; }
        new Thread(new Runnable() { @Override public void run() {
            try {
                log("=== LSPatch 修补开始 ===");
                log("目标: " + targetFile.getName());
                log("模块: " + moduleFiles.size() + " 个");
                ApkPatchEngine engine = new ApkPatchEngine(MainActivity.this);
                File[] mods = moduleFiles.toArray(new File[0]);
                final File output = engine.patch2(targetFile, mods, debug, override, new ApkSigner.LogCallback() {
                    @Override public void log(String msg) { MainActivity.this.log(msg); }
                });
                log("✅ 完成: " + output.getName() + " (" + (output.length() / 1048576L) + " MB)");
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("修补完成")
                            .setMessage("输出文件:\n" + output.getAbsolutePath() + "\n\n点击确定立即安装。")
                            .setPositiveButton("安装", new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface d, int w) { installApk(output); }
                            })
                            .setNegativeButton("关闭", null)
                            .show();
                }});
            } catch (final Throwable e) {
                log("❌ 失败: " + e.getMessage());
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("修补失败")
                            .setMessage(e.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                }});
            }
        }}).start();
    }

    private void installApk(File apk) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 简化: 直接使用 ACTION_SENDTO 或系统文件管理器
                // 为了不依赖 androidx，我们用简单的方式：
                // 调用系统包安装器
                Uri uri = Uri.parse("file://" + apk.getAbsolutePath());
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
            } else {
                intent.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) { toast("安装失败: " + e.getMessage()); }
    }

    private void pickTarget() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择目标 APK"), 1001);
    }

    private void pickModule() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择模块 APK"), 1002);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        File saved = saveAsTemp(data.getData(), req == 1001 ? "target.apk" : "module-" + (moduleFiles.size() + 1) + ".apk");
        if (saved == null) { toast("无法读取文件"); return; }
        if (req == 1001) { targetFile = saved; log("✓ 目标: " + saved.getName()); }
        else { moduleFiles.add(saved); log("✓ 模块: " + saved.getName()); }
        updateStatus();
    }

    private File saveAsTemp(Uri uri, String name) {
        try {
            File out = new File(getExternalFilesDir(null), name);
            InputStream is = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            is.close(); fos.close();
            return out;
        } catch (Exception e) { return null; }
    }

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("目标 APK: ").append(targetFile == null ? "(未选择)" : targetFile.getName()).append("\n");
        sb.append("模块数: ").append(moduleFiles.size());
        tvStatus.setText(sb.toString());
    }

    private void log(final String msg) {
        logs.append(msg).append("\n");
        runOnUiThread(new Runnable() { @Override public void run() { tvLog.setText(logs.toString()); }});
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private LayoutParams fillWidth() { return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
