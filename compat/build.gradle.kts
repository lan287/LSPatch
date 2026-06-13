/**
 * LSPatch 2.0 版本兼容模块 — Gradle 构建脚本 (Kotlin DSL)。
 *
 * 构建变体说明：
 * - debug    : 可调试版本，包含断言和详细日志
 * - release  : 发行版本，代码混淆 + R8 优化
 * - canary   : 测试版本（用于 CI 矩阵测试 Android 14/15 预览 API）
 *
 * 编译 SDK 矩阵：
 * ┌─────────┬────────┬─────────┬──────────┬────────────┐
 * │ 模块    │ minSdk │ targetSdk │ compileSdk │ 说明     │
 * ├─────────┼────────┼─────────┼──────────┼────────────┤
 * │ compat  │ 26     │ 35       │ android-35 | 核心兼容 │
 * └─────────┴────────┴─────────┴──────────┴────────────┘
 *
 * 多版本编译：通过 project.ext.variantSdk 指定，例如：
 *   ./gradlew assembleDebug -PvariantSdk=26
 *   ./gradlew assembleRelease -PvariantSdk=35
 */
plugins {
    id("com.android.library")
    id("kotlin-android")
}

/** 从 Gradle 属性读取的变体 SDK，默认 35 */
val variantSdk = providers.gradleProperty("variantSdk")
    .getOrElse("35")
    .toInt()
    .coerceIn(26, 35)

android {
    namespace = "org.lsposed.lspatch.compat"
    compileSdk = 35

    defaultConfig {
        // 最低 SDK: Android 8.0 (API 26) — 最早支持的 InMemoryDexClassLoader
        minSdk = variantSdk.coerceAtMost(35).coerceAtLeast(26)
        targetSdk = 35
        versionCode = 2_00_00
        versionName = "2.0.0-v$variantSdk"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }

        // CI 矩阵测试变体
        create("canary") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            matchingFallbacks.add("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // 兼容 Kotlin 1.9 / 2.0 共存
        languageVersion = "1.9"
        apiVersion = "1.9"
        // 启用上下文接收者（实验性，但对 DSL 类型安全有益）
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xcontext-receivers"
        )
    }

    // 对不同 SDK 级别设置不同的资源过滤
    applicationVariants.all { variant ->
        variant.outputs.all {
            val apkName = "lspatch-compat-${variant.name}-$variantSdk.apk"
            (this as com.android.build.gradle.internal.api.ApkVariantOutputImpl).outputFileName = apkName
        }
    }

    // Enable non-final R class to reduce compile times in large builds
    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
    }

    // Kotlin source set extension for Kotlin 2.0 compatibility
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ===== AndroidX =====
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.1")

    // ===== Kotlin 标准库（与 compileSdk 匹配）=====
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // ===== 编译期注解（保留以供后续模块系统扩展）=====
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

// ===== 编译期输出：打印构建配置以供 CI 矩阵解析 =====
tasks.register("printBuildConfig") {
    doLast {
        println(
            "=== LSPatch Compat Build Config ===\n" +
                    "variantSdk       : $variantSdk\n" +
                    "compileSdk       : 35\n" +
                    "minSdk (eff)     : ${android.defaultConfig.minSdk}\n" +
                    "namespace        : ${android.namespace}\n" +
                    "versionName      : ${android.defaultConfig.versionName}\n" +
                    "kotlin jvmTarget : 17\n" +
                    "====================================="
        )
    }
}
