package com.moidclient.hud.armor;

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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Armor Status HUD - equipped armor with icons and durability readout.
 * Empty slots are hidden; the box shrinks to what you wear.
 * Category: HUD
 */
public final class ArmorStatusHud {
    private static final int ROW_H = 18;
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private ArmorStatusHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("armorStatus", "Armor Status", "Equipped armor with durability.", "hud", true, "shield", true,
            ModuleOption.list(
                ModuleOption.bool("armorShowDurability", "Show durability"),
                ModuleOption.select("armorDurabilityMode", "Durability as", java.util.List.of("number", "percent")),
                ModuleOption.bool("armorDynamicColor", "Dynamic color (health)"),
                ModuleOption.bool("armorLowWarn", "Red when nearly broken"),
                ModuleOption.select("armorOrientation", "Layout", java.util.List.of("vertical", "horizontal")),
                ModuleOption.select("armorLabelSide", "Label position", java.util.List.of("right", "left", "above", "below")),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    /** One rendered item: the stack plus its durability label. */
    private record Row(ItemStack stack, String label) {}

    /** Placed item with box-local origin, size and measured text width. */
    private record Placed(ItemStack stack, String label, int x, int y, int w, int h, int textW) {}

    /** Laid-out box: items plus total size. */
    private record Layout(List<Placed> items, int w, int h) {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("armorStatus");
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
        Layout layout = layout(mod, rows, font::width);
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
                graphics.fill(-3, -3, layout.w() + 3, layout.h() + 3, bg);
            }
            for (Placed p : layout.items()) {
                drawItem(graphics, font, mod, shadow, p);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static void drawItem(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                                 ConfigManager.ModuleConfig mod, boolean shadow, Placed p) {
        String side = labelSide(mod);
        int iconX = p.x();
        int iconY = p.y();
        int textX = p.x();
        int textY = p.y();
        switch (side) {
            case "left" -> {
                iconX = p.x() + (p.label().isEmpty() ? 0 : p.textW() + 4);
                textY = p.y() + 4;
            }
            case "above" -> {
                iconX = p.x() + (p.w() - 16) / 2;
                iconY = p.y() + (p.label().isEmpty() ? 0 : 13);
                textX = p.x() + (p.w() - p.textW()) / 2;
            }
            case "below" -> {
                iconX = p.x() + (p.w() - 16) / 2;
                textX = p.x() + (p.w() - p.textW()) / 2;
                textY = p.y() + 20;
            }
            default -> { // right
                textX = p.x() + 20;
                textY = p.y() + 4;
            }
        }
        graphics.item(p.stack(), iconX, iconY);
        if (!p.label().isEmpty()) {
            graphics.text(font, p.label(), textX, textY, rowColor(mod, p.stack()), shadow);
        }
    }

    private static String labelSide(ConfigManager.ModuleConfig mod) {
        String side = mod.armorLabelSide;
        if ("left".equals(side) || "above".equals(side) || "below".equals(side)) return side;
        return "right";
    }

    /**
     * Lays items out for the orientation + label side. The measure function
     * maps label text to pixel width (same font in game and editor).
     */
    private static Layout layout(ConfigManager.ModuleConfig mod, List<Row> rows,
                                 java.util.function.ToIntFunction<String> measure) {
        List<Placed> items = new ArrayList<>();
        if (rows.isEmpty()) return new Layout(items, 0, 0);
        boolean horizontal = "horizontal".equals(mod.armorOrientation);
        String side = labelSide(mod);
        boolean stacked = side.equals("above") || side.equals("below");
        int cursorX = 0;
        int cursorY = 0;
        int boxW = 0;
        int boxH = 0;
        for (Row row : rows) {
            int textW = row.label().isEmpty() ? 0 : measure.applyAsInt(row.label());
            int itemW;
            int itemH;
            if (stacked) {
                itemW = Math.max(16, textW);
                itemH = row.label().isEmpty() ? 16 : 29;
            } else {
                itemW = row.label().isEmpty() ? 16 : 20 + textW;
                itemH = 18;
            }
            items.add(new Placed(row.stack(), row.label(), cursorX, cursorY, itemW, itemH, textW));
            if (horizontal) {
                boxW += itemW;
                boxH = Math.max(boxH, itemH);
                cursorX += itemW + 8;
            } else {
                boxW = Math.max(boxW, itemW);
                boxH += itemH;
                cursorY += itemH + (stacked ? 6 : 0);
            }
        }
        // Content size derives from the final cursor: gaps exist between
        // items (n-1 of them), not inside the summed widths.
        if (horizontal) {
            boxW = Math.max(0, cursorX - 8);
        } else if (stacked) {
            boxH = Math.max(0, cursorY - 6);
        } else {
            boxH = Math.max(0, cursorY);
        }
        return new Layout(items, boxW, boxH);
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("armorStatus");
        if (mod == null) return null;
        // Mirror the game: naked means no box (not placeholder text).
        List<Row> rows = collectRows(mod);
        if (rows.isEmpty()) return null;
        String joined = String.join("\n", rows.stream().map(Row::label).toList());
        int w;
        int h;
        try {
            var font = Minecraft.getInstance().font;
            Layout box = layout(mod, rows, font::width);
            w = box.w() + 6;
            h = box.h() + 5;
        } catch (Exception e) {
            w = 90;
            h = 20;
        }
        return new ModulePreview("armorStatus", joined, "effects", w, h, false);
    }

    private static List<Row> collectRows(ConfigManager.ModuleConfig mod) {
        List<Row> rows = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return rows;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack;
                try {
                    stack = mc.player.getItemBySlot(slot);
                } catch (Exception e) {
                    continue;
                }
                if (stack == null || stack.isEmpty()) continue;
                String label = "";
                if (mod.armorShowDurability) {
                    label = durabilityLabel(stack, mod);
                }
                rows.add(new Row(stack, label));
            }
        } catch (Exception ignored) {}
        return rows;
    }

    private static String durabilityLabel(ItemStack stack, ConfigManager.ModuleConfig mod) {
        try {
            if (!stack.isDamageableItem()) return "—";
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            if ("percent".equals(mod.armorDurabilityMode)) {
                int pct = (int) Math.round(remaining * 100.0 / Math.max(1, stack.getMaxDamage()));
                return pct + "%";
            }
            return String.valueOf(Math.max(0, remaining));
        } catch (Exception e) {
            return "";
        }
    }

    private static int rowColor(ConfigManager.ModuleConfig mod, ItemStack stack) {
        double frac = durabilityFraction(stack);
        // low-durability warning wins over the gradient so it stays visible
        // when both options are on.
        if (mod.armorLowWarn && frac >= 0 && frac < 0.1) {
            return ColorUtil.parseHex("#EF4444", mod.opacity);
        }
        if (mod.armorDynamicColor && frac >= 0) {
            return healthColor(frac, mod.opacity);
        }
        if (mod.textColor != null && !mod.textColor.isEmpty()) {
            try { return ColorUtil.parseHex(mod.textColor, mod.opacity); } catch (Exception e) { /* fall through */ }
        }
        if (mod.color != null && !mod.color.isEmpty()) {
            try { return ColorUtil.parseHex(mod.color, mod.opacity); } catch (Exception e) { /* fall through */ }
        }
        return ColorUtil.parseHex("#FFFFFF", mod.opacity);
    }

    /** Remaining durability 0-1, or -1 for undamageable items. */
    private static double durabilityFraction(ItemStack stack) {
        try {
            if (stack == null || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) return -1;
            return Math.max(0.0, Math.min(1.0,
                (stack.getMaxDamage() - stack.getDamageValue()) / (double) stack.getMaxDamage()));
        } catch (Exception e) {
            return -1;
        }
    }

    /** Smooth red -> green health gradient. */
    private static int healthColor(double frac, double opacity) {
        int alpha = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        int r = (int) Math.round(0xEF + (0x22 - 0xEF) * frac);
        int g = (int) Math.round(0x44 + (0xC5 - 0x44) * frac);
        int b = (int) Math.round(0x44 + (0x5E - 0x44) * frac);
        return (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
