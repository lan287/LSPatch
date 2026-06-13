package org.lsposed.lspatch.patch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * APK v1 JAR 签名实现 —— LSPatch 精简版
 * 功能：对目标 APK 重新签名（移除旧签名，写入 MANIFEST.MF / CERT.SF / CERT.RSA）
 */
public class ApkSigner {

    // ============ DER 编码工具 ============

    private static byte[] derSeq(byte[]... items) throws IOException {
        int total = 0;
        for (byte[] b : items) total += b.length;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x30);
        writeLength(bos, total);
        for (byte[] b : items) bos.write(b);
        return bos.toByteArray();
    }

    private static byte[] derSet(byte[]... items) throws IOException {
        int total = 0;
        for (byte[] b : items) total += b.length;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x31);
        writeLength(bos, total);
        for (byte[] b : items) bos.write(b);
        return bos.toByteArray();
    }

    private static byte[] derInt(byte[] signedBytes) throws IOException {
        // signedBytes: 已经是可签名整数的字节 (BigInteger.toByteArray() 风格)
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x02);
        writeLength(bos, signedBytes.length);
        bos.write(signedBytes);
        return bos.toByteArray();
    }

    private static byte[] derInt(long value) throws IOException {
        // 小整数编码
        if (value < 0x80) return derInt(new byte[] { (byte) value });
        if (value < 0x10000) return derInt(new byte[] { (byte) (value >> 8), (byte) value });
        if (value < 0x1000000L) return derInt(new byte[] { (byte) (value >> 16), (byte) (value >> 8), (byte) value });
        byte[] b = new byte[4];
        b[0] = (byte) (value >> 24); b[1] = (byte) (value >> 16); b[2] = (byte) (value >> 8); b[3] = (byte) value;
        return derInt(b);
    }

    private static byte[] derOctet(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x04);
        writeLength(bos, data.length);
        bos.write(data);
        return bos.toByteArray();
    }

    private static byte[] derOctetEmpty() throws IOException {
        return new byte[] { 0x04, 0x00 };
    }

    private static byte[] derBit(byte[] bitsInBytes) throws IOException {
        // BIT STRING: 0 unused bits 后是内容
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x03);
        writeLength(bos, bitsInBytes.length + 1);
        bos.write(0);
        bos.write(bitsInBytes);
        return bos.toByteArray();
    }

    private static byte[] derNull() {
        return new byte[] { 0x05, 0x00 };
    }

    // OID 编码
    private static byte[] derOid(String oid) throws IOException {
        String[] parts = oid.split("\\.");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        int value = first * 40 + second;
        writeBase128(body, value);
        for (int i = 2; i < parts.length; i++) writeBase128(body, Long.parseLong(parts[i]));
        byte[] bodyBytes = body.toByteArray();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x06);
        writeLength(bos, bodyBytes.length);
        bos.write(bodyBytes);
        return bos.toByteArray();
    }

    private static void writeBase128(ByteArrayOutputStream out, long v) {
        if (v < 0x80) { out.write((byte) v); return; }
        int bits = 63 - Long.numberOfLeadingZeros(v);
        int bytes = (bits / 7) + 1;
        for (int i = bytes - 1; i > 0; i--) {
            out.write((byte) (0x80 | ((v >> (i * 7)) & 0x7F)));
        }
        out.write((byte) (v & 0x7F));
    }

    private static void writeLength(ByteArrayOutputStream bos, int len) {
        if (len < 0x80) bos.write(len);
        else if (len < 0x100) { bos.write(0x81); bos.write(len); }
        else if (len < 0x10000) { bos.write(0x82); bos.write(len >> 8); bos.write(len & 0xFF); }
        else { bos.write(0x83); bos.write(len >> 16); bos.write((len >> 8) & 0xFF); bos.write(len & 0xFF); }
    }

    // Context-specific constructed 标签 [0] / [1] (隐式)
    private static byte[] derCtx0(byte[]... contents) throws IOException {
        int total = 0;
        for (byte[] b : contents) total += b.length;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0xA0);
        writeLength(bos, total);
        for (byte[] b : contents) bos.write(b);
        return bos.toByteArray();
    }

    private static byte[] derCtx1(byte[]... contents) throws IOException {
        int total = 0;
        for (byte[] b : contents) total += b.length;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0xA1);
        writeLength(bos, total);
        for (byte[] b : contents) bos.write(b);
        return bos.toByteArray();
    }

    // ============ 签名主流程 ============

    public static File sign(File apk, PrivateKey key, X509Certificate cert, final LogCallback cb) throws Exception {
        final java.io.PrintStream logger = new java.io.PrintStream(new java.io.OutputStream() {
            StringBuilder sb = new StringBuilder();
            @Override public void write(int b) {
                if (b == '\n') { cb.log(sb.toString()); sb.setLength(0); }
                else sb.append((char) b);
            }
        });

        // 1. 读取 APK，过滤掉旧签名文件 (流式处理，不加载全部内容)
        ZipFile zf = new ZipFile(apk);
        final java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        List<String> names = new ArrayList<>();

        java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e.getName();
            if (e.isDirectory()) continue;
            if (name.startsWith("META-INF/") &&
                (name.endsWith(".SF") || name.endsWith(".MF") ||
                 name.endsWith(".RSA") || name.endsWith(".DSA") ||
                 name.endsWith(".EC"))) continue;
            files.put(name, readEntry(zf, e));
            names.add(name);
        }
        zf.close();

        // 2. 计算内容 hash 用于 MANIFEST.MF
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Collections.sort(names);

        StringBuilder mf = new StringBuilder("Manifest-Version: 1.0\r\nBuilt-By: LSPatch\r\nCreated-By: LSPatch v0.6\r\n\r\n");
        // 先计算每一项
        StringBuilder sectionHashes = new StringBuilder();
        for (String name : names) {
            byte[] data = files.get(name);
            md.reset();
            String hash = Base64.encode(md.digest(data));
            sectionHashes.append("Name: ").append(name).append("\r\n");
            sectionHashes.append("SHA-256-Digest: ").append(hash).append("\r\n\r\n");
        }
        mf.append(sectionHashes.toString());
        byte[] mfBytes = mf.toString().getBytes("UTF-8");

        // 3. CERT.SF - 对整个 MANIFEST 和 每个 section 分别 hash
        StringBuilder sf = new StringBuilder("Signature-Version: 1.0\r\nCreated-By: LSPatch v0.6\r\n");
        md.reset();
        String mfHash = Base64.encode(md.digest(mfBytes));
        sf.append("SHA-256-Digest-Manifest: ").append(mfHash).append("\r\n\r\n");

        // 按 section (每 "Name: xxx") 分段并 hash
        // 先把 mf 按 "\r\n\r\n" 分成 sections
        String[] sections = mf.toString().split("\\r\\n\\r\\n");
        StringBuilder sfSections = new StringBuilder();
        for (int i = 1; i < sections.length; i++) {
            String s = sections[i];
            if (s.trim().length() == 0) continue;
            // 每个 section 必须以 \r\n\r\n 结尾
            String sectionText = s + "\r\n\r\n";
            md.reset();
            String sectionHash = Base64.encode(md.digest(sectionText.getBytes("UTF-8")));
            // 找到 Name: 行
            int idx = s.indexOf("\r\n");
            sfSections.append(s.substring(0, idx + 2)); // Name: xxx\r\n
            sfSections.append("SHA-256-Digest: ").append(sectionHash).append("\r\n\r\n");
        }
        byte[] sfBytes = (sf.toString() + sfSections.toString()).getBytes("UTF-8");

        // 4. 签名 CERT.RSA (PKCS7 SignedData)
        byte[] rsaBytes = buildPKCS7(key, cert, sfBytes, cb);

        // 5. 写入签名后的 APK
        File output = new File(apk.getParent(), apk.getName().replace(".apk", "") + "-signed.apk");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output));
        // 先写签名文件 (JAR 规范: 先写 META-INF/MANIFEST.MF, CERT.SF, CERT.RSA)
        // 实际上签名文件在 ZIP 末尾也可以被 Android 识别
        // 这里先写非签名文件，再写签名文件
        for (String name : names) {
            ZipEntry e = new ZipEntry(name);
            e.setMethod(ZipEntry.DEFLATED);
            e.setTime(System.currentTimeMillis());
            zos.putNextEntry(e);
            zos.write(files.get(name));
            zos.closeEntry();
        }

        addToZip(zos, "META-INF/MANIFEST.MF", mfBytes);
        addToZip(zos, "META-INF/CERT.SF", sfBytes);
        addToZip(zos, "META-INF/CERT.RSA", rsaBytes);

        zos.close();
        return output;
    }

    private static void addToZip(ZipOutputStream zos, String name, byte[] data) throws Exception {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.DEFLATED);
        e.setTime(System.currentTimeMillis());
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] readEntry(ZipFile zf, ZipEntry e) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = zf.getInputStream(e)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static byte[] readFile(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
        fis.close();
        return bos.toByteArray();
    }

    // ============ PKCS7 签名 (核心) ============
    private static byte[] buildPKCS7(PrivateKey key, X509Certificate cert, byte[] sfBytes, LogCallback cb) throws Exception {
        // 1. 用 RSA 签名 CERT.SF
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(sfBytes);
        byte[] signature = sig.sign();

        // 2. 构建签名者信息 SignerInfo
        //    version = 1 (签了 name + 序列号)
        //    issuerAndSerialNumber = SEQUENCE(issuerName, serial)
        //    digestAlgorithm = sha256
        //    signedAttributes = 包含 contentType(messageDigest, signingTime 可选)
        //      必须签名!
        //    signatureAlgorithm = rsaEncryption
        //    encryptedDigest(signature)
        //
        // 简化: 为与 Android AOSP apksigner 兼容，我们构造最小可识别签名者信息：
        // - 签名者信息中不含 signedAttributes (这是 CERTSIG 简化模式)
        // - 直接签名 content (sfBytes)

        // 构造 OID SHA256 = 2.16.840.1.101.3.4.2.1
        // OID RSA_ENCRYPTION = 1.2.840.113549.1.1.1
        // OID DATA = 1.2.840.113549.1.7.1
        // OID SIGNED_DATA = 1.2.840.113549.1.7.2

        // ========= SignerInfo =========
        // SignerInfo ::= SEQUENCE {
        //   version INTEGER 1,
        //   issuerAndSerialNumber SEQUENCE { issuer(X500Principal), serial(INTEGER) },
        //   digestAlgorithm SEQUENCE { oid sha256, NULL },
        //   [0] IMPLICIT signedAttributes OPTIONAL,
        //   signatureAlgorithm SEQUENCE { oid rsaEncryption, NULL },
        //   signatureValue OCTET STRING(signature)
        // }
        //
        // 简化: 没有 signedAttributes - 这是最简单的签名者信息
        // 注意: Android AOSP apksigner 也接受不带 signedAttributes 的签名

        byte[] version = derInt(1L);
        byte[] issuerName = cert.getIssuerX500Principal().getEncoded(); // 预编码 ASN.1
        byte[] serialBytes = cert.getSerialNumber().toByteArray();
        byte[] serial = derInt(serialBytes);
        byte[] issuerAndSerial = derSeq(issuerName, serial);
        byte[] digestAlg = derSeq(derOid("2.16.840.1.101.3.4.2.1"), derNull());
        byte[] sigAlg = derSeq(derOid("1.2.840.113549.1.1.1"), derNull());
        byte[] encryptedDigest = derOctet(signature);

        // 简化 SignerInfo = SEQUENCE(version, issuerAndSerial, digestAlg, sigAlg, encryptedDigest)
        byte[] signerInfo = derSeq(version, issuerAndSerial, digestAlg, sigAlg, encryptedDigest);

        // ========= SignedData =========
        // SignedData ::= SEQUENCE {
        //   version INTEGER 1,
        //   digestAlgorithms SET { digestAlg },
        //   contentInfo SEQUENCE { OID data, [0] EXPLICIT content },
        //   [0] IMPLICIT ExtendedCertificatesAndCertificates { SET OF certificate },
        //   [1] IMPLICIT crls OPTIONAL,
        //   signerInfos SET { signerInfo }
        // }

        byte[] digestAlgorithms = derSet(digestAlg);
        // contentInfo: data OID，内容为空 (可选的 [0] explicit content)
        // ContentInfo ::= SEQUENCE { contentType OID, [0] content OPTIONAL }
        // 我们需要 content 字段指向 "data" (可选)
        // Android 需要这个格式
        byte[] contentInfo = derSeq(derOid("1.2.840.113549.1.7.1"), derCtx0(derOctetEmpty()));

        // 证书: [0] IMPLICIT SET { certificate }
        byte[] certs = derCtx0(derSet(cert.getEncoded()));

        // signerInfos: SET { signerInfo }
        byte[] signerInfos = derSet(signerInfo);

        // 组装 signedData
        byte[] signedData = derSeq(version, digestAlgorithms, contentInfo, certs, signerInfos);

        // ========= 外部 ContentInfo (PKCS#7 文件) =========
        // ContentInfo ::= SEQUENCE {
        //   contentType OID signedData (1.2.840.113549.1.7.2),
        //   content [0] IMPLICIT signedData  // 隐式或显式
        // }
        // 按照 PKCS7 v1.5: ContentInfo.content 是 EXPLICIT [0]
        // 但 Android JAR 签名通常是 IMPLICIT [0]
        // 简化: [0] IMPLICIT 包装 signedData
        byte[] outerContentInfo = derSeq(derOid("1.2.840.113549.1.7.2"), derCtx0(signedData));

        return outerContentInfo;
    }

    public static interface LogCallback {
        void log(String msg);
    }

    // ============ 辅助: 生成测试密钥/证书 (在构建时使用) ============
    // 在 APK 运行时我们直接从 assets 加载密钥和证书
}
