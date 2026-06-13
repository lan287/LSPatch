package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * Android 9 (Pie, API 28) 版本适配器。
 *
 * <h3>版本特性</h3>
 * <ul>
 *   <li>传统 ART GC（userfaultfd 原型但未启用）</li>
 *   <li>引入隐藏 API 限制：浅灰名单 + 黑名单双级模式</li>
 *   <li>APK Signature Scheme v3 支持（SDK 28 引入）</li>
 *   <li>ART AOT 编译器：dex2oat 升级为 vdex + .art 格式</li>
 *   <li>应用 standby buckets（分区待机）</li>
 * </ul>
 *
 * <h3>文档</h3>
 * <a href="https://developer.android.com/about/versions/pie/android-9.0">
 * Android 9 Pie 功能和 API
 * </a>
 */
public class Android9Adapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        return new int[]{28, 28};
    }

    @Override
    public VersionCapability probeCapability() {
        boolean hasHiddenApi = systemPropertyEquals("ro.build.version.sdk", "28");
        return VersionCapability.builder()
                .sdkInt(Build.VERSION.SDK_INT)
                .sdkRelease(Build.VERSION.RELEASE)
                .hasUserfaultfdGC(false)
                .hasMemfdRestriction(false)
                .hasExecMemRestriction(false)
                .hasAppComponentFactory(false)
                .hasInMemoryDexClassLoader(true)
                .hasScopedStorage(false)
                .hasHiddenApiRestrictions(hasHiddenApi)
                .hasJitCompiler(true)
                .addSignatureScheme("v1")
                .addSignatureScheme("v2")
                .addSignatureScheme("v3")
                .runtimeName("ART (Pie)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        // Android 9：隐藏 API 限制开始，但 inline hook 仍然稳定
        return HookStrategy.ART_INLINE;
    }

    @Override
    public String getVersionName() {
        return "Pie";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://developer.android.com/about/versions/pie/android-9.0";
    }
}
