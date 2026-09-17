package com.moidclient.hud.cps;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudCompat;
import com.moidclient.hud.HudManager;
import com.moidclient.module.ModulePreview;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix3x2fStack;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * CPS Counter HUD - shows clicks per second (left / right / both).
 * Surprise: peak tracking + fire icon when bursting + smooth fade.
 * Category: HUD / Combat
 */
public final class CpsHud {
    // Lock-free deques: tick writes, render + WS-preview threads read.
    private static final Deque<Long> leftClicks = new ConcurrentLinkedDeque<>();
    private static final Deque<Long> rightClicks = new ConcurrentLinkedDeque<>();
    private static boolean wasLeftDown = false;
    private static boolean wasRightDown = false;
    private static int peakLeft = 0;
    private static int peakRight = 0;
    private static long lastPeakReset = System.currentTimeMillis();

    private CpsHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("cpsCounter", "CPS Counter", "Clicks per second - left | right with burst fire.", "hud", true, "mouse", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "CPS: {left} | {right}"),
                ModuleOption.nullableColor("textColor", "Text color", "empty = white"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.select("cpsMode", "Buttons shown", java.util.List.of("both", "left", "right")),
                ModuleOption.bool("cpsDynamicColor", "Dynamic color (burst green)"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    /** Called every client tick to poll mouse buttons */
    public static void onTick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            boolean leftDown = com.moidclient.utility.keybind.NativeKeys.isMouseDown(mc,
                com.moidclient.utility.keybind.NativeKeys.GLFW_MOUSE_LEFT);
            boolean rightDown = com.moidclient.utility.keybind.NativeKeys.isMouseDown(mc,
                com.moidclient.utility.keybind.NativeKeys.GLFW_MOUSE_RIGHT);
            long now = System.currentTimeMillis();

            if (leftDown && !wasLeftDown) {
                leftClicks.addLast(now);
                // burst peak
                int cur = getLeftCps();
                if (cur > peakLeft) peakLeft = cur;
            }
            if (rightDown && !wasRightDown) {
                rightClicks.addLast(now);
                int cur = getRightCps();
                if (cur > peakRight) peakRight = cur;
            }
            wasLeftDown = leftDown;
            wasRightDown = rightDown;

            // prune >1s
            while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000) leftClicks.pollFirst();
            while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) rightClicks.pollFirst();

