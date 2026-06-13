package io.github.libxposed.api;
import android.content.pm.ApplicationInfo;
import android.content.res.XResources;
public interface XposedModuleInterface {
    interface PackageLoadedParam { String getPackageName(); ClassLoader getClassLoader(); ApplicationInfo getApplicationInfo(); boolean isFirstPackage(); }
    interface SystemServerLoadedParam { ClassLoader getClassLoader(); }
    interface PackageResParam { String getPackageName(); XResources getResources(); ApplicationInfo getApplicationInfo(); }
}
