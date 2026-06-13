package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * 软降级兜底适配器。
 *
 * <p>
 * 当所有版本特定适配器都不可用时（例如未来的 Android 16+ 或未知的定制 ROM），
 * 此适配器会返回：
 * <ul>
 *   <li>{@link HookStrategy#SOFT_REFLECT} — 纯 Java 反射模式（无内存修改，零崩溃风险）</li>
 *   <li>{@code has* = false} 的能力探测（禁用所有需要 native 的特性）</li>
 * </ul>
 *
 * <p>
 * <b>注意</b>：这是最后的安全网，功能受限但保证不会崩溃。
 * 如果上层检测到使用的是 SoftFallbackAdapter，应向用户显示"兼容性降级模式"提示。
 */
public class SoftFallbackAdapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        // 接受任何 SDK 级别，但在 isApplicable 中显式返回 false
        return new int[]{1, Integer.MAX_VALUE};
    }

    @Override
    public boolean isApplicable() {
        // 仅在显式降级路径中使用，不参与自动选择
        return false;
    }

    @Override
    public VersionCapability probeCapability() {
        return VersionCapability.builder()
                .sdkInt(Build.VERSION.SDK_INT)
                .sdkRelease(Build.VERSION.RELEASE)
                .hasUserfaultfdGC(false)
                .hasMemfdRestriction(false)
                .hasExecMemRestriction(false)
                .hasAppComponentFactory(Build.VERSION.SDK_INT >= 29)
                .hasInMemoryDexClassLoader(Build.VERSION.SDK_INT >= 26)
                .hasScopedStorage(Build.VERSION.SDK_INT >= 29)
                .hasHiddenApiRestrictions(Build.VERSION.SDK_INT >= 28)
                .hasJitCompiler(Build.VERSION.SDK_INT >= 24)
                .addSignatureScheme("v1")
                .runtimeName("ART (Soft Fallback)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        return HookStrategy.SOFT_REFLECT;
    }

    @Override
    public String getVersionName() {
        return "Unknown / Future Android";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://source.android.com/";
    }

    @Override
    public boolean initialize() {
        // 软降级模式总是成功初始化（不依赖 native 资源）
        return true;
    }

    @Override
    public HiddenApiLevel getHiddenApiLevel() {
        // 保守假设最强限制
        return HiddenApiLevel.BLACKLIST_ONLY;
    }
}
