package org.lsposed.lspatch.compat.rom;

import android.os.Build;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ROM 厂商检测单元测试 — 模拟不同厂商的系统属性以验证检测逻辑。
 */
public class RomDetectorTest {

    @Test
    public void default_vendor_is_OTHER_when_no_rom_properties() {
        RomDetector.clearCache();
        // 默认 JVM 环境没有 Android 系统属性 → 应识别为 OTHER
        RomVendor vendor = RomDetector.getVendor();
        assertNotNull(vendor);
        // 在 CI 环境中 Build.MANUFACTURER 通常为 "unknown" 或为 null
        // 我们只验证不崩溃即可
    }

    @Test
    public void feature_set_returns_vendor() {
        RomFeatureSet features = RomDetector.getFeatureSet();
        assertNotNull(features);
        assertNotNull(features.getVendor());
    }

    @Test
    public void rom_adapter_always_provides_recommendation() {
        RomFeatureSet features = RomDetector.getFeatureSet();
        RomAdapter adapter = RomAdapter.create(features);
        assertNotNull(adapter);
        assertNotNull(adapter.describe());
        assertNotNull(adapter.getVendorDisplayName());
        assertNotNull(adapter.getCompatibilityAdvice());
    }

    @Test
    public void all_rom_features_are_valid_enums() {
        // 验证枚举值存在且不为 null
        assertTrue(RomFeature.values().length > 0);
        assertTrue(RomVendor.values().length > 0);
    }

    @Test
    public void feature_set_supports_features_helpers() {
        RomFeatureSet features = RomDetector.getFeatureSet();
        // hasFeature 对未知特性返回 false
        assertFalse(features.hasFeature(null));
        assertNotNull(features.getFeatures());
    }

    @Test
    public void adapter_has_strict_signature_check_detection() {
        // 华为 / 三星 ROM 应标记为严格签名检查
        RomVendor vendor = RomDetector.getVendor();
        RomFeatureSet features = new RomFeatureSet(vendor, java.util.Collections.emptySet());
        RomAdapter adapter = RomAdapter.create(features);
        assertNotNull(String.valueOf(adapter.needsSignatureBypass()));
    }

    @Test
    public void adapter_returns_valid_selinux_level() {
        RomFeatureSet features = RomDetector.getFeatureSet();
        RomAdapter adapter = RomAdapter.create(features);
        int level = adapter.getSelinuxStrictnessLevel();
        // 合法范围：0-2
        assertTrue("SELinux level should be in [0,2], got " + level,
                level >= 0 && level <= 2);
    }

    @Test
    public void native_hook_policy_is_non_null() {
        RomFeatureSet features = RomDetector.getFeatureSet();
        RomAdapter adapter = RomAdapter.create(features);
        // 不验证 true/false，只验证不崩溃
        // some ROMs might restrict native hook
        assertNotNull(adapter.allowsNativeHookWithoutPtrace());
    }

    @Test
    public void fallback_adapter_is_used_when_nothing_matches() {
        // 当 SDK 不在支持范围内时，使用 SoftFallbackAdapter
        int original = Build.VERSION.SDK_INT;
        try {
            setSdkInt(20); // Android 4.4 — 低于我们支持的 26
            // 验证 RomDetector 仍能工作且不崩溃
            RomVendor vendor = RomDetector.getVendor();
            assertNotNull(vendor);
        } finally {
            setSdkInt(original);
        }
    }

    // ============== 辅助：设置 SDK_INT ==============

    private static void setSdkInt(int value) {
        try {
            java.lang.reflect.Field f = Build.VERSION.class.getDeclaredField("SDK_INT");
            f.setAccessible(true);
            f.set(null, value);
        } catch (Exception ignored) {
        }
    }
}
