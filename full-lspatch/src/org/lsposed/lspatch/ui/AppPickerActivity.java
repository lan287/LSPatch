package org.lsposed.lspatch.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppPickerActivity extends Activity {

    private final List<AppInfo> apps = new ArrayList<>();
    private ListView listView;
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUI();
            loadAppsAsync();
        } catch (Throwable t) {
            TextView err = new TextView(this);
            err.setText("错误: " + t.getMessage());
            err.setTextColor(0xFFFF0000);
            err.setPadding(40, 40, 40, 40);
            setContentView(err);
        }
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setBackgroundColor(0xFF6C2DC7);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("选择已安装应用");
        title.setTextSize(17);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(14);
        cancel.setTextColor(0xBBFFFFFF);
        cancel.setPadding(dp(16), 0, 0, 0);
        cancel.setOnClickListener(v -> finish());
        header.addView(cancel);

        // List
        listView = new ListView(this);
        listView.setBackgroundColor(0xFFFAFAFA);
        listView.setDividerHeight(dp(1));
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xFFF0F0F0));
        adapter = new AppAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo info = apps.get(position);
            new AlertDialog.Builder(this)
                .setTitle(info.name)
                .setMessage("包名: " + info.packageName + "\n路径: " + info.apkPath)
                .setPositiveButton("选择", (d, w) -> {
                    Intent data = new Intent();
                    data.putExtra("apk_path", info.apkPath);
                    setResult(RESULT_OK, data);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
        });
        root.addView(listView, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void loadAppsAsync() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> pkgs = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : pkgs) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                if (ai.packageName.equals(getPackageName())) continue;
                String apkPath = ai.publicSourceDir != null ? ai.publicSourceDir : ai.sourceDir;
                if (apkPath == null || !new File(apkPath).exists()) continue;
                AppInfo info = new AppInfo();
                info.name = pm.getApplicationLabel(ai).toString();
                info.packageName = ai.packageName;
                info.apkPath = apkPath;
                info.icon = pm.getApplicationIcon(ai);
                info.apkSize = new File(apkPath).length();
                apps.add(info);
            }
            Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();
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
            if (v == null) v = createItem();
            AppInfo info = apps.get(i);
            ImageView iv = (ImageView) v.findViewWithTag("icon");
            iv.setImageDrawable(info.icon);
            TextView name = (TextView) v.findViewWithTag("name");
            name.setText(info.name);
            TextView pkg = (TextView) v.findViewWithTag("pkg");
            pkg.setText(info.packageName + "  |  " + formatSize(info.apkSize));
            return v;
        }

        private View createItem() {
            LinearLayout ll = new LinearLayout(AppPickerActivity.this);
            ll.setOrientation(LinearLayout.HORIZONTAL);
            ll.setPadding(dp(14), dp(10), dp(14), dp(10));
            ll.setBackgroundColor(0xFFFFFFFF);
            ll.setGravity(Gravity.CENTER_VERTICAL);

            ImageView iv = new ImageView(AppPickerActivity.this);
            iv.setTag("icon");
            int s = dp(44);
            ll.addView(iv, lp(s, s));

            LinearLayout col = new LinearLayout(AppPickerActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(12), 0, 0, 0);
            ll.addView(col, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView name = new TextView(AppPickerActivity.this);
            name.setTag("name");
            name.setTextSize(15);
            name.setTextColor(0xFF1A1A2E);
            col.addView(name);

            TextView pkg = new TextView(AppPickerActivity.this);
            pkg.setTag("pkg");
            pkg.setTextSize(11);
            pkg.setTextColor(0xFF999999);
            col.addView(pkg);

            return ll;
        }
    }

    private String formatSize(long size) {
        if (size < 1048576) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / 1048576.0);
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }
    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }
}