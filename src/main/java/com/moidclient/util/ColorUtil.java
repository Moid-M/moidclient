package com.moidclient.util;

/**
 * Shared color parsing for HUDs - avoids duplication across Ping/Fps/Cps/Keystrokes.
 */
public final class ColorUtil {
    private ColorUtil() {}

    /**
     * Parses #RRGGBB or #RGB with separate opacity 0..1 into ARGB int.
     * A single leading '#' is optional; embedded/stacked '#' is rejected.
     * NaN opacity falls back to fully opaque (a corrupt config should stay
     * visible, not silently invisible); infinities clamp as usual.
     */
    public static int parseHex(String hex, double opacity) {
        if (hex == null || hex.isEmpty()) throw new IllegalArgumentException("empty hex");
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        if (h.length() != 6) throw new IllegalArgumentException("invalid hex: " + hex);
        int rgb;
        try {
            rgb = Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid hex: " + hex);
        }
        return (alphaOf(opacity) << 24) | (rgb & 0xFFFFFF);
    }

    public static int withOpacity(int rgb, double opacity) {
        return (alphaOf(opacity) << 24) | (rgb & 0xFFFFFF);
    }

    private static int alphaOf(double opacity) {
        if (Double.isNaN(opacity)) return 255;
        return (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
    }

    public static boolean isValidHex(String hex) {
        return hex != null && hex.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    }
}
