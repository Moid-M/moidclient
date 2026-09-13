package com.moidclient.hud;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.biome.BiomeHud;
import com.moidclient.hud.clock.ClockHud;
import com.moidclient.hud.coords.CoordinatesHud;
import com.moidclient.hud.cps.CpsHud;
import com.moidclient.hud.fps.FpsHud;
import com.moidclient.hud.keystrokes.KeystrokesHud;
import com.moidclient.hud.ping.PingHud;
import com.moidclient.hud.server.ServerHud;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/**
 * Central HUD registry - delegates to categorized HUDs under /hud/*.
 * Categories: /hud/ping, /hud/fps, /hud/cps, /visuals/fullbright, /customization etc.
 */
public final class HudManager {
    private static ConfigManager config;

    private HudManager() {}

    public static void init(ConfigManager cfg) {
        config = cfg;
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "ping_hud"), (graphics, deltaTracker) -> PingHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "fps_hud"), (graphics, deltaTracker) -> FpsHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "cps_hud"), (graphics, deltaTracker) -> CpsHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "keystrokes_hud"), (graphics, deltaTracker) -> KeystrokesHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "coords_hud"), (graphics, deltaTracker) -> CoordinatesHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "server_hud"), (graphics, deltaTracker) -> ServerHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "clock_hud"), (graphics, deltaTracker) -> ClockHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "biome_hud"), (graphics, deltaTracker) -> BiomeHud.render(graphics, deltaTracker, config));
    }

    public static int getCurrentPing() { return PingHud.getCurrentPing(); }
    public static int getCurrentFps() { return FpsHud.getCurrentFps(); }
}
