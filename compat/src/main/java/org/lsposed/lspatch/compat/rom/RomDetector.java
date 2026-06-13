package org.lsposed.lspatch.compat.rom;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * ROM 厂商探测器 — 使用特性检测而非硬编码字符串匹配。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>优先探测系统属性（ro.miui.ui.version.name / ro.build.version.emui 等）</li>
 *   <li>其次探测 Manifest 中的特殊组件声明（如小米的 ContentProvider 钩子）</li>
 *   <li>最后使用 Build.MANUFACTURER / BRAND 兜底（仅当上述匹配失败时）</li>
 *   <li>不依赖任何可能变化的 ROM 显示字符串（如 "HyperOS 2.0" vs "MIUI 15"）</li>
 * </ul>
 *
 * <h3>文档参考</h3>
 * <a href="https://developer.android.com/reference/android/os/Build">Build</a>
 */
public final class RomDetector {

    private static final String TAG = "LSPatch-RomDetector";

    /** 小米 / HyperOS — ro.miui.ui.version.code 或 ro.miui.internal.storage */
    private static final String[] XIAOMI_KEYS = {
            "ro.miui.ui.version.name",
            "ro.miui.ui.version.code",
            "ro.miui.version.code_time",
            "ro.hyperos.version.name",
    };

    /** 华为 / 荣耀 — ro.build.version.emui / ro.build.version.harmonyos */
    private static final String[] HUAWEI_KEYS = {
            "ro.build.version.emui",
            "ro.build.version.harmonyos",
            "ro.huawei.build.version.incremental",
            "ro.hwui.api_version",
    };

    /** 荣耀（独立后）— ro.magic.ui.version 或 ro.honor.deviceid */
    private static final String[] HONOR_KEYS = {
            "ro.magic.ui.version",
            "ro.honor.build.version",
    };

    /** 三星 — ro.build.PDA / ro.product_ship 或 ro.config.tima 等 */
    private static final String[] SAMSUNG_KEYS = {
            "ro.build.PDA",
            "ro.config.tima",
            "ro.config.timaversion",
    };

    /** OPPO / 一加 / Realme — ro.build.version.opporom / ro.rom.dolby.dax.support 等 */
    private static final String[] OPPO_KEYS = {
            "ro.build.version.opporom",
            "ro.product.oppo.brand",
            "ro.oppo.market.name",
    };

    /** vivo / iQOO — ro.vivo.os.version / ro.vivo.os.build.display.id */
    private static final String[] VIVO_KEYS = {
            "ro.vivo.os.version",
            "ro.vivo.os.build.display.id",
            "ro.vivo.os.versioncode",
    };

    /** 魅族 — ro.build.display.id 或 ro.meizu.product.model */
    private static final String[] MEIZU_KEYS = {
            "ro.meizu.product.model",
            "ro.meizu.flyme.version",
            "persist.sys.mz.custom",
    };

    /** 联想 — ro.lenovo.product.model 等 */
    private static final String[] LENOVO_KEYS = {
            "ro.lenovo.product.model",
            "persist.sys.lenovo.product",
    };

    /** 摩托罗拉 — ro.product.device.brand 等 */
    private static final String[] MOTOROLA_KEYS = {
            "ro.product.brand.moto",
            "ro.mot.build.customerid",
    };

    private static volatile RomVendor cachedVendor = null;
    private static volatile RomFeatureSet cachedFeatureSet = null;

    private RomDetector() { /* 禁止实例化 */ }

    /**
     * 获取当前设备的 ROM 厂商（带缓存）。
     *
     * @return 已识别的 ROM 厂商
     */
    public static RomVendor getVendor() {
        if (cachedVendor != null) return cachedVendor;

        synchronized (RomDetector.class) {
            if (cachedVendor != null) return cachedVendor;
            cachedVendor = detectVendor();
            return cachedVendor;
        }
    }

    /**
     * 获取 ROM 的特性集（带缓存）。
     */
    public static RomFeatureSet getFeatureSet() {
        if (cachedFeatureSet != null) return cachedFeatureSet;

        synchronized (RomDetector.class) {
            if (cachedFeatureSet != null) return cachedFeatureSet;
            cachedFeatureSet = detectFeatures(getVendor());
            return cachedFeatureSet;
        }
    }

    /**
     * 仅用于测试 — 清除缓存（单测时可覆盖）。
     */
    static void clearCache() {
        cachedVendor = null;
        cachedFeatureSet = null;
    }

    // =====================================================================
    // 检测实现
    // =====================================================================

