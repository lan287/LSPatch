# LSPatch 2.0 架构变更说明

> **版本**: 2.0.0  
> **状态**: 草稿  
> **日期**: 2026-06-13

---

## 1. 概述

LSPatch 2.0 是对原有框架的全面架构重构，核心目标是将单体耦合的模块加载逻辑拆分为清晰分层的四层架构，同时建立跨 Android 8-15 的版本适配体系和多 ROM 厂商兼容层。

### 1.1 重构动机

| 问题 | 1.x 状态 | 2.0 解决方案 |
|------|----------|-------------|
| 模块加载与 Zygote 进程强耦合 | `LSPApplication` 在 Zygote 中执行大量初始化 | 分离为 `ZygoteHookManager`（生命周期管理）+ `ModuleLoaderImpl`（纯加载逻辑） |
| 硬编码 SDK 版本判断 | 散落 `Build.VERSION.SDK_INT` if-else | 策略模式 `VersionAdapter` 体系，每个 Android 版本独立适配器 |
| Native/Java Hook 无统一抽象 | C++ 直接反射调用 Java 层 | `HookProvider` 接口 + `ArtMethodHooker` 实现层 |
| 无 ROM 厂商适配 | 对小米/华为等定制 ROM 兼容靠补丁 | `RomDetector` 系统属性检测 + `RomAdapter` 特性适配 |
| Xposed API 与内部实现耦合 | 模块直接依赖 XposedBridge | `XposedBridgeCompat` 桥接层 + `MigrationTracker` 追踪 |

### 1.2 兼容性承诺

- **Android 版本**: 8.0 (API 26) ~ 15 (API 35)
- **架构**: arm64-v8a, armeabi-v7a（x86/x86_64 实验性支持）
- **ROM 厂商**: AOSP / MIUI / HyperOS / HarmonyOS / OneUI / ColorOS / FuntouchOS / Flyme / MyOS
- **Xposed API**: 100% 兼容（通过 `@Deprecated` 桥接），旧模块无需修改可运行

---

## 2. 四层架构

```
┌──────────────────────────────────────────────────────────────┐
│                     APPLICATION LAYER                         │
│              (宿主 APK / 模块 APK)                             │
├──────────────────────────────────────────────────────────────┤
│                      BRIDGE 层                                │
│  ┌─────────────────┐  ┌──────────────┐  ┌────────────────┐   │
│  │ XposedBridge     │  │ Resource      │  │ IPC Bridge      │  │
│  │ Compat            │  │ Injector      │  │ (Binder/Socket) │  │
│  └─────────────────┘  └──────────────┘  └────────────────┘   │
├──────────────────────────────────────────────────────────────┤
│                      MODULE 层                                │
│  ┌─────────────────┐  ┌──────────────┐  ┌────────────────┐   │
│  │ ModuleLifecycle  │  │ ModuleLoader  │  │ ModuleValidator │  │
│  │ (状态机)          │  │ Impl          │  │ (签名校验)       │  │
│  └─────────────────┘  └──────────────┘  └────────────────┘   │
├──────────────────────────────────────────────────────────────┤
│                       HOOK 层                                 │
│  ┌─────────────────┐  ┌──────────────┐  ┌────────────────┐   │
│  │ HookProvider      │  │ ArtMethod     │  │ StrategyChain   │  │
│  │ (抽象接口)         │  │ Hooker (C++)  │  │ (降级链)         │  │
│  └─────────────────┘  └──────────────┘  └────────────────┘   │
├──────────────────────────────────────────────────────────────┤
│                      LOADER 层                                │
│  ┌─────────────────┐  ┌──────────────┐  ┌────────────────┐   │
│  │ ZygoteHook       │  │ VersionAdapter│  │ RomAdapter      │  │
│  │ Manager           │  │ (策略选择)     │  │ (厂商适配)       │  │
│  └─────────────────┘  └──────────────┘  └────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 2.1 Loader 层

**职责**: 环境准备、进程注入、版本/ROM 适配、安全上下文初始化。

| 组件 | 类型 | 说明 |
|------|------|------|
| `ZygoteHookManager` | Java | Zygote 进程 Hook 入口，管理 nativeForkAndSpecialize/postForkChild |
| `VersionAdapter` | 抽象类 | Android 版本差异隔离，每个版本独立子类 |
| `VersionAdapterFactory` | 工厂 | 根据 `Build.VERSION.SDK_INT` 返回对应适配器 |
| `RomDetector` | Java | 通过系统属性检测 ROM 厂商 |
| `RomAdapter` | Java | 读取 ROM 特性集，调整 Hook 策略 |

**关键流程**:

```
Zygote 进程启动
    │
    ├── RomDetector.detect()         → 识别 ROM 厂商
    ├── VersionAdapterFactory.create() → 选择版本适配器
    ├── RomAdapter.init()             → 加载 ROM 特性集
    ├── ZygoteHookManager.init()      → 注册 nativeForkAndSpecialize Hook
    │
    └── [app_process fork]
        ├── postForkChild()
        ├── ModuleLoaderImpl.load()   → 加载模块
        └── ModuleLifecycle.onActivate() → 激活模块
