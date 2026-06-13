package de.robv.android.xposed;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
public class XposedHelpers {
    private static final Map<String, Field> fieldCache = new HashMap<>();
    private static final Map<String, Method> methodCache = new HashMap<>();
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try { return Class.forName(className, false, classLoader); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
    public static Field findField(Class<?> clazz, String fieldName) {
        String fullFieldName = clazz.getName() + '#' + fieldName;
        Field cached = fieldCache.get(fullFieldName);
        if (cached != null) return cached;
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            fieldCache.put(fullFieldName, field);
            return field;
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), fieldName);
            throw new RuntimeException(e);
        }
    }
    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        String fullMethodName = clazz.getName() + '#' + methodName + "#" + parameterTypes.length;
        Method cached = methodCache.get(fullMethodName);
        if (cached != null) return cached;
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            methodCache.put(fullMethodName, method);
            return method;
        } catch (NoSuchMethodException e) {
            if (clazz.getSuperclass() != null) return findMethodBestMatch(clazz.getSuperclass(), methodName, parameterTypes);
            throw new RuntimeException(e);
        }
    }
    public static Object getObjectField(Object obj, String fieldName) {
        try { return findField(obj.getClass(), fieldName).get(obj); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
    public static void setObjectField(Object obj, String fieldName, Object value) {
        try { findField(obj.getClass(), fieldName).set(obj, value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try { return findField(clazz, fieldName).get(null); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try { findField(clazz, fieldName).set(null, value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
    public static Object callMethod(Object obj, String methodName, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = args[i] != null ? args[i].getClass() : Object.class;
            return findMethodBestMatch(obj.getClass(), methodName, types).invoke(obj, args);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = args[i] != null ? args[i].getClass() : Object.class;
            return findMethodBestMatch(clazz, methodName, types).invoke(null, args);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }
}
