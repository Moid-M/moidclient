package com.moidclient.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Vanilla KeyMapping bridge without compile-time game imports (this lives in
 * the common sourceset, so everything is reflection-based).
 * Reads the live bound key code from a KeyMapping instance. Writing is done
 * by callers via {@code InputConstants.Type.KEYSYM.getOrCreate(code)} plus
 * {@code KeyMapping.setKey(...)} (client sourceset only).
 */
public final class KeybindUtil {
    private KeybindUtil() {}

    /**
     * Returns the bound key code of a vanilla KeyMapping, or -1 if unreadable.
     * For keyboard keys this matches GLFW codes on 26.1/26.2.
     */
    public static int readCode(Object keyMapping) {
        try {
            if (keyMapping == null) return -1;
            Field keyField = null;
            Class<?> c = keyMapping.getClass();
            while (c != null) {
                try {
                    keyField = c.getDeclaredField("key");
                    keyField.setAccessible(true);
                    break;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (keyField == null) return -1;
            Object key = keyField.get(keyMapping);
            if (key == null) return -1;
            Method getValue = key.getClass().getMethod("getValue");
            Object value = getValue.invoke(key);
            if (value instanceof Number number) return number.intValue();
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
