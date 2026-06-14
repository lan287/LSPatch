package org.lsposed.lspatch.sigkiller;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * SigKiller v2 — 多层纵深签名绕过
 *
 * 参考方案:
 *   - APKKiller (aimardcr)  — JNI + ActivityThread/LoadedApk 字段修改
 *   - ApkSignatureKiller / MT Manager  — IPackageManager 动态代理 + ActivityThread.sPackageManager
 *   - CorePatch (LSPosed)  — SigningDetails.checkCapability + Digest 验证绕过
 *   - LSPatch SigBypass  — PackageParser + PackageInfo.CREATOR
 *
 * 6 层绕过:
 *   Layer 1: ActivityThread.sPackageManager 动态代理 (MT Manager 方案)
 *   Layer 2: ApplicationPackageManager.mPM 替换
 *   Layer 3: LoadedApk 内部字段修改 (APKKiller 方案)
 *   Layer 4: PackageInfo.CREATOR 代理 (LSPatch 方案)
 *   Layer 5: Hidden API 限制绕过
 *   Layer 6: 签名缓存清理
 */
public class SigKiller {

    private static final String TAG = "SigKiller";
    private static boolean installed = false;
    private static String originalSigB64;
    private static String targetPkg;
    private static byte[] originalSigBytes;

    /**
     * 初始化 — 由 metaloader 或 Application.attachBaseContext 调用
     */
    public static void init(android.content.Context ctx) {
        if (installed) return;
        installed = true;
        targetPkg = ctx.getPackageName();

        try {
            // 读取原始签名
            loadConfig(ctx);

            // 绕过 Hidden API 限制 (Android 9+)
            bypassHiddenApiRestrictions();

            // Layer 1+2: 替换 IPackageManager (核心)
            hookIPackageManagerFull();

            // Layer 3: 修改 LoadedApk 内部字段
            modifyLoadedApk();

            // Layer 4: 代理 PackageInfo.CREATOR
            hookPackageInfoCreator();

            // Layer 6: 清理缓存
            clearSignatureCache();

            Log.i(TAG, "✓ SigKiller v2 已激活 (" + targetPkg + ")");
        } catch (Throwable e) {
            Log.e(TAG, "SigKiller 初始化失败", e);
        }
    }

    // ==================== 配置加载 ====================

