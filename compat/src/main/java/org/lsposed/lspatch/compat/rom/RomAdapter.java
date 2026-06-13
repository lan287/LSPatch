package org.lsposed.lspatch.compat.rom;

import android.content.Context;
import android.util.Log;

import org.lsposed.lspatch.compat.adapter.VersionAdapter;
import org.lsposed.lspatch.compat.adapter.VersionAdapter.HookStrategy;

import java.util.Set;

/**
 * ROM 兼容性适配器 — 处理不同厂商 ROM 的特有差异。
 *
 * <h3>为什么需要 ROM 适配？</h3>
 * <p>
 * 不同厂商会对 ART 做定制化修改、收紧 SELinux 策略、
 * 在系统进程注入签名校验 Hook。直接使用默认 ART 兼容策略可能导致：
 * <ul>
 *   <li>应用启动崩溃（华为 HarmonyOS 的 ART 重命名）</li>
 *   <li>Hook 失效（三星 KNOX 对 dex2oat 输出做校验）</li>
 *   <li>文件系统错误（小米 HyperOS 的 FUSE 限制）</li>
 * </ul>
 *
 * <h3>用法示例</h3>
 * <pre>{@code
 * RomFeatureSet features = RomDetector.getFeatureSet();
 * RomAdapter adapter = RomAdapter.create(features);
 * boolean needSigBypass = adapter.needsSignatureBypass();
 * HookStrategy adjusted = adapter.adjustHookStrategy(originalStrategy);
 * }</pre>
 */
public final class RomAdapter {

    private static final String TAG = "LSPatch-RomAdapter";

    private final RomFeatureSet featureSet;

    private RomAdapter(RomFeatureSet featureSet) {
        this.featureSet = featureSet;
    }

    /**
     * 创建 ROM 适配器。
     */
    public static RomAdapter create(RomFeatureSet featureSet) {
        return new RomAdapter(featureSet);
    }

    /**
     * 基于 ROM 特性调整 Hook 策略。
     *
     * <p>
     * 某些厂商 ROM 会修改 ART 内部结构或引入额外的签名校验。
     * 本方法返回调整后的最佳策略。
     */
    public HookStrategy adjustHookStrategy(HookStrategy original) {
        if (featureSet.hasFeature(RomFeature.HUAWEI_HARMONYOS)) {
            // HarmonyOS 的 ART 路径为 /apex/com.android.art/{arch}/libart-patch.so
            // 需要使用 Dex Pilot 或入口点替换
            if (original == HookStrategy.ART_INLINE) {
                Log.w(TAG, "HarmonyOS detected — forcing ENTRYPOINT_REPLACE strategy");
                return HookStrategy.ENTRYPOINT_REPLACE;
            }
        }

        if (featureSet.hasFeature(RomFeature.SAMSUNG_KNOX)) {
            // 三星 KNOX：对 ART inline Hook 有额外检测
            // 使用 Dex Pilot 或软降级更安全
            if (original == HookStrategy.ART_INLINE) {
                Log.w(TAG, "Samsung KNOX detected — recommending DEX_PILOT fallback");
                return HookStrategy.DEX_PILOT;
            }
        }

        if (featureSet.hasFeature(RomFeature.XIAOMI_HYPEROS)) {
            // 小米 HyperOS 对某些设备的 ART 做了修改
            // 若启用了 memfd 限制，使用 LSPLANT_V2 或降级
            if (original == HookStrategy.ART_INLINE) {
                Log.i(TAG, "HyperOS detected — recommending LSPLANT_V2 strategy");
                return HookStrategy.LSPLANT_V2;
            }
        }

        // 其他 ROM 保持原始策略
        return original;
    }

    /**
     * 是否需要启用签名绕过。
     */
    public boolean needsSignatureBypass() {
        return featureSet.hasStrictSignatureCheck() ||
               featureSet.isXiaomi() ||
               featureSet.isHuawei();
    }

    /**
     * 是否需要在 Java 层加 Hook 辅助。
     *
     * <p>
     * 某些厂商 ROM （如华为 EMUI）会禁用 JNI 层的 Hook，
     * 需要额外的 Java 反射辅助才能绕过。
     */
    public boolean needsJavaReflectionHelper() {
        return featureSet.hasFeature(RomFeature.HUAWEI_HARMONYOS) ||
               featureSet.hasFeature(RomFeature.HUAWEI_EMUI);
    }

