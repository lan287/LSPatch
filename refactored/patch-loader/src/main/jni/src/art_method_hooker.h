/*
 * This file is part of LSPatch 2.0.
 *
 * LSPatch 2.0 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Copyright (C) 2024 LSPatch 2.0 Contributors
 *
 * Refactored from: patch_loader.cpp
 * Changes:
 *   - Unified InlineHook and EntryPointReplacement into single IArtHook interface
 *   - Added JIT code cache invalidation (jit_code_cache.h)
 *   - Added SuspendAllThreads for thread-safe hook operations
 *   - Added PerformanceCounter instrumentation
 *   - Added structured error codes (HookError)
 *   - Added comprehensive error recovery (rollback on partial hook failure)
 */

#pragma once

#include <jni.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <shared_mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace lspd::v2 {

// ========================================================================
// Hook 类型
// ========================================================================

/** Hook 实现策略 */
enum class HookStrategy : uint8_t {
    /** 内联 Hook：替换目标方法的前 N 条指令为跳板 */
    INLINE = 0,
    /** 入口点替换：直接替换 ArtMethod 的 entry_point_from_quick_compiled_code_ */
    ENTRYPOINT_REPLACE = 1,
    /** 反射降级：通过 Java 反射实现（不修改 ART 内部结构） */
    SOFT_REFLECT = 2,
};

/** Hook 错误码 */
enum class HookError : int32_t {
    OK = 0,
    /** 目标方法为 null */
    TARGET_NULL = -1,
    /** 方法太短，无法内联 Hook */
    METHOD_TOO_SHORT = -2,
    /** 内存分配失败 */
    MEMORY_ALLOC_FAILED = -3,
    /** 线程挂起失败 */
    SUSPEND_THREADS_FAILED = -4,
    /** JIT 去优化失败 */
    DEOPTIMIZE_FAILED = -5,
    /** 已有冲突的 Hook */
    CONFLICTING_HOOK = -6,
    /** 引擎未初始化 */
    NOT_INITIALIZED = -7,
    /** 版本不支持 */
    VERSION_UNSUPPORTED = -8,
};

// ========================================================================
// Hook 配置
// ========================================================================

/** Hook 配置参数 */
struct HookConfig {
    /** 首选策略 */
    HookStrategy preferred_strategy = HookStrategy::INLINE;

    /** 是否启用 JIT 去优化 */
    bool enable_jit_deopt = true;

    /** 是否挂起所有线程（线程安全开关） */
    bool suspend_threads = true;

    /** 挂起超时（毫秒） */
    uint32_t suspend_timeout_ms = 100;

    /** 是否允许降级（Inline → EntryPoint → Soft） */
    bool allow_degradation = true;

    /** 最大内联 Hook 指令数（用于判断方法是否太短） */
    uint32_t min_method_size = 8;
};

// ========================================================================
// Hook 结果
// ========================================================================

/** Hook 句柄 */
struct ArtHookHandle {
    /** 唯一的 Hook ID */
    uint64_t id;

    /** 目标方法指针 */
    void* target;

    /** 实际使用的策略 */
    HookStrategy strategy;

    /** 原始入口点（用于 unhook 恢复） */
    void* original_entrypoint;

    /** 备份的原始指令（Inline Hook 模式） */
    std::vector<uint8_t> original_bytes;

    /** 回调函数指针 */
    void* callback;

    /** 是否处于激活状态 */
    std::atomic<bool> is_active{true};
};

/** Hook 操作结果 */
struct ArtHookResult {
    HookError error = HookError::OK;
    std::string error_message;
    std::unique_ptr<ArtHookHandle> handle;

    /** 便捷判断 */
    bool ok() const { return error == HookError::OK; }
};

// ========================================================================
// IArtMethodHooker 抽象接口
// ========================================================================

/**
 * ART Method Hook 抽象接口 — LSPatch 2.0 Native Hook 层的核心抽象。
 *
 * 统一了 Inline Hook 和 EntryPoint Replacement 两种策略。
 *
 * ### 线程安全保证
 * - hook() 和 unhook() 内部使用 SuspendAllThreads 挂起所有 Java 线程
 * - 使用 shared_mutex 保护 Hook 注册表
 * - Atomic<bool> 保护句柄状态
 *
 * ### JIT 兼容性
 * - hook() 前强制去优化目标方法
 * - 遍历 JIT Code Cache 清除已编译的 traces
 * - 设置目标方法为 kAccCompileDontBother 阻止重新编译
 *
 * ### 异常安全
 * - 所有资源使用 RAII（unique_ptr、scope guard）
 * - hook() 失败时自动恢复已修改的指令
 * - unhook() 幂等：重复调用无副作用
 */
