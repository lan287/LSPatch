package org.lsposed.lspatch.v2.hook

import androidx.annotation.RequiresApi
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method

// ============================================================================
// 回调类型
// ============================================================================

/**
 * Hook 回调的执行时机。
 *
 * 供 [HookCallback] 中声明，决定回调函数在目标方法执行的哪个阶段被调用。
 */
enum class HookTiming {
    /** 在目标方法执行之前调用。可以读取/修改参数，也可以返回 [HookResult.Skip] 跳过原方法。 */
    BEFORE,

    /** 在目标方法执行之后调用。可以读取/修改返回值或异常。 */
    AFTER,

    /** 完全替换目标方法实现。原方法不会被调用，以回调的返回值作为最终结果。 */
    REPLACE,
}

// ============================================================================
// Hook 结果
// ============================================================================

/**
 * Hook 回调的执行结果，用于控制目标方法的执行流程。
 *
 * 仅当 [HookTiming] 为 [HookTiming.BEFORE] 或 [HookTiming.REPLACE] 时有效。
 */
sealed class HookResult {
    /**
     * 继续执行原方法（或下一个回调链）。
     *
     * 如果 [HookTiming] 为 [HookTiming.BEFORE]，表示原方法正常执行。
     * 如果 [HookTiming] 为 [HookTiming.REPLACE]，此值无效——REPLACE 回调必须显式返回 [HookResult.Return]。
     */
    data object Continue : HookResult()

    /**
     * 跳过原方法执行，直接返回指定值。
     *
     * 对 `void` 方法，[value] 应为 `null` 或 [Unit]。
     */
    data class Return(val value: Any?) : HookResult()

    /**
     * 跳过原方法执行，抛出指定异常。
     */
    data class Throw(val throwable: Throwable) : HookResult()

    /**
     * 跳过原方法执行，不返回任何值（仅对 void 方法）。
     */
    data object Skip : HookResult()
}

// ============================================================================
// Hook 回调上下文
// ============================================================================

/**
 * Hook 回调上下文，封装了被 Hook 的方法/构造器/字段的运行时信息。
 *
 * @param T 方法的返回值类型（对 [ConstructorHook] 固定为 [Void]）
 */
data class HookContext<T>(
    /** 目标方法所属的实例（静态方法为 null） */
    val thisObject: Any?,

    /** 被 Hook 的目标方法或构造器 */
    val method: Member,

    /** 调用参数数组 */
    val args: Array<out Any?>,

    /** 标称返回值类型，用于类型推断 */
    val returnType: Class<T>,
) {
    /**
     * 获取第 [index] 个参数，自动类型转换。
     */
    @Suppress("UNCHECKED_CAST")
    fun <A> arg(index: Int): A = args[index] as A

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HookContext<*>) return false
        return thisObject == other.thisObject &&
                method == other.method &&
                args.contentEquals(other.args) &&
                returnType == other.returnType
    }

    override fun hashCode(): Int {
        var result = thisObject?.hashCode() ?: 0
        result = 31 * result + method.hashCode()
        result = 31 * result + args.contentHashCode()
        result = 31 * result + returnType.hashCode()
        return result
    }
}

// ============================================================================
// 回调接口
// ============================================================================

/**
 * 方法级 Hook 回调。
 *
 * 按 [HookTiming] 声明回调时机，返回 [HookResult] 控制执行流。
 *
 * ### 用法示例
 * ```kotlin
 * val callback = HookCallback<Any>(HookTiming.BEFORE) { ctx ->
 *     Log.d("Hook", "Method ${ctx.method.name} called with ${ctx.arg<String>(0)}")
 *     HookResult.Continue
 * }
 * ```
 *
 * @param T 目标方法的返回值类型
 * @property timing 回调执行时机
 * @property block 回调逻辑
 */
class HookCallback<T>(
    val timing: HookTiming,
    val block: (HookContext<T>) -> HookResult,
)

/**
 * 前置回调（Before）的便捷类型别名。
 */
typealias BeforeCallback<T> = (HookContext<T>) -> HookResult

/**
 * 后置回调（After）的便捷类型别名。
 * 后置回调可以读取/修改返回值，也可替换异常。
 */
typealias AfterCallback<T> = (HookContext<T>) -> HookResult

/**
 * 替换回调（Replace）的便捷类型别名。
 */
typealias ReplaceCallback<T> = (HookContext<T>) -> HookResult

// ============================================================================
// Hook 句柄
// ============================================================================

/**
 * Hook 句柄，代表一次已注册的 Hook 操作。
 *
 * 调用 [unhook] 可从目标方法上移除本次 Hook。
 * 句柄一旦 unhook 后不可重复使用。
 *
 * @property id 全局唯一的 Hook 标识符
 * @property isActive 当前是否处于激活状态
 */
