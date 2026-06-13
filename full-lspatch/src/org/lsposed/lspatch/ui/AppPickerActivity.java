package org.lsposed.lspatch.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppPickerActivity extends Activity {

    private final List<AppInfo> apps = new ArrayList<>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        // 标题栏
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(14), dp(14), dp(14), dp(10));
        header.setBackgroundColor(0xFF6C2DC7);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("选择已安装应用");
        title.setTextSize(18);
        title.setTextColor(0xFFFFFFFF);
        title.getPaint().setFakeBoldText(true);
        header.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(14);
        cancel.setTextColor(0xCCFFFFFF);
        cancel.setPadding(dp(12), 0, 0, 0);
        cancel.setOnClickListener(v -> finish());
        header.addView(cancel);

        // 加载中
        ProgressBar pb = new ProgressBar(this);
        pb.setPadding(0, dp(40), 0, 0);
        root.addView(pb);

        // 列表
        ListView lv = new ListView(this);
        lv.setBackgroundColor(0xFFFAFAFA);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        adapter = new AppAdapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo info = apps.get(position);
            new AlertDialog.Builder(this)
                .setTitle(info.name)
                .setMessage("包名: " + info.packageName + "\n路径: " + info.apkPath)
                .setPositiveButton("选择此应用", (d, w) -> {
                    Intent data = new Intent();
                    data.putExtra("apk_path", info.apkPath);
                    setResult(RESULT_OK, data);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
        });
        root.addView(lv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // 异步加载
        new Thread(() -> {
            loadApps();
            runOnUiThread(() -> {
                root.removeView(pb);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> pkgs = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : pkgs) {
            // 跳过系统应用 (可选: 只显示用户应用)
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            // 跳过自身
            if (ai.packageName.equals(getPackageName())) continue;
            String apkPath = ai.publicSourceDir != null ? ai.publicSourceDir : ai.sourceDir;
            if (apkPath == null) continue;
            File f = new File(apkPath);
            if (!f.exists()) continue;

            AppInfo info = new AppInfo();
            info.name = pm.getApplicationLabel(ai).toString();
            info.packageName = ai.packageName;
            info.apkPath = apkPath;
            info.icon = pm.getApplicationIcon(ai);
            info.apkSize = f.length();
            apps.add(info);
        }
        Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    class AppInfo {
        String name, packageName, apkPath;
        Drawable icon;
        long apkSize;
    }

    class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = createItemView();
            AppInfo info = apps.get(i);

            ImageView iv = v.findViewById(android.R.id.icon);
            iv.setImageDrawable(info.icon);

            TextView name = v.findViewById(android.R.id.text1);
            name.setText(info.name);

            TextView pkg = v.findViewById(android.R.id.text2);
            pkg.setText(info.packageName + "  |  " + formatSize(info.apkSize));

            return v;
        }

        private View createItemView() {
            LinearLayout ll = new LinearLayout(AppPickerActivity.this);
            ll.setOrientation(LinearLayout.HORIZONTAL);
            ll.setPadding(dp(14), dp(10), dp(14), dp(10));
            ll.setBackgroundColor(0xFFFFFFFF);
            ll.setGravity(android.view.Gravity.CENTER_VERTICAL);

            ImageView iv = new ImageView(AppPickerActivity.this);
            iv.setId(android.R.id.icon);
            int s = dp(42);
            ll.addView(iv, new LinearLayout.LayoutParams(s, s));

            LinearLayout col = new LinearLayout(AppPickerActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(12), 0, 0, 0);
            ll.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(AppPickerActivity.this);
            name.setId(android.R.id.text1);
            name.setTextSize(15);
            name.setTextColor(0xFF1A1A2E);
            col.addView(name);

            TextView pkg = new TextView(AppPickerActivity.this);
            pkg.setId(android.R.id.text2);
            pkg.setTextSize(11);
            pkg.setTextColor(0xFF888888);
            col.addView(pkg);

            // 底部分割线
            View divider = new View(AppPickerActivity.this);
            divider.setBackgroundColor(0xFFF0F0F0);
            LinearLayout wrap = new LinearLayout(AppPickerActivity.this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.addView(ll);
            wrap.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

            return wrap;
        }
    }

    private String formatSize(long size) {
        if (size < 1048576) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / 1048576.0);
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}