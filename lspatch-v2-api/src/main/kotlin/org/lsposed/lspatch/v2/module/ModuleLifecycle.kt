package org.lsposed.lspatch.v2.module

import android.content.pm.ApplicationInfo
import org.lsposed.lspatch.v2.hook.HookProvider

// ============================================================================
// 模块状态
// ============================================================================

/**
 * 模块生命周期状态。
 *
 * 状态转换遵循以下有限状态机：
 * ```
 *                      ┌─────────────┐
 *                      │  UNLOADED   │ ◄──────────────────────────────────┐
 *                      └──────┬──────┘                                    │
 *                             │ onLoad()                                  │
 *                             ▼                                           │
 *                      ┌─────────────┐                                    │
 *                      │   LOADED    │                                    │
 *                      └──────┬──────┘                                    │
 *                             │ onHook()                                  │
 *                             ▼                                           │
 *                      ┌─────────────┐     onDeactivate()     ┌──────────┐│
 *                      │   ACTIVE    │ ──────────────────────► │ INACTIVE ││
 *                      └──────┬──────┘                         └────┬─────┘│
 *                             │ onUnload()                         │       │
 *                             ▼                                    │       │
 *                      ┌─────────────┐                              │       │
 *                      │  UNLOADING  │ ◄────────────────────────────┘       │
 *                      └──────┬──────┘                                      │
 *                             │ (完成)                                      │
 *                             └─────────────────────────────────────────────┘
 * ```
 */
enum class ModuleStatus {
    /** 未加载：模块尚未被加载到内存中 */
    UNLOADED,

    /** 已加载：模块 APK 已解析，DEX 已预加载，但尚未注册 Hook */
    LOADED,

    /** 已激活：模块已注册 Hook，正在运行中 */
    ACTIVE,

    /** 已停用：模块 Hook 已移除，但资源尚未释放 */
    INACTIVE,

    /** 正在卸载：模块正在释放资源 */
    UNLOADING,

    /** 错误状态：模块加载或运行过程中发生不可恢复的错误 */
    ERROR,
}

// ============================================================================
// 模块上下文
// ============================================================================

/**
 * 模块运行时上下文，在 [ModuleLifecycle.onLoad] 成功后创建。
 *
 * 包含模块运行所需的所有环境信息，在模块生命周期内保持不变。
 *
 * @property packageName 模块 APK 的包名
 * @property modulePath 模块 APK 文件路径
 * @property classLoader 模块专属的隔离 ClassLoader
 * @property appInfo 宿主应用的 ApplicationInfo
 * @property processName 当前进程名
 * @property cacheDir 模块缓存目录
 * @property dataDir 模块数据目录
 * @property prefsDir 模块 SharedPreferences 目录
 */
data class ModuleContext(
    val packageName: String,
    val modulePath: String,
    val classLoader: ClassLoader,
    val appInfo: ApplicationInfo,
    val processName: String,
    val cacheDir: String,
    val dataDir: String,
    val prefsDir: String,
)

// ============================================================================
// 模块错误
// ============================================================================

/**
 * 模块错误信息，描述模块加载/运行过程中发生的错误。
 *
 * @property code 错误码，用于程序化处理
 * @property message 人类可读的错误描述
 * @property cause 原始异常（如有）
 * @property recoverable 是否可恢复（true 表示可通过重试恢复）
 */
data class ModuleError(
    val code: ModuleErrorCode,
    val message: String,
    val cause: Throwable? = null,
    val recoverable: Boolean = false,
)

/**
 * 模块错误码枚举。
 *
 * 每个错误码对应一种已知的失败场景，便于调用方进行针对性处理。
 */
enum class ModuleErrorCode {
    /** APK 文件不存在或损坏 */
    APK_NOT_FOUND,

    /** APK 签名验证失败 */
    SIGNATURE_VERIFICATION_FAILED,

    /** 模块 Manifest 解析失败 */
    MANIFEST_PARSE_FAILED,

    /** DEX 预加载失败 */
    DEX_LOAD_FAILED,

    /** Native 库加载失败 */
    NATIVE_LIB_LOAD_FAILED,

    /** 入口类未找到 */
    ENTRY_CLASS_NOT_FOUND,

    /** 入口类实例化失败 */
    ENTRY_CLASS_INSTANTIATION_FAILED,

    /** 模块权限不足 */
    PERMISSION_DENIED,

    /** Hook 注册失败 */
    HOOK_REGISTRATION_FAILED,

    /** 模块与当前 ART 版本不兼容 */
    ART_VERSION_INCOMPATIBLE,

    /** 模块内部异常（onLoad / onHook 回调中抛出） */
    INTERNAL_ERROR,

    /** 模块依赖缺失 */
    DEPENDENCY_MISSING,

