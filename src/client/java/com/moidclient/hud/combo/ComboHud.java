package com.moidclient.hud.combo;

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

/**
 * Combo Counter HUD - consecutive outgoing hits within a window.
 * Resets when you take damage or the window lapses.
 * Category: HUD
 */
public final class ComboHud {
    private ComboHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("comboCounter", "Combo Counter", "Consecutive hits without taking damage.", "hud", true, "activity", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "Combo: {value}"),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.slider("comboWindow", "Reset window (s)", 1, 10, 0.5, 3),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("comboCounter");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        double window = mod.optDouble("comboWindow", 3.0);
        if (window <= 0) window = 3.0;
        int combo = CombatTracker.getCombo((long) (window * 1000));
        int peak = CombatTracker.getPeakCombo();
        String text = formatText(mod, combo, peak);

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

    public static String formatText(ConfigManager.ModuleConfig mod, int combo, int peak) {
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "Combo: {value}";
        return fmt.replace("{combo}", String.valueOf(combo))
                .replace("{peak}", String.valueOf(peak))
                .replace("{value}", String.valueOf(combo));
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("comboCounter");
        if (mod == null) return null;
        // Honor the user's window like render() does; fall back to a sample
        // so the editor shows something placeable with no recent attacks.
        double window = mod.optDouble("comboWindow", 3.0);
        if (window <= 0) window = 3.0;
        int combo = CombatTracker.getCombo((long) (window * 1000));
        int peak = CombatTracker.getPeakCombo();
        if (combo == 0 && peak == 0) {
            combo = 3;
            peak = 5;
        }
        String text = formatText(mod, combo, peak);
        int[] size = HudManager.measureText(text, mod.background);
        return new ModulePreview("comboCounter", text, "text", size[0], size[1], false);
    }
}
