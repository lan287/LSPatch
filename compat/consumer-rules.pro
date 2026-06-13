# LSPatch 2.0 Compat — 消费者规则
# 这些规则将应用于所有使用此库的模块 / 应用 / APKS。

# 保留版本适配器接口，供外部模块通过反射/类加载访问
-keep class org.lsposed.lspatch.compat.adapter.VersionAdapter { *; }
-keep class org.lsposed.lspatch.compat.adapter.VersionAdapter$HookStrategy { *; }
-keep class org.lsposed.lspatch.compat.adapter.VersionAdapter$HiddenApiLevel { *; }
-keep class org.lsposed.lspatch.compat.adapter.VersionCapability { *; }
-keep class org.lsposed.lspatch.compat.adapter.VersionAdapterFactory { *; }

# 保留 ROM 兼容性 API
-keep class org.lsposed.lspatch.compat.rom.** { public protected *; }

# 保留 Xposed 兼容桥接（供旧模块使用）
-keep class org.lsposed.lspatch.compat.bridge.XposedBridgeCompat { public protected *; }
-keep class org.lsposed.lspatch.compat.bridge.MigrationTracker { public protected *; }
-keep class org.lsposed.lspatch.compat.bridge.FieldAccessBridge { public protected *; }

# 保留 Native Hook 相关 JNI 桥接
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 Kotlin 元数据以保证反射可用
-keepattributes Signature,*Annotation*,InnerClasses
-keep class kotlin.Metadata { *; }
