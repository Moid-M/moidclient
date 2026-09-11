package com.moidclient.utility.perspectiveskip;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Double-F5 Perspective Skip - pressing F5 jumps straight from first-person
 * to front-facing third-person, skipping third-person-back.
 * No mixins: intercepts the perspective key on START_CLIENT_TICK (before
 * vanilla handles it) and runs a custom FIRST -> FRONT -> FIRST cycle.
 * Only consumes the key while enabled; vanilla behavior is untouched otherwise.
 * Category: Utility
 */
public final class PerspectiveSkipManager {
    private PerspectiveSkipManager() {}

    public static ModuleDef definition() {
        return new ModuleDef("perspectiveSkip", "Perspective Skip",
                "F5 jumps first-person straight to front-facing, skipping third-person-back.",
                "utility", false, List.of());
    }

    public static void onTick(Minecraft mc, ConfigManager config) {
        try {
            if (mc == null || mc.options == null || config == null) return;
            ConfigManager.ModuleConfig mod = config.getModule("perspectiveSkip");
            if (mod == null || !mod.enabled) return;
            while (mc.options.keyTogglePerspective.consumeClick()) {
                CameraType cur = mc.options.getCameraType();
                if (cur.isFirstPerson()) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                } else {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                }
            }
        } catch (Exception ignored) {}
    }
}