class IArtMethodHooker {
public:
    virtual ~IArtMethodHooker() = default;

    // ----------------------------------------------------------------------
    // 生命周期
    // ----------------------------------------------------------------------

    /**
     * 初始化 Hook 引擎。
     *
     * @param env JNI 环境
     * @param art_handle ART 运行时句柄（libart.so 的基址）
     * @param config Hook 配置
     * @return 初始化结果
     */
    virtual ArtHookResult Init(JNIEnv* env, void* art_handle,
                               const HookConfig& config) = 0;

    /**
     * 关闭 Hook 引擎，释放所有资源。
     *
     * 自动 unhook 所有已注册的 Hook。
     */
    virtual void Shutdown() = 0;

    // ----------------------------------------------------------------------
    // Hook 操作
    // ----------------------------------------------------------------------

    /**
     * 注册一个方法 Hook。
     *
     * 按策略优先级尝试：
     *   1. INLINE (如果方法足够长)
     *   2. ENTRYPOINT_REPLACE (如果 INLINE 失败)
     *   3. SOFT_REFLECT (如果前两者都失败 + allow_degradation)
     *
     * @param target 目标 ArtMethod 指针
     * @param callback 回调跳板函数
     * @param user_data 用户数据（传递给回调）
     * @return Hook 结果，包含句柄
     */
    virtual ArtHookResult Hook(void* target, void* callback,
                                void* user_data = nullptr) = 0;

    /**
     * 移除一个 Hook。
     *
     * 恢复目标方法到原始状态，释放跳板内存。
     * 幂等操作：如果 handle 已失效则无操作。
     *
     * @param handle 要移除的 Hook 句柄
     * @return 操作结果
     */
    virtual ArtHookResult Unhook(ArtHookHandle& handle) = 0;

    /**
     * 移除目标方法上的所有 Hook。
     *
     * @param target 目标 ArtMethod 指针
     * @return 移除的 Hook 数量
     */
    virtual uint32_t UnhookAll(void* target) = 0;

    /**
     * 移除所有已注册的 Hook。
     *
     * @return 移除的 Hook 数量
     */
    virtual uint32_t UnhookAll() = 0;

    // ----------------------------------------------------------------------
    // 查询
    // ----------------------------------------------------------------------

    /** 是否已初始化 */
    virtual bool IsInitialized() const = 0;

    /** 获取当前活跃 Hook 数量 */
    virtual uint32_t ActiveHookCount() const = 0;

    /** 获取引擎名称 */
    virtual std::string EngineName() const = 0;
};

// ========================================================================
// ArtMethodHookerImpl — 具体实现
// ========================================================================

/**
 * IArtMethodHooker 的默认实现。
 *
 * 线程安全：使用 std::shared_mutex 保护 hook_registry_。
 * 异常安全：所有资源使用 RAII。
 */
class ArtMethodHookerImpl : public IArtMethodHooker {
public:
    ArtMethodHookerImpl() = default;
    ~ArtMethodHookerImpl() override { Shutdown(); }

    ArtHookResult Init(JNIEnv* env, void* art_handle,
                       const HookConfig& config) override;
    void Shutdown() override;

    ArtHookResult Hook(void* target, void* callback,
                       void* user_data = nullptr) override;
    ArtHookResult Unhook(ArtHookHandle& handle) override;
    uint32_t UnhookAll(void* target) override;
    uint32_t UnhookAll() override;

    bool IsInitialized() const override;
    uint32_t ActiveHookCount() const override;
    std::string EngineName() const override { return "ArtMethodHookerImpl"; }

private:
    // ── 策略实现 ──
    ArtHookResult TryInlineHook(void* target, void* callback, void* user_data);
    ArtHookResult TryEntryPointReplace(void* target, void* callback, void* user_data);
    ArtHookResult TrySoftReflect(void* target, void* callback, void* user_data);

    // ── JIT 兼容性 ──
    bool DeoptimizeMethod(void* target);
    bool InvalidateJitCodeCache(void* target);
    bool MarkMethodDontCompile(void* target);

    // ── 线程安全 ──
    bool SuspendAllThreads();
    void ResumeAllThreads();

    // ── 工具方法 ──
    uint32_t GetMethodSize(void* target) const;
    bool IsMethodCompiled(void* target) const;
    static uint64_t NextHookId();

    // ── 状态 ──
    std::atomic<bool> initialized_{false};
    JNIEnv* env_{nullptr};
    void* art_handle_{nullptr};
    HookConfig config_;

    mutable std::shared_mutex registry_mutex_;
    std::vector<std::unique_ptr<ArtHookHandle>> hook_registry_;

    static std::atomic<uint64_t> hook_id_counter_;
};

} // namespace lspd::v2