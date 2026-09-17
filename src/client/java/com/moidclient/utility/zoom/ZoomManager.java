package com.moidclient.utility.zoom;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.utility.keybind.Keybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Zoom - hold/toggle FOV zoom with per-frame exponential smoothing and
 * sensitivity scaling. The Camera mixin overwrites the computed FOV every
 * frame, so zoom-in eases in, release eases back out, and the user's option
 * is never touched. Sensitivity still goes through the option (in-range).
 * All option access is reflection-based so one build runs on every
 * supported version without mapping breakage.
 * Category: Utility
 */
public final class ZoomManager {
    private ZoomManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MoidClient/Zoom");

    public static ModuleDef definition() {
        return new ModuleDef("zoom", "Zoom",
                "Zoom the camera (C by default, hold or toggle).",
                "utility", false, "target", false,
                ModuleOption.list(
                    ModuleOption.select("zoomMode", "Activation", java.util.List.of("hold", "toggle")),
                    ModuleOption.keybind("zoomKey", "Key", com.moidclient.utility.keybind.NativeKeys.GLFW_KEY_C),
                    ModuleOption.slider("zoomLevel", "Zoom level", 1.5, 10.0, 0.5),
                    ModuleOption.slider("zoomMinLevel", "Min level", 1.0, 10.0, 0.5),
                    ModuleOption.slider("zoomMaxLevel", "Max level", 2.0, 12.0, 0.5),
                    ModuleOption.bool("zoomScrollAdjust", "Scroll to adjust"),
                    ModuleOption.slider("zoomScrollStep", "Scroll step", 0.25, 2.0, 0.25),
                    ModuleOption.bool("zoomSmooth", "Smooth zoom-in"),
                    ModuleOption.bool("zoomSmoothOut", "Smooth zoom-out"),
                    ModuleOption.slider("zoomSmoothSpeed", "Smooth speed", 0.05, 1.0, 0.05),
                    ModuleOption.bool("zoomCinematic", "Cinematic (hide HUD)"),
                    ModuleOption.bool("zoomLowerSensitivity", "Lower sensitivity while zoomed")
                ));
    }

    private static double baseFov = -1;
    private static double targetFov = -1;
    private static double currentFov = -1;
    private static double originalSens = -1;
    private static boolean held = false;
    private static boolean animOut = false;
    private static boolean smoothIn = true;
    private static boolean smoothOut = true;
    private static double speed = 0.4;
    private static boolean accessorWarned = false;
    // Scroll/cinematic state, refreshed every tick (mixins read these).
    private static boolean moduleEnabled = false;
    private static boolean scrollAdjust = true;
    private static double scrollStep = 1.0;
    private static double minLevel = 1.5;
    private static double maxLevel = 10.0;
    private static boolean cinematic = false;
    private static boolean levelDirty = false;
    private static ConfigManager.ModuleConfig lastMod = null;

    /**
     * Advances and returns the FOV for this frame, called by the Camera mixin
     * on every render frame. -1 means inactive (vanilla FOV applies untouched).
     */
    public static float frameFov(net.minecraft.client.DeltaTracker dt) {
        float dtSec = 0.05f;
        try {
            if (dt != null) dtSec = Math.max(0f, dt.getRealtimeDeltaTicks() / 20f);
        } catch (Exception ignored) {}
        double factor = frameFactor(speed, dtSec);
        if (held && targetFov > 0) {
            if (!smoothIn) {
                currentFov = targetFov;
            } else if (currentFov < 0) {
                currentFov = baseFov > 0 ? baseFov : targetFov;
            } else {
                currentFov += (targetFov - currentFov) * factor;
                if (Math.abs(targetFov - currentFov) < 0.3) currentFov = targetFov;
            }
            animOut = false;
            return (float) currentFov;
        }
        if (smoothOut && animOut && baseFov > 0 && currentFov > 0) {
            currentFov += (baseFov - currentFov) * factor;
            if (Math.abs(baseFov - currentFov) < 0.3) {
                snapInactive();
                return -1f;
            }
            return (float) currentFov;
        }
        animOut = false;
        return -1f;
    }

