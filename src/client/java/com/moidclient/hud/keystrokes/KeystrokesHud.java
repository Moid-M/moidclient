package com.moidclient.hud.keystrokes;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudCompat;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * Keystrokes HUD - Feather/Lunar-like.
 * Shows WASD + mouse + space/shift with pressed highlighting.
 * Category: HUD
 */
public final class KeystrokesHud {
    private KeystrokesHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("keystrokes", "Keystrokes", "WASD + mouse overlay.", "hud", true,
            ModuleOption.list(
                ModuleOption.bool("keystrokesShowW", "Show W"),
                ModuleOption.bool("keystrokesShowA", "Show A"),
                ModuleOption.bool("keystrokesShowS", "Show S"),
                ModuleOption.bool("keystrokesShowD", "Show D"),
                ModuleOption.bool("keystrokesShowMouse", "Show mouse buttons"),
                ModuleOption.bool("keystrokesShowSpace", "Show space"),
                ModuleOption.bool("keystrokesShowShift", "Show shift"),
                ModuleOption.bool("keystrokesShowCps", "Show CPS on mouse buttons"),
                ModuleOption.slider("keystrokesGap", "Key gap", 0, 10, 1),
                ModuleOption.bool("keystrokesOutline", "Key outlines"),
                ModuleOption.nullableColor("keystrokesPressedColor", "Pressed color", "empty = accent"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("keystrokes");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().handle();

        boolean wDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean aDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        boolean sDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        boolean dDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        boolean spaceDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean shiftDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean lmbDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rmbDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        boolean showMouse = mod.keystrokesShowMouse;
        boolean showSpace = mod.keystrokesShowSpace;
        boolean showShift = mod.keystrokesShowShift;
        boolean showW = mod.keystrokesShowW;
        boolean showA = mod.keystrokesShowA;
        boolean showS = mod.keystrokesShowS;
        boolean showD = mod.keystrokesShowD;
        boolean showCps = mod.keystrokesShowCps;
        int gap = Math.max(0, Math.min(10, mod.keystrokesGap));
        boolean outline = mod.keystrokesOutline;
        String pressedHex = mod.keystrokesPressedColor;
        if (pressedHex == null || pressedHex.isEmpty()) {
            try { pressedHex = config.getAccentColor(); } catch (Exception e) { pressedHex = "#FFFFFF"; }
        }

        int x = mod.x;
        int y = mod.y;
        double scale = mod.scale <= 0 ? 1.0 : mod.scale;
        double opacity = mod.opacity;
        double bgOpacity = mod.backgroundOpacity;
        String bgColor = mod.backgroundColor != null ? mod.backgroundColor : "#1A1B20";
        String textColor = mod.textColor;
        boolean shadow = mod.shadow;
        boolean bgEnabled = mod.background;

        // key metrics - Feather/Lunar-like compact
        int keySize = 18;
        int spaceWidth = keySize * 3 + gap * 2;
        int mouseWidth = 28;

        Minecraft mc2 = Minecraft.getInstance();
        var font = mc2.font;

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale((float) scale, (float) scale);

            int curY = 0;
            // Row 1: W centered
            if (showW) {
                int wX = keySize + gap;
                drawKey(graphics, font, "W", wX, curY, keySize, keySize, wDown, bgEnabled, bgColor, bgOpacity, textColor, pressedHex, opacity, shadow, outline);
                curY += keySize + gap;
            }
            // Row 2: A S D
            boolean hasASD = showA || showS || showD;
            if (hasASD) {
                if (showA) drawKey(graphics, font, "A", 0, curY, keySize, keySize, aDown, bgEnabled, bgColor, bgOpacity, textColor, pressedHex, opacity, shadow, outline);
                if (showS) drawKey(graphics, font, "S", keySize + gap, curY, keySize, keySize, sDown, bgEnabled, bgColor, bgOpacity, textColor, pressedHex, opacity, shadow, outline);
                if (showD) drawKey(graphics, font, "D", (keySize + gap) * 2, curY, keySize, keySize, dDown, bgEnabled, bgColor, bgOpacity, textColor, pressedHex, opacity, shadow, outline);
                curY += keySize + gap;
            }
            // Row 3: mouse if enabled (handle per-key L/R via showMouse + individual)
            boolean showL = showMouse && mod.keystrokesShowMouse; // keep legacy, but also check individual
            // for per-key, use showMouse as overall, but also allow disabling individual via showA etc. For mouse, use showMouse
            if (showMouse) {
                String leftLabel = showCps ? "L " + com.moidclient.hud.cps.CpsHud.getLeftCps() : "L";
                String rightLabel = showCps ? "R " + com.moidclient.hud.cps.CpsHud.getRightCps() : "R";
                // if individual L/R disabled via future per-key, still show both when showMouse true
                drawKey(graphics, font, leftLabel, 0, curY, mouseWidth, keySize, lmbDown, bgEnabled, bgColor, bgOpacity, textColor, pressedHex, opacity, shadow, outline);
                drawKey(graphics, font, rightLabel, mouseWidth + gap, curY, mouseWidth, keySize, rmbDown, bgEnabled, bgColor, bgOpacity, textColor, pressedHex, opacity, shadow, outline);
                curY += keySize + gap;
            }
            // Row 4: space if enabled
            if (showSpace) {
                drawKey(graphics, font, "SPACE", 0, curY, spaceWidth, keySize - 4, spaceDown, bgEnabled, bgColor, bgOpacity, textColor, pressedHex, opacity, shadow, outline);
                curY += (keySize - 4) + gap;
            }
            // Row 5: shift if enabled
            if (showShift) {
                drawKey(graphics, font, "SHIFT", 0, curY, spaceWidth, keySize - 6, shiftDown, bgEnabled, bgColor, bgOpacity, textColor, pressedHex, opacity, shadow, outline);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static void drawKey(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String label,
                                int x, int y, int w, int h, boolean pressed,
                                boolean bgEnabled, String bgColor, double bgOpacity,
                                String textColor, String pressedHex, double opacity, boolean shadow, boolean outline) {
        int bg = 0;
        int textCol;
        if (pressed) {
            // pressed uses pressedHex with full opacity
            try { textCol = ColorUtil.parseHex(pressedHex, opacity); } catch (Exception e) { textCol = ColorUtil.parseHex("#FFFFFF", opacity); }
            if (bgEnabled) {
                try { bg = ColorUtil.parseHex(pressedHex, bgOpacity); } catch (Exception e) { bg = ColorUtil.parseHex("#FFFFFF", bgOpacity); }
                // slightly dim bg for pressed
                bg = (bg & 0x00FFFFFF) | ((int)(bgOpacity * 255) << 24);
                g.fill(x, y, x + w, y + h, bg);
            } else {
                // no bg, just text color change
            }
        } else {
            if (bgEnabled) {
                try { bg = ColorUtil.parseHex(bgColor, bgOpacity); } catch (Exception e) { bg = ColorUtil.parseHex("#1A1B20", bgOpacity); }
                g.fill(x, y, x + w, y + h, bg);
            }
            if (textColor != null && !textColor.isEmpty()) {
                try { textCol = ColorUtil.parseHex(textColor, opacity); } catch (Exception e) { textCol = ColorUtil.parseHex("#FFFFFF", opacity); }
            } else {
                textCol = ColorUtil.parseHex("#FFFFFF", opacity);
            }
        }
        // center text
        int tw = font.width(label);
        int th = 8;
        int tx = x + (w - tw) / 2;
        int ty = y + (h - th) / 2 + 1;
        g.text(font, label, tx, ty, textCol, shadow);
        // border when not pressed and bg enabled and outline enabled
        if (!pressed && bgEnabled && outline) {
            int border = ColorUtil.parseHex("#2A2C34", opacity * 0.6);
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);
        }
    }

    // parseColor -> ColorUtil.parseHex
}
