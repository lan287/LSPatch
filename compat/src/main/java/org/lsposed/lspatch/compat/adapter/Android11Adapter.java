package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * Android 11 (R, API 30) 版本适配器。
 *
 * <h3>版本特性</h3>
 * <ul>
 *   <li>ART：全面启用 userfaultfd 机制管理 GC 页面迁移</li>
 *   <li>Hidden API：严格黑名单模式，浅灰名单限制大幅收紧</li>
 *   <li>Scoped Storage：强制（此前 Android 10 可通过 requestLegacyExternalStorage 绕过）</li>
 *   <li>APK Signature Scheme v4（增量签名验证）</li>
 *   <li>Package visibility filtering</li>
 *   <li>One-time permissions（一次性权限）</li>
 * </ul>
 *
 * <h3>文档</h3>
 * <a href="https://developer.android.com/about/versions/11">
 * Android 11 功能和 API
 * </a>
 */
public class Android11Adapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        return new int[]{30, 30};
    }

    @Override
    public VersionCapability probeCapability() {
        // Android 11 已启用 userfaultfd GC
        boolean useUffd = systemPropertyEquals("dalvik.vm.usejitprofiles", "true") ||
                !getSystemProperty("ro.art.vm.options", "").isEmpty();

        return VersionCapability.builder()
                .sdkInt(Build.VERSION.SDK_INT)
                .sdkRelease(Build.VERSION.RELEASE)
                .hasUserfaultfdGC(true)
                .hasMemfdRestriction(false)
                .hasExecMemRestriction(false)
                .hasAppComponentFactory(true)
                .hasInMemoryDexClassLoader(true)
                .hasScopedStorage(true)
                .hasHiddenApiRestrictions(true)
                .hasJitCompiler(true)
                .addSignatureScheme("v1")
                .addSignatureScheme("v2")
                .addSignatureScheme("v3")
                .addSignatureScheme("v4")
                .runtimeName("ART (R with userfaultfd)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        // Android 11：userfaultfd GC 引入，ART Inline Hook 仍稳定
        return HookStrategy.ART_INLINE;
    }

    @Override
    public String getVersionName() {
        return "Android 11";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://developer.android.com/about/versions/11";
    }
}
