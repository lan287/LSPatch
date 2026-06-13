package org.lsposed.lspatch.v2.resource

import androidx.annotation.RequiresApi
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Build
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer

// ============================================================================
// 注入资源类型
// ============================================================================

/**
 * 可注入的资源类型。
 */
enum class ResourceType {
    /** DEX 文件（.dex），将注入到宿主 ClassLoader 中 */
    DEX,

    /** Native 库（.so），将注入到进程的 Native Library 搜索路径中 */
    NATIVE_LIBRARY,

    /** 普通 Asset 文件（任意二进制），将注入到宿主 APK 的 assets 目录 */
    ASSET,

    /** Android 资源文件（resources.arsc / res/**），将参与宿主的资源解析 */
    ANDROID_RESOURCE,

    /** 布局文件（XML），将替换或追加到宿主的布局中 */
    LAYOUT_XML,

    /** SharedPreferences 文件 */
    SHARED_PREFERENCES,
}

// ============================================================================
// 注入优先级
// ============================================================================

/**
 * 资源注入的优先级。
 *
 * 当注入的资源与宿主资源同名时，决定使用哪个。
 */
enum class InjectionPriority {
    /** 注入的资源优先：同名时使用注入的资源 */
    OVERRIDE,

    /** 宿主的资源优先：同名时保留宿主原有资源 */
    FALLBACK,

    /** 合并：如果支持合并（如布局），则合并；否则使用注入的资源 */
    MERGE,
}

// ============================================================================
// 注入目标范围
// ============================================================================

/**
 * 资源注入的目标范围。
 */
enum class InjectionScope {
    /** 当前进程可见 */
    PROCESS,

    /** 当前包名下所有进程可见 */
    PACKAGE,

    /** 全局可见（所有应用进程） */
    GLOBAL,
}

// ============================================================================
// 注入资源描述符
// ============================================================================

/**
 * 描述一个待注入的资源。
 *
 * @property resourceType 资源类型
 * @property sourcePath 资源来源路径（APK 内路径、文件系统绝对路径 或 内存数据）
 * @property targetName 注入后的目标名称（如 assets 路径、资源 ID 名称等）
 * @property priority 同名时的优先级策略
 * @property scope 注入的可见范围
 * @property metadata 附加元数据（如 Native 库的加载顺序、资源 ID 映射等）
 */
data class ResourceDescriptor(
    val resourceType: ResourceType,
    val sourcePath: String,
    val targetName: String,
    val priority: InjectionPriority = InjectionPriority.OVERRIDE,
    val scope: InjectionScope = InjectionScope.PROCESS,
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * 便捷构造：从 ByteArray 内存数据创建 DEX 资源描述符。
     */
    companion object {
        /**
         * 创建 DEX 资源描述符。
         *
         * @param dexName DEX 文件名，如 `"classes2.dex"`
         * @param sourcePath 来源路径
         * @param priority 优先级
         */
        fun dex(
            dexName: String,
            sourcePath: String,
            priority: InjectionPriority = InjectionPriority.OVERRIDE,
        ): ResourceDescriptor = ResourceDescriptor(
            resourceType = ResourceType.DEX,
            sourcePath = sourcePath,
            targetName = dexName,
            priority = priority,
        )

        /**
         * 创建 Native 库资源描述符。
         *
         * @param libName 库名，不含 `lib` 前缀和 `.so` 后缀，如 `"hook"`
         * @param sourcePath 来源路径
         * @param priority 优先级
         */
        fun nativeLibrary(
            libName: String,
            sourcePath: String,
            priority: InjectionPriority = InjectionPriority.OVERRIDE,
        ): ResourceDescriptor = ResourceDescriptor(
            resourceType = ResourceType.NATIVE_LIBRARY,
            sourcePath = sourcePath,
            targetName = "lib${libName}.so",
            priority = priority,
        )

        /**
         * 创建 Asset 资源描述符。
         *
         * @param assetPath Asset 目标路径，如 `"lspatch/config.json"`
         * @param sourcePath 来源路径
         * @param priority 优先级
         */
        fun asset(
            assetPath: String,
            sourcePath: String,
            priority: InjectionPriority = InjectionPriority.OVERRIDE,
        ): ResourceDescriptor = ResourceDescriptor(
            resourceType = ResourceType.ASSET,
            sourcePath = sourcePath,
            targetName = assetPath,
            priority = priority,
        )

        /**
         * 创建 Android 资源描述符。
         *
         * @param resPackage 资源包名
         * @param sourcePath 来源路径
         * @param priority 优先级
         */
        fun androidResource(
            resPackage: String,
            sourcePath: String,
            priority: InjectionPriority = InjectionPriority.MERGE,
        ): ResourceDescriptor = ResourceDescriptor(
            resourceType = ResourceType.ANDROID_RESOURCE,
            sourcePath = sourcePath,
            targetName = resPackage,
            priority = priority,
        )

        /**
         * 创建布局资源描述符。
         *
         * @param layoutId 布局资源 ID（如 `R.layout.activity_main` 的整数值）
         * @param sourcePath 来源路径
         * @param priority 优先级
         */
        fun layout(
            layoutId: Int,
            sourcePath: String,
            priority: InjectionPriority = InjectionPriority.OVERRIDE,
        ): ResourceDescriptor = ResourceDescriptor(
            resourceType = ResourceType.LAYOUT_XML,
            sourcePath = sourcePath,
            targetName = layoutId.toString(),
            priority = priority,
        )
    }
}

