package org.lsposed.lspd.util;
import java.util.Enumeration;
import java.util.NoSuchElementException;

// 替代 sun.misc.CompoundEnumeration (与 Java 模块系统冲突)
public class CompoundEnumerationHelper<E> implements Enumeration<E> {
    private final Enumeration<E>[] enums;
    private int index;
    public CompoundEnumerationHelper(Enumeration<E>[] enums) { this.enums = enums; }
    private boolean next() {
        while (index < enums.length) { if (enums[index] != null && enums[index].hasMoreElements()) return true; index++; }
        return false;
    }
    public boolean hasMoreElements() { return next(); }
    public E nextElement() { if (!next()) throw new NoSuchElementException(); return enums[index].nextElement(); }
}
