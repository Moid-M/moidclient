package com.moidclient.hud.server;

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
 * Server IP HUD - shows the current server address, or Singleplayer.
 * Category: HUD
 */
public final class ServerHud {
    private ServerHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("server", "Server IP", "Current server address.", "hud", true, "server", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "Server: {server}"),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static String currentAddress() {
        try {
            var server = Minecraft.getInstance().getCurrentServer();
            if (server != null && server.ip != null && !server.ip.isEmpty()) return server.ip;
        } catch (Exception ignored) {}
        return "Singleplayer";
    }

    public static String formatText(ConfigManager.ModuleConfig mod, String address) {
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "Server: {server}";
        return fmt.replace("{server}", address)
                .replace("{ip}", address)
                .replace("{value}", address);
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("server");
        if (mod == null) return null;
        return ModulePreview.text("server", formatText(mod, currentAddress()));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("server");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null && mc.getConnection() == null) return;
        String text = formatText(mod, currentAddress());

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
