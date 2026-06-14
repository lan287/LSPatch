package org.lsposed.lspatch.patch;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ApkPatchEngine {

    private static final String TAG = "LSPatch-Engine";
    private final Context ctx;

    // 签名绕过级别 (与 LSPatch Constants 对齐)
    public static final int SIGBYPASS_DISABLE = 0;
    public static final int SIGBYPASS_PM = 1;         // PackageManager 层
    public static final int SIGBYPASS_PM_OPENAT = 2;  // PM + libc openat
    public static final int SIGBYPASS_MAX = 3;         // 完整绕过

    public ApkPatchEngine(Context ctx) { this.ctx = ctx; }

    /**
     * 修补目标 APK — 注入 LSPatch 框架 + SigKiller，不签名
     */
    public File patch(File target, File[] modules, boolean debuggable, boolean overrideVersion,
                       int sigBypassLevel, ApkSigner.LogCallback cb) throws Exception {

        if (cb != null) cb.log("读取: " + target.getName());
        long t0 = System.currentTimeMillis();

        // 提取原 APK 签名证书 (用于签名绕过)
        String origSig = extractOriginalSignature(target);
        if (cb != null && origSig != null) {
            cb.log("✓ 提取原签名: " + origSig.substring(0, Math.min(30, origSig.length())) + "...");
        }

        // 1. 流式复制目标 APK → 临时文件 (去除旧签名)
        File output = new File(ctx.getExternalFilesDir(null), "patch-" + System.currentTimeMillis() + ".apk");
        int entryCount = 0;

        try (ZipFile zf = new ZipFile(target);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {

            zos.setMethod(ZipOutputStream.DEFLATED);
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (e.isDirectory()) continue;
                if (name.startsWith("META-INF/") &&
                    (name.endsWith(".SF") || name.endsWith(".MF") ||
                     name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC")))
                    continue;

                ZipEntry out = new ZipEntry(name);
                out.setMethod(ZipEntry.DEFLATED);
                out.setTime(e.getTime());
                zos.putNextEntry(out);
                try (InputStream is = zf.getInputStream(e)) {
                    byte[] buf = new byte[16384]; int n;
                    while ((n = is.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
                entryCount++;
            }

            // 2. 注入 LSPatch 核心资源
            injectAsset(zos, "assets/lspatch/metaloader.dex", "metaloader.dex");
            injectAsset(zos, "assets/lspatch/loader.dex", "loader.dex");

            for (String arch : new String[]{"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}) {
                try {
                    byte[] so = readAsset("so/" + arch + "/liblspatch.so");
                    addBytesToZip(zos, "assets/lspatch/so/" + arch + "/liblspatch.so", so);
                    addBytesToZip(zos, "lib/" + arch + "/liblspatch.so", so);
                    if (cb != null) cb.log("  ✓ " + arch + " lib");
                } catch (Exception ignored) {}
            }

            // 3. 注入模块
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

            // 4. 生成 config.json (匹配 PatchConfig 格式)
            String config = buildConfig(debuggable, overrideVersion, sigBypassLevel, moduleCount, origSig);
            addBytesToZip(zos, "assets/lspatch/config.json", config.getBytes("UTF-8"));

            if (cb != null) {
                cb.log("配置: sigBypassLevel=" + sigBypassLevel + " debug=" + debuggable
                    + " override=" + overrideVersion + " modules=" + moduleCount);
            }
        }

        if (cb != null) {
            cb.log("流式处理: " + entryCount + " 条目, " + formatSize(output.length()));
        }

        // 5. 重命名输出
        String baseName = target.getName().replaceAll("\\.apk$", "");
        String suffix = "-LSPatched-v0.8";
        if (sigBypassLevel > 0) suffix += "-sigLv" + sigBypassLevel;
        suffix += "-unsigned.apk";
        File finalOutput = new File(ctx.getExternalFilesDir(null), baseName + suffix);
        if (output.renameTo(finalOutput)) { output = finalOutput; }

        if (cb != null) {
            long elapsed = System.currentTimeMillis() - t0;
            cb.log("✓ 修补完成 (" + (elapsed / 1000.0) + "s)");
            cb.log("⚠ 输出未签名，请用 apksigner 签名后安装");
            cb.log("  apksigner sign --ks key.jks output.apk");
        }
        return output;
    }

    // ==================== 签名提取 ====================

    /**
     * 从原 APK 提取签名证书 (Base64 DER)，用于绕过
     * 查找 META-INF/*.RSA 或 *.DSA 或 *.EC
     */
    private String extractOriginalSignature(File apk) {
        try (ZipFile zf = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (name.startsWith("META-INF/") &&
                    (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    try (InputStream is = zf.getInputStream(e);
                         ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                        return Base64.encode(bos.toByteArray());
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ==================== Config 生成 ====================

    /**
     * 生成 config.json — 匹配 LSPatch PatchConfig 格式
     *
     * PatchConfig 字段:
     *   boolean useManager       — 是否使用 Manager 模式
     *   boolean debuggable       — 是否可调试
     *   boolean overrideVersionCode — 允许降级安装
     *   int sigBypassLevel       — 签名绕过级别 (0-3)
     *   String originalSignature — 原签名 Base64 (用于绕过)
     *   String appComponentFactory — AppComponentFactory 类名
     *   LSPConfig lspConfig      — LSP 核心配置
     */
    private String buildConfig(boolean debuggable, boolean override, int sigBypass,
                                int moduleCount, String origSig) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"useManager\":false,");
        sb.append("\"debuggable\":").append(debuggable).append(",");
        sb.append("\"overrideVersionCode\":").append(override).append(",");
        sb.append("\"sigBypassLevel\":").append(sigBypass).append(",");
        if (origSig != null && !origSig.isEmpty()) {
            sb.append("\"originalSignature\":\"").append(origSig).append("\",");
        } else {
            sb.append("\"originalSignature\":null,");
        }
        sb.append("\"appComponentFactory\":\"org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub\",");
        sb.append("\"embeddedModules\":").append(moduleCount).append(",");
        sb.append("\"lspConfig\":{");
        sb.append("\"API_CODE\":93,");
        sb.append("\"VERSION_CODE\":356,");
        sb.append("\"VERSION_NAME\":\"0.8\",");
        sb.append("\"CORE_VERSION_CODE\":93,");
        sb.append("\"CORE_VERSION_NAME\":\"1.0.3\"");
        sb.append("}}");
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

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1048576) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / 1048576.0);
    }
}