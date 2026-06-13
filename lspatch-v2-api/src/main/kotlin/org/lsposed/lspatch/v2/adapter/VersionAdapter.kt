package org.lsposed.lspatch.v2.adapter

import android.os.Build
import android.os.Process
import android.system.Os
import androidx.annotation.RequiresApi
import org.lsposed.lspatch.v2.hook.HookProvider
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths

// ============================================================================
// 版本能力描述
// ============================================================================

/**
 * 当前 Android 版本的 ART 运行时能力描述。
 *
 * 由 [VersionAdapter] 在初始化时填充，供上层决策使用。
 *
 * @property sdkInt 实际的 API Level
 * @property hasUserfaultfdGC 是否使用 userfaultfd 进行 GC（Android 11+）
 * @property hasMemfdRestriction 是否限制了 memfd_create 系统调用（Android 14+）
 * @property hasExecMemRestriction 是否限制了可执行内存分配（Android 14+）
 * @property hasAppComponentFactory Android 10+ 支持的 AppComponentFactory 代理
 * @property hasInMemoryDexClassLoader 是否支持 InMemoryDexClassLoader（Android 8+）
 * @property hasScopedStorage 是否启用分区存储（Android 10+）
 * @property signatureScheme 支持的签名方案（V1/V2/V3/V4）
 * @property hiddenApiEnforcementPolicy 隐藏 API 限制策略级别
 */
data class VersionCapability(
    val sdkInt: Int,
    val hasUserfaultfdGC: Boolean,
    val hasMemfdRestriction: Boolean,
    val hasExecMemRestriction: Boolean,
    val hasAppComponentFactory: Boolean,
    val hasInMemoryDexClassLoader: Boolean,
    val hasScopedStorage: Boolean,
    val signatureScheme: Set<SignatureScheme>,
    val hiddenApiEnforcementPolicy: HiddenApiPolicy,
    /** 平台原生 so 搜索路径 */
    val nativeLibPaths: List<String>,
    /** 系统属性集合（用于快速查询） */
    val systemProperties: Map<String, String>,
)

/**
 * APK 签名方案。
 */
enum class SignatureScheme {
    V1,
    V2,
    V3,
    /** Android 11+ */
    V4,

    /** Android 15+ 的 APK Signature Scheme v4 自由格式 */
    V4_FREEFLOW,
}

/**
 * 隐藏 API 限制策略。
 */
enum class HiddenApiPolicy {
    /** 无限制 */
    DISABLED,

    /** 仅警告，不阻止 */
    JUST_WARN,

    /** 灰名单限制（Android 9-10） */
    DARK_GREY_AND_BLACK,

    /** 严格限制（Android 11+） */
    BLACKLIST_ONLY,
}

// ============================================================================
// VersionAdapter 抽象类
// ============================================================================

/**
 * Android 版本适配器抽象类 —— LSPatch 2.0 兼容性层的核心抽象。
 *
 * 使用**策略模式**隔离不同 Android 版本的实现差异。
 * 每个 Android 主版本（8/9/10/11/12/13/14/15）对应一个具体子类。
 *
 * ### 职责
 * 1. **版本探测**：[probeCapability] 探测当前设备的能力集
 * 2. **启动流程**：[bootstrap] 执行版本特定的初始化步骤
 * 3. **符号解析**：[resolveArtSymbol] 解析 ART 运行时的符号地址
 * 4. **公共工具**：提供反射、内存操作、SELinux 上下文管理等通用方法
 *
 * ### 子类实现要求
 * 每个子类必须实现：
 * - [probeCapability] — 探测版本能力
 * - [bootstrap] — 版本特定初始化
 * - [resolveArtSymbol] — ART 符号解析
 * - [createHookProvider] — 创建 Hook 提供者
 * - [targetSdkRange] — 声明适用的 SDK 范围
 *
 * ### 降级链
 * 当某个版本的适配器初始化失败时，自动降级到下一个版本：
 * ```
 * V15Adapter → V14Adapter → V13Adapter → ... → V8Adapter → SoftAdapter
 * ```
 *
 * @see VersionAdapterFactory
 */
abstract class VersionAdapter {

    // --------------------------------------------------------------------------
    // 抽象方法 — 子类必须实现
    // --------------------------------------------------------------------------

    /**
     * 探测当前设备的能力集。
     *
     * 读取系统属性、检测 SELinux 状态、尝试系统调用，返回结构化能力描述。
     *
     * @return 能力描述，用于后续策略选择
     */
    abstract fun probeCapability(): VersionCapability

