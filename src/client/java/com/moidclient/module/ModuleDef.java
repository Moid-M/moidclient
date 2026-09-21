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
    public final String icon;
    public final boolean editor;
    public final List<ModuleOption> options;
    /**
     * Whether fresh configs enable this module. Default false; modules that
     * ship on (telemetry block, statistics) opt in. Existing user choices
     * are never overwritten - this only seeds missing entries.
     */
    public final boolean defaultEnabled;

    public ModuleDef(String id, String name, String description, String category,
                     boolean overlay, List<ModuleOption> options) {
        this(id, name, description, category, overlay, id, false, options, false);
    }

    public ModuleDef(String id, String name, String description, String category,
                     boolean overlay, String icon, boolean editor, List<ModuleOption> options) {
        this(id, name, description, category, overlay, icon, editor, options, false);
    }

    private ModuleDef(String id, String name, String description, String category,
                      boolean overlay, String icon, boolean editor, List<ModuleOption> options,
                      boolean defaultEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.overlay = overlay;
        this.icon = icon != null ? icon : id;
        this.editor = editor;
        this.options = List.copyOf(options);
        this.defaultEnabled = defaultEnabled;
    }

    /** Copy with a fresh-config enabled default (existing choices untouched). */
    public ModuleDef withDefaultEnabled(boolean enabled) {
        return new ModuleDef(id, name, description, category, overlay, icon, editor, options, enabled);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("description", description);
        o.addProperty("category", category);
        o.addProperty("overlay", overlay);
        o.addProperty("icon", icon);
        o.addProperty("editor", editor);
        o.add("options", ModuleOption.toJsonList(options));
        return o;
    }
}
