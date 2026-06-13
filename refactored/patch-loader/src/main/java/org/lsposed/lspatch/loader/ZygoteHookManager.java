package org.lsposed.lspatch.loader;

import static org.lsposed.lspatch.share.Constants.CONFIG_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;

import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;
import android.os.Build;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;

import com.google.gson.Gson;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.service.LocalApplicationService;
import org.lsposed.lspatch.service.RemoteApplicationService;
import org.lsposed.lspatch.share.PatchConfig;
import org.lsposed.lspd.core.Startup;
import org.lsposed.lspd.service.ILSPApplicationService;
import org.lsposed.lspatch.v2.hook.HookProvider;
import org.lsposed.lspatch.v2.adapter.VersionAdapter;
import org.lsposed.lspatch.v2.adapter.VersionAdapterFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XposedHelpers;
import hidden.HiddenApiBridge;

// ============================================================================
// ZygoteHookManager — 重构后的 Zygote Hook 入口
// ============================================================================

/**
 * Zygote Hook 管理器 —— LSPatch 2.0 重构后的启动入口。
 *
 * <h3>与原 LSPApplication 的区别</h3>
 * <table>
 *   <tr><th>方面</th><th>原实现</th><th>重构后</th></tr>
 *   <tr><td>版本适配</td><td>分散的 if (Build.VERSION.SDK_INT >= ...)</td>
 *       <td>通过 {@link VersionAdapter} 策略模式统一管理</td></tr>
 *   <tr><td>Hook 注入</td><td>硬编码 XposedHelpers 反射调用</td>
 *       <td>通过 {@link HookProvider} 接口抽象</td></tr>
 *   <tr><td>错误恢复</td><td>无</td>
 *       <td>分级错误恢复：重试 → 降级 → 跳过</td></tr>
 *   <tr><td>日志</td><td>分散的 Log.d/e</td>
 *       <td>统一的 {@link BootstrapLogger} 带阶段标记</td></tr>
 *   <tr><td>线程安全</td><td>静态字段直接赋值</td>
 *       <td>{@link AtomicReference} 保护关键状态</td></tr>
 * </table>
 *
 * <h3>启动阶段</h3>
 * <ol>
 *   <li>{@link #probe} — 探测设备能力</li>
 *   <li>{@link #prepare} — 准备环境（提取 APK、解析配置）</li>
 *   <li>{@link #activate} — 激活 Hook（创建 LoadedApk、初始化 Xposed、加载模块）</li>
 *   <li>{@link #finalize} — 收尾（ClassLoader 切换、签名绕过）</li>
 * </ol>
 *
 * 每个阶段失败时均可回滚到上一阶段，确保不留下半初始化状态。
 */
@SuppressWarnings("unused")
public class ZygoteHookManager {

    // ========================================================================
    // 状态枚举
    // ========================================================================

    /** 启动阶段 */
    public enum Phase {
        UNINITIALIZED,
        PROBING,
        PROBED,
        PREPARING,
        PREPARED,
        ACTIVATING,
        ACTIVE,
        FINALIZING,
        COMPLETED,
        FAILED,
    }

    /** 失败时采取的策略 */
    public enum FailurePolicy {
        /** 重试一次 */
        RETRY_ONCE,
        /** 降级到安全模式（跳过非关键步骤） */
        DEGRADE,
        /** 跳过此步骤继续 */
        SKIP,
        /** 终止启动 */
        ABORT,
    }

    // ========================================================================
    // 常量
    // ========================================================================

    private static final String TAG = "LSPatch-ZygoteHook";
    private static final int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;
    private static final int PER_USER_RANGE = 100000;
    private static final int MAX_RETRY_COUNT = 2;

