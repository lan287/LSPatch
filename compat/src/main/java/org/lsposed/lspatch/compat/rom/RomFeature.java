package org.lsposed.lspatch.compat.rom;

/**
 * ROM 特性枚举。
 *
 * <p>
 * 用于描述不同厂商 ROM 的特性差异：
 * <ul>
 *   <li>SELinux 策略差异（华为/HarmonyOS 更严格）</li>
 *   <li>隐藏 API 豁免差异（MIUI 有时放宽）</li>
 *   <li>特殊系统组件（KNOX / 小米 Guard Provider）</li>
 *   <li>ART 版本差异（部分厂商会自定义 ART）</li>
 * </ul>
 */
public enum RomFeature {

    // ============ 通用 Android 特性 ============

    /** Android 10+ 分区存储强制启用 */
    SCOPED_STORAGE,

    /** Android 11+ 隐藏 API 仅黑名单模式 */
    HIDDEN_API_BLACKLIST_ONLY,

    /** Android 14+ memfd + execmem 限制 */
    MEMFD_EXEC_RESTRICTION,

    /** 隐藏 API 可访问（通过 hidden_api_blacklist_exemptions） */
    HIDDEN_API_ACCESSIBLE,

    // ============ 小米 / HyperOS ============

    /** MIUI / HyperOS — ContentProvider 用于拦截应用启动 */
    XIAOMI_GUARD_PROVIDER,

    /** 是否为 HyperOS（而非旧版 MIUI） */
    XIAOMI_HYPEROS,

    // ============ 华为 / 荣耀 ============

    /** 华为 EMUI（传统华为 ROM） */
    HUAWEI_EMUI,

    /** 华为 HarmonyOS（OpenHarmony 兼容层） */
    HUAWEI_HARMONYOS,

    /** 华为定制 SELinux 策略（更严格） */
    HUAWEI_SPECIAL_SELINUX,

    /** 荣耀 Magic UI（独立品牌后的 ROM） */
    HUAWEI_MAGIC_UI,

    // ============ 三星 ============

    /** 三星 KNOX 安全平台（检测到 KNOX 意味着更严格的签名校验） */
    SAMSUNG_KNOX,

    /** 三星 One UI（基于 Android） */
    SAMSUNG_ONE_UI,

    // ============ OPPO / Realme / 一加 ============

    /** OPPO ColorOS */
    OPPO_COLOR_OS,

    // ============ vivo ============

    /** vivo OriginOS */
    VIVO_ORIGIN_OS,

    // ============ 魅族 ============

    /** 魅族 FlymeOS */
    MEIZU_FLYME,
}
