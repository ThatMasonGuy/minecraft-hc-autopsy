package tempeststudios.hcautopsy.stats;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import tempeststudios.hcautopsy.HCAutopsy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for capturing player statistics from Minecraft's stat files.
 *
 * Minecraft stores player stats in JSON files at:
 * <world>/stats/<player-uuid>.json
 *
 * This service reads these files verbatim to ensure 100% stat fidelity.
 */
public class StatSnapshotService {

    private final MinecraftServer server;

    public StatSnapshotService(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Get the path to the stats directory for the current world.
     */
    public Path getStatsDirectory() {
        return server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
    }

    /**
     * Capture the raw stat snapshot for a specific player.
     *
     * @param playerUuid The player's UUID
     * @return Raw JSON string of the player's stats, or null if not found
     */
    public String captureSnapshot(UUID playerUuid) {
        Path statsFile = getStatsDirectory().resolve(playerUuid.toString() + ".json");

        if (!Files.exists(statsFile)) {
            HCAutopsy.LOGGER.debug("No stat file found for player {}", playerUuid);
            return null;
        }

        try {
            return Files.readString(statsFile);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to read stat file for player {}: {}", playerUuid, e.getMessage());
            return null;
        }
    }

    /**
     * Capture snapshots for all players who have stat files in the world.
     *
     * @return Map of player UUID to their raw stat JSON
     */
    public Map<UUID, String> captureAllSnapshots() {
        Map<UUID, String> snapshots = new HashMap<>();
        Path statsDir = getStatsDirectory();

        if (!Files.exists(statsDir)) {
            HCAutopsy.LOGGER.debug("Stats directory does not exist: {}", statsDir);
            return snapshots;
        }

        try (var files = Files.list(statsDir)) {
            files
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String uuidStr = filename.substring(0, filename.length() - 5); // Remove .json

                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            String content = Files.readString(path);
                            snapshots.put(uuid, content);
                        } catch (IllegalArgumentException e) {
                            HCAutopsy.LOGGER.debug("Skipping non-UUID stat file: {}", filename);
                        } catch (IOException e) {
                            HCAutopsy.LOGGER.error("Failed to read stat file {}: {}", filename, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to list stats directory: {}", e.getMessage());
        }

        return snapshots;
    }

    /**
     * Capture snapshots for all currently online players.
     * This forces a stat save before capturing to ensure fresh data.
     *
     * @return Map of player UUID to their raw stat JSON
     */
    public Map<UUID, String> captureOnlinePlayerSnapshots() {
        // First, force all online players to save their stats
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getStats().save();
        }

        // Small delay to ensure file writes complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Now capture only online players
        Map<UUID, String> snapshots = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String snapshot = captureSnapshot(player.getUUID());
            if (snapshot != null) {
                snapshots.put(player.getUUID(), snapshot);
            }
        }

        return snapshots;
    }

    /**
     * Force a specific player to save their stats immediately.
     *
     * @param player The player to save stats for
     */
    public void forceStatSave(ServerPlayer player) {
        player.getStats().save();
    }

    /**
     * Force all online players to save their stats immediately.
     */
    public void forceAllStatSaves() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getStats().save();
        }
    }

    /**
     * Get the current world name (folder name).
     */
    public String getWorldName() {
        // First, try to get the level name from save properties (most reliable)
        String levelName = server.getWorldData().getLevelName();
        if (levelName != null && !levelName.isBlank() && !levelName.equals(".")) {
            return levelName;
        }

        // Try to get from the world path
        Path worldPath = server.getWorldPath(LevelResource.ROOT);

        try {
            // Resolve to absolute path and get the actual folder name
            Path absolutePath = worldPath.toAbsolutePath().normalize();
            String folderName = absolutePath.getFileName().toString();

            // If valid, use it
            if (folderName != null && !folderName.isBlank() && !folderName.equals(".")) {
                return folderName;
            }

            // If still ".", the absolute path itself might be the answer
            // e.g., /home/user/server/world -> "world"
            String fullPath = absolutePath.toString();
            if (fullPath.contains(java.io.File.separator)) {
                String[] parts = fullPath.split(java.util.regex.Pattern.quote(java.io.File.separator));
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isBlank() && !parts[i].equals(".")) {
                        return parts[i];
                    }
                }
            }
        } catch (Exception e) {
            HCAutopsy.LOGGER.debug("Failed to resolve world path: {}", e.getMessage());
        }

        // Last resort fallback
        return "world";
    }

    /**
     * Get a list of all player UUIDs that have stat files.
     */
    public java.util.List<UUID> getAllPlayerUuids() {
        java.util.List<UUID> uuids = new java.util.ArrayList<>();
        Path statsDir = getStatsDirectory();

        if (!Files.exists(statsDir)) {
            return uuids;
        }

        try (var files = Files.list(statsDir)) {
            files
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String uuidStr = filename.substring(0, filename.length() - 5);
                        try {
                            uuids.add(UUID.fromString(uuidStr));
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to list stats directory: {}", e.getMessage());
        }

        return uuids;
    }
}
