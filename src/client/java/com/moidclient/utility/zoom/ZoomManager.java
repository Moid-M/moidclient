package com.moidclient.utility.zoom;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
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
                    ModuleOption.keybind("zoomKey", "Key", GLFW.GLFW_KEY_C),
                    ModuleOption.slider("zoomLevel", "Zoom level", 1.5, 10.0, 0.5),
                    ModuleOption.bool("zoomSmooth", "Smooth zoom-in"),
                    ModuleOption.bool("zoomSmoothOut", "Smooth zoom-out"),
                    ModuleOption.slider("zoomSmoothSpeed", "Smooth speed", 0.05, 1.0, 0.05),
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
    private static boolean toggled = false;
    private static boolean wasDown = false;
    private static boolean accessorWarned = false;

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
                currentFov = baseFov;
                animOut = false;
                return -1f;
            }
            return (float) currentFov;
        }
        animOut = false;
        return -1f;
    }

    /** Framerate-independent smoothing factor from the speed slider. */
    private static double frameFactor(double speed, float dtSec) {
        double k = Math.max(1.0, speed * 20.0);
        return 1.0 - Math.exp(-k * Math.max(0.0, dtSec));
    }

    public static void onTick(Minecraft mc, ConfigManager config) {
        try {
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("zoom") : null;
            boolean toggleMode = mod != null && "toggle".equals(mod.zoomMode);
            boolean down = isKeyDown(mc, mod != null ? mod.zoomKey : 0);
            if (toggleMode && down && !wasDown) toggled = !toggled;
            wasDown = down;
            if (mc == null || mc.options == null || mod == null || !mod.enabled || mc.player == null) {
                toggled = false;
                snapInactive();
                restoreSens(mc);
                return;
            }
            smoothIn = mod.zoomSmooth;
            smoothOut = mod.zoomSmoothOut;
            speed = mod.zoomSmoothSpeed <= 0 ? 0.4 : Math.max(0.05, Math.min(1.0, mod.zoomSmoothSpeed));
            boolean zooming = toggleMode ? toggled : down;
            if (!zooming) {
                // Release: clear the hold flag FIRST (otherwise the frame
                // loop keeps rendering zoomed forever), then ease out or snap.
                if (held) LOGGER.info("[MoidClient/Zoom] released");
                held = false;
                if (!smoothOut || currentFov < 0) snapInactive();
                else animOut = true;
                restoreSens(mc);
                return;
            }

            double level = mod.zoomLevel <= 0 ? 4.0 : Math.max(1.5, Math.min(10.0, mod.zoomLevel));
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
                LOGGER.info("[MoidClient/Zoom] engaged: baseFov={} level={} key={}", base, level, mod.zoomKey);
            }
            baseFov = base;
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

    /** Raw GLFW hold-state for a dashboard-bound key code (0 = unbound). */
    private static boolean isKeyDown(Minecraft mc, int code) {
        try {
            if (mc == null || mc.getWindow() == null || code <= 0) return false;
            return GLFW.glfwGetKey(mc.getWindow().handle(), code) == GLFW.GLFW_PRESS;
        } catch (Exception e) {
            return false;
        }
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
