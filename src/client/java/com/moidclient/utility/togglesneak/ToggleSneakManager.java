package com.moidclient.utility.togglesneak;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import net.minecraft.client.Minecraft;

/**
 * ToggleSneak - drives the vanilla toggle-sneak preference while enabled and
 * restores the player's own value afterwards. Vanilla owns every edge case
 * (screens, focus, respawn); this module is just a dashboard switch for it.
 * Category: Utility
 */
public final class ToggleSneakManager {
    private ToggleSneakManager() {}

    private static boolean enforced = false;
    private static boolean savedValue = false;

    public static ModuleDef definition() {
        return new ModuleDef("toggleSneak", "ToggleSneak",
                "Uses the vanilla toggle-sneak setting while enabled.",
                "utility", false, "switch", false,
                ModuleOption.list());
    }

    public static void onTick(Minecraft mc, ConfigManager config) {
        try {
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("toggleSneak") : null;
            if (mc == null || mc.options == null || mod == null) return;
            var toggle = mc.options.toggleCrouch();
            if (toggle == null) return;
            if (mod.enabled) {
                if (!enforced) {
                    enforced = true;
                    savedValue = toggle.get();
                }
                if (!toggle.get()) toggle.set(true);
            } else if (enforced) {
                enforced = false;
                toggle.set(savedValue);
            }
        } catch (Exception ignored) {}
    }
}