data class HookHandle(
    val id: String,
    val target: Member,
    private val unhookAction: () -> Unit,
) {
    @Volatile
    private var _isActive = true

    /** 当前是否处于激活状态（未被 unhook） */
    val isActive: Boolean get() = _isActive

    /**
     * 移除本次 Hook。
     *
     * 调用后 [isActive] 变为 `false`，重复调用无副作用。
     */
    fun unhook() {
        if (_isActive) {
            _isActive = false
            unhookAction()
        }
    }
}

// ============================================================================
// HookProvider 接口
// ============================================================================

/**
 * Hook 提供者接口 —— LSPatch 2.0 Hook 层的核心抽象。
 *
 * 向上层（Module 层）提供统一的 Hook 操作入口，屏蔽底层 ART Hook 引擎差异。
 *
 * ### 设计原则
 * - **三种 Hook 类型**：[hookMethod] 针对普通方法，[hookConstructor] 针对构造器，[hookField] 针对字段访问
 * - **三种回调时机**：[HookTiming.BEFORE]、[HookTiming.AFTER]、[HookTiming.REPLACE]
 * - **批量操作**：[hookAll] 支持一次 Hook 多个目标
 * - **生命周期管理**：所有 hook 操作返回 [HookHandle]，调用 [HookHandle.unhook] 即可移除
 *
 * ### 线程安全
 * 所有方法均为线程安全，可以从任意线程调用。回调在目标方法被调用的线程中执行。
 *
 * ### 使用示例
 * ```kotlin
 * val hookProvider: HookProvider = ...
 *
 * // Hook 一个方法
 * val handle = hookProvider.hookMethod(
 *     targetClass = "android.app.Activity",
 *     methodName = "onCreate",
 *     paramTypes = arrayOf(Bundle::class.java),
 *     callbacks = listOf(
 *         HookCallback(HookTiming.BEFORE) { ctx ->
 *             Log.d("Hook", "onCreate called")
 *             HookResult.Continue
 *         }
 *     )
 * )
 *
 * // 稍后移除
 * handle.unhook()
 * ```
 */
interface HookProvider {

    // --------------------------------------------------------------------------
    // 方法 Hook
    // --------------------------------------------------------------------------

    /**
     * Hook 一个实例方法或静态方法。
     *
     * @param targetClass 目标类的全限定名，如 `"android.app.Activity"`
     * @param methodName 方法名
     * @param paramTypes 参数类型列表，用于区分重载方法；无参方法传空数组
     * @param callbacks 回调列表，按 [HookTiming] 区分执行时机
     * @return Hook 句柄，调用 [HookHandle.unhook] 移除
     * @throws ClassNotFoundException 如果 [targetClass] 不存在
     * @throws NoSuchMethodException 如果指定方法不存在
     * @throws HookException 如果 Hook 注册失败（底层引擎不支持等）
     */
    fun hookMethod(
        targetClass: String,
        methodName: String,
        paramTypes: Array<Class<*>>,
        callbacks: List<HookCallback<*>>,
    ): HookHandle

    /**
     * Hook 一个实例方法或静态方法（通过 [Method] 对象）。
     *
     * 调用方需自行获取 [Method] 对象（例如通过反射），适用于泛型方法等复杂场景。
     *
     * @param target 目标 [Method] 对象
     * @param callbacks 回调列表
     * @return Hook 句柄
     * @throws HookException 如果 Hook 注册失败
     */
    fun hookMethod(
        target: Method,
        callbacks: List<HookCallback<*>>,
    ): HookHandle

    // --------------------------------------------------------------------------
    // 构造器 Hook
    // --------------------------------------------------------------------------

    /**
     * Hook 一个构造器。
     *
     * 回调中 [HookContext.thisObject] 为正在构造的实例（仅 AFTER 可用），
     * [HookContext.method] 为 [Constructor] 对象。
     *
     * @param targetClass 目标类的全限定名
     * @param paramTypes 构造器参数类型列表
     * @param callbacks 回调列表
     * @return Hook 句柄
     * @throws ClassNotFoundException 如果 [targetClass] 不存在
     * @throws NoSuchMethodException 如果指定构造器不存在
     * @throws HookException 如果 Hook 注册失败
     */
    fun hookConstructor(
        targetClass: String,
        paramTypes: Array<Class<*>>,
        callbacks: List<HookCallback<*>>,
    ): HookHandle