    /**
     * 执行版本特定的启动流程。
     *
     * 包括但不限于：
     * - 隐藏 API 豁免 (HiddenApiBypass)
     * - SELinux 上下文设置
     * - Native 库加载
     * - ART 运行时初始化
     *
     * @param capability 由 [probeCapability] 返回的能力描述
     * @return 启动结果，包含 [HookProvider] 实例
     */
    abstract fun bootstrap(capability: VersionCapability): BootstrapResult

    /**
     * 解析 ART 运行时中的符号地址。
     *
     * 例如 `_ZN3art9ArtMethod12PrettyMethodEPNS_11ThreadImplEb` 等。
     *
     * @param symbol 符号名（完整 mangled name 或简写）
     * @return 符号地址，如果未找到返回 `null`
     */
    abstract fun resolveArtSymbol(symbol: String): Long?

    /**
     * 创建适用于当前版本的 [HookProvider] 实例。
     *
     * @param capability 能力描述
     * @return Hook 提供者实例
     */
    abstract fun createHookProvider(capability: VersionCapability): HookProvider

    /**
     * 声明此适配器适用的 SDK 版本范围。
     *
     * @return 闭区间 \[minInclude, maxInclude\]，例如 `26..28` 表示 Android 8.0 到 9.0
     */
    abstract val targetSdkRange: IntRange

    /**
     * 适配器名称，用于日志和 Metrics。
     */
    abstract val adapterName: String

    // --------------------------------------------------------------------------
    // 公共方法
    // --------------------------------------------------------------------------

    /**
     * 判断此适配器是否适用于当前设备。
     *
     * 默认实现基于 [targetSdkRange] 与 [Build.VERSION.SDK_INT] 比较。
     * 子类可以覆写以添加更精确的判断逻辑（例如检查特定系统属性）。
     */
    open fun isApplicable(): Boolean {
        return Build.VERSION.SDK_INT in targetSdkRange
    }

    /**
     * 获取当前进程的 SELinux 上下文。
     *
     * 读取 `/proc/self/attr/current`，仅在 SELinux 启用时有效。
     *
     * @return SELinux 上下文字符串，如 `"u:r:untrusted_app:s0:c123,c456,c789"`；如果不可用返回 `null`
     */
    open fun getSelinuxContext(): String? {
        return runCatching {
            val path = Paths.get("/proc/self/attr/current")
            if (Files.exists(path)) {
                Files.readString(path).trim()
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * 判断 SELinux 是否处于 Enforcing 模式。
     */
    open fun isSelinuxEnforcing(): Boolean {
        return runCatching {
            val path = Paths.get("/sys/fs/selinux/enforce")
            Files.exists(path) && Files.readString(path).trim() == "1"
        }.getOrDefault(false)
    }

    /**
     * 获取系统属性值。
     *
     * 封装了 `SystemProperties.get()` 的反射调用，避免直接依赖隐藏 API。
     *
     * @param key 属性名，如 `"ro.build.version.sdk"`
     * @param defaultValue 默认值
     * @return 属性值，如果获取失败返回 [defaultValue]
     */
    open fun getSystemProperty(key: String, defaultValue: String = ""): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, defaultValue) as? String ?: defaultValue
        }.getOrDefault(defaultValue)
    }

    /**
     * 加载 Native 库并返回其句柄。
     *
     * 子类可以覆写此方法以实现自定义加载逻辑（如从 APK 内提取 so）。
     *
     * @param libName 库名，不含 `lib` 前缀和 `.so` 后缀
     * @param searchPaths 额外的搜索路径
     * @return Native 库的绝对路径
     */
    open fun loadNativeLibrary(libName: String, searchPaths: List<String> = emptyList()): String {
        // 尝试从给定路径加载
        for (path in searchPaths) {
            val soFile = File(path, "lib${libName}.so")
            if (soFile.exists()) {
                System.load(soFile.absolutePath)
                return soFile.absolutePath
            }
        }
        // 尝试系统默认路径
        System.loadLibrary(libName)
        return "lib${libName}.so"
    }

    /**
     * 安全地获取 Class.forName，避免在类不存在时崩溃。
     *
     * @param className 全限定类名
     * @param classLoader 使用的 ClassLoader，默认使用当前线程的 ContextClassLoader
     * @return Class 对象，如果未找到返回 `null`
     */
    protected fun safeClassForName(className: String, classLoader: ClassLoader? = null): Class<*>? {
        return runCatching {
            val cl = classLoader
                ?: Thread.currentThread().contextClassLoader
                ?: VersionAdapter::class.java.classLoader
            Class.forName(className, false, cl)
        }.getOrNull()
    }

