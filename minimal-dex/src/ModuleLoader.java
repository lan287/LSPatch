package org.lsposed.lspatch.util;
import android.content.pm.ApplicationInfo;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
public class ModuleLoader {
    private static final String TAG = "LSPatch-Module";
    public static class LoadedModule {
        public String packageName;
        public String apkPath;
        public ClassLoader classLoader;
        public String moduleClass;
    }
    public static List<LoadedModule> loadEmbeddedModules(ZipFile apkZip, ClassLoader parentCL) {
        List<LoadedModule> result = new ArrayList<>();
        // 模块嵌入在 assets/lspatch/modules/<package>.apk
        try {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = apkZip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("assets/lspatch/modules/") && name.endsWith(".apk")) {
                    String pkg = name.substring("assets/lspatch/modules/".length(), name.length() - 4);
                    android.util.Log.i(TAG, "Found embedded module: " + pkg);
                    // 提取并加载
                    try {
                        File moduleFile = extractEmbeddedModule(apkZip, entry, pkg);
                        LoadedModule m = new LoadedModule();
                        m.packageName = pkg;
                        m.apkPath = moduleFile.getAbsolutePath();
                        m.classLoader = new PathClassLoader(moduleFile.getAbsolutePath(), parentCL);
                        // 查找 assets/xposed_init
                        try (ZipFile moduleZip = new ZipFile(moduleFile)) {
                            java.util.zip.ZipEntry initEntry = moduleZip.getEntry("assets/xposed_init");
                            if (initEntry != null) {
                                try (java.io.InputStream is = moduleZip.getInputStream(initEntry)) {
                                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                                    String line = br.readLine();
                                    if (line != null && !line.trim().isEmpty()) {
                                        m.moduleClass = line.trim();
                                    }
                                }
                            }
                        }
                        result.add(m);
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Failed to load " + pkg, t);
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Error scanning modules", t);
        }
        return result;
    }
    private static File extractEmbeddedModule(ZipFile apkZip, java.util.zip.ZipEntry entry, String pkg) throws IOException {
        // 在应用的 files 目录下提取模块
        File cacheDir = new File("/data/local/tmp/lspatch_modules");
        cacheDir.mkdirs();
        File out = new File(cacheDir, pkg + ".apk");
        if (!out.exists() || out.length() != entry.getSize()) {
            try (java.io.InputStream is = apkZip.getInputStream(entry);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[8192]; int len;
                while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
            }
        }
        return out;
    }
}
