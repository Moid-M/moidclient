package com.moidclient.utility.freelook;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.utility.keybind.Keybinds;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

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
                    ModuleOption.keybind("freelookKey", "Key", com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_LEFT_ALT),
                    ModuleOption.slider("freelookSensitivity", "Look sensitivity", 0.25, 3.0, 0.05)
                ));
    }

    // Tick thread writes, render thread (CameraMixin/EntityMixin) reads.
    private static volatile boolean active = false;
    private static volatile CameraType prevCamera = null;
    private static volatile float yaw = 0;
    private static volatile float pitch = 0;
    private static volatile float sensitivity = 1.0f;

    // Current-screen accessor. 26.1 keeps a public field on Minecraft
    // (mc.screen); 26.2 moved it to Gui.screen(). Resolved once.
    private static int screenMode = 0; // 0 = unknown, 1 = mc.screen, 2 = gui.screen()
    private static java.lang.reflect.Field mcScreenField = null;
    private static java.lang.reflect.Field guiField = null;
    private static java.lang.reflect.Method guiScreenMethod = null;
    private static java.lang.reflect.Field guiScreenField = null;

    public static void onTick(Minecraft mc, ConfigManager config, KeyMapping freeLookKey) {
        try {
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("freelook") : null;
            if (mod == null || !mod.enabled) {
                Keybinds.reset(freeLookKey);
                disengage(mc);
                return;
            }
            boolean toggleMode = "toggle".equals(mod.freelookMode);
            // Dashboard is the remote: push its binding into the vanilla
            // mapping (visible + rebindable in Controls, persisted by vanilla).
            Keybinds.syncBinding(mc, freeLookKey, mod.freelookKey);
            boolean shouldBe = false;
            if (mc != null && mc.player != null && currentScreen(mc) == null) {
                shouldBe = Keybinds.isTriggered(freeLookKey, toggleMode);
                double s = mod.freelookSensitivity;
                if (Double.isNaN(s) || s <= 0) s = 1.0;
                sensitivity = (float) Math.max(0.25, Math.min(3.0, s));
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

    /** Accumulates a mouse delta into the detached camera (vanilla 0.15 factor). */
    public static void onTurn(double x, double y) {
        try {
            yaw += (float) (x * 0.15F * sensitivity);
            pitch = Mth.clamp(pitch + (float) (y * 0.15F * sensitivity), -90.0F, 90.0F);
        } catch (Exception ignored) {}
    }

    private static volatile float savedYaw = 0;
    private static volatile float savedPitch = 0;
    private static volatile float savedOldYaw = 0;
    private static volatile float savedOldPitch = 0;
    private static volatile boolean swapped = false;

    /**
     * Called at the head of Camera.update: lends the FreeLook angles to the
     * player so vanilla positions/rotates the camera (and its culling
     * frustum) around the player. Both current AND last-tick rotation are
     * swapped: vanilla interpolates between them, and leaving the old one at
     * the real rotation would make the camera trail and jitter. Restored by
     * {@link #swapOut()}.
     */
    public static void swapIn() {
        if (!active || swapped) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            savedYaw = mc.player.getYRot();
            savedPitch = mc.player.getXRot();
            savedOldYaw = mc.player.yRotO;
            savedOldPitch = mc.player.xRotO;
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            swapped = true;
        } catch (Exception ignored) {}
    }

    /** Called at the tail of Camera.update: gives the player its rotation back. */
    public static void swapOut() {
        if (!swapped) return;
        swapped = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                mc.player.setYRot(savedYaw);
                mc.player.setXRot(savedPitch);
                mc.player.yRotO = savedOldYaw;
                mc.player.xRotO = savedOldPitch;
            }
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
