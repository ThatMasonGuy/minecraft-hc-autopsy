package tempeststudios.hcautopsy.storage;

import net.fabricmc.loader.api.FabricLoader;
import tempeststudios.hcautopsy.HCAutopsy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Resolves HC Autopsy's launcher-agnostic app-data directory.
 */
public final class HCAutopsyData {
    private static final String DATA_DIR_PROPERTY = "hcautopsy.dataDir";
    private static final String STUDIO_DESKTOP_FOLDER = "TempestStudios";
    private static final String STUDIO_LINUX_FOLDER = "tempest-studios";
    private static final String MOD_DESKTOP_FOLDER = "HC-Autopsy";
    private static final String MOD_LINUX_FOLDER = "hc-autopsy";
    private static final List<String> TRACKED_CHILDREN = List.of("config.json", "runs", "lifetime");

    private HCAutopsyData() {
    }

    public static Path dataDir() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }

        return resolveDataDir(
                System.getProperty("os.name", ""),
                System.getProperty("user.home", "."),
                System.getenv("APPDATA"),
                System.getenv("XDG_DATA_HOME")
        );
    }

    public static Path legacyConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_LINUX_FOLDER);
    }

    public static void migrateLegacyConfigDataIfNeeded() {
        Path legacyDir = legacyConfigDir();
        Path dataDir = dataDir();
        if (samePath(legacyDir, dataDir) || !hasTrackedData(legacyDir)) {
            return;
        }

        if (hasTrackedData(dataDir)) {
            HCAutopsy.LOGGER.info(
                    "Found launcher-local HC Autopsy data at {} but app-data storage already exists at {}; leaving legacy data untouched.",
                    legacyDir.toAbsolutePath(),
                    dataDir.toAbsolutePath()
            );
            return;
        }

        try {
            copyDirectory(legacyDir, dataDir);
            HCAutopsy.LOGGER.info(
                    "Migrated launcher-local HC Autopsy data from {} to {}. Old files were left untouched.",
                    legacyDir.toAbsolutePath(),
                    dataDir.toAbsolutePath()
            );
        } catch (IOException | RuntimeException e) {
            HCAutopsy.LOGGER.warn(
                    "Failed to migrate launcher-local HC Autopsy data from {} to {}: {}",
                    legacyDir.toAbsolutePath(),
                    dataDir.toAbsolutePath(),
                    e.getMessage()
            );
        }
    }

    static Path resolveDataDir(String osName, String userHome, String appData, String xdgDataHome) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String home = userHome == null || userHome.isBlank() ? "." : userHome;

        if (normalizedOs.contains("win")) {
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, STUDIO_DESKTOP_FOLDER, MOD_DESKTOP_FOLDER);
            }
            return Path.of(home, "AppData", "Roaming", STUDIO_DESKTOP_FOLDER, MOD_DESKTOP_FOLDER);
        }

        if (normalizedOs.contains("mac")) {
            return Path.of(home, "Library", "Application Support", STUDIO_DESKTOP_FOLDER, MOD_DESKTOP_FOLDER);
        }

        Path base = xdgDataHome != null && !xdgDataHome.isBlank()
                ? Path.of(xdgDataHome)
                : Path.of(home, ".local", "share");
        return base.resolve(STUDIO_LINUX_FOLDER).resolve(MOD_LINUX_FOLDER);
    }

    static boolean hasTrackedData(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }

        for (String child : TRACKED_CHILDREN) {
            if (hasDataAt(dir.resolve(child))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDataAt(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                return Files.size(path) > 0;
            } catch (IOException ignored) {
                return true;
            }
        }

        if (!Files.isDirectory(path)) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            return paths.anyMatch(Files::isRegularFile);
        } catch (IOException ignored) {
            return true;
        }
    }

    private static boolean samePath(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
    }

    private static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            try {
                paths.forEach(source -> copyPath(sourceDir, source, targetDir));
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
    }

    private static void copyPath(Path sourceDir, Path source, Path targetDir) {
        try {
            Path target = targetDir.resolve(sourceDir.relativize(source));
            if (Files.isDirectory(source)) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
