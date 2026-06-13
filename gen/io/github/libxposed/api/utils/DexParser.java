package io.github.libxposed.api.utils;
import android.annotation.NonNull;
import android.annotation.Nullable;
import java.nio.ByteBuffer;
public interface DexParser {
    interface Id<Self> { int getId(); int getIndex(); @NonNull String getDescriptor(); @NonNull String getName(); @Nullable String getSignature(); }
    interface StringId extends Id<StringId> {}
    interface TypeId extends Id<TypeId> {}
    interface ProtoId extends Id<ProtoId> {}
    interface FieldId extends Id<FieldId> {}
    interface MethodId extends Id<MethodId> {}
    interface Annotation {}
    interface Array {}
    interface Value {}
    interface Class {}
    interface AnnotationVisitor {}
    interface FieldVisitor {}
    interface MethodVisitor {}
    interface ClassVisitor {}
}
