package org.lsposed.lspatch.sigkiller;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
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
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SigKiller — Java 层签名绕过，无需 Xposed/Root
 *
 * 参考: LSPatch SigBypass + CorePatch + ApkSignatureKiller
 *
 * 原理:
 * 1. 动态代理 IPackageManager — 拦截所有 PackageManager 调用，替换返回的签名
 * 2. 替换 PackageInfo.CREATOR — 拦截 Parcel 反序列化，替换签名
 * 3. 读取原始签名 → 所有签名查询返回原始签名，欺骗应用的签名校验
 */
public class SigKiller {

    private static final String TAG = "SigKiller";
    private static boolean installed = false;
    private static String originalSigB64;
    private static String targetPkg;

    /**
     * 初始化 — 由 metaloader 或 Application 调用
     */
    public static void init(android.content.Context ctx) {
        if (installed) return;
        installed = true;
        targetPkg = ctx.getPackageName();

        try {
            // 1. 读取 config.json 中的原始签名
            loadConfig(ctx);

            // 2. 代理 PackageInfo.CREATOR (拦截 Parcel 反序列化)
            hookPackageInfoCreator();

            // 3. 动态代理 IPackageManager (拦截所有 PM 调用)
            hookIPackageManager();

            Log.i(TAG, "✓ SigKiller 已激活 | pkg=" + targetPkg +
                " | sig=" + (originalSigB64 != null ? originalSigB64.substring(0, Math.min(20, originalSigB64.length())) + "..." : "null"));
        } catch (Throwable e) {
            Log.e(TAG, "✗ SigKiller 初始化失败", e);
        }
    }

    // ==================== 配置加载 ====================

    private static void loadConfig(android.content.Context ctx) {
        try {
            // 从 assets/lspatch/config.json 读取
            InputStream is = ctx.getAssets().open("lspatch/config.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            String json = bos.toString("UTF-8");

            // 提取 originalSignature
            int idx = json.indexOf("\"originalSignature\"");
            if (idx >= 0) {
                int start = json.indexOf("\"", idx + 20);
                if (start >= 0) {
                    int end = json.indexOf("\"", start + 1);
                    if (end >= 0) {
                        originalSigB64 = json.substring(start + 1, end);
                        if (originalSigB64.equals("null") || originalSigB64.isEmpty()) {
                            originalSigB64 = null;
                        }
                    }
                }
            }
            Log.i(TAG, "配置已加载: sigBypassLevel=" +
                json.substring(json.indexOf("\"sigBypassLevel\"") + 17, json.indexOf(",", json.indexOf("\"sigBypassLevel\""))));
        } catch (Exception e) {
            Log.w(TAG, "无法加载配置: " + e.getMessage());
        }
    }

    // ==================== PackageInfo.CREATOR 代理 ====================

    private static void hookPackageInfoCreator() {
        try {
            Field creatorField = PackageInfo.class.getField("CREATOR");
            @SuppressWarnings("unchecked")
            Parcelable.Creator<PackageInfo> original = (Parcelable.Creator<PackageInfo>) creatorField.get(null);

            Parcelable.Creator<PackageInfo> proxy = new Parcelable.Creator<PackageInfo>() {
                @Override
                public PackageInfo createFromParcel(Parcel source) {
                    int pos = source.dataPosition();
                    PackageInfo pi = original.createFromParcel(source);
                    if (pi != null) fixSignature(pi);
                    return pi;
                }
                @Override
                public PackageInfo[] newArray(int size) {
                    return original.newArray(size);
                }
            };

            // 替换静态字段
            makeFieldAccessible(creatorField);
            creatorField.set(null, proxy);
            Log.i(TAG, "✓ PackageInfo.CREATOR 已代理");
        } catch (Throwable e) {
            Log.w(TAG, "✗ PackageInfo.CREATOR 代理失败: " + e.getMessage());
        }
    }

    // ==================== IPackageManager 动态代理 ====================

    private static void hookIPackageManager() {
        try {
            // 获取 ServiceManager.getService("package")
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getDeclaredMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "package");

            // 获取 IPackageManager.Stub 接口
            String stubName = "android.content.pm.IPackageManager$Stub";
            Class<?> stub = Class.forName(stubName);
            Method asInterface = stub.getMethod("asInterface", IBinder.class);
            Object originalPM = asInterface.invoke(null, binder);

            Class<?> iPM = Class.forName("android.content.pm.IPackageManager");

            // 动态代理
            Object proxy = Proxy.newProxyInstance(
                iPM.getClassLoader(),
                new Class<?>[]{iPM},
                new PMInvocationHandler(originalPM)
            );

            // 替换 ServiceManager 缓存中的 binder
            hookServiceManagerCache("package", proxy);

            Log.i(TAG, "✓ IPackageManager 已代理");
        } catch (Throwable e) {
            Log.w(TAG, "✗ IPackageManager 代理失败: " + e.getMessage());
        }
    }

