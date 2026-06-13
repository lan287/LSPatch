# LSPatch 1.x → 2.0 API 迁移指南

> **版本**: 2.0.0  
> **状态**: 草稿  
> **日期**: 2026-06-13

---

## 1. 概述

LSPatch 2.0 引入了新的 `HookProvider` 接口和 `ModuleLifecycle` 状态机来替代旧的直接 Xposed API 调用。本指南帮助模块开发者将现有 LSPatch 1.x / Xposed 模块迁移到 2.0 API。

**关键承诺**: 2.0 提供 `XposedBridgeCompat` 桥接层，旧 API 标注 `@Deprecated` 但仍可正常工作。迁移可以在模块迭代过程中渐进完成。

---

## 2. 模块入口变更

### 1.x 写法

```java
// 旧: 实现 IXposedHookLoadPackage
public class MyModule implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.target.app")) return;
        // 直接在这里 Hook
        XposedHelpers.findAndHookMethod(
            "com.target.app.MainActivity",
            lpparam.classLoader,
            "onCreate",
            Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    // ...
                }
            }
        );
    }
}
```

### 2.0 写法

```java
// 新: 实现 ModuleLifecycle
public class MyModule extends ModuleLifecycle {
    @Override
    public void onActivate(ModuleContext context) {
        if (!context.getPackageName().equals("com.target.app")) return;

        HookProvider provider = context.getHookProvider();
        ClassLoader cl = context.getClassLoader();

        provider.findAndHookMethod(
            "com.target.app.MainActivity",
            cl,
            "onCreate",
            new Class<?>[]{Bundle.class},
            new HookCallback() {
                @Override
                public void beforeCall(HookParam param) {
                    // 同 beforeHookedMethod
                }

                @Override
                public void afterCall(HookParam param) {
                    // 同 afterHookedMethod
                }
            }
        );
    }

    @Override
    protected ModuleMetadata getMetadata() {
        return new ModuleMetadata.Builder()
            .setPackageName("com.example.mymodule")
            .setMinApiLevel(26)
            .setRequiresFramework(false)
            .build();
    }
}
```

---

## 3. API 对照表

### 3.1 Hook 方法

| 操作 | 1.x (Xposed API) | 2.0 (新 API) |
|------|-----------------|-------------|
| Hook 方法 | `XposedHelpers.findAndHookMethod()` | `provider.findAndHookMethod()` |
| Hook 构造器 | `XposedHelpers.findAndHookConstructor()` | `provider.findAndHookConstructor()` |
| Hook 所有重载 | 手动遍历 | `provider.hookAllMethods()` |
| 取消 Hook | 不支持 | `handle.unhook()` |
| 检查是否已 Hook | 不支持 | `provider.isHooked()` |

**迁移示例**:

```java
// 1.x
XposedHelpers.findAndHookMethod(
    "com.target.Utils", cl, "doSomething", String.class, int.class,
    new XC_MethodHook() { ... }
);

// 2.0
HookHandle handle = provider.findAndHookMethod(
    "com.target.Utils", cl, "doSomething",
    new Class<?>[]{String.class, int.class},
    new HookCallback() { ... }
);
// 新能力: 可以显式取消
handle.unhook();
```

### 3.2 Hook 回调

| 1.x 回调方法 | 2.0 回调方法 | 说明 |
|-------------|-------------|------|
| `beforeHookedMethod(param)` | `beforeCall(param)` | 方法调用前 |
| `afterHookedMethod(param)` | `afterCall(param)` | 方法调用后 |
| `param.setResult(value)` | `param.setResult(value)` | 一致 |
| `param.getResult()` | `param.getResult()` | 一致 |
| `param.thisObject` | `param.getThisObject()` | getter 封装 |
| `param.args` | `param.getArgs()` | getter 封装 |
| `param.setObjectExtra()` | `param.setExtra()` | 简化命名 |
| `param.getObjectExtra()` | `param.getExtra()` | 简化命名 |
| 无 | `param.replaceResult(value)` | 新: 显式替换返回值 |

**回调类型**:

```java
// 2.0 新增回调模式
public enum HookCallbackType {
    BEFORE,     // 仅 before
    AFTER,      // 仅 after
    REPLACE     // 完全替换方法实现
}

// REPLACE 模式示例
provider.hookMethod(method, HookCallbackType.REPLACE, param -> {
    // 完全控制方法行为
    if (shouldBlock()) {
        param.setResult(null);
        return;
    }
    param.invokeOriginal(); // 手动调用原方法
});
```

### 3.3 字段访问

| 1.x API | 2.0 API |
|---------|---------|
| `XposedHelpers.getObjectField(obj, "field")` | `FieldAccessBridge.getObjectField(obj, "field")` |
| `XposedHelpers.setObjectField(obj, "field", val)` | `FieldAccessBridge.setObjectField(obj, "field", val)` |
| `XposedHelpers.getStaticObjectField(clazz, "field")` | `FieldAccessBridge.getStaticObjectField(clazz, "field")` |
| `XposedHelpers.findField(clazz, "field")` | `FieldAccessBridge.findField(clazz, "field")` |
| `XposedHelpers.getAdditionalInstanceField()` | 移除（不安全） |

### 3.4 模块元数据

