package com.moidclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Clean JSON configuration stored at .minecraft/config/MoidClient.json
 * Holds accent color, HUD module states, positions, scale, opacity.
 */
public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoidClient/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "moidclient.json";

    // Default accent presets
    public static final String DEFAULT_ACCENT = "#9F9F9F";

    private File configFile;
    private JsonObject root;

    // In-memory representation for convenience
    private final Map<String, ModuleConfig> modules = new LinkedHashMap<>();
    private String accentColor = DEFAULT_ACCENT;
    private String themeTextColor = "#F9FAFB";

    public static class ModuleConfig {
        public boolean enabled = false;
        public int x = 10;
        public int y = 10;        public double scale = 1.0;
        public double opacity = 1.0; // text opacity
        public double backgroundOpacity = 0.85; // independent background opacity
        public String color = null; // legacy
        // ping specific
        public boolean background = false;
        public String backgroundColor = "#1A1B20";
        public String textColor = null; // null = auto (ping color)
        public String format = null; // null = per-module default (see fillMissingDefaults)
        public boolean shadow = true;
        // fps specific
        public String fpsMode = "stable"; // "fast" (per-frame) or "stable" (once/sec)
        public boolean fpsDynamicColor = false; // false=use textColor/white, true=gradient 30-90
        // cps specific
        public String cpsMode = "both"; // both / left / right
        public boolean cpsDynamicColor = false;
        // tps specific
        public boolean tpsDynamicColor = true; // false=white, true=health bands
        // keystrokes specific - Feather/Lunar-like
        public boolean keystrokesShowMouse = true;
        public boolean keystrokesShowSpace = true;
        public boolean keystrokesShowShift = true;
        public boolean keystrokesShowW = true;
        public boolean keystrokesShowA = true;
        public boolean keystrokesShowS = true;
        public boolean keystrokesShowD = true;
        public boolean keystrokesShowCps = false; // show CPS inside LMB/RMB
        public int keystrokesGap = 2; // 0-10 px
        public boolean keystrokesOutline = true;
        public String keystrokesPressedColor = null; // null = accent
        // fullbright specific
        public double fullbrightGamma = 12.0; // gamma when enabled (1-15)
        // block outline specific
        public double blockOutlineWidth = 2.0; // line thickness (1-5)
        public boolean blockOutlineFade = false; // animated gradient to second color
        public String blockOutlineColor2 = null; // null = solid color1
        public String blockOutlineMode = "block"; // block (full outline) or face (targeted face only)
        // custom hitboxes specific
        public boolean hitboxPlayers = true;
        public String hitboxPlayersColor = "#06B6D4";
        public boolean hitboxHostiles = true;
        public String hitboxHostilesColor = "#EF4444";
        public boolean hitboxPassives = true;
        public String hitboxPassivesColor = "#22C55E";
        public boolean hitboxOther = false;
        public String hitboxOtherColor = "#9CA3AF";
        public boolean hitboxEyeLine = true;
        public double hitboxEyeLength = 2.0; // 1-5
        public double hitboxPadding = 0.0; // 0-0.5
        public String hitboxRenderRate = "Every frame"; // Every frame / Every 2nd frame / Every 3rd frame
        public double hitboxWidth = 2.0; // 1-5
        public double hitboxOpacity = 0.9; // 0.1-1
        public double hitboxRange = 64.0; // 16-128 blocks
        // perspective skip specific
        public String perspectiveSkipMode = "skipBack"; // skipBack (F5 skips third-person-back) or skipFront (F5 skips front-facing)
        // zoom specific
        public String zoomMode = "hold"; // hold or toggle
        public int zoomKey = 67; // GLFW key code (67 = C)
        public double zoomLevel = 4.0; // FOV divisor while zoom key held (1.5-10)
        public boolean zoomSmooth = true;
        public boolean zoomSmoothOut = true;
        public double zoomSmoothSpeed = 0.4; // smoothing rate (0.05-1)
        public boolean zoomLowerSensitivity = true;
        // freelook specific
        public String freelookMode = "hold"; // hold or toggle
        public int freelookKey = 342; // GLFW key code (342 = Left Alt)
        public double freelookSensitivity = 1.0; // mouse multiplier while active (0.25-3)
        // Catch-all for options this class doesn't know: future modules and
        // plugins store arbitrary validated primitives here, so adding an
        // option no longer requires editing this file. Serialized as-is.
        public Map<String, JsonElement> custom = new LinkedHashMap<>();

        public ModuleConfig() {}
        public ModuleConfig(boolean enabled, int x, int y) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
        }
    }

    public ConfigManager() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        if (!configDir.exists()) configDir.mkdirs();
        this.configFile = new File(configDir, FILE_NAME);
        loadOrCreate();
    }

    // For testing / headless without FabricLoader
    public ConfigManager(File file) {
        this.configFile = file;
        loadOrCreate();
    }

    private void loadOrCreate() {
        // migrate from old MoidClient.json if needed
        if (!configFile.exists()) {
            File old = new File(configFile.getParentFile(), "MoidClient.json");
            if (old.exists()) {
                try (FileReader r = new FileReader(old)) {
                    JsonObject j = JsonParser.parseReader(r).getAsJsonObject();
                    this.root = j;
                    if (j.has("accentColor")) {
                        String c = j.get("accentColor").getAsString();
                        if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) accentColor = c;
                    }
                    if (j.has("themeTextColor")) {
                        String c = j.get("themeTextColor").getAsString();
                        if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) themeTextColor = c;
                    } else if (j.has("textColor")) {
                        String c = j.get("textColor").getAsString();
                        if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) themeTextColor = c;
                    }
                    if (j.has("modules") && j.get("modules").isJsonObject()) {
                        JsonObject mods = j.getAsJsonObject("modules");
                        for (var e : mods.entrySet()) {
                            ModuleConfig cfg = GSON.fromJson(e.getValue(), ModuleConfig.class);
                            modules.put(e.getKey(), cfg);
                        }
                    }
                    ensureDefaults(false);
                    // fill missing ping defaults
                    fillMissingDefaults();
                    save();
                    LOGGER.info("[MoidClient] Migrated config from {}", old.getAbsolutePath());
                    return;
                } catch (Exception e) { LOGGER.warn("[MoidClient] Migration failed", e); }
            }
            createDefaults();
            save();
            LOGGER.info("[MoidClient] Created default config at {}", configFile.getAbsolutePath());
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            this.root = json;
            // accent - validated
            if (json.has("accentColor")) {
                String c = json.get("accentColor").getAsString();
                if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) accentColor = c;
            }
            if (json.has("themeTextColor")) {
                String c = json.get("themeTextColor").getAsString();
                if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) themeTextColor = c;
            } else if (json.has("textColor")) {
                String c = json.get("textColor").getAsString();
                if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) themeTextColor = c;
            }
            // modules
            if (json.has("modules") && json.get("modules").isJsonObject()) {
                JsonObject mods = json.getAsJsonObject("modules");
                for (var entry : mods.entrySet()) {
                    ModuleConfig cfg = GSON.fromJson(entry.getValue(), ModuleConfig.class);
                    modules.put(entry.getKey(), cfg);
                }
            }
            // ensure all expected modules exist
            ensureDefaults(false);
            fillMissingDefaults();
            LOGGER.info("[MoidClient] Loaded config from {}", configFile.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("[MoidClient] Failed to load config, recreating defaults", e);
            createDefaults();
            save();
        }
    }

    private void createDefaults() {
        root = new JsonObject();
        accentColor = DEFAULT_ACCENT;
        modules.clear();
        ensureDefaults(true);
    }

    /**
     * Ensure all HUD modules exist. If `overwrite` false, only add missing.
     */
    private void ensureDefaults(boolean overwrite) {
        registerDefault("ping", new ModuleConfig(false, 10, 50), overwrite);
        registerDefault("fpsCounter", new ModuleConfig(false, 10, 10), overwrite);
        registerDefault("tpsCounter", new ModuleConfig(false, 10, 70), overwrite);
        registerDefault("cpsCounter", new ModuleConfig(false, 10, 30), overwrite);
        registerDefault("keystrokes", new ModuleConfig(false, 10, 90), overwrite);
        registerDefault("coords", new ModuleConfig(false, 10, 110), overwrite);
        registerDefault("server", new ModuleConfig(false, 10, 130), overwrite);
        registerDefault("clock", new ModuleConfig(false, 10, 150), overwrite);
        registerDefault("biome", new ModuleConfig(false, 10, 170), overwrite);
        registerDefault("fullbright", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("blockOutline", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("perspectiveSkip", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("zoom", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("freelook", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("hitboxes", new ModuleConfig(false, 0, 0), overwrite);
        // removed: testModule, armorStatus, fpsBoost (not implemented)
    }

    private void registerDefault(String id, ModuleConfig cfg, boolean overwrite) {
        if (overwrite || !modules.containsKey(id)) {
            modules.put(id, cfg);
        }
    }

    private void fillMissingDefaults() {
        // remove old unused modules (keep cpsCounter now implemented)
        modules.keySet().removeIf(k -> k.equals("testModule") || k.equals("armorStatus") || k.equals("fpsBoost"));
        for (var e : modules.entrySet()) {
            ModuleConfig c = e.getValue();
            if (c.custom == null) c.custom = new LinkedHashMap<>();
            if (c.backgroundColor == null) c.backgroundColor = "#1A1B20";
            if (c.backgroundOpacity == 0) {
                // migrate from old opacity*0.85 or default 0.85
                c.backgroundOpacity = c.opacity > 0 ? Math.max(0.2, Math.min(1, c.opacity * 0.85)) : 0.85;
            }
            c.backgroundOpacity = Math.max(0, Math.min(1, c.backgroundOpacity));
            c.opacity = Math.max(0.2, Math.min(1, c.opacity == 0 ? 1.0 : c.opacity));
            if (c.fpsMode == null || (!c.fpsMode.equals("fast") && !c.fpsMode.equals("stable"))) {
                c.fpsMode = "stable";
            }
            if (c.cpsMode == null || (!c.cpsMode.equals("both") && !c.cpsMode.equals("left") && !c.cpsMode.equals("right"))) {
                c.cpsMode = "both";
            }
            // migrate fps/cps format from old ping placeholder
            if (e.getKey().equals("fpsCounter") && c.format != null && c.format.contains("{ping}")) {
                c.format = "FPS: {fps}";
            }
            if (e.getKey().equals("cpsCounter") && c.format != null && c.format.contains("{ping}")) {
                c.format = "CPS: {left} | {right}";
            }
            // heal configs that saved the old shared default on non-ping modules
            if (!e.getKey().equals("ping") && "Ping: {ping} ms".equals(c.format)) c.format = null;
            // heal previous coords separator default
            if (e.getKey().equals("coords") && "XYZ: {x} / {y} / {z}".equals(c.format)) c.format = null;
            if (c.format == null) {
                switch (e.getKey()) {
                    case "ping" -> c.format = "Ping: {ping} ms";
                    case "fpsCounter" -> c.format = "FPS: {fps}";
                    case "tpsCounter" -> c.format = "TPS: {tps}";
                    case "cpsCounter" -> c.format = "CPS: {left} | {right}";
                    case "coords" -> c.format = "XYZ: {x} | {y} | {z}";
                    case "server" -> c.format = "Server: {server}";
                    case "clock" -> c.format = "{time} | Day {day}";
                    case "biome" -> c.format = "Biome: {biome}";
                    default -> c.format = "{value}";
                }
            }
            // fullbright gamma 1-15
            if (e.getKey().equals("fullbright")) {
                if (c.fullbrightGamma == 0) c.fullbrightGamma = 12.0;
                c.fullbrightGamma = Math.max(1.0, Math.min(15.0, c.fullbrightGamma));
            }
            // block outline thickness 1-5
            if (e.getKey().equals("blockOutline")) {
                if (c.blockOutlineWidth == 0) c.blockOutlineWidth = 2.0;
                c.blockOutlineWidth = Math.max(1.0, Math.min(5.0, c.blockOutlineWidth));
            }
            // hitboxes ranges
            if (e.getKey().equals("hitboxes")) {
                if (c.hitboxWidth == 0) c.hitboxWidth = 2.0;
                c.hitboxWidth = Math.max(1.0, Math.min(5.0, c.hitboxWidth));
                if (c.hitboxOpacity == 0) c.hitboxOpacity = 0.9;
                c.hitboxOpacity = Math.max(0.1, Math.min(1.0, c.hitboxOpacity));
                if (c.hitboxRange == 0) c.hitboxRange = 64.0;
                c.hitboxRange = Math.max(16.0, Math.min(128.0, c.hitboxRange));
                if (c.hitboxEyeLength == 0) c.hitboxEyeLength = 2.0;
                c.hitboxEyeLength = Math.max(1.0, Math.min(5.0, c.hitboxEyeLength));
                c.hitboxPadding = Math.max(0.0, Math.min(0.5, c.hitboxPadding));
                if (c.hitboxRenderRate == null || (!c.hitboxRenderRate.equals("Every frame")
                        && !c.hitboxRenderRate.equals("Every 2nd frame")
                        && !c.hitboxRenderRate.equals("Every 3rd frame"))) {
                    c.hitboxRenderRate = "Every frame";
                }
            }
        }
        ModuleConfig ping = modules.get("ping");
        if (ping != null && ping.format == null) ping.format = "Ping: {ping} ms";
        ModuleConfig fps = modules.get("fpsCounter");
        if (fps != null) {
            if (fps.format == null) fps.format = "FPS: {fps}";
            if (fps.fpsMode == null) fps.fpsMode = "stable";
        }
        ModuleConfig tps = modules.get("tpsCounter");
        if (tps != null && tps.format == null) tps.format = "TPS: {tps}";
        ModuleConfig cps = modules.get("cpsCounter");
        if (cps != null) {
            if (cps.format == null) cps.format = "CPS: {left} | {right}";
            if (cps.cpsMode == null) cps.cpsMode = "both";
        }
        ModuleConfig fb = modules.get("fullbright");
        if (fb != null && fb.fullbrightGamma == 0) fb.fullbrightGamma = 12.0;
        // zoom level 1.5-10
        ModuleConfig zoom = modules.get("zoom");
        if (zoom != null) {
            if (zoom.zoomLevel == 0) zoom.zoomLevel = 4.0;
            zoom.zoomLevel = Math.max(1.5, Math.min(10.0, zoom.zoomLevel));
            if (!"hold".equals(zoom.zoomMode) && !"toggle".equals(zoom.zoomMode)) zoom.zoomMode = "hold";
            if (zoom.zoomSmoothSpeed == 0) zoom.zoomSmoothSpeed = 0.4;
            zoom.zoomSmoothSpeed = Math.max(0.05, Math.min(1.0, zoom.zoomSmoothSpeed));
            if (zoom.zoomKey <= 0) zoom.zoomKey = 67;
        }
        // freelook sensitivity 0.25-3
        ModuleConfig freelook = modules.get("freelook");
        if (freelook != null) {
            if (freelook.freelookSensitivity == 0) freelook.freelookSensitivity = 1.0;
            freelook.freelookSensitivity = Math.max(0.25, Math.min(3.0, freelook.freelookSensitivity));
            if (!"hold".equals(freelook.freelookMode) && !"toggle".equals(freelook.freelookMode)) freelook.freelookMode = "hold";
            if (freelook.freelookKey <= 0) freelook.freelookKey = 342;
        }
    }

    public synchronized void save() {
        try {
            JsonObject out = new JsonObject();
            out.addProperty("accentColor", accentColor);
            out.addProperty("themeTextColor", themeTextColor);
            JsonObject mods = new JsonObject();
            for (Map.Entry<String, ModuleConfig> e : modules.entrySet()) {
                mods.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            }
            out.add("modules", mods);
            // preserve extra fields like performance if needed
            if (root != null) {
                for (var k : root.keySet()) {
                    if (!out.has(k) && !k.equals("modules") && !k.equals("accentColor") && !k.equals("themeTextColor")) {
                        out.add(k, root.get(k));
                    }
                }
            }
            this.root = out;
            // atomic write: write to tmp then move over the live file.
            // NOTE: File.renameTo() cannot replace an existing file on Windows
            // (fails silently with `false`), so use Files.move instead.
            File tmp = new File(configFile.getParentFile(), configFile.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(out, writer);
            }
            try {
                Files.move(tmp.toPath(), configFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("[MoidClient] Failed to save config", e);
        }
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String color) {
        if (color != null && color.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
            this.accentColor = color;
            save();
        }
    }

    public String getThemeTextColor() { return themeTextColor; }
    public void setThemeTextColor(String color) {
        if (color != null && color.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
            this.themeTextColor = color;
            save();
        }
    }

    public Map<String, ModuleConfig> getModules() {
        return modules;
    }

    public ModuleConfig getModule(String id) {
        return modules.get(id);
    }

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("accentColor", accentColor);
        out.addProperty("themeTextColor", themeTextColor);
        JsonObject mods = new JsonObject();
        for (Map.Entry<String, ModuleConfig> e : modules.entrySet()) {
            mods.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        }
        out.add("modules", mods);
        return out;
    }

    /** Every key handled explicitly in {@link #updateModule}. Anything else
     * falls into {@code ModuleConfig.custom} (validated) so new options work
     * without editing this file. Keep in sync when adding explicit fields. */
    private static final java.util.Set<String> KNOWN_MODULE_KEYS = java.util.Set.of(
        "enabled", "x", "y", "scale", "opacity", "backgroundOpacity", "color",
        "background", "backgroundColor", "textColor", "format", "shadow",
        "fpsMode", "fpsDynamicColor", "tpsDynamicColor", "cpsMode", "cpsDynamicColor",
        "keystrokesShowMouse", "keystrokesShowSpace", "keystrokesShowShift",
        "keystrokesShowW", "keystrokesShowA", "keystrokesShowS", "keystrokesShowD",
        "keystrokesShowCps", "keystrokesGap", "keystrokesOutline", "keystrokesPressedColor",
        "fullbrightGamma", "blockOutlineWidth", "blockOutlineFade", "blockOutlineColor2",
        "blockOutlineMode", "perspectiveSkipMode",
        "hitboxPlayers", "hitboxHostiles", "hitboxPassives", "hitboxOther",
        "hitboxEyeLine", "hitboxEyeLength", "hitboxPadding", "hitboxRenderRate",
        "hitboxWidth", "hitboxOpacity", "hitboxRange",
        "hitboxPlayersColor", "hitboxHostilesColor", "hitboxPassivesColor", "hitboxOtherColor",
        "zoomMode", "zoomKey", "zoomLevel", "zoomSmooth", "zoomSmoothSpeed", "zoomLowerSensitivity",
        "freelookMode", "freelookKey", "freelookSensitivity"
    );

    public void updateModule(String id, JsonObject data) {
        ModuleConfig cfg = modules.get(id);
        if (cfg == null) {
            cfg = new ModuleConfig();
            modules.put(id, cfg);
        }
        if (cfg.custom == null) cfg.custom = new LinkedHashMap<>();
        if (data.has("enabled")) cfg.enabled = data.get("enabled").getAsBoolean();
        if (data.has("x")) cfg.x = data.get("x").getAsInt();
        if (data.has("y")) cfg.y = data.get("y").getAsInt();
        if (data.has("scale")) cfg.scale = data.get("scale").getAsDouble();
        if (data.has("opacity")) cfg.opacity = data.get("opacity").getAsDouble();
        if (data.has("backgroundOpacity")) cfg.backgroundOpacity = data.get("backgroundOpacity").getAsDouble();
        if (data.has("color") && !data.get("color").isJsonNull()) cfg.color = data.get("color").getAsString();
        if (data.has("background")) cfg.background = data.get("background").getAsBoolean();
        if (data.has("backgroundColor") && !data.get("backgroundColor").isJsonNull()) {
            String c = data.get("backgroundColor").getAsString();
            if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) cfg.backgroundColor = c;
        }
        if (data.has("textColor")) {
            if (data.get("textColor").isJsonNull()) cfg.textColor = null;
            else {
                String c = data.get("textColor").getAsString();
                if (c != null && (c.isEmpty() || c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))) cfg.textColor = c;
            }
        }
        if (data.has("format") && !data.get("format").isJsonNull()) cfg.format = data.get("format").getAsString();
        if (data.has("shadow")) cfg.shadow = data.get("shadow").getAsBoolean();
        if (data.has("fpsMode") && !data.get("fpsMode").isJsonNull()) {
            String m = data.get("fpsMode").getAsString();
            if (m.equals("fast") || m.equals("stable")) cfg.fpsMode = m;
        }
        if (data.has("fpsDynamicColor")) cfg.fpsDynamicColor = data.get("fpsDynamicColor").getAsBoolean();
        if (data.has("tpsDynamicColor")) cfg.tpsDynamicColor = data.get("tpsDynamicColor").getAsBoolean();
        if (data.has("cpsMode") && !data.get("cpsMode").isJsonNull()) {
            String m = data.get("cpsMode").getAsString();
            if (m.equals("both") || m.equals("left") || m.equals("right")) cfg.cpsMode = m;
        }
        if (data.has("cpsDynamicColor")) cfg.cpsDynamicColor = data.get("cpsDynamicColor").getAsBoolean();
        if (data.has("keystrokesShowMouse")) cfg.keystrokesShowMouse = data.get("keystrokesShowMouse").getAsBoolean();
        if (data.has("keystrokesShowSpace")) cfg.keystrokesShowSpace = data.get("keystrokesShowSpace").getAsBoolean();
        if (data.has("keystrokesShowShift")) cfg.keystrokesShowShift = data.get("keystrokesShowShift").getAsBoolean();
        if (data.has("keystrokesShowW")) cfg.keystrokesShowW = data.get("keystrokesShowW").getAsBoolean();
        if (data.has("keystrokesShowA")) cfg.keystrokesShowA = data.get("keystrokesShowA").getAsBoolean();
        if (data.has("keystrokesShowS")) cfg.keystrokesShowS = data.get("keystrokesShowS").getAsBoolean();
        if (data.has("keystrokesShowD")) cfg.keystrokesShowD = data.get("keystrokesShowD").getAsBoolean();
        if (data.has("keystrokesShowCps")) cfg.keystrokesShowCps = data.get("keystrokesShowCps").getAsBoolean();
        if (data.has("keystrokesGap")) cfg.keystrokesGap = data.get("keystrokesGap").getAsInt();
        if (data.has("keystrokesOutline")) cfg.keystrokesOutline = data.get("keystrokesOutline").getAsBoolean();
        if (data.has("keystrokesPressedColor")) {
            if (data.get("keystrokesPressedColor").isJsonNull()) cfg.keystrokesPressedColor = null;
            else cfg.keystrokesPressedColor = data.get("keystrokesPressedColor").getAsString();
        }
        if (data.has("fullbrightGamma")) cfg.fullbrightGamma = data.get("fullbrightGamma").getAsDouble();
        if (data.has("blockOutlineWidth")) cfg.blockOutlineWidth = data.get("blockOutlineWidth").getAsDouble();
        if (data.has("blockOutlineFade")) cfg.blockOutlineFade = data.get("blockOutlineFade").getAsBoolean();
        if (data.has("blockOutlineColor2")) {
            if (data.get("blockOutlineColor2").isJsonNull()) cfg.blockOutlineColor2 = null;
            else {
                String c = data.get("blockOutlineColor2").getAsString();
                if (c != null && (c.isEmpty() || c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))) cfg.blockOutlineColor2 = c.isEmpty() ? null : c;
            }
        }
        if (data.has("blockOutlineMode") && !data.get("blockOutlineMode").isJsonNull()) {
            String m = data.get("blockOutlineMode").getAsString();
            if (m.equals("block") || m.equals("face")) cfg.blockOutlineMode = m;
        }
        if (data.has("perspectiveSkipMode") && !data.get("perspectiveSkipMode").isJsonNull()) {
            String m = data.get("perspectiveSkipMode").getAsString();
            if (m.equals("skipBack") || m.equals("skipFront")) cfg.perspectiveSkipMode = m;
        }
        if (data.has("zoomLevel")) cfg.zoomLevel = data.get("zoomLevel").getAsDouble();
        if (data.has("zoomSmooth")) cfg.zoomSmooth = data.get("zoomSmooth").getAsBoolean();
        if (data.has("zoomSmoothOut")) cfg.zoomSmoothOut = data.get("zoomSmoothOut").getAsBoolean();
        if (data.has("zoomSmoothSpeed")) cfg.zoomSmoothSpeed = data.get("zoomSmoothSpeed").getAsDouble();
        if (data.has("zoomLowerSensitivity")) cfg.zoomLowerSensitivity = data.get("zoomLowerSensitivity").getAsBoolean();
        if (data.has("zoomKey")) cfg.zoomKey = data.get("zoomKey").getAsInt();
        if (data.has("zoomMode") && !data.get("zoomMode").isJsonNull()) {
            String m = data.get("zoomMode").getAsString();
            if (m.equals("hold") || m.equals("toggle")) cfg.zoomMode = m;
        }
        if (data.has("freelookMode") && !data.get("freelookMode").isJsonNull()) {
            String m = data.get("freelookMode").getAsString();
            if (m.equals("hold") || m.equals("toggle")) cfg.freelookMode = m;
        }
        if (data.has("freelookSensitivity")) cfg.freelookSensitivity = data.get("freelookSensitivity").getAsDouble();
        if (data.has("freelookKey")) cfg.freelookKey = data.get("freelookKey").getAsInt();
        if (data.has("hitboxPlayers")) cfg.hitboxPlayers = data.get("hitboxPlayers").getAsBoolean();
        if (data.has("hitboxHostiles")) cfg.hitboxHostiles = data.get("hitboxHostiles").getAsBoolean();
        if (data.has("hitboxPassives")) cfg.hitboxPassives = data.get("hitboxPassives").getAsBoolean();
        if (data.has("hitboxOther")) cfg.hitboxOther = data.get("hitboxOther").getAsBoolean();
        if (data.has("hitboxEyeLine")) cfg.hitboxEyeLine = data.get("hitboxEyeLine").getAsBoolean();
        if (data.has("hitboxEyeLength")) cfg.hitboxEyeLength = data.get("hitboxEyeLength").getAsDouble();
        if (data.has("hitboxPadding")) cfg.hitboxPadding = data.get("hitboxPadding").getAsDouble();
        if (data.has("hitboxRenderRate") && !data.get("hitboxRenderRate").isJsonNull()) {
            String r = data.get("hitboxRenderRate").getAsString();
            if (r.equals("Every frame") || r.equals("Every 2nd frame") || r.equals("Every 3rd frame")) cfg.hitboxRenderRate = r;
        }
        if (data.has("hitboxWidth")) cfg.hitboxWidth = data.get("hitboxWidth").getAsDouble();
        if (data.has("hitboxOpacity")) cfg.hitboxOpacity = data.get("hitboxOpacity").getAsDouble();
        if (data.has("hitboxRange")) cfg.hitboxRange = data.get("hitboxRange").getAsDouble();
        for (String colorKey : new String[]{"hitboxPlayersColor", "hitboxHostilesColor", "hitboxPassivesColor", "hitboxOtherColor"}) {
            if (data.has(colorKey) && !data.get(colorKey).isJsonNull()) {
                String c = data.get(colorKey).getAsString();
                if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
                    if (colorKey.equals("hitboxPlayersColor")) cfg.hitboxPlayersColor = c;
                    else if (colorKey.equals("hitboxHostilesColor")) cfg.hitboxHostilesColor = c;
                    else if (colorKey.equals("hitboxPassivesColor")) cfg.hitboxPassivesColor = c;
                    else cfg.hitboxOtherColor = c;
                }
            }
        }
        // Unknown keys (future/plugin options): keep validated primitives so
        // new options round-trip without code changes. Keys are restricted to
        // plain identifiers, strings capped, no objects/arrays.
        for (var entry : data.entrySet()) {
            String key = entry.getKey();
            if (KNOWN_MODULE_KEYS.contains(key)) continue;
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) continue;
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) continue;
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isString() && primitive.getAsString().length() > 512) continue;
            if (primitive.isNumber()) {
                double number = primitive.getAsDouble();
                if (Double.isNaN(number) || Double.isInfinite(number)) continue;
            }
            cfg.custom.put(key, value.deepCopy());
        }
        save();
    }

    public void importFromJson(JsonObject json) {
        if (json.has("accentColor")) {
            String c = json.get("accentColor").getAsString();
            if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) accentColor = c;
        }
        if (json.has("themeTextColor")) {
            String c = json.get("themeTextColor").getAsString();
            if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) themeTextColor = c;
        } else if (json.has("textColor")) {
            String c = json.get("textColor").getAsString();
            if (c != null && c.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) themeTextColor = c;
        }
        if (json.has("modules") && json.get("modules").isJsonObject()) {
            JsonObject mods = json.getAsJsonObject("modules");
            modules.clear();
            for (var entry : mods.entrySet()) {
                ModuleConfig cfg = GSON.fromJson(entry.getValue(), ModuleConfig.class);
                modules.put(entry.getKey(), cfg);
            }
        }
        ensureDefaults(false);
        fillMissingDefaults();
        this.root = json;
        save();
    }

    public File getConfigFile() {
        return configFile;
    }
}
