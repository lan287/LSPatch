package org.lsposed.lspd.nativebridge;
public class NativeAPI {
    public static native boolean forkServer(String prefix);
    public static native String getInstalledModulesPath();
    public static native String getEntryPoint(String packageName, String processName, boolean isDefaultProcess);
}
