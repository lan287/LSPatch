package org.lsposed.hiddenapibypass;

import java.lang.reflect.Method;

public class HiddenApiBypass {
    private HiddenApiBypass() {}

    public static Object invoke(Class<?> clazz, Object obj, String methodName, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Long) types[i] = long.class;
            else if (args[i] instanceof Integer) types[i] = int.class;
            else if (args[i] instanceof Boolean) types[i] = boolean.class;
            else if (args[i] instanceof Float) types[i] = float.class;
            else if (args[i] instanceof Double) types[i] = double.class;
            else if (args[i] instanceof Byte) types[i] = byte.class;
            else if (args[i] instanceof Short) types[i] = short.class;
            else if (args[i] instanceof Character) types[i] = char.class;
            else if (args[i] != null) types[i] = args[i].getClass();
        }
        Method m = null;
        try {
            m = clazz.getDeclaredMethod(methodName, types);
        } catch (NoSuchMethodException e) {
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterTypes().length == args.length) {
                    m = method;
                    break;
                }
            }
            if (m == null) throw e;
        }
        m.setAccessible(true);
        return m.invoke(obj, args);
    }
}
