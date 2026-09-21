package com.moidclient.network;

import com.moidclient.config.ConfigManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
    /** Runs after an import is applied (lets the caller converge defaults). */
    private volatile Runnable onImport = null;

    public void setOnImport(Runnable r) {
        this.onImport = r;
    }
    // Flood guard: sustained full-serialize + broadcast per message is cheap
    // for a dashboard but not for a tight loop. Burst 30/s per session.
    private static final int RATE_MAX_PER_SEC = 30;
    private final Map<String, long[]> rateWindows = new ConcurrentHashMap<>();

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
        rateWindows.remove(ctx.sessionId());
        LOGGER.info("[MoidClient] WebSocket client disconnected: {}", ctx.sessionId());
    }

    public void onMessage(WsContext ctx, String message) {
        if (message == null) return;
        if (message.length() > 512_000) {
            LOGGER.warn("[MoidClient] WS message too large ({}), dropped", message.length());
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "";
            // Only IMPORT_CONFIG may be large (full config); everything else
            // is a small patch and anything bigger is a flood, not a slider.
            if (!"IMPORT_CONFIG".equals(type) && message.length() > 100_000) {
                LOGGER.warn("[MoidClient] WS {} too large ({}), dropped", type, message.length());
                return;
            }
            if (!checkRate(ctx, "PREVIEW_MODULE".equals(type) ? 60 : RATE_MAX_PER_SEC)) {
                LOGGER.warn("[MoidClient] WS flood from {}, patch dropped", ctx.sessionId());
                return;
            }

            switch (type) {
                case "UPDATE_MODULE" -> handleUpdateModule(json, true, true);
                case "PREVIEW_MODULE" -> handleUpdateModule(json, false, false);
                case "UPDATE_ACCENT_COLOR" -> handleUpdateAccent(json);
                case "UPDATE_TEXT_COLOR", "UPDATE_THEME_TEXT_COLOR" -> handleUpdateTextColor(json);
                case "EXPORT_CONFIG" -> handleExport(ctx);
                case "IMPORT_CONFIG" -> handleImport(json);
                case "PING" -> ctx.send("{\"type\":\"PONG\"}");
                default -> LOGGER.warn("[MoidClient] Unknown WS type: {}", type);
            }
        } catch (Exception e) {
            LOGGER.error("[MoidClient] Failed to handle WS message ({} chars)", message.length(), e);
        }
    }

    /** Fixed-window per-session throttle; bursty dashboards fit, tight loops don't. */
    private boolean checkRate(WsContext ctx, int max) {
        long now = System.currentTimeMillis();
        long[] slot = rateWindows.computeIfAbsent(ctx.sessionId(), k -> new long[]{now, 0});
        synchronized (slot) {
            if (now - slot[0] > 1000) {
                slot[0] = now;
                slot[1] = 0;
            }
            if (slot[1] >= max) return false;
            slot[1]++;
            return true;
        }
    }

    // Previews skip the broadcast too: the game reads live memory and the
    // sending dashboard mutated its own copy optimistically, so there is no
    // viewer to update - and no 60Hz DOM reconciliation storm. The release
    // sends a normal saving update that converges every view.
    private void handleUpdateModule(JsonObject json, boolean save, boolean broadcast) {
        if (!json.has("id") || !json.has("data")) return;
        String id = json.get("id").getAsString();
        JsonObject data = json.getAsJsonObject("data");
        config.updateModule(id, data, save);
        if (broadcast) broadcastSync();
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
        config.importFromJson(data);
        try {
            if (onImport != null) onImport.run();
        } catch (Exception e) {
            LOGGER.warn("[MoidClient] Post-import hook failed", e);
        }
        broadcastSync();
    }

    /** Send to one session; a dead session is dropped instead of breaking the broadcast loop. */
    private boolean sendSafe(WsContext ctx, String msg) {
        try {
            if (ctx.session.isOpen()) {
                ctx.send(msg);
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("[MoidClient] WS send failed, dropping session {}", ctx.sessionId());
        }
        sessions.remove(ctx);
        return false;
    }

    public void sendSync(WsContext ctx) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "SYNC_CONFIG");
        payload.add("data", config.toJson());
        sendSafe(ctx, payload.toString());
    }

    public void broadcastSync() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "SYNC_CONFIG");
        payload.add("data", config.toJson());
        String msg = payload.toString();
        for (WsContext s : sessions.toArray(new WsContext[0])) {
            sendSafe(s, msg);
        }
    }

    public void broadcastWindowSize(int width, int height, int scaledWidth, int scaledHeight, double guiScale) {        JsonObject data = new JsonObject();
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
        for (WsContext s : sessions.toArray(new WsContext[0])) {
            sendSafe(s, msg);
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
        for (WsContext wsCtx : sessions.toArray(new WsContext[0])) {
            sendSafe(wsCtx, msg);
        }
    }
    /** Pushes updater progress to every dashboard (phase/received/total). */
    public void broadcastUpdateStatus(JsonObject data) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", "UPDATE_STATUS");
            payload.add("data", data != null ? data : new JsonObject());
            String msg = payload.toString();
            for (WsContext s : sessions.toArray(new WsContext[0])) {
                sendSafe(s, msg);
            }
        } catch (Exception ignored) {}
    }

    public Set<WsContext> getSessions() {
        return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(sessions));
    }
}
