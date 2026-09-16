package com.moidclient.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.moidclient.config.ConfigManager;
import com.moidclient.hud.biome.BiomeHud;
import com.moidclient.hud.clock.ClockHud;
import com.moidclient.hud.coords.CoordinatesHud;
import com.moidclient.hud.cps.CpsHud;
import com.moidclient.hud.fps.FpsHud;
import com.moidclient.hud.keystrokes.KeystrokesHud;
import com.moidclient.hud.ping.PingHud;
import com.moidclient.hud.server.ServerHud;
import com.moidclient.hud.session.SessionTimerHud;
import com.moidclient.hud.potions.PotionEffectsHud;
import com.moidclient.hud.armor.ArmorStatusHud;
import com.moidclient.hud.tps.TpsHud;
import com.moidclient.utility.freelook.FreeLookManager;
import com.moidclient.utility.perspectiveskip.PerspectiveSkipManager;
import com.moidclient.utility.zoom.ZoomManager;
import com.moidclient.visuals.blockoutline.BlockOutlineRenderer;
import com.moidclient.visuals.hitboxes.HitboxRenderer;
import com.moidclient.visuals.fullbright.FullbrightManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Registry of all modules. The Web dashboard fetches this via
 * GET /api/modules and renders cards generically.
 *
 * Adding a module = write definition() + renderer, then one register() call
 * below. No hardcoded lists to extend, no frontend changes needed.
 * Definitions without a preview (non-HUD modules) pass null as previewFn.
 */
public final class ModuleRegistry {
    private ModuleRegistry() {}

    private static final Map<String, Supplier<ModuleDef>> DEFS = new LinkedHashMap<>();
    private static final Map<String, BiFunction<ConfigManager, LiveStats, ModulePreview>> PREVIEWS = new LinkedHashMap<>();

    static {
        register(PingHud::definition, PingHud::preview);
        register(FpsHud::definition, FpsHud::preview);
        register(TpsHud::definition, TpsHud::preview);
        register(CpsHud::definition, CpsHud::preview);
        register(KeystrokesHud::definition, KeystrokesHud::preview);
        register(CoordinatesHud::definition, CoordinatesHud::preview);
        register(ServerHud::definition, ServerHud::preview);
        register(ClockHud::definition, ClockHud::preview);
        register(BiomeHud::definition, BiomeHud::preview);
        register(SessionTimerHud::definition, SessionTimerHud::preview);
        register(PotionEffectsHud::definition, PotionEffectsHud::preview);
        register(ArmorStatusHud::definition, ArmorStatusHud::preview);
        register(FullbrightManager::definition, null);
        register(BlockOutlineRenderer::definition, null);
        register(PerspectiveSkipManager::definition, null);
        register(ZoomManager::definition, null);
        register(FreeLookManager::definition, null);
        register(HitboxRenderer::definition, null);
    }

    /**
     * Registers a module. Called once per module in the static block above;
     * custom modules add their own register() call here.
     */
    public static void register(Supplier<ModuleDef> defFn,
                                BiFunction<ConfigManager, LiveStats, ModulePreview> previewFn) {
        ModuleDef def = defFn.get();
        DEFS.put(def.id, defFn);
        if (previewFn != null) PREVIEWS.put(def.id, previewFn);
    }

    public static List<ModuleDef> all() {
        List<ModuleDef> out = new ArrayList<>(DEFS.size());
        for (Supplier<ModuleDef> fn : DEFS.values()) out.add(fn.get());
        return out;
    }

    public static JsonArray toJson() {
        JsonArray arr = new JsonArray();
        for (ModuleDef def : all()) arr.add(def.toJson());
        return arr;
    }

    /** Server-computed editor previews for every module that has one. */
    public static JsonObject previews(ConfigManager config, LiveStats stats) {
        JsonObject out = new JsonObject();
        for (var e : PREVIEWS.entrySet()) {
            try {
                putPreview(out, e.getValue().apply(config, stats));
            } catch (Exception ignored) {}
        }
        return out;
    }

    /**
     * Applies option defaults declared in definitions into each module's
     * config (missing keys only, then saves if anything changed). Call once
     * at startup: new options from modules or plugins land with zero
     * ConfigManager edits. Keys claimed by explicit ConfigManager fields
     * are skipped - those keep their own defaults.
     */
    public static void applyOptionDefaults(ConfigManager config) {
        if (config == null) return;
        try {
            boolean touched = false;
            for (ModuleDef def : all()) {
                ConfigManager.ModuleConfig mod = config.getModule(def.id);
                if (mod == null) {
                    mod = new ConfigManager.ModuleConfig(false, 10, 10);
                    config.getModules().put(def.id, mod);
                    touched = true;
                }
                if (mod.custom == null) continue;
                for (ModuleOption opt : def.options) {
                    if (opt.defValue == null || opt.defValue.isJsonNull()) continue;
                    if (ConfigManager.isKnownModuleKey(opt.key)) continue;
                    if (mod.custom.containsKey(opt.key)) continue;
                    mod.custom.put(opt.key, opt.defValue.deepCopy());
                    touched = true;
                }
            }
            if (touched) config.save();
        } catch (Exception ignored) {}
    }

    private static void putPreview(JsonObject out, ModulePreview preview) {
        if (preview != null) out.add(preview.id, preview.toJson());
    }
}
