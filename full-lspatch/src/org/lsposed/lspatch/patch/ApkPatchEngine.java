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
    private static final char[] KEY_STORE_PASS = "lspatch2024".toCharArray();
    private final Context ctx;

    public ApkPatchEngine(Context ctx) { this.ctx = ctx; }

    public File patch2(File target, File[] modules, boolean debuggable, boolean overrideVersion,
                        ApkSigner.LogCallback cb) throws Exception {
        if (cb != null) cb.log("读取目标 APK: " + target.getName() + " (" + formatSize(target.length()) + ")");

        // 1. 读取 APK 文件
        Map<String, byte[]> files = new LinkedHashMap<>();
        Set<String> existingDirs = new LinkedHashSet<>();

        ZipFile zf = new ZipFile(target);
        Enumeration<? extends ZipEntry> entries = zf.entries();
        int dexCount = 0;
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName();
            if (e.isDirectory()) { existingDirs.add(name); continue; }
            if (name.startsWith("META-INF/") &&
                (name.endsWith(".SF") || name.endsWith(".MF") ||
                 name.endsWith(".RSA") || name.endsWith(".DSA") ||
                 name.endsWith(".EC"))) continue;
            files.put(name, readEntry(zf, e));
            if (name.startsWith("classes") && name.endsWith(".dex")) dexCount++;
        }
        zf.close();
        if (cb != null) cb.log("目标 APK 解析: " + dexCount + " dex, " + files.size() + " 个文件");

        // 2. 注入 LSPatch 资源
        // metaloader.dex, loader.dex, native libs
        addAsset(files, "assets/lspatch/metaloader.dex", "metaloader.dex", cb);
        addAsset(files, "assets/lspatch/loader.dex", "loader.dex", cb);
        for (String arch : new String[]{"arm64-v8a","armeabi-v7a","x86","x86_64"}) {
            if (addAsset(files, "assets/lspatch/so/" + arch + "/liblspatch.so",
                         "so/" + arch + "/liblspatch.so", cb) > 0) {
                if (cb != null) cb.log("添加 " + arch + " 原生库");
            }
        }

        // 模块 APK
        int moduleCount = 0;
        if (modules != null && modules.length > 0) {
            for (int i = 0; i < modules.length; i++) {
                File m = modules[i];
                if (m == null || !m.exists()) continue;
                byte[] data = readFileBytes(m);
                String key = "assets/lspatch/modules/module-" + (i + 1) + ".apk";
                files.put(key, data);
                if (cb != null) cb.log("添加模块: " + m.getName());
                moduleCount++;
            }
        }

        // config.json
        String config = "{\"useManager\":false,\"debuggable\":" + debuggable + ",\"sigBypassLevel\":0,\"originalSignature\":null,\"appComponentFactory\":\"org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub\",\"embeddedModules\":" + moduleCount + ",\"lspConfig\":{\"API_CODE\":93,\"VERSION_CODE\":348,\"VERSION_NAME\":\"0.6\",\"CORE_VERSION_CODE\":93,\"CORE_VERSION_NAME\":\"1.0.3\"}}";
        files.put("assets/lspatch/config.json", config.getBytes("UTF-8"));

        // 3. 写未签名 APK
        File unsigned = new File(ctx.getExternalFilesDir(null), "unsigned-" + System.currentTimeMillis() + ".apk");
        writeApk(unsigned, files);
        if (cb != null) cb.log("未签名 APK: " + formatSize(unsigned.length()));

        // 4. 签名
        if (cb != null) cb.log("开始签名 APK...");
        PrivateKey key = loadPrivateKey();
        X509Certificate cert = loadCertificate();
        if (key == null) throw new RuntimeException("私钥加载失败");
        if (cert == null) throw new RuntimeException("证书加载失败");
        File signed = ApkSigner.sign(unsigned, key, cert, cb);
        unsigned.delete();

        String baseName = target.getName().replaceAll("\\.apk$", "");
        File output = new File(ctx.getExternalFilesDir(null), baseName + "-LSPatched-" + System.currentTimeMillis() + ".apk");
        if (!signed.renameTo(output)) { return signed; }
        return output;
    }

    private int addAsset(Map<String, byte[]> files, String dest, String assetPath, ApkSigner.LogCallback cb) {
        try {
            byte[] data = readAssetBytes(assetPath);
            if (data == null || data.length == 0) return 0;
            files.put(dest, data);
            return data.length;
        } catch (Throwable ignored) { return 0; }
    }

    private byte[] readAssetBytes(String path) throws Exception {
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

    private byte[] readEntry(ZipFile zf, ZipEntry e) throws Exception {
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

    private PrivateKey loadPrivateKey() {
        try (InputStream is = ctx.getAssets().open("release.keystore")) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(is, KEY_STORE_PASS);
            return (PrivateKey) ks.getKey("release", KEY_STORE_PASS);
        } catch (Exception e) {
            Log.e(TAG, "加载私钥失败", e);
            return null;
        }
    }

    private X509Certificate loadCertificate() {
        try (InputStream is = ctx.getAssets().open("release.keystore")) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(is, KEY_STORE_PASS);
            return (X509Certificate) ks.getCertificate("release");
        } catch (Exception e) {
            Log.e(TAG, "加载证书失败", e);
            return null;
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1048576) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / 1048576.0);
    }
}
