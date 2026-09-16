package com.moidclient.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes one configurable option of a module for the Web dashboard.
 * The dashboard renders cards generically from this schema - no per-module
 * HTML needed. Types: boolean, slider, color, text, select, keybind.
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
    /**
     * Declared default, applied into the config automatically for keys the
     * config doesn't know (see ModuleRegistry.applyOptionDefaults). Null =
     * no opinion; the reader falls back to its own default instead.
     */
    public final JsonElement defValue;

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
        this.defValue = null;
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
        this.defValue = base.defValue;
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
        this.defValue = base.defValue;
    }

    public static ModuleOption bool(String key, String label) {
        return new ModuleOption(key, "boolean", label);
    }

    /** Boolean with an explicit default (applied automatically if unset). */
    public static ModuleOption bool(String key, String label, boolean def) {
        ModuleOption base = new ModuleOption(key, "boolean", label);
        return withDefault(base, new JsonPrimitive(def));
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

    /** Slider with an explicit default (applied automatically if unset). */
    public static ModuleOption slider(String key, String label, double min, double max, double step, double def) {
        ModuleOption base = new ModuleOption(key, "slider", label);
        ModuleOption out = new ModuleOption(base, null, null, false, min, max, step, null);
        return withDefault(out, new JsonPrimitive(def));
    }

    public static ModuleOption color(String key, String label) {
        return new ModuleOption(key, "color", label);
    }

    /** Color with an explicit default (applied automatically if unset). */
    public static ModuleOption color(String key, String label, String def) {
        ModuleOption base = new ModuleOption(key, "color", label);
        return def != null ? withDefault(base, new JsonPrimitive(def)) : base;
    }

    public static ModuleOption nullableColor(String key, String label, String hint) {
        ModuleOption base = new ModuleOption(key, "color", label);
        return new ModuleOption(base, hint, "auto", true, null, null, null, null);
    }

    public static ModuleOption text(String key, String label, String placeholder) {
        ModuleOption base = new ModuleOption(key, "text", label);
        return new ModuleOption(base, null, placeholder, false, null, null, null, null);
    }

    /** Text with an explicit default (applied automatically if unset). */
    public static ModuleOption text(String key, String label, String placeholder, String def) {
        ModuleOption base = new ModuleOption(key, "text", label);
        ModuleOption out = new ModuleOption(base, null, placeholder, false, null, null, null, null);
        return def != null ? withDefault(out, new JsonPrimitive(def)) : out;
    }

    public static ModuleOption select(String key, String label, List<String> options) {
        ModuleOption base = new ModuleOption(key, "select", label);
        return new ModuleOption(base, null, null, false, null, null, null, options);
    }

    /** Select with an explicit default (applied automatically if unset). */
    public static ModuleOption select(String key, String label, List<String> options, String def) {
        ModuleOption base = new ModuleOption(key, "select", label);
        ModuleOption out = new ModuleOption(base, null, null, false, null, null, null, options);
        return def != null ? withDefault(out, new JsonPrimitive(def)) : out;
    }

    /**
     * Dashboard-rebindable hold key, stored as a raw GLFW key code
     * (e.g. 67 = C, 342 = Left Alt). The dashboard renders a press-a-key
     * capture button; the game syncs the value into the vanilla KeyMapping,
     * which is what actually triggers (and what Controls shows).
     */
    public static ModuleOption keybind(String key, String label, int defaultCode) {
        ModuleOption base = new ModuleOption(key, "keybind", label);
        return withDefault(base, new JsonPrimitive(defaultCode));
    }

    private static ModuleOption withDefault(ModuleOption base, JsonElement def) {
        return new ModuleOption(base.key, base.type, base.label, base.hint, base.placeholder,
                base.nullable, base.reveals, base.min, base.max, base.step, base.options, def);
    }

    private ModuleOption(String key, String type, String label, String hint, String placeholder,
                         boolean nullable, String reveals,
                         Double min, Double max, Double step, List<String> options, JsonElement defValue) {
        this.key = key;
        this.type = type;
        this.label = label;
        this.hint = hint;
        this.placeholder = placeholder;
        this.nullable = nullable;
        this.reveals = reveals;
        this.min = min;
        this.max = max;
        this.step = step;
        this.options = options != null ? List.copyOf(options) : List.of();
        this.defValue = defValue;
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
        if (defValue != null && !defValue.isJsonNull()) o.add("default", defValue);
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
