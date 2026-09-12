package com.moidclient.hud.fps;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudCompat;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix3x2fStack;

/**
 * FPS Counter HUD - shows frames per second with fast/stable modes and optional dynamic gradient.
 * Category: HUD
 */
public final class FpsHud {
    private static int stableFps = 60;
    private static long lastFpsUpdate = 0;

    private FpsHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("fpsCounter", "FPS Counter", "Shows current frames per second.", "hud", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "FPS: {fps}"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.select("fpsMode", "Update speed", java.util.List.of("stable", "fast")),
                ModuleOption.bool("fpsDynamicColor", "Dynamic color (red-green)"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("fpsCounter");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        int fpsVal = getFpsForMode(mod.fpsMode, deltaTracker);
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "FPS: {fps}";
        String text = fmt.replace("{fps}", String.valueOf(fpsVal)).replace("{value}", String.valueOf(fpsVal)).replace("{ping}", String.valueOf(fpsVal));

        int color;
        if (mod.fpsDynamicColor) {
            color = colorForFpsGradient(fpsVal, mod.opacity);
        } else {
            if (mod.textColor != null && !mod.textColor.isEmpty()) {
                try { color = ColorUtil.parseHex(mod.textColor, mod.opacity); } catch (Exception e) { color = ColorUtil.parseHex("#FFFFFF", mod.opacity); }
            } else if (mod.color != null && !mod.color.isEmpty()) {
                try { color = ColorUtil.parseHex(mod.color, mod.opacity); } catch (Exception e) { color = ColorUtil.parseHex("#FFFFFF", mod.opacity); }
            } else {
                color = ColorUtil.parseHex("#FFFFFF", mod.opacity);
            }
        }
        boolean shadow = mod.shadow;

        int x = mod.x;
        int y = mod.y;
        double scale = mod.scale;
        if (scale <= 0) scale = 1.0;

        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int textW = font.width(text);
        int textH = 9;

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale((float) scale, (float) scale);
            if (mod.background) {
                int bg = ColorUtil.parseHex(mod.backgroundColor != null ? mod.backgroundColor : "#1A1B20", mod.backgroundOpacity);
                graphics.fill(-3, -3, textW + 3, textH + 3, bg);
            }
            graphics.text(font, text, 0, 0, color, shadow);
        } finally {
            pose.popMatrix();
        }
    }

    public static int getCurrentFps() {
        try { return Minecraft.getInstance().getFps(); } catch (Exception e) { return 60; }
    }

    private static int getFpsForMode(String mode, DeltaTracker dt) {
        try {
            if ("fast".equals(mode)) {
                float rt = dt != null ? dt.getRealtimeDeltaTicks() : 0;
                if (rt > 0.001f) return Math.max(1, Math.round(20f / rt));
                return Minecraft.getInstance().getFps();
            }
            int raw = Minecraft.getInstance().getFps();
            long now = System.currentTimeMillis();
            if (now - lastFpsUpdate >= 1000) {
                stableFps = raw;
                lastFpsUpdate = now;
            }
            return stableFps;
        } catch (Exception e) {
            return 60;
        }
    }

    private static int colorForFpsGradient(int fps, double opacity) {
        int alpha = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        if (fps >= 90) return (alpha << 24) | (0x22C55E & 0xFFFFFF);
        if (fps <= 30) return (alpha << 24) | (0xFF0000 & 0xFFFFFF);
        double t = (fps - 30) / 60.0;
        int r1 = 0xFF, g1 = 0x00, b1 = 0x00;
        int r2 = 0x22, g2 = 0xC5, b2 = 0x5E;
        int r = (int) Math.round(r1 + (r2 - r1) * t);
        int g = (int) Math.round(g1 + (g2 - g1) * t);
        int b = (int) Math.round(b1 + (b2 - b1) * t);
        int rgb = (r << 16) | (g << 8) | b;
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    // parseColor -> ColorUtil.parseHex
}
