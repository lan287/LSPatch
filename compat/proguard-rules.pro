# LSPatch 2.0 Compat Module — ProGuard 规则
#
# 保留策略：
# 1. 保留所有与版本检测相关的反射类
# 2. 保留 AndroidX / JUnit 测试类
# 3. 保留 ROM 检测与适配相关的 API
# 4. 保留 ModuleLifecycle 相关的接口（供模块动态加载）
# 5. 保留所有 Native 方法的 JNI 桥接声明

# --- 1. 保留核心适配器 API（供外部调用）---
-keep class org.lsposed.lspatch.compat.adapter.** { *; }
-keepclassmembers class org.lsposed.lspatch.compat.adapter.** {
    public *;
    protected *;
}

# --- 2. 保留 ROM 适配层 ---
-keep class org.lsposed.lspatch.compat.rom.** { *; }
-keepclassmembers class org.lsposed.lspatch.compat.rom.** {
    public *;
    protected *;
}

# --- 3. 保留 Xposed 兼容桥接器 ---
-keep class org.lsposed.lspatch.compat.bridge.** { *; }
-keepclassmembers class org.lsposed.lspatch.compat.bridge.** {
    public *;
    protected *;
    native <methods>;
}

# --- 4. 保留 Hook 回调相关 ---
-keep class org.lsposed.lspatch.compat.bridge.HookParam { *; }

# --- 5. 保留 Native 方法 JNI 注册 ---
-keepclasseswithmembers class * {
    native <methods>;
}

# --- 6. 保留 Kotlin 元数据 ---
-keep class kotlin.Metadata { *; }
-keepattributes Signature,InnerClasses,*Annotation*

# --- 7. 保留 AndroidX 注解 ---
-keepattributes *Annotation*

# --- 8. 供 Android 8+ 使用的 Java 11/17 类型 ---
-keep class java.lang.invoke.** { *; }
-keep class java.lang.reflect.** { *; }

# --- 9. 保留 buildConfigField（SDK 版本信息等）---
-keep class org.lsposed.lspatch.compat.BuildConfig { *; }