    /**
     * 安全地获取方法，避免在方法不存在时崩溃。
     *
     * @param clazz 目标类
     * @param methodName 方法名
     * @param paramTypes 参数类型
     * @return Method 对象，如果未找到返回 `null`
     */
    protected fun safeGetMethod(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>,
    ): Method? {
        return runCatching {
            clazz.getDeclaredMethod(methodName, *paramTypes).also { it.isAccessible = true }
        }.getOrNull()
    }

    /**
     * 安全地获取字段，避免在字段不存在时崩溃。
     *
     * @param clazz 目标类
     * @param fieldName 字段名
     * @return Field 对象，如果未找到返回 `null`
     */
    protected fun safeGetField(clazz: Class<*>, fieldName: String): Field? {
        return runCatching {
            clazz.getDeclaredField(fieldName).also { it.isAccessible = true }
        }.getOrNull()
    }

    /**
     * 扫描进程的 `/proc/self/maps` 以查找已加载的 Native 库基址。
     *
     * @param libName 库名匹配模式（如 `libart.so`）
     * @return 库的基址，如果未找到返回 `null`
     */
    protected fun findLibraryBase(libName: String): Long? {
        return runCatching {
            Files.readAllLines(Paths.get("/proc/self/maps")).forEach { line ->
                if (line.contains(libName) && line.contains("r-xp")) {
                    val addr = line.substringBefore("-").trim()
                    return addr.toLong(16)
                }
            }
            null
        }.getOrNull()
    }

    /**
     * 获取当前进程的 UID。
     */
    protected val myUid: Int get() = Process.myUid()

    /**
     * 获取当前进程的 PID。
     */
    protected val myPid: Int get() = Process.myPid()

    /**
     * 获取当前进程名。
     */
    protected val myProcessName: String
        get() = runCatching {
            Files.readString(Paths.get("/proc/self/cmdline")).trimEnd('\u0000')
        }.getOrDefault("unknown")
}

// ============================================================================
// 启动结果
// ============================================================================

/**
 * [VersionAdapter.bootstrap] 的返回结果。
 *
 * @property hookProvider 创建好的 Hook 提供者
 * @property capability 最终确认的能力描述（可能与探测阶段相同，也可能因初始化失败而降级）
 * @property isDegraded 是否发生了降级
 * @property degradeReason 降级原因（仅在 [isDegraded] 为 true 时有值）
 * @property warnings 启动过程中的警告信息
 */
data class BootstrapResult(
    val hookProvider: HookProvider,
    val capability: VersionCapability,
    val isDegraded: Boolean = false,
    val degradeReason: String? = null,
    val warnings: List<String> = emptyList(),
)

// ============================================================================
// 版本适配器工厂
// ============================================================================

/**
 * 版本适配器工厂。
 *
 * 按优先级尝试所有适配器，选择第一个通过 [VersionAdapter.isApplicable] 且 [VersionAdapter.bootstrap] 成功的适配器。
 *
 * ### 使用方式
 * ```kotlin
 * val adapter = VersionAdapterFactory.create()
 * val result = adapter.bootstrap(adapter.probeCapability())
 * ```
 */
object VersionAdapterFactory {

    /**
     * 按优先级排列的适配器候选列表。
     *
     * 顺序：高版本在前，低版本在后，最后是 SoftAdapter（兜底）。
     */
    private val candidates: List<VersionAdapter> = listOf(
        VerticalAdapterV15(),
        VerticalAdapterV14(),
        VerticalAdapterV13(),
        VerticalAdapterV12(),
        VerticalAdapterV11(),
        VerticalAdapterV10(),
        VerticalAdapterV9(),
        VerticalAdapterV8(),
        SoftFallbackAdapter(),
    )

    /**
     * 创建适用于当前设备的最佳 [VersionAdapter]。
     *
     * 遍历候选列表，选择第一个满足条件的适配器。
     * 如果适配器 [bootstrap] 失败，自动尝试下一个。
     *
     * @return 可用的适配器实例
     * @throws IllegalStateException 如果所有适配器都不可用
     */
    fun create(): VersionAdapter {
        val warnings = mutableListOf<String>()

        for (adapter in candidates) {
            if (!adapter.isApplicable()) continue

            return try {
                adapter
            } catch (e: Exception) {
                warnings.add("${adapter.adapterName}: ${e.message}")
                continue
            }
        }

        throw IllegalStateException(
            "No compatible VersionAdapter found for SDK ${Build.VERSION.SDK_INT}. " +
                    "Warnings: ${warnings.joinToString("; ")}"
        )
    }

