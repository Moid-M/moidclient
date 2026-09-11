package com.moidclient;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudManager;
import com.moidclient.hud.cps.CpsHud;
import com.moidclient.network.NetworkPackets;
import com.moidclient.server.ServerManager;
import com.moidclient.visuals.fullbright.FullbrightManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

/**
 * Fabric ClientModInitializer.
 * Registers keybind K / Right Shift to open Web GUI in default browser.
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
    private int lastWindowWidth=-1, lastWindowHeight=-1, lastScaledW=-1, lastScaledH=-1;
    private int windowTick=0;
    private int liveTick=0;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOGGER.info("[MoidClient] Initializing ultra-lightweight utility client...");

        // 1) Config
        configManager = new ConfigManager();
        networkPackets = new NetworkPackets(configManager);
        // 1b) HUD (ping display etc) - register before server
        try { HudManager.init(configManager); } catch (Exception e) { LOGGER.error("[MoidClient] Failed to init HUD", e); }

        // 2) Server (dynamic port binding + asset serving + WS)
        serverManager = new ServerManager(configManager, networkPackets);
        try {
            serverManager.start();
        } catch (Exception e) {
            LOGGER.error("[MoidClient] Failed to start Web GUI server", e);
        }

        // 3) Keybind registration - default K (also allow Right Shift via second binding fallback)
        // 26.1: KeyMappingHelper.registerKeyMapping + InputConstants (Mojang mappings)
        // For 1.21.1 fallback use: KeyBindingHelper.registerKeyBinding + InputUtil.Type.KEYSYM
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.moidclient.openGui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyMapping.Category.register(Identifier.parse("moidclient:main"))
        ));

        // Also register Right Shift as alternative if user prefers - use event to listen both
        // GLFW_RIGHT_SHIFT = 344, but InputUtil handles it

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
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
                        var win = Minecraft.getInstance().getWindow();
                        if (win != null) {
                            long h = win.handle();
                            w = org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_W) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            a = org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            s = org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_S) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            d = org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_D) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            space = org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            shift = org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS || org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            lmb = org.lwjgl.glfw.GLFW.glfwGetMouseButton(h, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            rmb = org.lwjgl.glfw.GLFW.glfwGetMouseButton(h, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                        }
                    } catch (Exception e) { LOGGER.warn("[MoidClient] key poll failed", e); }
                    networkPackets.broadcastLiveStats(ping, fps, cpsL, cpsR, w, a, s, d, space, shift, lmb, rmb);
                } catch (Exception e) { LOGGER.error("[MoidClient] live stats broadcast failed", e); }
            }
        });
        // schedule initial window size probe without raw Thread - use tick counter
        // will be sent on next tick where window is available (handled via windowTick counter)
        LOGGER.info("[MoidClient] Initialized. Press [K] to open Web Dashboard at {}", serverManager.getBaseUrl());
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

            // Try enum OS field
            for (var f : utilClass.getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("OperatingSystem") || f.getType().getSimpleName().equals("OS")) {
                    // static instance
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[MoidClient] Util.open fallback failed: {}", e.getMessage());
        }
        return false;
    }

    public static ClientMod getInstance() { return INSTANCE; }
    public ConfigManager getConfigManager() { return configManager; }
    public ServerManager getServerManager() { return serverManager; }
}

