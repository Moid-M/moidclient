package com.moidclient.hud;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Version-proof F1 HUD-hidden check.
 * 26.1 keeps a boolean on options (options.hideGui), 26.2+ moved it to
 * gui.getHud().isHidden(). Resolved once via reflection so one source
 * compiles and runs on every supported version.
 */
public final class HudCompat {
    private static volatile int mode = 0; // 0 = unknown, 1 = 26.2+ style, 2 = 26.1 style
    private static Field guiField;
    private static Method getHudMethod;
    private static Method isHiddenMethod;
    private static Field optionsField;
    private static Field hideGuiField;

    private HudCompat() {}

    public static boolean isHudHidden(Minecraft mc) {
        try {
            // Cinematic zoom hides every overlay (checked first: cheapest gate).
            try {
                if (com.moidclient.utility.zoom.ZoomManager.isCinematicActive()) return true;
            } catch (Exception ignored) {}
            if (mc == null) return false;
            if (mode == 0) detect(mc);
            if (mode == 1) {
                Object gui = guiField.get(mc);
                if (gui == null) return false;
                Object hud = getHudMethod.invoke(gui);
                if (hud == null) return false;
                return (Boolean) isHiddenMethod.invoke(hud);
            } else if (mode == 2) {
                Object options = optionsField.get(mc);
                if (options == null) return false;
                return hideGuiField.getBoolean(options);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static synchronized void detect(Minecraft mc) throws Exception {
        if (mode != 0) return;
        try {
            guiField = field(Minecraft.class, "gui");
            Object gui = guiField.get(mc);
            Class<?> guiClass = gui != null ? gui.getClass() : Class.forName("net.minecraft.client.gui.Gui");
            getHudMethod = method(guiClass, "getHud");
            Object hud = gui != null ? getHudMethod.invoke(gui) : null;
            Class<?> hudClass = hud != null ? hud.getClass() : Class.forName("net.minecraft.client.gui.Hud");
            isHiddenMethod = method(hudClass, "isHidden");
            mode = 1;
        } catch (Exception e) {
            optionsField = field(Minecraft.class, "options");
            Object options = optionsField.get(mc);
            Class<?> optClass = options != null ? options.getClass() : Class.forName("net.minecraft.client.Options");
            hideGuiField = field(optClass, "hideGui");
            mode = 2;
        }
    }

    private static Field field(Class<?> c, String name) throws Exception {
        try {
            return c.getField(name);
        } catch (NoSuchFieldException e) {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }
    }

    private static Method method(Class<?> c, String name) throws Exception {
        try {
            return c.getMethod(name);
        } catch (NoSuchMethodException e) {
            Method m = c.getDeclaredMethod(name);
            m.setAccessible(true);
            return m;
        }
    }
}