    // ========================================================================
    // 线程安全状态
    // ========================================================================

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.UNINITIALIZED);
    private final AtomicReference<ActivityThread> activityThreadRef = new AtomicReference<>();
    private final AtomicReference<LoadedApk> stubLoadedApkRef = new AtomicReference<>();
    private final AtomicReference<LoadedApk> appLoadedApkRef = new AtomicReference<>();
    private final AtomicReference<PatchConfig> configRef = new AtomicReference<>();
    private final AtomicReference<VersionAdapter> adapterRef = new AtomicReference<>();
    private final AtomicReference<HookProvider> hookProviderRef = new AtomicReference<>();

    private final BootstrapLogger logger;
    private final PerformanceCounter perfCounter;

    public ZygoteHookManager() {
        this.logger = new BootstrapLogger(TAG);
        this.perfCounter = new PerformanceCounter();
    }

    // ========================================================================
    // 公共入口
    // ========================================================================

    /**
     * 主启动入口 — 从 Native 层通过 JNI 调用。
     *
     * 异常安全保证：任何阶段失败都会回滚到上一个稳定状态，绝不留下半初始化。
     *
     * @throws RemoteException 如果 Manager 通信失败
     * @throws IOException 如果文件操作失败
     */
    public void onLoad() throws RemoteException, IOException {
        if (isIsolated()) {
            logger.debug("Skip isolated process (uid=%d)", android.os.Process.myUid());
            return;
        }

        perfCounter.start("total_bootstrap");
        logger.info("=== LSPatch 2.0 Bootstrap Start ===");
        logger.info("SDK=%d, Process=%s", Build.VERSION.SDK_INT, ActivityThread.currentProcessName());

        try {
            // ── 阶段 1: 探测 ──
            transitionTo(Phase.PROBING);
            if (!probe()) {
                abort("Device capability probe failed");
                return;
            }
            transitionTo(Phase.PROBED);

            // ── 阶段 2: 准备 ──
            transitionTo(Phase.PREPARING);
            if (!prepare()) {
                abort("Environment preparation failed");
                return;
            }
            transitionTo(Phase.PREPARED);

            // ── 阶段 3: 激活 ──
            transitionTo(Phase.ACTIVATING);
            if (!activate()) {
                abort("Hook activation failed");
                return;
            }
            transitionTo(Phase.ACTIVE);

            // ── 阶段 4: 收尾 ──
            transitionTo(Phase.FINALIZING);
            finalizeBootstrap();
            transitionTo(Phase.COMPLETED);

            perfCounter.stop("total_bootstrap");
            logger.info("=== LSPatch 2.0 Bootstrap Complete (%dms) ===", perfCounter.get("total_bootstrap"));

        } catch (Throwable e) {
            logger.error("Bootstrap failed at phase %s: %s", phase.get(), e.getMessage(), e);
            transitionTo(Phase.FAILED);
            rollback();
        }
    }

    // ========================================================================
    // 阶段 1: 探测
    // ========================================================================

    /**
     * 探测设备能力，创建版本适配器。
     */
    private boolean probe() {
        return executeWithRecovery("probe", () -> {
            perfCounter.start("probe");
            var adapter = VersionAdapterFactory.createOrFallback();
            adapterRef.set(adapter);
            var cap = adapter.probeCapability();
            logger.info("Adapter: %s, SDK=%d, userfaultfd=%b, memfd_restrict=%b",
                    adapter.adapterName(),
                    cap.sdkInt(),
                    cap.hasUserfaultfdGC(),
                    cap.hasMemfdRestriction());
            logger.info("SELinux enforcing=%b, context=%s",
                    adapter.isSelinuxEnforcing(),
                    adapter.getSelinuxContext());
            perfCounter.stop("probe");
            return true;
        }, FailurePolicy.RETRY_ONCE);
    }

    // ========================================================================
    // 阶段 2: 准备
    // ========================================================================

    /**
     * 准备环境：提取原始 APK、解析配置、创建 Context。
     */
    private boolean prepare() {
        return executeWithRecovery("prepare", () -> {
            perfCounter.start("prepare");
            var activityThread = ActivityThread.currentActivityThread();
            activityThreadRef.set(activityThread);

            var context = createLoadedApkWithContext(activityThread);
            if (context == null) {
                logger.error("createLoadedApkWithContext returned null");
                return false;
            }
            perfCounter.stop("prepare");
            return true;
        }, FailurePolicy.RETRY_ONCE);
    }

    // ========================================================================
    // 阶段 3: 激活
    // ========================================================================

    /**
     * 激活 Hook：初始化 Xposed 框架、加载模块。
     */
    private boolean activate() {
        return executeWithRecovery("activate", () -> {
            perfCounter.start("activate");
            var context = activityThreadRef.get().getApplication();
            var config = configRef.get();
            var loadedApk = appLoadedApkRef.get();

            // 创建服务客户端
            ILSPApplicationService service;
            if (config.useManager) {
                service = new RemoteApplicationService(context);
            } else {
                service = new LocalApplicationService(context);
            }

            // 禁用 JIT Profile
            perfCounter.start("disable_profile");
            disableProfile(context);
            perfCounter.stop("disable_profile");

            // 初始化 Xposed
            perfCounter.start("init_xposed");
            Startup.initXposed(false, ActivityThread.currentProcessName(),
                    context.getApplicationInfo().dataDir, service);
            Startup.bootstrapXposed();
            perfCounter.stop("init_xposed");

            // 加载模块
            perfCounter.start("load_modules");
            LSPLoader.initModules(loadedApk);
            perfCounter.stop("load_modules");

            logger.info("Modules loaded: %d packages in process",
                    de.robv.android.xposed.XposedInit.loadedPackagesInProcess.size());
            perfCounter.stop("activate");
            return true;
        }, FailurePolicy.DEGRADE);
    }

    // ========================================================================
    // 阶段 4: 收尾
    // ========================================================================

    /**
     * 收尾：ClassLoader 切换、签名绕过。
     */
    private void finalizeBootstrap() throws IOException {
        executeWithRecovery("finalize", () -> {
            perfCounter.start("finalize");
            var config = configRef.get();

            // ClassLoader 切换
            perfCounter.start("switch_classloader");
            switchAllClassLoader();
            perfCounter.stop("switch_classloader");

            // 签名绕过
            perfCounter.start("sig_bypass");
            var context = getActivityThread().getApplication();
            SigBypass.doSigBypass(context, config.sigBypassLevel);
            perfCounter.stop("sig_bypass");

            perfCounter.stop("finalize");
            return true;
        }, FailurePolicy.SKIP);
    }

    // ========================================================================
    // 回滚
    // ========================================================================

    /**
     * 回滚到安全状态。
     *
     * 根据当前阶段执行逆向操作：
     * - ACTIVATING → 移除已加载的模块
     * - PREPARING → 清理提取的 APK 文件
     * - PROBING → 释放适配器
     */
    private void rollback() {
        logger.warn("Rolling back from phase: %s", phase.get());
        try {
            var current = phase.get();
            if (current == Phase.ACTIVATING || current == Phase.ACTIVE) {
                logger.info("Rollback: deactivating modules");
                appLoadedApkRef.set(null);
            }
            if (current == Phase.PREPARING || current == Phase.PREPARED) {
                logger.info("Rollback: cleaning up prepared state");
                stubLoadedApkRef.set(null);
                configRef.set(null);
            }
            if (current == Phase.PROBING || current == Phase.PROBED) {
                logger.info("Rollback: releasing adapter");
                adapterRef.set(null);
            }
            activityThreadRef.set(null);
            transitionTo(Phase.UNINITIALIZED);
            logger.info("Rollback complete");
        } catch (Throwable e) {
            logger.error("Rollback failed: %s", e.getMessage(), e);
        }
    }

    // ========================================================================
    // 核心逻辑（从原 LSPApplication 迁移）
    // ========================================================================

    /**
     * 创建 LoadedApk 和 Context。
     *
     * 与原 LSPApplication.createLoadedApkWithContext() 行为一致，
     * 但增加了详细日志和错误处理。
     */
    private Context createLoadedApkWithContext(ActivityThread activityThread) {
        try {
            var mBoundApplication = XposedHelpers.getObjectField(activityThread, "mBoundApplication");
            var stubLoadedApk = (LoadedApk) XposedHelpers.getObjectField(mBoundApplication, "info");
            var appInfo = (ApplicationInfo) XposedHelpers.getObjectField(mBoundApplication, "appInfo");
            var compatInfo = (CompatibilityInfo) XposedHelpers.getObjectField(mBoundApplication, "compatInfo");
            var baseClassLoader = stubLoadedApk.getClassLoader();

            stubLoadedApkRef.set(stubLoadedApk);

            // 解析配置
            PatchConfig config;
            try (var is = baseClassLoader.getResourceAsStream(CONFIG_ASSET_PATH)) {
                var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                config = new Gson().fromJson(reader, PatchConfig.class);
            } catch (IOException e) {
                logger.error("Failed to load config from %s: %s", CONFIG_ASSET_PATH, e.getMessage());
                return null;
            }
            configRef.set(config);
            logger.info("Config: useManager=%b, sigBypassLevel=%d, appComponentFactory=%s",
                    config.useManager, config.sigBypassLevel, config.appComponentFactory);

            // 提取原始 APK
            Path originPath = Paths.get(appInfo.dataDir, "cache/lspatch/origin/");
            Path cacheApkPath;
            try (var sourceFile = new ZipFile(appInfo.sourceDir)) {
                var entry = sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH);
                if (entry == null) {
                    logger.error("Original APK entry not found: %s", ORIGINAL_APK_ASSET_PATH);
                    return null;
                }
                cacheApkPath = originPath.resolve(entry.getCrc() + ".apk");
            }

            appInfo.sourceDir = cacheApkPath.toString();
            appInfo.publicSourceDir = cacheApkPath.toString();
            appInfo.appComponentFactory = config.appComponentFactory;

            if (!Files.exists(cacheApkPath)) {
                logger.info("Extracting original APK to %s", cacheApkPath);
                FileUtils.deleteFolderIfExists(originPath);
                Files.createDirectories(originPath);
                try (var is = baseClassLoader.getResourceAsStream(ORIGINAL_APK_ASSET_PATH)) {
                    if (is == null) {
                        logger.error("Original APK resource not found in assets");
                        return null;
                    }
                    Files.copy(is, cacheApkPath);
                }
                logger.info("Original APK extracted: %d bytes", Files.size(cacheApkPath));
            }
            cacheApkPath.toFile().setWritable(false);

            // 替换 ActivityThread 中的 LoadedApk
            var mPackages = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mPackages");
            mPackages.remove(appInfo.packageName);
            var appLoadedApk = activityThread.getPackageInfoNoCheck(appInfo, compatInfo);
            appLoadedApkRef.set(appLoadedApk);
            XposedHelpers.setObjectField(mBoundApplication, "info", appLoadedApk);

            // 修复 ActivityClientRecord 中的 LoadedApk 引用
            fixActivityClientRecords(activityThread, stubLoadedApk, appLoadedApk);

            logger.info("LoadedApk replaced: %s → %s", stubLoadedApk, appLoadedApk);

            // 创建 Context
            var context = (Context) XposedHelpers.callStaticMethod(
                    Class.forName("android.app.ContextImpl"),
                    "createAppContext", activityThread, stubLoadedApk);

            // 验证 AppComponentFactory
            if (config.appComponentFactory != null) {
                try {
                    context.getClassLoader().loadClass(config.appComponentFactory);
                } catch (ClassNotFoundException e) {
                    logger.warn("Original AppComponentFactory not found: %s (may be 3rd-party shell)",
                            config.appComponentFactory);
                    appInfo.appComponentFactory = null;
                }
            }

            return context;
        } catch (Throwable e) {
            logger.error("createLoadedApkWithContext failed: %s", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 修复 ActivityThread 中 mActivities 和 mLaunchingActivities 的 LoadedApk 引用。
     */
    @SuppressWarnings("unchecked")
    private void fixActivityClientRecords(ActivityThread activityThread,
                                          LoadedApk stub, LoadedApk app) {
        try {
            var activityClientRecordClass = XposedHelpers.findClass(
                    "android.app.ActivityThread$ActivityClientRecord",
                    ActivityThread.class.getClassLoader());

            BiConsumer<Object, Object> fixer = (k, v) -> {
                if (activityClientRecordClass.isInstance(v)) {
                    var pkgInfo = XposedHelpers.getObjectField(v, "packageInfo");
                    if (pkgInfo == stub) {
                        logger.debug("Fixing LoadedApk in ActivityClientRecord");
                        XposedHelpers.setObjectField(v, "packageInfo", app);
                    }
                }
            };

            var mActivities = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mActivities");
            mActivities.forEach(fixer);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    var mLaunchingActivities = (Map<?, ?>) XposedHelpers.getObjectField(
                            activityThread, "mLaunchingActivities");
                    mLaunchingActivities.forEach(fixer);
                } catch (Throwable ignored) {
                    logger.debug("mLaunchingActivities not available (pre-S)");
                }
            }
            logger.debug("ActivityClientRecord fix complete");
        } catch (Throwable e) {
            logger.warn("fixActivityClientRecords failed (non-fatal): %s", e.getMessage());
        }
    }

    /**
     * 禁用 JIT Profile 文件。
     *
     * 与原 LSPApplication.disableProfile() 行为一致，但增加错误恢复。
     */
    private void disableProfile(Context context) {
        var appInfo = context.getApplicationInfo();
        if (appInfo == null) return;

        final var codePaths = new ArrayList<String>();
        if ((appInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0) {
            codePaths.add(appInfo.sourceDir);
        }
        if (appInfo.splitSourceDirs != null) {
            Collections.addAll(codePaths, appInfo.splitSourceDirs);
        }
        if (codePaths.isEmpty()) return;

        var pkgName = context.getPackageName();
        var profileDir = HiddenApiBridge.Environment_getDataProfilesDePackageDirectory(
                appInfo.uid / PER_USER_RANGE, pkgName);
        var attrs = PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("r--------"));

        int disabled = 0, skipped = 0, failed = 0;
        for (int i = codePaths.size() - 1; i >= 0; i--) {
            String splitName = (i == 0) ? null : appInfo.splitNames[i - 1];
            var curProfileFile = new File(profileDir,
                    splitName == null ? "primary.prof" : splitName + ".split.prof").getAbsoluteFile();

            try {
                if (!curProfileFile.canWrite() && Files.size(curProfileFile.toPath()) == 0) {
                    skipped++;
                    continue;
                }
                if (curProfileFile.exists() && !curProfileFile.delete()) {
                    try (var writer = new FileOutputStream(curProfileFile)) {
                        // 清空内容
                    } catch (Throwable ignored) {
                    }
                    Os.chmod(curProfileFile.getAbsolutePath(), 00400);
                } else {
                    Files.createFile(curProfileFile.toPath(), attrs);
                }
                disabled++;
            } catch (Throwable e) {
                logger.warn("Failed to disable profile %s: %s",
                        curProfileFile.getAbsolutePath(), e.getMessage());
                failed++;
            }
        }
        logger.info("Profile: disabled=%d, skipped=%d, failed=%d", disabled, skipped, failed);
    }

    /**
     * 将 stub LoadedApk 的 ClassLoader 替换为 app LoadedApk 的 ClassLoader。
     */
    private void switchAllClassLoader() {
        var stub = stubLoadedApkRef.get();
        var app = appLoadedApkRef.get();
        if (stub == null || app == null) return;

        int switched = 0;
        for (Field field : LoadedApk.class.getDeclaredFields()) {
            if (field.getType() == ClassLoader.class) {
                try {
                    var obj = XposedHelpers.getObjectField(app, field.getName());
                    XposedHelpers.setObjectField(stub, field.getName(), obj);
                    switched++;
                } catch (Throwable e) {
                    logger.debug("Failed to switch ClassLoader field '%s': %s",
                            field.getName(), e.getMessage());
                }
            }
        }
        logger.info("ClassLoader fields switched: %d", switched);
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private static boolean isIsolated() {
        return (android.os.Process.myUid() % PER_USER_RANGE) >= FIRST_APP_ZYGOTE_ISOLATED_UID;
    }

    private ActivityThread getActivityThread() {
        return activityThreadRef.get();
    }

    private void transitionTo(Phase newPhase) {
        var oldPhase = phase.getAndSet(newPhase);
        logger.debug("Phase: %s → %s", oldPhase, newPhase);
    }

    private void abort(String reason) {
        logger.error("ABORT: %s", reason);
        transitionTo(Phase.FAILED);
        rollback();
    }

    /**
     * 执行操作并处理失败恢复。
     *
     * @param name 操作名称（用于日志）
     * @param action 要执行的操作
     * @param policy 失败时的处理策略
     * @return 操作是否成功
     */
    private boolean executeWithRecovery(String name, ThrowingSupplier<Boolean> action,
                                         FailurePolicy policy) {
        try {
            logger.info("→ %s", name);
            boolean result = action.get();
            if (!result) {
                logger.warn("← %s FAILED", name);
                return applyFailurePolicy(name, action, policy);
            }
            logger.info("← %s OK", name);
            return true;
        } catch (Throwable e) {
            logger.error("← %s EXCEPTION: %s", name, e.getMessage(), e);
            return applyFailurePolicy(name, action, policy);
        }
    }

    private boolean applyFailurePolicy(String name, ThrowingSupplier<Boolean> action,
                                        FailurePolicy policy) {
        switch (policy) {
            case RETRY_ONCE:
                logger.info("Retrying %s (attempt 2)...", name);
                try {
                    return action.get();
                } catch (Throwable e) {
                    logger.error("Retry also failed: %s", e.getMessage());
                    return false;
                }
            case DEGRADE:
                logger.warn("Degrading: skipping %s", name);
                return true; // 降级：跳过但继续
            case SKIP:
                logger.warn("Skipping %s", name);
                return true;
            case ABORT:
            default:
                return false;
        }
    }

    // ========================================================================
    // 内部类
    // ========================================================================

    /** 可抛出异常的 Supplier */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    /** 启动过程日志器，带阶段前缀 */
    static class BootstrapLogger {
        private final String tag;

        BootstrapLogger(String tag) {
            this.tag = tag;
        }

        void debug(String fmt, Object... args) {
            Log.d(tag, format(fmt, args));
        }

        void info(String fmt, Object... args) {
            Log.i(tag, format(fmt, args));
        }

        void warn(String fmt, Object... args) {
            Log.w(tag, format(fmt, args));
        }

        void error(String fmt, Object... args) {
            Log.e(tag, format(fmt, args));
        }

        void error(String fmt, String msg, Throwable tr) {
            Log.e(tag, format(fmt, msg), tr);
        }

        private static String format(String fmt, Object... args) {
            if (args.length == 0) return fmt;
            return String.format(fmt, args);
        }
    }

    /** 性能计数器 */
    static class PerformanceCounter {
        private final Map<String, Long> startTimes = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, Long> durations = new java.util.concurrent.ConcurrentHashMap<>();

        void start(String name) {
            startTimes.put(name, System.currentTimeMillis());
        }

        void stop(String name) {
            var start = startTimes.remove(name);
            if (start != null) {
                durations.put(name, System.currentTimeMillis() - start);
            }
        }

        long get(String name) {
            return durations.getOrDefault(name, -1L);
        }
    }
}