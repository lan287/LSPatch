package org.lsposed.lspatch.loader.util;
import java.io.*;
public class FileUtils {
    public static String readAllText(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }
    public static void writeAllText(File file, String text) throws IOException {
        try (FileWriter fw = new FileWriter(file)) { fw.write(text); }
    }
    public static void copy(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }
}
