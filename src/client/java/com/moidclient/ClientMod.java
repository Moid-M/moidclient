package com.moidclient;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudManager;
import com.moidclient.hud.cps.CpsHud;
import com.moidclient.module.ModuleRegistry;
import com.moidclient.utility.perspectiveskip.PerspectiveSkipManager;
import com.moidclient.visuals.hitboxes.HitboxRenderer;
import com.moidclient.network.NetworkPackets;
import com.moidclient.server.ServerManager;
import com.moidclient.visuals.blockoutline.BlockOutlineRenderer;
import com.moidclient.visuals.fullbright.FullbrightManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

/**
 * Fabric ClientModInitializer.
 * Registers keybind K to open the Web GUI in the default browser.
 * Starts embedded server on dynamic port 18423-18450.
 */
public class ClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoidClient");
    public static final String MOD_ID = "moidclient";

    private static ClientMod INSTANCE;
    private ConfigManager configManager;
    private NetworkPackets networkPackets;
    private ServerManager serverManager;
    private KeyMapping openGuiKey;
    private KeyMapping zoomKey;
    private KeyMapping freeLookKey;
    private int lastWindowWidth=-1, lastWindowHeight=-1, lastScaledW=-1, lastScaledH=-1;
    private int windowTick=0;
    private int liveTick=0;
    private int keySyncTick=0;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOGGER.info("[MoidClient] Initializing ultra-lightweight utility client...");

        // 1) Config + schema defaults declared in module definitions
        configManager = new ConfigManager();
        try { ModuleRegistry.applyOptionDefaults(configManager); } catch (Exception e) { LOGGER.error("[MoidClient] Failed to apply option defaults", e); }
        networkPackets = new NetworkPackets(configManager);
        // 1b) HUD (ping display etc) - register before server
        try { HudManager.init(configManager); } catch (Exception e) { LOGGER.error("[MoidClient] Failed to init HUD", e); }
        try { BlockOutlineRenderer.register(configManager); } catch (Exception e) { LOGGER.error("[MoidClient] Failed to init Block Outline", e); }
        try { HitboxRenderer.register(configManager); } catch (Exception e) { LOGGER.error("[MoidClient] Failed to init Hitboxes", e); }

        // 2) Server (dynamic port binding + asset serving + WS)
        serverManager = new ServerManager(configManager, networkPackets, ModuleRegistry::toJson);
        try {
            serverManager.start();
        } catch (Exception e) {
            LOGGER.error("[MoidClient] Failed to start Web GUI server", e);
        }

        // 3) Keybind registration - dashboard key plus module hold-keys.
        // Zoom/FreeLook live in vanilla Controls (rebindable in-game) AND in
        // the dashboard (rebindable there): managers sync the dashboard value
        // into the vanilla mapping every tick, vanilla persists it.
        // Codes go through NativeKeys so 26.3 (SDL) gets translated values.
        KeyMapping.Category moidCategory = KeyMapping.Category.register(Identifier.parse("moidclient:main"));
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.moidclient.openGui",
                com.moidclient.utility.keybind.NativeKeys.keyType(),
                com.moidclient.utility.keybind.NativeKeys.toNative(
                    com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_K),
                moidCategory
        ));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.moidclient.zoom",
                com.moidclient.utility.keybind.NativeKeys.keyType(),
                com.moidclient.utility.keybind.NativeKeys.toNative(
                    com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_C),
                moidCategory
        ));
        freeLookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.moidclient.freelook",
                com.moidclient.utility.keybind.NativeKeys.keyType(),
                com.moidclient.utility.keybind.NativeKeys.toNative(
                    com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_LEFT_ALT),
                moidCategory
        ));
        // Config (dashboard) is master on boot: these vanilla mappings were
        // just constructed with defaults — client entrypoints run before
        // GameOptions loads options.txt — so push the saved codes in.
        // Reading the live defaults back here would wipe custom binds on
        // every restart. Controls rebinds during play are still adopted by
        // the per-second reverse sync below.
        try {
            var zoomMod = configManager.getModule("zoom");
            if (zoomMod != null) com.moidclient.utility.keybind.Keybinds.adoptConfig(zoomKey, zoomMod.zoomKey);
            var freelookMod = configManager.getModule("freelook");
            if (freelookMod != null) com.moidclient.utility.keybind.Keybinds.adoptConfig(freeLookKey, freelookMod.freelookKey);
            configManager.save();
        } catch (Exception e) { LOGGER.error("[MoidClient] Failed to adopt keybinds", e); }

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            try { PerspectiveSkipManager.onTick(client, configManager); } catch (Exception e) { LOGGER.error("[MoidClient] PerspectiveSkip tick failed", e); }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey != null) while (openGuiKey.consumeClick()) {
                openWebGui();
            }
            // window size broadcast for HUD editor rectangle preview (throttled 10 ticks)
            if (++windowTick % 10 == 0 && client != null && client.getWindow() != null) {
                try {
                    var win = client.getWindow();
                    int w = win.getWidth();
                    int h = win.getHeight();
                    int sw = win.getGuiScaledWidth();
                    int sh = win.getGuiScaledHeight();
                    double scale = win.getGuiScale();
                    if (w != lastWindowWidth || h != lastWindowHeight || sw != lastScaledW || sh != lastScaledH) {
                        lastWindowWidth = w; lastWindowHeight = h; lastScaledW = sw; lastScaledH = sh;
                        networkPackets.broadcastWindowSize(w, h, sw, sh, scale);
                    }
                } catch (Exception ignored) {}
            }
            // cps tracking (every tick) with logging
            try { CpsHud.onTick(); } catch (Exception e) { LOGGER.error("[MoidClient] Cps tick failed", e); }
            try { FullbrightManager.onTick(configManager); } catch (Exception e) { LOGGER.error("[MoidClient] Fullbright tick failed", e); }
            try { com.moidclient.utility.zoom.ZoomManager.onTick(client, configManager, zoomKey); } catch (Exception e) { LOGGER.error("[MoidClient] Zoom tick failed", e); }
            try { com.moidclient.utility.freelook.FreeLookManager.onTick(client, configManager, freeLookKey); } catch (Exception e) { LOGGER.error("[MoidClient] FreeLook tick failed", e); }
            try { com.moidclient.utility.togglesprint.ToggleSprintManager.onTick(client, configManager); } catch (Exception e) { LOGGER.error("[MoidClient] ToggleSprint tick failed", e); }
            try { com.moidclient.utility.togglesneak.ToggleSneakManager.onTick(client, configManager); } catch (Exception e) { LOGGER.error("[MoidClient] ToggleSneak tick failed", e); }
            try { com.moidclient.utility.autohide.AutohideManager.onTick(client, configManager); } catch (Exception e) { LOGGER.error("[MoidClient] Autohide tick failed", e); }
            // reverse keybind sync (Controls -> dashboard), throttled 1s:
            // adopt live bindings changed in-game so the dashboard follows.
            if (++keySyncTick % 20 == 0) {
                try { syncKeybindsToDashboard(); } catch (Exception e) { LOGGER.error("[MoidClient] Keybind sync failed", e); }
            }
            // live stats for editor - throttled 20 ticks (1s) to keep WS stable
            // only poll/broadcast when at least one WS client is connected
            if (++liveTick % 20 == 0 && !networkPackets.getSessions().isEmpty()) {
                try {
                    int ping = HudManager.getCurrentPing();
                    int fps = HudManager.getCurrentFps();
                    int cpsL = 0, cpsR = 0;
                    boolean w=false,a=false,s=false,d=false,space=false,shift=false,lmb=false,rmb=false;
                    try { cpsL = CpsHud.getLeftCps(); cpsR = CpsHud.getRightCps(); } catch (Exception e) { LOGGER.warn("[MoidClient] cps get failed", e); }
                    try {
                        var mc = Minecraft.getInstance();
                        w = com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_W);
                        a = com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_A);
                        s = com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_S);
                        d = com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_D);
                        space = com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_SPACE);
                        shift = com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_LEFT_SHIFT)
                            || com.moidclient.utility.keybind.NativeKeys.isDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_RIGHT_SHIFT);
                        lmb = com.moidclient.utility.keybind.NativeKeys.isMouseDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_MOUSE_LEFT);
                        rmb = com.moidclient.utility.keybind.NativeKeys.isMouseDown(mc, com.moidclient.utility.keybind.NativeKeys.GLFW_MOUSE_RIGHT);
                    } catch (Exception e) { LOGGER.warn("[MoidClient] key poll failed", e); }
                    com.moidclient.module.LiveStats stats = new com.moidclient.module.LiveStats(
                            ping, fps, cpsL, cpsR, CpsHud.getPeakLeft(), CpsHud.getPeakRight(),
                            w, a, s, d, space, shift, lmb, rmb,
                            com.moidclient.hud.tps.TpsHud.getCurrentTps());
                    com.google.gson.JsonObject previews = com.moidclient.module.ModuleRegistry.previews(configManager, stats);
                    networkPackets.broadcastLiveStats(ping, fps, cpsL, cpsR, w, a, s, d, space, shift, lmb, rmb, previews);
                } catch (Exception e) { LOGGER.error("[MoidClient] live stats broadcast failed", e); }
            }
        });
        // schedule initial window size probe without raw Thread - use tick counter
        // will be sent on next tick where window is available (handled via windowTick counter)
        String baseUrl = serverManager.getActivePort() == -1 ? "server failed to start (see log)" : serverManager.getBaseUrl();
        LOGGER.info("[MoidClient] Initialized. Press [K] to open Web Dashboard at {}", baseUrl);
    }

    public void openWebGui() {
        if (serverManager == null || serverManager.getActivePort() == -1) {
            LOGGER.warn("[MoidClient] Server not started, cannot open GUI");
            return;
        }
        String url = serverManager.getBaseUrl();
        LOGGER.info("[MoidClient] Opening Web GUI: {}", url);
        try {
            // Spec requirement: Util.getOperatingSystem().open("http://localhost:<PORT>")
            // Try Mojang Util via reflection to stay compatible across 26.1 mappings
            boolean opened = tryOpenViaMinecraftUtil(url);
            if (!opened) {
                // Fallback to Desktop
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                } else {
                    // OS-specific fallback
                    String os = System.getProperty("os.name").toLowerCase();
                    // use ProcessBuilder with args array to avoid shell injection
                    if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                    else if (os.contains("mac")) new ProcessBuilder("open", url).start();
                    else new ProcessBuilder("xdg-open", url).start();
                }
            }
        } catch (Exception e) {
            LOGGER.error("[MoidClient] Failed to open browser", e);
        }
    }

    /**
     * Attempts to call net.minecraft.util.Util.getOperatingSystem().open(url)
     * and also new Mojang mapping net.minecraft.util.Util.OS / getPlatform().openUri
     * via reflection to avoid compile-time mapping breakage.
     */
    private boolean tryOpenViaMinecraftUtil(String url) {
        try {
            // Try old Yarn: net.minecraft.util.Util.getOperatingSystem().open(url)
            Class<?> utilClass = Class.forName("net.minecraft.util.Util");
            try {
                var m = utilClass.getMethod("getOperatingSystem");
                Object os = m.invoke(null);
                var open = os.getClass().getMethod("open", String.class);
                open.invoke(os, url);
                return true;
            } catch (NoSuchMethodException ignored) {}

            // Try Mojang 26.1: Util.getPlatform().openUri(String) or OS.openUri
            try {
                var m2 = utilClass.getMethod("getPlatform");
                Object os2 = m2.invoke(null);
                var open2 = os2.getClass().getMethod("openUri", String.class);
                open2.invoke(os2, url);
                return true;
            } catch (NoSuchMethodException ignored) {}

            try {
                var m3 = utilClass.getMethod("getOperatingSystem");
                Object os3 = m3.invoke(null);
                var open3 = os3.getClass().getMethod("openUri", String.class);
                open3.invoke(os3, url);
                return true;
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            LOGGER.debug("[MoidClient] Util.open fallback failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Reverse keybind sync: if a binding was changed in Controls, adopt the
     * live value into the config and push it to the dashboard. The push
     * tracker is informed so the forward path doesn't fight it back.
     * Live codes are translated to dashboard numbering first.
     */
    private void syncKeybindsToDashboard() {
        try {
            boolean changed = false;
            var zoomMod = configManager.getModule("zoom");
            if (zoomMod != null && zoomKey != null) {
                int live = com.moidclient.util.KeybindUtil.readCode(zoomKey);
                int code = live > 0 ? com.moidclient.utility.keybind.NativeKeys.fromNative(live) : -1;
                if (code > 0 && code != zoomMod.zoomKey) {
                    zoomMod.zoomKey = code;
                    com.moidclient.utility.keybind.Keybinds.noteApplied(zoomKey, code);
                    changed = true;
                }
            }
            var freelookMod = configManager.getModule("freelook");
            if (freelookMod != null && freeLookKey != null) {
                int live = com.moidclient.util.KeybindUtil.readCode(freeLookKey);
                int code = live > 0 ? com.moidclient.utility.keybind.NativeKeys.fromNative(live) : -1;
                if (code > 0 && code != freelookMod.freelookKey) {
                    freelookMod.freelookKey = code;
                    com.moidclient.utility.keybind.Keybinds.noteApplied(freeLookKey, code);
                    changed = true;
                }
            }
            if (changed) {
                configManager.save();
                networkPackets.broadcastSync();
            }
        } catch (Exception ignored) {}
    }

    public static ClientMod getInstance() { return INSTANCE; }
    public ConfigManager getConfigManager() { return configManager; }
    public ServerManager getServerManager() { return serverManager; }
}

