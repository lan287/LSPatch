pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 兼容 LSPosed 核心依赖（部分依赖发布在 GitHub Maven / JitPack）
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.google.com") }
    }
}

rootProject.name = "LSPatch2"
include(":compat")
