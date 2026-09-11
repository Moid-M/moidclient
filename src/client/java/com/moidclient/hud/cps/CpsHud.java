package com.moidclient.hud.cps;

import com.moidclient.config.ConfigManager;
import com.moidclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * CPS Counter HUD - shows clicks per second (left / right / both).
 * Surprise: peak tracking + fire icon when bursting + smooth fade.
 * Category: HUD / Combat
 */
public final class CpsHud {
    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static boolean wasLeftDown = false;
    private static boolean wasRightDown = false;
    private static int peakLeft = 0;
    private static int peakRight = 0;
    private static long lastPeakReset = System.currentTimeMillis();

    private CpsHud() {}

    /** Called every client tick to poll mouse buttons */
    public static void onTick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return;
            long handle = mc.getWindow().handle();
            boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
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

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("cpsCounter");
        if (mod == null || !mod.enabled) return;
        if (Minecraft.getInstance().options.hideGui) return;

        int left = getLeftCps();
        int right = getRightCps();
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
                text = text + " \uD83D\uDD25";
            }
        }

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
                int bg = ColorUtil.parseHex(mod.backgroundColor != null ? mod.backgroundColor : "#1A1B20", mod.backgroundOpacity);
                graphics.fill(-3, -3, textW + 3, textH + 3, bg);
            }
            graphics.text(font, text, 0, 0, color, shadow);
        } finally {
            pose.popMatrix();
        }
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