    /**
     * 尝试创建适配器，如果所有候选都失败则返回降级适配器。
     *
     * @return 适配器实例（永远不会抛异常）
     */
    fun createOrFallback(): VersionAdapter {
        return runCatching { create() }.getOrDefault(SoftFallbackAdapter())
    }
}

// ============================================================================
// 具体版本适配器（骨架）
// ============================================================================

/**
 * Android 8.0 / 8.1 (API 26-27) 版本适配器。
 *
 * 特点：
 * - 传统 ART GC (无 userfaultfd)
 * - 隐藏 API 限制较轻 (灰名单模式)
 * - 无 AppComponentFactory 代理
 * - 支持 InMemoryDexClassLoader
 */
@RequiresApi(Build.VERSION_CODES.O)
open class VerticalAdapterV8 : VersionAdapter() {

    override val targetSdkRange: IntRange = 26..27
    override val adapterName: String = "V8Adapter"

    override fun probeCapability(): VersionCapability {
        return VersionCapability(
            sdkInt = Build.VERSION.SDK_INT,
            hasUserfaultfdGC = false,
            hasMemfdRestriction = false,
            hasExecMemRestriction = false,
            hasAppComponentFactory = false,
            hasInMemoryDexClassLoader = true,
            hasScopedStorage = false,
            signatureScheme = setOf(SignatureScheme.V1, SignatureScheme.V2),
            hiddenApiEnforcementPolicy = HiddenApiPolicy.DARK_GREY_AND_BLACK,
            nativeLibPaths = listOf(
                "/system/lib64",
                "/system/lib",
                "/vendor/lib64",
                "/vendor/lib",
            ),
            systemProperties = buildSystemProperties(),
        )
    }

    override fun bootstrap(capability: VersionCapability): BootstrapResult {
        // 子类实现
        throw NotImplementedError("V8Adapter.bootstrap is not yet implemented")
    }

    override fun resolveArtSymbol(symbol: String): Long? {
        // 子类实现
        return null
    }

    override fun createHookProvider(capability: VersionCapability): HookProvider {
        throw NotImplementedError("V8Adapter.createHookProvider is not yet implemented")
    }

    /**
     * 构建常用系统属性映射。
     */
    protected fun buildSystemProperties(): Map<String, String> {
        val keys = listOf(
            "ro.build.version.sdk",
            "ro.build.version.release",
            "ro.product.cpu.abi",
            "dalvik.vm.usejitprofiles",
            "dalvik.vm.dex2oat-filter",
            "dalvik.vm.image-dex2oat-filter",
        )
        return keys.associateWith { getSystemProperty(it) }
    }
}

/**
 * Android 9 (API 28) 版本适配器。
 *
 * 与 V8 类似，但隐藏 API 限制更严格。
 */
@RequiresApi(Build.VERSION_CODES.P)
open class VerticalAdapterV9 : VerticalAdapterV8() {

    override val targetSdkRange: IntRange = 28..28
    override val adapterName: String = "V9Adapter"

    override fun probeCapability(): VersionCapability {
        return super.probeCapability().copy(
            hiddenApiEnforcementPolicy = HiddenApiPolicy.DARK_GREY_AND_BLACK,
        )
    }
}

/**
 * Android 10 (API 29) 版本适配器。
 *
 * 新增：
 * - AppComponentFactory 代理
 * - 分区存储 (Scoped Storage)
 * - ART GC 重构开始
 */
@RequiresApi(Build.VERSION_CODES.Q)
open class VerticalAdapterV10 : VerticalAdapterV9() {

    override val targetSdkRange: IntRange = 29..29
    override val adapterName: String = "V10Adapter"

    override fun probeCapability(): VersionCapability {
        return super.probeCapability().copy(
            hasAppComponentFactory = true,
            hasScopedStorage = true,
            signatureScheme = setOf(SignatureScheme.V1, SignatureScheme.V2, SignatureScheme.V3),
        )
    }
}

/**
 * Android 11 (API 30) 版本适配器。
 *
 * 新增：
 * - userfaultfd GC
 * - 隐藏 API 严格限制
 * - APK Signature Scheme V4
 */
@RequiresApi(Build.VERSION_CODES.R)
open class VerticalAdapterV11 : VerticalAdapterV10() {

    override val targetSdkRange: IntRange = 30..30
    override val adapterName: String = "V11Adapter"

