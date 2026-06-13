# LSPatch 2.0 — 代码审查清单

> 审查日期: ________  
> 审查人: ________  
> 版本: 2.0.0-rc1  
> 审查范围: 所有重构后的模块 (compat/adapter, patch-loader, hook, native)

---

## 1. Native 代码内存安全 (C++17)

| # | 检查项 | 状态 | 发现 | 严重程度 |
|---|--------|------|------|----------|
| 1.1 | `new` / `malloc` 是否有对应的 `delete` / `free`？ | ☐ |  | 🔴 |
| 1.2 | `std::unique_ptr` / `std::shared_ptr` 是否替代了裸指针？ | ☐ |  | 🟡 |
| 1.3 | `std::vector<uint8_t>` 在 `original_bytes` 中是否有越界访问？ | ☐ |  | 🔴 |
| 1.4 | `reinterpret_cast` 是否安全？（ArtMethod* → void* 转换） | ☐ |  | 🟡 |
| 1.5 | `__builtin___clear_cache` 参数范围是否正确？ | ☐ |  | 🔴 |
| 1.6 | JNI 局部引用是否在循环中正确释放？（`env->DeleteLocalRef`） | ☐ |  | 🟡 |
| 1.7 | `std::memcpy` 目标缓冲区大小是否足够？ | ☐ |  | 🔴 |
| 1.8 | `dlopen` 是否有对应的 `dlclose` 或生命周期管理？ | ☐ |  | 🟡 |
| 1.9 | `SIGSEGV` 信号处理是否安全？ | ☐ |  | 🔴 |
| 1.10 | 字符串复制是否使用 `std::string` 而非裸 `char*`？ | ☐ |  | 🟡 |

**重点关注文件**：
- [ ] `art_method_hooker.cpp` — `TryInlineHook()` 中的 `std::memcpy` 和 `__builtin___clear_cache`
- [ ] `art_method_hooker.cpp` — `ArtMethodHookerImpl::Shutdown()` 中的资源释放
- [ ] `patch_main_refactored.cpp` — `dlopen("libart.so")` 的生命周期

---

## 2. 并发访问的锁机制

| # | 检查项 | 状态 | 发现 | 严重程度 |
|---|--------|------|------|----------|
| 2.1 | `std::shared_mutex` 是否读写分离？（读用 shared_lock，写用 unique_lock） | ☐ |  | 🔴 |
| 2.2 | `std::atomic<bool>` 是否使用正确的 memory_order？（acquire/release） | ☐ |  | 🟡 |
| 2.3 | `SuspendAllThreads()` 失败时是否有超时回退？ | ☐ |  | 🔴 |
| 2.4 | `hook_registry_` 是否在所有访问路径中加锁？ | ☐ |  | 🔴 |
| 2.5 | Java 层 `AtomicReference` 是否替代了非线程安全的静态字段？ | ☐ |  | 🟡 |
| 2.6 | `ConcurrentHashMap` 是否在 `handleMap` 中使用？ | ☐ |  | 🟡 |
| 2.7 | `CopyOnWriteArrayList` 是否用于 `listeners` 列表？ | ☐ |  | 🟡 |
| 2.8 | `volatile` 是否用于跨线程可见的标志位？ | ☐ |  | 🟡 |
| 2.9 | 是否存在 `check-then-act` 竞态条件？ | ☐ |  | 🔴 |
| 2.10 | 双重检查锁 (DCL) 是否使用 `volatile` 或 `atomic`？ | ☐ |  | 🟡 |

**重点关注文件**：
- [ ] `art_method_hooker.h` — `hook_registry_` 的 `shared_mutex` 保护
- [ ] `ZygoteHookManager.java` — `AtomicReference<Phase>` 的状态转换
- [ ] `ArtMethodHooker.java` — `handleMap` 的并发安全性

---

## 3. 异常路径的资源释放

| # | 检查项 | 状态 | 发现 | 严重程度 |
|---|--------|------|------|----------|
| 3.1 | `ScopeGuard` / RAII 是否在异常路径中正确释放资源？ | ☐ |  | 🔴 |
| 3.2 | `try-finally` 或 `try-with-resources` 是否覆盖所有 I/O 操作？ | ☐ |  | 🟡 |
| 3.3 | `rollback()` 是否在 `ZygoteHookManager` 中处理了所有阶段？ | ☐ |  | 🔴 |
| 3.4 | `Shutdown()` 是否在异常路径中调用 `UnhookAll()`？ | ☐ |  | 🔴 |
| 3.5 | `InjectionHandle.unload()` 是否在异常路径中调用？ | ☐ |  | 🟡 |
| 3.6 | `SharedMemory.close()` 是否在 `Result.failure` 后执行？ | ☐ |  | 🟡 |
| 3.7 | `ZipFile` 是否在 `try-with-resources` 中打开？ | ☐ |  | 🟡 |
| 3.8 | `FileOutputStream` 是否在异常路径中关闭？ | ☐ |  | 🟡 |
| 3.9 | Native 层 `ScopedTimer` 的析构函数是否 noexcept？ | ☐ |  | 🟡 |
| 3.10 | `ResumeAllThreads()` 是否在 `SuspendAllThreads()` 失败后仍被调用？ | ☐ |  | 🔴 |