    /** 未知错误 */
    UNKNOWN,
}

// ============================================================================
// 模块状态上报
// ============================================================================

/**
 * 模块状态变更事件，由 [ModuleLifecycle] 产生，供上层（Bridge 层）上报给 Manager。
 *
 * @property moduleId 模块唯一标识
 * @property status 当前状态
 * @property previousStatus 前一状态（用于追踪状态转换）
 * @property timestamp 状态变更时间戳（毫秒）
 * @property error 如果状态为 [ModuleStatus.ERROR]，包含错误详情
 * @property extra 附加信息（如已注册 Hook 数量、已注入资源数量等）
 */
data class ModuleStatusReport(
    val moduleId: String,
    val status: ModuleStatus,
    val previousStatus: ModuleStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val error: ModuleError? = null,
    val extra: Map<String, String> = emptyMap(),
)

// ============================================================================
// 模块状态监听器
// ============================================================================

/**
 * 模块状态变更监听器。
 *
 * 在模块生命周期各阶段转换时被调用，用于日志记录、Metrics 上报、错误追踪等。
 */
fun interface ModuleStatusListener {
    /**
     * 模块状态发生变更时回调。
     *
     * 注意：此回调在模块生命周期状态机的方法调用线程中执行，不应执行耗时操作。
     *
     * @param report 状态变更报告
     */
    fun onStatusChanged(report: ModuleStatusReport)
}

// ============================================================================
// 加载参数
// ============================================================================

/**
 * 传递给 [ModuleLifecycle.onHook] 的加载参数，
 * 对应 Xposed API 中的 [LoadPackageParam]。
 *
 * @property packageName 被加载的宿主应用包名
 * @property processName 当前进程名
 * @property classLoader 宿主应用的 ClassLoader
 * @property appInfo 宿主应用的 ApplicationInfo
 * @property isFirstApplication 是否为首次启动
 * @property hookProvider 当前可用的 Hook 提供者实例
 */
data class LoadPackageParam(
    val packageName: String,
    val processName: String,
    val classLoader: ClassLoader,
    val appInfo: ApplicationInfo,
    val isFirstApplication: Boolean,
    val hookProvider: HookProvider,
)

// ============================================================================
// ModuleLifecycle 接口
// ============================================================================

/**
 * 模块生命周期接口 —— LSPatch 2.0 Module 层的核心抽象。
 *
 * 定义了模块从加载到卸载的完整生命周期，包含错误处理和状态上报机制。
 *
 * ### 生命周期
 * 1. **[onLoad]** — 加载模块 APK，解析 DEX，创建隔离 ClassLoader，构建 [ModuleContext]
 * 2. **[onHook]** — 接收 [LoadPackageParam]，触发模块入口类的 `handleLoadPackage` 回调
 * 3. **[onDeactivate]** — 移除所有已注册的 Hook（可选，通常由 [onUnload] 直接触发）
 * 4. **[onUnload]** — 释放所有资源，卸载模块
 *
 * ### 错误处理
 * 每个生命周期方法通过 [Result] 类型返回结果。
 * 失败时返回 [ModuleError]，包含错误码和处理建议。
 *
 * ### 状态上报
 * 每次状态变更时，通过 [ModuleStatusListener] 回调通知上层。
 *
 * ### 使用示例
 * ```kotlin
 * val lifecycle: ModuleLifecycle = ...
 *
 * lifecycle.addStatusListener { report ->
 *     Log.i("Module", "${report.moduleId}: ${report.previousStatus} -> ${report.status}")
 * }
 *
 * when (val result = lifecycle.onLoad("/path/to/module.apk")) {
 *     is Result.Success -> {
 *         val ctx = result.value
 *         lifecycle.onHook(ctx, LoadPackageParam(...))
 *     }
 *     is Result.Failure -> {
 *         Log.e("Module", "Load failed: ${result.error.message}")
 *     }
 * }
 * ```
 */
interface ModuleLifecycle {

    // --------------------------------------------------------------------------
    // 生命周期方法
    // --------------------------------------------------------------------------

    /**
     * 加载模块。
     *
     * 执行以下操作：
     * 1. 验证模块 APK 的签名
     * 2. 解析模块 Manifest，提取入口类列表
     * 3. 预加载模块 DEX 到内存
     * 4. 创建隔离的 ClassLoader
     * 5. 构建 [ModuleContext]
     *
     * 成功后状态变为 [ModuleStatus.LOADED]。
     *
     * @param modulePath 模块 APK 文件的绝对路径
     * @param parentClassLoader 父 ClassLoader（通常为 LSPatch Bridge API 的 ClassLoader）
     * @return 成功时返回 [ModuleContext]；失败时返回 [ModuleError]
     */
    fun onLoad(
        modulePath: String,
        parentClassLoader: ClassLoader,
    ): Result<ModuleContext>

