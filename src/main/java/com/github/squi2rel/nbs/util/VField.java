package com.github.squi2rel.nbs.util;

import java.lang.invoke.VarHandle;

@SuppressWarnings("unchecked")
public record VField<T, F>(VarHandle varHandle) {

    public void set(T obj, F value) {
        varHandle.set(obj, value);
    }

    public F get(T obj) {
        return (F) varHandle.get(obj);
    }
}