```

### 2.2 Hook 层

**职责**: 提供统一的 ART Method Hook 能力，隔离 Native/Java 实现差异。

| 组件 | 层级 | 说明 |
|------|------|------|
| `HookProvider` | 接口（Java） | `hookMethod()`, `unhook()`, `isSupported()` |
| `ArtMethodHooker` | 实现（C++/Java） | ART 内联 Hook，操纵 ArtMethod.entry_point_from_quick_compiled_code_ |
| `StrategyChain` | Java | 三级降级: ART_INLINE → LSPLANT_V2 → DEX_PILOT → ENTRYPOINT_REPLACE → SOFT_REFLECT |

**Hook 降级链**:

```
尝试 ART_INLINE Hook
    │
    ├── 成功 → Hook 注册完毕
    │
    └── 失败（Android 版本/ROM 不支持）
        └── 尝试 LSPLANT_V2
            │
            ├── 成功 → 记录降级原因
            │
            └── 失败
                └── 尝试 DEX_PILOT
                    │
                    ├── 成功 → 记录降级原因
                    │
                    └── 失败
                        └── 尝试 ENTRYPOINT_REPLACE
                            │
                            └── 失败 → SOFT_REFLECT (最终兜底)
```

**Native 层架构** (`art_method_hooker.cpp`):

```
JNI_OnLoad()
    ├── GetArtMethodSize()           → 计算 ArtMethod 结构体大小
    ├── ResolveArtMethodOffsets()    → 解析各字段偏移
    ├── InitTrampolineAllocator()    → 初始化跳板内存分配器
    └── RegisterHookEngine()         → 注册 Hook 引擎（线程安全初始化）

DoHook(ArtMethod* method, callback)
    ├── ValidateArtMethod(method)    → 校验方法有效性
    ├── SuspendAllThreads()          → 暂停所有线程
    ├── SaveOriginalEntrypoint()     → 保存原始入口点
    ├── PatchEntrypoint(trampoline)  → 写入跳板地址
    ├── ResumeAllThreads()           → 恢复线程
    ├── FlushCache(method)           → 刷新指令缓存
    └── Return HookHandle            → 返回 Hook 句柄
```

### 2.3 Module 层

**职责**: 标准化模块生命周期管理，提供沙箱化加载和签名验证。

**生命周期状态机**:

```
                    load()
  UNLOADED ─────────────────────→ LOADED
     ^                              │
     │                              │ activate()
     │                              ▼
     │                           ACTIVE
     │                              │
     │                              │ deactivate()
     │                              ▼
     │                          INACTIVE
     │                              │
     └──────────────────────────────┘
               unload()
```

| 组件 | 说明 |
|------|------|
| `ModuleLifecycle` | 抽象类，定义 `onLoad()` / `onActivate()` / `onDeactivate()` / `onUnload()` |
| `ModuleLoaderImpl` | 实现类，负责 APK 解析、Dex 加载、资源注入、类加载 |
| `ModuleValidator` | 签名验证链：APK 签名块 → 证书链解析 → 信任锚比对 → 权限声明校验 |

**模块签名验证链**:

```
APK 文件
    │
    ├── ApkSignatureSchemeV2/V3 解析
    │
    ├── 提取签名证书链
    │
    ├── 与预置信任锚（TrustAnchor）比对
    │     └── 不匹配 → ✅ 安全模块密钥验证
    │     └── 匹配   → 继续下一步
    │
    ├── AndroidManifest 权限声明校验
    │     └── 未声明必要权限 → 拒绝加载
    │
    └── 通过 → 允许加载模块
