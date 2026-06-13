package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * Android 12 (Snow Cone / S, API 31-32) 版本适配器。
 *
 * <h3>版本特性</h3>
 * <ul>
 *   <li>ART：lsplant V2 兼容，JIT 编译路径变化</li>
 *   <li>Splash Screen API（启动画面动画）</li>
 *   <li>App Hibernation（休眠应用）</li>
 *   <li>Scrolled Storage：更严格的限制</li>
 *   <li>隐私增强：APP_PRESENTER 权限</li>
 *   <li>为 12L（API 32）提供兼容性</li>
 * </ul>
 *
 * <h3>文档</h3>
 * <a href="https://developer.android.com/about/versions/12">
 * Android 12 功能和 API
 * </a>
 */
public class Android12Adapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        return new int[]{31, 32};
    }

    @Override
    public VersionCapability probeCapability() {
        // Android 12 对 ART 做了大量优化
        boolean useJitProfiles = "true".equals(getSystemProperty("dalvik.vm.usejitprofiles", "true"));

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
                .hasJitCompiler(useJitProfiles)
                .addSignatureScheme("v1")
                .addSignatureScheme("v2")
                .addSignatureScheme("v3")
                .addSignatureScheme("v4")
                .runtimeName("ART (S)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        // Android 12+ 推荐使用 lsplant V2 策略
        return HookStrategy.LSPLANT_V2;
    }

    @Override
    public String getVersionName() {
        return "Android 12 / 12L";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://developer.android.com/about/versions/12";
    }

    @Override
    public boolean isApplicable() {
        return Build.VERSION.SDK_INT == 31 || Build.VERSION.SDK_INT == 32;
    }
}
