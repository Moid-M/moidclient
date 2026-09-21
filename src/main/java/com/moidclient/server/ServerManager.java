package com.moidclient.server;

import com.moidclient.config.ConfigManager;
import com.moidclient.network.NetworkPackets;
import com.moidclient.stats.StatsRecorder;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Netty/Javalin HTTP & WebSocket server with dynamic port binding.
 * Attempts 18423..18450 inclusive, serves static assets from resources/webroot.
 */
public class ServerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoidClient/Server");
    private static final int START_PORT = 18423;
    private static final int END_PORT = 18450;

    private final ConfigManager config;
    private final NetworkPackets network;
    private final Supplier<com.google.gson.JsonElement> moduleDefs;
    private final StatsRecorder stats;
    private volatile com.moidclient.update.UpdateManager updates;
    private volatile Runnable restartHook;
    private Javalin app;
    private int activePort = -1;

    public ServerManager(ConfigManager config, NetworkPackets network) {
        this(config, network, null);
    }

    public ServerManager(ConfigManager config, NetworkPackets network, Supplier<com.google.gson.JsonElement> moduleDefs) {
        this(config, network, moduleDefs, null);
    }

    public ServerManager(ConfigManager config, NetworkPackets network, Supplier<com.google.gson.JsonElement> moduleDefs,
                         StatsRecorder stats) {
        this.config = config;
        this.network = network;
        this.moduleDefs = moduleDefs;
        this.stats = stats;
    }

    public int getActivePort() {
        return activePort;
    }

    /** Wired once at startup; null until then (endpoints answer IDLE). */
    public void setUpdateManager(com.moidclient.update.UpdateManager updates) {
        this.updates = updates;
    }

    /** Client-side restart (launches the watcher, then stops the game). */
    public void setRestartHook(Runnable restartHook) {
        this.restartHook = restartHook;
    }

    public String getBaseUrl() {
        return "http://localhost:" + activePort;
    }

    /**
     * Start server with incremental port search. Blocks until started or throws.
     * Binds strictly to 127.0.0.1 and removes TOCTOU race by directly attempting to start.
     */
    public void start() {
        for (int port = START_PORT; port <= END_PORT; port++) {
            Javalin candidate = null;
            try {
                candidate = createApp(port);
                candidate.start("127.0.0.1", port);
                this.app = candidate;
                this.activePort = port;
                LOGGER.info("==================================================");
                LOGGER.info("[MoidClient] Web GUI running at http://localhost:{}", port);
                LOGGER.info("[MoidClient] WebSocket at ws://localhost:{}/ws", port);
                LOGGER.info("==================================================");
                System.out.println("[MoidClient] Web GUI running at http://localhost:" + port);
                return;
            } catch (Exception e) {
                LOGGER.warn("[MoidClient] Port {} unavailable, trying next: {}", port, e.getMessage());
                if (candidate != null) {
                    try { candidate.stop(); } catch (Exception ignored) {}
                }
            }
        }
        throw new RuntimeException("[MoidClient] No available port found between " + START_PORT + " and " + END_PORT);
    }

    private Javalin createApp(int port) {
        return Javalin.create(cfg -> {
            // Serve static frontend from classpath `webroot/` (maps to src/main/resources/webroot)
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "webroot";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.precompress = false;
            });
            cfg.showJavalinBanner = false;
            // CORS: only allow same-origin / localhost; WS is same-origin anyway
            cfg.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                rule.allowHost("http://localhost:" + port);
                rule.allowHost("http://127.0.0.1:" + port);
            }));
        }).ws("/ws", ws -> {
            ws.onConnect(ctx -> network.onConnect(ctx));
            ws.onClose(ctx -> network.onClose(ctx));
            ws.onMessage(ctx -> network.onMessage(ctx, ctx.message()));
        })          .get("/api/config", ctx -> ctx.contentType("application/json").result(config.toJson().toString()))
          .get("/api/modules", ctx -> {
              if (moduleDefs != null) {
                  try {
                      // NOTE: serialize Gson manually - ctx.json() uses Jackson and mangles Gson trees.
                      ctx.contentType("application/json").result(moduleDefs.get().toString());
                      return;
                  } catch (Exception e) {
                      LOGGER.warn("[MoidClient] Failed to serialize module defs", e);
                  }
              }
              ctx.status(503).contentType("application/json").result("[]");
          })
          .get("/api/port", ctx -> ctx.result(String.valueOf(port)))
          .get("/api/health", ctx -> ctx.json(java.util.Map.of("status", "ok", "port", port)))
          .get("/api/stats", ctx -> {
              try {
                  com.google.gson.JsonObject history = stats != null ? stats.toJson() : null;
                  ctx.contentType("application/json").result(history != null ? history.toString() : "{\"sessions\":[]}");
              } catch (Exception e) {
                  ctx.status(500).result("{\"sessions\":[]}");
              }
          })
          .post("/api/stats/clear", ctx -> {
              try {
                  if (stats != null) stats.clear();
                  ctx.result("ok");
              } catch (Exception e) {
                  ctx.status(500).result("err");
              }
          })
          .post("/api/log", ctx -> {
              try {
                  String body = ctx.body();
                  // single truncate to 2000 chars to avoid log spam
                  String logBody = body.length() > 2000 ? body.substring(0, 2000) + "..." : body;
                  LOGGER.info("[MoidClient][Web] {}", logBody);
                  ctx.status(200).result("ok");
              } catch (Exception e) {
                  LOGGER.warn("[MoidClient][Web] log failed", e);
                  ctx.status(500).result("err");
              }
          })
          .get("/api/update/status", ctx -> {
              try {
                  com.google.gson.JsonObject s = updates != null
                          ? updates.statusJson() : idleUpdateJson();
                  ctx.contentType("application/json").result(s.toString());
              } catch (Exception e) {
                  ctx.status(500).result("{\"phase\":\"ERROR\",\"error\":\"status failed\"}");
              }
          })
          .post("/api/update/check", ctx -> {
              try {
                  if (updates == null) { ctx.status(503).result("{\"phase\":\"ERROR\",\"error\":\"updater not ready\"}"); return; }
                  updates.check();
                  ctx.contentType("application/json").result(updates.statusJson().toString());
              } catch (Exception e) {
                  LOGGER.warn("[MoidClient] Update check endpoint failed", e);
                  ctx.status(500).result("{\"phase\":\"ERROR\",\"error\":\"check failed\"}");
              }
          })
          .post("/api/update/download", ctx -> {
              try {
                  if (updates == null) { ctx.status(503).result("{\"error\":\"updater not ready\"}"); return; }
                  boolean started = updates.startDownload();
                  ctx.contentType("application/json").result("{\"started\":" + started + "}");
              } catch (Exception e) {
                  ctx.status(500).result("{\"started\":false}");
              }
          })
          .post("/api/update/discard", ctx -> {
              try {
                  if (updates == null) { ctx.status(503).result("{\"error\":\"updater not ready\"}"); return; }
                  updates.discardStaged();
                  ctx.contentType("application/json").result(updates.statusJson().toString());
              } catch (Exception e) {
                  ctx.status(500).result("{\"error\":\"discard failed\"}");
              }
          })
          .post("/api/update/restart", ctx -> {              try {
                  if (updates == null || restartHook == null) {
                      ctx.status(503).result("{\"error\":\"restart not available\"}");
                      return;
                  }
                  updates.writeRestartScript();
                  restartHook.run();
                  ctx.contentType("application/json").result("{\"ok\":true}");
              } catch (Exception e) {
                  LOGGER.warn("[MoidClient] Update restart failed", e);
                  String msg = e.getMessage() == null ? "restart failed" : e.getMessage().replace("\"", "'");
                  ctx.status(500).result("{\"error\":\"" + msg + "\"}");
              }
          });
    }

    private static com.google.gson.JsonObject idleUpdateJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("phase", "IDLE");
        return o;
    }

    public void stop() {
        try {
            if (app != null) app.stop();
        } catch (Exception e) {
            LOGGER.warn("[MoidClient] Server stop failed", e);
        } finally {
            app = null;
            activePort = -1;
        }
        LOGGER.info("[MoidClient] Server stopped");
    }
}
