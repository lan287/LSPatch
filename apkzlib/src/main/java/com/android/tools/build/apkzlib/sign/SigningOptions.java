/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.build.apkzlib.sign;

import com.android.apksig.util.RunnablesExecutor;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class SigningOptions {

    public static Builder builder() {
        return new Builder()
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(false)
            .setValidation(Validation.ALWAYS_VALIDATE);
    }

    private final PrivateKey key;
    private final ImmutableList<X509Certificate> certificates;
    private final boolean v1SigningEnabled;
    private final boolean v2SigningEnabled;
    private final int minSdkVersion;
    private final Validation validation;
    private final RunnablesExecutor executor;
    private final byte[] sdkDependencyData;

    private SigningOptions(PrivateKey key, ImmutableList<X509Certificate> certificates,
                          boolean v1SigningEnabled, boolean v2SigningEnabled, int minSdkVersion,
                          Validation validation, RunnablesExecutor executor, byte[] sdkDependencyData) {
        this.key = key;
        this.certificates = certificates;
        this.v1SigningEnabled = v1SigningEnabled;
        this.v2SigningEnabled = v2SigningEnabled;
        this.minSdkVersion = minSdkVersion;
        this.validation = validation;
        this.executor = executor;
        this.sdkDependencyData = sdkDependencyData;
    }

    public PrivateKey getKey() { return key; }
    public ImmutableList<X509Certificate> getCertificates() { return certificates; }
    public boolean isV1SigningEnabled() { return v1SigningEnabled; }
    public boolean isV2SigningEnabled() { return v2SigningEnabled; }
    public int getMinSdkVersion() { return minSdkVersion; }
    public Validation getValidation() { return validation; }
    public RunnablesExecutor getExecutor() { return executor; }
    public byte[] getSdkDependencyData() { return sdkDependencyData; }

    public enum Validation {
        ALWAYS_VALIDATE,
        ASSUME_VALID,
        ASSUME_INVALID,
    }

    public static class Builder {
        private PrivateKey key;
        private ImmutableList<X509Certificate> certificates;
        private boolean v1SigningEnabled;
        private boolean v2SigningEnabled;
        private int minSdkVersion;
        private Validation validation;
        private RunnablesExecutor executor;
        private byte[] sdkDependencyData;

        public Builder setKey(PrivateKey key) { this.key = key; return this; }
        public Builder setCertificates(ImmutableList<X509Certificate> certs) { this.certificates = certs; return this; }
        public Builder setCertificates(X509Certificate... certs) {
            this.certificates = ImmutableList.copyOf(certs); return this;
        }
        public Builder setV1SigningEnabled(boolean enabled) { this.v1SigningEnabled = enabled; return this; }
        public Builder setV2SigningEnabled(boolean enabled) { this.v2SigningEnabled = enabled; return this; }
        public Builder setMinSdkVersion(int version) { this.minSdkVersion = version; return this; }
        public Builder setValidation(Validation validation) { this.validation = validation; return this; }
        public Builder setExecutor(RunnablesExecutor executor) { this.executor = executor; return this; }
        public Builder setSdkDependencyData(byte[] data) { this.sdkDependencyData = data; return this; }

        public SigningOptions build() {
            Preconditions.checkArgument(minSdkVersion >= 0, "minSdkVersion < 0");
            Preconditions.checkArgument(certificates != null && !certificates.isEmpty(),
                    "There should be at least one certificate in SigningOptions");
            return new SigningOptions(key, certificates, v1SigningEnabled, v2SigningEnabled,
                    minSdkVersion, validation, executor, sdkDependencyData);
        }
    }
}
