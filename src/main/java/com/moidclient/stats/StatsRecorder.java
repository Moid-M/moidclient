package com.moidclient.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Local performance history for the dashboard Statistics tab. One session
 * per game launch, 1Hz samples (fps/ping/tps/memory MB) plus join/leave
 * events with world/server labels. Single JSON file next to the config,
 * atomically written, capped to the newest sessions. Local only - nothing
 * here ever leaves the PC.
 */
public final class StatsRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoidClient/Stats");
    private static final Gson GSON = new GsonBuilder().create();
    private static final String FILE_NAME = "moidclient-stats.json";
    private static final int MAX_SESSIONS = 10;
    private static final long SAVE_INTERVAL_MS = 30_000;

    private final File file;
    private final List<JsonObject> sessions = new ArrayList<>();
    private JsonObject current;
    private Object lastLevel;
    private Object lastConn;
    private String lastLabel;
    private long lastSaveMs;

    public StatsRecorder() {
        this(new File(FabricLoader.getInstance().getConfigDir().toFile(), FILE_NAME));
    }

    /** For testing / headless without FabricLoader. */
    public StatsRecorder(File file) {
        this.file = file;
        load();
        beginSession();
    }

    public synchronized void recordSample(int fps, int ping, double tps, long memMb) {
        if (current == null) return;
        try {
            JsonObject s = new JsonObject();
            s.addProperty("t", System.currentTimeMillis());
            s.addProperty("fps", fps);
            s.addProperty("ping", ping);
            s.addProperty("tps", Math.max(0, Math.min(20, tps)));
            s.addProperty("mem", Math.max(0, memMb));
            current.getAsJsonArray("samples").add(s);
        } catch (Exception ignored) {}
    }

    /**
     * Tracks world/server presence. Fires leave+join events on change;
     * label is a display string (server IP, Realms, LAN, Singleplayer).
     */
    public synchronized void noteContext(Object level, Object conn, String label) {
        if (current == null) return;
        try {
            if (level == lastLevel && conn == lastConn) return;
            long now = System.currentTimeMillis();
            if (lastLevel != null || lastConn != null) {
                addEvent(now, "leave", lastLabel != null ? lastLabel : "unknown");
            }
            lastLevel = level;
            lastConn = conn;
            lastLabel = label;
            if (level != null || conn != null) {
                addEvent(now, "join", label != null ? label : "unknown");
            }
        } catch (Exception ignored) {}
    }

    public synchronized void maybeSave() {
        long now = System.currentTimeMillis();
        if (now - lastSaveMs >= SAVE_INTERVAL_MS) {
            lastSaveMs = now;
            saveLocked();
        }
    }

    /** Wipes all history and starts a fresh current session. */
    public synchronized void clear() {
        try {
            sessions.clear();
            lastLevel = null;
            lastConn = null;
            lastLabel = null;
            beginSession();
            lastSaveMs = System.currentTimeMillis();
            saveLocked();
        } catch (Exception e) {
            LOGGER.warn("[MoidClient] Stats clear failed", e);
        }
    }

    public synchronized void saveAndClose() {
        try {
            if (current != null) current.addProperty("endedAt", System.currentTimeMillis());
        } catch (Exception ignored) {}
        lastSaveMs = System.currentTimeMillis();
        saveLocked();
        lastLevel = null;
        lastConn = null;
    }

    /** Full history copy for GET /api/stats. */
    public synchronized JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("version", 1);
        JsonArray arr = new JsonArray();
        for (JsonObject s : sessions) {
            try {
                arr.add(GSON.fromJson(GSON.toJson(s), JsonObject.class));
            } catch (Exception ignored) {}
        }
        out.add("sessions", arr);
        return out;
    }

    private void addEvent(long now, String type, String where) {
        try {
            JsonObject e = new JsonObject();
            e.addProperty("t", now);
            e.addProperty("type", type);
            e.addProperty("where", where);
            current.getAsJsonArray("events").add(e);
        } catch (Exception ignored) {}
    }

    private void beginSession() {
        try {
            current = new JsonObject();
            current.addProperty("id", "s" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
            current.addProperty("startedAt", System.currentTimeMillis());
            current.addProperty("endedAt", 0);
            current.add("events", new JsonArray());
            current.add("samples", new JsonArray());
            sessions.add(current);
            pruneLocked();
        } catch (Exception e) {
            LOGGER.warn("[MoidClient] Stats session start failed", e);
        }
    }

    private void pruneLocked() {
        try {
            while (sessions.size() > MAX_SESSIONS) sessions.remove(0);
        } catch (Exception ignored) {}
    }

    private void load() {
        try {
            sessions.clear();
            if (!file.exists()) return;
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (!root.has("sessions") || !root.get("sessions").isJsonArray()) return;
            for (var el : root.getAsJsonArray("sessions")) {
                try {
                    if (!el.isJsonObject()) continue;
                    JsonObject s = el.getAsJsonObject();
                    if (!s.has("startedAt") || !s.has("samples")) continue;
                    if (!s.has("events")) s.add("events", new JsonArray());
                    if (!s.has("endedAt")) s.addProperty("endedAt", 0);
                    // Previous run may have died without closing: cap it.
                    if (s.get("endedAt").getAsLong() == 0) {
                        s.addProperty("endedAt", System.currentTimeMillis());
                    }
                    sessions.add(s);
                } catch (Exception ignored) {}
            }
            pruneLocked();
        } catch (Exception e) {
            LOGGER.warn("[MoidClient] Stats load failed, starting fresh", e);
        }
    }

    private void saveLocked() {
        try {
            pruneLocked();
            JsonObject out = new JsonObject();
            out.addProperty("version", 1);
            JsonArray arr = new JsonArray();
            for (JsonObject s : sessions) arr.add(s);
            out.add("sessions", arr);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                LOGGER.warn("[MoidClient] Could not create stats dir {}", parent);
            }
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), GSON.toJson(out), StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("[MoidClient] Stats save failed", e);
            try {
                File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
                Files.deleteIfExists(tmp.toPath());
            } catch (Exception ignored) {}
        }
    }
}
