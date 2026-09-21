package com.moidclient.stats;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for stats sessions, events, pruning and persistence.
 * Uses the File-based constructor - no FabricLoader, no Minecraft classes.
 */
public class StatsRecorderTest {

    private StatsRecorder freshRecorder() throws Exception {
        File dir = Files.createTempDirectory("moidclient-statstest").toFile();
        return new StatsRecorder(new File(dir, "moidclient-stats.json"));
    }

    @Test
    public void samplesAndEventsRoundTrip() throws Exception {
        File dir = Files.createTempDirectory("moidclient-statstest").toFile();
        File file = new File(dir, "moidclient-stats.json");
        StatsRecorder recorder = new StatsRecorder(file);

        recorder.recordSample(120, 42, 20.0, 1500);
        Object level = new Object();
        Object conn = new Object();
        recorder.noteContext(level, conn, "play.example.net");
        recorder.recordSample(119, 43, 19.8, 1501);
        recorder.noteContext(null, null, null);
        recorder.saveAndClose();

        StatsRecorder reloaded = new StatsRecorder(file);
        JsonObject root = reloaded.toJson();
        assertEquals(2, root.getAsJsonArray("sessions").size());
        JsonObject first = root.getAsJsonArray("sessions").get(0).getAsJsonObject();
        assertEquals(2, first.getAsJsonArray("samples").size());
        assertEquals(2, first.getAsJsonArray("events").size());
        assertEquals("join", first.getAsJsonArray("events").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("play.example.net", first.getAsJsonArray("events").get(0).getAsJsonObject().get("where").getAsString());
        assertEquals("leave", first.getAsJsonArray("events").get(1).getAsJsonObject().get("type").getAsString());
        assertTrue(first.get("endedAt").getAsLong() > 0);
    }

    @Test
    public void sessionsArePruned() throws Exception {
        StatsRecorder recorder = freshRecorder();
        // beginSession runs once per constructor: 12 recorders = 12 sessions.
        File dir = Files.createTempDirectory("moidclient-statstest").toFile();
        File file = new File(dir, "moidclient-stats.json");
        for (int i = 0; i < 12; i++) {
            new StatsRecorder(file).saveAndClose();
        }
        StatsRecorder reloaded = new StatsRecorder(file);
        assertEquals(10, reloaded.toJson().getAsJsonArray("sessions").size());
        // Original recorder object is untouched by the loop above.
        assertTrue(recorder.toJson().getAsJsonArray("sessions").size() >= 1);
    }
}
