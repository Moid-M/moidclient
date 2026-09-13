package com.moidclient.hud.biome;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudCompat;
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
 * Biome HUD - shows the biome at the player's feet.
 * Category: HUD
 */
public final class BiomeHud {
    private BiomeHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("biome", "Biome", "Current biome name.", "hud", true, "mountain", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "Biome: {biome}"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static String currentBiome() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return "Unknown";
            var holder = mc.level.getBiome(mc.player.blockPosition());
            String path = holder.unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown");
            StringBuilder pretty = new StringBuilder();
            for (String part : path.split("_")) {
                if (part.isEmpty()) continue;
                if (pretty.length() > 0) pretty.append(' ');
                pretty.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) pretty.append(part.substring(1));
            }
            return pretty.length() > 0 ? pretty.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public static String formatText(ConfigManager.ModuleConfig mod, String biome) {
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "Biome: {biome}";
        return fmt.replace("{biome}", biome)
                .replace("{value}", biome);
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("biome");
        if (mod == null) return null;
        return ModulePreview.text("biome", formatText(mod, currentBiome()));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("biome");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String text = formatText(mod, currentBiome());

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
                int bg = ColorUtil.parseHex(mod.backgroundColor != null ? mod.backgroundColor : "#1A1B20", mod.backgroundOpacity);
                graphics.fill(-3, -3, textW + 3, textH + 3, bg);
            }
            graphics.text(font, text, 0, 0, color, mod.shadow);
        } finally {
            pose.popMatrix();
        }
    }
}