    private static RomVendor detectVendor() {
        // 1. 优先级最高：小米 / 华为 / 荣耀 — 明确的系统属性
        if (hasAnySystemProperty(XIAOMI_KEYS)) return RomVendor.XIAOMI;
        if (hasAnySystemProperty(HONOR_KEYS)) return RomVendor.HONOR;
        if (hasAnySystemProperty(HUAWEI_KEYS)) return RomVendor.HUAWEI;
        if (hasAnySystemProperty(SAMSUNG_KEYS)) return RomVendor.SAMSUNG;
        if (hasAnySystemProperty(OPPO_KEYS)) return RomVendor.OPPO;
        if (hasAnySystemProperty(VIVO_KEYS)) return RomVendor.VIVO;
        if (hasAnySystemProperty(MEIZU_KEYS)) return RomVendor.MEIZU;
        if (hasAnySystemProperty(LENOVO_KEYS)) return RomVendor.LENOVO;
        if (hasAnySystemProperty(MOTOROLA_KEYS)) return RomVendor.MOTOROLA;

        // 2. 次要优先级：Build.MANUFACTURER / BRAND
        String manufacturer = (Build.MANUFACTURER != null ? Build.MANUFACTURER : "").toLowerCase();
        String brand = (Build.BRAND != null ? Build.BRAND : "").toLowerCase();
        String fingerprint = (Build.FINGERPRINT != null ? Build.FINGERPRINT : "").toLowerCase();

        if (manufacturer.contains("xiaomi") || brand.contains("xiaomi")) return RomVendor.XIAOMI;
        if (manufacturer.contains("huawei") || brand.contains("huawei")) return RomVendor.HUAWEI;
        if (manufacturer.contains("honor") || brand.contains("honor")) return RomVendor.HONOR;
        if (manufacturer.contains("samsung") || brand.contains("samsung")) return RomVendor.SAMSUNG;
        if (manufacturer.contains("oppo") || brand.contains("oppo")) return RomVendor.OPPO;
        if (manufacturer.contains("vivo") || brand.contains("vivo")) return RomVendor.VIVO;
        if (manufacturer.contains("meizu") || brand.contains("meizu")) return RomVendor.MEIZU;
        if (manufacturer.contains("lenovo") || brand.contains("lenovo")) return RomVendor.LENOVO;
        if (manufacturer.contains("motorola") || brand.contains("motorola") || manufacturer.contains("motorola")) return RomVendor.MOTOROLA;
        if (fingerprint.contains("pixel") || manufacturer.equals("google")) return RomVendor.PIXEL;

        // 3. 兜底
        Log.i(TAG, "Unknown ROM: manufacturer=" + Build.MANUFACTURER +
                " brand=" + Build.BRAND);
        return RomVendor.OTHER;
    }

    private static RomFeatureSet detectFeatures(RomVendor vendor) {
        Set<RomFeature> features = new HashSet<>();

        // 通用 Android 特性检测
        if (Build.VERSION.SDK_INT >= 29) features.add(RomFeature.SCOPED_STORAGE);
        if (Build.VERSION.SDK_INT >= 30) features.add(RomFeature.HIDDEN_API_BLACKLIST_ONLY);
        if (Build.VERSION.SDK_INT >= 34) features.add(RomFeature.MEMFD_EXEC_RESTRICTION);

        // 厂商特定特性
        switch (vendor) {
            case XIAOMI:
                features.add(RomFeature.XIAOMI_GUARD_PROVIDER);
                if (isHyperOS()) features.add(RomFeature.XIAOMI_HYPEROS);
                break;
            case HUAWEI:
                if (isHarmonyOS()) {
                    features.add(RomFeature.HUAWEI_HARMONYOS);
                    features.add(RomFeature.HUAWEI_SPECIAL_SELINUX);
                }
                features.add(RomFeature.HUAWEI_EMUI);
                break;
            case HONOR:
                features.add(RomFeature.HUAWEI_MAGIC_UI);
                break;
            case SAMSUNG:
                features.add(RomFeature.SAMSUNG_KNOX);
                features.add(RomFeature.SAMSUNG_ONE_UI);
                break;
            case OPPO:
                features.add(RomFeature.OPPO_COLOR_OS);
                break;
            case VIVO:
                features.add(RomFeature.VIVO_ORIGIN_OS);
                break;
            case MEIZU:
                features.add(RomFeature.MEIZU_FLYME);
                break;
            default:
                break;
        }

        // 检测隐藏 API 豁免状态
        features.add(RomFeature.HIDDEN_API_ACCESSIBLE);

        Log.i(TAG, "ROM " + vendor + " features: " + features);
        return new RomFeatureSet(vendor, features);
    }

    // =====================================================================
    // 辅助
    // =====================================================================

    private static boolean hasAnySystemProperty(String[] keys) {
        for (String key : keys) {
            String value = getSystemProperty(key);
            if (value != null && !value.isEmpty()) return true;
        }
        return false;
    }

    private static String getSystemProperty(String key) {
        try {
            Class<?> systemProps = Class.forName("android.os.SystemProperties");
            Method get = systemProps.getMethod("get", String.class);
            return (String) get.invoke(null, key);
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isHyperOS() {
        String v = getSystemProperty("ro.hyperos.version.name");
        return v != null && !v.isEmpty();
    }

    private static boolean isHarmonyOS() {
        String v = getSystemProperty("ro.build.version.harmonyos");
        return v != null && !v.isEmpty();
    }
}
