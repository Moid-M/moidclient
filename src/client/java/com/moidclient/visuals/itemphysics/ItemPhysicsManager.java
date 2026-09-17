package com.moidclient.visuals.itemphysics;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;

/**
 * Item Physics - client-side drop animations. Flying items spin, grounded
 * items lie flat. Pure render transform, no packets, server never knows.
 * Actual rotation is applied by ItemEntityRendererMixin from per-frame
 * entity state (see ItemPhysicsState).
 * Category: Visuals
 */
public final class ItemPhysicsManager {
    private ItemPhysicsManager() {}

    public static ModuleDef definition() {
        return new ModuleDef("itemPhysics", "Item Physics",
                "Dropped items spin in air and lie flat on ground.",
                "visuals", false, "box", false,
                ModuleOption.list(
                    ModuleOption.slider("itemTumbleSpeed", "Spin speed", 0.02, 0.6, 0.01, 0.25)
                ));
    }

    public static boolean isEnabled() {
        try {
            var config = com.moidclient.ClientMod.getInstance().getConfigManager();
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("itemPhysics") : null;
            return mod != null && mod.enabled;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static double tumbleSpeed() {
        try {
            var config = com.moidclient.ClientMod.getInstance().getConfigManager();
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("itemPhysics") : null;
            if (mod == null) return 0.25;
            double v = mod.optDouble("itemTumbleSpeed", 0.25);
            if (v == 0) v = 0.25;
            return Math.max(0.02, Math.min(0.6, v));
        } catch (Exception ignored) {
            return 0.25;
        }
    }
}
