package org.lsposed.lspatch.patch;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ApkPatchEngine {

    private static final String TAG = "LSPatch-Engine";
    private static final String[] KEYSTORE_TYPES = {"BKS", "PKCS12", "JKS"};
    private static final char[] DEFAULT_KEY_PASS = "lspatch".toCharArray();
    private final Context ctx;

    public ApkPatchEngine(Context ctx) { this.ctx = ctx; }

    public File patch(File target, File[] modules, boolean debuggable, boolean overrideVersion,
                       int sigBypassLevel, PrivateKey customKey, X509Certificate customCert,
                       ApkSigner.LogCallback cb) throws Exception {

        if (cb != null) cb.log("读取: " + target.getName());
        long t0 = System.currentTimeMillis();

        // 1. 流式复制目标 APK 到临时文件 (去除旧签名)
        File unsigned = new File(ctx.getExternalFilesDir(null), "unsigned-" + System.currentTimeMillis() + ".apk");
        int entryCount = 0;
        try (ZipFile zf = new ZipFile(target);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(unsigned))) {

            zos.setMethod(ZipOutputStream.DEFLATED);
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (e.isDirectory()) continue;
                // 跳过旧签名文件
                if (name.startsWith("META-INF/") &&
                    (name.endsWith(".SF") || name.endsWith(".MF") ||
                     name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC")))
                    continue;

                ZipEntry out = new ZipEntry(name);
                out.setMethod(ZipEntry.DEFLATED);
                out.setTime(e.getTime());
                zos.putNextEntry(out);
                try (InputStream is = zf.getInputStream(e)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = is.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
                entryCount++;
            }

            // 2. 注入 LSPatch 资源
            injectAsset(zos, "assets/lspatch/metaloader.dex", "metaloader.dex");
            injectAsset(zos, "assets/lspatch/loader.dex", "loader.dex");
            for (String arch : new String[]{"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}) {
                try {
                    byte[] so = readAsset("so/" + arch + "/liblspatch.so");
                    addBytesToZip(zos, "assets/lspatch/so/" + arch + "/liblspatch.so", so);
                    if (cb != null) cb.log("  ✓ " + arch + " lib");
                    // 同时复制到 lib/
                    addBytesToZip(zos, "lib/" + arch + "/liblspatch.so", so);
                } catch (Exception ignored) {}
            }

            // 3. 注入模块 APK
            int moduleCount = 0;
            if (modules != null && modules.length > 0) {
                for (int i = 0; i < modules.length; i++) {
                    File m = modules[i];
                    if (m == null || !m.exists()) continue;
                    addFileToZip(zos, "assets/lspatch/modules/module-" + (i + 1) + ".apk", m);
                    if (cb != null) cb.log("添加模块: " + m.getName());
                    moduleCount++;
                }
            }

            // 4. 生成 config.json
            String config = buildConfig(debuggable, overrideVersion, sigBypassLevel, moduleCount);
            addBytesToZip(zos, "assets/lspatch/config.json", config.getBytes("UTF-8"));
        }

        if (cb != null) {
            cb.log("流式处理完成: " + entryCount + " 条目, " + formatSize(unsigned.length()));
        }

        // 5. 签名
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

        // 6. 输出
        String baseName = target.getName().replaceAll("\\.apk$", "");
        String suffix = "-LSPatched-v0.7";
        if (sigBypassLevel > 0) suffix += "-sigLv" + sigBypassLevel;
        suffix += "-" + System.currentTimeMillis() + ".apk";
        File output = new File(ctx.getExternalFilesDir(null), baseName + suffix);
        if (signed.renameTo(output)) { signed = output; }

        if (cb != null) {
            long elapsed = System.currentTimeMillis() - t0;
            cb.log("✓ 修补完成 (" + (elapsed / 1000.0) + "s)");
        }
        return output;
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

    private void injectAsset(ZipOutputStream zos, String destPath, String assetName) {
        try {
            byte[] data = readAsset(assetName);
            if (data != null && data.length > 0) addBytesToZip(zos, destPath, data);
        } catch (Exception ignored) {}
    }

    private byte[] readAsset(String path) throws Exception {
        try (InputStream is = ctx.getAssets().open(path);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private void addBytesToZip(ZipOutputStream zos, String name, byte[] data) throws Exception {
        ZipEntry ze = new ZipEntry(name);
        ze.setMethod(ZipEntry.DEFLATED);
        ze.setTime(System.currentTimeMillis());
        zos.putNextEntry(ze);
        zos.write(data);
        zos.closeEntry();
    }

    private void addFileToZip(ZipOutputStream zos, String name, File f) throws Exception {
        ZipEntry ze = new ZipEntry(name);
        ze.setMethod(ZipEntry.DEFLATED);
        ze.setTime(System.currentTimeMillis());
        zos.putNextEntry(ze);
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[16384]; int n;
            while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
        }
        zos.closeEntry();
    }

    // ==================== 密钥加载 (内置) ====================

    private PrivateKey loadPrivateKey() {
        return (PrivateKey) loadFromKeystore(true);
    }

    private X509Certificate loadCertificate() {
        return (X509Certificate) loadFromKeystore(false);
    }

    private Object loadFromKeystore(boolean wantKey) {
        // 尝试多种密钥库类型
        for (String type : KEYSTORE_TYPES) {
            try (InputStream is = ctx.getAssets().open("release.keystore")) {
                KeyStore ks = KeyStore.getInstance(type);
                ks.load(is, DEFAULT_KEY_PASS);
                if (wantKey) {
                    Object key = ks.getKey("lspatch", DEFAULT_KEY_PASS);
                    if (key != null) return key;
                } else {
                    Object cert = ks.getCertificate("lspatch");
                    if (cert != null) return cert;
                }
            } catch (Exception ignored) {}
        }
        android.util.Log.e(TAG, "Failed to load built-in key with all types");
        return null;
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1048576) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / 1048576.0);
    }
}