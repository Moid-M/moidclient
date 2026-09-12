package com.moidclient.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.moidclient.config.ConfigManager;
import com.moidclient.hud.cps.CpsHud;
import com.moidclient.hud.fps.FpsHud;
import com.moidclient.hud.keystrokes.KeystrokesHud;
import com.moidclient.hud.ping.PingHud;
import com.moidclient.utility.perspectiveskip.PerspectiveSkipManager;
import com.moidclient.visuals.blockoutline.BlockOutlineRenderer;
import com.moidclient.visuals.hitboxes.HitboxRenderer;
import com.moidclient.visuals.fullbright.FullbrightManager;

import java.util.List;

/**
 * Single registry of all modules. The Web dashboard fetches this via
 * GET /api/modules and renders cards generically - adding a module means
 * writing definition() + renderer, never touching the frontend.
 */
public final class ModuleRegistry {
    private ModuleRegistry() {}

    public static List<ModuleDef> all() {
        return List.of(
            PingHud.definition(),
            FpsHud.definition(),
            CpsHud.definition(),
            KeystrokesHud.definition(),
            FullbrightManager.definition(),
            BlockOutlineRenderer.definition(),
            PerspectiveSkipManager.definition(),
            HitboxRenderer.definition()
        );
    }

    public static JsonArray toJson() {
        JsonArray arr = new JsonArray();
        for (ModuleDef def : all()) arr.add(def.toJson());
        return arr;
    }

    /** Server-computed editor previews for every module that has one. */
    public static JsonObject previews(ConfigManager config, LiveStats stats) {
        JsonObject out = new JsonObject();
        putPreview(out, PingHud.preview(config, stats));
        putPreview(out, FpsHud.preview(config, stats));
        putPreview(out, CpsHud.preview(config, stats));
        putPreview(out, KeystrokesHud.preview(config, stats));
        return out;
    }

    private static void putPreview(JsonObject out, ModulePreview preview) {
        if (preview != null) out.add(preview.id, preview.toJson());
    }
}
