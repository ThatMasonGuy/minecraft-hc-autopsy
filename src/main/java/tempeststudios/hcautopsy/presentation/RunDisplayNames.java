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

    public static String runId(String runId, String worldName) {
        String displayedRunId = runId(runId);
        if (runId == null || runId.isBlank() || worldName == null || worldName.isBlank()) {
            return displayedRunId;
        }

        String trimmedWorldName = worldName.trim();
        String displayedWorldName = world(trimmedWorldName);
        if (trimmedWorldName.equals(displayedWorldName)) {
            return runId.trim();
        }

        String originalPrefix = sanitizeRunIdWorldPrefix(trimmedWorldName) + "__";
        String displayedPrefix = sanitizeRunIdWorldPrefix(displayedWorldName) + "__";
        String trimmedRunId = runId.trim();
        if (trimmedRunId.startsWith(originalPrefix)) {
            return displayedPrefix + trimmedRunId.substring(originalPrefix.length());
        }
        return displayedRunId;
    }

    private static String sanitizeRunIdWorldPrefix(String worldName) {
        return worldName.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
