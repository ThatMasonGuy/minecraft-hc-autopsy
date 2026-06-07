package tempeststudios.hcautopsy.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import tempeststudios.hcautopsy.HCAutopsy;
import tempeststudios.hcautopsy.data.Run;
import tempeststudios.hcautopsy.data.RunMetadata;
import tempeststudios.hcautopsy.data.RunState;
import tempeststudios.hcautopsy.stats.AggregationEngine;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Manages all file I/O for HC Autopsy.
 *
 * Directory structure:
 * /config/hc-autopsy/
 * ├── runs/
 * │   ├── <world-name>__<timestamp>/
 * │   │   ├── metadata.json
 * │   │   ├── players/
 * │   │   │   └── <uuid>.json
 * │   │   └── aggregated.json
 * │   └── ...
 * ├── lifetime/
 * │   ├── players/
 * │   │   └── <uuid>.json
 * │   └── server.json
 * └── config.json
 */
public class PersistenceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private final Path baseDir;
    private final Path runsDir;
    private final Path lifetimeDir;
    private final Path lifetimePlayersDir;
    private final Path playerNamesPath;
    private final AggregationEngine aggregationEngine;
    private final Map<UUID, String> playerNameCache;

    public PersistenceManager() {
        this.baseDir = FabricLoader.getInstance().getConfigDir().resolve("hc-autopsy");
        this.runsDir = baseDir.resolve("runs");
        this.lifetimeDir = baseDir.resolve("lifetime");
        this.lifetimePlayersDir = lifetimeDir.resolve("players");
        this.playerNamesPath = lifetimeDir.resolve("player-names.json");
        this.aggregationEngine = new AggregationEngine();
        this.playerNameCache = new HashMap<>();

        initializeDirectories();
        loadPlayerNameCache();
    }

    /**
     * Create all necessary directories.
     */
    private void initializeDirectories() {
        try {
            Files.createDirectories(runsDir);
            Files.createDirectories(lifetimePlayersDir);
            HCAutopsy.LOGGER.info("HC Autopsy data directory initialized at: {}", baseDir);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to create HC Autopsy directories: {}", e.getMessage());
        }
    }

    /**
     * Generate a unique run ID based on world name and timestamp.
     */
    public String generateRunId(String worldName) {
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        // Sanitize world name for filesystem safety
        String sanitized = worldName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized + "__" + timestamp;
    }

    /**
     * Get the directory path for a specific run.
     */
    public Path getRunDirectory(String runId) {
        return runsDir.resolve(runId);
    }

    /**
     * Get the path to a run's metadata file.
     */
    private Path getMetadataPath(String runId) {
        return getRunDirectory(runId).resolve("metadata.json");
    }

    /**
     * Get the players directory for a run.
     */
    private Path getRunPlayersDir(String runId) {
        return getRunDirectory(runId).resolve("players");
    }

    /**
     * Get the path to a player's snapshot within a run.
     */
    private Path getPlayerSnapshotPath(String runId, UUID playerUuid) {
        return getRunPlayersDir(runId).resolve(playerUuid.toString() + ".json");
    }

    /**
     * Get the path to a run's aggregated stats.
     */
    private Path getRunAggregatedPath(String runId) {
        return getRunDirectory(runId).resolve("aggregated.json");
    }

    /**
     * Get the path to a player's lifetime stats.
     */
    private Path getLifetimePlayerPath(UUID playerUuid) {
        return lifetimePlayersDir.resolve(playerUuid.toString() + ".json");
    }

    /**
     * Get the path to server-wide lifetime stats.
     */
    private Path getServerLifetimePath() {
        return lifetimeDir.resolve("server.json");
    }

    // ==================== Run Operations ====================

    /**
     * Create a new run and save its initial metadata.
     */
    public RunMetadata createRun(String worldName) {
        String runId = generateRunId(worldName);
        RunMetadata metadata = new RunMetadata(runId, worldName);

        try {
            Path runDir = getRunDirectory(runId);
            Files.createDirectories(runDir);
            Files.createDirectories(getRunPlayersDir(runId));
            saveMetadata(metadata);
            HCAutopsy.LOGGER.info("Created new run: {} for world '{}'", runId, worldName);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to create run directory: {}", e.getMessage());
        }

        return metadata;
    }

    /**
     * Save run metadata.
     */
    public void saveMetadata(RunMetadata metadata) {
        Path path = getMetadataPath(metadata.getRunId());
        try {
            writeStringAtomic(path, metadata.toJson());
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to save run metadata: {}", e.getMessage());
        }
    }

    /**
     * Load run metadata by run ID.
     */
    public RunMetadata loadMetadata(String runId) {
        Path path = getMetadataPath(runId);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            String json = Files.readString(path);
            return RunMetadata.fromJson(json);
        } catch (IOException | RuntimeException e) {
            HCAutopsy.LOGGER.error("Failed to load run metadata: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Save a player's stat snapshot for a run.
     */
    public void savePlayerSnapshot(String runId, UUID playerUuid, String rawStats) {
        Path path = getPlayerSnapshotPath(runId, playerUuid);
        try {
            writeStringAtomic(path, rawStats);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to save player snapshot for {}: {}", playerUuid, e.getMessage());
        }
    }

    /**
     * Load a player's stat snapshot from a run.
     */
    public String loadPlayerSnapshot(String runId, UUID playerUuid) {
        Path path = getPlayerSnapshotPath(runId, playerUuid);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            return Files.readString(path);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to load player snapshot: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Load all player snapshots for a run.
     */
    public Map<UUID, String> loadAllPlayerSnapshots(String runId) {
        Map<UUID, String> snapshots = new HashMap<>();
        Path playersDir = getRunPlayersDir(runId);

        if (!Files.exists(playersDir)) {
            return snapshots;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersDir, "*.json")) {
            for (Path path : stream) {
                String filename = path.getFileName().toString();
                String uuidStr = filename.substring(0, filename.length() - 5);
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String content = Files.readString(path);
                    snapshots.put(uuid, content);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to load player snapshots: {}", e.getMessage());
        }

        return snapshots;
    }

    /**
     * Save aggregated stats for a run.
     */
    public void saveRunAggregated(String runId, String aggregatedStats) {
        Path path = getRunAggregatedPath(runId);
        try {
            writeStringAtomic(path, aggregatedStats);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to save run aggregated stats: {}", e.getMessage());
        }
    }

    /**
     * Load aggregated stats for a run.
     */
    public String loadRunAggregated(String runId) {
        Path path = getRunAggregatedPath(runId);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            return Files.readString(path);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to load run aggregated stats: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Load a complete run with metadata and all snapshots.
     */
    public Run loadRun(String runId) {
        RunMetadata metadata = loadMetadata(runId);
        if (metadata == null) {
            return null;
        }

        Map<UUID, String> snapshots = loadAllPlayerSnapshots(runId);
        return new Run(metadata, snapshots);
    }

    /**
     * Find a run by world name that is currently active.
     */
    public RunMetadata findActiveRunForWorld(String worldName) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(runsDir)) {
            for (Path runDir : stream) {
                if (!Files.isDirectory(runDir)) continue;

                String runId = runDir.getFileName().toString();
                RunMetadata metadata = loadMetadata(runId);

                if (metadata != null
                        && metadata.getWorldName().equals(worldName)
                        && metadata.getState() == RunState.ACTIVE) {
                    return metadata;
                }
            }
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to search for active run: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get all run IDs, sorted by creation time (newest first).
     */
    public List<String> getAllRunIds() {
        List<String> runIds = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(runsDir)) {
            for (Path runDir : stream) {
                if (Files.isDirectory(runDir)) {
                    runIds.add(runDir.getFileName().toString());
                }
            }
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to list runs: {}", e.getMessage());
        }

        runIds.sort((left, right) -> {
            RunMetadata leftMetadata = loadMetadata(left);
            RunMetadata rightMetadata = loadMetadata(right);
            if (leftMetadata != null && rightMetadata != null) {
                return Long.compare(rightMetadata.getStartedAt(), leftMetadata.getStartedAt());
            }
            if (leftMetadata != null) {
                return -1;
            }
            if (rightMetadata != null) {
                return 1;
            }
            return right.compareTo(left);
        });
        return runIds;
    }

    /**
     * Get the most recently created run.
     */
    public RunMetadata getLatestRun() {
        List<String> runIds = getAllRunIds();
        if (runIds.isEmpty()) {
            return null;
        }
        return loadMetadata(runIds.get(0));
    }

    /**
     * Get the most recently wiped run.
     */
    public RunMetadata getLastWipedRun() {
        for (String runId : getAllRunIds()) {
            RunMetadata metadata = loadMetadata(runId);
            if (metadata != null && metadata.getState() == RunState.WIPED) {
                return metadata;
            }
        }
        return null;
    }

    // ==================== Lifetime Stats ====================

    /**
     * Load a player's lifetime stats.
     */
    public String loadLifetimePlayerStats(UUID playerUuid) {
        Path path = getLifetimePlayerPath(playerUuid);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            return Files.readString(path);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to load lifetime stats for {}: {}", playerUuid, e.getMessage());
            return null;
        }
    }

    /**
     * Save a player's lifetime stats.
     */
    public void saveLifetimePlayerStats(UUID playerUuid, String stats) {
        Path path = getLifetimePlayerPath(playerUuid);
        try {
            writeStringAtomic(path, stats);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to save lifetime stats for {}: {}", playerUuid, e.getMessage());
        }
    }

    /**
     * Update lifetime stats by adding a run's stats to the existing totals.
     */
    public void updateLifetimeStats(Map<UUID, String> runSnapshots) {
        for (Map.Entry<UUID, String> entry : runSnapshots.entrySet()) {
            UUID playerUuid = entry.getKey();
            String runStats = entry.getValue();

            String existingLifetime = loadLifetimePlayerStats(playerUuid);
            String newLifetime;

            if (existingLifetime == null) {
                newLifetime = runStats;
            } else {
                newLifetime = aggregationEngine.aggregate(List.of(existingLifetime, runStats));
            }

            saveLifetimePlayerStats(playerUuid, newLifetime);
        }

        // Also update server-wide lifetime
        updateServerLifetimeStats(runSnapshots.values());
    }

    /**
     * Load server-wide lifetime stats.
     */
    public String loadServerLifetimeStats() {
        Path path = getServerLifetimePath();
        if (!Files.exists(path)) {
            return null;
        }

        try {
            return Files.readString(path);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to load server lifetime stats: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Update server-wide lifetime stats.
     */
    private void updateServerLifetimeStats(Collection<String> runSnapshots) {
        String existingLifetime = loadServerLifetimeStats();
        List<String> toAggregate = new ArrayList<>(runSnapshots);

        if (existingLifetime != null) {
            toAggregate.add(0, existingLifetime);
        }

        String newLifetime = aggregationEngine.aggregate(toAggregate);

        try {
            writeStringAtomic(getServerLifetimePath(), newLifetime);
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to save server lifetime stats: {}", e.getMessage());
        }
    }

    /**
     * Get all player UUIDs that have lifetime stats.
     */
    public List<UUID> getAllLifetimePlayerUuids() {
        List<UUID> uuids = new ArrayList<>();

        if (!Files.exists(lifetimePlayersDir)) {
            return uuids;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(lifetimePlayersDir, "*.json")) {
            for (Path path : stream) {
                String filename = path.getFileName().toString();
                String uuidStr = filename.substring(0, filename.length() - 5);
                try {
                    uuids.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to list lifetime players: {}", e.getMessage());
        }

        return uuids;
    }

    /**
     * Recalculate all lifetime stats from scratch based on WIPED runs only.
     * This is called when a run is "continued" to remove its stats from lifetime totals.
     */
    public void recalculateLifetimeStats() {
        HCAutopsy.LOGGER.info("Recalculating lifetime stats from all wiped runs...");

        // Clear existing lifetime stats
        try {
            if (Files.exists(lifetimePlayersDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(lifetimePlayersDir, "*.json")) {
                    for (Path path : stream) {
                        Files.delete(path);
                    }
                }
            }
            Files.deleteIfExists(getServerLifetimePath());
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to clear lifetime stats: {}", e.getMessage());
        }

        // Collect all snapshots from WIPED runs only
        Map<UUID, List<String>> playerSnapshots = new HashMap<>();
        List<String> allSnapshots = new ArrayList<>();

        for (String runId : getAllRunIds()) {
            RunMetadata metadata = loadMetadata(runId);
            if (metadata == null || metadata.getState() != RunState.WIPED) {
                continue; // Skip non-wiped runs (ACTIVE or CONTINUED)
            }

            Map<UUID, String> runSnapshots = loadAllPlayerSnapshots(runId);
            for (Map.Entry<UUID, String> entry : runSnapshots.entrySet()) {
                playerSnapshots.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(entry.getValue());
                allSnapshots.add(entry.getValue());
            }
        }

        // Aggregate per-player lifetime stats
        for (Map.Entry<UUID, List<String>> entry : playerSnapshots.entrySet()) {
            String aggregated = aggregationEngine.aggregate(entry.getValue());
            saveLifetimePlayerStats(entry.getKey(), aggregated);
        }

        // Aggregate server-wide lifetime stats
        if (!allSnapshots.isEmpty()) {
            String serverLifetime = aggregationEngine.aggregate(allSnapshots);
            try {
                writeStringAtomic(getServerLifetimePath(), serverLifetime);
            } catch (IOException e) {
                HCAutopsy.LOGGER.error("Failed to save recalculated server lifetime stats: {}", e.getMessage());
            }
        }

        HCAutopsy.LOGGER.info("Lifetime stats recalculated from {} wiped runs",
                getAllRunIds().stream()
                        .map(this::loadMetadata)
                        .filter(m -> m != null && m.getState() == RunState.WIPED)
                        .count());
    }

    // ==================== Utility ====================

    /**
     * Get the base directory path.
     */
    public Path getBaseDirectory() {
        return baseDir;
    }

    public synchronized void rememberPlayer(UUID playerUuid, String playerName) {
        if (playerUuid == null || playerName == null || playerName.isBlank()) {
            return;
        }

        String previousName = playerNameCache.put(playerUuid, playerName);
        if (!playerName.equals(previousName)) {
            savePlayerNameCache();
        }
    }

    public synchronized String getKnownPlayerName(UUID playerUuid) {
        return playerNameCache.get(playerUuid);
    }

    public synchronized UUID findKnownPlayerUuidByName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        for (Map.Entry<UUID, String> entry : playerNameCache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(playerName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized List<String> getKnownPlayerNames() {
        return playerNameCache.values().stream()
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Check if any runs exist.
     */
    public boolean hasAnyRuns() {
        return !getAllRunIds().isEmpty();
    }

    private void loadPlayerNameCache() {
        if (!Files.exists(playerNamesPath)) {
            return;
        }

        try {
            JsonObject json = GSON.fromJson(Files.readString(playerNamesPath), JsonObject.class);
            if (json == null) {
                return;
            }
            for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                try {
                    playerNameCache.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException | RuntimeException e) {
            HCAutopsy.LOGGER.error("Failed to load player name cache: {}", e.getMessage());
        }
    }

    private synchronized void savePlayerNameCache() {
        JsonObject json = new JsonObject();
        playerNameCache.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().toLowerCase(Locale.ROOT)))
                .forEach(entry -> json.addProperty(entry.getKey().toString(), entry.getValue()));

        try {
            writeStringAtomic(playerNamesPath, GSON.toJson(json));
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to save player name cache: {}", e.getMessage());
        }
    }

    private void writeStringAtomic(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path tempPath = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tempPath, content);
            try {
                Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }
}
