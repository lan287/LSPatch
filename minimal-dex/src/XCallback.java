package de.robv.android.xposed;
public class XCallback {
    public static final int PRIORITY_DEFAULT = 50;
    public static final int PRIORITY_HIGHEST = 10000;
    public static final int PRIORITY_LOWEST = -10000;
    public final int priority;
    public XCallback() { this.priority = PRIORITY_DEFAULT; }
    public XCallback(int priority) { this.priority = priority; }
    public static final class Param { public Object callback; }
}
