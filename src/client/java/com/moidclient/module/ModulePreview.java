package com.moidclient.module;

import com.google.gson.JsonObject;

/**
 * Server-computed editor preview for one module. The dashboard renders
 * these verbatim - it holds no per-module preview logic of its own.
 * kind "text": plain pill sized to content. kind "keystrokes": fixed
 * w/h box. highlight: pressed-state background.
 */
public final class ModulePreview {
    public final String id;
    public final String text;
    public final String kind;
    public final int w;
    public final int h;
    public final boolean highlight;

    public ModulePreview(String id, String text, String kind, int w, int h, boolean highlight) {
        this.id = id;
        this.text = text;
        this.kind = kind;
        this.w = w;
        this.h = h;
        this.highlight = highlight;
    }

    public static ModulePreview text(String id, String text) {
        return new ModulePreview(id, text, "text", 0, 0, false);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        o.addProperty("kind", kind);
        o.addProperty("w", w);
        o.addProperty("h", h);
        o.addProperty("highlight", highlight);
        return o;
    }
}
