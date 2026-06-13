/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.build.apkzlib.zfile;

import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import java.io.File;

public interface ApkCreatorFactory {

  ApkCreator make(CreationData creationData);

  class CreationData {

    public static Builder builder() {
      return new Builder()
          .setBuiltBy(null)
          .setCreatedBy(null)
          .setNoCompressPredicate(s -> false)
          .setIncremental(false);
    }

    private final File apkPath;
    private final Optional<SigningOptions> signingOptions;
    private final String builtBy;
    private final String createdBy;
    private final NativeLibrariesPackagingMode nativeLibrariesPackagingMode;
    private final Predicate<String> noCompressPredicate;
    private final boolean incremental;

    CreationData(File apkPath, Optional<SigningOptions> signingOptions, String builtBy,
                String createdBy, NativeLibrariesPackagingMode packagingMode,
                Predicate<String> noCompressPredicate, boolean incremental) {
      this.apkPath = apkPath;
      this.signingOptions = signingOptions;
      this.builtBy = builtBy;
      this.createdBy = createdBy;
      this.nativeLibrariesPackagingMode = packagingMode;
      this.noCompressPredicate = noCompressPredicate;
      this.incremental = incremental;
    }

    public File getApkPath() { return apkPath; }
    public Optional<SigningOptions> getSigningOptions() { return signingOptions; }
    public String getBuiltBy() { return builtBy; }
    public String getCreatedBy() { return createdBy; }
    public NativeLibrariesPackagingMode getNativeLibrariesPackagingMode() { return nativeLibrariesPackagingMode; }
    public Predicate<String> getNoCompressPredicate() { return noCompressPredicate; }
    public boolean isIncremental() { return incremental; }

    public static class Builder {
      private File apkPath;
      private Optional<SigningOptions> signingOptions = Optional.absent();
      private String builtBy;
      private String createdBy;
      private NativeLibrariesPackagingMode nativeLibrariesPackagingMode;
      private Predicate<String> noCompressPredicate;
      private boolean incremental;

      public Builder setApkPath(File apkPath) { this.apkPath = apkPath; return this; }
      public Builder setSigningOptions(SigningOptions signingOptions) {
        this.signingOptions = Optional.fromNullable(signingOptions); return this;
      }
      public Builder setBuiltBy(String builtBy) { this.builtBy = builtBy; return this; }
      public Builder setCreatedBy(String createdBy) { this.createdBy = createdBy; return this; }
      public Builder setNativeLibrariesPackagingMode(NativeLibrariesPackagingMode packagingMode) {
        this.nativeLibrariesPackagingMode = packagingMode; return this;
      }
      public Builder setNoCompressPredicate(Predicate<String> predicate) {
        this.noCompressPredicate = predicate; return this;
      }
      public Builder setIncremental(boolean incremental) { this.incremental = incremental; return this; }

      public CreationData build() {
        Preconditions.checkArgument(apkPath != null, "Output apk path is not set");
        return new CreationData(apkPath, signingOptions, builtBy, createdBy,
            nativeLibrariesPackagingMode, noCompressPredicate, incremental);
      }
    }
  }
}
