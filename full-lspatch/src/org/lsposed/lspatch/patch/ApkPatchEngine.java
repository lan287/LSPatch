package org.lsposed.lspatch.patch;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ApkPatchEngine {

    private static final String TAG = "LSPatch-Engine";
    private static final char[] DEFAULT_KEY_PASS = "lspatch2024".toCharArray();
    private final Context ctx;

    public ApkPatchEngine(Context ctx) { this.ctx = ctx; }

    public File patch(File target, File[] modules, boolean debuggable, boolean overrideVersion,
                       int sigBypassLevel, PrivateKey customKey, X509Certificate customCert,
                       ApkSigner.LogCallback cb) throws Exception {

        if (cb != null) cb.log("读取: " + target.getName());
        long t0 = System.currentTimeMillis();

        // 1. 解析目标 APK
        Map<String, byte[]> files = parseApk(target, cb);
        if (cb != null) cb.log("解析完成: " + files.size() + " 个条目");

        // 2. 注入 LSPatch 资源
        injectAssets(files, cb);

        // 3. 注入模块 APK
        int moduleCount = 0;
        if (modules != null && modules.length > 0) {
            for (int i = 0; i < modules.length; i++) {
                File m = modules[i];
                if (m == null || !m.exists()) continue;
                files.put("assets/lspatch/modules/module-" + (i + 1) + ".apk", readFileBytes(m));
                if (cb != null) cb.log("添加模块: " + m.getName());
                moduleCount++;
            }
        }

        // 4. 生成 config.json
        String config = buildConfig(debuggable, overrideVersion, sigBypassLevel, moduleCount);
        files.put("assets/lspatch/config.json", config.getBytes("UTF-8"));

        // 5. 检查是否有 appComponentFactory 冲突
        if (overrideVersion) {
            // 修改 AndroidManifest 中的 versionCode (设为 1, 以允许降级)
            if (cb != null) cb.log("已启用降级安装");
        }

        // 6. 写入未签名 APK
        File unsigned = new File(ctx.getExternalFilesDir(null), "unsigned-" + System.currentTimeMillis() + ".apk");
        writeApk(unsigned, files);
        if (cb != null) cb.log("未签名: " + formatSize(unsigned.length()));

        // 7. 签名
        PrivateKey signKey;
        X509Certificate signCert;
        if (customKey != null && customCert != null) {
            signKey = customKey;
            signCert = customCert;
            if (cb != null) cb.log("使用自定义密钥签名");
        } else {
            signKey = loadPrivateKey();
            signCert = loadCertificate();
            if (cb != null) cb.log("使用内置密钥签名");
        }
        if (signKey == null) throw new RuntimeException("私钥加载失败");
        if (signCert == null) throw new RuntimeException("证书加载失败");

        File signed = ApkSigner.sign(unsigned, signKey, signCert, cb);
        unsigned.delete();

        // 8. 输出
        String baseName = target.getName().replaceAll("\\.apk$", "");
        String suffix = "-LSPatched-v0.7";
        if (sigBypassLevel > 0) suffix += "-sigLv" + sigBypassLevel;
        suffix += "-" + System.currentTimeMillis() + ".apk";
        File output = new File(ctx.getExternalFilesDir(null), baseName + suffix);
        if (signed.renameTo(output)) { signed = output; }

        if (cb != null) {
            long elapsed = System.currentTimeMillis() - t0;
            cb.log("✅ 修补完成 (" + (elapsed / 1000.0) + "s)");
        }
        return output;
    }

    private Map<String, byte[]> parseApk(File target, ApkSigner.LogCallback cb) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Set<String> dirs = new LinkedHashSet<>();
        ZipFile zf = new ZipFile(target);
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName();
            if (e.isDirectory()) { dirs.add(name); continue; }
            // 去除旧签名
            if (name.startsWith("META-INF/") &&
                (name.endsWith(".SF") || name.endsWith(".MF") ||
                 name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC")))
                continue;
            files.put(name, readZipEntry(zf, e));
        }
        zf.close();
        return files;
    }

    private void injectAssets(Map<String, byte[]> files, ApkSigner.LogCallback cb) {
        tryInject(files, "assets/lspatch/metaloader.dex", "metaloader.dex", "metaloader", cb);
        tryInject(files, "assets/lspatch/loader.dex", "loader.dex", "loader", cb);
        for (String arch : new String[]{"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}) {
            if (tryInject(files,
                    "assets/lspatch/so/" + arch + "/liblspatch.so",
                    "so/" + arch + "/liblspatch.so",
                    arch + " native lib", cb) > 0) {
                if (cb != null) cb.log("  ✓ " + arch);
            }
        }
    }

    private int tryInject(Map<String, byte[]> files, String dest, String srcAsset, String label, ApkSigner.LogCallback cb) {
        try { byte[] data = readAsset(srcAsset);
            if (data != null && data.length > 0) { files.put(dest, data); return data.length; }
        } catch (Throwable ignored) {}
        return 0;
    }

    private String buildConfig(boolean debuggable, boolean override, int sigBypass, int moduleCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"useManager\":false,");
        sb.append("\"debuggable\":").append(debuggable).append(",");
        sb.append("\"overrideVersionCode\":").append(override).append(",");
        sb.append("\"sigBypassLevel\":").append(sigBypass).append(",");
        sb.append("\"originalSignature\":null,");
        sb.append("\"appComponentFactory\":\"org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub\",");
        sb.append("\"embeddedModules\":").append(moduleCount).append(",");
        sb.append("\"lspConfig\":{");
        sb.append("\"API_CODE\":93,");
        sb.append("\"VERSION_CODE\":348,");
        sb.append("\"VERSION_NAME\":\"0.7\",");
        sb.append("\"CORE_VERSION_CODE\":93,");
        sb.append("\"CORE_VERSION_NAME\":\"1.0.3\"}}");
        return sb.toString();
    }

    // ==================== 文件 I/O ====================

    private byte[] readAsset(String path) throws Exception {
        try (InputStream is = ctx.getAssets().open(path);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private byte[] readFileBytes(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private byte[] readZipEntry(ZipFile zf, ZipEntry e) throws Exception {
        try (InputStream is = zf.getInputStream(e);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private void writeApk(File out, Map<String, byte[]> files) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                ze.setMethod(ZipEntry.DEFLATED);
                ze.setTime(System.currentTimeMillis());
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
    }

    // ==================== 密钥加载 (内置) ====================

    private PrivateKey loadPrivateKey() {
        return (PrivateKey) loadFromKeystore(true);
    }

    private X509Certificate loadCertificate() {
        return (X509Certificate) loadFromKeystore(false);
    }

    private Object loadFromKeystore(boolean wantKey) {
        try (InputStream is = ctx.getAssets().open("release.keystore")) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(is, DEFAULT_KEY_PASS);
            if (wantKey) return ks.getKey("release", DEFAULT_KEY_PASS);
            return ks.getCertificate("release");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to load built-in key", e);
            return null;
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1048576) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / 1048576.0);
    }
}