package org.lsposed.lspatch.loader;
import android.content.pm.ApplicationInfo;
import android.content.res.XResources;
import de.robv.android.xposed.XposedInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class LSPLoader {
    public static void initModules(Object loadedApk) {
        try {
            Field f = loadedApk.getClass().getDeclaredField("mApplicationInfo");
            f.setAccessible(true);
            ApplicationInfo appInfo = (ApplicationInfo) f.get(loadedApk);
            XposedInit.loadedPackagesInProcess.add(appInfo.packageName);
            Field resDirF = loadedApk.getClass().getDeclaredField("mResDir");
            resDirF.setAccessible(true);
            String resDir = (String) resDirF.get(loadedApk);
            XResources.setPackageNameForResDir(appInfo.packageName, resDir);
            Method clM = loadedApk.getClass().getMethod("getClassLoader");
            ClassLoader cl = (ClassLoader) clM.invoke(loadedApk);
            XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam();
            lpparam.packageName = appInfo.packageName;
            lpparam.processName = appInfo.packageName;
            lpparam.classLoader = cl;
            lpparam.appInfo = appInfo;
            lpparam.isFirstApplication = true;
            XC_LoadPackage.callAll(lpparam);
        } catch (Throwable t) {
            android.util.Log.e("LSPatch-Loader", "Error in initModules", t);
        }
    }
}
