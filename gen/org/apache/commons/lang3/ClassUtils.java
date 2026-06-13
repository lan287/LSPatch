package org.apache.commons.lang3;
import java.util.*;
public class ClassUtils {
    public static String getPackageName(String className) {
        if (className == null || className.isEmpty()) return "";
        int i = className.lastIndexOf('.');
        return i < 0 ? "" : className.substring(0, i);
    }
    public static List<String> getAllInterfaces(List<Class<?>> classes) {
        return new ArrayList<>();
    }
    public static List<String> getAllSuperclasses(Class<?> cls) {
        return new ArrayList<>();
    }
}
