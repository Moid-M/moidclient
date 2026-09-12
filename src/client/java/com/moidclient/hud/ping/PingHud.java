package com.moidclient.hud.ping;

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
 * Ping Display HUD - shows server latency.
 * Category: HUD
 */
public final class PingHud {
    private PingHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("ping", "Ping Display", "Server latency in ms.", "hud", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "Ping: {ping} ms"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("ping");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        int ping = getPing();
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "Ping: {ping} ms";
        String text = fmt.replace("{ping}", String.valueOf(ping)).replace("{value}", String.valueOf(ping));

        int color;
        if (mod.textColor != null && !mod.textColor.isEmpty()) {
            try { color = ColorUtil.parseHex(mod.textColor, mod.opacity); } catch (Exception e) { color = colorForPing(ping, mod.opacity); }
        } else if (mod.color != null && !mod.color.isEmpty()) {
            try { color = ColorUtil.parseHex(mod.color, mod.opacity); } catch (Exception e) { color = colorForPing(ping, mod.opacity); }
        } else {
            color = colorForPing(ping, mod.opacity);
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

    public static int getCurrentPing() { return getPing(); }

    private static int getPing() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return 0;
            if (mc.player == null) return 0;
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info == null) return 0;
            return info.getLatency();
        } catch (Exception e) {
            return 0;
        }
    }

    private static int colorForPing(int ping, double opacity) {
        int alpha = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        int rgb;
        if (ping <= 0) rgb = 0x9CA3AF;
        else if (ping < 80) rgb = 0x10B981;
        else if (ping < 150) rgb = 0xF59E0B;
        else rgb = 0xEF4444;
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    // parseColor moved to ColorUtil.parseHex
}
