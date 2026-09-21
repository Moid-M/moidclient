package com.moidclient.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for unknown-option round-tripping (plugin-proofing).
 * Uses the File-based constructor - no FabricLoader, no Minecraft classes.
 */
public class ConfigManagerTest {

    private ConfigManager freshManager() throws Exception {
        File dir = Files.createTempDirectory("moidclient-test").toFile();
        return new ConfigManager(new File(dir, "moidclient.json"));
    }

    @Test
    public void unknownPrimitiveOptionsRoundTrip() throws Exception {
        ConfigManager config = freshManager();

        JsonObject patch = new JsonObject();
        patch.addProperty("mySlider", 2.5);
        patch.addProperty("myToggle", true);
        patch.addProperty("myText", "hello");
        config.updateModule("futureModule", patch);

        // In memory
        assertEquals(2.5, config.getModule("futureModule").custom.get("mySlider").getAsDouble(), 0.0001);
        assertTrue(config.getModule("futureModule").custom.get("myToggle").getAsBoolean());
        assertEquals("hello", config.getModule("futureModule").custom.get("myText").getAsString());

        // Survives save + reload
        File file = config.getConfigFile();
        ConfigManager reloaded = new ConfigManager(file);
        assertEquals(2.5, reloaded.getModule("futureModule").custom.get("mySlider").getAsDouble(), 0.0001);
        assertTrue(reloaded.getModule("futureModule").custom.get("myToggle").getAsBoolean());
        assertEquals("hello", reloaded.getModule("futureModule").custom.get("myText").getAsString());

        // And appears flat in the dashboard JSON (custom merged top-level)
        JsonObject sync = reloaded.toJson();
        JsonObject mod = sync.getAsJsonObject("modules").getAsJsonObject("futureModule");
        assertEquals("hello", mod.get("myText").getAsString());
        assertEquals(2.5, mod.get("mySlider").getAsDouble(), 0.0001);
        assertTrue(mod.get("myToggle").getAsBoolean());
        assertFalse(mod.has("custom"));
    }

    @Test
    public void explicitFieldsWinOverStaleCustomDupes() throws Exception {
        ConfigManager config = freshManager();
        // Modules are seeded from definitions (no hardcoded ids here), so
        // create it the same way a dashboard patch would.
        JsonObject patch = new JsonObject();
        patch.addProperty("zoomLevel", 4.0);
        config.updateModule("zoom", patch);
        config.getModule("zoom").custom.put("zoomLevel", new com.google.gson.JsonPrimitive(999.0));
        JsonObject mod = config.toJson().getAsJsonObject("modules").getAsJsonObject("zoom");
        assertEquals(config.getModule("zoom").zoomLevel, mod.get("zoomLevel").getAsDouble(), 0.0001);
    }

    @Test
    public void flattenedExportReimports() throws Exception {
        ConfigManager config = freshManager();
        JsonObject patch = new JsonObject();
        patch.addProperty("myToggle", true);
        config.updateModule("futureModule", patch);
        JsonObject exported = config.toJson();

        ConfigManager config2 = freshManager();
        config2.importFromJson(exported);
        assertTrue(config2.getModule("futureModule").custom.get("myToggle").getAsBoolean());
    }

    @Test
    public void hostileKeysAreRejected() throws Exception {
        ConfigManager config = freshManager();

        JsonObject patch = new JsonObject();
        patch.addProperty("enabled", true);
        patch.addProperty("__proto__", true);
        patch.addProperty("a-very-long-key-name-with-dashes", true);
        JsonObject nested = new JsonObject();
        nested.addProperty("x", 1);
        patch.add("myObject", nested);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 600; i++) big.append('x');
        patch.addProperty("tooBig", big.toString());
        config.updateModule("futureModule", patch);

        // Known key applied, hostile keys dropped
        assertTrue(config.getModule("futureModule").enabled);
        assertTrue(config.getModule("futureModule").custom.isEmpty());
    }

    @Test
    public void oldSavesWithoutCustomStillLoad() throws Exception {
        File dir = Files.createTempDirectory("moidclient-test").toFile();
        File file = new File(dir, "moidclient.json");
        JsonObject root = new JsonObject();
        root.addProperty("accentColor", "#FFFFFF");
        JsonObject mods = new JsonObject();
        JsonObject ping = new JsonObject();
        ping.addProperty("enabled", true);
        mods.add("ping", ping);
        root.add("modules", mods);
        Files.writeString(file.toPath(), root.toString());

        ConfigManager config = new ConfigManager(file);
        assertTrue(config.getModule("ping").enabled);
        assertTrue(config.getModule("ping").custom.isEmpty());
    }

    @Test
    public void knownKeysNeverLeakIntoCustom() throws Exception {
        ConfigManager config = freshManager();
        String raw = "{\"zoomLevel\": 5.0, \"mystery\": 1}";
        config.updateModule("zoom", JsonParser.parseString(raw).getAsJsonObject());
        assertEquals(5.0, config.getModule("zoom").zoomLevel, 0.0001);
        assertFalse(config.getModule("zoom").custom.containsKey("zoomLevel"));
        assertTrue(config.getModule("zoom").custom.containsKey("mystery"));
    }
}