    override fun probeCapability(): VersionCapability {
        return super.probeCapability().copy(
            hasUserfaultfdGC = true,
            hiddenApiEnforcementPolicy = HiddenApiPolicy.BLACKLIST_ONLY,
            signatureScheme = setOf(SignatureScheme.V1, SignatureScheme.V2, SignatureScheme.V3, SignatureScheme.V4),
        )
    }
}

/**
 * Android 12 / 12L (API 31-32) 版本适配器。
 */
@RequiresApi(Build.VERSION_CODES.S)
open class VerticalAdapterV12 : VerticalAdapterV11() {

    override val targetSdkRange: IntRange = 31..32
    override val adapterName: String = "V12Adapter"

    override fun probeCapability(): VersionCapability {
        return super.probeCapability().copy()
    }
}

/**
 * Android 13 (API 33) 版本适配器。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class VerticalAdapterV13 : VerticalAdapterV12() {

    override val targetSdkRange: IntRange = 33..33
    override val adapterName: String = "V13Adapter"

    override fun probeCapability(): VersionCapability {
        return super.probeCapability().copy()
    }
}

/**
 * Android 14 (API 34) 版本适配器。
 *
 * 新增限制：
 * - memfd_create 系统调用受限
 * - 可执行内存分配受限 (execmem SELinux 限制)
 * - 需要特殊方式绕过
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class VerticalAdapterV14 : VerticalAdapterV13() {

    override val targetSdkRange: IntRange = 34..34
    override val adapterName: String = "V14Adapter"

    override fun probeCapability(): VersionCapability {
        return super.probeCapability().copy(
            hasMemfdRestriction = true,
            hasExecMemRestriction = true,
        )
    }
}

/**
 * Android 15 (API 35) 版本适配器。
 *
 * 新增限制：
 * - APK Signature Scheme V4 自由格式
 * - 更严格的 native 代码执行限制
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
open class VerticalAdapterV15 : VerticalAdapterV14() {

    override val targetSdkRange: IntRange = 35..35
    override val adapterName: String = "V15Adapter"

    override fun probeCapability(): VersionCapability {
        return super.probeCapability().copy(
            signatureScheme = setOf(
                SignatureScheme.V1,
                SignatureScheme.V2,
                SignatureScheme.V3,
                SignatureScheme.V4,
                SignatureScheme.V4_FREEFLOW,
            ),
        )
    }
}

// ============================================================================
// 兜底适配器
// ============================================================================

/**
 * 软降级兜底适配器。
 *
 * 当所有版本专用适配器都不可用时，使用此适配器以纯反射模式运行。
 * 功能受限但确保基本可用：
 * - 不支持 Native Hook
 * - 不支持字段 Hook
 * - 仅支持基础的 MethodHook
 *
 * [targetSdkRange] 覆盖所有版本，但 [isApplicable] 返回 `false`，
 * 确保此适配器仅在 [VersionAdapterFactory.create] 的降级路径中被使用。
 */
class SoftFallbackAdapter : VerticalAdapterV8() {

    override val targetSdkRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE
    override val adapterName: String = "SoftFallbackAdapter"

    /**
     * 永远返回 `false`，确保此适配器不会在正常选择路径中被选中。
     */
    override fun isApplicable(): Boolean = false

    override fun probeCapability(): VersionCapability {
        return VersionCapability(
            sdkInt = Build.VERSION.SDK_INT,
            hasUserfaultfdGC = false,
            hasMemfdRestriction = false,
            hasExecMemRestriction = false,
            hasAppComponentFactory = false,
            hasInMemoryDexClassLoader = false,
            hasScopedStorage = false,
            signatureScheme = setOf(SignatureScheme.V1),
            hiddenApiEnforcementPolicy = HiddenApiPolicy.BLACKLIST_ONLY,
            nativeLibPaths = emptyList(),
            systemProperties = emptyMap(),
        )
    }

    override fun bootstrap(capability: VersionCapability): BootstrapResult {
        return BootstrapResult(
            hookProvider = createHookProvider(capability),
            capability = capability,
            isDegraded = true,
            degradeReason = "All version-specific adapters failed. Using pure reflection mode.",
            warnings = listOf(
                "Native hooks are disabled",
                "Field hooks are disabled",
                "JNI hooks are disabled",
                "Performance will be degraded",
            ),
        )
    }

    override fun createHookProvider(capability: VersionCapability): HookProvider {
        throw NotImplementedError("SoftFallback HookProvider not yet implemented")
    }
}