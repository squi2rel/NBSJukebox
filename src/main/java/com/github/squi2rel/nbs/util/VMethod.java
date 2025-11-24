package com.github.squi2rel.nbs.util;

import java.lang.invoke.MethodHandle;

public record VMethod<R>(MethodHandle handle) {

    @SuppressWarnings("unchecked")
    public R invoke(Object... args) {
        try {
            return (R) handle.invokeWithArguments(args);
        } catch (Throwable t) {
            if (t instanceof Error) throw (Error) t;
            throw new RuntimeException(t);
        }
    }
}