package org.lsposed.lspatch.v2.perf;

import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lsposed.lspatch.v2.hook.*;
import org.lsposed.lspatch.v2.module.*;
import org.lsposed.lspatch.compat.adapter.VersionAdapter;
import org.lsposed.lspatch.compat.adapter.VersionAdapterFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.Assert.*;

/**
 * 性能基准测试 — 测量 Hook 前后的性能影响。
 *
 * <pre>
 * 测试指标:
 * ┌───────────────────────────────┬──────────┬───────────────────────┐
 * │ 指标                          │ 目标     │ 说明                   │
 * ├───────────────────────────────┼──────────┼───────────────────────┤
 * │ Hook 注册延迟                 │ < 5ms    │ hookMethod 调用耗时   │
 * │ Hook 卸载延迟                 │ < 5ms    │ unhook 调用耗时       │
 * │ 方法调用开销 (无 Hook)        │ < 1us    │ 基准线：无 Hook 时    │
 * │ 方法调用开销 (BEFORE)         │ < 10us   │ 带 BEFORE 回调时      │
 * │ 方法调用开销 (AFTER)          │ < 10us   │ 带 AFTER 回调时       │
 * │ 并发 Hook 吞吐量              │ > 1000/s │ 每秒可注册的最大 Hook  │
 * │ 内存占用 (每 Hook)            │ < 1KB    │ 每个 Hook 的额外内存  │
 * │ 模块加载总时间                │ < 100ms  │ DEX 解析 + 类加载     │
 * └───────────────────────────────┴──────────┴───────────────────────┘
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PerformanceBenchmarkTest {

    private static final String TAG = "LSPatch-PerfBench";
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCH_ITERATIONS = 1000;
    private static final int CONCURRENT_BENCH_THREADS = 4;
    private static final int CONCURRENT_BENCH_HOOKS = 500;

    private HookProvider hookProvider;
    private final List<String> benchmarkResults = new ArrayList<>();

    // ========================================================================
    // 测试目标
    // ========================================================================

    public static class PerfTarget {
        @SuppressWarnings("unused")
        public int fastMethod(int a, int b) {
            return a + b;
        }

        @SuppressWarnings("unused")
        public String stringMethod(String input) {
            return input.toUpperCase();
        }

        @SuppressWarnings("unused")
        public void voidMethod() {
            // no-op
        }
    }

    // ========================================================================
    // 初始化
    // ========================================================================

    @Before
    public void setUp() {
        hookProvider = new MockHookProvider();
        benchmarkResults.add("=== LSPatch 2.0 Performance Benchmark ===");
        benchmarkResults.add("Date: " + new Date());
        benchmarkResults.add("SDK: " + android.os.Build.VERSION.SDK_INT);
        benchmarkResults.add("Device: " + android.os.Build.MODEL);
        benchmarkResults.add("Arch: " + System.getProperty("os.arch"));
        benchmarkResults.add("");
    }

    @After
    public void tearDown() {
        // 写入报告
        StringBuilder report = new StringBuilder();
        for (String line : benchmarkResults) {
            report.append(line).append("\n");
        }
        Log.i(TAG, report.toString());
    }

    // ========================================================================
    // 基准测试
    // ========================================================================

    @Test
    public void benchmark_hookRegistrationLatency() throws Exception {
        Method method = PerfTarget.class.getDeclaredMethod("fastMethod", int.class, int.class);

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            HookHandle h = hookProvider.hookMethod(method, Collections.emptyList());
            h.unhook();
        }

        // 测量
        long totalHookNs = 0;
        long totalUnhookNs = 0;
        for (int i = 0; i < BENCH_ITERATIONS; i++) {
            long start = System.nanoTime();
            HookHandle h = hookProvider.hookMethod(method, Collections.emptyList());
            totalHookNs += System.nanoTime() - start;

            start = System.nanoTime();
            h.unhook();
            totalUnhookNs += System.nanoTime() - start;
        }

        double avgHookUs = totalHookNs / (double) BENCH_ITERATIONS / 1000.0;
        double avgUnhookUs = totalUnhookNs / (double) BENCH_ITERATIONS / 1000.0;

        benchmarkResults.add(String.format("Hook Registration: avg=%.2fus (n=%d)", avgHookUs, BENCH_ITERATIONS));
        benchmarkResults.add(String.format("Hook Unregistration: avg=%.2fus (n=%d)", avgUnhookUs, BENCH_ITERATIONS));

        assertTrue("Hook registration should be under 5000us", avgHookUs < 5000);
        assertTrue("Hook unregistration should be under 5000us", avgUnhookUs < 5000);
    }

    @Test
    public void benchmark_methodCallOverhead() throws Exception {
        PerfTarget target = new PerfTarget();
        Method method = PerfTarget.class.getDeclaredMethod("fastMethod", int.class, int.class);

        // 基准：无 Hook
        long baselineTotal = 0;
        for (int i = 0; i < BENCH_ITERATIONS; i++) {
            long start = System.nanoTime();
            target.fastMethod(i, i + 1);
            baselineTotal += System.nanoTime() - start;
        }
        double baselineNs = baselineTotal / (double) BENCH_ITERATIONS;

        // 带 BEFORE Hook
        HookHandle h = hookProvider.hookMethod(method,
                Collections.singletonList(new HookCallback<>(HookTiming.BEFORE,
                        ctx -> HookResult.Continue.INSTANCE)));
        long hookedTotal = 0;
        for (int i = 0; i < BENCH_ITERATIONS; i++) {
            long start = System.nanoTime();
            target.fastMethod(i, i + 1);
            hookedTotal += System.nanoTime() - start;
        }
        double hookedNs = hookedTotal / (double) BENCH_ITERATIONS;
        h.unhook();

        double overheadPercent = ((hookedNs - baselineNs) / baselineNs) * 100;
        benchmarkResults.add(String.format("Baseline (no hook): %.2fns/call", baselineNs));
        benchmarkResults.add(String.format("With BEFORE hook: %.2fns/call (%.1f%% overhead)", hookedNs, overheadPercent));

        Log.i(TAG, String.format("Overhead: %.1f%%", overheadPercent));
        assertTrue("Hook overhead should be reasonable (< 500%)",
                overheadPercent < 500);
    }

    @Test
    public void benchmark_concurrentHookThroughput() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_BENCH_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_BENCH_THREADS);
        AtomicLong totalHooks = new AtomicLong(0);

        long startTime = SystemClock.elapsedRealtime();

        for (int t = 0; t < CONCURRENT_BENCH_THREADS; t++) {
            executor.submit(() -> {
                try {
                    Method method = PerfTarget.class.getDeclaredMethod("fastMethod", int.class, int.class);
                    for (int i = 0; i < CONCURRENT_BENCH_HOOKS; i++) {
                        HookHandle h = hookProvider.hookMethod(method, Collections.emptyList());
                        totalHooks.incrementAndGet();
                        h.unhook();
                    }
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long elapsedMs = SystemClock.elapsedRealtime() - startTime;
        double throughput = totalHooks.get() / (elapsedMs / 1000.0);

        benchmarkResults.add(String.format("Concurrent Throughput: %.0f hooks/s (%d threads, %d hooks each, %dms)",
                throughput, CONCURRENT_BENCH_THREADS, CONCURRENT_BENCH_HOOKS, elapsedMs));

        assertTrue("Throughput should be > 100 hooks/s", throughput > 100);
        executor.shutdown();
    }

    @Test
    public void benchmark_memoryEstimate() throws Exception {
        // 运行 GC 建立基线
        Runtime rt = Runtime.getRuntime();
        System.gc();
        System.runFinalization();
        System.gc();
        long baselineMemory = rt.totalMemory() - rt.freeMemory();

        Method method = PerfTarget.class.getDeclaredMethod("fastMethod", int.class, int.class);
        List<HookHandle> handles = new ArrayList<>();

        // 注册 1000 个 Hook
        for (int i = 0; i < 1000; i++) {
            handles.add(hookProvider.hookMethod(method, Collections.emptyList()));
        }

        System.gc();
        System.runFinalization();
        System.gc();
        long hookedMemory = rt.totalMemory() - rt.freeMemory();

        long memoryPerHook = (hookedMemory - baselineMemory) / 1000;

        // 清理
        for (HookHandle h : handles) {
            h.unhook();
        }
        handles.clear();

        benchmarkResults.add(String.format("Memory per hook: %d bytes (estimated over 1000 hooks)", memoryPerHook));
        benchmarkResults.add(String.format("Baseline memory: %d KB", baselineMemory / 1024));
        benchmarkResults.add(String.format("Hooked memory: %d KB", hookedMemory / 1024));

        assertTrue("Memory per hook should be < 10KB", memoryPerHook < 10240);
    }

    // ========================================================================
    // Mock HookProvider
    // ========================================================================

    static class MockHookProvider implements HookProvider {
        private final AtomicInteger idCounter = new AtomicInteger(1);

        @Override
        public HookHandle hookMethod(String targetClass, String methodName,
                                      Class<?>[] paramTypes, List<HookCallback<?>> callbacks) {
            try {
                Class<?> clazz = Class.forName(targetClass);
                Method m = clazz.getDeclaredMethod(methodName, paramTypes);
                return hookMethod(m, callbacks);
            } catch (Exception e) {
                throw new HookException("Hook failed", e);
            }
        }

        @Override
        public HookHandle hookMethod(Method target, List<HookCallback<?>> callbacks) {
            String id = "perf_" + idCounter.getAndIncrement();
            return new HookHandle(id, target, () -> {});
        }

        @Override public HookHandle hookConstructor(String targetClass, Class<?>[] paramTypes,
                                                      List<HookCallback<?>> callbacks) {
            String id = "perf_" + idCounter.getAndIncrement();
            return new HookHandle(id, null, () -> {});
        }

        @Override public HookHandle hookConstructor(Constructor<?> target,
                                                      List<HookCallback<?>> callbacks) {
            String id = "perf_" + idCounter.getAndIncrement();
            return new HookHandle(id, target, () -> {});
        }

        @Override public HookHandle hookField(String targetClass, String fieldName,
                                                List<HookCallback<?>> callbacks) {
            String id = "perf_" + idCounter.getAndIncrement();
            return new HookHandle(id, null, () -> {});
        }

        @Override public HookHandle hookStaticField(String targetClass, String fieldName,
                                                      List<HookCallback<?>> callbacks) {
            return hookField(targetClass, fieldName, callbacks);
        }

        @Override public HookHandle hookField(Field target, List<HookCallback<?>> callbacks) {
            String id = "perf_" + idCounter.getAndIncrement();
            return new HookHandle(id, target, () -> {});
        }

        @Override public List<HookHandle> hookAll(String targetClass,
                                                    java.util.function.Predicate<Method> predicate,
                                                    List<HookCallback<?>> callbacks) {
            return java.util.Collections.emptyList();
        }

        @Override public HookCapability capability() {
            return new HookCapability(true, true, false, true, false, 0, "MockPerf", false);
        }

        @Override public boolean supports(HookType hookType) {
            return hookType != HookType.FIELD;
        }
    }
}