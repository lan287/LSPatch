package org.lsposed.lspatch.patch;

public class Base64 {
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] DECODE = new int[128];

    static {
        for (int i = 0; i < DECODE.length; i++) DECODE[i] = -1;
        for (int i = 0; i < ALPHABET.length; i++) DECODE[ALPHABET[i]] = i;
        DECODE['='] = -2;
    }

    public static String encode(byte[] bytes) {
        StringBuilder sb = new StringBuilder((bytes.length + 2) / 3 * 4);
        int remainder = bytes.length % 3;
        int limit = bytes.length - remainder;
        for (int i = 0; i < limit; i += 3) {
            int b1 = bytes[i] & 0xFF, b2 = bytes[i + 1] & 0xFF, b3 = bytes[i + 2] & 0xFF;
            sb.append(ALPHABET[b1 >> 2]);
            sb.append(ALPHABET[(b1 & 3) << 4 | b2 >> 4]);
            sb.append(ALPHABET[(b2 & 15) << 2 | b3 >> 6]);
            sb.append(ALPHABET[b3 & 63]);
        }
        if (remainder == 1) {
            int b = bytes[limit] & 0xFF;
            sb.append(ALPHABET[b >> 2]);
            sb.append(ALPHABET[(b & 3) << 4]);
            sb.append('='); sb.append('=');
        } else if (remainder == 2) {
            int b1 = bytes[limit] & 0xFF, b2 = bytes[limit + 1] & 0xFF;
            sb.append(ALPHABET[b1 >> 2]);
            sb.append(ALPHABET[(b1 & 3) << 4 | b2 >> 4]);
            sb.append(ALPHABET[(b2 & 15) << 2]);
            sb.append('=');
        }
        return sb.toString();
    }

    public static byte[] decode(String in) {
        String s = in.replaceAll("\\s", "");
        int outLen = s.length() / 4 * 3;
        if (s.endsWith("==")) outLen -= 2;
        else if (s.endsWith("=")) outLen -= 1;
        byte[] out = new byte[outLen];
        int pos = 0, i = 0;
        while (i < s.length()) {
            int c1 = DECODE[s.charAt(i++) & 0x7F];
            int c2 = DECODE[s.charAt(i++) & 0x7F];
            int c3 = DECODE[s.charAt(i++) & 0x7F];
            int c4 = DECODE[s.charAt(i++) & 0x7F];
            int combined = (c1 << 18) | (c2 << 12) | ((c3 == -2 ? 0 : c3) << 6) | (c4 == -2 ? 0 : c4);
            out[pos++] = (byte) (combined >> 16);
            if (c3 != -2) out[pos++] = (byte) (combined >> 8);
            if (c4 != -2) out[pos++] = (byte) combined;
        }
        return out;
    }
}
