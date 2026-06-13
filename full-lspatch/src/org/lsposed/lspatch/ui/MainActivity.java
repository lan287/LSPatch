package org.lsposed.lspatch.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.LinearLayout.LayoutParams;

import org.lsposed.lspatch.patch.ApkPatchEngine;
import org.lsposed.lspatch.patch.ApkSigner;

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

    // 自定义密钥
    private File customKeyStoreFile;
    private String customKeyPass = "";
    private PrivateKey customPrivateKey;
    private X509Certificate customCert;

    // 签名绕过级别
    private int sigBypassLevel = 0;

    // 选项
    private boolean debuggable = false;
    private boolean overrideVersion = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("lspatch_prefs", MODE_PRIVATE);
        loadSavedPrefs();
        buildUI();
        updateAllStatus();
        log("LSPatch v0.7 就绪");
        requestStoragePermission();
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

    // ==================== UI 构建 ====================

    private void buildUI() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF0F2F5);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(32));
        sv.addView(root);
        setContentView(sv);

        addHeader(root);
        addStatusCard(root);
        addTargetSection(root);
        addModuleSection(root);
        addKeySection(root);
        addOptionsSection(root);
        addSigBypassSection(root);
        addPatchButton(root);
        addLogSection(root);
    }

    // ---- 头部 ----
    private void addHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(14));
        root.addView(header, fillWidth());

        // Logo 圆圈
        TextView logo = new TextView(this);
        logo.setText("LS");
        logo.setTextSize(18);
        logo.setTextColor(0xFFFFFFFF);
        logo.setGravity(Gravity.CENTER);
        int ls = dp(44);
        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setShape(GradientDrawable.OVAL);
        logoBg.setColors(new int[]{0xFF6C2DC7, 0xFF9C27B0});
        logo.setBackground(logoBg);
        header.addView(logo, new LayoutParams(ls, ls));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(12), 0, 0, 0);
        header.addView(titleCol, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView title = new TextView(this);
        title.setText("LSPatch");
        title.setTextSize(22);
        title.setTextColor(0xFF1A1A2E);
        title.getPaint().setFakeBoldText(true);
        titleCol.addView(title);

        TextView ver = new TextView(this);
        ver.setText("v0.7  |  非 Root Xposed 框架");
        ver.setTextSize(11);
        ver.setTextColor(0xFF888888);
        titleCol.addView(ver);
    }

    // ---- 状态卡片 ----
    private void addStatusCard(LinearLayout root) {
        tvStatus = new TextView(this);
        tvStatus.setTextSize(12);
        tvStatus.setTextColor(0xFF444444);
        tvStatus.setBackgroundColor(0xFFFFFFFF);
        tvStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        cardStyle(tvStatus);
        root.addView(tvStatus, fillWidth());
    }

    // ---- 目标 APK 区域 ----
    private void addTargetSection(LinearLayout root) {
        root.addView(sectionLabel("目标 APK"));

        LinearLayout row = hRow();
        Button btnFile = accentBtn("📁 从文件选择");
        btnFile.setOnClickListener(v -> pickTargetFile());
        row.addView(btnFile, btnWeight());

        View gap = new View(this);
        row.addView(gap, new LayoutParams(dp(8), 0));

        Button btnApp = accentBtn("📱 从已安装应用");
        btnApp.setOnClickListener(v -> pickFromApps(REQ_TARGET_APP));
        row.addView(btnApp, btnWeight());

        root.addView(row, fillWidth());
    }

    // ---- 模块 APK 区域 ----
    private void addModuleSection(LinearLayout root) {
        marginTop(root, dp(14));
        root.addView(sectionLabel("Xposed 模块 (可选, 可多选)"));

        LinearLayout row = hRow();
        Button btnMFile = accentBtn("📁 从文件选择");
        btnMFile.setOnClickListener(v -> pickModuleFile());
        row.addView(btnMFile, btnWeight());

        View gap = new View(this);
        row.addView(gap, new LayoutParams(dp(8), 0));

        Button btnMApp = accentBtn("📱 从已安装应用");
        btnMApp.setOnClickListener(v -> pickFromApps(REQ_MODULE_APP));
        row.addView(btnMApp, btnWeight());

        root.addView(row, fillWidth());
    }

    // ---- 自定义密钥区域 ----
    private void addKeySection(LinearLayout root) {
        marginTop(root, dp(14));
        root.addView(sectionLabel("签名密钥 (可选, 默认使用内置密钥)"));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        cardStyle(card);
        root.addView(card, fillWidth());

        LinearLayout r1 = hRow();
        Button btnImport = new Button(this);
        btnImport.setText("🔑 导入自定义密钥库");
        btnImport.setTextSize(13);
        btnImport.setTextColor(0xFFFFFFFF);
        GradientDrawable kg = new GradientDrawable();
        kg.setCornerRadius(dp(6));
        kg.setColor(0xFF5C6BC0);
        btnImport.setBackground(kg);
        btnImport.setPadding(0, dp(10), 0, dp(10));
        btnImport.setOnClickListener(v -> pickKeyFile());
        r1.addView(btnImport, btnWeight());

        Button btnClear = new Button(this);
        btnClear.setText("重置");
        btnClear.setTextSize(13);
        btnClear.setTextColor(0xFFE53935);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setCornerRadius(dp(6));
        cbg.setStroke(dp(1), 0xFFE53935);
        cbg.setColor(0x00FFFFFF);
        btnClear.setBackground(cbg);
        btnClear.setPadding(dp(16), dp(10), dp(16), dp(10));
        btnClear.setOnClickListener(v -> { clearCustomKey(); });
        r1.addView(btnClear, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        r1.setPadding(0, 0, 0, dp(8));
        card.addView(r1, fillWidth());

        final EditText etPass = new EditText(this);
        etPass.setHint("密钥库密码 (如需要)");
        etPass.setTextSize(13);
        etPass.setPadding(dp(10), dp(8), dp(10), dp(8));
        etPass.setBackgroundColor(0xFFF5F5F5);
        etPass.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                customKeyPass = s.toString();
                if (customKeyStoreFile != null) loadCustomKey();
            }
        });
        card.addView(etPass, fillWidth());

        tvKeyStatus = new TextView(this);
        tvKeyStatus.setTextSize(11);
        tvKeyStatus.setTextColor(0xFF888888);
        tvKeyStatus.setPadding(0, dp(6), 0, 0);
        card.addView(tvKeyStatus);

        updateKeyStatus();
    }

    // ---- 选项区域 ----
    private void addOptionsSection(LinearLayout root) {
        marginTop(root, dp(14));
        root.addView(sectionLabel("修补选项"));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        cardStyle(card);
        root.addView(card, fillWidth());

        // debuggable
        LinearLayout r1 = hRow();
        final TextView tvDbg = new TextView(this);
        tvDbg.setText("Debuggable");
        tvDbg.setTextSize(14);
        tvDbg.setTextColor(0xFF333333);
        r1.addView(tvDbg, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        final Button swDbg = new Button(this);
        swDbg.setText("关");
        swDbg.setTextSize(12);
        swDbg.setPadding(dp(18), dp(6), dp(18), dp(6));
        updateToggleBtn(swDbg, debuggable);
        swDbg.setOnClickListener(v -> {
            debuggable = !debuggable;
            updateToggleBtn(swDbg, debuggable);
        });
        r1.addView(swDbg);
        card.addView(r1, fillWidth());

        View div1 = new View(this);
        div1.setBackgroundColor(0xFFEEEEEE);
        card.addView(div1, new LayoutParams(LayoutParams.MATCH_PARENT, dp(1)));
        marginV(div1, dp(10), dp(10));

        // override version
        LinearLayout r2 = hRow();
        final TextView tvOv = new TextView(this);
        tvOv.setText("允许降级安装");
        tvOv.setTextSize(14);
        tvOv.setTextColor(0xFF333333);
        r2.addView(tvOv, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        final Button swOv = new Button(this);
        swOv.setText("关");
        swOv.setTextSize(12);
        swOv.setPadding(dp(18), dp(6), dp(18), dp(6));
        updateToggleBtn(swOv, overrideVersion);
        swOv.setOnClickListener(v -> {
            overrideVersion = !overrideVersion;
            updateToggleBtn(swOv, overrideVersion);
        });
        r2.addView(swOv);
        card.addView(r2, fillWidth());
    }

    // ---- 签名绕过级别 ----
    private void addSigBypassSection(LinearLayout root) {
        marginTop(root, dp(14));
        root.addView(sectionLabel("签名校验绕过"));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        cardStyle(card);
        root.addView(card, fillWidth());

        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(LinearLayout.VERTICAL);

        String[][] options = {
            {"0", "禁用 - 不绕过签名校验 (安全模式)"},
            {"1", "级别 1 (PM) - 绕过 PackageManager 签名检查"},
            {"2", "级别 2 (IO) - 绕过 PM + I/O 层签名检查"},
            {"3", "级别 3 (DIR) - 绕过 PM/I/O/Direct 层签名检查 (最强)"},
        };

        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i);
            rb.setText(options[i][1]);
            rb.setTextSize(13);
            rb.setTextColor(0xFF444444);
            rb.setPadding(0, dp(4), 0, dp(4));
            if (i == sigBypassLevel) rb.setChecked(true);
            rg.addView(rb);
        }

        rg.setOnCheckedChangeListener((group, checkedId) -> {
            sigBypassLevel = checkedId;
            prefs.edit().putInt("sig_bypass_level", sigBypassLevel).apply();
        });

        card.addView(rg);

        // 颜色提示
        TextView hint = new TextView(this);
        hint.setText("⚠ 级别越高兼容性越好但安全性越低，建议从级别1开始尝试");
        hint.setTextSize(10);
        hint.setTextColor(0xFFFF9800);
        hint.setPadding(0, dp(8), 0, 0);
        card.addView(hint);
    }

    // ---- 修补按钮 ----
    private void addPatchButton(LinearLayout root) {
        marginTop(root, dp(18));
        Button btnPatch = new Button(this);
        btnPatch.setText("⚡  开始修补并签名");
        btnPatch.setTextSize(18);
        btnPatch.setTextColor(0xFFFFFFFF);
        btnPatch.getPaint().setFakeBoldText(true);
        btnPatch.setPadding(0, dp(16), 0, dp(16));

        GradientDrawable pbg = new GradientDrawable();
        pbg.setCornerRadius(dp(10));
        pbg.setColors(new int[]{0xFF6C2DC7, 0xFFAB47BC});
        btnPatch.setBackground(pbg);

        btnPatch.setOnClickListener(v -> doPatch());
        root.addView(btnPatch, fillWidth());
    }

    // ---- 日志 ----
    private void addLogSection(LinearLayout root) {
        marginTop(root, dp(14));
        root.addView(sectionLabel("日志输出"));

        tvLog = new TextView(this);
        tvLog.setTextSize(11);
        tvLog.setTextColor(0xFF1A1A2E);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setBackgroundColor(0xFF1E1E2E);
        tvLog.setTextColor(0xFFCCCCCC);
        tvLog.setPadding(dp(10), dp(10), dp(10), dp(10));
        tvLog.setMinHeight(dp(160));
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        cardStyle(tvLog);
        root.addView(tvLog, fillWidth());
    }

    // ==================== UI 辅助 ====================

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF6C2DC7);
        tv.getPaint().setFakeBoldText(true);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private LinearLayout hRow() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        return ll;
    }

    private Button accentBtn(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setTextColor(0xFF444444);
        btn.setPadding(0, dp(10), 0, dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(0xFFF3E5F5);
        bg.setStroke(dp(1), 0xFFCE93D8);
        btn.setBackground(bg);

        return btn;
    }

    private LayoutParams btnWeight() {
        return new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
    }

    private void cardStyle(View v) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFFFFFFFF);
        bg.setStroke(dp(1), 0xFFE0E0E0);
        v.setBackground(bg);
    }

    private void updateToggleBtn(Button btn, boolean on) {
        if (on) {
            btn.setText("开");
            btn.setTextColor(0xFFFFFFFF);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(0xFF6C2DC7);
            btn.setBackground(bg);
        } else {
            btn.setText("关");
            btn.setTextColor(0xFF888888);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(0xFFE0E0E0);
            btn.setBackground(bg);
        }
    }

    private LayoutParams fillWidth() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    private void marginTop(View v, int dp) {
        LayoutParams lp = (LayoutParams) v.getLayoutParams();
        if (lp != null) lp.topMargin = dp;
    }

    private void marginV(View v, int top, int bottom) {
        LayoutParams lp = (LayoutParams) v.getLayoutParams();
        if (lp != null) { lp.topMargin = top; lp.bottomMargin = bottom; }
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // ==================== 选择逻辑 ====================

    private void pickTargetFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        startActivityForResult(Intent.createChooser(intent, "选择目标 APK"), REQ_TARGET_FILE);
    }

    private void pickModuleFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        startActivityForResult(Intent.createChooser(intent, "选择模块 APK"), REQ_MODULE_FILE);
    }

    private void pickFromApps(int reqCode) {
        Intent intent = new Intent(this, AppPickerActivity.class);
        startActivityForResult(intent, reqCode);
    }

    private void pickKeyFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "选择密钥库文件 (.jks/.p12)"), REQ_KEY_FILE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;

        if (req == REQ_TARGET_APP || req == REQ_MODULE_APP) {
            // 从 AppPicker 返回，获取 APK 路径
            String apkPath = data != null ? data.getStringExtra("apk_path") : null;
            if (apkPath == null) return;
            File f = new File(apkPath);
            if (!f.exists()) { toast("APK 文件不存在"); return; }
            if (req == REQ_TARGET_APP) {
                targetFile = copyToTemp(f, "target.apk");
                log("✓ 目标: " + f.getName());
            } else {
                File m = copyToTemp(f, "module-" + (moduleFiles.size() + 1) + ".apk");
                if (m != null) { moduleFiles.add(m); log("✓ 模块: " + f.getName()); }
            }
            updateAllStatus();
            return;
        }

        if (req == REQ_KEY_FILE) {
            if (data == null || data.getData() == null) return;
            File saved = saveAsTemp(data.getData(), "custom.keystore");
            if (saved == null) { toast("无法读取密钥文件"); return; }
            customKeyStoreFile = saved;
            prefs.edit().putString("custom_key_path", saved.getAbsolutePath()).apply();
            loadCustomKey();
            updateKeyStatus();
            return;
        }

        // 文件选择
        if (data == null || data.getData() == null) return;
        String name = req == REQ_TARGET_FILE ? "target.apk"
                      : "module-" + (moduleFiles.size() + 1) + ".apk";
        File saved = saveAsTemp(data.getData(), name);
        if (saved == null) { toast("无法读取文件"); return; }
        if (req == REQ_TARGET_FILE) { targetFile = saved; log("✓ 目标: " + saved.getName()); }
        else { moduleFiles.add(saved); log("✓ 模块: " + saved.getName()); }
        updateAllStatus();
    }

    private File copyToTemp(File src, String name) {
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

    // ==================== 自定义密钥 ====================

    private void loadCustomKey() {
        if (customKeyStoreFile == null || !customKeyStoreFile.exists()) {
            customPrivateKey = null;
            customCert = null;
            return;
        }
        try {
            KeyStore ks = KeyStore.getInstance(
                customKeyStoreFile.getName().toLowerCase().endsWith(".p12") ||
                customKeyStoreFile.getName().toLowerCase().endsWith(".pkcs12")
                ? "PKCS12" : "JKS");
            FileInputStream fis = new FileInputStream(customKeyStoreFile);
            ks.load(fis, customKeyPass.toCharArray());
            fis.close();

            // 尝试找到私钥和证书
            java.util.Enumeration<String> aliases = ks.aliases();
            String alias = null;
            while (aliases.hasMoreElements()) {
                String a = aliases.nextElement();
                if (ks.isKeyEntry(a)) { alias = a; break; }
            }
            if (alias == null) throw new Exception("密钥库中没有私钥条目");

            customPrivateKey = (PrivateKey) ks.getKey(alias, customKeyPass.toCharArray());
            customCert = (X509Certificate) ks.getCertificate(alias);
            log("✓ 自定义密钥加载成功: " + alias);
        } catch (Exception e) {
            customPrivateKey = null;
            customCert = null;
            log("✗ 密钥加载失败: " + e.getMessage());
        }
    }

    private void clearCustomKey() {
        customKeyStoreFile = null;
        customKeyPass = "";
        customPrivateKey = null;
        customCert = null;
        prefs.edit().remove("custom_key_path").remove("custom_key_pass").apply();
        log("已重置为内置密钥");
        updateKeyStatus();
    }

    private void updateKeyStatus() {
        if (customKeyStoreFile != null && customPrivateKey != null) {
            tvKeyStatus.setText("✓ 已加载: " + customKeyStoreFile.getName());
            tvKeyStatus.setTextColor(0xFF4CAF50);
        } else if (customKeyStoreFile != null) {
            tvKeyStatus.setText("⚠ 密钥文件已选择但加载失败, 请检查密码");
            tvKeyStatus.setTextColor(0xFFFF9800);
        } else {
            tvKeyStatus.setText("使用内置签名密钥");
            tvKeyStatus.setTextColor(0xFF888888);
        }
    }

    private void updateAllStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("目标: ").append(targetFile == null ? "(未选择)" : targetFile.getName())
          .append("  |  模块: ").append(moduleFiles.size()).append(" 个");
        tvStatus.setText(sb.toString());
    }

    // ==================== 修补逻辑 ====================

    private void doPatch() {
        if (targetFile == null) { toast("请先选择目标 APK"); return; }
        new Thread(() -> {
            try {
                log("════════════════════════");
                log("LSPatch v0.7 修补开始");
                log("目标: " + targetFile.getName());
                log("模块: " + moduleFiles.size() + " 个");
                log("签名绕过级别: " + sigBypassLevel);
                log("Debuggable: " + debuggable);
                log("降级安装: " + overrideVersion);
                log("密钥: " + (customPrivateKey != null ? "自定义" : "内置"));
                log("════════════════════════");

                ApkPatchEngine engine = new ApkPatchEngine(MainActivity.this);
                File[] mods = moduleFiles.toArray(new File[0]);

                PrivateKey signKey = customPrivateKey;
                X509Certificate signCert = customCert;

                final File output = engine.patch(targetFile, mods, debuggable, overrideVersion,
                    sigBypassLevel, signKey, signCert, msg -> MainActivity.this.log(msg));

                log("✅ 完成: " + output.getName() + " (" + (output.length() / 1048576L) + " MB)");

                new Handler(Looper.getMainLooper()).post(() -> {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("修补完成")
                        .setMessage("输出文件:\n" + output.getAbsolutePath() + "\n\n点击确定立即安装。")
                        .setPositiveButton("安装", (d, w) -> installApk(output))
                        .setNegativeButton("关闭", null)
                        .show();
                });
            } catch (final Throwable e) {
                log("❌ 失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("修补失败")
                        .setMessage(e.getMessage())
                        .setPositiveButton("确定", null)
                        .show();
                });
            }
        }).start();
    }

    private void installApk(File apk) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

    private void log(final String msg) {
        logs.append(msg).append("\n");
        runOnUiThread(() -> tvLog.setText(logs.toString()));
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

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}