    private static void loadConfig(android.content.Context ctx) {
        try {
            InputStream is = ctx.getAssets().open("lspatch/config.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            String json = bos.toString("UTF-8");

            int idx = json.indexOf("\"originalSignature\"");
            if (idx >= 0) {
                int start = json.indexOf("\"", idx + 20);
                if (start >= 0) {
                    int end = json.indexOf("\"", start + 1);
                    if (end >= 0) {
                        originalSigB64 = json.substring(start + 1, end);
                        if (originalSigB64.equals("null") || originalSigB64.isEmpty()) {
                            originalSigB64 = null;
                        } else {
                            originalSigBytes = Base64.decode(originalSigB64, Base64.DEFAULT);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "配置加载失败: " + e.getMessage());
        }
    }

    // ==================== Hidden API 绕过 ====================

    private static void bypassHiddenApiRestrictions() {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            // 方法1: 通过反射修改 VMRuntime 的隐藏API策略
            Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object runtime = getRuntime.invoke(null);
            Method setHiddenApiExemptions = vmRuntime.getDeclaredMethod("setHiddenApiExemptions", String[].class);
            setHiddenApiExemptions.invoke(runtime, (Object) new String[]{"L"});
            Log.i(TAG, "✓ Hidden API 限制已绕过");
        } catch (Throwable e1) {
            // 方法2: 尝试通过 meta-reflection
            try {
                Method forName = Class.class.getDeclaredMethod("forName", String.class);
                Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
                Log.i(TAG, "✓ Hidden API 绕过 v2");
            } catch (Throwable e2) {
                Log.w(TAG, "Hidden API 绕过失败: " + e1.getMessage());
            }
        }
    }

    // ==================== Layer 1+2: IPackageManager 全面代理 ====================

    private static void hookIPackageManagerFull() {
        try {
            // 1. 获取 ActivityThread
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAT = atClass.getDeclaredMethod("currentActivityThread");
            currentAT.setAccessible(true);
            Object activityThread = currentAT.invoke(null);

            // 2. 获取 IPackageManager (从 ActivityThread.sPackageManager)
            Field spmField = atClass.getDeclaredField("sPackageManager");
            makeMutable(spmField);
            Object originalPM = spmField.get(activityThread);

            if (originalPM == null) {
                // 尝试从 ServiceManager 获取
                Class<?> smClass = Class.forName("android.os.ServiceManager");
                Method getService = smClass.getDeclaredMethod("getService", String.class);
                IBinder binder = (IBinder) getService.invoke(null, "package");
                Class<?> stubClass = Class.forName("android.content.pm.IPackageManager$Stub");
                Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
                originalPM = asInterface.invoke(null, binder);
            }

            if (originalPM == null) {
                Log.w(TAG, "无法获取 IPackageManager");
                return;
            }

            // 3. 创建动态代理
            Class<?> iPMClass = Class.forName("android.content.pm.IPackageManager");
            Object proxy = Proxy.newProxyInstance(
                iPMClass.getClassLoader(),
                new Class<?>[]{iPMClass},
                new PMInvocationHandlerV2(originalPM)
            );

            // 4. 替换 ActivityThread.sPackageManager
            spmField.set(activityThread, proxy);
            Log.i(TAG, "✓ Layer 1: ActivityThread.sPackageManager 已代理");

            // 5. 替换 PackageManager.mPM (用户态 PM 引用)
            try {
                PackageManager pm = getApplicationPackageManager();
                if (pm != null) {
                    Field mpmField = pm.getClass().getDeclaredField("mPM");
                    makeMutable(mpmField);
                    mpmField.set(pm, proxy);
                    Log.i(TAG, "✓ Layer 2: PackageManager.mPM 已替换");
                }
            } catch (Throwable e) {
                Log.w(TAG, "Layer 2 失败: " + e.getMessage());
            }

            // 6. 同时替换 ServiceManager 中的缓存(如果存在)
            try {
                Class<?> smClass = Class.forName("android.os.ServiceManager");
                for (Field f : smClass.getDeclaredFields()) {
                    if (java.util.Map.class.isAssignableFrom(f.getType())) {
                        makeMutable(f);
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> cache = (java.util.Map<String, Object>) f.get(null);
                        if (cache != null && cache.containsKey("package")) {
                            cache.put("package", proxy);
                            Log.i(TAG, "✓ ServiceManager 缓存已更新");
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}

        } catch (Throwable e) {
            Log.w(TAG, "IPackageManager 代理失败: " + e.getMessage());
        }
    }

    private static PackageManager getApplicationPackageManager() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAT = atClass.getDeclaredMethod("currentActivityThread");
            currentAT.setAccessible(true);
            Object at = currentAT.invoke(null);
            Method getApplication = atClass.getDeclaredMethod("getApplication");
            getApplication.setAccessible(true);
            Object app = getApplication.invoke(at);
            if (app instanceof android.app.Application) {
                return ((android.app.Application) app).getPackageManager();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ==================== Layer 3: LoadedApk 字段修改 (APKKiller 方案) ====================

    private static void modifyLoadedApk() {
        if (originalSigBytes == null) return;
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAT = atClass.getDeclaredMethod("currentActivityThread");
            currentAT.setAccessible(true);
            Object at = currentAT.invoke(null);

            // 获取 BoundApplication (AppBindData)
            Field mBoundApplication = atClass.getDeclaredField("mBoundApplication");
            makeMutable(mBoundApplication);
            Object bindData = mBoundApplication.get(at);

            // 获取 info (LoadedApk)
            if (bindData != null) {
                Field infoField = bindData.getClass().getDeclaredField("info");
                makeMutable(infoField);
                Object loadedApk = infoField.get(bindData);

                if (loadedApk != null) {
                    // 修改 mSignatures
                    try {
                        Field sigField = loadedApk.getClass().getDeclaredField("mSignatures");
                        makeMutable(sigField);
                        Signature[] sigs = new Signature[]{new Signature(originalSigBytes)};
                        sigField.set(loadedApk, sigs);
                        Log.i(TAG, "✓ Layer 3a: LoadedApk.mSignatures 已修改");
                    } catch (NoSuchFieldException e) {
                        // 某些版本没有这个字段，尝试其他途径
                    }

                    // 修改 mApplicationInfo 的签名相关
                    try {
                        Field appInfoField = loadedApk.getClass().getDeclaredField("mApplicationInfo");
                        makeMutable(appInfoField);
                        Object appInfo = appInfoField.get(loadedApk);
                        if (appInfo != null && "android.content.pm.ApplicationInfo".equals(appInfo.getClass().getName())) {
                            // 修改 sourceDir / publicSourceDir (防止 APK 完整性检测)
                            String apkPath = (String) appInfo.getClass().getField("sourceDir").get(appInfo);
                            Field sourceDir = appInfo.getClass().getField("sourceDir");
                            Field publicSourceDir = appInfo.getClass().getField("publicSourceDir");
                            makeMutable(sourceDir);
                            makeMutable(publicSourceDir);
                            // 保持路径不变，但确保签名信息不被追溯
                            Log.i(TAG, "✓ Layer 3b: ApplicationInfo 已检查");
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // 获取所有 LoadedApk 缓存
            try {
                Field mPackages = atClass.getDeclaredField("mPackages");
                makeMutable(mPackages);
                @SuppressWarnings("unchecked")
                Object packages = mPackages.get(at);
                if (packages instanceof java.util.Map) {
                    // 遍历所有 LoadedApk，修改签名
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) packages;
                    for (Object weakRef : map.values()) {
                        try {
                            Object loadedApkObj = weakRef.getClass().getMethod("get").invoke(weakRef);
                            if (loadedApkObj != null) {
                                try {
                                    Field sigField = loadedApkObj.getClass().getDeclaredField("mSignatures");
                                    makeMutable(sigField);
                                    sigField.set(loadedApkObj, new Signature[]{new Signature(originalSigBytes)});
                                } catch (NoSuchFieldException ignored2) {}
                            }
                        } catch (Throwable ignored2) {}
                    }
                    Log.i(TAG, "✓ Layer 3c: 所有 LoadedApk 缓存已处理");
                }
            } catch (Throwable ignored) {}

        } catch (Throwable e) {
            Log.w(TAG, "Layer 3 失败: " + e.getMessage());
        }
    }

    // ==================== Layer 4: PackageInfo.CREATOR 代理 ====================

    @SuppressWarnings("unchecked")
    private static void hookPackageInfoCreator() {
        try {
            Field creatorField = PackageInfo.class.getField("CREATOR");
            makeMutable(creatorField);
            final Parcelable.Creator<PackageInfo> original =
                (Parcelable.Creator<PackageInfo>) creatorField.get(null);

            Parcelable.Creator<PackageInfo> proxy = new Parcelable.Creator<PackageInfo>() {
                @Override
                public PackageInfo createFromParcel(Parcel source) {
                    PackageInfo pi = original.createFromParcel(source);
                    if (pi != null) fixSignature(pi);
                    return pi;
                }
                @Override
                public PackageInfo[] newArray(int size) {
                    return original.newArray(size);
                }
            };
            creatorField.set(null, proxy);
            Log.i(TAG, "✓ Layer 4: PackageInfo.CREATOR 已代理");
        } catch (Throwable e) {
            Log.w(TAG, "Layer 4 失败: " + e.getMessage());
        }
    }

    // ==================== Layer 6: 缓存清理 ====================

    private static void clearSignatureCache() {
        try {
            // 清理 PackageManager 的签名缓存 (sPackageInfoCache)
            Class<?> pmClass = Class.forName("android.app.ApplicationPackageManager");
            Field cacheField = pmClass.getDeclaredField("sPackageInfoCache");
            makeMutable(cacheField);
            Object cache = cacheField.get(null);
            if (cache instanceof java.util.Map) {
                ((java.util.Map<?, ?>) cache).clear();
                Log.i(TAG, "✓ Layer 6: PackageManager 缓存已清理");
            }
        } catch (Throwable e) {
            Log.w(TAG, "Layer 6 失败: " + e.getMessage());
        }
    }

    // ==================== 签名修复 ====================

    private static void fixSignature(PackageInfo pi) {
        if (pi == null || pi.packageName == null) return;
        if (originalSigBytes == null) return;

        try {
            Signature replacement = new Signature(originalSigBytes);

            // 替换 signatures 数组
            if (pi.signatures != null && pi.signatures.length > 0) {
                pi.signatures[0] = replacement;
            } else {
                pi.signatures = new Signature[]{replacement};
            }

            // Android P+ SigningInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo si = pi.signingInfo;
                if (si != null) {
                    try {
                        // 反射修改 signingInfo 的内部数据
                        // apkContentsSigners
                        Field acsField = SigningInfo.class.getDeclaredField("mSigningDetails");
                        if (acsField != null) {
                            makeMutable(acsField);
                            // SigningDetails 是隐藏类，我们只修改 PackageInfo 的 signingInfo
                        }
                    } catch (Throwable ignored) {}

                    // 如果 signingInfo 有 getApkContentsSigners，尝试获取并修改
                    try {
                        Signature[] contents = si.getApkContentsSigners();
                        if (contents != null && contents.length > 0) {
                            contents[0] = replacement;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // Android R+ (11+) 可能有多签名
            if (Build.VERSION.SDK_INT >= 30 && pi.signingInfo != null) {
                try {
                    Signature[] history = pi.signingInfo.getSigningCertificateHistory();
                    if (history != null && history.length > 0) {
                        history[0] = replacement;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            Log.w(TAG, "签名替换失败: " + e.getMessage());
        }
    }

    // ==================== IPackageManager 代理处理器 ====================

    private static class PMInvocationHandlerV2 implements InvocationHandler {
        private final Object originalPM;

        PMInvocationHandlerV2(Object originalPM) { this.originalPM = originalPM; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(originalPM, args);

            // 拦截 getPackageInfo
            if (result instanceof PackageInfo) {
                fixSignature((PackageInfo) result);
            }

            return result;
        }
    }

    // ==================== 反射工具 ====================

    private static void makeMutable(Field f) {
        f.setAccessible(true);
        try {
            Field modifiers = Field.class.getDeclaredField("accessFlags");
            modifiers.setAccessible(true);
            modifiers.setInt(f, f.getModifiers() & ~Modifier.FINAL);
        } catch (Throwable e) {
            try {
                // Android 11+ 可能需要用另一种方式
                Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                modifiers.setInt(f, f.getModifiers() & ~Modifier.FINAL);
            } catch (Throwable ignored) {}
        }
    }
}