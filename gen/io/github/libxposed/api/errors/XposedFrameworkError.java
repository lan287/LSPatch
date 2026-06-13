package io.github.libxposed.api.errors;
public class XposedFrameworkError extends Error {
    public XposedFrameworkError() { super(); }
    public XposedFrameworkError(String message) { super(message); }
    public XposedFrameworkError(String message, Throwable cause) { super(message, cause); }
}
