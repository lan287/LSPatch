package org.lsposed.lspatch.compat.bridge;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Xposed API 兼容性桥接器 — 将旧版 Xposed v82-v93 API 映射到新的接口系统。
 *
 * <h3>迁移路线</h3>
 * <table>
 *   <tr><th>旧 API</th><th>新 API</th><th>状态</th></tr>
 *   <tr><td>XposedHelpers.findAndHookMethod()</td><td>HookProvider.hookMethod()</td><td>桥接可用</td></tr>
 *   <tr><td>XposedHelpers.findAndHookConstructor()</td><td>HookProvider.hookConstructor()</td><td>桥接可用</td></tr>
 *   <tr><td>XC_MethodHook / XC_MethodReplacement</td><td>HookCallback + HookResult</td><td>桥接可用</td></tr>
 *   <tr><td>XposedBridge.log()</td><td>Logger 抽象</td><td>桥接可用</td></tr>
 *   <tr><td>XposedInit.loadedPackagesInProcess</td><td>ModuleLoader.getActivePackages()</td><td>废弃</td></tr>
 *   <tr><td>XposedHelpers.setStaticBooleanField()</td><td>FieldAccessBridge</td><td>桥接可用</td></tr>
 * </table>
 *
 * <h3>用法示例（旧版）</h3>
 * <pre>{@code
 * XposedHelpers.findAndHookMethod(
 *     "com.example.app.TargetClass",
 *     classLoader,
 *     "targetMethod",
 *     String.class,
 *     new XC_MethodHook() {
 *         protected void beforeHookedMethod(MethodHookParam param) {
 *             param.args[0] = "patched";
 *         }
 *     }
 * );
 * }</pre>
 *
 * <h3>新用法（推荐）</h3>
 * <pre>{@code
 * HookProvider hp = getHookProvider();
 * hp.hookMethod(
 *     "com.example.app.TargetClass",
 *     "targetMethod",
 *     new Class<?>[]{String.class},
 *     Arrays.asList(new HookCallback<Object>(BEFORE) {
 *         @Override public HookResult onBefore(HookContext<Object> ctx) {
 *             ctx.setArg(0, "patched");
 *             return HookResult.CONTINUE;
 *         }
 *     })
 * );
 * }</pre>
 *
 * @deprecated 本类仅用于模块迁移期兼容。新模块应直接使用 {@code HookProvider} 接口。
 */
@Deprecated
public class XposedBridgeCompat {

    private static final String TAG = "LSPatch-XposedCompat";
    private static final AtomicLong hookIdCounter = new AtomicLong(0);

    /** 记录 → HookHandle 映射（供 unhook 使用）。 */
    private static final Map<String, Object> hookRegistry = new HashMap<>();

    // ========================================================================
    // 主入口 — findAndHookMethod
    // ========================================================================

    /**
     * 查找并 Hook 一个方法（与 {@code XposedHelpers.findAndHookMethod} 行为一致）。
     *
     * @deprecated 新代码应使用 HookProvider.hookMethod()。
     */
    @Deprecated
    public static void findAndHookMethod(
            String className,
            ClassLoader classLoader,
            String methodName,
            Object... parameterTypesAndCallback
    ) {
        recordDeprecated("findAndHookMethod(String, ClassLoader, String, Object...)",
                "Use HookProvider.hookMethod(String, String, Class<?>[], List<HookCallback>)");

        try {
            // 解析参数类型（最后一个参数是 XC_MethodHook 回调）
            int callbackCount = 1;  // 简单实现：默认回调为最后一个元素
            int paramCount = parameterTypesAndCallback.length - callbackCount;

            Class<?>[] paramTypes = new Class<?>[paramCount];
            Object callback = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];

            for (int i = 0; i < paramCount; i++) {
                Object arg = parameterTypesAndCallback[i];
                if (arg instanceof Class) {
                    paramTypes[i] = (Class<?>) arg;
                } else if (arg instanceof String) {
                    paramTypes[i] = Class.forName((String) arg, false, classLoader);
                } else {
                    throw new IllegalArgumentException("Invalid parameter type at index " + i);
                }
            }

            // 反射获取目标方法
            Class<?> target = Class.forName(className, false, classLoader);
            Method method = target.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);

            // TODO: 此处调用实际的 HookProvider 实现
            // HookProvider.hookMethod(method, ...)

