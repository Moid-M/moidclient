package com.moidclient.utility.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Version bridge for key input. The dashboard and config always speak GLFW
 * key codes; on 26.3+ (SDL windowing) they are translated at the boundary.
 * Mouse buttons go through the vanilla MouseHandler on every version.
 *
 * The Stonecutter branches below are written in 26.1-active form (VCS
 * version): the SDL path is commented out until a 26.3 build flips it.
 */
public final class NativeKeys {
    private NativeKeys() {}

    // GLFW code constants (dashboard/config numbering, valid on every version).
    public static final int GLFW_KEY_W = 87;
    public static final int GLFW_KEY_A = 65;
    public static final int GLFW_KEY_S = 83;
    public static final int GLFW_KEY_D = 68;
    public static final int GLFW_KEY_C = 67;
    public static final int GLFW_KEY_K = 75;
    public static final int GLFW_KEY_SPACE = 32;
    public static final int GLFW_KEY_LEFT_SHIFT = 340;
    public static final int GLFW_KEY_RIGHT_SHIFT = 344;
    public static final int GLFW_KEY_LEFT_ALT = 342;
    public static final int GLFW_MOUSE_LEFT = 0;
    public static final int GLFW_MOUSE_RIGHT = 1;
    public static final int GLFW_MOUSE_MIDDLE = 2;

    // GLFW -> SDL translation for 26.3+ (values read from its InputConstants).
    private static final Map<Integer, Integer> FORWARD = new HashMap<>();
    private static final Map<Integer, Integer> BACKWARD = new HashMap<>();

    static {
        for (int i = 0; i < 26; i++) map(65 + i, 4 + i); // A-Z
        map(48, 39); // 0
        for (int i = 1; i <= 9; i++) map(48 + i, 29 + i); // 1-9
        for (int i = 0; i < 12; i++) map(290 + i, 58 + i); // F1-F12
        map(262, 79); // Right
        map(263, 80); // Left
        map(264, 81); // Down
        map(265, 82); // Up
        map(32, 44); // Space
        map(257, 40); // Enter
        map(258, 43); // Tab
        map(259, 42); // Backspace
        map(256, 41); // Escape
        map(280, 57); // CapsLock
        map(260, 73); // Insert
        map(261, 76); // Delete
        map(268, 74); // Home
        map(269, 77); // End
        map(266, 75); // PageUp
        map(267, 78); // PageDown
        map(45, 45); // Minus
        map(61, 46); // Equals
        map(91, 47); // Left bracket
        map(93, 48); // Right bracket
        map(92, 49); // Backslash
        map(59, 51); // Semicolon
        map(39, 52); // Quote
        map(96, 53); // Backquote
        map(44, 54); // Comma
        map(46, 55); // Period
        map(47, 56); // Slash
        map(340, 225); // Left shift
        map(344, 229); // Right shift
        map(341, 224); // Left control
        map(345, 228); // Right control
        map(342, 226); // Left alt
        map(346, 230); // Right alt
        map(343, 227); // Left super
        map(347, 231); // Right super
        map(0, 1); // Mouse left
        map(1, 3); // Mouse right
        map(2, 2); // Mouse middle
        for (var e : FORWARD.entrySet()) BACKWARD.put(e.getValue(), e.getKey());
    }

    private static void map(int glfw, int sdl) {
        FORWARD.put(glfw, sdl);
    }

    /** KeyMapping type for this version (renamed KEYSYM -> KEYBOARD in 26.3). */
    public static InputConstants.Type keyType() {
        try {
            return InputConstants.Type.valueOf("KEYBOARD");
        } catch (IllegalArgumentException e) {
            return InputConstants.Type.valueOf("KEYSYM");
        }
    }

    /** Dashboard (GLFW) code -> native code for this version. */
    public static int toNative(int glfwCode) {
        //? if >=26.3 {
        /*Integer nativeCode = FORWARD.get(glfwCode);
        return nativeCode != null ? nativeCode : glfwCode;
        *///?} else {
        return glfwCode;
        //?}
    }

    /** Native code -> dashboard (GLFW) code. Unknown codes pass through. */
    public static int fromNative(int nativeCode) {
        //? if >=26.3 {
        /*Integer glfwCode = BACKWARD.get(nativeCode);
        return glfwCode != null ? glfwCode : nativeCode;
        *///?} else {
        return nativeCode;
        //?}
    }

    /** Hold-state for a dashboard (GLFW) key code. */
    public static boolean isDown(Minecraft mc, int glfwCode) {
        if (mc == null || glfwCode <= 0) return false;
        try {
            //? if >=26.3 {
            /*return InputConstants.isKeyDown(toNative(glfwCode));
            *///?} else {
            return mc.getWindow() != null
                && org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().handle(), glfwCode)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            //?}
        } catch (Exception e) {
            return false;
        }
    }

    /** Hold-state for a dashboard (GLFW) mouse button (0 = left, 1 = right, 2 = middle). */
    public static boolean isMouseDown(Minecraft mc, int glfwButton) {
        try {
            if (mc == null || mc.mouseHandler == null) return false;
            return switch (glfwButton) {
                case 0 -> mc.mouseHandler.isLeftPressed();
                case 1 -> mc.mouseHandler.isRightPressed();
                case 2 -> mc.mouseHandler.isMiddlePressed();
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }
}
