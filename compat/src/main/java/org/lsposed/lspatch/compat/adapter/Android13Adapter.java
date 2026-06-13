package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * Android 13 (Tiramisu, API 33) 版本适配器。
 *
 * <h3>版本特性</h3>
 * <ul>
 *   <li>ART：lsplant V2 完全兼容，userfaultfd GC 稳定</li>
 *   <li>照片选择器（Photo Picker）</li>
 *   <li>通知权限：POST_NOTIFICATIONS 运行时权限</li>
 *   <li>应用特定语言偏好（Locale per-app）</li>
 *   <li>Scoped Storage：进一步收紧</li>
 *   <li>引入 {@code Context#createAttributionContext(String)}</li>
 * </ul>
 *
 * <h3>文档</h3>
 * <a href="https://developer.android.com/about/versions/13">
 * Android 13 功能和 API
 * </a>
 */
public class Android13Adapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        return new int[]{33, 33};
    }

    @Override
    public VersionCapability probeCapability() {
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
                .runtimeName("ART (Tiramisu)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        // Android 13：lsplant V2 策略最佳
        return HookStrategy.LSPLANT_V2;
    }

    @Override
    public String getVersionName() {
        return "Android 13 (Tiramisu)";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://developer.android.com/about/versions/13";
    }
}
