# LSPatch 2.0 发布检查清单

> **版本**: 2.0.0-beta1  
> **目标发布日期**: TBD  
> **检查人**: TBD  
> **检查日期**: TBD

---

## 1. 代码质量

### 1.1 静态分析

- [ ] Lint 检查通过（零错误）
  ```bash
  ./gradlew lint
  ```
- [ ] Kotlin 代码风格检查（ktlint 或 detekt）
  ```bash
  ./gradlew detekt
  ```
- [ ] C++ 代码风格检查（clang-format）
  ```bash
  find . -name "*.cpp" -o -name "*.h" | xargs clang-format --dry-run --Werror
  ```
- [ ] ProGuard/R8 混淆规则验证
  ```bash
  ./gradlew :compat:minifyReleaseWithR8
  ```

### 1.2 编译

- [ ] Release 构建成功（所有 ABI）
  ```bash
  ./gradlew assembleRelease
  ```
- [ ] 无编译警告（`-Werror` 模式）
- [ ] 所有 flavor 构建通过
  ```bash
  ./gradlew assembleAllFlavors
  ```

### 1.3 测试

- [ ] 单元测试全部通过
  ```bash
  ./gradlew test
  ```
- [ ] 集成测试通过（需要设备/模拟器）
  ```bash
  ./gradlew connectedAndroidTest
  ```
- [ ] 性能基准测试未退化
- [ ] 兼容性矩阵测试通过（Android 8-15）

---

## 2. 二进制验证

### 2.1 ABI 覆盖

- [ ] arm64-v8a 原生库存在
  - [ ] `liblspatch.so`
  - [ ] `libart_method_hooker.so`
- [ ] armeabi-v7a 原生库存在
  - [ ] `liblspatch.so`
  - [ ] `libart_method_hooker.so`
- [ ] x86_64 原生库（实验性）
- [ ] x86 原生库（实验性）

### 2.2 符号导出

- [ ] 仅导出预期 JNI 符号（`JNI_OnLoad`）
- [ ] 无意外全局符号泄漏
  ```bash
  nm -D liblspatch.so | grep " T " | grep -v "JNI_OnLoad"
  ```
- [ ] `.dynamic` 段最小化

### 2.3 文件大小

| 组件 | 预期大小 | 实际大小 | 状态 |
|------|---------|---------|------|
| liblspatch.so (arm64) | < 500KB | | [ ] |
| libart_method_hooker.so (arm64) | < 200KB | | [ ] |
| lspatch-v2-api.aar | < 100KB | | [ ] |
| compat.aar | < 300KB | | [ ] |

---

## 3. 签名配置验证

### 3.1 签名环境检查

- [ ] Keystore 文件存在且可访问
- [ ] 密钥别名正确
- [ ] 密钥密码验证通过
  ```bash
  keytool -list -keystore <keystore_path> -alias <key_alias> -storepass <password>
  ```
- [ ] 证书有效期充足（> 1年）
  ```bash
  keytool -list -v -keystore <keystore_path> -alias <key_alias> \
    | grep "Valid from"
  ```

### 3.2 Gradle 签名配置

- [ ] `signingConfigs.release` 配置正确
- [ ] 签名密钥从环境变量/CI Secrets 读取（不硬编码）
- [ ] `buildTypes.release.signingConfig` 引用正确

**推荐配置**:

