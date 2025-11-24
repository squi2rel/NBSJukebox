package com.github.squi2rel.nbs.util;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class ReflectUtil {

    public static <T> void set(T object, Class<?> fieldType, Object value) {
        try {
            Field field = getField(object.getClass(), fieldType);
            if (field == null) throw new NoSuchFieldError("No such field with type " + fieldType);
            field.setAccessible(true);
            field.set(object, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T, F> VField<T, F> getHandle(Class<T> clazz, Class<F> fieldType) {
        try {
            Field field = getField(clazz, fieldType);
            if (field == null) throw new NoSuchFieldError("No such field with type " + fieldType);
            return new VField<>(MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).findVarHandle(clazz, field.getName(), fieldType));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Field getField(Class<?> clazz, Class<?> type) {
        Class<?> now = clazz;
        while (now != null) {
            for (Field field : now.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() == type) return field;
            }
            now = now.getSuperclass();
        }
        return null;
    }

    public static <T, R> VMethod<R> getHandleStatic(Class<T> clazz, Class<R> returnType, Class<?>... paramTypes) {
        return getMethod(clazz, returnType, paramTypes, true);
    }

    private static <R> @NotNull VMethod<R> getMethod(Class<?> clazz, Class<R> returnType, Class<?>[] paramTypes, boolean isStatic) {
        try {
            Method method = null;
            for (Method m : clazz.getDeclaredMethods()) {
                if (!isStatic && Modifier.isStatic(m.getModifiers())) continue;
                if (m.getReturnType().equals(returnType) && Arrays.equals(m.getParameterTypes(), paramTypes)) {
                    method = m;
                    break;
                }
            }

            if (method == null) {
                throw new NoSuchMethodException(
                        String.format("No instance method found in %s with signature: %s(%s)",
                                clazz.getName(), returnType.getSimpleName(), Arrays.toString(paramTypes))
                );
            }
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
            MethodType methodType = MethodType.methodType(returnType, paramTypes);
            MethodHandle handle;
            if (isStatic) {
                handle = lookup.findStatic(clazz, method.getName(), methodType);
            } else {
                handle = lookup.findVirtual(clazz, method.getName(), methodType);
            }
            return new VMethod<>(handle);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
