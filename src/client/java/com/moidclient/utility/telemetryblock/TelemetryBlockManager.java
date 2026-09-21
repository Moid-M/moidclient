package com.moidclient.utility.telemetryblock;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;

/**
 * Telemetry Block - forces vanilla's telemetry master switch off while
 * enabled (on by default). Vanilla then hands out its own DISABLED sender
 * for every world session and menu ping, so analytics events never leave
 * the PC. Functional Mojang traffic (logins, skins, servers) is untouched -
 * only the telemetry sender goes quiet. Local event logs still work.
 * Category: Utility
 */
public final class TelemetryBlockManager {
    private TelemetryBlockManager() {}

    public static ModuleDef definition() {
        return new ModuleDef("telemetryBlock", "Block Mojang Telemetry",
                "Blocks Mojang telemetry events (on by default).",
                "utility", false, "shield", false,
                ModuleOption.list()).withDefaultEnabled(true);
    }

    /**
     * Fail-closed: before config loads, block (matches the default-on).
     * Note: vanilla memoizes the outside-session sender on first use, so a
     * mid-session disable/re-enable cycle fully settles on world (re)join;
     * world-session senders are always fresh.
     */
    public static boolean isEnabled() {
        try {
            var instance = com.moidclient.ClientMod.getInstance();
            if (instance == null) return true;
            var config = instance.getConfigManager();
            if (config == null) return true;
            ConfigManager.ModuleConfig mod = config.getModule("telemetryBlock");
            if (mod == null) return true;
            return mod.enabled;
        } catch (Exception ignored) {
            return true;
        }
    }
}
