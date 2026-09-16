package com.moidclient.hud.tps;

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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * TPS Counter HUD - estimates server ticks per second by sampling world
 * time against the wall clock over a rolling 3s window. 20.0 = healthy,
 * lower = lagging server. Resets on world change/time rewind.
 * Category: HUD
 */
public final class TpsHud {
    private static final Deque<long[]> SAMPLES = new ArrayDeque<>();
    private static double lastTps = 20.0;

    private TpsHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("tpsCounter", "TPS Counter", "Server ticks per second.", "hud", true, "gauge", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "TPS: {tps}"),
                ModuleOption.nullableColor("textColor", "Text color", "empty = auto by TPS"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.bool("tpsDynamicColor", "Dynamic color (health)"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("tpsCounter");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        double tpsVal = sample();
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "TPS: {tps}";
        String text = fmt.replace("{tps}", formatTps(tpsVal)).replace("{value}", formatTps(tpsVal));

        int color;
        if (mod.tpsDynamicColor) {
            color = colorForTps(tpsVal, mod.opacity);
        } else if (mod.textColor != null && !mod.textColor.isEmpty()) {
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

    /** Current estimate, shared with LiveStats for editor previews. */
    public static double getCurrentTps() {
        return sample();
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("tpsCounter");
        if (mod == null) return null;
        double tps = stats != null ? stats.tps : 20.0;
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "TPS: {tps}";
        String text = fmt.replace("{tps}", formatTps(tps)).replace("{value}", formatTps(tps));
        int[] size = HudManager.measureText(text, mod.background);
        return new ModulePreview("tpsCounter", text, "text", size[0], size[1], false);
    }

    private static String formatTps(double tps) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0, Math.min(20.0, tps)));
    }

    /** Samples world time vs wall clock; render thread only. */
    private static double sample() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return lastTps;
            // Paused singleplayer advances no ticks while the clock runs -
            // freeze the display instead of tanking to 0. Clearing keeps the
            // post-resume reading clean (no dip from the paused span).
            try {
                if (mc.isPaused()) {
                    SAMPLES.clear();
                    return lastTps;
                }
            } catch (Exception ignored) {}
            long gameTime = mc.level.getLevelData().getGameTime();
            long now = System.currentTimeMillis();
            if (!SAMPLES.isEmpty() && gameTime < SAMPLES.peekLast()[0]) SAMPLES.clear();
            SAMPLES.addLast(new long[]{gameTime, now});
            while (SAMPLES.size() > 600) SAMPLES.pollFirst();
            while (!SAMPLES.isEmpty() && now - SAMPLES.peekFirst()[1] > 3000) SAMPLES.pollFirst();
            if (SAMPLES.size() >= 2) {
                long[] first = SAMPLES.peekFirst();
                long[] last = SAMPLES.peekLast();
                long deltaMs = last[1] - first[1];
                if (deltaMs >= 500) {
                    double tps = (last[0] - first[0]) * 1000.0 / deltaMs;
                    lastTps = Math.max(0.0, Math.min(20.0, tps));
                }
            }
        } catch (Exception ignored) {}
        return lastTps;
    }

    private static int colorForTps(double tps, double opacity) {
        int alpha = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        int rgb;
        if (tps >= 19.0) rgb = 0x22C55E;
        else if (tps >= 15.0) rgb = 0xEAB308;
        else rgb = 0xEF4444;
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
