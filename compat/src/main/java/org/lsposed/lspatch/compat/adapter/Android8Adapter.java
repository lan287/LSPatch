package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * Android 8.0 / 8.1 (Oreo, API 26-27) 版本适配器。
 *
 * <h3>版本特性</h3>
 * <ul>
 *   <li>传统 ART GC（无 userfaultfd）</li>
 *   <li>隐藏 API 限制为警告模式（实际为灰名单轻量限制）</li>
 *   <li>引入 {@link dalvik.system.InMemoryDexClassLoader}</li>
 *   <li>签名方案：V1 + V2</li>
 *   <li>无 AppComponentFactory</li>
 *   <li>无 Scoped Storage</li>
 * </ul>
 *
 * <h3>文档</h3>
 * <a href="https://developer.android.com/about/versions/oreo/android-8.0">
 * Android 8.0 功能和 API
 * </a>
 */
public class Android8Adapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        return new int[]{26, 27};
    }

    @Override
    public VersionCapability probeCapability() {
        return VersionCapability.builder()
                .sdkInt(Build.VERSION.SDK_INT)
                .sdkRelease(Build.VERSION.RELEASE)
                .hasUserfaultfdGC(false)
                .hasMemfdRestriction(false)
                .hasExecMemRestriction(false)
                .hasAppComponentFactory(false)
                .hasInMemoryDexClassLoader(true)
                .hasScopedStorage(false)
                .hasHiddenApiRestrictions(false)
                .hasJitCompiler(true)
                .addSignatureScheme("v1")
                .addSignatureScheme("v2")
                .runtimeName("ART (Oreo)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        // Android 8：ART 结构相对简单，Inline Hook 稳定
        return HookStrategy.ART_INLINE;
    }

    @Override
    public String getVersionName() {
        return "Oreo";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://developer.android.com/about/versions/oreo/android-8.0";
    }

    @Override
    public HiddenApiLevel getHiddenApiLevel() {
        return HiddenApiLevel.NONE_OR_WARN;
    }
}