// ============================================================================
// 注入结果
// ============================================================================

/**
 * 单个资源的注入结果。
 *
 * @property descriptor 注入的资源描述符
 * @property success 是否成功
 * @property error 失败原因（仅在 [success] 为 false 时有效）
 * @property handle 注入句柄，用于后续卸载此资源
 */
data class InjectionResult(
    val descriptor: ResourceDescriptor,
    val success: Boolean,
    val error: String? = null,
    val handle: InjectionHandle? = null,
)

/**
 * 批量注入的汇总结果。
 */
data class BatchInjectionResult(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val results: List<InjectionResult>,
    val handles: List<InjectionHandle>,
) {
    /**
     * 撤销所有已注入的资源（回滚）。
     */
    fun rollback() {
        handles.forEach { it.unload() }
    }
}

// ============================================================================
// 注入句柄
// ============================================================================

/**
 * 注入句柄，代表一次已完成的资源注入。
 *
 * 调用 [unload] 可卸载已注入的资源，释放相关内存。
 * 句柄一旦 unload 后不可重复使用。
 *
 * @property id 全局唯一的注入标识符
 * @property descriptor 注入的资源描述符
 * @property isActive 当前是否处于激活状态
 */
data class InjectionHandle(
    val id: String,
    val descriptor: ResourceDescriptor,
    private val unloadAction: () -> Unit,
) {
    @Volatile
    private var _isActive = true

    /** 当前是否处于激活状态（未被卸载） */
    val isActive: Boolean get() = _isActive

    /**
     * 卸载已注入的资源，释放相关内存。
     *
     * 调用后 [isActive] 变为 `false`，重复调用无副作用。
     */
    fun unload() {
        if (_isActive) {
            _isActive = false
            unloadAction()
        }
    }
}

// ============================================================================
// 资源注入统计
// ============================================================================

/**
 * 资源注入的运行时统计信息。
 */
data class InjectionStats(
    /** 当前已注入的 DEX 数量 */
    val injectedDexCount: Int = 0,

    /** 当前已注入的 Native 库数量 */
    val injectedNativeLibCount: Int = 0,

    /** 当前已注入的 Asset 数量 */
    val injectedAssetCount: Int = 0,

    /** 当前已注入的 Android 资源包数量 */
    val injectedResPackageCount: Int = 0,

    /** 当前已注入的布局数量 */
    val injectedLayoutCount: Int = 0,

    /** 已注入资源总内存占用（字节，估算值） */
    val totalMemoryUsage: Long = 0,

    /** 所有已注入资源的句柄列表 */
    val activeHandles: List<InjectionHandle> = emptyList(),
)

// ============================================================================
// ResourceInjector 接口
// ============================================================================

