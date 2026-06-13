package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * Android 15 (Vanilla Ice Cream, API 35) 版本适配器。
 *
 * <h3>版本特性与限制</h3>
 * <ul>
 *   <li>APK Signature Scheme v4 自由格式（Freetag）</li>
 *   <li>ART APEX 35：新的 ART 版本标识</li>
 *   <li>更严格的 hidden API 隔离：JNI 层隐藏 API 检测</li>
 *   <li>编译系统：进一步限制基于反射的绕过手段</li>
 *   <li>文件系统加密：默认强制执行</li>
 *   <li>引入 {@code ActivityEmbedding} 官方替代（更严格约束）</li>
 *   <li>Data Sync Framework 增强版</li>
 * </ul>
 *
 * <h3>Hook 策略</h3>
 * 推荐：LSPLANT_V2（需要同时启用 memfd + execmem 绕过）
 * 降级链：ENTRYPOINT_REPLACE → DEX_PILOT → SOFT_REFLECT
 *
 * <h3>文档</h3>
 * <a href="https://developer.android.com/about/versions/15">
 * Android 15 功能和 API
 * </a>
 */
public class Android15Adapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        return new int[]{35, 35};
    }

    @Override
    public VersionCapability probeCapability() {
        // Android 15：memfd + execmem 限制是硬限制
        return VersionCapability.builder()
                .sdkInt(Build.VERSION.SDK_INT)
                .sdkRelease(Build.VERSION.RELEASE)
                .hasUserfaultfdGC(true)
                .hasMemfdRestriction(true)
                .hasExecMemRestriction(true)
                .hasAppComponentFactory(true)
                .hasInMemoryDexClassLoader(true)
                .hasScopedStorage(true)
                .hasHiddenApiRestrictions(true)
                .hasJitCompiler(true)
                .addSignatureScheme("v1")
                .addSignatureScheme("v2")
                .addSignatureScheme("v3")
                .addSignatureScheme("v4")
                .addSignatureScheme("v4-freetag")
                .runtimeName("ART (Vanilla Ice Cream, APEX 35)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        // Android 15：优先 LSPLANT_V2（lsplant）；如果 ART 结构探测失败，
        // 则退化为 ENTRYPOINT_REPLACE
        return HookStrategy.LSPLANT_V2;
    }

    @Override
    public String getVersionName() {
        return "Android 15 (Vanilla Ice Cream)";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://developer.android.com/about/versions/15";
    }

    /**
     * Android 15 的降级策略。
     *
     * @return 首选降级策略
     */
    public HookStrategy getFallbackStrategy() {
        return HookStrategy.ENTRYPOINT_REPLACE;
    }

    /**
     * 最终降级策略（纯 Java 反射 + DexClassLoader 热替换）。
     */
    public HookStrategy getFinalFallbackStrategy() {
        return HookStrategy.SOFT_REFLECT;
    }
}
