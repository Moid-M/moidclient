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

    private FullbrightManager() {}

    public static ModuleDef definition() {
        return new ModuleDef("fullbright", "Fullbright", "Gamma boost for dark areas - no overlay.", "visuals", false, "sun", false,
            ModuleOption.list(
                ModuleOption.slider("fullbrightGamma", "Brightness", 1, 15, 0.5)
            ));
    }

    public static void onTick(ConfigManager config) {
        try {
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
                try {
                    Field f = null;
                    Class<?> c = opt.getClass();
                    while (c != null) {
                        try { f = c.getDeclaredField("value"); break; } catch (NoSuchFieldException e) { c = c.getSuperclass(); }
                    }
                    if (f != null) {
                        f.setAccessible(true);
                        f.set(opt, target);
                        setDirect = true;
                    }
                } catch (Exception ignored) {}
                if (!setDirect) {
                    try { opt.set(Math.min(target, 1.0)); } catch (Exception ignored) {}
                }
            } else if (originalGamma >= 0) {
                try {
                    Field f = null;
                    Class<?> c = opt.getClass();
                    while (c != null) {
                        try { f = c.getDeclaredField("value"); break; } catch (NoSuchFieldException e) { c = c.getSuperclass(); }
                    }
                    if (f != null) { f.setAccessible(true); f.set(opt, originalGamma); }
                    else opt.set(originalGamma);
                } catch (Exception ex) {
                    try { opt.set(originalGamma); } catch (Exception ignored) {}
                }
                originalGamma = -1;
            }
        } catch (Exception ignored) {}
    }
}
