package com.moidclient.utility.keybind;

import com.moidclient.util.KeybindUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shared hold-key engine for modules with dashboard-rebindable keys.
 * Each module owns a vanilla KeyMapping (visible + rebindable in Controls,
 * persisted in options.txt); the dashboard is the remote that pushes its
 * binding in, and {@link #isTriggered} evaluates hold/toggle state with
 * per-mapping latch memory. Future hold-key modules reuse this, no copies.
 */
public final class Keybinds {
    private Keybinds() {}

    private static final Map<KeyMapping, Boolean> LATCHED = new IdentityHashMap<>();
    /**
     * Last dashboard code pushed per mapping. Pushes only happen when the
     * dashboard value changed — never blindly every tick — so a rebind made
     * in Controls is left alone for the reverse path to adopt.
     */
    private static final Map<KeyMapping, Integer> LAST_PUSHED = new IdentityHashMap<>();

    /**
     * Pushes a dashboard key code into the vanilla mapping (and saves
     * options) when the dashboard value changed. No-op otherwise.
     * Must rebuild the dispatch index after setKey: vanilla only writes the
     * field, so without resetMapping() the old key keeps triggering and the
     * new one dispatches to nobody.
     */
    public static void syncBinding(Minecraft mc, KeyMapping mapping, int code) {
        try {
            if (mc == null || mc.options == null || mapping == null || code <= 0) return;
            Integer last = LAST_PUSHED.get(mapping);
            if (last != null && last == code) return;
            int nativeCode = NativeKeys.toNative(code);
            int live = KeybindUtil.readCode(mapping);
            // Transient read failure: record nothing so the push is retried
            // next tick instead of being suppressed forever.
            if (live < 0) return;
            LAST_PUSHED.put(mapping, code);
            if (live == nativeCode) return;
            mapping.setKey(NativeKeys.keyType().getOrCreate(nativeCode));
            try { KeyMapping.resetMapping(); } catch (Exception ignored) {}
            try { mc.options.save(); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    /**
     * Notes an externally applied binding (Controls rebind adopted by the
     * reverse path) so the next push doesn't fight it back.
     */
    public static void noteApplied(KeyMapping mapping, int code) {
        if (mapping != null) LAST_PUSHED.put(mapping, code);
    }

    /**
     * Boot path: pushes a saved dashboard code into a freshly registered
     * vanilla mapping. Client entrypoints run before GameOptions loads
     * options.txt, so the mapping still holds its constructor default here —
     * reading it back into config would wipe every custom bind on restart.
     * The push is recorded so the per-tick forward sync doesn't fight it.
     */
    public static void adoptConfig(KeyMapping mapping, int code) {
        try {
            if (mapping == null || code <= 0) return;
            int nativeCode = NativeKeys.toNative(code);
            mapping.setKey(NativeKeys.keyType().getOrCreate(nativeCode));
            try { KeyMapping.resetMapping(); } catch (Exception ignored) {}
            LAST_PUSHED.put(mapping, code);
        } catch (Exception ignored) {}
    }

    /**
     * Evaluates the trigger: raw hold-state in hold mode, latched press
     * state in toggle mode. Drains the click queue so it never piles up.
     */
    public static boolean isTriggered(KeyMapping mapping, boolean toggleMode) {
        if (mapping == null) return false;
        try {
            if (toggleMode) {
                boolean latched = LATCHED.getOrDefault(mapping, false);
                while (mapping.consumeClick()) latched = !latched;
                LATCHED.put(mapping, latched);
                return latched;
            }
            LATCHED.put(mapping, false);
            while (mapping.consumeClick()) {}
            return mapping.isDown();
        } catch (Exception e) {
            return false;
        }
    }

    /** Clears the toggle latch (call when the module is disabled). */
    public static void reset(KeyMapping mapping) {
        if (mapping != null) LATCHED.remove(mapping);
    }
}