```

### 2.4 Bridge 层

**职责**: 向上层模块提供统一的 API 桥接、资源注入和跨进程通信。

| 组件 | 说明 |
|------|------|
| `XposedBridgeCompat` | 旧 Xposed API (`XposedHelpers.findAndHookMethod`) 到新接口的映射 |
| `HookParam` | Hook 参数封装，兼容 `XC_MethodHook.MethodHookParam` |
| `FieldAccessBridge` | 字段访问桥接，兼容 `XposedHelpers.getObjectField` 等 |
| `MigrationTracker` | 统计废弃 API 调用，生成迁移报告 |
| `ResourceInjector` | 资源注入接口，将模块资源合并到宿主 Resources |

---

## 3. 版本适配体系

### 3.1 适配器矩阵

| 适配器 | 覆盖版本 | ART 特性 | 推荐策略 |
|--------|---------|---------|---------|
| `Android8Adapter` | API 26-27 | 无 JIT 内联约束 | ART_INLINE |
| `Android9Adapter` | API 28 | Profile-guided JIT | DEX_PILOT |
| `Android10Adapter` | API 29 | JIT 编译线程池 | LSPLANT_V2 |
| `Android11Adapter` | API 30 | APEX 模块化 | LSPLANT_V2 |
| `Android12Adapter` | API 31-32 | ArtMethod 结构变更 | ART_INLINE（增强） |
| `Android13Adapter` | API 33 | Runtime app enum | ENTRYPOINT_REPLACE |
| `Android14Adapter` | API 34 | 16KB 页大小 | ART_INLINE（对齐修复） |
| `Android15Adapter` | API 35 | 最新 API 限制 | DEX_PILOT |
| `SoftFallbackAdapter` | 未来版本 | 纯反射 | SOFT_REFLECT |

### 3.2 VersionAdapter 基类设计

```java
public abstract class VersionAdapter {
    // 核心抽象方法
    protected abstract HookStrategy getDefaultStrategy();
    protected abstract HiddenApiLevel getHiddenApiAccess();
    protected abstract Set<String> getForbiddenClasses();

    // 模板方法
    public final void prepareHookEnvironment() {
        bypassHiddenApiRestrictions(getHiddenApiAccess());
        warmupCriticalClasses();
        verifyArtCompatibility();
    }

    // 反射工具方法
    protected static Object reflectGet(Class<?> clazz, String field, Object instance);
    protected static void reflectSet(Class<?> clazz, String field, Object instance, Object value);
    protected static Method reflectMethod(Class<?> clazz, String method, Class<?>... params);
}
```

---

## 4. ROM 兼容性层

### 4.1 ROM 检测机制

**检测优先级**: 系统属性 > 特征文件 > 特征类 > 启发式

| ROM | 检测属性 | 特征 |
|------|---------|------|
| MIUI/HyperOS | `ro.miui.ui.version.*` | 定制 ArtMethod 布局、额外 SELinux 策略 |
| HarmonyOS | `ro.build.version.emui`, `ro.config.hw_optb` | OpenHarmony ArtMethod 偏移差异 |
| OneUI (Samsung) | `ro.build.PDA` | Knox 安全限制、额外 Seccomp 规则 |
| ColorOS/OxygenOS | `ro.build.version.opporom` | 自定义系统类加载器 |
| FuntouchOS (Vivo) | `ro.vivo.os.version`, `ro.iqoo.os.version` | 系统属性访问限制 |
| Flyme (Meizu) | `ro.build.display.id` | 额外 JVM 参数 |
| MyOS (ZTE) | `persist.sys.zte_config` | 类加载顺序差异 |

### 4.2 RomFeature 特性集

```java
RomFeatureSet features = RomAdapter.getFeatures();

if (features.has(RomFeature.CUSTOM_ARTMETHOD_LAYOUT)) {
    // 加载该 ROM 对应的 ArtMethod 偏移配置
}

if (features.has(RomFeature.RESTRICTED_PTRACE)) {
    // 使用备选线程挂起方案
}

