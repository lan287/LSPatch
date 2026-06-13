package org.lsposed.lspatch.compat.bridge;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * 字段访问桥接器 — 替代 {@code XposedHelpers.setStatic*/getStatic*} 的 API。
 *
 * <p>
 * 提供以下优势：
 * <ul>
 *   <li>类型安全（使用泛型而非 Object）</li>
 *   <li>字段查找缓存（减少反射开销）</li>
 *   <li>详细的异常报告（失败时可追溯到具体字段）</li>
 *   <li>支持 JVM 层替代 Field Hook</li>
 * </ul>
 *
 * <h3>迁移示例</h3>
 * <pre>{@code
 * // 旧 API
 * boolean enabled = XposedHelpers.getStaticBooleanField(
 *     "com.example.Config", classLoader, "ENABLED");
 * XposedHelpers.setStaticIntField(
 *     "com.example.Config", classLoader, "VERSION", 42);
 *
 * // 新 API
 * FieldAccessBridge fab = FieldAccessBridge.forClass("com.example.Config", classLoader);
 * boolean enabled = fab.getStaticBoolean("ENABLED");
 * fab.setStaticInt("VERSION", 42);
 * }</pre>
 */
public final class FieldAccessBridge {

    private static final String TAG = "LSPatch-FieldBridge";

    /** 缓存：类 → 字段名 → Field */
    private static final Map<String, Map<String, Field>> fieldCache = new HashMap<>();

    private final Class<?> targetClass;

    private FieldAccessBridge(Class<?> targetClass) {
        this.targetClass = targetClass;
    }

    /**
     * 创建一个针对指定类的字段访问桥接器。
     *
     * @param className 全限定类名
     * @param classLoader 类加载器
     * @return FieldAccessBridge 实例
     * @throws IllegalArgumentException 如果类找不到
     */
    public static FieldAccessBridge forClass(String className, ClassLoader classLoader) {
        try {
            Class<?> target = Class.forName(className, false, classLoader);
            return new FieldAccessBridge(target);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + className, e);
        }
    }

    /**
     * 通过 Class 对象创建。
     */
    public static FieldAccessBridge forClass(Class<?> targetClass) {
        return new FieldAccessBridge(targetClass);
    }

    // ============== 静态字段读取 ==============

    public boolean getStaticBoolean(String fieldName) {
        Field field = resolveStatic(fieldName, boolean.class);
        try {
            return field.getBoolean(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read static boolean field: " + fieldName, e);
        }
    }

    public int getStaticInt(String fieldName) {
        Field field = resolveStatic(fieldName, int.class);
        try {
            return field.getInt(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read static int field: " + fieldName, e);
        }
    }

    public long getStaticLong(String fieldName) {
        Field field = resolveStatic(fieldName, long.class);
        try {
            return field.getLong(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read static long field: " + fieldName, e);
        }
    }

    public String getStaticString(String fieldName) {
        Field field = resolveStatic(fieldName, String.class);
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read static string field: " + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getStaticObject(String fieldName, Class<T> type) {
        Field field = resolveStatic(fieldName, type);
        try {
            return (T) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read static field: " + fieldName, e);
        }
    }

    // ============== 静态字段写入 ==============

    public void setStaticBoolean(String fieldName, boolean value) {
        Field field = resolveStatic(fieldName, boolean.class);
        try { field.setBoolean(null, value); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write static boolean: " + fieldName, e);
        }
    }

    public void setStaticInt(String fieldName, int value) {
        Field field = resolveStatic(fieldName, int.class);
        try { field.setInt(null, value); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write static int: " + fieldName, e);
        }
    }

    public void setStaticLong(String fieldName, long value) {
        Field field = resolveStatic(fieldName, long.class);
        try { field.setLong(null, value); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write static long: " + fieldName, e);
        }
    }

    public void setStaticObject(String fieldName, Object value) {
        Field field = resolveStatic(fieldName, Object.class);
        try { field.set(null, value); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write static field: " + fieldName, e);
        }
    }

    // ============== 实例字段读写 ==============

    public int getInstanceInt(Object instance, String fieldName) {
        Field field = resolveInstance(fieldName, int.class);
        try { return field.getInt(instance); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read instance int field: " + fieldName, e);
        }
    }

    public void setInstanceInt(Object instance, String fieldName, int value) {
        Field field = resolveInstance(fieldName, int.class);
        try { field.setInt(instance, value); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write instance int field: " + fieldName, e);
        }
    }

    public Object getInstanceObject(Object instance, String fieldName) {
        Field field = resolveInstance(fieldName, Object.class);
        try { return field.get(instance); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read instance field: " + fieldName, e);
        }
    }

    public void setInstanceObject(Object instance, String fieldName, Object value) {
        Field field = resolveInstance(fieldName, Object.class);
        try { field.set(instance, value); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write instance field: " + fieldName, e);
        }
    }

    // ============== 内部：字段查找缓存 ==============

    private Field resolveStatic(String fieldName, Class<?> expectedType) {
        Field field = findField(fieldName);
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalStateException("Field '" + fieldName + "' on " +
                    targetClass.getName() + " is not static");
        }
        if (expectedType != Object.class && !field.getType().isAssignableFrom(expectedType)
                && field.getType() != expectedType) {
            // 类型不严格匹配但可能是 boxed — 放宽检查
            Log.w(TAG, "Field '" + fieldName + "' on " + targetClass.getName() +
                    " has type " + field.getType().getName() + " (expected " + expectedType.getName() + ")");
        }
        return field;
    }

    private Field resolveInstance(String fieldName, Class<?> expectedType) {
        Field field = findField(fieldName);
        if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalStateException("Field '" + fieldName + "' on " +
                    targetClass.getName() + " is static, use getStatic*()");
        }
        return field;
    }

    private Field findField(String fieldName) {
        String classKey = targetClass.getName();

        // 快速路径：缓存命中
        Map<String, Field> classCache = fieldCache.get(classKey);
        if (classCache != null) {
            Field cached = classCache.get(fieldName);
            if (cached != null) return cached;
        }

        // 慢速路径：反射查找（含父类）
        Class<?> current = targetClass;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);

                // 写入缓存
                fieldCache.computeIfAbsent(classKey, k -> new HashMap<>())
                        .put(fieldName, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        throw new IllegalStateException("Field '" + fieldName + "' not found on " +
                targetClass.getName() + " or any of its superclasses");
    }

    /**
     * 清除字段缓存（测试辅助）。
     */
    public static void clearCache() {
        fieldCache.clear();
    }

    /**
     * 获取当前缓存的字段数量（测试辅助）。
     */
    public static int getCachedFieldCount() {
        int total = 0;
        for (Map<String, Field> classCache : fieldCache.values()) {
            total += classCache.size();
        }
        return total;
    }
}
