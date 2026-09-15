package com.moidclient.utility.freelook;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * FreeLook - hold-key 360-degree camera. Mouse deltas are rerouted into a
 * detached camera (see EntityMixin/CameraMixin) while held, so the player
 * entity never turns and movement direction is preserved. On release the
 * camera snaps back behind the player automatically.
 * Category: Utility
 */
public final class FreeLookManager {
    private FreeLookManager() {}

    public static ModuleDef definition() {
        return new ModuleDef("freelook", "FreeLook",
                "Detached 360° camera (Left Alt by default, hold or toggle).",
                "utility", false, "eye", false,
                ModuleOption.list(
                    ModuleOption.select("freelookMode", "Activation", java.util.List.of("hold", "toggle")),
                    ModuleOption.keybind("freelookKey", "Key", GLFW.GLFW_KEY_LEFT_ALT),
                    ModuleOption.slider("freelookSensitivity", "Look sensitivity", 0.25, 3.0, 0.05)
                ));
    }

    private static boolean active = false;
    private static boolean toggled = false;
    private static boolean wasDown = false;
    private static CameraType prevCamera = null;
    private static float yaw = 0;
    private static float pitch = 0;
    private static float sensitivity = 1.0f;

    // Current-screen accessor. 26.1 keeps a public field on Minecraft
    // (mc.screen); 26.2 moved it to Gui.screen(). Resolved once.
    private static int screenMode = 0; // 0 = unknown, 1 = mc.screen, 2 = gui.screen()
    private static java.lang.reflect.Field mcScreenField = null;
    private static java.lang.reflect.Field guiField = null;
    private static java.lang.reflect.Method guiScreenMethod = null;
    private static java.lang.reflect.Field guiScreenField = null;

    public static void onTick(Minecraft mc, ConfigManager config) {
        try {
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("freelook") : null;
            if (mod == null || !mod.enabled) {
                toggled = false;
                disengage(mc);
                return;
            }
            boolean toggleMode = "toggle".equals(mod.freelookMode);
            boolean down = isKeyDown(mc, mod.freelookKey);
            if (toggleMode && down && !wasDown) toggled = !toggled;
            wasDown = down;
            boolean shouldBe = false;
            if (mc != null && mc.player != null && currentScreen(mc) == null) {
                shouldBe = toggleMode ? toggled : down;
                sensitivity = mod.freelookSensitivity <= 0 ? 1.0f : (float) mod.freelookSensitivity;
            }
            if (shouldBe && !active) {
                // engage: start from the current view direction (no jump) and
                // drop into third-person-back so you see your character
                yaw = mc.player.getYRot();
                pitch = mc.player.getXRot();
                try {
                    prevCamera = mc.options.getCameraType();
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                } catch (Exception ignored) {}
                active = true;
            } else if (!shouldBe && active) {
                disengage(mc);
            }
        } catch (Exception ignored) {}
    }

    /** Leaves freelook and restores the previous perspective. */
    private static void disengage(Minecraft mc) {
        if (!active && prevCamera == null) return;
        active = false;
        try {
            if (mc != null && mc.options != null && prevCamera != null) {
                mc.options.setCameraType(prevCamera);
            }
        } catch (Exception ignored) {}
        prevCamera = null;
    }

    /** Raw GLFW hold-state for a dashboard-bound key code (0 = unbound). */
    private static boolean isKeyDown(Minecraft mc, int code) {
        try {
            if (mc == null || mc.getWindow() == null || code <= 0) return false;
            return GLFW.glfwGetKey(mc.getWindow().handle(), code) == GLFW.GLFW_PRESS;
        } catch (Exception e) {
            return false;
        }
    }

    /** Accumulates a mouse delta into the detached camera (vanilla 0.15 factor). */
    public static void onTurn(double x, double y) {
        try {
            yaw += (float) (x * 0.15F * sensitivity);
            pitch = Mth.clamp(pitch + (float) (y * 0.15F * sensitivity), -90.0F, 90.0F);
        } catch (Exception ignored) {}
    }

    public static boolean isActive() {
        return active;
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    /** Returns the currently open screen, or null. Version-proof via reflection. */
    private static Object currentScreen(Minecraft mc) {
        try {
            if (screenMode == 0) detectScreen(mc);
            if (screenMode == 1) {
                return mcScreenField.get(mc);
            } else if (screenMode == 2) {
                Object gui = guiField.get(mc);
                if (gui == null) return null;
                if (guiScreenMethod != null) return guiScreenMethod.invoke(gui);
                if (guiScreenField != null) return guiScreenField.get(gui);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static synchronized void detectScreen(Minecraft mc) throws Exception {
        if (screenMode != 0) return;
        // 26.1 style: public field Minecraft.screen
        try {
            mcScreenField = mc.getClass().getField("screen");
            screenMode = 1;
            return;
        } catch (NoSuchFieldException ignored) {}
        // 26.2 style: Gui.screen()
        java.lang.reflect.Field gf;
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
        screenMode = 2;
    }
}
