package org.lsposed.lspatch.compat.adapter;

import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本适配器工厂。
 *
 * <p>
 * 按优先级遍历注册的适配器：Android 15 → 14 → 13 → 12 → 11 → 10 → 9 → 8。
 * 选择第一个通过 {@link VersionAdapter#isApplicable()} 的适配器。
 * 如果所有适配器初始化失败，则回退到 {@link SoftFallbackAdapter}。
 *
 * <h3>用法示例</h3>
 * <pre>{@code
 * VersionAdapter adapter = VersionAdapterFactory.create();
 * VersionCapability capability = adapter.probeCapability();
 * HookStrategy strategy = adapter.applyHookStrategy();
 * Log.i("LSPatch", "Using adapter: " + adapter);
 * }</pre>
 */
public final class VersionAdapterFactory {

    private static final String TAG = "LSPatch-VersionFactory";

    /** 已注册的适配器列表（按版本从高到低排序）。 */
    private static final List<Class<? extends VersionAdapter>> REGISTRY = new ArrayList<>();

    static {
        // 注册顺序：从高版本到低版本
        REGISTRY.add(Android15Adapter.class);
        REGISTRY.add(Android14Adapter.class);
        REGISTRY.add(Android13Adapter.class);
        REGISTRY.add(Android12Adapter.class);
        REGISTRY.add(Android11Adapter.class);
        REGISTRY.add(Android10Adapter.class);
        REGISTRY.add(Android9Adapter.class);
        REGISTRY.add(Android8Adapter.class);
    }

    private VersionAdapterFactory() { /* 禁止实例化 */ }

    /**
     * 创建适用于当前设备的最佳适配器。
     *
     * @return 已初始化的适配器实例，永不返回 null
     */
    public static VersionAdapter create() {
        Log.i(TAG, "Creating adapter for SDK " + Build.VERSION.SDK_INT);

        for (Class<? extends VersionAdapter> clazz : REGISTRY) {
            try {
                VersionAdapter adapter = clazz.getDeclaredConstructor().newInstance();
                if (adapter.isApplicable()) {
                    if (adapter.initialize()) {
                        Log.i(TAG, "Selected adapter: " + adapter.getVersionName() +
                                " (" + clazz.getSimpleName() + ")");
                        return adapter;
                    } else {
                        Log.w(TAG, adapter.getVersionName() + " initialize() returned false, trying next");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to instantiate " + clazz.getSimpleName() + ": " + e.getMessage());
            }
        }

        // 所有适配器失败 → 回退到软适配
        Log.w(TAG, "All version adapters failed — falling back to SoftFallbackAdapter");
        return new SoftFallbackAdapter();
    }

    /**
     * 创建但不初始化；仅根据 SDK 级别选择。
     * 用于单元测试或不需要版本检测的场景。
     *
     * @return 未初始化的适配器实例
     */
    public static VersionAdapter createBySdk(int sdkInt) {
        for (Class<? extends VersionAdapter> clazz : REGISTRY) {
            try {
                VersionAdapter adapter = clazz.getDeclaredConstructor().newInstance();
                int[] range = adapter.getTargetSdkRange();
                if (sdkInt >= range[0] && sdkInt <= range[1]) {
                    return adapter;
                }
            } catch (Exception ignored) {
            }
        }
        return new SoftFallbackAdapter();
    }

    /**
     * 注册一个自定义适配器（用于测试或扩展）。
     *
     * @param adapterClass 自定义适配器类
     * @param priority 优先级（true = 插入到列表最前面，false = 追加到列表末尾）
     */
    public static void registerCustom(Class<? extends VersionAdapter> adapterClass, boolean priority) {
        if (priority) {
            REGISTRY.add(0, adapterClass);
        } else {
            REGISTRY.add(adapterClass);
        }
        Log.i(TAG, "Registered custom adapter: " + adapterClass.getSimpleName());
    }

    /**
     * 返回所有已注册适配器的数量。
     */
    public static int getRegisteredCount() {
        return REGISTRY.size();
    }
}
