package io.github.libxposed.api.errors;
public class HookFailedError extends Error {
    public HookFailedError() { super(); }
    public HookFailedError(String message) { super(message); }
    public HookFailedError(String message, Throwable cause) { super(message, cause); }
}
