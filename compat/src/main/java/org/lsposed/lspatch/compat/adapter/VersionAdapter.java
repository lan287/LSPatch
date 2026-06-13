package org.lsposed.lspatch.compat.adapter;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Android 版本适配器 — 策略模式的核心抽象。
 *
 * <p>
 * 每个 Android 版本实现一个具体的 {@link VersionAdapter} 子类，
 * 通过 {@link VersionAdapterFactory} 按运行时 SDK 级别选择。
 *
 * <p>
 * <b>文档参考</b>：
 * <a href="https://developer.android.com/about/versions">Android 各版本说明</a>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>子类重写 {@link #getTargetSdkRange()} 声明适用范围</li>
 *   <li>子类重写 {@link #probeCapability()} 探测该版本特有能力</li>
 *   <li>子类通过 {@link #applyHookStrategy()} 返回该版本推荐的 Hook 策略</li>
 *   <li>所有反射操作通过安全工具方法 {@link #safeGetMethod(Class, String, Class[])} 执行</li>
 * </ul>
 */
public abstract class VersionAdapter {

    private static final String TAG = "LSPatch-VersionAdapter";

    /**
     * Hook 策略枚举。
     */
    public enum HookStrategy {
        /** 纯 ART Inline Hook（libart.so 指令替换） */
        ART_INLINE,
        /** lsplant V2 样式（支持 Android 12+） */
        LSPLANT_V2,
        /** Dex / JIT 编译层 Hook（Android 11+ ART 代码缓存重写） */
        DEX_PILOT,
        /** 入口点替换（仅替换 ArtMethod.entry_point_from_quick_compiled_code） */
        ENTRYPOINT_REPLACE,
        /** 纯 Java 反射降级（无内存修改） */
        SOFT_REFLECT,
    }

    /**
     * Hidden API 限制级别。
     */
    public enum HiddenApiLevel {
        /** Android 9 以下：无限制（或警告模式） */
        NONE_OR_WARN,
        /** Android 9：浅灰名单 + 黑名单混合 */
        DARK_GRAY_AND_BLACK,
        /** Android 10+：严格的黑名单 */
        BLACKLIST_ONLY,
    }

    // ===== 抽象方法 =====

    /**
     * 返回此适配器适用的 SDK 范围（闭区间）。
     *
     * @return [minSdk, maxSdk]
     */
    public abstract int[] getTargetSdkRange();

    /**
     * 探测当前设备的版本能力。
     *
     * @return 能力描述对象
     */
    public abstract VersionCapability probeCapability();

    /**
     * 返回此版本推荐的 Hook 策略。
     *
     * @return 策略枚举
     */
    public abstract HookStrategy applyHookStrategy();

    /**
     * 版本中文名。
     */
    public abstract String getVersionName();

    /**
     * 该版本的官方文档链接（用于参考/调试）。
     */
    public abstract String getDocumentationUrl();

    // ===== 公共方法 =====

    /**
     * 判断此适配器是否适用于当前设备。
     */
    public boolean isApplicable() {
        int[] range = getTargetSdkRange();
        return Build.VERSION.SDK_INT >= range[0] && Build.VERSION.SDK_INT <= range[1];
    }

    /**
     * 执行版本特定的初始化。
     *
     * @return 是否成功；若返回 false，工厂将尝试下一个降级适配器。
     */
    public boolean initialize() {
        Log.i(TAG, "Initializing adapter: " + getClass().getSimpleName() +
                " for " + getVersionName() + " (SDK " + getTargetSdkRange()[0] + ")");
        return true;
    }

    /**
     * 返回该版本的 Hidden API 限制级别。
     */
    public HiddenApiLevel getHiddenApiLevel() {
        if (Build.VERSION.SDK_INT >= 29) {
            return HiddenApiLevel.BLACKLIST_ONLY;
        } else if (Build.VERSION.SDK_INT == 28) {
            return HiddenApiLevel.DARK_GRAY_AND_BLACK;
        }
        return HiddenApiLevel.NONE_OR_WARN;
    }

    /**
     * 释放资源。
     */
    public void dispose() {
    }

    @Override
    public String toString() {
        int[] range = getTargetSdkRange();
        return String.format(Locale.ROOT,
                "%s{sdk=[%d..%d], version=%s, strategy=%s}",
                getClass().getSimpleName(), range[0], range[1],
                getVersionName(), applyHookStrategy());
    }

    // ===== 保护工具方法 =====

    /**
     * 安全反射：获取方法引用。
     */
    protected Method safeGetMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            Method m = clazz.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "safeGetMethod: " + clazz.getName() + "#" + name + " not found");
            return null;
        }
    }

    /**
     * 安全反射：调用静态方法。
     */
    @SuppressWarnings("unchecked")
    protected <T> T safeCallStatic(Method m, Object... args) {
        if (m == null) return null;
        try {
            return (T) m.invoke(null, args);
        } catch (Exception e) {
            Log.w(TAG, "safeCallStatic: " + m.getName() + " failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 读取系统属性（安全封装）。
     */
    protected String getSystemProperty(String key, String def) {
        try {
            Class<?> systemProps = Class.forName("android.os.SystemProperties");
            Method get = systemProps.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 检测某个系统属性是否存在并等于指定值。
     */
    protected boolean systemPropertyEquals(String key, String expected) {
        return expected.equals(getSystemProperty(key, ""));
    }

    /**
     * 在 dalvik.vm.* 属性中查找。
     */
    protected boolean hasDalvikVmProperty(String suffix) {
        String v = getSystemProperty("dalvik.vm." + suffix, "");
        return v != null && !v.isEmpty();
    }
}
