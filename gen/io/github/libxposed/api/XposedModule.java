package io.github.libxposed.api;
import android.content.pm.ApplicationInfo;
import io.github.libxposed.api.XposedModuleInterface.*;
public abstract class XposedModule {
    private final ApplicationInfo mApplicationInfo;
    public XposedModule(ApplicationInfo info) { mApplicationInfo = info; }
    public ApplicationInfo getApplicationInfo() { return mApplicationInfo; }
    public void onPackageLoaded(PackageLoadedParam param) {}
    public void onSystemServerLoaded(SystemServerLoadedParam param) {}
    public void onPackageResourcesLoaded(PackageResParam param) {}
}
