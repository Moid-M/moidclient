package com.moidclient.hud.coords;

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
 * Coordinates HUD - shows the player's XYZ block position.
 * Category: HUD
 */
public final class CoordinatesHud {
    private CoordinatesHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("coords", "Coordinates", "Your XYZ block position.", "hud", true, "pin", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "XYZ: {x} | {y} | {z}"),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static String formatText(ConfigManager.ModuleConfig mod, int x, int y, int z) {
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "XYZ: {x} | {y} | {z}";
        return fmt.replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z))
                .replace("{value}", x + " " + y + " " + z);
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("coords");
        if (mod == null) return null;
        try {
            var player = Minecraft.getInstance().player;
            if (player == null) return sizedPreview(mod, formatText(mod, 0, 64, 0));
            var pos = player.blockPosition();
            return sizedPreview(mod, formatText(mod, pos.getX(), pos.getY(), pos.getZ()));
        } catch (Exception e) {
            return ModulePreview.text("coords", formatText(mod, 0, 64, 0));
        }
    }

    private static ModulePreview sizedPreview(ConfigManager.ModuleConfig mod, String text) {
        int[] size = HudManager.measureText(text, mod.background);
        return new ModulePreview("coords", text, "text", size[0], size[1], false);
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("coords");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var pos = mc.player.blockPosition();
        String text = formatText(mod, pos.getX(), pos.getY(), pos.getZ());

        int color;
        if (mod.textColor != null && !mod.textColor.isEmpty()) {
            try { color = ColorUtil.parseHex(mod.textColor, mod.opacity); } catch (Exception e) { color = ColorUtil.parseHex("#FFFFFF", mod.opacity); }
        } else if (mod.color != null && !mod.color.isEmpty()) {
            try { color = ColorUtil.parseHex(mod.color, mod.opacity); } catch (Exception e) { color = ColorUtil.parseHex("#FFFFFF", mod.opacity); }
        } else {
            color = ColorUtil.parseHex("#FFFFFF", mod.opacity);
        }

        int x = mod.x;
        int y = mod.y;
        double scale = mod.scale <= 0 ? 1.0 : mod.scale;

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
            graphics.text(font, text, 0, 0, color, mod.shadow);
        } finally {
            pose.popMatrix();
        }
    }
}
