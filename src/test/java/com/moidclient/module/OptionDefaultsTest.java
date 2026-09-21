package com.moidclient.module;

import com.moidclient.config.ConfigManager;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Proves option defaults declared in definition() land in config without
 * ConfigManager edits. Headless: definitions used here are pure data.
 */
public class OptionDefaultsTest {

    private static ModuleDef fakeDef() {
        return new ModuleDef("fakeFutureMod", "Fake", "d", "hud", false, "box", false,
            ModuleOption.list(
                ModuleOption.bool("lit", "Lit", true),
                ModuleOption.slider("vol", "Vol", 0, 10, 1, 7),
                ModuleOption.text("greeting", "Greeting", "hi", "hello"),
                ModuleOption.select("mode", "Mode", List.of("a", "b"), "b"),
                ModuleOption.color("tint", "Tint", "#FF0000"),
                ModuleOption.keybind("key", "Key", 67)
            ));
    }

    @Test
    public void defaultsLandInCustom() throws Exception {
        File dir = Files.createTempDirectory("moidclient-test").toFile();
        ConfigManager config = new ConfigManager(new File(dir, "moidclient.json"));

        ModuleRegistry.register(() -> fakeDef(), null);
        ModuleRegistry.applyOptionDefaults(config);

        ConfigManager.ModuleConfig mod = config.getModule("fakeFutureMod");
        assertTrue(mod.optBool("lit", false));
        assertEquals(7.0, mod.optDouble("vol", 0), 0.0001);
        assertEquals("hello", mod.optString("greeting", null));
        assertEquals("b", mod.optString("mode", null));
        assertEquals("#FF0000", mod.optString("tint", null));
        assertEquals(67, mod.optInt("key", 0));
    }

    @Test
    public void userValuesAreNeverOverwritten() throws Exception {
        File dir = Files.createTempDirectory("moidclient-test").toFile();
        ConfigManager config = new ConfigManager(new File(dir, "moidclient.json"));

        ModuleRegistry.register(() -> fakeDef(), null);
        ModuleRegistry.applyOptionDefaults(config);
        // user changes vol, then defaults re-apply (e.g. next launch)
        ConfigManager.ModuleConfig mod = config.getModule("fakeFutureMod");
        com.google.gson.JsonObject patch = new com.google.gson.JsonObject();
        patch.addProperty("vol", 3.0);
        config.updateModule("fakeFutureMod", patch);
        ModuleRegistry.applyOptionDefaults(config);

        assertEquals(3.0, config.getModule("fakeFutureMod").optDouble("vol", 0), 0.0001);
    }

        @Test
    public void defaultEnabledSeedsShipOnModules() throws Exception {
        File dir = Files.createTempDirectory("moidclient-test").toFile();
        ConfigManager config = new ConfigManager(new File(dir, "moidclient.json"));
        ModuleRegistry.applyOptionDefaults(config);

        // telemetryBlock + statistics ship ON via their definitions; a plain
        // HUD ships OFF. Seeding never touches existing choices (covered by
        // userValuesAreNeverOverwritten for options; enabled behaves the same
        // since only missing modules are created).
        assertTrue(config.getModule("telemetryBlock").enabled);
        assertTrue(config.getModule("statistics").enabled);
        assertFalse(config.getModule("ping").enabled);
    }

    @Test
    public void knownKeysAreSkipped() throws Exception {
        File dir = Files.createTempDirectory("moidclient-test").toFile();
        ConfigManager config = new ConfigManager(new File(dir, "moidclient.json"));

        ModuleDef def = new ModuleDef("fakeFutureMod2", "Fake2", "d", "hud", false, "box", false,
            ModuleOption.list(ModuleOption.slider("scale", "Scale", 0.5, 2.0, 0.01, 9.9)));
        ModuleRegistry.register(() -> def, null);
        ModuleRegistry.applyOptionDefaults(config);

        // scale is claimed by explicit ConfigManager fields: must NOT leak into custom
        assertFalse(config.getModule("fakeFutureMod2").custom.containsKey("scale"));
    }
}
