package com.moidclient.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes one configurable option of a module for the Web dashboard.
 * The dashboard renders cards generically from this schema - no per-module
 * HTML needed. Types: boolean, slider, color, text, select.
 */
public final class ModuleOption {
    public final String key;
    public final String type;
    public final String label;
    public final String hint;
    public final String placeholder;
    public final boolean nullable;
    public final String reveals;
    public final Double min;
    public final Double max;
    public final Double step;
    public final List<String> options;

    private ModuleOption(String key, String type, String label) {
        this.key = key;
        this.type = type;
        this.label = label;
        this.hint = null;
        this.placeholder = null;
        this.nullable = false;
        this.reveals = null;
        this.min = null;
        this.max = null;
        this.step = null;
        this.options = List.of();
    }

    private ModuleOption(ModuleOption base, String hint, String placeholder, boolean nullable,
                         Double min, Double max, Double step, List<String> options) {
        this.key = base.key;
        this.type = base.type;
        this.label = base.label;
        this.hint = hint;
        this.placeholder = placeholder;
        this.nullable = nullable;
        this.reveals = base.reveals;
        this.min = min;
        this.max = max;
        this.step = step;
        this.options = options != null ? List.copyOf(options) : List.of();
    }

    private ModuleOption(ModuleOption base, String reveals) {
        this.key = base.key;
        this.type = base.type;
        this.label = base.label;
        this.hint = base.hint;
        this.placeholder = base.placeholder;
        this.nullable = base.nullable;
        this.reveals = reveals;
        this.min = base.min;
        this.max = base.max;
        this.step = base.step;
        this.options = base.options;
    }

    public static ModuleOption bool(String key, String label) {
        return new ModuleOption(key, "boolean", label);
    }

    /**
     * Toggle-button that reveals another option (e.g. a color picker) with
     * animation while enabled. Renders like the background toggle.
     */
    public static ModuleOption revealToggle(String key, String label, String revealsKey) {
        return new ModuleOption(new ModuleOption(key, "boolean", label), revealsKey);
    }

    public static ModuleOption slider(String key, String label, double min, double max, double step) {
        ModuleOption base = new ModuleOption(key, "slider", label);
        return new ModuleOption(base, null, null, false, min, max, step, null);
    }

    public static ModuleOption color(String key, String label) {
        return new ModuleOption(key, "color", label);
    }

    public static ModuleOption nullableColor(String key, String label, String hint) {
        ModuleOption base = new ModuleOption(key, "color", label);
        return new ModuleOption(base, hint, "auto", true, null, null, null, null);
    }

    public static ModuleOption text(String key, String label, String placeholder) {
        ModuleOption base = new ModuleOption(key, "text", label);
        return new ModuleOption(base, null, placeholder, false, null, null, null, null);
    }

    public static ModuleOption select(String key, String label, List<String> options) {
        ModuleOption base = new ModuleOption(key, "select", label);
        return new ModuleOption(base, null, null, false, null, null, null, options);
    }

    /** Shared scale slider (0.5-2x) used by all overlay modules. */
    public static ModuleOption scale() {
        return slider("scale", "Scale", 0.5, 2.0, 0.01);
    }

    /** Shared text-opacity slider used by overlay + outline modules. */
    public static ModuleOption opacity() {
        return slider("opacity", "Opacity", 0.2, 1.0, 0.01);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("key", key);
        o.addProperty("type", type);
        o.addProperty("label", label);
        if (hint != null) o.addProperty("hint", hint);
        if (placeholder != null) o.addProperty("placeholder", placeholder);
        if (nullable) o.addProperty("nullable", true);
        if (reveals != null) o.addProperty("reveals", reveals);
        if (min != null) o.addProperty("min", min);
        if (max != null) o.addProperty("max", max);
        if (step != null) o.addProperty("step", step);
        if (!options.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String s : options) arr.add(s);
            o.add("options", arr);
        }
        return o;
    }

    public static JsonArray toJsonList(List<ModuleOption> opts) {
        JsonArray arr = new JsonArray();
        for (ModuleOption o : opts) arr.add(o.toJson());
        return arr;
    }

    public static List<ModuleOption> list(ModuleOption... opts) {
        List<ModuleOption> out = new ArrayList<>();
        for (ModuleOption o : opts) out.add(o);
        return out;
    }
}
