package com.moidclient.server;

import com.moidclient.config.ConfigManager;
import com.moidclient.network.NetworkPackets;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;

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
    private Javalin app;
    private int activePort = -1;

    public ServerManager(ConfigManager config, NetworkPackets network) {
        this.config = config;
        this.network = network;
    }

    public int getActivePort() {
        return activePort;
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
        }).get("/api/config", ctx -> ctx.json(config.toJson()))
          .get("/api/port", ctx -> ctx.result(String.valueOf(port)))
          .get("/api/health", ctx -> ctx.json(java.util.Map.of("status", "ok", "port", port)))
          .post("/api/log", ctx -> {
              try {
                  String body = ctx.body();
                  if (body.length() > 10000) body = body.substring(0, 10000) + "...(truncated)";
                  // truncate to 2000 chars to avoid log spam
                  String logBody = body.length() > 2000 ? body.substring(0, 2000) + "..." : body;
                  LOGGER.info("[MoidClient][Web] {}", logBody);
                  ctx.status(200).result("ok");
              } catch (Exception e) {
                  LOGGER.warn("[MoidClient][Web] log failed", e);
                  ctx.status(500).result("err");
              }
          });
    }

    public void stop() {
        if (app != null) {
            app.stop();
            LOGGER.info("[MoidClient] Server stopped");
        }
    }

    public static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
