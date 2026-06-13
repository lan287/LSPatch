package org.lsposed.lspatch.compat.rom;

import java.util.Collections;
import java.util.Set;

/**
 * 封装 ROM 的完整特性信息。
 */
public final class RomFeatureSet {

    private final RomVendor vendor;
    private final Set<RomFeature> features;

    public RomFeatureSet(RomVendor vendor, Set<RomFeature> features) {
        this.vendor = vendor;
        this.features = Collections.unmodifiableSet(features);
    }

    public RomVendor getVendor() {
        return vendor;
    }

    public boolean hasFeature(RomFeature feature) {
        return features.contains(feature);
    }

    public Set<RomFeature> getFeatures() {
        return features;
    }

    public boolean isXiaomi() { return vendor == RomVendor.XIAOMI; }
    public boolean isHuawei() { return vendor == RomVendor.HUAWEI; }
    public boolean isSamsung() { return vendor == RomVendor.SAMSUNG; }
    public boolean isOppo() { return vendor == RomVendor.OPPO; }
    public boolean isVivo() { return vendor == RomVendor.VIVO; }
    public boolean isPixel() { return vendor == RomVendor.PIXEL; }

    /** 是否为需要特殊签名绕过的厂商 ROM */
    public boolean hasStrictSignatureCheck() {
        return features.contains(RomFeature.SAMSUNG_KNOX) ||
               features.contains(RomFeature.HUAWEI_HARMONYOS) ||
               features.contains(RomFeature.HUAWEI_SPECIAL_SELINUX);
    }

    /** 是否需要 Hidden API 豁免 */
    public boolean needsHiddenApiExemption() {
        return features.contains(RomFeature.HIDDEN_API_BLACKLIST_ONLY) ||
               features.contains(RomFeature.HUAWEI_HARMONYOS);
    }

    @Override
    public String toString() {
        return "RomFeatureSet{vendor=" + vendor + ", features=" + features + "}";
    }
}
