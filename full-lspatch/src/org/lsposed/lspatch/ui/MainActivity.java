package org.lsposed.lspatch.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.lsposed.lspatch.patch.ApkPatchEngine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_TARGET_FILE = 1001;
    private static final int REQ_MODULE_FILE = 1002;
    private static final int REQ_TARGET_APP = 2001;
    private static final int REQ_MODULE_APP = 2002;
    private static final int REQ_KEY_FILE = 3001;

    private File targetFile;
    private final List<File> moduleFiles = new ArrayList<>();
    private TextView tvStatus, tvKeyStatus, tvLog;
    private final StringBuilder logs = new StringBuilder();
    private SharedPreferences prefs;

    private File customKeyStoreFile;
    private String customKeyPass = "";
    private PrivateKey customPrivateKey;
    private X509Certificate customCert;
    private EditText etKeyPass;
    private int sigBypassLevel = 0;
    private boolean debuggable = false;
    private boolean overrideVersion = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            prefs = getSharedPreferences("lspatch_prefs", MODE_PRIVATE);
            loadSavedPrefs();
            buildAllUI();
            updateAllStatus();
            log("LSPatch v0.7.2 就绪");
            requestStoragePermission();
        } catch (Throwable t) {
            // fallback: show error screen
            TextView err = new TextView(this);
            err.setText("LSPatch 启动失败:\n" + t.getClass().getName() + "\n" + t.getMessage());
            err.setTextSize(14);
            err.setTextColor(Color.RED);
            err.setPadding(40, 40, 40, 40);
            setContentView(err);
        }
    }

    private void loadSavedPrefs() {
        String savedKeyPath = prefs.getString("custom_key_path", "");
        if (!savedKeyPath.isEmpty()) {
            File f = new File(savedKeyPath);
            if (f.exists()) {
                customKeyStoreFile = f;
                customKeyPass = prefs.getString("custom_key_pass", "");
            }
        }
        sigBypassLevel = prefs.getInt("sig_bypass_level", 0);
    }

    // ==================== UI (极简安全) ====================

    private void buildAllUI() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF5F5F5);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(40));
        sv.addView(root);
        setContentView(sv);

        // 标题
        addTitle(root);
        addSpace(root, dp(16));

        // 状态
        tvStatus = label("目标: 未选择  |  模块: 0 个", 13, 0xFF666666);
        tvStatus.setBackgroundColor(0xFFFFFFFF);
        tvStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(tvStatus, matchW());

        addSpace(root, dp(14));

        // 目标 APK
        root.addView(sectionTitle("目标 APK"));
        root.addView(hr(root, btn("从文件选择", this::pickTargetFile), btn("从已安装应用", this::pickTargetApp)));

        addSpace(root, dp(14));

        // 模块 APK
        root.addView(sectionTitle("Xposed 模块 (可选)"));
        root.addView(hr(root, btn("从文件选择", this::pickModuleFile), btn("从已安装应用", this::pickModuleApp)));

        addSpace(root, dp(14));

        // 自定义密钥
        root.addView(sectionTitle("签名密钥"));
        addCustomKeyCard(root);

        addSpace(root, dp(14));

        // 选项
        root.addView(sectionTitle("选项"));
        addOptionsCard(root);

        addSpace(root, dp(14));

        // 签名绕过
        root.addView(sectionTitle("签名校验绕过"));
        addSigBypassCard(root);

        addSpace(root, dp(20));

        // 修补按钮
        Button btnPatch = new Button(this);
        btnPatch.setText("开始修补并签名");
        btnPatch.setTextSize(18);
        btnPatch.setTypeface(Typeface.DEFAULT_BOLD);
        btnPatch.setTextColor(0xFFFFFFFF);
        btnPatch.setBackgroundColor(0xFF6C2DC7);
        btnPatch.setPadding(0, dp(16), 0, dp(16));
        btnPatch.setOnClickListener(v -> doPatch());
        root.addView(btnPatch, matchW());

        addSpace(root, dp(16));

        // 日志
        root.addView(sectionTitle("日志"));
        tvLog = new TextView(this);
        tvLog.setTextSize(11);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setBackgroundColor(0xFF1E1E2E);
        tvLog.setTextColor(0xFFCCCCCC);
        tvLog.setPadding(dp(12), dp(12), dp(12), dp(12));
        tvLog.setMinHeight(dp(180));
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        root.addView(tvLog, matchW());

        // 尝试恢复已保存的密钥
        if (customKeyStoreFile != null && !customKeyPass.isEmpty()) {
            loadCustomKey();
            updateKeyStatus();
        }
    }

    // ---- 标题 ----
    private void addTitle(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // logo 圆
        TextView logo = new TextView(this);
        logo.setText("LS");
        logo.setTextSize(20);
        logo.setTextColor(0xFFFFFFFF);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        int s = dp(48);
        // 使用 PaintDrawable 替代 GradientDrawable (更安全)
        android.graphics.drawable.shapes.OvalShape shape = new android.graphics.drawable.shapes.OvalShape();
        android.graphics.drawable.ShapeDrawable sd = new android.graphics.drawable.ShapeDrawable(shape);
        sd.getPaint().setColor(0xFF6C2DC7);
        logo.setBackground(sd);
        row.addView(logo, new LinearLayout.LayoutParams(s, s));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView t = new TextView(this);
        t.setText("LSPatch");
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF1A1A2E);
        col.addView(t);

        TextView v = new TextView(this);
        v.setText("v0.7.2  |  非 Root Xposed 框架");
        v.setTextSize(11);
        v.setTextColor(0xFF999999);
        col.addView(v);

        parent.addView(row, matchW());
    }

    // ---- 自定义密钥卡片 ----
    private void addCustomKeyCard(LinearLayout parent) {
        LinearLayout card = cardBg();

        Button btnImport = new Button(this);
        btnImport.setText("导入密钥库 (.jks/.p12)");
        btnImport.setTextSize(13);
        btnImport.setTextColor(0xFFFFFFFF);
        btnImport.setBackgroundColor(0xFF5C6BC0);
        btnImport.setPadding(dp(12), dp(10), dp(12), dp(10));
        btnImport.setOnClickListener(v -> pickKeyFile());
        card.addView(btnImport, matchW());

        addSpace(card, dp(6));
        Button btnClear = new Button(this);
        btnClear.setText("重置为内置密钥");
        btnClear.setTextSize(12);
        btnClear.setTextColor(0xFF888888);
        btnClear.setBackgroundColor(0xFFEEEEEE);
        btnClear.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnClear.setOnClickListener(v -> clearCustomKey());
        card.addView(btnClear, matchW());

        addSpace(card, dp(6));
        etKeyPass = new EditText(this);
        etKeyPass.setHint("密钥库密码");
        etKeyPass.setTextSize(13);
        etKeyPass.setPadding(dp(10), dp(8), dp(10), dp(8));
        etKeyPass.setBackgroundColor(0xFFF0F0F0);
        // 恢复已保存的密码
        if (customKeyStoreFile != null && !customKeyPass.isEmpty()) {
            etKeyPass.setText(customKeyPass);
        }
        etKeyPass.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                customKeyPass = s.toString();
                if (customKeyStoreFile != null && !customKeyPass.isEmpty()) {
                    loadCustomKey();
                    if (customPrivateKey != null) {
                        // 加载成功，保存密码
                        prefs.edit().putString("custom_key_pass", customKeyPass).apply();
                    }
                }
            }
        });
        card.addView(etKeyPass, matchW());

        tvKeyStatus = label("使用内置签名密钥", 11, 0xFF888888);
        tvKeyStatus.setPadding(0, dp(6), 0, 0);
        card.addView(tvKeyStatus);

        updateKeyStatus();
        parent.addView(card, matchW());
    }

    // ---- 选项卡片 ----
    private void addOptionsCard(LinearLayout parent) {
        LinearLayout card = cardBg();
        card.addView(toggleRow("Debuggable", b -> { debuggable = b; }), matchW());
        card.addView(divider());
        card.addView(toggleRow("允许降级安装", b -> { overrideVersion = b; }), matchW());
        parent.addView(card, matchW());
    }

    // ---- 签名绕过卡片 ----
    private void addSigBypassCard(LinearLayout parent) {
        LinearLayout card = cardBg();

        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(LinearLayout.VERTICAL);

        String[] opts = {
            "级别 0 - 禁用 (安全模式)",
            "级别 1 - PackageManager 绕过",
            "级别 2 - PM + I/O 层绕过",
            "级别 3 - 完整绕过 (最强)",
        };
        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i);
            rb.setText(opts[i]);
            rb.setTextSize(13);
            rb.setTextColor(0xFF444444);
            rb.setPadding(0, dp(4), 0, dp(4));
            if (i == sigBypassLevel) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((group, id) -> {
            sigBypassLevel = id;
            prefs.edit().putInt("sig_bypass_level", sigBypassLevel).apply();
        });
        card.addView(rg);

        addSpace(card, dp(4));
        TextView hint = label("级别越高兼容性越好，但请从低级别开始尝试", 10, 0xFFFF9800);
        card.addView(hint);

        parent.addView(card, matchW());
    }

    // ---- 开关行 ----
    private LinearLayout toggleRow(String name, java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(14);
        tv.setTextColor(0xFF333333);
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        final boolean[] state = {false};
        Button sw = new Button(this);
        sw.setText("关");
        sw.setTextSize(12);
        sw.setTextColor(0xFF888888);
        sw.setBackgroundColor(0xFFE0E0E0);
        sw.setPadding(dp(20), dp(6), dp(20), dp(6));
        sw.setOnClickListener(v -> {
            state[0] = !state[0];
            if (state[0]) {
                sw.setText("开");
                sw.setTextColor(0xFFFFFFFF);
                sw.setBackgroundColor(0xFF6C2DC7);
            } else {
                sw.setText("关");
                sw.setTextColor(0xFF888888);
                sw.setBackgroundColor(0xFFE0E0E0);
            }
            onChange.accept(state[0]);
        });
        row.addView(sw);

        return row;
    }

    // ==================== 选择逻辑 ====================

    private void pickTargetFile() { pickFile(REQ_TARGET_FILE); }
    private void pickModuleFile() { pickFile(REQ_MODULE_FILE); }
    private void pickTargetApp() { startActivityForResult(new Intent(this, AppPickerActivity.class), REQ_TARGET_APP); }
    private void pickModuleApp() { startActivityForResult(new Intent(this, AppPickerActivity.class), REQ_MODULE_APP); }

    private void pickFile(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/vnd.android.package-archive");
        startActivityForResult(Intent.createChooser(i, "选择 APK"), req);
    }

    private void pickKeyFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "选择密钥库"), REQ_KEY_FILE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;

        try {
            if (req == REQ_TARGET_APP || req == REQ_MODULE_APP) {
                String path = data != null ? data.getStringExtra("apk_path") : null;
                if (path == null) return;
                File f = new File(path);
                if (!f.exists()) { toast("文件不存在"); return; }
                if (req == REQ_TARGET_APP) {
                    targetFile = copyIn(f, "target.apk");
                    log("✓ " + f.getName());
                } else {
                    File m = copyIn(f, "module-" + (moduleFiles.size() + 1) + ".apk");
                    if (m != null) { moduleFiles.add(m); log("✓ " + f.getName()); }
                }
                updateAllStatus();
                return;
            }

            if (req == REQ_KEY_FILE) {
                if (data == null || data.getData() == null) return;
                File saved = saveTemp(data.getData(), "custom.keystore");
                if (saved == null) { toast("无法读取"); return; }
                customKeyStoreFile = saved;
                customKeyPass = "";
                customPrivateKey = null;
                customCert = null;
                prefs.edit().putString("custom_key_path", saved.getAbsolutePath()).remove("custom_key_pass").apply();
                // 清空密码框，提示用户输入密码
                if (etKeyPass != null) etKeyPass.setText("");
                log("✓ 密钥文件已选择: " + saved.getName());
                log("  请在下方输入密码");
                updateKeyStatus();
                return;
            }

            // 文件选择
            if (data == null || data.getData() == null) return;
            String name = (req == REQ_TARGET_FILE) ? "target.apk" : "module-" + (moduleFiles.size() + 1) + ".apk";
            File saved = saveTemp(data.getData(), name);
            if (saved == null) { toast("无法读取"); return; }
            if (req == REQ_TARGET_FILE) { targetFile = saved; log("✓ " + saved.getName()); }
            else { moduleFiles.add(saved); log("✓ " + saved.getName()); }
            updateAllStatus();
        } catch (Throwable t) {
            toast("错误: " + t.getMessage());
        }
    }

    private File copyIn(File src, String name) {
        try {
            File out = new File(getExternalFilesDir(null), name);
            FileInputStream fis = new FileInputStream(src);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192]; int n;
            while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
            fis.close(); fos.close();
            return out;
        } catch (Exception e) { return null; }
    }

    private File saveTemp(Uri uri, String name) {
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

    // ==================== 自定义密钥 ====================

    private void loadCustomKey() {
        if (customKeyStoreFile == null || !customKeyStoreFile.exists()) { customPrivateKey = null; customCert = null; return; }
        try {
            String fn = customKeyStoreFile.getName().toLowerCase();
            String type = (fn.endsWith(".p12") || fn.endsWith(".pkcs12")) ? "PKCS12" : "JKS";
            KeyStore ks = KeyStore.getInstance(type);
            FileInputStream fis = new FileInputStream(customKeyStoreFile);
            ks.load(fis, customKeyPass.toCharArray());
            fis.close();
            java.util.Enumeration<String> aliases = ks.aliases();
            String alias = null;
            while (aliases.hasMoreElements()) { String a = aliases.nextElement(); if (ks.isKeyEntry(a)) { alias = a; break; } }
            if (alias == null) throw new Exception("密钥库中没有私钥条目");
            customPrivateKey = (PrivateKey) ks.getKey(alias, customKeyPass.toCharArray());
            customCert = (X509Certificate) ks.getCertificate(alias);
            log("✓ 自定义密钥加载成功: " + alias);
        } catch (Exception e) {
            customPrivateKey = null; customCert = null;
            log("✗ 密钥加载失败: " + e.getMessage());
        }
    }

    private void clearCustomKey() {
        customKeyStoreFile = null; customKeyPass = ""; customPrivateKey = null; customCert = null;
        prefs.edit().remove("custom_key_path").remove("custom_key_pass").apply();
        if (etKeyPass != null) etKeyPass.setText("");
        log("已重置为内置密钥");
        updateKeyStatus();
    }

    private void updateKeyStatus() {
        if (tvKeyStatus == null) return;
        if (customKeyStoreFile != null && customPrivateKey != null) {
            tvKeyStatus.setText("✓ 已加载: " + customKeyStoreFile.getName());
            tvKeyStatus.setTextColor(0xFF4CAF50);
        } else if (customKeyStoreFile != null && customKeyPass.isEmpty()) {
            tvKeyStatus.setText("密钥文件已选择，请在上方输入密码");
            tvKeyStatus.setTextColor(0xFF2196F3);
        } else if (customKeyStoreFile != null) {
            tvKeyStatus.setText("⚠ 密码不正确或密钥库无效，请重试");
            tvKeyStatus.setTextColor(0xFFFF9800);
        } else {
            tvKeyStatus.setText("使用内置签名密钥");
            tvKeyStatus.setTextColor(0xFF888888);
        }
    }

    private void updateAllStatus() {
        if (tvStatus != null) {
            tvStatus.setText("目标: " + (targetFile == null ? "(未选择)" : targetFile.getName()) + "  |  模块: " + moduleFiles.size() + " 个");
        }
    }

    // ==================== 修补 ====================

    private void doPatch() {
        if (targetFile == null) { toast("请先选择目标 APK"); return; }
        new Thread(() -> {
            try {
                log("--- LSPatch v0.7.2 ---");
                log("目标: " + targetFile.getName() + "  模块: " + moduleFiles.size());
                log("绕过级别: " + sigBypassLevel + "  Debug: " + debuggable + "  降级: " + overrideVersion);
                log("密钥: " + (customPrivateKey != null ? "自定义" : "内置"));

                ApkPatchEngine engine = new ApkPatchEngine(MainActivity.this);
                File[] mods = moduleFiles.toArray(new File[0]);
                final File output = engine.patch(targetFile, mods, debuggable, overrideVersion,
                    sigBypassLevel, customPrivateKey, customCert, MainActivity.this::log);

                log("✅ 完成: " + output.getName());

                new Handler(Looper.getMainLooper()).post(() ->
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("修补完成")
                        .setMessage("文件:\n" + output.getAbsolutePath())
                        .setPositiveButton("安装", (d, w) -> installApk(output))
                        .setNegativeButton("关闭", null)
                        .show()
                );
            } catch (final Throwable e) {
                log("❌ 失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() ->
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("错误").setMessage(e.getMessage())
                        .setPositiveButton("确定", null).show()
                );
            }
        }).start();
    }

    private void installApk(File apk) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) { toast("安装失败: " + e.getMessage()); }
    }

    private void log(final String msg) {
        logs.append(msg).append("\n");
        runOnUiThread(() -> { if (tvLog != null) tvLog.setText(logs.toString()); });
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== UI 工具 ====================

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFF6C2DC7);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private TextView label(String text, int size, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        return tv;
    }

    private Button btn(String text, Runnable onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(0xFF444444);
        b.setBackgroundColor(0xFFEEEEEE);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private LinearLayout hr(LinearLayout parent, Button b1, Button b2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        p.rightMargin = dp(6);
        row.addView(b1, p);
        row.addView(b2, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private LinearLayout cardBg() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(0xFFFFFFFF);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFFEEEEEE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private void addSpace(LinearLayout parent, int h) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
    }

    private LinearLayout.LayoutParams matchW() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}