    /** True while the cinematic overlay-hide should apply (checked by mixins/HUDs). */
    public static boolean isCinematicActive() {
        try {
            return cinematic && moduleEnabled && currentFov > 0 && (held || animOut);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Mouse-wheel hook (called from the scroll mixin): while zoomed, each
     * notch moves the level instead of the hotbar. Returns true when consumed.
     * Positive offset (wheel up) zooms in.
     */
    public static boolean onScroll(double yOffset) {
        try {
            if (!moduleEnabled || !scrollAdjust || !held || lastMod == null) return false;
            if (yOffset == 0) return false;
            double next = lastMod.zoomLevel + (yOffset > 0 ? scrollStep : -scrollStep);
            next = Math.max(minLevel, Math.min(maxLevel, next));
            // Snap tiny values to one decimal so the dashboard shows clean numbers.
            next = Math.round(next * 10.0) / 10.0;
            if (next != lastMod.zoomLevel) {
                lastMod.zoomLevel = next;
                levelDirty = true;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static double clampLevel(double level) {
        double lo = minLevel <= 0 ? 1.5 : minLevel;
        double hi = maxLevel <= 0 ? 10.0 : maxLevel;
        if (hi < lo) hi = lo;
        return Math.max(lo, Math.min(hi, level));
    }

    /** Framerate-independent smoothing factor from the speed slider. */
    private static double frameFactor(double speed, float dtSec) {
        double k = Math.max(1.0, speed * 20.0);
        return 1.0 - Math.exp(-k * Math.max(0.0, dtSec));
    }

    public static void onTick(Minecraft mc, ConfigManager config, KeyMapping zoomKey) {
        try {
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("zoom") : null;
            if (mc == null || mc.options == null || mod == null || !mod.enabled || mc.player == null) {
                moduleEnabled = false;
                Keybinds.reset(zoomKey);
                snapInactive();
                restoreSens(mc);
                return;
            }
            moduleEnabled = true;
            lastMod = mod;
            smoothIn = mod.zoomSmooth;
            smoothOut = mod.zoomSmoothOut;
            speed = mod.zoomSmoothSpeed <= 0 ? 0.4 : Math.max(0.05, Math.min(1.0, mod.zoomSmoothSpeed));
            // Snapshot refreshed every tick (even while released) so the
            // first press after a dashboard change uses fresh values.
            scrollAdjust = mod.zoomScrollAdjust;
            scrollStep = mod.zoomScrollStep <= 0 ? 1.0 : Math.max(0.25, Math.min(2.0, mod.zoomScrollStep));
            minLevel = mod.zoomMinLevel;
            maxLevel = mod.zoomMaxLevel;
            cinematic = mod.zoomCinematic;
            boolean toggleMode = "toggle".equals(mod.zoomMode);
            // Dashboard is the remote: push its binding into the vanilla
            // mapping (visible + rebindable in Controls, persisted by vanilla).
            Keybinds.syncBinding(mc, zoomKey, mod.zoomKey);
            boolean zooming = Keybinds.isTriggered(zoomKey, toggleMode);
            if (!zooming) {
                // Release: clear the hold flag FIRST (otherwise the frame
                // loop keeps rendering zoomed forever), then ease out or snap.
                held = false;
                if (!smoothOut || currentFov < 0) snapInactive();
                else animOut = true;
                restoreSens(mc);
                if (levelDirty && config != null) {
                    levelDirty = false;
                    try { config.save(); } catch (Exception ignored) {}
                }
                return;
            }

            double level = clampLevel(mod.zoomLevel <= 0 ? 4.0 : mod.zoomLevel);
            OptionAccess fov = OptionAccess.find(mc.options, "fov", "getFov");
            if (fov == null) {
                if (!accessorWarned) {
                    accessorWarned = true;
                    LOGGER.warn("[MoidClient/Zoom] FOV option not found, zoom disabled until restart");
                }
                return;
            }
            double base;
            try {
                base = fov.getAsDouble();
            } catch (Exception e) {
                return;
            }
            if (baseFov < 0) {
                LOGGER.debug("[MoidClient/Zoom] engaged: baseFov={} level={} key={}", base, level, mod.zoomKey);
            }
            // Re latch every tick while held: the FOV option is never written
            // by zoom (only the computed frame FOV is overwritten), so any
            // change here is the user's own slider move and must be honored.
            baseFov = base;
            // Recomputed every tick (not just on engage) so scroll-adjust
            // takes effect while held.
            targetFov = Math.max(1.0, base / level);
            held = true;
            animOut = false;

            if (mod.zoomLowerSensitivity) {
                OptionAccess sens = OptionAccess.find(mc.options, "sensitivity", "getSensitivity");
                if (sens != null) {
                    if (originalSens < 0) originalSens = sens.getAsDouble();
                    sens.setFromDouble(Math.max(0.01, originalSens / level));
                }
            }
        } catch (Exception ignored) {}
    }

    /** Hard reset: no animation, mixin goes inactive immediately. */
    private static void snapInactive() {
        held = false;
        animOut = false;
        baseFov = -1;
        targetFov = -1;
        currentFov = -1;
    }

    /** Restores the user's sensitivity (FOV option was never touched). */
    private static void restoreSens(Minecraft mc) {
        if (originalSens < 0) {
            originalSens = -1;
            return;
        }
        try {
            if (mc != null && mc.options != null) {
                OptionAccess sens = OptionAccess.find(mc.options, "sensitivity", "getSensitivity");
                if (sens != null) sens.setFromDouble(originalSens);
            }
        } catch (Exception e) {
            LOGGER.debug("[MoidClient/Zoom] restore failed", e);
        }
        originalSens = -1;
    }

    /**
     * Reflection handle for a vanilla option (e.g. FOV, sensitivity).
     * Resolves the option object from the options holder via method or field.
     * Writes go straight to the option's {@code value} field, bypassing the
     * slider-range validation in set() (a 4x zoom from FOV 70 targets 17.5,
     * far below the slider minimum - set() would coerce it back).
     */
    private static final class OptionAccess {
        private final Object option;
        private final Method get;
        private final Field valueField;
        private final Method set;

        private OptionAccess(Object option, Method get, Field valueField, Method set) {
            this.option = option;
            this.get = get;
            this.valueField = valueField;
            this.set = set;
        }

        static OptionAccess find(Object options, String... candidates) {
            for (String name : candidates) {
                // accessor method first (e.g. options.fov())
                try {
                    Method accessor = options.getClass().getMethod(name);
                    Object opt = accessor.invoke(options);
                    OptionAccess access = wrap(opt);
                    if (access != null) return access;
                } catch (Exception ignored) {}
                // then field (e.g. options.fov)
                try {
                    Field field = field(options.getClass(), name);
                    Object opt = field.get(options);
                    OptionAccess access = wrap(opt);
                    if (access != null) return access;
                } catch (Exception ignored) {}
            }
            return null;
        }

        private static OptionAccess wrap(Object opt) {
            if (opt == null) return null;
            try {
                Method get = opt.getClass().getMethod("get");
                Field valueField = null;
                try {
                    valueField = field(opt.getClass(), "value");
                } catch (Exception ignored) {}
                Method set = null;
                for (Method m : opt.getClass().getMethods()) {
                    if (m.getName().equals("set") && m.getParameterCount() == 1) {
                        set = m;
                        break;
                    }
                }
                if (valueField == null && set == null) return null;
                return new OptionAccess(opt, get, valueField, set);
            } catch (Exception e) {
                return null;
            }
        }

        private static Field field(Class<?> c, String name) throws Exception {
            // Walk superclasses: vanilla often returns anonymous OptionInstance
            // subclasses whose private fields live on the parent (same pattern
            // FullbrightManager already uses for gamma).
            Class<?> cur = c;
            while (cur != null) {
                try {
                    return cur.getField(name);
                } catch (NoSuchFieldException ignored) {}
                try {
                    Field f = cur.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
                cur = cur.getSuperclass();
            }
            throw new NoSuchFieldException(name);
        }

        double getAsDouble() throws Exception {
            return ((Number) get.invoke(option)).doubleValue();
        }

        void setFromDouble(double value) throws Exception {
            if (valueField != null) {
                Class<?> type = valueField.getType();
                if (type == int.class || type == Integer.class) {
                    valueField.set(option, (int) Math.round(value));
                } else if (type == float.class || type == Float.class) {
                    valueField.set(option, (float) value);
                } else if (type == double.class || type == Double.class) {
                    valueField.set(option, value);
                } else if (set != null) {
                    invokeSet(value);
                } else {
                    throw new IllegalStateException("no writable option value");
                }
                return;
            }
            invokeSet(value);
        }

        private void invokeSet(double value) throws Exception {
            Class<?> param = set.getParameterTypes()[0];
            if (param == int.class || param == Integer.class) {
                set.invoke(option, (int) Math.round(value));
            } else if (param == float.class || param == Float.class) {
                set.invoke(option, (float) value);
            } else {
                set.invoke(option, value);
            }
        }
    }
}
