package org.lsposed.lspatch.compat.rom;

/**
 * ROM 厂商枚举。
 *
 * <p>
 * 使用特性检测替代硬编码 ROM 判断：
 * 首先通过系统属性匹配特定 ROM 的标识字符，但不直接依赖具体型号字符串。
 */
public enum RomVendor {

    /** 小米 / Redmi / POCO (MIUI / HyperOS) */
    XIAOMI,

    /** 华为 / 荣耀 (EMUI / HarmonyOS / Magic UI) */
    HUAWEI,

    /** 三星 (One UI) */
    SAMSUNG,

    /** OPPO / OnePlus / Realme (ColorOS / OxygenOS / Realme UI) */
    OPPO,

    /** vivo / iQOO (OriginOS / Funtouch OS) */
    VIVO,

    /** 魅族 (FlymeOS) */
    MEIZU,

    /** 荣耀（独立品牌）*/
    HONOR,

    /** Google Pixel (Stock Android) */
    PIXEL,

    /** 摩托罗拉 (MyUX / Moto Actions) */
    MOTOROLA,

    /** 联想 (ZUI) */
    LENOVO,

    /** 一加（独立版本，非 OPPO ColorOS） */
    ONEPLUS,

    /** 其他/未知厂商 */
    OTHER,
}