| 1.x 方式 | 2.0 方式 |
|---------|---------|
| `xposed_init` 文件 | `getMetadata()` 方法 |
| `assets/xposed_init` 声明入口 | `ModuleMetadata.Builder` |
| 无版本限制 | `setMinApiLevel()` / `setMaxApiLevel()` |
| 无范围声明 | `setScope(new String[]{...})` |

```java
@Override
protected ModuleMetadata getMetadata() {
    return new ModuleMetadata.Builder()
        .setPackageName("com.example.mymodule")
        .setVersion("2.0.0")
        .setMinApiLevel(26)
        .setMaxApiLevel(35)
        .setScope(new String[]{"com.target.app", "com.target.app2"})
        .setDescription("My LSPatch 2.0 module")
        .build();
}
```

---

## 4. 生命周期变更

### 4.1 旧生命周期

1.x 模块生命周期是不明确的:
- `IXposedHookLoadPackage.handleLoadPackage()` — 每个应用启动时调用
- `IXposedHookZygoteInit.initZygote()` — Zygote 启动时调用一次
- 无卸载/反激活概念

### 4.2 新生命周期

2.0 使用显式状态机:

```
UNLOADED → LOADED → ACTIVE → INACTIVE → UNLOADED
```

```java
public class MyModule extends ModuleLifecycle {
    @Override
    public void onLoad(ModuleContext context) {
        // 模块加载，此时尚未激活
        // 可用于预初始化、缓存准备
    }

    @Override
    public void onActivate(ModuleContext context) {
        // 模块激活，此时可以 Hook
        // 主 Hook 逻辑放这里
    }

    @Override
    public void onDeactivate(ModuleContext context) {
        // 模块反激活，清理 Hook
        // HookProvider 会自动清理，这里做业务清理
    }

    @Override
    public void onUnload(ModuleContext context) {
        // 模块卸载，释放全部资源
    }
}
```

---

## 5. 破坏性变更

### 5.1 移除的 API

| 移除的 API | 原因 | 替代方案 |
|-----------|------|---------|
| `XposedHelpers.callMethod()` 直接调用 | 类型不安全 | 标准反射 `Method.invoke()` |
| `XposedHelpers.callStaticMethod()` | 类型不安全 | 标准反射 |
| `XposedHelpers.getAdditionalInstanceField()` | 内存泄漏风险 | `WeakHashMap` |
| `XposedHelpers.setAdditionalInstanceField()` | 内存泄漏风险 | `WeakHashMap` |
| `de.robv.android.xposed.XposedBridge.log()` | 耦合 Xposed 实现 | SLF4J / Android Log |
| `XC_LoadPackage.LoadPackageParam` | 结构冗余 | `ModuleContext` |

### 5.2 行为变更

| 变更 | 1.x 行为 | 2.0 行为 |
|------|---------|---------|
| Hook 失败处理 | 静默失败 | 抛出 `HookException`（可捕获） |
| 重复 Hook | 静默覆盖 | `HookProvider.unhook()` 幂等，需显式重 Hook |
| 模块加载顺序 | 不确定 | 按优先级排序 |
| 资源注入 | 自动 | 需显式调用 `ResourceInjector` |

---

## 6. 渐进式迁移

### 6.1 阶段一: 不修改代码

模块代码完全不变，依赖 `XposedBridgeCompat` 自动桥接。`MigrationTracker` 会统计使用了多少废弃 API。

```gradle
// build.gradle - 2.0 兼容模式
dependencies {
    implementation 'org.lsposed:lspatch-v2-api:2.0.0'
    // XposedBridgeCompat 随 API 包提供
}
```

### 6.2 阶段二: 入口迁移

将 `IXposedHookLoadPackage` 迁移到 `ModuleLifecycle`，内部仍使用旧 API。

```java
public class MyModule extends ModuleLifecycle {
    @Override
    public void onActivate(ModuleContext context) {
        // 仍使用旧 Xposed API
        XposedBridgeCompat.hookAllMethods(
            context.findClass("com.target.MainActivity"),
            "onCreate",
            new XC_MethodHook() { ... }
        );
    }
}
```

### 6.3 阶段三: 完全迁移

所有 API 切换到 2.0 原生接口，移除 `XposedBridgeCompat` 依赖。

### 6.4 迁移追踪

```java
// 在模块中主动追踪迁移进度
MigrationTracker tracker = context.getMigrationTracker();
Map<String, Long> stats = tracker.getStats();
System.out.println("仍需迁移的 API 调用: " + stats);
// 输出示例: {findAndHookMethod=12, getObjectField=8, callMethod=3}
```

---

## 7. 常见问题

### Q: 旧模块能否直接运行?
A: 可以。`XposedBridgeCompat` 保证 100% API 兼容，旧模块无需修改即可运行。但建议逐步迁移以获得更好的性能和稳定性。

### Q: 如何判断当前是 1.x 还是 2.0?
A: 使用 `ModuleContext.getApiVersion()`:

```java
if (context.getApiVersion() >= 20000) {
    // 2.0 特性可用
}
```

### Q: Hook 降级会影响我的模块吗?
A: 不影响。降级对内对外透明，模块代码无需关心底层 Hook 策略。

### Q: 多模块同时运行时的隔离?
A: 每个模块独立 `ModuleContext`，Hook 操作隔离，一个模块崩溃不影响其他模块。