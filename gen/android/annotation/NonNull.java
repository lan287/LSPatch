package android.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.SOURCE) @Target({ElementType.METHOD,ElementType.PARAMETER,ElementType.FIELD,ElementType.ANNOTATION_TYPE,ElementType.LOCAL_VARIABLE})
public @interface NonNull {}