            // reset peak every 5s if no new peak
            if (now - lastPeakReset > 5000) {
                peakLeft = getLeftCps();
                peakRight = getRightCps();
                lastPeakReset = now;
            }
        } catch (Exception ignored) {}
    }

    public static int getLeftCps() {
        long now = System.currentTimeMillis();
        while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000) leftClicks.pollFirst();
        return leftClicks.size();
    }

    public static int getRightCps() {
        long now = System.currentTimeMillis();
        while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) rightClicks.pollFirst();
        return rightClicks.size();
    }

    public static int getPeakLeft() {
        return peakLeft;
    }

    public static int getPeakRight() {
        return peakRight;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("cpsCounter");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        int left = getLeftCps();
        int right = getRightCps();
        String mode = mod.cpsMode != null ? mod.cpsMode : "both";
        String text = formatText(mod, left, right);

        int color;
        // dynamic color: low cps muted, high cps green
        boolean useDynamic = mod.cpsDynamicColor;
        if (useDynamic) {
            int cpsForColor = "right".equals(mode) ? right : "left".equals(mode) ? left : Math.max(left, right);
            color = colorForCps(cpsForColor, mod.opacity);
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

    /** Shared text builder used by both the in-game HUD and the dashboard preview. */
    public static String formatText(ConfigManager.ModuleConfig mod, int left, int right) {
        return formatText(mod, left, right, peakLeft, peakRight);
    }

    public static String formatText(ConfigManager.ModuleConfig mod, int left, int right, int peakLeft, int peakRight) {
        String mode = mod.cpsMode != null ? mod.cpsMode : "both";
        String fmt = mod.format;
        if (fmt == null || fmt.isEmpty()) {
            if ("left".equals(mode)) fmt = "CPS: {left}";
            else if ("right".equals(mode)) fmt = "CPS: {right}";
            else fmt = "CPS: {left} | {right}";
        }
        // legacy migration: if format still contains {ping}
        if (fmt.contains("{ping}")) {
            if ("left".equals(mode)) fmt = "CPS: {left}";
            else if ("right".equals(mode)) fmt = "CPS: {right}";
            else fmt = "CPS: {left} | {right}";
        }
        // hide unused placeholder when mode is left/right (so Both shows "8 | 12", Left shows "8", Right shows "12")
        if ("left".equals(mode)) {
            fmt = fmt.replaceAll("\\s*\\|\\s*\\{right\\}", "").replaceAll("\\{right\\}\\s*\\|\\s*", "").replace("{right}", "").replace("{r}", "");
            fmt = fmt.replaceAll("\\|\\s*\\|", "|").replaceAll("\\s*\\|\\s*$", "").replaceAll("^\\s*\\|\\s*", "").trim();
            // clean up "CPS:  |" -> "CPS:"
            fmt = fmt.replaceAll("\\s*\\|\\s*$", "").trim();
        } else if ("right".equals(mode)) {
            fmt = fmt.replaceAll("\\s*\\|\\s*\\{left\\}", "").replaceAll("\\{left\\}\\s*\\|\\s*", "").replace("{left}", "").replace("{l}", "");
            fmt = fmt.replaceAll("\\|\\s*\\|", "|").replaceAll("\\s*\\|\\s*$", "").replaceAll("^\\s*\\|\\s*", "").trim();
            fmt = fmt.replaceAll("\\s*\\|\\s*$", "").trim();
        }
        String text = fmt.replace("{left}", String.valueOf(left))
                         .replace("{right}", String.valueOf(right))
                         .replace("{cps}", String.valueOf(Math.max(left, right)))
                          .replace("{peakLeft}", String.valueOf(peakLeft))
                          .replace("{peakRight}", String.valueOf(peakRight))
                          .replace("{peak}", String.valueOf(Math.max(peakLeft, peakRight)))
                         .replace("{l}", String.valueOf(left))
                         .replace("{r}", String.valueOf(right))
                         .replace("{value}", String.valueOf(left))
                         .replace("{ping}", String.valueOf(left));
        // clean any leftover empty placeholders after mode filtering
        text = text.replaceAll("\\s*\\|\\s*\\|", " | ").replaceAll("\\s*\\|\\s*$", "").trim();

        // surprise: fire icon when bursting (>10) and peak tag
        boolean bursting = left > 10 || right > 10;
        if (bursting && !text.contains("🔥")) {
            // subtle: add fire for fun when bursting, but keep format clean if user has custom format without fire
            // we append fire only if format is default style
            if (fmt.equals("CPS: {left} | {right}") || fmt.equals("CPS: {cps}")) {
                text = text + " 🔥";
            }
        }
        return text;
    }

    public static com.moidclient.module.ModulePreview preview(ConfigManager config, com.moidclient.module.LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("cpsCounter");
        if (mod == null) return null;
        int left = stats != null ? stats.cpsLeft : 0;
        int right = stats != null ? stats.cpsRight : 0;
        int peakL = stats != null ? stats.peakLeft : 0;
        int peakR = stats != null ? stats.peakRight : 0;
        String text = formatText(mod, left, right, peakL, peakR);
        int[] size = HudManager.measureText(text, mod.background);
        return new ModulePreview("cpsCounter", text, "text", size[0], size[1], false);
    }

    private static int colorForCps(int cps, double opacity) {
        int alpha = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        int rgb;
        if (cps >= 12) rgb = 0x22C55E; // bright green burst
        else if (cps >= 7) rgb = 0xEAB308; // amber mid
        else if (cps >= 3) rgb = 0xF97316; // orange low-mid
        else rgb = 0x9CA3AF; // muted idle
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    // parseColor -> ColorUtil.parseHex
}
