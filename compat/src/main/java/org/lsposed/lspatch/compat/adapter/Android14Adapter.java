package org.lsposed.lspatch.compat.adapter;

import android.os.Build;

/**
 * Android 14 (Upside Down Cake, API 34) 版本适配器。
 *
 * <h3>版本特性与限制</h3>
 * <ul>
 *   <li>严格的 memfd_create() 限制：memfd 必须标记为 MFD_EXEC 或 MFD_NOEXEC_SCAN</li>
 *   <li>execmem SELinux 限制：不可直接通过 mmap 分配可执行内存</li>
 *   <li>ART：引入 ART APEX 模块化升级（art_apex）</li>
 *   <li>Target SDK 强制 >= 34 时，对 hidden API 的访问限制更强</li>
 *   <li>Foreground Service Types（前台服务类型清单）</li>
 *   <li>数据同步 Framework（Data Sync Framework）</li>
 *   <li>部分照片访问（Photo Picker）权限细化</li>
 *   <li>应用 hibernation 管理增强</li>
 * </ul>
 *
 * <h3>Hook 策略</h3>
 * 推荐：LSPLANT_V2 → 降级到 DEX_PILOT → 最终 SOFT_REFLECT
 *
 * <h3>文档</h3>
 * <a href="https://developer.android.com/about/versions/14">
 * Android 14 功能和 API
 * </a>
 */
public class Android14Adapter extends VersionAdapter {

    @Override
    public int[] getTargetSdkRange() {
        return new int[]{34, 34};
    }

    @Override
    public VersionCapability probeCapability() {
        // Android 14 引入 memfd + execmem 限制
        // 检测实际是否启用：读取 /proc/sys/vm/mmap_min_addr
        // 或检查是否存在 libmemfd_hook.so
        boolean hasMemfdRestriction = Build.VERSION.SDK_INT >= 34;
        boolean hasExecMemRestriction = Build.VERSION.SDK_INT >= 34;

        return VersionCapability.builder()
                .sdkInt(Build.VERSION.SDK_INT)
                .sdkRelease(Build.VERSION.RELEASE)
                .hasUserfaultfdGC(true)
                .hasMemfdRestriction(hasMemfdRestriction)
                .hasExecMemRestriction(hasExecMemRestriction)
                .hasAppComponentFactory(true)
                .hasInMemoryDexClassLoader(true)
                .hasScopedStorage(true)
                .hasHiddenApiRestrictions(true)
                .hasJitCompiler(true)
                .addSignatureScheme("v1")
                .addSignatureScheme("v2")
                .addSignatureScheme("v3")
                .addSignatureScheme("v4")
                .runtimeName("ART (Upside Down Cake)")
                .build();
    }

    @Override
    public HookStrategy applyHookStrategy() {
        // Android 14：优先 lsplant V2，若失败降级到 Dex Pilot
        return HookStrategy.LSPLANT_V2;
    }

    @Override
    public String getVersionName() {
        return "Android 14 (Upside Down Cake)";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://developer.android.com/about/versions/14";
    }

    /**
     * Android 14 的降级策略：当 Inline Hook 因 execmem 限制失败时，
     * 推荐使用 Dex Pilot（基于 JIT 编译层的 Hook）。
     *
     * @return 降级策略
     */
    public HookStrategy getFallbackStrategy() {
        return HookStrategy.DEX_PILOT;
    }
}
