package com.moidclient.utility.autohide;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.util.ScreenUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Autohide HUD - slides/shrinks the vanilla bottom cluster (hotbar, hearts,
 * hunger, armor, air) away after input goes idle, brings it back on any
 * activity. Pose-only animation: the 26.x submit pipeline bakes colors into
 * every draw call with no global alpha, so fading is not feasible without
 * per-draw surgery - slide/shrink/grow read just as well.
 * Close/open animations are configured independently. Category: HUD
 */
public final class AutohideManager {
    private AutohideManager() {}

    /** Pixels the cluster travels when fully hidden (clears it on any scale). */
    private static final float SLIDE_PX = 64.0f;
    /** Hide/show sweep duration at speed 1. */
    private static final long ANIM_MS = 250;

    private static boolean targetShown = true;
    private static long transitionStartMs = 0;
    private static float fromAmount = 0f;
    private static long lastActivityMs = 0;
    private static int lastSelectedSlot = -1;
    private static long animMs = ANIM_MS;

    public static ModuleDef definition() {
        return new ModuleDef("autohideHud", "Autohide HUD",
                "Hides the hotbar cluster when idle.",
                "hud", false, "activity", false,
                ModuleOption.list(
                    ModuleOption.slider("autohideTimeout", "Idle timeout (s)", 1, 10, 0.5, 3),
                    ModuleOption.slider("autohideSpeed", "Animation speed", 0.5, 3, 0.25, 1),
                    ModuleOption.bool("autohideIgnoreMovement", "Only hotbar use wakes", false),
                    ModuleOption.select("autohideClose", "Hide animation",
                            java.util.List.of("slide", "shrink", "instant"), "slide"),
                    ModuleOption.select("autohideOpen", "Show animation",
                            java.util.List.of("slide", "grow", "pop", "instant"), "slide")
                ));
    }

    public static void onTick(Minecraft mc, ConfigManager config) {
        try {
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("autohideHud") : null;
            if (mc == null || mod == null || !mod.enabled || mc.player == null) {
                noteShown();
                return;
            }
            boolean ignoreMovement = false;
            double speed = 1.0;
            try {
                ignoreMovement = mod.optBool("autohideIgnoreMovement", false);
                speed = mod.optDouble("autohideSpeed", 1.0);
            } catch (Exception ignored) {}
            if (speed <= 0) speed = 1.0;
            animMs = (long) (ANIM_MS / Math.max(0.1, Math.min(8.0, speed)));
            // With ignore-movement only hotbar use (slot changes) and open
            // screens wake the HUD; walking and clicking do not.
            boolean active = ScreenUtil.isScreenOpen(mc);
            if (!ignoreMovement) {
                active = active
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_W)
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_A)
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_S)
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_D)
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_SPACE)
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_LEFT_SHIFT)
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_MOUSE_LEFT)
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_MOUSE_RIGHT)
                        || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_MOUSE_MIDDLE);
            }
            try {
                int selected = mc.player.getInventory().getSelectedSlot();
                if (lastSelectedSlot < 0) lastSelectedSlot = selected;
                if (selected != lastSelectedSlot) {
                    lastSelectedSlot = selected;
                    active = true;
                }
            } catch (Exception ignored) {}
            long now = System.currentTimeMillis();
            if (active) lastActivityMs = now;
            double timeout = 3.0;
            try {
                timeout = mod.optDouble("autohideTimeout", 3.0);
                if (timeout <= 0) timeout = 3.0;
            } catch (Exception ignored) {}
            boolean wantShown = (now - lastActivityMs) < timeout * 1000L;
            if (wantShown != targetShown) {
                fromAmount = hiddenAt(now);
                transitionStartMs = now;
                targetShown = wantShown;
            }
        } catch (Exception ignored) {}
    }

    /** Hidden amount 0 (shown) .. 1 (hidden) at a timestamp, eased. */
    private static float hiddenAt(long now) {
        long dur = Math.max(1L, animMs);
        float t = Math.max(0f, Math.min(1f, (now - transitionStartMs) / (float) dur));
        t = t * t * (3 - 2 * t);
        float target = targetShown ? 0f : 1f;
        return fromAmount + (target - fromAmount) * t;
    }

    /** Overshoot ease for the pop-in animation (briefly past 1). */
    private static float easeOutBack(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (t - 1f) * (t - 1f) * (t - 1f) + c1 * (t - 1f) * (t - 1f);
    }

    private static void noteShown() {
        targetShown = true;
        fromAmount = 0f;
        transitionStartMs = System.currentTimeMillis();
    }

    /** Pushes the current hide transform (always balanced by popTransform). */
    public static void pushTransform(GuiGraphicsExtractor graphics) {
        try {
            graphics.pose().pushMatrix();
        } catch (Exception ignored) {
            return;
        }
        try {
            var mc = Minecraft.getInstance();
            var config = com.moidclient.ClientMod.getInstance().getConfigManager();
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("autohideHud") : null;
            if (mc == null || mod == null || !mod.enabled) return;
            float h = hiddenAt(System.currentTimeMillis());
            if (h <= 0f) return;
            boolean showing = targetShown;
            String mode = showing ? mod.optString("autohideOpen", "slide")
                    : mod.optString("autohideClose", "slide");
            if ("instant".equals(mode)) {
                h = showing ? 0f : 1f;
                if (h <= 0f) return;
            }
            var pose = graphics.pose();
            boolean scalePath = (!showing && "shrink".equals(mode))
                    || (showing && ("grow".equals(mode) || "pop".equals(mode)));
            if (scalePath) {
                float s;
                if (showing && "pop".equals(mode)) {
                    s = Math.max(0f, easeOutBack(1f - h));
                } else {
                    s = Math.max(0f, 1f - h);
                }
                if (s <= 0f) {
                    pose.translate(0f, SLIDE_PX * 2f);
                    return;
                }
                float px = 100f;
                float py = 100f;
                try {
                    if (mc.getWindow() != null) {
                        px = mc.getWindow().getGuiScaledWidth() / 2f;
                        py = mc.getWindow().getGuiScaledHeight() - 8f;
                    }
                } catch (Exception ignored) {}
                pose.translate(px, py);
                pose.scale(s, s);
                pose.translate(-px, -py);
            } else {
                pose.translate(0f, h * SLIDE_PX);
            }
        } catch (Exception ignored) {}
    }

    /** Pops what pushTransform pushed (always paired, even when identity). */
    public static void popTransform(GuiGraphicsExtractor graphics) {
        try {
            graphics.pose().popMatrix();
        } catch (Exception ignored) {}
    }
}