    /**
     * 注册 Hook。
     *
     * 在 [onLoad] 成功后调用。遍历模块的入口类列表，依次调用每个入口类的
     * `handleLoadPackage` 或等效回调，让模块通过 [HookProvider] 注册 Hook。
     *
     * 成功后状态变为 [ModuleStatus.ACTIVE]。
     *
     * @param context 由 [onLoad] 返回的模块上下文
     * @param param 宿主应用的加载参数，包含 [HookProvider] 实例
     * @return 成功时返回已注册的 Hook 句柄列表；失败时返回 [ModuleError]
     */
    fun onHook(
        context: ModuleContext,
        param: LoadPackageParam,
    ): Result<List<Any>>

    /**
     * 停用模块（移除所有 Hook 但保留资源）。
     *
     * 移除本模块注册的所有 Hook，使模块进入休眠状态。
     * 之后可通过 [onActivate] 重新激活。
     *
     * 成功后状态变为 [ModuleStatus.INACTIVE]。
     *
     * @param context 模块上下文
     */
    fun onDeactivate(context: ModuleContext)

    /**
     * 重新激活已停用的模块。
     *
     * 在 [onDeactivate] 后调用，重新执行 [onHook] 逻辑。
     * 成功后状态变为 [ModuleStatus.ACTIVE]。
     *
     * @param context 模块上下文
     * @param param 宿主应用的加载参数
     * @return 成功时返回已注册的 Hook 句柄列表；失败时返回 [ModuleError]
     */
    fun onActivate(
        context: ModuleContext,
        param: LoadPackageParam,
    ): Result<List<Any>>

    /**
     * 卸载模块。
     *
     * 执行以下操作：
     * 1. 移除所有 Hook（如果尚处于 ACTIVE 状态，先执行 deactivate）
     * 2. 释放 ClassLoader 和相关资源
     * 3. 清理缓存文件
     *
     * 成功后状态变为 [ModuleStatus.UNLOADED]。
     *
     * @param context 模块上下文
     */
    fun onUnload(context: ModuleContext)

    // --------------------------------------------------------------------------
    // 状态查询
    // --------------------------------------------------------------------------

    /**
     * 获取模块当前状态。
     */
    val status: ModuleStatus

    /**
     * 获取模块唯一标识。
     */
    val moduleId: String

    /**
     * 获取模块上下文（仅在 [ModuleStatus.LOADED] 及之后的状态中可用）。
     */
    val context: ModuleContext?

    // --------------------------------------------------------------------------
    // 状态监听
    // --------------------------------------------------------------------------

    /**
     * 注册状态变更监听器。
     *
     * 监听器在每次状态变更时被同步调用。
     * 同一监听器重复注册不会重复触发。
     *
     * @param listener 状态变更监听器
     */
    fun addStatusListener(listener: ModuleStatusListener)

    /**
     * 移除状态变更监听器。
     *
     * @param listener 要移除的监听器
     */
    fun removeStatusListener(listener: ModuleStatusListener)

    // --------------------------------------------------------------------------
    // 错误恢复
    // --------------------------------------------------------------------------

    /**
     * 获取模块最后一次错误信息。
     *
     * 如果没有发生过错误，返回 `null`。
     */
    val lastError: ModuleError?

    /**
     * 清除错误状态，尝试恢复到上一个正常状态。
     *
     * 如果当前状态为 [ModuleStatus.ERROR] 且错误是可恢复的（[ModuleError.recoverable] == true），
     * 则清除错误状态并恢复到 [ModuleStatus.LOADED]。
     * 调用方需要重新调用 [onHook] 来激活模块。
     *
     * @return 成功时返回 `true`；如果错误不可恢复或当前不在错误状态，返回 `false`
     */
    fun recover(): Boolean
}

// ============================================================================
// 结果类型别名
// ============================================================================

/**
 * 扩展函数：从 [Result] 中提取错误信息，用于日志记录。
 */
fun <T> Result<T>.errorOrNull(): ModuleError? =
    exceptionOrNull()?.let { e ->
        when (e) {
            is ModuleLifecycleException -> e.error
            else -> ModuleError(
                code = ModuleErrorCode.UNKNOWN,
                message = e.message ?: "Unknown error",
                cause = e,
            )
        }
    }

// ============================================================================
// 异常
// ============================================================================

/**
 * 模块生命周期异常。
 *
 * 当模块生命周期方法抛出异常时，内部自动包装为此异常并塞入 [Result.failure]。
 *
 * @property error 结构化的错误信息
 */
class ModuleLifecycleException(
    val error: ModuleError,
) : RuntimeException(error.message, error.cause)