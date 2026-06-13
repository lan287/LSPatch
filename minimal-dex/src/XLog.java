package org.lsposed.lspatch.loader.util;
public class XLog {
    private static final String TAG = "LSPatch";
    public static void i(String msg) { android.util.Log.i(TAG, msg); }
    public static void i(String msg, Throwable t) { android.util.Log.i(TAG, msg, t); }
    public static void d(String msg) { android.util.Log.d(TAG, msg); }
    public static void w(String msg) { android.util.Log.w(TAG, msg); }
    public static void w(String msg, Throwable t) { android.util.Log.w(TAG, msg, t); }
    public static void e(String msg) { android.util.Log.e(TAG, msg); }
    public static void e(String msg, Throwable t) { android.util.Log.e(TAG, msg, t); }
}
