package com.moidclient.hud.memory;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudCompat;
import com.moidclient.hud.HudManager;
import com.moidclient.module.LiveStats;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.module.ModulePreview;
import com.moidclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix3x2fStack;

/**
 * Memory Usage HUD - JVM heap usage (used / max) with percent.
 * Category: HUD
 */
public final class MemoryHud {
    private MemoryHud() {}

    // Displayed usage eases toward the sample (classic smooth readout).
    private static double displayedUsedMb = 0;

    public static ModuleDef definition() {
        return new ModuleDef("memoryUsage", "Memory Usage", "JVM heap usage.", "hud", true, "gauge", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "Memory: {value} MB"),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static long usedMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
    }

    public static long maxMb() {
        return Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    public static int usedPct() {
        long max = maxMb();
        if (max <= 0) return 0;
        return (int) Math.max(0, Math.min(100, usedMb() * 100 / max));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("memoryUsage");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        long rawUsed = usedMb();
        float dtSec = 0.05f;
        try {
            if (deltaTracker != null) dtSec = Math.max(0f, deltaTracker.getRealtimeDeltaTicks() / 20f);
        } catch (Exception ignored) {}
        if (displayedUsedMb <= 0) displayedUsedMb = rawUsed;
        double k = 1.0 - Math.exp(-3.0 * Math.max(0.0, dtSec));
        displayedUsedMb += (rawUsed - displayedUsedMb) * k;
        long used = Math.round(displayedUsedMb);
        long max = maxMb();
        int pct = max <= 0 ? 0 : (int) Math.max(0, Math.min(100, displayedUsedMb * 100 / max));
        String text = formatText(mod, used, max, pct);

        int color;
        if (mod.textColor != null && !mod.textColor.isEmpty()) {
            try { color = ColorUtil.parseHex(mod.textColor, mod.opacity); } catch (Exception e) { color = ColorUtil.parseHex("#FFFFFF", mod.opacity); }
        } else if (mod.color != null && !mod.color.isEmpty()) {
            try { color = ColorUtil.parseHex(mod.color, mod.opacity); } catch (Exception e) { color = ColorUtil.parseHex("#FFFFFF", mod.opacity); }
        } else {
            color = ColorUtil.parseHex("#FFFFFF", mod.opacity);
        }
        boolean shadow = mod.shadow;

        int x = mod.x;
        int y = mod.y;
        double scale = mod.scale;
        if (scale <= 0) scale = 1.0;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        var font = mc.font;
        int textW = font.width(text);
        int textH = 9;

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale((float) scale, (float) scale);
            if (mod.background) {
                int bg;
                try { bg = ColorUtil.parseHex(mod.backgroundColor != null ? mod.backgroundColor : "#1A1B20", mod.backgroundOpacity); }
                catch (Exception e) { bg = ColorUtil.withOpacity(0x1A1B20, mod.backgroundOpacity); }
                graphics.fill(-3, -3, textW + 3, textH + 3, bg);
            }
            graphics.text(font, text, 0, 0, color, shadow);
        } finally {
            pose.popMatrix();
        }
    }

    public static String formatText(ConfigManager.ModuleConfig mod, long used, long max, int pct) {
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "Memory: {value} MB";
        return fmt.replace("{used}", String.valueOf(used))
                .replace("{max}", String.valueOf(max))
                .replace("{pct}", String.valueOf(pct))
                .replace("{value}", String.valueOf(used));
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("memoryUsage");
        if (mod == null) return null;
        String text = formatText(mod, usedMb(), maxMb(), usedPct());
        int[] size = HudManager.measureText(text, mod.background);
        return new ModulePreview("memoryUsage", text, "text", size[0], size[1], false);
    }
}