```kotlin
// build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "lspatch"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 3.3 APK 签名验证

- [ ] V1 (JAR) 签名存在
- [ ] V2 (APK Signature Scheme v2) 签名存在
- [ ] V3 (APK Signature Scheme v3) 签名存在
  ```bash
  apksigner verify --verbose build/outputs/apk/release/lspatch-release.apk
  ```
- [ ] 签名证书 SHA-256 与预期一致
  ```bash
  apksigner verify --print-certs build/outputs/apk/release/lspatch-release.apk
  ```

---

## 4. 版本号一致性

### 4.1 版本号统一检查

| 位置 | 预期值 | 实际值 | 状态 |
|------|--------|--------|------|
| `VERSION.properties` | 2.0.0 | | [ ] |
| `build.gradle.kts` versionName | 2.0.0 | | [ ] |
| `build.gradle.kts` versionCode | 20000 | | [ ] |
| `CHANGELOG.md` 标题 | 2.0.0 | | [ ] |
| `ARCHITECTURE.md` 版本 | 2.0.0 | | [ ] |
| `MIGRATION.md` 版本 | 2.0.0 | | [ ] |
| `PERFORMANCE_REPORT.md` 版本 | 2.0.0-beta1 | | [ ] |
| AAR Maven 坐标 version | 2.0.0 | | [ ] |

### 4.2 版本号规则

- `versionCode` = `MAJOR * 10000 + MINOR * 100 + PATCH`
  - 2.0.0 → `20000`
  - 2.1.0 → `20100`
  - 2.0.1 → `20001`

---

## 5. 文档完整性

- [ ] `ARCHITECTURE.md` - 架构变更说明
- [ ] `MIGRATION.md` - API 迁移指南
- [ ] `PERFORMANCE_REPORT.md` - 性能基准报告
- [ ] `CHANGELOG.md` - 版本变更日志
- [ ] `CODE_REVIEW_CHECKLIST.md` - 代码审查清单
- [ ] 所有文档中的版本号与发布版本一致
- [ ] 所有文档中的文件路径与实际一致

---

## 6. 发布制品

### 6.1 制品清单

- [ ] `lspatch-v2-api.aar` - 核心 API 库
- [ ] `compat.aar` - 兼容层库
- [ ] `patch-loader.aar` - 加载器库
- [ ] 原生库 SO 文件（各 ABI）
- [ ] ProGuard 映射文件 (`mapping.txt`)
- [ ] 签名信息摘要

### 6.2 发布渠道准备

- [ ] Maven Central / 私有仓库坐标确认
  ```
  groupId: org.lsposed
  artifactId: lspatch-v2-api
  version: 2.0.0
  ```
- [ ] GitHub Release 页面准备
  - [ ] Release Notes 编写
  - [ ] 制品上传
- [ ] CI 发布流水线配置
  - [ ] 触发条件: tag `v*`
  - [ ] 构建 → 签名 → 上传 → 发布

---

## 7. 兼容性验证

### 7.1 向后兼容

- [ ] 旧模块（1.x API）可在 2.0 上运行
- [ ] `XposedBridgeCompat` 桥接所有旧 API
- [ ] `MigrationTracker` 正确统计废弃 API 调用

### 7.2 版本兼容矩阵

| Android 版本 | 架构 | 测试状态 | 备注 |
|-------------|------|---------|------|
| 8.0 (API 26) | arm64 | [ ] | |
| 8.1 (API 27) | arm64 | [ ] | |
| 9 (API 28) | arm64 | [ ] | |
| 10 (API 29) | arm64 | [ ] | |
| 11 (API 30) | arm64 | [ ] | |
| 12 (API 31) | arm64 | [ ] | |
| 12L (API 32) | arm64 | [ ] | |
| 13 (API 33) | arm64 | [ ] | |
| 14 (API 34) | arm64 | [ ] | |
| 15 (API 35) | arm64 | [ ] | |

### 7.3 ROM 兼容矩阵

| ROM | 版本 | 设备 | 测试状态 |
|-----|------|------|---------|
| AOSP (Pixel) | 14 | Pixel 8 | [ ] |
| MIUI | 14 | Xiaomi 13 | [ ] |
| HyperOS | 1.0 | Xiaomi 14 | [ ] |
| OneUI | 6.1 | Galaxy S24 | [ ] |
| ColorOS | 14 | OnePlus 12 | [ ] |
| HarmonyOS | 4 | Mate 60 | [ ] |

---

## 8. 安全审查

- [ ] 无硬编码密钥/密码/令牌
- [ ] 无调试日志输出到 Release 构建
- [ ] ProGuard 规则正确排除反射调用的类
- [ ] 模块签名验证链完整
- [ ] SELinux 上下文切换有回退机制
- [ ] Native 内存操作无缓冲区溢出
- [ ] 无 `system()` / `exec()` 等危险调用

---

## 9. 最终签核

| 角色 | 姓名 | 签核 | 日期 |
|------|------|------|------|
| 开发负责人 | | [ ] | |
| QA 负责人 | | [ ] | |
| 安全审查 | | [ ] | |
| 发布经理 | | [ ] | |

---

## 10. 发布命令参考

```bash
# 1. 更新版本号
# 编辑 VERSION.properties, build.gradle.kts 中的 versionName/versionCode

# 2. 清理构建
./gradlew clean

# 3. 运行全部测试
./gradlew test connectedAndroidTest

# 4. 构建 Release
./gradlew assembleRelease

# 5. 验证签名
apksigner verify --verbose build/outputs/apk/release/lspatch-release.apk

# 6. 输出制品信息
ls -lh build/outputs/apk/release/
ls -lh build/outputs/aar/

# 7. 创建 Git Tag
git tag -a v2.0.0 -m "LSPatch 2.0.0 Release"
git push origin v2.0.0

# 8. 发布到 Maven
./gradlew publishReleasePublicationToMavenRepository
```