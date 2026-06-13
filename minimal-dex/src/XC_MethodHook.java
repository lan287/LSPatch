package de.robv.android.xposed;
import java.lang.reflect.Member;
public abstract class XC_MethodHook extends XCallback {
    public XC_MethodHook() {}
    public XC_MethodHook(int priority) { super(priority); }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        public Object result;
        public Throwable throwable;
        public boolean returnEarly = false;
    }
}
