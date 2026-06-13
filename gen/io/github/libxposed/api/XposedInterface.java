package io.github.libxposed.api;
import java.lang.reflect.Executable;
public interface XposedInterface {
    interface BeforeHookCallback { Executable getMember(); Object getThisObject(); Object[] getArgs(); }
    interface AfterHookCallback { Executable getMember(); Object getThisObject(); Object[] getArgs(); Object getResult(); Throwable getThrowable(); boolean getThrowableOrNull(); }
    interface Hooker { void before(BeforeHookCallback param) throws Throwable; void after(AfterHookCallback param) throws Throwable; }
    interface MethodUnhooker<T extends Executable> { T getMember(); void unhook(); }
}