**重点关注文件**：
- [ ] `ZygoteHookManager.java` — `rollback()` 方法覆盖所有 Phase
- [ ] `art_method_hooker.cpp` — `ScopeGuard` 的 `resume_guard` 路径
- [ ] `ModuleLoaderImpl.java` — `onLoad()` 失败时的资源清理

---

## 4. SELinux 上下文切换 / 安全策略

| # | 检查项 | 状态 | 发现 | 严重程度 |
|---|--------|------|------|----------|
| 4.1 | `isSelinuxEnforcing()` 是否有对应的 `setSelinuxEnforcing()` 调用？ | ☐ |  | 🔴 |
| 4.2 | `getSelinuxContext()` 是否在切换后恢复？ | ☐ |  | 🔴 |
| 4.3 | 读写 `/proc/self/attr/current` 是否在 try-catch 中？ | ☐ |  | 🟡 |
| 4.4 | `mprotect` 调用是否在 SELinux `neverallow` 规则下安全？ | ☐ |  | 🔴 |
| 4.5 | 文件权限修改 (`Os.chmod`) 是否被 SELinux 审计？ | ☐ |  | 🟡 |
| 4.6 | `execmem` 限制下是否提供降级路径？ | ☐ |  | 🔴 |
| 4.7 | Native 库加载路径是否在 SELinux 允许的域内？ | ☐ |  | 🟡 |
| 4.8 | 是否在 `sepolicy-inject` 之前检查了 ROM 兼容性？ | ☐ |  | 🟡 |
| 4.9 | `RomAdapter.allowsNativeHookWithoutPtrace()` 是否影响策略选择？ | ☐ |  | 🟡 |
| 4.10 | 华为/荣耀的 `HUAWEI_SPECIAL_SELINUX` 特性是否触发降级？ | ☐ |  | 🟡 |

**重点关注文件**：
- [ ] `VersionAdapter.java` — `isSelinuxEnforcing()` / `getSelinuxContext()`
- [ ] `bypass_sig.cpp` — `__openat` inline hook 的 `mprotect` 调用
- [ ] `RomAdapter.java` — `getSelinuxStrictnessLevel()` 和相关策略

---

## 5. 签名验证 / 安全认证

| # | 检查项 | 状态 | 发现 | 严重程度 |
|---|--------|------|------|----------|
| 5.1 | `SigBypass.doSigBypass()` 是否在所有版本中都有安全回退？ | ☐ |  | 🔴 |
| 5.2 | `TrustStore.verify()` 是否检查证书链完整性？ | ☐ |  | 🔴 |
| 5.3 | `SecureChannel` 的 Token 是否有过期时间？ | ☐ |  | 🟡 |
| 5.4 | `RemoteApplicationService` 的 `TODO: Authentication` 是否已解决？ | ☐ |  | 🔴 |
| 5.5 | `ModuleService.onBind()` 是否校验了调用者 UID 和签名？ | ☐ |  | 🔴 |
| 5.6 | `Signature` 对象比较是否使用 `equals()` 而非 `==`？ | ☐ |  | 🟡 |
| 5.7 | 签名绕过级别 `sigBypassLevel` 是否所有值都是安全的？ | ☐ |  | 🟡 |
| 5.8 | `IdentityProof` 中是否包含防重放攻击的 nonce？ | ☐ |  | 🟡 |

---

## 6. 版本兼容性 / 降级路径

| # | 检查项 | 状态 | 发现 | 严重程度 |
|---|--------|------|------|----------|
| 6.1 | `VersionAdapterFactory.create()` 是否在所有 SDK 26-35 上返回有效适配器？ | ☐ |  | 🔴 |
| 6.2 | `SoftFallbackAdapter` 是否在所有未知 SDK 上可用？ | ☐ |  | 🟡 |
| 6.3 | 反射字段名 (`mPackages`, `mBoundApplication` 等) 是否在 Android 版本间稳定？ | ☐ |  | 🔴 |
| 6.4 | `ArtMethod` 字段偏移是否在 SDK 版本间验证？ | ☐ |  | 🔴 |
| 6.5 | `InMemoryDexClassLoader` 是否在 SDK 26 以下优雅降级？ | ☐ |  | 🟡 |
| 6.6 | `AppComponentFactory` 是否在 SDK 29 以下正确跳过？ | ☐ |  | 🟡 |
| 6.7 | `userfaultfd` GC 兼容性是否在初始化时检测？ | ☐ |  | 🟡 |

---

## 7. 审查结论

| 结论 | 选择 |
|------|------|
| ☐ 通过 — 无严重问题 | |
| ☐ 有条件通过 — 存在 __ 项非阻塞问题 | |
| ☐ 需要修改 — 存在 __ 项阻塞问题 | |
| ☐ 拒绝 — 需要重新设计 | |

**阻塞问题列表** (如有):

| # | 问题描述 | 文件:行号 | 建议修复 |
|---|----------|-----------|----------|
| 1 | | | |
| 2 | | | |
| 3 | | | |

**非阻塞建议** (如有):

| # | 建议 | 文件:行号 |
|---|------|-----------|
| 1 | | |
| 2 | | |

---

**审查签名**: ________  
**审查日期**: ________