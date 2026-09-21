package com.moidclient.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moidclient.network.NetworkPackets;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-updater: compares the running version against the latest stable
 * GitHub release, downloads the matching per-Minecraft jar with progress,
 * verifies its SHA-256 checksum, and stages it for apply-on-restart.
 *
 * Why staging instead of swapping live: on Windows the running jar is file-
 * locked, and Fabric resolves mods before any mod code runs - so two
 * Moid-Client jars in mods/ would fail launch before a janitor could clean
 * up. The game therefore never touches mods/ itself: it stages into
 * <gameDir>/moidclient-update/, and either the user swaps manually or a
 * detached watcher script swaps after the game process exits.
 *
 * All network goes to github.com / api.github.com / *.githubusercontent.com
 * over HTTPS, only when the user presses a button (no background phoning).
 */
public final class UpdateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoidClient/Update");

    static final String API_LATEST = "https://api.github.com/repos/Moid-M/moidclient/releases/latest";
    static final long MAX_JAR_BYTES = 64L * 1024 * 1024;
    static final long MAX_API_BYTES = 256L * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern JAR_NAME = Pattern.compile("^Moid-Client-v(.+?)\\+(\\d+\\.\\d+)\\.jar$");
    private static final Pattern MC_NUMERIC = Pattern.compile("(\\d+)\\.(\\d+)");

    public enum Phase { IDLE, CHECKING, DOWNLOADING, VERIFYING, READY, ERROR, RESTARTING }

    /** Point-in-time snapshot, serialized to the dashboard as-is. */
    public static final class Status {
        public volatile Phase phase = Phase.IDLE;
        public volatile String current = "?";
        public volatile String latest = null;
        public volatile boolean updateAvailable = false;
        public volatile boolean devAhead = false;
        public volatile String assetName = null;
        public volatile long assetSize = 0;
        public volatile long received = 0;
        public volatile long total = 0;
        public volatile String checksum = null; // ok | missing | mismatch
        public volatile String stagedFile = null;
        public volatile String oldFile = null;
        public volatile String error = null;
        public volatile String note = null;
        public volatile JsonObject lastResult = null; // watcher outcome from a previous restart
    }

    private final NetworkPackets network; // nullable in headless tests
    private final HttpClient http;
    private final Status status = new Status();
    private final AtomicBoolean downloading = new AtomicBoolean(false);
    private volatile String assetUrl = null;
    private volatile String expectedSha = null;
    private long lastBroadcastMs = 0;

    public UpdateManager(NetworkPackets network) {
        this.network = network;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        try {
            status.current = FabricLoader.getInstance().getModContainer("moidclient")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("?");
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ pure

    /** "v1.4.0" -> [1,4,0]; suffixes (-dev), build metadata (+26.1) and
     * non-numeric tails are dropped. */
    static int[] parseVersion(String v) {
        try {
            String s = v == null ? "" : v.trim();
            if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
            int plus = s.indexOf('+');
            if (plus >= 0) s = s.substring(0, plus);
            int dash = s.indexOf('-');
            if (dash >= 0) s = s.substring(0, dash);
            String[] parts = s.split("\\.");
            int[] out = new int[Math.max(1, parts.length)];
            for (int i = 0; i < parts.length; i++) {
                try { out[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException e) { out[i] = 0; }
            }
            return out;
        } catch (Exception e) {
            return new int[]{0};
        }
    }

    /** Negative if a < b, zero if equal, positive if a > b. */
    static int compareVersions(String a, String b) {
        int[] x = parseVersion(a);
        int[] y = parseVersion(b);
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? x[i] : 0;
            int yi = i < y.length ? y[i] : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    /** "26.1", "26.1.2" or "26.1+fabric" -> "26.1". Null when unparseable. */
    static String mcMajorMinor(String mcVersion) {
        if (mcVersion == null) return null;
        Matcher m = MC_NUMERIC.matcher(mcVersion);
        if (!m.find()) return null;
        return m.group(1) + "." + m.group(2);
    }

    /** Picks our per-Minecraft asset from a release's asset names. Null if none. */
    static String pickAsset(List<String> names, String tagVersion, String mc) {
        if (names == null || tagVersion == null || mc == null) return null;
        String want = "Moid-Client-v" + tagVersion + "+" + mc + ".jar";
        for (String n : names) {
            if (want.equals(n)) return n;
        }
        return null;
    }

    /** Parses a sha256sum-style line ("<hex>[  ]<file>") to lowercase hex. Null if bad. */
    static String parseShaLine(String line) {
        if (line == null) return null;
        String token = line.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        return token.matches("[0-9a-f]{64}") ? token : null;
    }

    // ------------------------------------------------------------------ http

    private String getText(URI uri, long maxBytes) throws Exception {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = host.equals("api.github.com") || host.equals("github.com")
                || host.endsWith(".githubusercontent.com");
        if (!allowed) throw new IllegalStateException("Blocked host: " + host);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", "MoidClient-Updater")
                .header("Accept", "application/vnd.github+json")
                .GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " from " + host);
        }
        try (InputStream in = res.body()) {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            long total = 0;
            int r;
            while ((r = in.read(buf)) != -1) {
                total += r;
                if (total > maxBytes) throw new IllegalStateException("Response too large");
                sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ check

    /** Runs on the caller's thread (Javalin worker). Never throws. */
    public synchronized Status check() {
        // Never stomp an active download or a staged update with check noise.
        if (downloading.get() || status.phase == Phase.DOWNLOADING || status.phase == Phase.VERIFYING) {
            return status;
        }
        boolean wasReady = status.phase == Phase.READY && status.stagedFile != null;
        status.phase = Phase.CHECKING;
        status.error = null;
        status.note = null;
        broadcast();
        try {
            JsonObject rel = JsonParser.parseString(getText(URI.create(API_LATEST), MAX_API_BYTES)).getAsJsonObject();
            String tag = rel.has("tag_name") ? rel.get("tag_name").getAsString() : null;
            if (tag == null) throw new IllegalStateException("Release has no tag");
            String tagVer = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            status.latest = tagVer;

            String mc = null;
            try {
                mc = FabricLoader.getInstance().getModContainer("minecraft")
                        .map(c -> mcMajorMinor(c.getMetadata().getVersion().getFriendlyString()))
                        .orElse(null);
            } catch (Exception ignored) {}

            List<String> names = new ArrayList<>();
            String url = null;
            long size = 0;
            String sha = null;
            if (rel.has("assets") && rel.get("assets").isJsonArray()) {
                JsonArray assets = rel.getAsJsonArray("assets");
                names = collectNames(assets);
                String want = pickAsset(names, tagVer, mc);
                for (var e : assets) {
                    JsonObject a = e.getAsJsonObject();
                    String n = a.has("name") ? a.get("name").getAsString() : "";
                    if (n.equals(want)) {
                        url = a.has("browser_download_url") ? a.get("browser_download_url").getAsString() : null;
                        try { size = a.has("size") ? a.get("size").getAsLong() : 0; } catch (Exception ignored) {}
                    }
                    if (n.equals(want + ".sha256")) {
                        String shaUrl = a.has("browser_download_url") ? a.get("browser_download_url").getAsString() : null;
                        if (shaUrl != null) sha = parseShaLine(getText(URI.create(shaUrl), 4096));
                    }
                }
                names = collectNames(assets);
            }

            int cmp = compareVersions(status.current, tagVer);
            if (cmp < 0) {
                if (url == null) {
                    status.phase = Phase.ERROR;
                    status.error = "No build for Minecraft " + (mc != null ? mc : "?") + " in " + tag;
                } else if (sha == null) {
                    status.phase = Phase.ERROR;
                    status.error = tag + " has no published checksum - refusing (grab it manually from GitHub)";
                } else {
                    String offered = pickAssetFromUrl(url);
                    String running = runningJarName();
                    if (running != null && running.equals(offered)) {
                        // Already running this exact file (e.g. just applied
                        // it) - don't offer to download yourself again.
                        status.phase = Phase.IDLE;
                        status.updateAvailable = false;
                        status.devAhead = false;
                        status.assetName = offered;
                        status.assetSize = size;
                        status.note = "You're already running this build (" + offered + ")";
                    } else {
                        status.phase = Phase.IDLE;
                        status.updateAvailable = true;
                        status.devAhead = false;
                        status.assetName = offered;
                        status.assetSize = size;
                        status.total = size;
                        status.received = 0;
                        assetUrl = url;
                        expectedSha = sha;
                    }
                }
            } else if (cmp > 0) {
                status.phase = Phase.IDLE;
                status.updateAvailable = false;
                status.devAhead = true;
                status.note = "You're ahead of the latest stable release (" + tag + ") - probably a dev build";
            } else {
                status.phase = Phase.IDLE;
                status.updateAvailable = false;
                status.devAhead = false;
                status.note = "Up to date";
            }
        } catch (Exception e) {
            LOGGER.warn("[MoidClient] Update check failed", e);
            status.phase = Phase.ERROR;
            status.error = "Check failed: " + e.getMessage();
        }
        // A staged update stays staged no matter what the check found.
        if (wasReady && status.stagedFile != null && status.phase != Phase.ERROR) {
            status.phase = Phase.READY;
        }
        broadcast();
        return status;
    }

    private static List<String> collectNames(JsonArray assets) {
        List<String> names = new ArrayList<>();
        for (var e : assets) {
            try { names.add(e.getAsJsonObject().get("name").getAsString()); } catch (Exception ignored) {}
        }
        return names;
    }

    /** Last URL segment, URL-decoded (GitHub encodes '+' as %2B in asset URLs). */
    static String pickAssetFromUrl(String url) {
        try {
            String s = url;
            int q = s.indexOf('?');
            if (q >= 0) s = s.substring(0, q);
            String name = s.substring(s.lastIndexOf('/') + 1);
            return java.net.URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "update.jar";
        }
    }

    // ------------------------------------------------------------------ paths

    static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path stageDir() {
        return gameDir().resolve("moidclient-update");
    }

    public static Path modsDir() {
        return gameDir().resolve("mods");
    }

    public static Path resultFile() {
        return stageDir().resolve("update-result.json");
    }

    /** Staged jar filename, or null when nothing is ready. */
    public String stagedFileName() {
        return status.stagedFile;
    }

    /** Filename of the currently running jar, or null if it can't be told. */
    static String runningJarName() {
        try {
            var paths = FabricLoader.getInstance().getModContainer("moidclient")
                    .map(c -> c.getOrigin().getPaths()).orElse(List.of());
            for (Path p : paths) {
                String n = p.getFileName().toString();
                if (n.endsWith(".jar")) return n;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** "Moid-Client-v1.5.0+26.1.jar" -> "1.5.0", else null. Tolerates the
     * %2B-encoding GitHub uses in URLs, so leftovers from unencoded eras
     * still match for cleanup. */
    static String versionFromJarName(String name) {
        if (name == null) return null;
        Matcher m = JAR_NAME.matcher(name.replace("%2B", "+"));
        return m.matches() ? m.group(1) : null;
    }

    // ------------------------------------------------------------------ status

    public Status status() {
        return status;
    }

    public JsonObject statusJson() {
        JsonObject o = new JsonObject();
        o.addProperty("phase", status.phase.name());
        o.addProperty("current", status.current);
        o.addProperty("latest", status.latest);
        o.addProperty("updateAvailable", status.updateAvailable);
        o.addProperty("devAhead", status.devAhead);
        o.addProperty("assetName", status.assetName);
        o.addProperty("assetSize", status.assetSize);
        o.addProperty("received", status.received);
        o.addProperty("total", status.total);
        o.addProperty("checksum", status.checksum);
        o.addProperty("stagedFile", status.stagedFile);
        o.addProperty("oldFile", status.oldFile);
        o.addProperty("error", status.error);
        o.addProperty("note", status.note);
        if (status.lastResult != null) o.add("lastResult", status.lastResult.deepCopy());
        return o;
    }

    private void broadcast() {
        try {
            long now = System.currentTimeMillis();
            if (network == null) return;
            // Progress storms the DOM like slider drags did - throttle to 4Hz,
            // always letting phase changes and completion through.
            boolean important = status.phase != Phase.DOWNLOADING
                    || status.received >= status.total;
            if (!important && now - lastBroadcastMs < 250) return;
            lastBroadcastMs = now;
            network.broadcastUpdateStatus(statusJson());
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ download

    /** Drops a staged update (e.g. a newer release superseded it). */
    public synchronized boolean discardStaged() {
        try {
            if (status.stagedFile != null) {
                try { Files.deleteIfExists(stageDir().resolve(status.stagedFile)); } catch (Exception ignored) {}
            }
            try { Files.deleteIfExists(stageDir().resolve("update.json")); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        status.stagedFile = null;
        status.oldFile = null;
        status.checksum = null;
        status.received = 0;
        status.updateAvailable = false;
        status.phase = Phase.IDLE;
        status.note = "Staged update discarded - check again for the latest";
        broadcast();
        return true;
    }

    /** Starts the background download. False when already running, already staged, or nothing checked. */
    public boolean startDownload() {
        if (!downloading.compareAndSet(false, true)) return false;
        if (!status.updateAvailable || assetUrl == null || status.stagedFile != null) {
            downloading.set(false);
            return false;
        }
        Thread t = new Thread(this::downloadLoop, "MoidClient-Update");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void downloadLoop() {
        try {
            status.phase = Phase.DOWNLOADING;
            status.error = null;
            status.received = 0;
            broadcast();
            Files.createDirectories(stageDir());
            Path part = stageDir().resolve(status.assetName + ".part");
            Path staged = stageDir().resolve(status.assetName);

            URI uri = URI.create(assetUrl);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean allowed = host.equals("github.com") || host.endsWith(".githubusercontent.com");
            if (!allowed) throw new IllegalStateException("Blocked host: " + host);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "MoidClient-Updater")
                    .GET().build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + res.statusCode());
            }
            long total = res.headers().firstValueAsLong("Content-Length").orElse(status.assetSize);
            if (total <= 0) total = status.assetSize;
            if (total > MAX_JAR_BYTES) throw new IllegalStateException("Asset too large");
            status.total = total;

            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = res.body();
                 OutputStream out = Files.newOutputStream(part,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) != -1) {
                    status.received += r;
                    if (status.received > MAX_JAR_BYTES) throw new IllegalStateException("Asset too large");
                    sha.update(buf, 0, r);
                    out.write(buf, 0, r);
                    broadcast();
                }
            }

            status.phase = Phase.VERIFYING;
            broadcast();
            String got = HexFormat.of().formatHex(sha.digest());
            if (expectedSha == null || !expectedSha.equalsIgnoreCase(got)) {
                try { Files.deleteIfExists(part); } catch (Exception ignored) {}
                status.checksum = "mismatch";
                throw new IllegalStateException("Checksum mismatch - staged file deleted, try again");
            }
            status.checksum = "ok";
            Files.move(part, staged, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            status.stagedFile = staged.getFileName().toString();
            status.oldFile = runningJarName();
            status.updateAvailable = false;
            status.phase = Phase.READY;
            try {
                JsonObject manifest = new JsonObject();
                manifest.addProperty("from", status.current);
                manifest.addProperty("to", status.latest);
                manifest.addProperty("stagedFile", status.stagedFile);
                manifest.addProperty("oldFile", status.oldFile);
                manifest.addProperty("at", System.currentTimeMillis());
                Files.writeString(stageDir().resolve("update.json"), manifest.toString(),
                        StandardCharsets.UTF_8);
            } catch (Exception e) {
                LOGGER.warn("[MoidClient] Update manifest write failed", e);
            }
        } catch (Exception e) {
            LOGGER.warn("[MoidClient] Update download failed", e);
            status.phase = Phase.ERROR;
            status.error = "Download failed: " + e.getMessage();
        } finally {
            downloading.set(false);
            broadcast();
        }
    }

    // ------------------------------------------------------------------ janitor

    /**
     * Startup cleanup. Completes an interrupted watcher swap (*.pending),
     * drops stale staged files and .part leftovers, surfaces the watcher's
     * last result, and best-effort removes older Moid jars from mods/.
     * (The duplicate-id case can't reach us - Fabric resolves mods first -
     * so this is strictly leftover hygiene, never load-order surgery.)
     */
    public void runStartupJanitor() {
        try {
            Path stage = stageDir();
            if (Files.isDirectory(stage)) {
                try (var s = Files.list(stage)) {
                    for (Path p : s.toList()) {
                        String n = p.getFileName().toString();
                        try {
                            if (n.endsWith(".part")) Files.deleteIfExists(p);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
                // Surface the watcher outcome (written after game exit).
                try {
                    Path result = stage.resolve("update-result.json");
                    if (Files.isRegularFile(result)) {
                        status.lastResult = JsonParser.parseString(Files.readString(result)).getAsJsonObject();
                        Files.deleteIfExists(result);
                    }
                } catch (Exception e) {
                    LOGGER.debug("[MoidClient] Update result read failed", e);
                }
                // Drop staged jars at or below the running version (stale);
                // newer staged jars stay for manual apply.
                try (var s = Files.list(stage)) {
                    for (Path p : s.toList()) {
                        String n = p.getFileName().toString();
                        if (!n.endsWith(".jar")) continue;
                        String v = versionFromJarName(n);
                        if (v != null && compareVersions(v, status.current) <= 0) {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
            // Complete an interrupted swap: *.pending in mods/ becomes the jar.
            try {
                Path mods = modsDir();
                if (Files.isDirectory(mods)) {
                    try (var s = Files.list(mods)) {
                        for (Path p : s.toList()) {
                            String n = p.getFileName().toString();
                            if (!n.endsWith(".pending")) continue;
                            Path target = mods.resolve(n.substring(0, n.length() - ".pending".length()));
                            try {
                                if (!Files.exists(target)) Files.move(p, target);
                                else Files.deleteIfExists(p);
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                    // Best-effort sweep of older Moid jars (locked files just skip).
                    String running = runningJarName();
                    try (var s = Files.list(mods)) {
                        for (Path p : s.toList()) {
                            String n = p.getFileName().toString();
                            if (!n.endsWith(".jar") || n.equals(running)) continue;
                            String v = versionFromJarName(n);
                            if (v != null && compareVersions(v, status.current) < 0) {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            LOGGER.debug("[MoidClient] Update janitor failed", e);
        }
    }

    // ------------------------------------------------------------------ restart

    public void markRestarting() {
        status.phase = Phase.RESTARTING;
        broadcast();
    }

    /**
     * Writes the detached watcher script that swaps the jars after this
     * process exits. Returns the script path for the client to launch.
     */
    public Path writeRestartScript() throws Exception {
        if (status.stagedFile == null) throw new IllegalStateException("Nothing staged");
        Files.createDirectories(stageDir());
        Path script = stageDir().resolve("moid-restart.ps1");
        String ps1 = """
                param([long]$ParentPid, [string]$ModsDir, [string]$StageDir, [string]$NewFile, [string]$ResultFile)
                $ErrorActionPreference = 'Stop'
                function Write-Result([bool]$ok, [string]$err) {
                  $r = @{ ok = $ok; version = $NewFile; error = $err; at = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() }
                  $r | ConvertTo-Json -Compress | Set-Content -LiteralPath $ResultFile -Encoding utf8
                }
                try {
                  $deadline = (Get-Date).AddSeconds(120)
                  while ((Get-Date) -lt $deadline) {
                    try { Get-Process -Id $ParentPid -ErrorAction Stop | Out-Null; Start-Sleep -Seconds 1 }
                    catch { break }
                  }
                  try { Get-Process -Id $ParentPid -ErrorAction Stop | Out-Null; Write-Result $false 'game still running after 120s'; exit 1 }
                  catch {}
                  # Crash-safe order: stage as .pending (Fabric ignores non-.jar),
                  # delete olds, then promote. A crash mid-swap either leaves the
                  # old jar running or a .pending the next launch promotes.
                  $pending = Join-Path $ModsDir ($NewFile + '.pending')
                  Move-Item -LiteralPath (Join-Path $StageDir $NewFile) -Destination $pending -Force
                  Get-ChildItem -LiteralPath $ModsDir -Filter 'Moid-Client-*.jar' | Where-Object { $_.Name -ne $NewFile } | Remove-Item -Force
                  Rename-Item -LiteralPath $pending -NewName $NewFile
                  Remove-Item -LiteralPath (Join-Path $StageDir 'update.json') -Force -ErrorAction SilentlyContinue
                  Write-Result $true $null
                } catch {
                  Write-Result $false $_.Exception.Message
                  exit 1
                }
                """;
        Files.writeString(script, ps1, StandardCharsets.UTF_8);
        return script;
    }
}
