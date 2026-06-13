package org.lsposed.lspatch.loader;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import de.robv.android.xposed.XposedInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import hidden.HiddenApiBridge;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspatch.share.PatchConfig;
import org.lsposed.lspatch.util.ModuleLoader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.zip.ZipFile;

public class LSPApplication {
    private static final String TAG = "LSPatch-Loader";
    private static volatile boolean initialized = false;

    public static void onLoad(ClassLoader classLoader, Object... args) {
        if (initialized) return;
        initialized = true;
        try {
            Log.i(TAG, "LSPatch loader starting...");
            XposedBridge.XPOSED_BRIDGE_LOADED = true;
            
            // 通过反射获取 ApplicationInfo
            Object activityThread = getActivityThread();
            ApplicationInfo appInfo = getAppInfo(activityThread);
            String packageName = appInfo != null ? appInfo.packageName : "";
            String apkPath = appInfo != null ? appInfo.sourceDir : "";
            Log.i(TAG, "Loading for package: " + packageName);
            
            // 读取配置
            PatchConfig config = readConfig(classLoader);
            if (config == null) {
                config = new PatchConfig();
                config.useManager = false;
            }
            
            // 加载嵌入的 Xposed 模块
            if (!config.useManager && config.embeddedModules != null && !config.embeddedModules.isEmpty()) {
                try (ZipFile apkZip = new ZipFile(apkPath)) {
                    List<ModuleLoader.LoadedModule> modules = ModuleLoader.loadEmbeddedModules(apkZip, classLoader);
                    for (ModuleLoader.LoadedModule m : modules) {
                        callModuleOnLoad(m, packageName, classLoader, appInfo);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Error loading modules", t);
                }
            }
            
            // 触发 XC_LoadPackage 回调
            XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam();
            lpparam.packageName = packageName;
            lpparam.processName = packageName;
            lpparam.classLoader = classLoader;
            lpparam.appInfo = appInfo;
            lpparam.isFirstApplication = true;
            for (Object cb : XposedBridge.sLoadedPackageCallbacks) {
                try {
                    if (cb instanceof XC_LoadPackage) ((XC_LoadPackage) cb).handleLoadPackage(lpparam);
                } catch (Throwable t) { Log.e(TAG, "Error in hook callback", t); }
            }
            XC_LoadPackage.callAll(lpparam);
            
            XposedInit.loadedPackagesInProcess.add(packageName);
            Log.i(TAG, "LSPatch loader initialized successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize LSPatch loader", t);
        }
    }

    private static void callModuleOnLoad(ModuleLoader.LoadedModule m, String pkg, ClassLoader cl, ApplicationInfo appInfo) {
        try {
            if (m.moduleClass != null) {
                Class<?> mainClass = Class.forName(m.moduleClass, true, m.classLoader);
                Object instance = mainClass.newInstance();
                for (Method method : mainClass.getMethods()) {
                    if ("handleLoadPackage".equals(method.getName()) && method.getParameterTypes().length == 1) {
                        XC_LoadPackage.LoadPackageParam lpp = new XC_LoadPackage.LoadPackageParam();
                        lpp.packageName = pkg;
                        lpp.classLoader = cl;
                        lpp.appInfo = appInfo;
                        lpp.processName = pkg;
                        lpp.isFirstApplication = true;
                        method.invoke(instance, lpp);
                        Log.i(TAG, "Called handleLoadPackage on " + m.packageName);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize module " + m.packageName, t);
        }
    }

    private static PatchConfig readConfig(ClassLoader cl) {
        try (InputStream is = cl.getResourceAsStream(Constants.CONFIG_ASSET_PATH)) {
            if (is == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int len;
            while ((len = is.read(buf)) > 0) bos.write(buf, 0, len);
            String json = bos.toString("UTF-8");
            Log.d(TAG, "Config: " + json);
            PatchConfig cfg = new PatchConfig();
            cfg.useManager = json.contains("\"useManager\":true") || json.contains("\"useManager\": true");
            return cfg;
        } catch (Throwable t) {
            Log.e(TAG, "Error reading config", t);
            return null;
        }
    }

    private static Object getActivityThread() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method m = at.getDeclaredMethod("currentActivityThread");
            m.setAccessible(true);
            return m.invoke(null);
        } catch (Throwable t) { return null; }
    }

    private static ApplicationInfo getAppInfo(Object activityThread) {
        try {
            if (activityThread == null) return null;
            Field f = activityThread.getClass().getDeclaredField("mPackages");
            f.setAccessible(true);
            Object map = f.get(activityThread);
            if (map instanceof java.util.Map) {
                for (Object v : ((java.util.Map<?, ?>) map).values()) {
                    Field af = v.getClass().getDeclaredField("mApplicationInfo");
                    af.setAccessible(true);
                    return (ApplicationInfo) af.get(v);
                }
            }
        } catch (Throwable t) { Log.e(TAG, "getAppInfo failed", t); }
        return null;
    }
}
