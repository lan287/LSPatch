package de.robv.android.xposed;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class XposedBridge {
    public static final String TAG = "LSPosed-Bridge";
    public static volatile List<Object> sLoadedPackageCallbacks = Collections.synchronizedList(new ArrayList<>());
    public static volatile List<Object> sInitPackageResourcesCallbacks = Collections.synchronizedList(new ArrayList<>());
    public static final int XPOSED_VERSION = 100;
    public static boolean XPOSED_BRIDGE_LOADED = false;
    public static boolean hookMethod(Member hookMethod, XC_MethodHook callback) {
        try {
            sLoadedPackageCallbacks.add(callback);
            return true;
        } catch (Throwable t) { return false; }
    }
    public static void unhookMethod(Member hookMethod, XC_MethodHook callback) {}
    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args) throws Throwable { return null; }
    public static void log(String message) { android.util.Log.i(TAG, message); }
    public static void log(Throwable t) { android.util.Log.e(TAG, t != null ? t.getMessage() : "Error", t); }
}
