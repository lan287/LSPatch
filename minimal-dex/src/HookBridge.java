package org.lsposed.lspd.nativebridge;
import java.lang.reflect.Member;
public class HookBridge {
    public static native boolean hookMethod(Member method, Object callback, Object additionalInfo);
    public static native boolean unhookMethod(Member method, Object callback);
    public static native Object invokeOriginalMethod(Member method, Object thisObject, Object[] args);
    public static native void deoptimizeMethod(Member method);
}
