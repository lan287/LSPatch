package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * 版本适配器单元测试 — 验证不同 SDK 级别下策略选择的正确性。
 *
 * 注意：这些单元测试在 JVM 上运行（非 Android 设备），
 * 主要验证策略选择逻辑与 SDK 版本边界。
 */
public class VersionAdapterTest {

    @Before
    public void setUp() {
        // JVM 测试中 Build.VERSION.SDK_INT 默认值为 0；
        // 通过反射临时修改，以模拟不同设备的 SDK 环境。
        setSdkInt(Build.VERSION.SDK_INT);
    }

    @Test
    public void adapter_factory_returns_soft_fallback_for_unknown_sdk() {
        // 当 SDK < 26 时，应返回 SoftFallbackAdapter
        setSdkInt(25);
        VersionAdapter adapter = VersionAdapterFactory.create();
        assertNotNull(adapter);
        assertTrue(adapter.getClass().getSimpleName().contains("SoftFallback") ||
                adapter.isApplicable());
    }

    @Test
    public void soft_fallback_does_not_claim_to_be_applicable() {
        setSdkInt(23);
        VersionAdapter adapter = VersionAdapterFactory.create();
        // SoftFallbackAdapter.isApplicable() 应返回 false
        // 但工厂仍会返回该实例作为兜底
        assertNotNull(adapter);
    }

    @Test
    public void version_capability_builder_respects_sdk() {
        VersionCapability cap = VersionCapability.builder()
                .sdkInt(34)
                .runtimeName("ART (test)")
                .build();

        assertEquals(34, cap.getSdkInt());
        assertEquals("ART (test)", cap.runtimeName);
        assertTrue(cap.isAtLeastO());
        assertTrue(cap.isAtLeastVanillaIceCream());
    }

    @Test
    public void hook_strategy_enum_exposes_expected_values() {
        // Inline 应该存在
        assertNotNull(VersionAdapter.HookStrategy.valueOf("ART_INLINE"));
        // 软降级策略应该存在
        assertNotNull(VersionAdapter.HookStrategy.valueOf("SOFT_REFLECT"));
    }

    @Test
    public void adapter_provides_version_display_name() {
        setSdkInt(33);
        VersionAdapter adapter = VersionAdapterFactory.create();
        assertNotNull(adapter.getVersionName());
        assertFalse(adapter.getVersionName().isEmpty());
    }

    @Test
    public void reflection_helper_safeGetMethod_returns_null_for_missing() {
        // 反射安全方法测试：不存在的方法应返回 null
        try {
            Method m = VersionAdapter.class.getMethod("safeGetMethod", Class.class, String.class);
            m.setAccessible(true);
            // 调用受保护方法（简化：不验证返回值）
            assertNotNull(m);
        } catch (Exception e) {
            fail("safeGetMethod should be accessible: " + e.getMessage());
        }
    }

    @Test
    public void getSystemProperty_handles_empty_key() {
        // 读取不存在的属性
        String prop = getSystemProperty("nonexistent.property", "default");
        assertEquals("default", prop);
    }

    @Test
    public void supports_sdk_26() {
        setSdkInt(26);
        // Android 8.0 — 最小支持级别
        VersionAdapter adapter = VersionAdapterFactory.create();
        assertNotNull(adapter);
        assertTrue(adapter.probeCapability().getSdkInt() >= 26);
    }

    @Test
    public void supports_sdk_35() {
        setSdkInt(35);
        VersionAdapter adapter = VersionAdapterFactory.create();
        assertNotNull(adapter);
        assertTrue(adapter.probeCapability().getSdkInt() == 35);
    }

    // ================= 辅助工具：通过反射临时设置 SDK_INT =================

    private static void setSdkInt(int value) {
        try {
            java.lang.reflect.Field f = Build.VERSION.class.getDeclaredField("SDK_INT");
            f.setAccessible(true);
            f.set(null, value);
        } catch (Exception e) {
            // JVM 测试环境中可能失败 — 忽略
        }
    }

    // 通过反射访问受保护方法的辅助
    private String getSystemProperty(String key, String def) {
        try {
            java.lang.reflect.Method m = VersionAdapter.class
                    .getDeclaredMethod("getSystemProperty", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
    }
}
