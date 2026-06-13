package org.lsposed.lspatch.compat.adapter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 当前设备的 Android 版本能力描述。
 * 由 {@link VersionAdapter#probeCapability()} 生成，作为上层策略选择的输入。
 *
 * <p>
 * 文档参考：
 * <a href="https://developer.android.com/reference/android/os/Build.VERSION_CODES">
 * Build.VERSION_CODES
 * </a>
 */
public final class VersionCapability {

    private final int sdkInt;
    private final String sdkRelease;
    private final boolean hasUserfaultfdGC;
    private final boolean hasMemfdRestriction;
    private final boolean hasExecMemRestriction;
    private final boolean hasAppComponentFactory;
    private final boolean hasInMemoryDexClassLoader;
    private final boolean hasScopedStorage;
    private final boolean hasHiddenApiRestrictions;
    private final boolean hasJitCompiler;
    private final Set<String> supportedSignatureSchemes;
    private final String runtimeName;

    VersionCapability(Builder b) {
        this.sdkInt = b.sdkInt;
        this.sdkRelease = b.sdkRelease;
        this.hasUserfaultfdGC = b.hasUserfaultfdGC;
        this.hasMemfdRestriction = b.hasMemfdRestriction;
        this.hasExecMemRestriction = b.hasExecMemRestriction;
        this.hasAppComponentFactory = b.hasAppComponentFactory;
        this.hasInMemoryDexClassLoader = b.hasInMemoryDexClassLoader;
        this.hasScopedStorage = b.hasScopedStorage;
        this.hasHiddenApiRestrictions = b.hasHiddenApiRestrictions;
        this.hasJitCompiler = b.hasJitCompiler;
        this.supportedSignatureSchemes = Collections.unmodifiableSet(new HashSet<>(b.signatureSchemes));
        this.runtimeName = b.runtimeName;
    }

    public int getSdkInt() { return sdkInt; }
    public String getSdkRelease() { return sdkRelease; }
    public boolean hasUserfaultfdGC() { return hasUserfaultfdGC; }
    public boolean hasMemfdRestriction() { return hasMemfdRestriction; }
    public boolean hasExecMemRestriction() { return hasExecMemRestriction; }
    public boolean hasAppComponentFactory() { return hasAppComponentFactory; }
    public boolean hasInMemoryDexClassLoader() { return hasInMemoryDexClassLoader; }
    public boolean hasScopedStorage() { return hasScopedStorage; }
    public boolean hasHiddenApiRestrictions() { return hasHiddenApiRestrictions; }
    public boolean hasJitCompiler() { return hasJitCompiler; }
    public Set<String> getSupportedSignatureSchemes() { return supportedSignatureSchemes; }
    public String getRuntimeName() { return runtimeName; }

    public boolean isAtLeastO() { return sdkInt >= 26; }
    public boolean isAtLeastP() { return sdkInt >= 28; }
    public boolean isAtLeastQ() { return sdkInt >= 29; }
    public boolean isAtLeastR() { return sdkInt >= 30; }
    public boolean isAtLeastS() { return sdkInt >= 31; }
    public boolean isAtLeastTiramisu() { return sdkInt >= 33; }
    public boolean isAtLeastUpsideDownCake() { return sdkInt >= 34; }
    public boolean isAtLeastVanillaIceCream() { return sdkInt >= 35; }

    @Override
    public String toString() {
        return "VersionCapability{sdk=" + sdkInt + ", release='" + sdkRelease + "', " +
                "runtime=" + runtimeName + ", userfaultfd=" + hasUserfaultfdGC + ", " +
                "memfd=" + hasMemfdRestriction + ", execmem=" + hasExecMemRestriction + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int sdkInt;
        private String sdkRelease = "UNKNOWN";
        private boolean hasUserfaultfdGC;
        private boolean hasMemfdRestriction;
        private boolean hasExecMemRestriction;
        private boolean hasAppComponentFactory;
        private boolean hasInMemoryDexClassLoader;
        private boolean hasScopedStorage;
        private boolean hasHiddenApiRestrictions;
        private boolean hasJitCompiler;
        private final Set<String> signatureSchemes = new HashSet<>();
        private String runtimeName = "ART";

        public Builder sdkInt(int v) { this.sdkInt = v; return this; }
        public Builder sdkRelease(String v) { this.sdkRelease = v; return this; }
        public Builder hasUserfaultfdGC(boolean v) { this.hasUserfaultfdGC = v; return this; }
        public Builder hasMemfdRestriction(boolean v) { this.hasMemfdRestriction = v; return this; }
        public Builder hasExecMemRestriction(boolean v) { this.hasExecMemRestriction = v; return this; }
        public Builder hasAppComponentFactory(boolean v) { this.hasAppComponentFactory = v; return this; }
        public Builder hasInMemoryDexClassLoader(boolean v) { this.hasInMemoryDexClassLoader = v; return this; }
        public Builder hasScopedStorage(boolean v) { this.hasScopedStorage = v; return this; }
        public Builder hasHiddenApiRestrictions(boolean v) { this.hasHiddenApiRestrictions = v; return this; }
        public Builder hasJitCompiler(boolean v) { this.hasJitCompiler = v; return this; }
        public Builder addSignatureScheme(String v) { this.signatureSchemes.add(v); return this; }
        public Builder runtimeName(String v) { this.runtimeName = v; return this; }

        public VersionCapability build() { return new VersionCapability(this); }
    }
}
