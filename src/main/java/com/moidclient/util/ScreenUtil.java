package com.moidclient.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Version-proof "is a screen open" probe without compile-time game imports
 * (common sourceset, reflection only - same pattern as KeybindUtil).
 * 26.1 keeps the screen on a Minecraft field; 26.2+ moved it to Gui.
 */
public final class ScreenUtil {
    private ScreenUtil() {}

    private static volatile int mode = 0; // 0 = unknown, 1 = mc.screen, 2 = gui.screen()
    private static Field mcScreenField = null;
    private static Field guiField = null;
    private static Method guiScreenMethod = null;
    private static Field guiScreenField = null;

    /** True when any GUI screen is open (inventory, chat, menus, ...). */
    public static boolean isScreenOpen(Object mc) {
        try {
            if (mc == null) return false;
            if (mode == 0) detect(mc);
            if (mode == 1) return mcScreenField.get(mc) != null;
            if (mode == 2) {
                Object gui = guiField.get(mc);
                if (gui == null) return false;
                if (guiScreenMethod != null) return guiScreenMethod.invoke(gui) != null;
                if (guiScreenField != null) return guiScreenField.get(gui) != null;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static synchronized void detect(Object mc) throws Exception {
        if (mode != 0) return;
        // 26.1 style: public field Minecraft.screen
        try {
            mcScreenField = mc.getClass().getField("screen");
            mode = 1;
            return;
        } catch (NoSuchFieldException ignored) {}
        // 26.2 style: Gui.screen()
        Field gf;
        try {
            gf = mc.getClass().getField("gui");
        } catch (NoSuchFieldException e) {
            gf = mc.getClass().getDeclaredField("gui");
            gf.setAccessible(true);
        }
        guiField = gf;
        Object gui = guiField.get(mc);
        Class<?> guiClass = gui != null ? gui.getClass()
                : Class.forName("net.minecraft.client.gui.Gui");
        try {
            guiScreenMethod = guiClass.getMethod("screen");
        } catch (NoSuchMethodException e) {
            try {
                guiScreenField = guiClass.getField("screen");
            } catch (NoSuchFieldException e2) {
                guiScreenField = guiClass.getDeclaredField("screen");
                guiScreenField.setAccessible(true);
            }
        }
        mode = 2;
    }
}
