package org.lsposed.lspatch.compat.bridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 废弃 API 追踪器 — 记录所有通过 {@link XposedBridgeCompat} 调用的
 * 已废弃 Xposed API 的使用情况。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>模块开发者在调试模式下检测，以了解迁移优先级</li>
 *   <li>LSPatch Manager 可展示"使用了 XX 个废弃 API"的统计</li>
 *   <li>用于 CI 测试，统计随版本迭代下降的废弃 API 使用量</li>
 * </ul>
 */
public final class MigrationTracker {

    private static final Map<String, AtomicInteger> usage = new ConcurrentHashMap<>();
    private static final Map<String, String> recommendations = new ConcurrentHashMap<>();
    private static volatile boolean enabled = true;
    private static volatile boolean verbose = true;

    private MigrationTracker() { /* 禁止实例化 */ }

    /**
     * 记录一次废弃 API 使用。
     */
    public static void record(String oldApi, String recommendation) {
        if (!enabled) return;
        usage.computeIfAbsent(oldApi, k -> new AtomicInteger(0)).incrementAndGet();
        if (recommendation != null && !recommendations.containsKey(oldApi)) {
            recommendations.put(oldApi, recommendation);
        }
    }

    /**
     * 获取每个废弃 API 的使用次数。
     */
    public static Map<String, Integer> getStats() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : usage.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }

    /**
     * 获取每个废弃 API 对应的迁移建议。
     */
    public static Map<String, String> getRecommendations() {
        return recommendations;
    }

    /**
     * 获取总调用次数（用于模块自检："该模块使用了 N 个废弃 API"）。
     */
    public static int getTotalDeprecatedCalls() {
        int total = 0;
        for (AtomicInteger count : usage.values()) {
            total += count.get();
        }
        return total;
    }

    /**
     * 清除统计（测试辅助）。
     */
    public static void reset() {
        usage.clear();
        recommendations.clear();
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isVerbose() { return verbose; }
    public static void setVerbose(boolean v) { verbose = v; }

    /**
     * 生成用户可读的迁移报告。
     */
    public static String generateReport(String moduleName) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LSPatch 2.0 模块迁移报告 ===\n");
        sb.append("模块: ").append(moduleName).append("\n");
        sb.append("废弃 API 总调用: ").append(getTotalDeprecatedCalls()).append("\n\n");
        sb.append("---- 详细 ----\n");
        int i = 1;
        for (Map.Entry<String, AtomicInteger> entry : usage.entrySet()) {
            sb.append("  ").append(i++).append(". ").append(entry.getKey())
                    .append(" (").append(entry.getValue().get()).append("次)\n");
            String rec = recommendations.get(entry.getKey());
            if (rec != null) {
                sb.append("     → 建议: ").append(rec).append("\n");
            }
        }
        sb.append("============= 结束 =============");
        return sb.toString();
    }
}
