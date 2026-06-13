package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * Android 10 (Q, API 29) 版本适配器。
 *
 * <h3>版本特性</h3>
 * <ul>
 *   <li>引入 {@code AppComponentFactory} — 可在 {@code AndroidManifest.xml} 中声明</li>
 *   <li>Scoped Storage（分区存储）</li>
 *   <li>ART：优化的 JIT 代码缓存、Profile-guided compilation 更广泛</li>
 *   <li>Hidden API：浅灰名单完全分离为轻灰名单 + 黑名单</li>
 *   <li>Activity 生命周期改进</li>
 *   <li>APK Signature Scheme v3（升级支持）</li>
 * </ul>
 *
 * <h3>文档</h3>
 * <a href="https://developer.android.com/about/versions/10">
 * Android 10 功能和 API
 * </a>
 */
public class Android10Adapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        return new int[]{29, 29};
    }

    @Override
    public VersionCapability probeCapability() {
        // 检测是否启用 JIT
        boolean jitEnabled = "true".equals(getSystemProperty("dalvik.vm.usejit", "true"));

        return VersionCapability.builder()
                .sdkInt(Build.VERSION.SDK_INT)
                .sdkRelease(Build.VERSION.RELEASE)
                .hasUserfaultfdGC(false)
                .hasMemfdRestriction(false)
                .hasExecMemRestriction(false)
                .hasAppComponentFactory(true)
                .hasInMemoryDexClassLoader(true)
                .hasScopedStorage(true)
                .hasHiddenApiRestrictions(true)
                .hasJitCompiler(jitEnabled)
                .addSignatureScheme("v1")
                .addSignatureScheme("v2")
                .addSignatureScheme("v3")
                .runtimeName("ART (Q)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        // Android 10：ART 结构稳定，ART Inline Hook 推荐
        return HookStrategy.ART_INLINE;
    }

    @Override
    public String getVersionName() {
        return "Android 10";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://developer.android.com/about/versions/10";
    }
}
