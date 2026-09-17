package com.moidclient.visuals.fullbright;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;

/**
 * Fullbright - gamma boost for dark areas.
 * Category: Visuals
 */
public final class FullbrightManager {
    private static double originalGamma = -1;
    // Cached reflection for the option's raw value field (resolved once per
    // option class instead of walked + setAccessible on every tick).
    private static Field cachedValueField = null;
    private static Class<?> cachedValueClass = null;

    private FullbrightManager() {}

    /** Raw value field of a vanilla option, or null if unresolvable. */
    private static Field valueFieldOf(Object opt) {
        if (opt == null) return null;
        Class<?> c = opt.getClass();
        if (cachedValueField != null && c == cachedValueClass) return cachedValueField;
        Field f = null;
        Class<?> cur = c;
        while (cur != null) {
            try { f = cur.getDeclaredField("value"); break; } catch (NoSuchFieldException e) { cur = cur.getSuperclass(); }
        }
        if (f != null) {
            try { f.setAccessible(true); } catch (Exception ignored) {}
        }
        cachedValueField = f;
        cachedValueClass = c;
        return f;
    }

    public static ModuleDef definition() {
        return new ModuleDef("fullbright", "Fullbright", "Gamma boost for dark areas - no overlay.", "visuals", false, "sun", false,
            ModuleOption.list(
                ModuleOption.slider("fullbrightGamma", "Brightness", 1, 15, 0.5)
            ));
    }

    public static void onTick(ConfigManager config) {        try {
            if (config == null) return;
            var fb = config.getModule("fullbright");
            var mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;
            var opt = mc.options.gamma();
            if (fb != null && fb.enabled) {
                double target = fb.fullbrightGamma;
                if (target < 1) target = 12; if (target > 15) target = 15;
                if (originalGamma < 0) originalGamma = opt.get();
                boolean setDirect = false;
                Field f = valueFieldOf(opt);
                if (f != null) {
                    try { f.set(opt, target); setDirect = true; } catch (Exception ignored) {}
                }
                if (!setDirect) {
                    try { opt.set(Math.min(target, 1.0)); } catch (Exception ignored) {}
                }
            } else if (originalGamma >= 0) {
                Field f = valueFieldOf(opt);
                try {
                    if (f != null) f.set(opt, originalGamma);
                    else opt.set(originalGamma);
                } catch (Exception ex) {
                    try { opt.set(originalGamma); } catch (Exception ignored) {}
                }
                originalGamma = -1;
            }
        } catch (Exception ignored) {}
    }

    /** Writes a raw gamma straight to the option field (bypasses validation). */
    public static void writeGammaRaw(double value) {
        try {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;
            var opt = mc.options.gamma();
            Field f = valueFieldOf(opt);
            if (f == null) return;
            f.set(opt, value);
        } catch (Exception ignored) {}
    }

    /**
     * Called at the head of Options.save: park a legal value so vanilla can
     * serialize (our boosted gamma fails validation and spams the log).
     */
    public static void onSaveStart() {
        if (originalGamma < 0) return;
        writeGammaRaw(Math.max(0.0, Math.min(1.0, originalGamma)));
    }
}
