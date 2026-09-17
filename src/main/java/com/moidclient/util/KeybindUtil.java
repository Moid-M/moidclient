package com.moidclient.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vanilla KeyMapping bridge without compile-time game imports (this lives in
 * the common sourceset, so everything is reflection-based).
 * Reads the live bound key code from a KeyMapping instance. Writing is done
 * by callers via {@code InputConstants.Type.KEYSYM.getOrCreate(code)} plus
 * {@code KeyMapping.setKey(...)} (client sourceset only).
 */
public final class KeybindUtil {
    private KeybindUtil() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MoidClient/Keybind");
    private static boolean warned = false;

    /** -1 with a one-time warn (mapping breakage must not fail silently). */
    private static synchronized int fail(String msg) {
        if (!warned) {
            warned = true;
            LOGGER.warn("[MoidClient] KeybindUtil: {}", msg);
        }
        return -1;
    }

    /**
     * Returns the bound key code of a vanilla KeyMapping, or -1 if unreadable.
     * Raw codes are translated to dashboard (GLFW) numbering by callers via
     * NativeKeys — on 26.3 the live values are native SDL codes.
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
            if (keyField == null) return fail("KeyMapping.key field not found (mapping changed?)");
            Object key = keyField.get(keyMapping);
            if (key == null) return -1;
            Method getValue = key.getClass().getMethod("getValue");
            Object value = getValue.invoke(key);
            if (value instanceof Number number) return number.intValue();
            return -1;
        } catch (Exception e) {
            return fail("failed to read KeyMapping code: " + e);
        }
    }
}