/**
 * 资源注入器接口 —— LSPatch 2.0 Bridge 层的资源注入抽象。
 *
 * 提供统一的资源注入/卸载能力，支持 DEX、Native 库、Asset、Android 资源、布局
 * 等多种资源类型的注入。
 *
 * ### 资源类型支持
 * | 类型 | 注入方式 | 卸载方式 |
 * |------|---------|---------|
 * | [ResourceType.DEX] | 创建 [dalvik.system.InMemoryDexClassLoader]，追加到宿主 ClassLoader | 移除 ClassLoader，释放 DEX 内存 |
 * | [ResourceType.NATIVE_LIBRARY] | [System.load] 加载 so 到当前进程 | 通过 [NativeLibraryHandle] 执行 dlclose（尽力而为） |
 * | [ResourceType.ASSET] | 替换 [AssetManager] 中的 Asset | 恢复到原始 Asset |
 * | [ResourceType.ANDROID_RESOURCE] | 合并 resources.arsc，更新 [Resources] 对象 | 恢复到原始资源 |
 * | [ResourceType.LAYOUT_XML] | 替换 [Resources] 中的布局 XML | 恢复到原始布局 |
 *
 * ### 线程安全
 * 所有方法均为线程安全，可以从任意线程调用。
 *
 * ### 使用示例
 * ```kotlin
 * val injector: ResourceInjector = ...
 *
 * // 注入单个 DEX
 * val result = injector.inject(
 *     ResourceDescriptor.dex("classes2.dex", "/path/to/classes2.dex")
 * )
 * if (result.success) {
 *     Log.i("Inject", "DEX injected: ${result.handle?.id}")
 * }
 *
 * // 批量注入
 * val batchResult = injector.injectBatch(listOf(
 *     ResourceDescriptor.dex("classes2.dex", "/path/to/classes2.dex"),
 *     ResourceDescriptor.nativeLibrary("hook", "/path/to/libhook.so"),
 *     ResourceDescriptor.asset("config.json", "/path/to/config.json"),
 * ))
 * Log.i("Inject", "Succeeded: ${batchResult.succeeded}, Failed: ${batchResult.failed}")
 *
 * // 卸载
 * result.handle?.unload()
 * ```
 */
interface ResourceInjector {

    // --------------------------------------------------------------------------
    // 资源注入
    // --------------------------------------------------------------------------

    /**
     * 注入单个资源。
     *
     * @param descriptor 注入的资源描述符
     * @return 注入结果，包含成功/失败状态和卸载句柄
     */
    fun inject(descriptor: ResourceDescriptor): InjectionResult

    /**
     * 批量注入资源。
     *
     * 比逐个调用 [inject] 更高效，可以批量处理相同类型的资源。
     * 部分失败不影响其他资源的注入。
     *
     * @param descriptors 待注入的资源描述符列表
     * @return 批量注入结果，包含每个资源的独立结果
     */
    fun injectBatch(descriptors: List<ResourceDescriptor>): BatchInjectionResult

    // --------------------------------------------------------------------------
    // DEX 注入
    // --------------------------------------------------------------------------

    /**
     * 注入 DEX 文件到宿主 ClassLoader。
     *
     * 在 Android 8+ 上使用 [dalvik.system.InMemoryDexClassLoader]，
     * 在更低版本上使用 [dalvik.system.DexClassLoader] + 临时文件。
     *
     * @param dexName DEX 文件名，如 `"classes2.dex"`
     * @param dexData DEX 文件的字节数据
     * @param parentClassLoader 父 ClassLoader
     * @param priority 同名时的优先级
     * @return 注入结果
     */
    fun injectDex(
        dexName: String,
        dexData: ByteArray,
        parentClassLoader: ClassLoader,
        priority: InjectionPriority = InjectionPriority.OVERRIDE,
    ): InjectionResult

    /**
     * 注入 DEX 文件（从文件路径读取）。
     *
     * @param dexName DEX 文件名
     * @param dexPath DEX 文件路径
     * @param parentClassLoader 父 ClassLoader
     * @param priority 优先级
     * @return 注入结果
     * @see injectDex
     */
    fun injectDexFromFile(
        dexName: String,
        dexPath: String,
        parentClassLoader: ClassLoader,
        priority: InjectionPriority = InjectionPriority.OVERRIDE,
    ): InjectionResult

    // --------------------------------------------------------------------------
    // Native 库注入
    // --------------------------------------------------------------------------

    /**
     * 注入 Native 库到当前进程。
     *
     * 使用 [System.load] 或 [System.loadLibrary] 加载。
     * 注意：部分平台上 Native 库卸载（dlclose）可能不可靠。
     *
     * @param libName 库名，不含 `lib` 前缀和 `.so` 后缀
     * @param libPath 库文件的绝对路径
     * @param priority 优先级
     * @return 注入结果
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun injectNativeLibrary(
        libName: String,
        libPath: String,
        priority: InjectionPriority = InjectionPriority.OVERRIDE,
    ): InjectionResult

    /**
     * 注入 Native 库（从 ByteBuffer 内存数据）。
     *
     * 将内存数据写入临时文件后加载。适用于从 APK 内提取 so 的场景。
     *
     * @param libName 库名
     * @param libData 库文件的字节数据
     * @param priority 优先级
     * @return 注入结果
     */
    fun injectNativeLibraryFromMemory(
        libName: String,
        libData: ByteBuffer,
        priority: InjectionPriority = InjectionPriority.OVERRIDE,
    ): InjectionResult