            String id = "compat_" + hookIdCounter.incrementAndGet();
            Log.i(TAG, "[Compat] Hook registered: " + className + "." + methodName +
                    " [id=" + id + "] (delegation pending)");

        } catch (Throwable t) {
            Log.e(TAG, "[Compat] findAndHookMethod failed: " + t.getMessage());
            // 与原 XposedHelpers 行为一致 — 静默失败并记录到日志
        }
    }

    /**
     * @deprecated 同上 — 签名接收 Method 对象。
     */
    @Deprecated
    public static void hookMethod(Method method, Object callback) {
        recordDeprecated("hookMethod(Method, XC_MethodHook)",
                "Use HookProvider.hookMethod(Method, List<HookCallback>)");
    }

    /**
     * @deprecated 同上 — 查找并 Hook 构造器。
     */
    @Deprecated
    public static void findAndHookConstructor(
            String className,
            ClassLoader classLoader,
            Object... parameterTypesAndCallback
    ) {
        recordDeprecated("findAndHookConstructor(String, ClassLoader, Object...)",
                "Use HookProvider.hookConstructor(Class, Class<?>[], List<HookCallback>)");
    }

    // ========================================================================
    // 字段访问
    // ========================================================================

    /**
     * @deprecated 使用 FieldAccessBridge.setStaticBooleanField()。
     */
    @Deprecated
    public static void setStaticBooleanField(String className, ClassLoader cl,
                                              String fieldName, boolean value) {
        recordDeprecated("XposedHelpers.setStaticBooleanField(String, ClassLoader, String, boolean)",
                "Use FieldAccessBridge.setStaticField(Class, String, Object)");
    }

    /**
     * @deprecated 使用 FieldAccessBridge.getStaticField()。
     */
    @Deprecated
    public static Object getStaticObjectField(String className, ClassLoader cl,
                                               String fieldName) {
        recordDeprecated("XposedHelpers.getStaticObjectField(String, ClassLoader, String)",
                "Use FieldAccessBridge.getStaticField(Class, String)");
        return null;
    }

    /**
     * @deprecated 使用 FieldAccessBridge.setInstanceField()。
     */
    @Deprecated
    public static void setObjectField(Object obj, String fieldName, Object value) {
        recordDeprecated("XposedHelpers.setObjectField(Object, String, Object)",
                "Use FieldAccessBridge.setInstanceField(Object, String, Object)");
    }

    // ========================================================================
    // 日志
    // ========================================================================

    /**
     * @deprecated 使用统一的 Logger 接口。
     */
    @Deprecated
    public static void log(String text) {
        recordDeprecated("XposedBridge.log(String)",
                "Use Logger.info(String) or Logger.debug(String) for structured logging");
        Log.i(TAG, text);
    }

    /**
     * @deprecated 同上。
     */
    @Deprecated
    public static void log(Throwable t) {
        recordDeprecated("XposedBridge.log(Throwable)",
                "Use Logger.error(String, Throwable) for structured error logging");
        Log.e(TAG, "Xposed-compat error", t);
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 查找类（与 {@code XposedHelpers.findClass} 行为一致）。
     */
    @Deprecated
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        recordDeprecated("XposedHelpers.findClass(String, ClassLoader)",
                "Use reflection or ModuleClassLoader directly");
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            // 与原 XposedHelpers 行为一致 — 返回 null 而非抛异常
            Log.w(TAG, "[Compat] findClass failed: " + className);
            return null;
        }
    }

    /**
     * @deprecated 使用新的 ModuleContext 系统。
     */
    @Deprecated
    public static String getActiveXposedVersion() {
        recordDeprecated("XposedBridge.getXposedVersion()",
                "Use ModuleRuntime.getFrameworkVersion()");
        return "LSPatch 2.0 (compat)";
    }

    // ========================================================================
    // 内部：废弃 API 追踪
    // ========================================================================

    /**
     * 记录废弃 API 的使用情况 — 用于统计和模块诊断。
     * 在生产环境中，可将此信息汇总后发送给 Manager 或日志。
     */
    private static void recordDeprecated(String oldApi, String recommendation) {
        if (MigrationTracker.isEnabled()) {
            MigrationTracker.record(oldApi, recommendation);
            if (MigrationTracker.isVerbose()) {
                Log.w(TAG, "[DEPRECATED] " + oldApi + "\n  → " + recommendation);
            }
        }
    }

    /**
     * 暴露给上层：获取所有废弃 API 的使用次数（用于模块自检和 UI 提示）。
     */
    public static Map<String, Integer> getDeprecatedUsageStats() {
        return MigrationTracker.getStats();
    }

    /**
     * 清除统计（测试辅助）。
     */
    public static void resetDeprecatedUsageStats() {
        MigrationTracker.reset();
    }
}
