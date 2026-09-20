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
import com.moidclient.hud.session.SessionTimerHud;
import com.moidclient.hud.potions.PotionEffectsHud;
import com.moidclient.hud.armor.ArmorStatusHud;
import com.moidclient.hud.combo.ComboHud;
import com.moidclient.hud.memory.MemoryHud;
import com.moidclient.hud.reach.ReachHud;
import com.moidclient.hud.tps.TpsHud;
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
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "tps_hud"), (graphics, deltaTracker) -> TpsHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "cps_hud"), (graphics, deltaTracker) -> CpsHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "keystrokes_hud"), (graphics, deltaTracker) -> KeystrokesHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "coords_hud"), (graphics, deltaTracker) -> CoordinatesHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "server_hud"), (graphics, deltaTracker) -> ServerHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "clock_hud"), (graphics, deltaTracker) -> ClockHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "biome_hud"), (graphics, deltaTracker) -> BiomeHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "session_hud"), (graphics, deltaTracker) -> SessionTimerHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "potions_hud"), (graphics, deltaTracker) -> PotionEffectsHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "armor_hud"), (graphics, deltaTracker) -> ArmorStatusHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "combo_hud"), (graphics, deltaTracker) -> ComboHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "reach_hud"), (graphics, deltaTracker) -> ReachHud.render(graphics, deltaTracker, config));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("moidclient", "memory_hud"), (graphics, deltaTracker) -> MemoryHud.render(graphics, deltaTracker, config));
    }

    public static int getCurrentPing() { return PingHud.getCurrentPing(); }
    public static int getCurrentFps() { return FpsHud.getCurrentFps(); }
    public static double getCurrentTps() { return TpsHud.getCurrentTps(); }

    /** In-game text size in MC pixels, matching what renderers draw. */
    public static int[] measureText(String text, boolean background) {
        try {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            int pad = background ? 6 : 0;
            return new int[]{font.width(text) + pad, 9 + pad};
        } catch (Exception e) {
            int w = text != null ? text.length() * 6 : 60;
            return new int[]{w, 9};
        }
    }
}
