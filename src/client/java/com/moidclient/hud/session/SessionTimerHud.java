package com.moidclient.hud.session;

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
 * Session Timer HUD - time spent in the current world/server.
 * Resets on world change (tracked by level identity).
 * Category: HUD
 */
public final class SessionTimerHud {
    private static long worldStartMs = 0;
    private static Object lastLevel = null;
    private static long serverStartMs = 0;
    private static Object lastConnection = null;
    private static long clientStartMs = 0;

    private SessionTimerHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("sessionTimer", "Session Timer", "Tracks playtime (world, server or client).", "hud", true, "timer", true,
            ModuleOption.list(
                ModuleOption.select("sessionScope", "Scope", java.util.List.of("world", "server", "client")),
                ModuleOption.text("format", "Format", "Session: {time}"),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("sessionTimer");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        String scope = scopeOf(mod);
        if ("world".equals(scope) && mc.level == null) return;

        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "Session: {time}";
        String time = formatElapsed(elapsedMs(scope));
        String text = fmt.replace("{time}", time).replace("{value}", time);

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

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("sessionTimer");
        if (mod == null) return null;
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "Session: {time}";
        String time = formatElapsed(elapsedMs(scopeOf(mod)));
        String text = fmt.replace("{time}", time).replace("{value}", time);
        int[] size = HudManager.measureText(text, mod.background);
        return new ModulePreview("sessionTimer", text, "text", size[0], size[1], false);
    }

    private static String scopeOf(ConfigManager.ModuleConfig mod) {
        String scope = mod.sessionScope;
        if ("server".equals(scope) || "client".equals(scope)) return scope;
        return "world";
    }

    /**
     * Milliseconds in the selected scope.
     * world: resets on level change. server: resets when the connection
     * changes (survives dimension hops on the same server). client: since
     * game launch, never resets.
     */
    private static long elapsedMs(String scope) {
        try {
            if ("client".equals(scope)) {
                if (clientStartMs <= 0) clientStartMs = System.currentTimeMillis();
                return System.currentTimeMillis() - clientStartMs;
            }
            Minecraft mc = Minecraft.getInstance();
            if ("server".equals(scope)) {
                Object conn = mc != null ? mc.getConnection() : null;
                if (conn == null) {
                    lastConnection = null;
                    return serverStartMs <= 0 ? 0 : System.currentTimeMillis() - serverStartMs;
                }
                if (conn != lastConnection || serverStartMs <= 0) {
                    lastConnection = conn;
                    serverStartMs = System.currentTimeMillis();
                }
                return System.currentTimeMillis() - serverStartMs;
            }
            if (mc == null || mc.level == null) {
                lastLevel = null;
                return worldStartMs <= 0 ? 0 : System.currentTimeMillis() - worldStartMs;
            }
            if (mc.level != lastLevel || worldStartMs <= 0) {
                lastLevel = mc.level;
                worldStartMs = System.currentTimeMillis();
            }
            return System.currentTimeMillis() - worldStartMs;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formatElapsed(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return h + ":" + String.format("%02d", m) + ":" + String.format("%02d", s);
        return m + ":" + String.format("%02d", s);
    }
}