    /**
     * Hook 一个构造器（通过 [Constructor] 对象）。
     *
     * @param target 目标 [Constructor] 对象
     * @param callbacks 回调列表
     * @return Hook 句柄
     * @throws HookException 如果 Hook 注册失败
     */
    fun hookConstructor(
        target: Constructor<*>,
        callbacks: List<HookCallback<*>>,
    ): HookHandle

    // --------------------------------------------------------------------------
    // 字段 Hook
    // --------------------------------------------------------------------------

    /**
     * Hook 一个字段的读写操作。
     *
     * 仅支持实例字段；静态字段请使用 [hookStaticField]。
     *
     * @param targetClass 目标类的全限定名
     * @param fieldName 字段名
     * @param callbacks 回调列表，BEFORE 在读取/写入前调用，AFTER 在读取/写入后调用
     * @return Hook 句柄
     * @throws ClassNotFoundException 如果 [targetClass] 不存在
     * @throws NoSuchFieldException 如果指定字段不存在
     * @throws HookException 如果 Hook 注册失败（部分引擎不支持字段 Hook）
     */
    fun hookField(
        targetClass: String,
        fieldName: String,
        callbacks: List<HookCallback<*>>,
    ): HookHandle

    /**
     * Hook 一个静态字段的读写操作。
     *
     * @param targetClass 目标类的全限定名
     * @param fieldName 静态字段名
     * @param callbacks 回调列表
     * @return Hook 句柄
     * @throws ClassNotFoundException 如果 [targetClass] 不存在
     * @throws NoSuchFieldException 如果指定字段不存在
     * @throws HookException 如果 Hook 注册失败
     */
    fun hookStaticField(
        targetClass: String,
        fieldName: String,
        callbacks: List<HookCallback<*>>,
    ): HookHandle

    /**
     * Hook 一个字段（通过 [Field] 对象）。
     *
     * @param target 目标 [Field] 对象
     * @param callbacks 回调列表
     * @return Hook 句柄
     * @throws HookException 如果 Hook 注册失败
     */
    fun hookField(
        target: Field,
        callbacks: List<HookCallback<*>>,
    ): HookHandle

    // --------------------------------------------------------------------------
    // 批量 Hook
    // --------------------------------------------------------------------------

    /**
     * Hook 指定类中所有匹配的方法。
     *
     * 遍历 [targetClass] 的所有 declared 方法，对每个匹配 [predicate] 的方法注册 Hook。
     * 返回所有成功创建的 Hook 句柄列表。
     *
     * @param targetClass 目标类的全限定名
     * @param predicate 方法筛选条件，返回 `true` 的方法会被 Hook
     * @param callbacks 回调列表
     * @return 所有成功创建的 Hook 句柄列表
     */
    fun hookAll(
        targetClass: String,
        predicate: (Method) -> Boolean,
        callbacks: List<HookCallback<*>>,
    ): List<HookHandle>

    // --------------------------------------------------------------------------
    // 能力查询
    // --------------------------------------------------------------------------

    /**
     * 当前 Hook 引擎的能力描述。
     *
     * 模块可以通过此属性判断当前环境是否支持某些 Hook 特性。
     */
    val capability: HookCapability

    /**
     * 判断当前引擎是否支持指定类型的 Hook。
     */
    fun supports(hookType: HookType): Boolean
}

// ============================================================================
// Hook 能力描述
// ============================================================================

/**
 * Hook 引擎的能力描述。
 *
 * 不同底层引擎（lsplant inline、DexPilot、SoftHook）能力不同。
 */
data class HookCapability(
    /** 是否支持方法 Hook */
    val supportsMethodHook: Boolean = true,

    /** 是否支持构造器 Hook */
    val supportsConstructorHook: Boolean = true,

    /** 是否支持字段 Hook（部分引擎不支持） */
    val supportsFieldHook: Boolean = false,

    /** 是否支持 [HookTiming.REPLACE] 模式 */
    val supportsReplace: Boolean = true,

    /** 是否支持 JNI 函数 Hook */
    val supportsJniHook: Boolean = false,

    /** 最大同时 Hook 数量（0 表示无限制） */
    val maxHookCount: Int = 0,

    /** 引擎名称 */
    val engineName: String = "Unknown",

    /** 是否处于降级模式 */
    val isDegraded: Boolean = false,
)

/**
 * Hook 类型枚举，用于 [HookProvider.supports] 查询。
 */
enum class HookType {
    METHOD,
    CONSTRUCTOR,
    FIELD,
    STATIC_FIELD,
    JNI,
    REPLACE,
}

// ============================================================================
// 异常
// ============================================================================

/**
 * Hook 操作异常。
 *
 * 当 Hook 注册失败时抛出，包含失败原因描述和原始异常（如有）。
 */
class HookException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)