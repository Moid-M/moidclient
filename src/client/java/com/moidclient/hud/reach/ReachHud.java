package com.moidclient.hud.reach;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudCompat;
import com.moidclient.hud.HudManager;
import com.moidclient.hud.combat.CombatTracker;
import com.moidclient.module.LiveStats;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.module.ModulePreview;
import com.moidclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix3x2fStack;

import java.util.Locale;

/**
 * Reach Display HUD - distance of the last attack (eye to hit point).
 * Category: HUD
 */
public final class ReachHud {
    private ReachHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("reachDisplay", "Reach Display", "Distance of your last attack.", "hud", true, "target", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "Reach: {value}"),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static String formatReach(double reach) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, reach));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("reachDisplay");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        String text = formatText(mod, CombatTracker.getLastReach());

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

    public static String formatText(ConfigManager.ModuleConfig mod, double reach) {
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "Reach: {value}";
        return fmt.replace("{reach}", formatReach(reach))
                .replace("{value}", formatReach(reach));
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("reachDisplay");
        if (mod == null) return null;
        // Sample fallback so the editor shows something placeable pre-attack.
        double reach = CombatTracker.getLastReach();
        if (reach <= 0) reach = 3.2;
        String text = formatText(mod, reach);
        int[] size = HudManager.measureText(text, mod.background);
        return new ModulePreview("reachDisplay", text, "text", size[0], size[1], false);
    }
}