if (features.has(RomFeature.EXTRA_SECCOMP_FILTERS)) {
    // 调整系统调用白名单
}
```

---

## 5. 线程安全与并发模型

### 5.1 C++ 层

| 结构 | 保护机制 | 粒度 |
|------|---------|------|
| `g_hook_map` | `std::shared_mutex`（读写锁） | Hook 表全局 |
| `g_trampoline_allocator` | `std::mutex` | 内存分配 |
| ArtMethod 写入 | `SuspendAllThreads` + `std::atomic` | 每条方法 |
| 初始化标志 | `std::once_flag` + `std::call_once` | 一次性 |

### 5.2 Java 层

| 结构 | 保护机制 |
|------|---------|
| `ZygoteHookManager.hooks` | `ConcurrentHashMap<String, HookHandle>` |
| `ModuleLoaderImpl.loadedModules` | `CopyOnWriteArrayList<ModuleContext>` |
| `ModuleLifecycle.state` | `AtomicReference<ModuleState>` |
| `MigrationTracker.counters` | `ConcurrentHashMap<String, AtomicLong>` |

---

## 6. 安全性架构

### 6.1 模块签名验证

```
APK Signature Scheme V2/V3
    │
    ├── 解析签名块
    ├── 提取 X.509 证书链
    ├── 验证证书链完整性
    ├── 比对信任锚（SHA-256）
    └── 校验 AndroidManifest 权限声明
```

### 6.2 SELinux 上下文管理

- 使用 `setcon` 切换到模块安全上下文
- 仅在兼容 ROM 特性集允许时切换
- 操作完成后恢复原始上下文
- 失败时回退到当前上下文并记录警告

### 6.3 异常安全

- **C++**: RAII `ScopeGuard` 确保资源在异常路径释放
- **Java**: try-with-resources + `ZygoteHookManager.rollback()` 回滚机制
- **关键路径**: 每个 `native` 方法调用包裹 try-catch，确保 Java 层可感知 Native 异常

---

## 7. 性能设计

### 7.1 关键性能指标

| 指标 | 目标 | 实现手段 |
|------|------|---------|
| Hook 注册延迟 | < 5ms | 内存池分配跳板、批量 Suspend |
| Hook 方法调用开销 | < 10μs | 内联跳板、避免 JNI 跨越 |
| 并发 Hook 吞吐量 | > 1000 hooks/s | 读写锁分离、无锁原子操作 |
| 每 Hook 内存占用 | < 1KB | 紧凑跳板布局、共享只读区 |

### 7.2 性能计数器

内置性能计数器，可在 Debug 构建中启用：

```
LSPatch_Perf_HookRegister_us       (Hook 注册耗时)
LSPatch_Perf_MethodCall_ns          (Hook 方法调用平均耗时)
LSPatch_Perf_ThreadSuspend_us       (线程挂起耗时)
LSPatch_Perf_ModuleLoad_ms          (模块加载耗时)
```

---

## 8. 与 1.x 的关键差异

| 维度 | 1.x | 2.0 |
|------|-----|-----|
| 架构分层 | 单体耦合 | 四层解耦 |
| Hook 抽象 | 无统一接口 | HookProvider 接口 |
| 版本适配 | 硬编码 if-else | 策略模式 VersionAdapter |
| ROM 适配 | 补丁式修复 | RomDetector + RomAdapter |
| 模块生命周期 | 隐式状态 | ModuleLifecycle 显式状态机 |
| Xposed API | 直接依赖 | 桥接层 + MigrationTracker |
| 线程安全 | 缺少保障 | C++ shared_mutex + Java ConcurrentHashMap |
| 异常安全 | 无保障 | RAII ScopeGuard + rollback |
| 签名验证 | 基础校验 | 完整验证链 |

---

## 9. 目录结构

```
workspace/
├── lspatch-v2-api/          # 核心接口定义层
│   └── src/main/kotlin/org/lsposed/lspatch/v2/
│       ├── hook/HookProvider.kt
│       ├── module/ModuleLifecycle.kt
│       ├── adapter/VersionAdapter.kt
│       └── resource/ResourceInjector.kt
│
├── refactored/patch-loader/  # 核心实现层
│   └── src/main/
│       ├── java/org/lsposed/lspatch/loader/
│       │   ├── ZygoteHookManager.java
│       │   └── ModuleLoaderImpl.java
│       └── jni/src/
│           ├── art_method_hooker.h
│           ├── art_method_hooker.cpp
│           └── patch_main_refactored.cpp
│
├── compat/                   # 兼容层
│   └── src/main/java/org/lsposed/lspatch/compat/
│       ├── adapter/          # 版本适配器
│       ├── rom/              # ROM 兼容
│       └── bridge/           # Xposed API 桥接
│
└── qa/                       # 质量保障
    ├── test/unit/
    ├── test/integration/
    ├── test/perf/
    ├── review/
    ├── docs/
    └── release/
```