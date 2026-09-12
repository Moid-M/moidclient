package com.moidclient.network;

import com.moidclient.config.ConfigManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bi-directional WebSocket message handling.
 * Protocol:
 *  - Client -> Server: { type: "UPDATE_MODULE", id, data: {...} }
 *  - Client -> Server: { type: "UPDATE_ACCENT_COLOR", color: "#..." }
 *  - Client -> Server: { type: "EXPORT_CONFIG" } -> server replies with EXPORT_CONFIG
 *  - Client -> Server: { type: "IMPORT_CONFIG", data: {...} }
 *  - Server -> Client: { type: "SYNC_CONFIG", data: { accentColor, modules } }
 */
public class NetworkPackets {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoidClient/WS");
    private final ConfigManager config;
    private final Set<WsContext> sessions = ConcurrentHashMap.newKeySet();
    private volatile JsonObject lastWindowSize = null;

    public NetworkPackets(ConfigManager config) {
        this.config = config;
    }

    public void onConnect(WsContext ctx) {
        sessions.add(ctx);
        LOGGER.info("[MoidClient] WebSocket client connected: {}", ctx.sessionId());
        sendSync(ctx);
        if (lastWindowSize != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", "WINDOW_SIZE");
            payload.add("data", lastWindowSize.deepCopy());
            ctx.send(payload.toString());
        }
    }

    public void onClose(WsContext ctx) {
        sessions.remove(ctx);
        LOGGER.info("[MoidClient] WebSocket client disconnected: {}", ctx.sessionId());
    }

    public void onMessage(WsContext ctx, String message) {
        if (message == null) return;
        if (message.length() > 100_000) {
            LOGGER.warn("[MoidClient] WS message too large ({}), dropped", message.length());
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "";

            switch (type) {
                case "UPDATE_MODULE" -> handleUpdateModule(json);
                case "UPDATE_ACCENT_COLOR" -> handleUpdateAccent(json);
                case "UPDATE_TEXT_COLOR", "UPDATE_THEME_TEXT_COLOR" -> handleUpdateTextColor(json);
                case "EXPORT_CONFIG" -> handleExport(ctx);
                case "IMPORT_CONFIG" -> handleImport(json);
                case "PING" -> ctx.send("{\"type\":\"PONG\"}");
                default -> LOGGER.warn("[MoidClient] Unknown WS type: {}", type);
            }
        } catch (Exception e) {
            String preview = message.length() > 300 ? message.substring(0, 300) + "..." : message;
            LOGGER.error("[MoidClient] Failed to handle WS message: {}", preview, e);
        }
    }

    private void handleUpdateModule(JsonObject json) {
        if (!json.has("id") || !json.has("data")) return;
        String id = json.get("id").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        config.updateModule(id, data);
        broadcastSync();
    }

    private void handleUpdateAccent(JsonObject json) {
        if (!json.has("color")) return;
        String color = json.get("color").getAsString();
        if (color == null || !color.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
            LOGGER.warn("[MoidClient] Invalid accent color ignored: {}", color);
            return;
        }
        config.setAccentColor(color);
        broadcastSync();
    }

    private void handleUpdateTextColor(JsonObject json) {
        if (!json.has("color")) return;
        String color = json.get("color").getAsString();
        if (color == null || !color.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
            LOGGER.warn("[MoidClient] Invalid text color ignored: {}", color);
            return;
        }
        config.setThemeTextColor(color);
        broadcastSync();
    }

    private void handleExport(WsContext ctx) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "EXPORT_CONFIG");
        payload.add("data", config.toJson());
        ctx.send(payload.toString());
    }

    private void handleImport(JsonObject json) {
        if (!json.has("data")) return;
        JsonObject data = json.getAsJsonObject("data");
        // basic size guard: reject >500KB import
        if (data.toString().length() > 512_000) {
            LOGGER.warn("[MoidClient] IMPORT_CONFIG too large, rejected");
            return;
        }
        config.importFromJson(data);
        broadcastSync();
    }

    public void sendSync(WsContext ctx) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "SYNC_CONFIG");
        payload.add("data", config.toJson());
        ctx.send(payload.toString());
    }

    public void broadcastSync() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "SYNC_CONFIG");
        payload.add("data", config.toJson());
        String msg = payload.toString();
        for (WsContext s : sessions) {
            if (s.session.isOpen()) {
                s.send(msg);
            }
        }
    }

    public void broadcastWindowSize(int width, int height, int scaledWidth, int scaledHeight, double guiScale) {
        JsonObject data = new JsonObject();
        data.addProperty("width", width);
        data.addProperty("height", height);
        data.addProperty("scaledWidth", scaledWidth);
        data.addProperty("scaledHeight", scaledHeight);
        data.addProperty("guiScale", guiScale);
        lastWindowSize = data.deepCopy();
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "WINDOW_SIZE");
        payload.add("data", data);
        String msg = payload.toString();
        for (WsContext s : sessions) {
            if (s.session.isOpen()) {
                s.send(msg);
            }
        }
    }

    public void broadcastLiveStats(int ping, int fps, int cpsLeft, int cpsRight, boolean w, boolean a, boolean sKey, boolean d, boolean space, boolean shift, boolean lmb, boolean rmb, JsonObject previews) {
        JsonObject data = new JsonObject();
        data.addProperty("ping", ping);
        data.addProperty("fps", fps);
        data.addProperty("cpsLeft", cpsLeft);
        data.addProperty("cpsRight", cpsRight);
        JsonObject keys = new JsonObject();
        keys.addProperty("w", w);
        keys.addProperty("a", a);
        keys.addProperty("s", sKey);
        keys.addProperty("d", d);
        keys.addProperty("space", space);
        keys.addProperty("shift", shift);
        keys.addProperty("lmb", lmb);
        keys.addProperty("rmb", rmb);
        data.add("keys", keys);
        if (previews != null) data.add("previews", previews);
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "LIVE_STATS");
        payload.add("data", data);
        String msg = payload.toString();
        for (WsContext wsCtx : sessions) {
            if (wsCtx.session.isOpen()) {
                wsCtx.send(msg);
            }
        }
    }
    // legacy overload
    public void broadcastLiveStats(int ping, int fps) {
        broadcastLiveStats(ping, fps, 0, 0, false, false, false, false, false, false, false, false, null);
    }

    public Set<WsContext> getSessions() {
        return sessions;
    }
}
