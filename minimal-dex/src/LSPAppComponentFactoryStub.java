package org.lsposed.lspatch.metaloader;
import android.app.AppComponentFactory;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;
import dalvik.system.InMemoryDexClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class LSPAppComponentFactoryStub extends AppComponentFactory {
    private static final String TAG = "LSPatch-MetaLoader";
    private static final Map<String, String> archToLib = new HashMap<String, String>(4);
    static {
        archToLib.put("arm", "armeabi-v7a");
        archToLib.put("arm64", "arm64-v8a");
        archToLib.put("x86", "x86");
        archToLib.put("x86_64", "x86_64");
        try {
            ClassLoader cl = LSPAppComponentFactoryStub.class.getClassLoader();
            String arch = getArch();
            String libName = archToLib.get(arch);
            if (libName == null) libName = "arm64-v8a";
            
            // 加载 loader.dex
            byte[] dex = null;
            try (InputStream is = cl.getResourceAsStream("assets/lspatch/loader.dex");
                 ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                if (is != null) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = is.read(buffer)) != -1) os.write(buffer, 0, n);
                    dex = os.toByteArray();
                    Log.i(TAG, "Loaded loader.dex: " + dex.length + " bytes");
                } else Log.w(TAG, "loader.dex not found");
            }
            
            // 加载原生库
            try {
                System.loadLibrary("lspatch");
                Log.i(TAG, "Loaded liblspatch.so");
            } catch (Throwable t) { Log.e(TAG, "Failed to load native lib", t); }
            
            // 调用 loader
            if (dex != null && dex.length > 0) {
                ByteBuffer[] buffers = new ByteBuffer[] { ByteBuffer.wrap(dex) };
                ClassLoader loaderCL = new InMemoryDexClassLoader(buffers, cl);
                try {
                    Class<?> appClass = Class.forName("org.lsposed.lspatch.loader.LSPApplication", true, loaderCL);
                    Method onLoad = appClass.getDeclaredMethod("onLoad", ClassLoader.class, Object[].class);
                    onLoad.invoke(null, loaderCL, new Object[] {});
                    Log.i(TAG, "LSPApplication.onLoad called");
                } catch (Throwable t) { Log.e(TAG, "Error calling LSPApplication.onLoad", t); }
            }
        } catch (Throwable t) { Log.e(TAG, "Error in meta-loader init", t); }
    }
    
    private static String getArch() {
        try {
            Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object runtime = getRuntime.invoke(null);
            Method vmInstr = vmRuntime.getDeclaredMethod("vmInstructionSet");
            vmInstr.setAccessible(true);
            return (String) vmInstr.invoke(runtime);
        } catch (Throwable t) { return "arm64"; }
    }
}
