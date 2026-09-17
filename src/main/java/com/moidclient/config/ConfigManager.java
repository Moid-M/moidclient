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
        // potion effects specific
        public boolean potionColored = true;
        public boolean potionShowAmplifier = true;
        public boolean potionShowDuration = true;
        public boolean potionShowAmbient = true;
        public int potionMaxEffects = 5; // 1-10
        // armor status specific
        public boolean armorShowDurability = true;
        public String armorDurabilityMode = "number"; // number or percent
        public boolean armorDynamicColor = true; // false=fixed color, true=health gradient
        public boolean armorLowWarn = true;
        public String armorOrientation = "vertical"; // vertical or horizontal
        public String armorLabelSide = "right"; // right, left, above or below
        // session timer specific
        public String sessionScope = "world"; // world, server or client
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
        public double zoomLevel = 4.0; // divisor while zoomed (clamped to min/max)
        public double zoomMinLevel = 1.5;
        public double zoomMaxLevel = 10.0;
        public boolean zoomScrollAdjust = true;
        public double zoomScrollStep = 1.0; // per wheel notch (0.25-2)
        public boolean zoomSmooth = true;
        public boolean zoomSmoothOut = true;
        public double zoomSmoothSpeed = 0.4; // smoothing rate (0.05-1)
        public boolean zoomCinematic = false; // hide crosshair + HUD while zoomed
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

        /** Typed readers for {@link #custom} entries (plugin options). */
        public boolean optBool(String key, boolean fallback) {
            try {
                JsonElement e = custom != null ? custom.get(key) : null;
                if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
                    return e.getAsBoolean();
                }
            } catch (Exception ignored) {}
            return fallback;
        }

        public int optInt(String key, int fallback) {
            try {
                JsonElement e = custom != null ? custom.get(key) : null;
                if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                    return e.getAsInt();
                }
            } catch (Exception ignored) {}
            return fallback;
        }

        public double optDouble(String key, double fallback) {
            try {
                JsonElement e = custom != null ? custom.get(key) : null;
                if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                    double v = e.getAsDouble();
                    if (!Double.isNaN(v) && !Double.isInfinite(v)) return v;
                }
            } catch (Exception ignored) {}
            return fallback;
        }

        public String optString(String key, String fallback) {
            try {
                JsonElement e = custom != null ? custom.get(key) : null;
                if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                    return e.getAsString();
                }
            } catch (Exception ignored) {}
            return fallback;
        }
    }

    public ConfigManager() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        if (!configDir.exists() && !configDir.mkdirs()) LOGGER.warn("[MoidClient] Could not create config dir {}", configDir);
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
                try (java.io.Reader r = new java.io.InputStreamReader(new java.io.FileInputStream(old), java.nio.charset.StandardCharsets.UTF_8)) {
                    JsonObject j = JsonParser.parseReader(r).getAsJsonObject();
                    this.root = j;
                    if (j.has("accentColor")) {
                        String c = j.get("accentColor").getAsString();
                        if (c != null && c.matches(HEX_PATTERN)) accentColor = c;
                    }
                    if (j.has("themeTextColor")) {
                        String c = j.get("themeTextColor").getAsString();
                        if (c != null && c.matches(HEX_PATTERN)) themeTextColor = c;
                    } else if (j.has("textColor")) {
                        String c = j.get("textColor").getAsString();
                        if (c != null && c.matches(HEX_PATTERN)) themeTextColor = c;
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
        try (java.io.Reader reader = new java.io.InputStreamReader(new java.io.FileInputStream(configFile), java.nio.charset.StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            this.root = json;
            // accent - validated
            if (json.has("accentColor")) {
                String c = json.get("accentColor").getAsString();
                if (c != null && c.matches(HEX_PATTERN)) accentColor = c;
            }
            if (json.has("themeTextColor")) {
                String c = json.get("themeTextColor").getAsString();
                if (c != null && c.matches(HEX_PATTERN)) themeTextColor = c;
            } else if (json.has("textColor")) {
                String c = json.get("textColor").getAsString();
                if (c != null && c.matches(HEX_PATTERN)) themeTextColor = c;
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
        registerDefault("sessionTimer", new ModuleConfig(false, 10, 190), overwrite);
        registerDefault("potionEffects", new ModuleConfig(false, 10, 210), overwrite);
        registerDefault("armorStatus", new ModuleConfig(false, 10, 230), overwrite);
        registerDefault("fullbright", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("blockOutline", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("perspectiveSkip", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("zoom", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("freelook", new ModuleConfig(false, 0, 0), overwrite);
        registerDefault("hitboxes", new ModuleConfig(false, 0, 0), overwrite);
        // telemetryBlock ships ON: never overwrite an existing choice.
        registerDefault("telemetryBlock", new ModuleConfig(true, 0, 0), false);
        // removed: testModule, fpsBoost (not implemented)
    }

    private void registerDefault(String id, ModuleConfig cfg, boolean overwrite) {
        if (overwrite || !modules.containsKey(id)) {
            modules.put(id, cfg);
        }
    }

    private void fillMissingDefaults() {
        // heal corrupt (null-deserialized) entries in place; the healing
        // below fills blanks with defaults.
        for (var e : modules.entrySet()) {
            if (e.getValue() == null) e.setValue(new ModuleConfig());
        }
        // remove old unused modules (keep cpsCounter now implemented)
        modules.keySet().removeIf(k -> k.equals("testModule") || k.equals("fpsBoost"));
        for (var e : modules.entrySet()) {
            ModuleConfig c = e.getValue();
            if (c.custom == null) c.custom = new LinkedHashMap<>();
            // Drop stale known-keys from custom (older versions leaked some
            // in): explicit fields are the source of truth, and toJson
            // flattening must not let the stale copy shadow them.
            c.custom.keySet().removeIf(ConfigManager::isKnownModuleKey);
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
                    case "sessionTimer" -> c.format = "Session: {time}";
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
        // potion list size 1-10
        ModuleConfig potions = modules.get("potionEffects");
        if (potions != null) {
            if (potions.potionMaxEffects == 0) potions.potionMaxEffects = 5;
            potions.potionMaxEffects = Math.max(1, Math.min(10, potions.potionMaxEffects));
        }
        // armor durability mode
        ModuleConfig armor = modules.get("armorStatus");
        if (armor != null && !"number".equals(armor.armorDurabilityMode) && !"percent".equals(armor.armorDurabilityMode)) {
            armor.armorDurabilityMode = "number";
        }
        if (armor != null && !"vertical".equals(armor.armorOrientation) && !"horizontal".equals(armor.armorOrientation)) {
            armor.armorOrientation = "vertical";
        }
        if (armor != null && !"right".equals(armor.armorLabelSide) && !"left".equals(armor.armorLabelSide)
                && !"above".equals(armor.armorLabelSide) && !"below".equals(armor.armorLabelSide)) {
            armor.armorLabelSide = "right";
        }
        // session scope
        ModuleConfig session = modules.get("sessionTimer");
        if (session != null && !"world".equals(session.sessionScope) && !"server".equals(session.sessionScope)
                && !"client".equals(session.sessionScope)) {
            session.sessionScope = "world";
        }
        ModuleConfig cps = modules.get("cpsCounter");
        if (cps != null) {
            if (cps.format == null) cps.format = "CPS: {left} | {right}";
            if (cps.cpsMode == null) cps.cpsMode = "both";
        }
        ModuleConfig fb = modules.get("fullbright");
        if (fb != null && fb.fullbrightGamma == 0) fb.fullbrightGamma = 12.0;
        // zoom ranges (min/max bound both the slider and scroll)
        ModuleConfig zoom = modules.get("zoom");
        if (zoom != null) {
            if (zoom.zoomMinLevel <= 0) zoom.zoomMinLevel = 1.5;
            zoom.zoomMinLevel = Math.max(1.0, Math.min(10.0, zoom.zoomMinLevel));
            if (zoom.zoomMaxLevel <= 0) zoom.zoomMaxLevel = 10.0;
            zoom.zoomMaxLevel = Math.max(2.0, Math.min(12.0, zoom.zoomMaxLevel));
            if (zoom.zoomMaxLevel < zoom.zoomMinLevel) zoom.zoomMaxLevel = zoom.zoomMinLevel;
            if (zoom.zoomLevel == 0) zoom.zoomLevel = 4.0;
            zoom.zoomLevel = Math.max(zoom.zoomMinLevel, Math.min(zoom.zoomMaxLevel, zoom.zoomLevel));
            if (zoom.zoomScrollStep == 0) zoom.zoomScrollStep = 1.0;
            zoom.zoomScrollStep = Math.max(0.25, Math.min(2.0, zoom.zoomScrollStep));
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
            try (java.io.Writer writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(tmp), java.nio.charset.StandardCharsets.UTF_8)) {
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
            try {
                File tmp = new File(configFile.getParentFile(), configFile.getName() + ".tmp");
                java.nio.file.Files.deleteIfExists(tmp.toPath());
            } catch (Exception ignored) {}
        }
    }

    public synchronized String getAccentColor() {
        return accentColor;
    }

    public synchronized void setAccentColor(String color) {
        if (color != null && color.matches(HEX_PATTERN)) {
            this.accentColor = color;
            save();
        }
    }

    public synchronized String getThemeTextColor() { return themeTextColor; }
    public synchronized void setThemeTextColor(String color) {
        if (color != null && color.matches(HEX_PATTERN)) {
            this.themeTextColor = color;
            save();
        }
    }

    public synchronized Map<String, ModuleConfig> getModules() {
        return modules;
    }

    public synchronized ModuleConfig getModule(String id) {
        return modules.get(id);
    }

    public synchronized JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("accentColor", accentColor);
        out.addProperty("themeTextColor", themeTextColor);
        JsonObject mods = new JsonObject();
        for (Map.Entry<String, ModuleConfig> e : modules.entrySet()) {
            JsonObject tree = GSON.toJsonTree(e.getValue()).getAsJsonObject();
            // Flatten custom so the dashboard reads/writes option keys
            // directly (it treats module JSON as flat). Explicit fields win:
            // stale duplicates from older versions must not shadow them.
            JsonObject custom = null;
            if (tree.has("custom") && tree.get("custom").isJsonObject()) {
                custom = tree.getAsJsonObject("custom");
            }
            tree.remove("custom");
            if (custom != null) {
                for (var ce : custom.entrySet()) {
                    if (!tree.has(ce.getKey())) tree.add(ce.getKey(), ce.getValue());
                }
            }
            mods.add(e.getKey(), tree);
        }
        out.add("modules", mods);
        return out;
    }

    /** Every key handled explicitly in {@link #updateModule}. Anything else
     * falls into {@code ModuleConfig.custom} (validated) so new options work
     * without editing this file. Keep in sync when adding explicit fields. */
    private static final String HEX_PATTERN = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$";
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
        "zoomMode", "zoomKey", "zoomLevel", "zoomMinLevel", "zoomMaxLevel",
        "zoomScrollAdjust", "zoomScrollStep", "zoomCinematic", "zoomSmooth", "zoomSmoothOut", "zoomSmoothSpeed", "zoomLowerSensitivity",
        "freelookMode", "freelookKey", "freelookSensitivity",
        "potionColored", "potionShowAmplifier", "potionShowDuration", "potionShowAmbient",
        "potionMaxEffects", "armorShowDurability", "armorDurabilityMode", "armorDynamicColor",
        "armorLowWarn", "armorOrientation", "armorLabelSide", "sessionScope"
    );

    public static boolean isKnownModuleKey(String key) {
        return key != null && KNOWN_MODULE_KEYS.contains(key);
    }

    /**
     * Lenient WS readers: one malformed field returns its fallback instead of
     * throwing and aborting the rest of the patch.
     */
    private static boolean getBool(JsonObject data, String key, boolean fallback) {
        try {
            if (data.has(key) && !data.get(key).isJsonNull()) return data.get(key).getAsBoolean();
        } catch (Exception ignored) {}
        return fallback;
    }

    private static int getInt(JsonObject data, String key, int fallback) {
        try {
            if (data.has(key) && !data.get(key).isJsonNull()) return data.get(key).getAsInt();
        } catch (Exception ignored) {}
        return fallback;
    }

    private static double getDouble(JsonObject data, String key, double fallback) {
        try {
            if (data.has(key) && !data.get(key).isJsonNull()) return data.get(key).getAsDouble();
        } catch (Exception ignored) {}
        return fallback;
    }

    private static String getString(JsonObject data, String key) {
        try {
            if (data.has(key) && !data.get(key).isJsonNull()) return data.get(key).getAsString();
        } catch (Exception ignored) {}
        return null;
    }

    public synchronized void updateModule(String id, JsonObject data) {
        updateModule(id, data, true);
    }

    /**
     * Applies a dashboard patch. Preview writes update memory only (no disk,
     * no broadcast - the game reads live memory, the sender already shows
     * its own values), so high-frequency drags stay smooth without hammering
     * storage or the dashboard DOM; the release always sends a saving update.
     */
    public synchronized void updateModule(String id, JsonObject data, boolean save) {
        ModuleConfig cfg = modules.get(id);
        if (cfg == null) {
            cfg = new ModuleConfig();
            modules.put(id, cfg);
        }
        if (cfg.custom == null) cfg.custom = new LinkedHashMap<>();
        cfg.enabled = getBool(data, "enabled", cfg.enabled);
        cfg.x = getInt(data, "x", cfg.x);
        cfg.y = getInt(data, "y", cfg.y);
        double scale = getDouble(data, "scale", cfg.scale);
        if (scale > 0) cfg.scale = scale;
        double opacity = getDouble(data, "opacity", cfg.opacity);
        cfg.opacity = Math.max(0.2, Math.min(1.0, opacity == 0 ? 1.0 : opacity));
        cfg.backgroundOpacity = Math.max(0, Math.min(1, getDouble(data, "backgroundOpacity", cfg.backgroundOpacity)));
        String color = getString(data, "color");
        if (color != null) cfg.color = color;
        cfg.background = getBool(data, "background", cfg.background);
        String bgColor = getString(data, "backgroundColor");
        if (bgColor != null && bgColor.matches(HEX_PATTERN)) cfg.backgroundColor = bgColor;
        if (data.has("textColor")) {
            if (data.get("textColor").isJsonNull()) cfg.textColor = null;
            else {
                String c = getString(data, "textColor");
                if (c != null && (c.isEmpty() || c.matches(HEX_PATTERN))) cfg.textColor = c;
            }
        }
        String format = getString(data, "format");
        if (format != null) cfg.format = format.length() > 512 ? format.substring(0, 512) : format;
        cfg.shadow = getBool(data, "shadow", cfg.shadow);
        String fpsMode = getString(data, "fpsMode");
        if ("fast".equals(fpsMode) || "stable".equals(fpsMode)) cfg.fpsMode = fpsMode;
        cfg.fpsDynamicColor = getBool(data, "fpsDynamicColor", cfg.fpsDynamicColor);
        cfg.tpsDynamicColor = getBool(data, "tpsDynamicColor", cfg.tpsDynamicColor);
        cfg.potionColored = getBool(data, "potionColored", cfg.potionColored);
        cfg.potionShowAmplifier = getBool(data, "potionShowAmplifier", cfg.potionShowAmplifier);
        cfg.potionShowDuration = getBool(data, "potionShowDuration", cfg.potionShowDuration);
        cfg.potionShowAmbient = getBool(data, "potionShowAmbient", cfg.potionShowAmbient);
        cfg.potionMaxEffects = Math.max(1, Math.min(10, getInt(data, "potionMaxEffects", cfg.potionMaxEffects)));
        cfg.armorShowDurability = getBool(data, "armorShowDurability", cfg.armorShowDurability);
        cfg.armorLowWarn = getBool(data, "armorLowWarn", cfg.armorLowWarn);
        String armorMode = getString(data, "armorDurabilityMode");
        if ("number".equals(armorMode) || "percent".equals(armorMode)) cfg.armorDurabilityMode = armorMode;
        cfg.armorDynamicColor = getBool(data, "armorDynamicColor", cfg.armorDynamicColor);
        String armorOrientation = getString(data, "armorOrientation");
        if ("vertical".equals(armorOrientation) || "horizontal".equals(armorOrientation)) cfg.armorOrientation = armorOrientation;
        String armorLabelSide = getString(data, "armorLabelSide");
        if ("right".equals(armorLabelSide) || "left".equals(armorLabelSide) || "above".equals(armorLabelSide) || "below".equals(armorLabelSide)) cfg.armorLabelSide = armorLabelSide;
        String sessionScope = getString(data, "sessionScope");
        if ("world".equals(sessionScope) || "server".equals(sessionScope) || "client".equals(sessionScope)) cfg.sessionScope = sessionScope;
        String cpsMode = getString(data, "cpsMode");
        if ("both".equals(cpsMode) || "left".equals(cpsMode) || "right".equals(cpsMode)) cfg.cpsMode = cpsMode;
        cfg.cpsDynamicColor = getBool(data, "cpsDynamicColor", cfg.cpsDynamicColor);
        cfg.keystrokesShowMouse = getBool(data, "keystrokesShowMouse", cfg.keystrokesShowMouse);
        cfg.keystrokesShowSpace = getBool(data, "keystrokesShowSpace", cfg.keystrokesShowSpace);
        cfg.keystrokesShowShift = getBool(data, "keystrokesShowShift", cfg.keystrokesShowShift);
        cfg.keystrokesShowW = getBool(data, "keystrokesShowW", cfg.keystrokesShowW);
        cfg.keystrokesShowA = getBool(data, "keystrokesShowA", cfg.keystrokesShowA);
        cfg.keystrokesShowS = getBool(data, "keystrokesShowS", cfg.keystrokesShowS);
        cfg.keystrokesShowD = getBool(data, "keystrokesShowD", cfg.keystrokesShowD);
        cfg.keystrokesShowCps = getBool(data, "keystrokesShowCps", cfg.keystrokesShowCps);
        cfg.keystrokesGap = Math.max(0, getInt(data, "keystrokesGap", cfg.keystrokesGap));
        cfg.keystrokesOutline = getBool(data, "keystrokesOutline", cfg.keystrokesOutline);
        if (data.has("keystrokesPressedColor")) {
            if (data.get("keystrokesPressedColor").isJsonNull()) cfg.keystrokesPressedColor = null;
            else {
                String c = getString(data, "keystrokesPressedColor");
                if (c != null && (c.isEmpty() || c.matches(HEX_PATTERN))) cfg.keystrokesPressedColor = c;
            }
        }
        cfg.fullbrightGamma = Math.max(1.0, Math.min(15.0, getDouble(data, "fullbrightGamma", cfg.fullbrightGamma)));
        cfg.blockOutlineWidth = Math.max(1.0, Math.min(5.0, getDouble(data, "blockOutlineWidth", cfg.blockOutlineWidth)));
        cfg.blockOutlineFade = getBool(data, "blockOutlineFade", cfg.blockOutlineFade);
        if (data.has("blockOutlineColor2")) {
            if (data.get("blockOutlineColor2").isJsonNull()) cfg.blockOutlineColor2 = null;
            else {
                String c = getString(data, "blockOutlineColor2");
                if (c != null && (c.isEmpty() || c.matches(HEX_PATTERN))) cfg.blockOutlineColor2 = c.isEmpty() ? null : c;
            }
        }
        String blockOutlineMode = getString(data, "blockOutlineMode");
        if ("block".equals(blockOutlineMode) || "face".equals(blockOutlineMode)) cfg.blockOutlineMode = blockOutlineMode;
        String perspectiveSkipMode = getString(data, "perspectiveSkipMode");
        if ("skipBack".equals(perspectiveSkipMode) || "skipFront".equals(perspectiveSkipMode)) cfg.perspectiveSkipMode = perspectiveSkipMode;
        double zoomMinLevel = getDouble(data, "zoomMinLevel", cfg.zoomMinLevel);
        cfg.zoomMinLevel = Math.max(1.0, Math.min(10.0, zoomMinLevel <= 0 ? 1.5 : zoomMinLevel));
        double zoomMaxLevel = getDouble(data, "zoomMaxLevel", cfg.zoomMaxLevel);
        cfg.zoomMaxLevel = Math.max(2.0, Math.min(12.0, zoomMaxLevel <= 0 ? 10.0 : zoomMaxLevel));
        if (cfg.zoomMinLevel <= 0) cfg.zoomMinLevel = 1.5;
        if (cfg.zoomMaxLevel <= 0) cfg.zoomMaxLevel = 10.0;
        if (cfg.zoomMaxLevel < cfg.zoomMinLevel) cfg.zoomMaxLevel = cfg.zoomMinLevel;
        double zoomLevel = getDouble(data, "zoomLevel", cfg.zoomLevel);
        if (zoomLevel == 0) zoomLevel = 4.0;
        cfg.zoomLevel = Math.max(cfg.zoomMinLevel, Math.min(cfg.zoomMaxLevel, zoomLevel));
        cfg.zoomScrollAdjust = getBool(data, "zoomScrollAdjust", cfg.zoomScrollAdjust);
        double zoomScrollStep = getDouble(data, "zoomScrollStep", cfg.zoomScrollStep);
        cfg.zoomScrollStep = Math.max(0.25, Math.min(2.0, zoomScrollStep == 0 ? 1.0 : zoomScrollStep));
        cfg.zoomCinematic = getBool(data, "zoomCinematic", cfg.zoomCinematic);
        cfg.zoomSmooth = getBool(data, "zoomSmooth", cfg.zoomSmooth);
        cfg.zoomSmoothOut = getBool(data, "zoomSmoothOut", cfg.zoomSmoothOut);
        double zoomSmoothSpeed = getDouble(data, "zoomSmoothSpeed", cfg.zoomSmoothSpeed);
        cfg.zoomSmoothSpeed = Math.max(0.05, Math.min(1.0, zoomSmoothSpeed == 0 ? 0.4 : zoomSmoothSpeed));
        cfg.zoomLowerSensitivity = getBool(data, "zoomLowerSensitivity", cfg.zoomLowerSensitivity);
        int zoomKey = getInt(data, "zoomKey", cfg.zoomKey);
        if (zoomKey > 0) cfg.zoomKey = zoomKey;
        String zoomMode = getString(data, "zoomMode");
        if ("hold".equals(zoomMode) || "toggle".equals(zoomMode)) cfg.zoomMode = zoomMode;
        String freelookMode = getString(data, "freelookMode");
        if ("hold".equals(freelookMode) || "toggle".equals(freelookMode)) cfg.freelookMode = freelookMode;
        double freelookSensitivity = getDouble(data, "freelookSensitivity", cfg.freelookSensitivity);
        cfg.freelookSensitivity = Math.max(0.25, Math.min(3.0, freelookSensitivity == 0 ? 1.0 : freelookSensitivity));
        int freelookKey = getInt(data, "freelookKey", cfg.freelookKey);
        if (freelookKey > 0) cfg.freelookKey = freelookKey;
        cfg.hitboxPlayers = getBool(data, "hitboxPlayers", cfg.hitboxPlayers);
        cfg.hitboxHostiles = getBool(data, "hitboxHostiles", cfg.hitboxHostiles);
        cfg.hitboxPassives = getBool(data, "hitboxPassives", cfg.hitboxPassives);
        cfg.hitboxOther = getBool(data, "hitboxOther", cfg.hitboxOther);
        cfg.hitboxEyeLine = getBool(data, "hitboxEyeLine", cfg.hitboxEyeLine);
        double hitboxEyeLength = getDouble(data, "hitboxEyeLength", cfg.hitboxEyeLength);
        cfg.hitboxEyeLength = Math.max(1.0, Math.min(5.0, hitboxEyeLength == 0 ? 2.0 : hitboxEyeLength));
        cfg.hitboxPadding = Math.max(0.0, Math.min(0.5, getDouble(data, "hitboxPadding", cfg.hitboxPadding)));
        String hitboxRenderRate = getString(data, "hitboxRenderRate");
        if ("Every frame".equals(hitboxRenderRate) || "Every 2nd frame".equals(hitboxRenderRate) || "Every 3rd frame".equals(hitboxRenderRate)) cfg.hitboxRenderRate = hitboxRenderRate;
        double hitboxWidth = getDouble(data, "hitboxWidth", cfg.hitboxWidth);
        cfg.hitboxWidth = Math.max(1.0, Math.min(5.0, hitboxWidth == 0 ? 2.0 : hitboxWidth));
        double hitboxOpacity = getDouble(data, "hitboxOpacity", cfg.hitboxOpacity);
        cfg.hitboxOpacity = Math.max(0.1, Math.min(1.0, hitboxOpacity == 0 ? 0.9 : hitboxOpacity));
        double hitboxRange = getDouble(data, "hitboxRange", cfg.hitboxRange);
        cfg.hitboxRange = Math.max(16.0, Math.min(128.0, hitboxRange == 0 ? 64.0 : hitboxRange));
        for (String colorKey : new String[]{"hitboxPlayersColor", "hitboxHostilesColor", "hitboxPassivesColor", "hitboxOtherColor"}) {
            String c = getString(data, colorKey);
            if (c != null && c.matches(HEX_PATTERN)) {
                if (colorKey.equals("hitboxPlayersColor")) cfg.hitboxPlayersColor = c;
                else if (colorKey.equals("hitboxHostilesColor")) cfg.hitboxHostilesColor = c;
                else if (colorKey.equals("hitboxPassivesColor")) cfg.hitboxPassivesColor = c;
                else cfg.hitboxOtherColor = c;
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
        if (save) save();
    }

    public synchronized void importFromJson(JsonObject json) {
        if (json.has("accentColor")) {
            String c = json.get("accentColor").getAsString();
            if (c != null && c.matches(HEX_PATTERN)) accentColor = c;
        }
        if (json.has("themeTextColor")) {
            String c = json.get("themeTextColor").getAsString();
            if (c != null && c.matches(HEX_PATTERN)) themeTextColor = c;
        } else if (json.has("textColor")) {
            String c = json.get("textColor").getAsString();
            if (c != null && c.matches(HEX_PATTERN)) themeTextColor = c;
        }
        if (json.has("modules") && json.get("modules").isJsonObject()) {
            JsonObject mods = json.getAsJsonObject("modules");
            modules.clear();
            for (var entry : mods.entrySet()) {
                try {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    ModuleConfig cfg = GSON.fromJson(entry.getValue(), ModuleConfig.class);
                    if (cfg == null) cfg = new ModuleConfig();
                    if (cfg.custom == null) cfg.custom = new LinkedHashMap<>();
                    // Pull flattened custom keys back in (toJson flattens).
                    for (var f : obj.entrySet()) {
                        String key = f.getKey();
                        if ("custom".equals(key) || isKnownModuleKey(key)) continue;
                        var value = f.getValue();
                        if (value != null && value.isJsonPrimitive()) {
                            cfg.custom.put(key, value.deepCopy());
                        }
                    }
                    modules.put(entry.getKey(), cfg);
                } catch (Exception ignored) {}
            }
        }
        ensureDefaults(false);
        fillMissingDefaults();
        this.root = json.deepCopy();
        save();
    }

    public File getConfigFile() {
        return configFile;
    }
}
