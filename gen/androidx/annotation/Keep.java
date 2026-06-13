package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target({ElementType.METHOD,ElementType.FIELD,ElementType.TYPE,ElementType.CONSTRUCTOR})
public @interface Keep {}
