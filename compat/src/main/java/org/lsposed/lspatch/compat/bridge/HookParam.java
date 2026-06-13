package org.lsposed.lspatch.compat.bridge;

import android.util.Log;

import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;

/**
 * Hook 上下文 — 与旧版 XposedHelpers 的 XC_MethodHook.MethodHookParam 行为一致，
 * 内部委托给新的 HookProvider 系统。
 *
 * <h3>兼容性说明</h3>
 * 保留与 Xposed v82+ 相同的字段名和行为，仅在内部做桥接。
 * 标记 {@link Deprecated} 的字段/方法会在运行时记录迁移建议。
 */
@SuppressWarnings({"unused", "DeprecatedIsStillUsed"})
public class HookParam {

    private static final String TAG = "LSPatch-HookCompat";

    /** 被 Hook 的方法或构造器。 */
    public Member method;

    /** this 对象；对 static 方法为 null。 */
    public Object thisObject;

    /** 调用参数数组；对替换 Hook 可直接修改以影响原方法行为。 */
    public Object[] args;

    /** 方法返回值（仅 AFTER_Hook 回调有效）。 */
    public Object result;

    /** 方法抛出的异常（仅 AFTER_Hook 回调有效）。 */
    public Throwable throwable;

    /** 额外信息槽 — 模块可在 BEFORE / AFTER 之间传递状态。 */
    private Map<String, Object> extra;

    // ────────────── 状态管理 ──────────────

    /**
     * @deprecated 使用 {@link #setResult(Object)} 或 {@link #setThrowable(Throwable)} 替代。
     * 直接赋值此字段虽然仍可工作，但缺乏类型检查。
     */
    @Deprecated
    public void setResultDirect(Object value) {
        this.result = value;
        this.throwable = null;
        MigrationTracker.recordDeprecatedUsage("HookParam.result direct assignment",
                "Use setResult(Object) instead");
    }

    /** 设置返回值并清除之前设置的异常。 */
    public void setResult(Object value) {
        this.result = value;
        this.throwable = null;
    }

    /** 设置抛出的异常并清除之前设置的返回值。 */
    public void setThrowable(Throwable t) {
        this.throwable = t;
        this.result = null;
    }

    /** 是否已设置返回值或异常。 */
    public boolean hasResult() {
        return result != null || throwable != null;
    }

    /** 获取指定索引的参数，带自动类型转换。 */
    @SuppressWarnings("unchecked")
    public <T> T getArg(int index) {
        if (args == null || index < 0 || index >= args.length) {
            throw new IndexOutOfBoundsException("Invalid arg index " + index);
        }
        return (T) args[index];
    }

    /** 设置指定索引的参数（带类型检查警告）。 */
    public void setArg(int index, Object value) {
        if (args == null || index < 0 || index >= args.length) {
            throw new IndexOutOfBoundsException("Invalid arg index " + index);
        }
        args[index] = value;
    }

    /** 获取/设置额外信息（BEFORE → AFTER Hook 间传递状态）。 */
    public synchronized Object getExtra(String key) {
        return extra != null ? extra.get(key) : null;
    }

    public synchronized void setExtra(String key, Object value) {
        if (extra == null) extra = new HashMap<>();
        extra.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> T getExtra(String key, T defaultValue) {
        Object v = extra != null ? extra.get(key) : null;
        return v != null ? (T) v : defaultValue;
    }
}
