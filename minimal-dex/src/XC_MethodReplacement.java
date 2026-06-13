package de.robv.android.xposed;
import java.lang.reflect.Member;
public abstract class XC_MethodReplacement extends XCallback {
    public XC_MethodReplacement() {}
    public XC_MethodReplacement(int priority) { super(priority); }
    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable { return null; }
    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        public Object result;
        public Throwable throwable;
        public boolean returnEarly = false;
    }
}
