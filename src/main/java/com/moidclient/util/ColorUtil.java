package com.moidclient.util;

/**
 * Shared color parsing for HUDs - avoids duplication across Ping/Fps/Cps/Keystrokes.
 */
public final class ColorUtil {
    private ColorUtil() {}

    /**
     * Parses #RRGGBB or #RGB with separate opacity 0..1 into ARGB int.
     */
    public static int parseHex(String hex, double opacity) {
        if (hex == null || hex.isEmpty()) throw new IllegalArgumentException("empty hex");
        String h = hex.replace("#", "").trim();
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        if (h.length() != 6) throw new IllegalArgumentException("invalid hex: " + hex);
        int rgb = Integer.parseInt(h, 16);
        int alpha = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static int withOpacity(int rgb, double opacity) {
        int alpha = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static boolean isValidHex(String hex) {
        return hex != null && hex.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    }
}
