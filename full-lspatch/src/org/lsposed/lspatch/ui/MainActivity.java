package org.lsposed.lspatch.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.lsposed.lspatch.patch.ApkPatchEngine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // ── MD3 色板 ──
    private static final int C_PRIMARY     = 0xFF6750A4;
    private static final int C_ON_PRIMARY  = 0xFFFFFFFF;
    private static final int C_SURFACE     = 0xFFFFFBFE;
    private static final int C_BG          = 0xFFF7F2FA;
    private static final int C_ON_SURFACE  = 0xFF1C1B1F;
    private static final int C_ON_SURFACE_V = 0xFF49454F;
    private static final int C_OUTLINE     = 0xFF79747E;
    private static final int C_LOG_BG      = 0xFF1C1B1F;
    private static final int C_LOG_TEXT    = 0xFFC4C7C7;
    private static final int C_ACCENT_ON   = 0xFF4CAF50;
    private static final int C_ACCENT_WARN = 0xFFFF9800;
    private static final int C_ERROR        = 0xFFB3261E;
    private static final int C_SCONTAINER  = 0xFFE8DEF8;

    private static final int REQ_TARGET_FILE = 1001;
    private static final int REQ_MODULE_FILE = 1002;
    private static final int REQ_TARGET_APP  = 2001;
    private static final int REQ_MODULE_APP  = 2002;

    // 绕过级别定义: {level, title, description, unused}
    private static final Object[][] SIG_LEVELS = {
        {0,  "禁用",           "不绕过任何签名校验\n适用于已正确签名的 APK"},
        {1,  "基础 Hook",      "Hook ActivityThread.sPackageManager\n替换 PackageInfo 签名数组"},
        {2,  "增强绕过",       "Level 1 + PackageInfo.CREATOR 代理\n+ 拦截 Parcel 反序列化签名"},
        {3,  "完整绕过(推荐)", "Level 2 + Native openat Hook\n+ LoadedApk 字段修改 + 缓存清理\n覆盖 PM/IO/Parcel/Native 全层级"},
    };

    private File targetFile;
    private final List<File> moduleFiles = new ArrayList<>();
    private TextView tvStatus, tvLog;
    private final StringBuilder logs = new StringBuilder();
    private SharedPreferences prefs;
    private int sigBypassLevel = 3;
    private boolean debuggable = false;
    private boolean overrideVersion = false;
    // 记录选中卡片引用用于 UI 更新
    private final List<LinearLayout> sigCards = new ArrayList<>();
    private final List<TextView> sigCardTitles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            prefs = getSharedPreferences("lspatch_prefs", MODE_PRIVATE);
            sigBypassLevel = prefs.getInt("sig_bypass_level", 3);
            buildAllUI();
            updateAllStatus();
            log("LSPatch v0.8 | 输出未签名 APK，自行签名");
            requestStoragePermission();
        } catch (Throwable t) {
            TextView err = new TextView(this);
            err.setText("LSPatch 启动失败:\n" + t.getClass().getName() + "\n" + t.getMessage());
            err.setTextSize(14);
            err.setPadding(40, 40, 40, 40);
            setContentView(err);
        }
    }

    // ==================== 构造 UI ====================

    private void buildAllUI() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(C_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(40));
        sv.addView(root);
        setContentView(sv);

        addHeader(root);
        addSpace(root, dp(20));

        // 状态条
        tvStatus = new TextView(this);
        tvStatus.setTextSize(13);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatus.setTextColor(C_ON_SURFACE_V);
        tvStatus.setText("目标: 未选择  |  模块: 0 个");
        tvStatus.setPadding(dp(16), dp(12), dp(16), dp(12));
        tvStatus.setBackground(roundedBg(C_SURFACE, dp(16)));
        root.addView(tvStatus, matchW());
        addSpace(root, dp(16));

        // 目标 APK 卡片
        root.addView(md3Card("目标 APK", "选择要修补的应用安装包", C_PRIMARY,
            new String[]{"从文件选择", "从已安装应用"},
            new Runnable[]{this::pickTargetFile, this::pickTargetApp}));
        addSpace(root, dp(12));

        // 模块卡片
        root.addView(md3Card("Xposed 模块", "可选，注入 LSPosed 模块 APK", 0xFF625B71,
            new String[]{"从文件选择", "从已安装应用"},
            new Runnable[]{this::pickModuleFile, this::pickModuleApp}));
        addSpace(root, dp(12));

        // 签名绕过卡片 — 修复版
        addSigBypassCards(root);
        addSpace(root, dp(12));

        // 选项卡片
        addOptionsCard(root);
        addSpace(root, dp(24));

        // 修补按钮
        Button btnPatch = new Button(this);
        btnPatch.setText("开始修补 (输出未签名 APK)");
        btnPatch.setTextSize(16);
        btnPatch.setTypeface(Typeface.DEFAULT_BOLD);
        btnPatch.setTextColor(C_ON_PRIMARY);
        btnPatch.setBackground(roundedBg(C_PRIMARY, dp(28)));
        btnPatch.setPadding(0, dp(16), 0, dp(16));
        btnPatch.setAllCaps(false);
        btnPatch.setOnClickListener(v -> doPatch());
        root.addView(btnPatch, matchW());
        addSpace(root, dp(20));

        // 日志
        TextView logTitle = new TextView(this);
        logTitle.setText("日志");
        logTitle.setTextSize(13);
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        logTitle.setTextColor(C_ON_SURFACE_V);
        logTitle.setPadding(0, 0, 0, dp(8));
        root.addView(logTitle);

        tvLog = new TextView(this);
        tvLog.setTextSize(11);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setBackground(roundedBg(C_LOG_BG, dp(12)));
        tvLog.setTextColor(C_LOG_TEXT);
        tvLog.setPadding(dp(14), dp(14), dp(14), dp(14));
        tvLog.setMinHeight(dp(180));
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setLineSpacing(dp(2), 1f);
        root.addView(tvLog, matchW());
    }

    // ── 标题 ──
    private void addHeader(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = new TextView(this);
        logo.setText("LS");
        logo.setTextSize(22);
        logo.setTextColor(C_ON_PRIMARY);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        int s = dp(52);
        logo.setBackground(roundedBg(C_PRIMARY, dp(16)));
        row.addView(logo, new LinearLayout.LayoutParams(s, s));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, 0, 0);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView t = new TextView(this);
        t.setText("LSPatch");
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(C_ON_SURFACE);
        col.addView(t);
        TextView v = new TextView(this);
        v.setText("v0.8  |  非 Root Xposed 框架");
        v.setTextSize(12);
        v.setTextColor(C_ON_SURFACE_V);
        col.addView(v);
        parent.addView(row, matchW());
    }

    // ── MD3 卡片 (两个按钮) ──
    private LinearLayout md3Card(String title, String subtitle, int accentColor,
                                  String[] btnLabels, Runnable[] actions) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBg(C_SURFACE, dp(16)));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(C_ON_SURFACE);
        card.addView(tv);
        TextView st = new TextView(this);
        st.setText(subtitle);
        st.setTextSize(11);
        st.setTextColor(C_ON_SURFACE_V);
        st.setPadding(0, dp(2), 0, dp(12));
        card.addView(st);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < btnLabels.length; i++) {
            Button b = new Button(this);
            b.setText(btnLabels[i]);
            b.setTextSize(13);
            b.setAllCaps(false);
            b.setTextColor(accentColor);
            b.setBackground(roundedBg(accentColor & 0x00FFFFFF | 0x14000000, dp(20)));
            b.setPadding(dp(16), dp(10), dp(16), dp(10));
            final int idx = i;
            b.setOnClickListener(v -> actions[idx].run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i == 0) lp.rightMargin = dp(8);
            row.addView(b, lp);
        }
        card.addView(row, matchW());
        return card;
    }

    // ── 签名绕过卡片 (点击选择，单个互斥) ──
    private void addSigBypassCards(LinearLayout parent) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(roundedBg(C_SURFACE, dp(16)));
        outer.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView tv = new TextView(this);
        tv.setText("签名校验绕过");
        tv.setTextSize(14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(C_ON_SURFACE);
        outer.addView(tv);

        TextView st = new TextView(this);
        st.setText("选择绕过级别 — 注入 config.json 控制 native + Java 层 Hook");
        st.setTextSize(11);
        st.setTextColor(C_ON_SURFACE_V);
        st.setPadding(0, dp(2), 0, dp(14));
        outer.addView(st);

        sigCards.clear();
        sigCardTitles.clear();

        for (int i = 0; i < SIG_LEVELS.length; i++) {
            final int idx = (int) SIG_LEVELS[i][0];
            String title = "Lv" + idx + "  " + (String) SIG_LEVELS[i][1];
            String desc = (String) SIG_LEVELS[i][2];

            LinearLayout entry = new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            entry.setPadding(dp(12), dp(10), dp(12), dp(10));
            entry.setBackground(roundedBg(C_BG, dp(12)));

            TextView et = new TextView(this);
            et.setText(title);
            et.setTextSize(13);
            et.setTypeface(Typeface.DEFAULT_BOLD);
            et.setTextColor(C_ON_SURFACE);
            entry.addView(et);

            TextView ed = new TextView(this);
            ed.setText(desc);
            ed.setTextSize(10);
            ed.setTextColor(C_ON_SURFACE_V);
            ed.setPadding(0, dp(4), 0, 0);
            entry.addView(ed);

            sigCards.add(entry);
            sigCardTitles.add(et);

            entry.setOnClickListener(v -> selectSigLevel(idx));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            outer.addView(entry, lp);
        }

        // 选中当前级别
        selectSigLevelUI(sigBypassLevel);

        addSpace(outer, dp(8));

        TextView hint = new TextView(this);
        hint.setText("config.json 注入 originalSignature (原签名 Base64) + sigBypassLevel");
        hint.setTextSize(10);
        hint.setTextColor(C_ACCENT_WARN);
        outer.addView(hint);

        parent.addView(outer, matchW());
    }

    private void selectSigLevel(int level) {
        sigBypassLevel = level;
        prefs.edit().putInt("sig_bypass_level", level).apply();
        selectSigLevelUI(level);
    }

    private void selectSigLevelUI(int level) {
        for (int i = 0; i < sigCards.size(); i++) {
            int cardLevel = (int) SIG_LEVELS[i][0];
            boolean sel = (cardLevel == level);
            sigCards.get(i).setBackground(roundedBg(sel ? C_SCONTAINER : C_BG, dp(12)));
            sigCardTitles.get(i).setTextColor(sel ? C_PRIMARY : C_ON_SURFACE);
        }
    }

    // ── 选项卡片 ──
    private void addOptionsCard(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBg(C_SURFACE, dp(16)));
        card.setPadding(0, dp(4), 0, dp(4));
        card.addView(toggleRow("Debuggable 模式", "允许调试修补后的应用", b -> debuggable = b));
        card.addView(md3Divider());
        card.addView(toggleRow("允许降级安装", "覆盖安装更低版本号", b -> overrideVersion = b));
        parent.addView(card, matchW());
    }

    private LinearLayout toggleRow(String name, String desc, java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(14);
        tv.setTextColor(C_ON_SURFACE);
        col.addView(tv);
        TextView dv = new TextView(this);
        dv.setText(desc);
        dv.setTextSize(10);
        dv.setTextColor(C_ON_SURFACE_V);
        col.addView(dv);
        final boolean[] state = {false};
        Button sw = new Button(this);
        sw.setText("关");
        sw.setTextSize(12);
        sw.setAllCaps(false);
        sw.setTextColor(C_ON_SURFACE_V);
        sw.setBackground(roundedBg(C_BG, dp(20)));
        sw.setPadding(dp(20), dp(8), dp(20), dp(8));
        sw.setOnClickListener(v -> {
            state[0] = !state[0];
            if (state[0]) { sw.setText("开"); sw.setTextColor(C_ON_PRIMARY); sw.setBackground(roundedBg(C_PRIMARY, dp(20))); }
            else { sw.setText("关"); sw.setTextColor(C_ON_SURFACE_V); sw.setBackground(roundedBg(C_BG, dp(20))); }
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
                if (req == REQ_TARGET_APP) { targetFile = copyIn(f, "target.apk"); log("✓ " + f.getName()); }
                else { File m = copyIn(f, "module-" + (moduleFiles.size()+1) + ".apk"); if (m != null) { moduleFiles.add(m); log("✓ " + f.getName()); } }
                updateAllStatus();
                return;
            }
            if (data == null || data.getData() == null) return;
            String name = (req == REQ_TARGET_FILE) ? "target.apk" : "module-" + (moduleFiles.size()+1) + ".apk";
            File saved = saveTemp(data.getData(), name);
            if (saved == null) { toast("无法读取"); return; }
            if (req == REQ_TARGET_FILE) { targetFile = saved; log("✓ " + saved.getName()); }
            else { moduleFiles.add(saved); log("✓ " + saved.getName()); }
            updateAllStatus();
        } catch (Throwable t) { toast("错误: " + t.getMessage()); }
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

    private void updateAllStatus() {
        if (tvStatus != null) tvStatus.setText("目标: " + (targetFile==null?"(未选择)":targetFile.getName()) + "  |  模块: " + moduleFiles.size() + " 个");
    }

    // ==================== 修补 ====================

    private void doPatch() {
        if (targetFile == null) { toast("请先选择目标 APK"); return; }
        new Thread(() -> {
            try {
                log("── LSPatch v0.8 ──");
                log("目标: " + targetFile.getName());
                log("模块: " + moduleFiles.size() + " 个");
                log("绕过级别: Lv" + sigBypassLevel);
                log("  技术栈: " + getBypassDesc(sigBypassLevel));
                log("签名策略: 输出未签名 APK");

                ApkPatchEngine engine = new ApkPatchEngine(MainActivity.this);
                File[] mods = moduleFiles.toArray(new File[0]);
                final File output = engine.patch(targetFile, mods, debuggable, overrideVersion,
                    sigBypassLevel, MainActivity.this::log);

                log("输出: " + output.getAbsolutePath());
                new Handler(Looper.getMainLooper()).post(() ->
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("修补完成")
                        .setMessage("输出未签名 APK:\n" + output.getAbsolutePath() + "\n\n请用 apksigner / MT 管理器签名后安装")
                        .setPositiveButton("分享", (d, w) -> shareApk(output))
                        .setNegativeButton("关闭", null)
                        .show());
            } catch (final Throwable e) {
                log("✗ 失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() ->
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("错误").setMessage(e.getMessage())
                        .setPositiveButton("确定", null).show());
            }
        }).start();
    }

    private String getBypassDesc(int level) {
        for (Object[] lv : SIG_LEVELS) if ((int)lv[0] == level) return (String) lv[2];
        return "未知";
    }

    private void shareApk(File apk) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/vnd.android.package-archive");
            i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(apk));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "分享 APK"));
        } catch (Exception e) { toast("分享失败: " + e.getMessage()); }
    }

    private void log(final String msg) {
        logs.append(msg).append("\n");
        runOnUiThread(() -> { if (tvLog != null) tvLog.setText(logs.toString()); });
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                catch (Exception ignored) {}
            }
        }
    }

    // ==================== MD3 工具 ====================

    private ShapeDrawable roundedBg(int color, float radiusDp) {
        float r = dp(radiusDp);
        float[] radii = {r,r,r,r,r,r,r,r};
        ShapeDrawable sd = new ShapeDrawable(new RoundRectShape(radii, null, null));
        sd.getPaint().setColor(color);
        return sd;
    }

    private View md3Divider() {
        View v = new View(this);
        v.setBackgroundColor(C_OUTLINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = dp(16); lp.rightMargin = dp(16);
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