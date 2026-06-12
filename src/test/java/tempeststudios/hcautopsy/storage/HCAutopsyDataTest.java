package tempeststudios.hcautopsy.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HCAutopsyDataTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesWindowsAppDataFolder() {
        Path resolved = HCAutopsyData.resolveDataDir(
                "Windows 11",
                "C:\\Users\\Mason",
                "C:\\Users\\Mason\\AppData\\Roaming",
                null
        );

        assertEquals(Path.of("C:\\Users\\Mason\\AppData\\Roaming", "TempestStudios", "HC-Autopsy"), resolved);
    }

    @Test
    void systemPropertyOverridesDataFolder() {
        String previous = System.getProperty("hcautopsy.dataDir");
        try {
            System.setProperty("hcautopsy.dataDir", tempDir.toString());

            assertEquals(tempDir, HCAutopsyData.dataDir());
        } finally {
            if (previous == null) {
                System.clearProperty("hcautopsy.dataDir");
            } else {
                System.setProperty("hcautopsy.dataDir", previous);
            }
        }
    }

    @Test
    void resolvesMacApplicationSupportFolder() {
        Path resolved = HCAutopsyData.resolveDataDir(
                "Mac OS X",
                "/Users/mason",
                null,
                null
        );

        assertEquals(Path.of("/Users/mason", "Library", "Application Support", "TempestStudios", "HC-Autopsy"), resolved);
    }

    @Test
    void resolvesLinuxXdgFolder() {
        Path resolved = HCAutopsyData.resolveDataDir(
                "Linux",
                "/home/mason",
                null,
                "/var/data"
        );

        assertEquals(Path.of("/var/data", "tempest-studios", "hc-autopsy"), resolved);
    }

    @Test
    void resolvesLinuxLocalShareFallback() {
        Path resolved = HCAutopsyData.resolveDataDir(
                "Linux",
                "/home/mason",
                null,
                null
        );

        assertEquals(Path.of("/home/mason", ".local", "share", "tempest-studios", "hc-autopsy"), resolved);
    }

    @Test
    void emptyDirectoriesDoNotCountAsTrackedData() throws Exception {
        Files.createDirectories(tempDir.resolve("runs"));

        assertFalse(HCAutopsyData.hasTrackedData(tempDir));
    }

    @Test
    void runFilesCountAsTrackedData() throws Exception {
        Path metadata = tempDir.resolve("runs").resolve("world__20260613-120000").resolve("metadata.json");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, "{}");

        assertTrue(HCAutopsyData.hasTrackedData(tempDir));
    }
}
