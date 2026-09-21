package com.moidclient.utility.togglesprint;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import net.minecraft.client.Minecraft;

/**
 * ToggleSprint - drives the vanilla toggle-sprint preference while enabled
 * and restores the player's own value afterwards. Vanilla owns every edge
 * case (screens, focus, respawn); this module is just a dashboard switch
 * for it. Mirrors ToggleSneakManager one to one.
 * Category: Utility
 */
public final class ToggleSprintManager {
    private ToggleSprintManager() {}

    private static boolean enforced = false;
    private static boolean savedValue = false;

    public static ModuleDef definition() {
        return new ModuleDef("toggleSprint", "ToggleSprint",
                "Uses the vanilla toggle-sprint setting while enabled.",
                "utility", false, "activity", false,
                ModuleOption.list());
    }

    public static void onTick(Minecraft mc, ConfigManager config) {
        try {
            if (mc == null || mc.options == null) return;
            var toggle = mc.options.toggleSprint();
            if (toggle == null) return;
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("toggleSprint") : null;
            if (mod == null) {
                // config wiped mid-session: never leave vanilla stuck on.
                if (enforced) {
                    enforced = false;
                    toggle.set(savedValue);
                }
                return;
            }
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
