package de.robv.android.xposed.callbacks;
import android.content.pm.ApplicationInfo;
import de.robv.android.xposed.XCallback;
import java.util.List;
import java.util.ArrayList;
public abstract class XC_LoadPackage extends XCallback {
    public static final List<XC_LoadPackage> sLoadedPackageCallbacks = new ArrayList<>();
    public XC_LoadPackage() { super(); }
    public XC_LoadPackage(int priority) { super(priority); }
    public abstract void handleLoadPackage(LoadPackageParam lpparam) throws Throwable;
    public static void callAll(LoadPackageParam lpparam) {
        for (XC_LoadPackage cb : sLoadedPackageCallbacks) {
            try { cb.handleLoadPackage(lpparam); } catch (Throwable t) { android.util.Log.e("Xposed", "Error", t); }
        }
    }
    public static final class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public ApplicationInfo appInfo;
        public boolean isFirstApplication;
    }
}