    // --------------------------------------------------------------------------
    // Asset 注入
    // --------------------------------------------------------------------------

    /**
     * 注入 Asset 文件到宿主应用的 Asset 空间。
     *
     * 对 [android.content.res.AssetManager] 进行 Hook，使指定的 Asset 路径
     * 返回注入的数据。
     *
     * @param assetPath Asset 目标路径，如 `"lspatch/config.json"`
     * @param assetData Asset 文件的字节数据
     * @param priority 同名时的优先级
     * @return 注入结果
     */
    fun injectAsset(
        assetPath: String,
        assetData: ByteArray,
        priority: InjectionPriority = InjectionPriority.OVERRIDE,
    ): InjectionResult

    /**
     * 注入 Asset 文件（从文件路径读取）。
     *
     * @param assetPath Asset 目标路径
     * @param filePath 源文件路径
     * @param priority 优先级
     * @return 注入结果
     * @see injectAsset
     */
    fun injectAssetFromFile(
        assetPath: String,
        filePath: String,
        priority: InjectionPriority = InjectionPriority.OVERRIDE,
    ): InjectionResult

    // --------------------------------------------------------------------------
    // Android 资源注入
    // --------------------------------------------------------------------------

    /**
     * 注入 Android 资源包（resources.arsc）到宿主应用。
     *
     * 合并注入的资源包与宿主原有的资源包，使注入的资源 ID 可通过 [Resources] 系列方法访问。
     *
     * @param resPackage 资源包名
     * @param arscPath resources.arsc 文件路径
     * @param priority 同名资源 ID 的优先级
     * @return 注入结果
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun injectResources(
        resPackage: String,
        arscPath: String,
        priority: InjectionPriority = InjectionPriority.MERGE,
    ): InjectionResult

    /**
     * 注入布局 XML 文件。
     *
     * 替换或追加到宿主 [Resources] 的布局缓存中。
     *
     * @param layoutId 布局资源 ID 的整数值
     * @param layoutXml 布局 XML 内容
     * @param priority 优先级
     * @return 注入结果
     */
    fun injectLayout(
        layoutId: Int,
        layoutXml: InputStream,
        priority: InjectionPriority = InjectionPriority.OVERRIDE,
    ): InjectionResult

    // --------------------------------------------------------------------------
    // 资源卸载
    // --------------------------------------------------------------------------

    /**
     * 卸载指定句柄对应的资源。
     *
     * 与 [InjectionHandle.unload] 等效，但允许通过句柄 ID 卸载。
     *
     * @param handleId 注入句柄的 ID
     * @return 是否找到并成功卸载
     */
    fun unload(handleId: String): Boolean

    /**
     * 卸载所有已注入的资源。
     *
     * 按注入的逆序卸载，确保依赖关系正确。
     *
     * @return 成功卸载的数量
     */
    fun unloadAll(): Int

    /**
     * 卸载指定类型的全部已注入资源。
     *
     * @param resourceType 要卸载的资源类型
     * @return 成功卸载的数量
     */
    fun unloadByType(resourceType: ResourceType): Int

    // --------------------------------------------------------------------------
    // 查询
    // --------------------------------------------------------------------------

    /**
     * 获取注入统计信息。
     */
    val stats: InjectionStats

    /**
     * 获取所有活跃的注入句柄。
     */
    val activeHandles: List<InjectionHandle>

    /**
     * 判断指定资源是否已被注入。
     *
     * @param resourceType 资源类型
     * @param targetName 目标名称
     * @return 如果已注入返回对应的句柄，否则返回 `null`
     */
    fun findInjected(resourceType: ResourceType, targetName: String): InjectionHandle?
}

// ============================================================================
// 异常
// ============================================================================

/**
 * 资源注入异常。
 *
 * 当资源注入失败时抛出，包含失败原因和资源描述。
 */
class ResourceInjectionException(
    val descriptor: ResourceDescriptor,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("Failed to inject ${descriptor.resourceType} '${descriptor.targetName}': $message", cause)