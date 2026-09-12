package com.moidclient.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pure unit tests for color parsing shared by all HUD renderers.
 * No Minecraft classes needed - runs on plain JUnit.
 */
public class ColorUtilTest {

    @Test
    public void parseFullHexWithFullOpacity() {
        assertEquals(0xFFFF0000, ColorUtil.parseHex("#FF0000", 1.0));
    }

    @Test
    public void parseShortHexExpands() {
        assertEquals(ColorUtil.parseHex("#FF0000", 1.0), ColorUtil.parseHex("#F00", 1.0));
    }

    @Test
    public void parseAppliesOpacity() {
        assertEquals(0x80FF0000, ColorUtil.parseHex("#FF0000", 0.5));
        assertEquals(0x00FF0000, ColorUtil.parseHex("#FF0000", 0.0));
    }

    @Test
    public void parseClampsOpacity() {
        assertEquals(0xFFFF0000, ColorUtil.parseHex("#FF0000", 2.0));
        assertEquals(0x00FF0000, ColorUtil.parseHex("#FF0000", -1.0));
    }

    @Test
    public void parseRejectsGarbage() {
        for (String bad : new String[]{null, "", "#", "#GGGGGG", "#12345", "red", "#1234567"}) {
            try {
                ColorUtil.parseHex(bad, 1.0);
                fail("expected IllegalArgumentException for: " + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void withOpacityKeepsRgb() {
        assertEquals(0x80123456, ColorUtil.withOpacity(0xFF123456, 0.5));
    }

    @Test
    public void validityCheck() {
        assertTrue(ColorUtil.isValidHex("#1A1B20"));
        assertTrue(ColorUtil.isValidHex("#fff"));
        assertFalse(ColorUtil.isValidHex(""));
        assertFalse(ColorUtil.isValidHex(null));
        assertFalse(ColorUtil.isValidHex("#12345"));
        assertFalse(ColorUtil.isValidHex("blue"));
    }
}
