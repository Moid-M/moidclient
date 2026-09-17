package com.moidclient.hud.potions;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Potion Effects HUD - active mob effects with amplifier and duration,
 * longest first. Text-only list; effect colors optional.
 * Category: HUD
 */
public final class PotionEffectsHud {
    private static final int ROW_H = 10;

    private PotionEffectsHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("potionEffects", "Potion Effects", "Active effects with timers.", "hud", true, "flask", true,
            ModuleOption.list(
                ModuleOption.bool("potionColored", "Effect colors"),
                ModuleOption.bool("potionShowAmplifier", "Show amplifier"),
                ModuleOption.bool("potionShowDuration", "Show duration"),
                ModuleOption.bool("potionShowAmbient", "Show ambient"),
                ModuleOption.slider("potionMaxEffects", "Max shown", 1, 10, 1),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    /** One rendered row: display text plus optional effect color (null = default). */
    private record Row(String text, Integer color) {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("potionEffects");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.font == null) return;

        List<Row> rows = collectRows(mod);
        if (rows.isEmpty()) return;

        int x = mod.x;
        int y = mod.y;
        double scale = mod.scale;
        if (scale <= 0) scale = 1.0;

        var font = mc.font;
        int boxW = 0;
        for (Row row : rows) boxW = Math.max(boxW, font.width(row.text()));
        int boxH = rows.size() * ROW_H - 1;
        boolean shadow = mod.shadow;

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale((float) scale, (float) scale);
            if (mod.background) {
                int bg;
                try { bg = ColorUtil.parseHex(mod.backgroundColor != null ? mod.backgroundColor : "#1A1B20", mod.backgroundOpacity); }
                catch (Exception e) { bg = ColorUtil.withOpacity(0x1A1B20, mod.backgroundOpacity); }
                graphics.fill(-3, -3, boxW + 3, boxH + 3, bg);
            }
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int color;
                if (row.color() != null) {
                    int alpha = (int) Math.round(Math.max(0, Math.min(1, mod.opacity)) * 255);
                    color = (alpha << 24) | (row.color() & 0xFFFFFF);
                } else {
                    color = defaultColor(mod);
                }
                graphics.text(font, row.text(), 0, i * ROW_H, color, shadow);
            }
        } finally {
            pose.popMatrix();
        }
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("potionEffects");
        if (mod == null) return null;
        // Mirror the game: no effects means no box (not eternal samples).
        List<Row> rows = collectRows(mod);
        if (rows.isEmpty()) return null;
        String joined = String.join("\n", rows.stream().map(Row::text).toList());
        int w = 0;
        try {
            var font = Minecraft.getInstance().font;
            for (Row row : rows) w = Math.max(w, font.width(row.text()));
        } catch (Exception e) {
            w = 80;
        }
        return new ModulePreview("potionEffects", joined, "effects", w + 6, rows.size() * ROW_H + 5, false);
    }

    /** Live effect rows (text + optional effect color), longest first, capped. */
    private static List<Row> collectRows(ConfigManager.ModuleConfig mod) {
        List<Row> rows = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return rows;
            var effects = new ArrayList<>(mc.player.getActiveEffects());
            effects.sort(Comparator.comparingInt(
                (net.minecraft.world.effect.MobEffectInstance e) -> e.getDuration()).reversed());
            int max = Math.max(1, Math.min(10, mod.potionMaxEffects <= 0 ? 5 : mod.potionMaxEffects));
            for (var inst : effects) {
                if (rows.size() >= max) break;
                if (!mod.potionShowAmbient && inst.isAmbient()) continue;
                String name;
                Integer color = null;
                try {
                    var effect = inst.getEffect().value();
                    name = effect.getDisplayName().getString();
                    if (mod.potionColored) color = effect.getColor();
                } catch (Exception e) {
                    continue;
                }
                StringBuilder line = new StringBuilder(name);
                if (mod.potionShowAmplifier && inst.getAmplifier() > 0) {
                    line.append(' ').append(roman(inst.getAmplifier() + 1));
                }
                if (mod.potionShowDuration) {
                    line.append(' ').append(formatDuration(inst));
                }
                rows.add(new Row(line.toString(), color));
            }
        } catch (Exception ignored) {}
        return rows;
    }

    private static int defaultColor(ConfigManager.ModuleConfig mod) {
        if (mod.textColor != null && !mod.textColor.isEmpty()) {
            try { return ColorUtil.parseHex(mod.textColor, mod.opacity); } catch (Exception e) { /* fall through */ }
        }
        if (mod.color != null && !mod.color.isEmpty()) {
            try { return ColorUtil.parseHex(mod.color, mod.opacity); } catch (Exception e) { /* fall through */ }
        }
        return ColorUtil.parseHex("#FFFFFF", mod.opacity);
    }

    private static String formatDuration(net.minecraft.world.effect.MobEffectInstance inst) {
        try {
            if (inst.isInfiniteDuration()) return "∞";
            int sec = Math.max(0, inst.getDuration() / 20);
            return (sec / 60) + ":" + String.format("%02d", sec % 60);
        } catch (Exception e) {
            return "";
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
