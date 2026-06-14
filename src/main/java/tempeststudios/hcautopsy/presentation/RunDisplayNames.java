package tempeststudios.hcautopsy.presentation;

import java.util.Locale;

/**
 * Keeps persisted run identifiers intact while making player-facing names tidy.
 */
public final class RunDisplayNames {
    private static final String WORLDS_PATH_PREFIX = "worlds/";
    private static final String WORLDS_ID_PREFIX = "worlds_";

    private RunDisplayNames() {
    }

    public static String world(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "Unknown";
        }

        String trimmed = worldName.trim();
        String normalized = trimmed.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        if (normalized.toLowerCase(Locale.ROOT).startsWith(WORLDS_PATH_PREFIX)) {
            String withoutPrefix = normalized.substring(WORLDS_PATH_PREFIX.length());
            if (!withoutPrefix.isBlank()) {
                return withoutPrefix;
            }
        }

        return trimmed;
    }

    public static String runId(String runId) {
        if (runId == null || runId.isBlank()) {
            return "Unknown";
        }

        String trimmed = runId.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(WORLDS_ID_PREFIX)) {
            String withoutPrefix = trimmed.substring(WORLDS_ID_PREFIX.length());
            if (!withoutPrefix.isBlank()) {
                return withoutPrefix;
            }
        }

        return trimmed;
    }
}
