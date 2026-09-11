package com.moidclient.module;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Static definition of a module: identity + dashboard rendering schema.
 * Implemented once per module via {@code public static ModuleDef definition()}.
 * Categories: hud, visuals, utility. Overlay modules get position + background
 * controls automatically (overlay=true).
 */
public final class ModuleDef {
    public final String id;
    public final String name;
    public final String description;
    public final String category;
    public final boolean overlay;
    public final List<ModuleOption> options;

    public ModuleDef(String id, String name, String description, String category,
                     boolean overlay, List<ModuleOption> options) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.overlay = overlay;
        this.options = List.copyOf(options);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("description", description);
        o.addProperty("category", category);
        o.addProperty("overlay", overlay);
        o.add("options", ModuleOption.toJsonList(options));
        return o;
    }
}