    /**
     * 获取 SELinux 上下文安全等级。
     *
     * @return 0 = 标准（Pixel/AOSP），1 = 中等（大部分 ROM），2 = 严格（华为/荣耀）
     */
    public int getSelinuxStrictnessLevel() {
        if (featureSet.hasFeature(RomFeature.HUAWEI_SPECIAL_SELINUX) ||
            featureSet.hasFeature(RomFeature.HUAWEI_HARMONYOS)) {
            return 2;
        }
        if (featureSet.hasFeature(RomFeature.SAMSUNG_KNOX)) {
            return 2;
        }
        if (featureSet.isPixel()) {
            return 0;
        }
        return 1;
    }

    /**
     * 是否需要启用 Hidden API 豁免。
     */
    public boolean needsHiddenApiExemption() {
        return featureSet.needsHiddenApiExemption();
    }

    /**
     * 获取 ROM 描述文本（用于日志/UI 展示）。
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROM: ").append(featureSet.getVendor());
        sb.append(" (strictness=").append(getSelinuxStrictnessLevel()).append(")");
        sb.append(", features: ").append(featureSet.getFeatures());
        return sb.toString();
    }

    /**
     * 获取 ROM 标识字符串（用于 UI 显示）。
     */
    public String getVendorDisplayName() {
        switch (featureSet.getVendor()) {
            case XIAOMI:
                return featureSet.hasFeature(RomFeature.XIAOMI_HYPEROS) ? "HyperOS (Xiaomi)" : "MIUI (Xiaomi)";
            case HUAWEI:
                return featureSet.hasFeature(RomFeature.HUAWEI_HARMONYOS) ? "HarmonyOS (Huawei)" : "EMUI (Huawei)";
            case HONOR:
                return featureSet.hasFeature(RomFeature.HUAWEI_MAGIC_UI) ? "Magic UI (Honor)" : "Honor";
            case SAMSUNG:
                return "One UI (Samsung)";
            case OPPO:
                return "ColorOS (OPPO/Realme/OnePlus)";
            case VIVO:
                return "OriginOS (vivo/iQOO)";
            case MEIZU:
                return "FlymeOS (Meizu)";
            case PIXEL:
                return "Stock Android (Google Pixel)";
            case MOTOROLA:
                return "MyUX (Motorola)";
            case LENOVO:
                return "ZUI (Lenovo)";
            case ONEPLUS:
                return "OxygenOS (OnePlus)";
            case OTHER:
            default:
                return "Unknown / Generic Android";
        }
    }

    /**
     * 获取 ROM 的版本适配器偏好。
     */
    public String getAdapterPreferenceHint() {
        switch (featureSet.getVendor()) {
            case HUAWEI:
                return "HarmonyOS-optimized ART paths — use DexPilot";
            case HONOR:
                return "Honor/Magic UI — similar to Huawei EMUI";
            case SAMSUNG:
                return "Samsung KNOX signature checks — use DexPilot";
            case XIAOMI:
                return "HyperOS — use lsplant V2";
            case VIVO:
            case OPPO:
            case MEIZU:
            case PIXEL:
            default:
                return "Standard ART — default strategies work fine";
        }
    }

    /**
     * 获取原生 Hook 是否允许在无 ptrace 的情况下启用。
     */
    public boolean allowsNativeHookWithoutPtrace() {
        // 华为/HarmonyOS/荣耀 限制更严格；其他 ROM 基本允许
        return !featureSet.hasFeature(RomFeature.HUAWEI_SPECIAL_SELINUX) &&
               !featureSet.hasFeature(RomFeature.SAMSUNG_KNOX);
    }

    /**
     * 获取 ROM 兼容建议（用于 UI 展示）。
     */
    public String getCompatibilityAdvice() {
        switch (featureSet.getVendor()) {
            case HUAWEI:
                return "建议启用签名绕过 (SigBypass Level >= 2) 并使用 DexPilot Hook 策略";
            case HONOR:
                return "建议启用签名绕过 (SigBypass Level >= 2)，类似华为 EMUI";
            case SAMSUNG:
                return "建议启用签名绕过 (SigBypass Level >= 3)，避免 KNOX 检测";
            case XIAOMI:
                return "建议使用 lsplant V2 Hook 策略；部分 HyperOS 设备需启用签名绕过";
            case OPPO:
            case VIVO:
            case MEIZU:
                return "一般兼容标准 ART 策略，若失败可尝试 DexPilot 降级";
            case PIXEL:
                return "Pixel/Stock Android 完全兼容所有 Hook 策略";
            default:
                return "未知 ROM，若出现问题请尝试启用降级模式 (SoftFallback)";
        }
    }
}
