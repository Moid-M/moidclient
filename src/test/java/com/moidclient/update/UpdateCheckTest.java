package com.moidclient.update;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for the updater's pure logic (no network, no Fabric).
 */
public class UpdateCheckTest {

    @Test
    public void versionsCompareNumerically() {
        assertTrue(UpdateManager.compareVersions("1.3.0", "1.4.0") < 0);
        assertTrue(UpdateManager.compareVersions("1.4.0", "1.3.0") > 0);
        assertEquals(0, UpdateManager.compareVersions("1.4.0", "1.4.0"));
        // 1.10 > 1.9 numerically (not lexicographically)
        assertTrue(UpdateManager.compareVersions("1.9.0", "1.10.0") < 0);
        // missing components equal zero
        assertEquals(0, UpdateManager.compareVersions("1.4", "1.4.0"));
    }

    @Test
    public void tagsNormalize() {
        assertEquals(0, UpdateManager.compareVersions("v1.4.0", "1.4.0"));
        assertEquals(0, UpdateManager.compareVersions("1.4.0-dev", "1.4.0"));
        assertEquals(0, UpdateManager.compareVersions("V2.0.0", "2.0.0"));
        // build metadata (what Fabric reports: "1.4.0+26.1") must not read newer
        assertEquals(0, UpdateManager.compareVersions("1.4.0+26.1", "1.4.0"));
        assertTrue(UpdateManager.compareVersions("1.4.0+26.1", "1.3.0") > 0);
    }

    @Test
    public void mcMajorMinorExtracts() {
        assertEquals("26.1", UpdateManager.mcMajorMinor("26.1"));
        assertEquals("26.1", UpdateManager.mcMajorMinor("26.1.2"));
        assertEquals("26.3", UpdateManager.mcMajorMinor("26.3+fabric.0.19.5"));
        assertNull(UpdateManager.mcMajorMinor(null));
        assertNull(UpdateManager.mcMajorMinor("snapshot"));
    }

    @Test
    public void assetPickMatchesMcVersion() {
        List<String> names = List.of(
                "Moid-Client-v1.4.0+26.1.jar",
                "Moid-Client-v1.4.0+26.2.jar",
                "Moid-Client-v1.4.0+26.3.jar",
                "Moid-Client-v1.4.0+26.1.jar.sha256");
        assertEquals("Moid-Client-v1.4.0+26.2.jar",
                UpdateManager.pickAsset(names, "1.4.0", "26.2"));
        assertNull(UpdateManager.pickAsset(names, "1.4.0", "25.9"));
        assertNull(UpdateManager.pickAsset(names, "1.5.0", "26.2"));
    }

    @Test
    public void shaLinesParse() {
        // uppercase hex is lowercased
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                UpdateManager.parseShaLine(
                        "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855  f.jar"));
        assertEquals(64, UpdateManager.parseShaLine(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  f.jar").length());
        assertNull(UpdateManager.parseShaLine("not-a-hash  f.jar"));
        assertNull(UpdateManager.parseShaLine(null));
    }

    @Test
    public void jarNameVersionsParse() {
        assertEquals("1.4.0", UpdateManager.versionFromJarName("Moid-Client-v1.4.0+26.1.jar"));
        // tag suffixes ride along (parseVersion strips them for comparison)
        assertEquals("1.4.1-updatertest", UpdateManager.versionFromJarName("Moid-Client-v1.4.1-updatertest%2B26.1.jar"));
        assertNull(UpdateManager.versionFromJarName("sodium-1.2.jar"));
        assertNull(UpdateManager.versionFromJarName(null));
    }

    @Test
    public void assetUrlsDecodePlus() {
        // GitHub encodes '+' as %2B in browser_download_url - the staged
        // filename must be the real jar name or nothing matches afterwards.
        assertEquals("Moid-Client-v1.4.1-updatertest+26.1.jar",
                UpdateManager.pickAssetFromUrl(
                        "https://github.com/Moid-M/moidclient/releases/download/v1.4.1-updatertest/Moid-Client-v1.4.1-updatertest%2B26.1.jar"));
    }
}
