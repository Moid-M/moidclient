package com.moidclient.utility.statistics;

import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;

/**
 * Statistics - records local performance history (fps/ping/tps/memory plus
 * world join/leave events) for the dashboard Statistics tab. No in-game
 * component; the dashboard card is just the on/off switch. Local only.
 * Category: Utility
 */
public final class StatisticsManager {
    private StatisticsManager() {}

    public static ModuleDef definition() {
        return new ModuleDef("statistics", "Statistics",
                "Records local performance history.",
                "utility", false, "signal", false,
                ModuleOption.list()).withDefaultEnabled(true);
    }
}