    /**
     * 替换 ServiceManager 中的缓存引用
     */
    private static void hookServiceManagerCache(String name, Object proxy) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            // 尝试 sCache 字段
            try {
                Field sCache = sm.getDeclaredField("sCache");
                makeFieldAccessible(sCache);
                @SuppressWarnings("unchecked")
                Map<String, IBinder> cache = (Map<String, IBinder>) sCache.get(null);
                if (cache != null) {
                    // 创建包装了 proxy 的 binder
                    IBinder proxyBinder = createBinderProxy(proxy);
                    cache.put(name, proxyBinder);
                    Log.i(TAG, "✓ ServiceManager.sCache 已更新");
                    return;
                }
            } catch (NoSuchFieldException ignored) {}

            // 尝试 sServiceManager 字段
            Field sSM = sm.getDeclaredField("sServiceManager");
            makeFieldAccessible(sSM);
            Object ssm = sSM.get(null);
            if (ssm != null) {
                // 尝试在 ServiceManager 对象上设置缓存
                for (Field f : ssm.getClass().getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        makeFieldAccessible(f);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cache = (Map<String, Object>) f.get(ssm);
                        if (cache != null) {
                            cache.put(name, proxy);
                            Log.i(TAG, "✓ ServiceManager 缓存已更新 (via " + f.getName() + ")");
                            return;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "✗ 无法更新 ServiceManager 缓存: " + e.getMessage());
        }
    }

    private static IBinder createBinderProxy(Object proxy) {
        // 动态代理已经实现了 IPackageManager 接口
        // 我们需要它看起来像个 IBinder
        return new IBinderProxy(proxy);
    }

    // ==================== 签名修复 ====================

    private static void fixSignature(PackageInfo pi) {
        if (pi == null || pi.packageName == null) return;
        if (originalSigB64 == null) return;

        try {
            byte[] sigBytes = Base64.decode(originalSigB64, Base64.DEFAULT);
            Signature replacement = new Signature(sigBytes);

            // 替换 signatures 数组
            if (pi.signatures != null && pi.signatures.length > 0) {
                pi.signatures[0] = replacement;
            } else {
                pi.signatures = new Signature[]{replacement};
            }

            // Android P+ 的 SigningInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.SigningInfo si = pi.signingInfo;
                if (si != null) {
                    try {
                        // 替换 apkContentsSigners
                        Signature[] contents = si.getApkContentsSigners();
                        if (contents != null && contents.length > 0) {
                            contents[0] = replacement;
                        }
                        // 替换 signingCertificateHistory
                        Signature[] history = si.getSigningCertificateHistory();
                        if (history != null && history.length > 0) {
                            history[0] = replacement;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "签名替换失败: " + e.getMessage());
        }
    }

    // ==================== IPackageManager 调用处理器 ====================

    private static class PMInvocationHandler implements InvocationHandler {
        private final Object originalPM;

        PMInvocationHandler(Object originalPM) { this.originalPM = originalPM; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(originalPM, args);

            // 拦截返回 PackageInfo 的方法
            if (result instanceof PackageInfo) {
                fixSignature((PackageInfo) result);
            }

            return result;
        }
    }

    // ==================== IBinder 代理 ====================

    private static class IBinderProxy implements IBinder {
        private final Object pmProxy;

        IBinderProxy(Object pmProxy) { this.pmProxy = pmProxy; }

        @Override
        public String getInterfaceDescriptor() { return "android.content.pm.IPackageManager"; }

        @Override
        public boolean isBinderAlive() { return true; }

        @Override
        public boolean pingBinder() { return true; }

        @Override
        public IInterface queryLocalInterface(String descriptor) {
            if (pmProxy instanceof IInterface) return (IInterface) pmProxy;
            return null;
        }

        @Override
        public void dump(java.io.FileDescriptor fd, String[] args) {}

        @Override
        public void dumpAsync(java.io.FileDescriptor fd, String[] args) {}

        @Override
        public boolean transact(int code, Parcel data, Parcel reply, int flags) {
            try {
                // 使用 IPackageManager.Stub 的 onTransact
                if (pmProxy != null) {
                    Class<?> stub = pmProxy.getClass();
                    Method transact = stub.getMethod("onTransact", int.class, Parcel.class, Parcel.class, int.class);
                    return (Boolean) transact.invoke(pmProxy, code, data, reply, flags);
                }
            } catch (Throwable e) {
                Log.w(TAG, "transact failed: " + e.getMessage());
            }
            return false;
        }

        @Override
        public void linkToDeath(DeathRecipient recipient, int flags) {}
        @Override
        public boolean unlinkToDeath(DeathRecipient recipient, int flags) { return false; }
    }

    // ==================== 反射工具 ====================

    private static void makeFieldAccessible(Field f) {
        f.setAccessible(true);
        // Android 9+ 隐藏 API 限制绕过
        try {
            Field modifiers = Field.class.getDeclaredField("accessFlags");
            modifiers.setAccessible(true);
            modifiers.setInt(f, f.getModifiers() & ~0x00000010); // 清除 final
        } catch (Throwable ignored) {}
    }
}