package io.github.libxposed.api.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface XposedHooker { boolean value() default true; }
