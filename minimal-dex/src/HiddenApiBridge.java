package hidden;
import java.lang.reflect.*;
public class HiddenApiBridge {
    public static int AssetManager_addAssetPath(Object am, String path) {
        try {
            Method m = android.content.res.AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            m.setAccessible(true);
            return (int) m.invoke(am, path);
        } catch (Throwable t) { return 0; }
    }
    public static android.os.IBinder Binder_allowBlocking(android.os.IBinder binder) {
        try {
            Method m = android.os.Binder.class.getDeclaredMethod("allowBlocking", android.os.IBinder.class);
            m.setAccessible(true);
            return (android.os.IBinder) m.invoke(null, binder);
        } catch (Throwable t) { return binder; }
    }
    public static void Resources_setImpl(android.content.res.Resources resources, Object impl) {
        try {
            Field f = android.content.res.Resources.class.getDeclaredField("mResourcesImpl");
            f.setAccessible(true);
            f.set(resources, impl);
        } catch (Throwable ignored) {}
    }
    public static ClassLoader ActivityThread_getTopLevelClassLoader() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method currentAT = at.getDeclaredMethod("currentActivityThread");
            currentAT.setAccessible(true);
            Object activityThread = currentAT.invoke(null);
            Field f = at.getDeclaredField("mTopLevelClassLoader");
            f.setAccessible(true);
            return (ClassLoader) f.get(activityThread);
        } catch (Throwable t) { return null; }
    }
}
