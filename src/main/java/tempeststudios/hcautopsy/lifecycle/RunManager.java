package tempeststudios.hcautopsy.lifecycle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import tempeststudios.hcautopsy.HCAutopsy;
import tempeststudios.hcautopsy.data.RunMetadata;
import tempeststudios.hcautopsy.data.RunState;
import tempeststudios.hcautopsy.data.WipeCause;
import tempeststudios.hcautopsy.notification.DiscordNotifier;
import tempeststudios.hcautopsy.persistence.PersistenceManager;
import tempeststudios.hcautopsy.stats.AggregationEngine;
import tempeststudios.hcautopsy.stats.StatSnapshotService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle of hardcore runs.
 *
 * Responsibilities:
 * - Starting new runs when a world loads
 * - Detecting and processing deaths (wipes)
 * - Snapshotting stats at wipe time
 * - Handling run continuation
 */
public class RunManager {
    private static final int STAT_SAVE_DELAY_MS = 500; // Delay after forcing stat save before snapshot

    private final MinecraftServer server;
    private final PersistenceManager persistence;
    private final StatSnapshotService statService;
    private final AggregationEngine aggregationEngine;
    private final DiscordNotifier discordNotifier;
    private final ScheduledExecutorService executor;

    private RunMetadata activeRun;
    private final AtomicBoolean wipeInProgress = new AtomicBoolean(false);

    public RunManager(MinecraftServer server, PersistenceManager persistence, DiscordNotifier discordNotifier) {
        this.server = server;
        this.persistence = persistence;
        this.statService = new StatSnapshotService(server);
        this.aggregationEngine = new AggregationEngine();
        this.discordNotifier = discordNotifier;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hc-autopsy-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialize the run manager when the server starts.
     * Either resumes an existing active run or creates a new one.
     */
    public void onServerStart() {
        String worldName = statService.getWorldName();
        HCAutopsy.LOGGER.info("HC Autopsy initializing for world: {}", worldName);

        // Check if there's an active run for this world
        activeRun = persistence.findActiveRunForWorld(worldName);

        if (activeRun != null) {
            HCAutopsy.LOGGER.info("Resuming existing run: {} (started {})",
                    activeRun.getRunId(), formatTimestamp(activeRun.getStartedAt()));
        } else {
            // Create a new run
            activeRun = persistence.createRun(worldName);
            HCAutopsy.LOGGER.info("Created new run: {}", activeRun.getRunId());
        }
    }

    /**
     * Clean shutdown of the run manager.
     */
    public void onServerStop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Save current run state if active
        if (activeRun != null) {
            persistence.saveMetadata(activeRun);
        }
    }

    /**
     * Register a player as participating in the current run.
     * Called when players join the server.
     */
    public void registerPlayer(ServerPlayer player) {
        if (activeRun != null && activeRun.getState().isTracking()) {
            activeRun.addParticipant(player.getUUID());
            persistence.saveMetadata(activeRun);
        }
    }

    /**
     * Handle a player death. Returns true if this death caused a wipe.
     */
    public boolean onPlayerDeath(ServerPlayer player, Component deathMessage, String damageSourceType,
                                 String attackerType, String attackerName) {
        // Quick check: is there an active run?
        if (activeRun == null || !activeRun.getState().isTracking()) {
            HCAutopsy.LOGGER.debug("Ignoring death - no active run");
            return false;
        }

        // Atomic check to prevent race conditions with multiple rapid deaths
        if (!wipeInProgress.compareAndSet(false, true)) {
            HCAutopsy.LOGGER.debug("Ignoring death - wipe already in progress");
            return false;
        }

        HCAutopsy.LOGGER.info("=== WORLD WIPE DETECTED ===");
        HCAutopsy.LOGGER.info("Player {} died: {}", player.getName().getString(), deathMessage.getString());

        try {
            // Create wipe cause record
            WipeCause wipeCause = WipeCause.create(
                    player.getUUID(),
                    player.getName().getString(),
                    deathMessage.getString(),
                    damageSourceType,
                    attackerType,
                    attackerName
            );

            // Mark run as wiped immediately (locks it from future deaths)
            activeRun.markWiped(wipeCause);
            persistence.saveMetadata(activeRun);

            // Schedule stat capture after a short delay to ensure disk consistency
            final RunMetadata wipedRun = activeRun;
            executor.schedule(() -> captureWipeStats(wipedRun), STAT_SAVE_DELAY_MS, TimeUnit.MILLISECONDS);

            return true;
        } catch (Exception e) {
            HCAutopsy.LOGGER.error("Failed to process wipe: {}", e.getMessage(), e);
            wipeInProgress.set(false);
            return false;
        }
    }

    /**
     * Capture all player stats after a wipe.
     * Runs on background thread after a short delay.
     */
    private void captureWipeStats(RunMetadata wipedRun) {
        try {
            HCAutopsy.LOGGER.info("Capturing wipe stats for run: {}", wipedRun.getRunId());

            // Force all online players to save their stats
            server.execute(statService::forceAllStatSaves);

            // Wait for disk writes
            Thread.sleep(STAT_SAVE_DELAY_MS);

            // Capture all stat files
            Map<UUID, String> snapshots = statService.captureAllSnapshots();
            HCAutopsy.LOGGER.info("Captured {} player snapshots", snapshots.size());

            // Save individual snapshots
            for (Map.Entry<UUID, String> entry : snapshots.entrySet()) {
                persistence.savePlayerSnapshot(wipedRun.getRunId(), entry.getKey(), entry.getValue());
            }

            // Generate and save aggregated stats for this run
            String aggregated = aggregationEngine.aggregate(snapshots.values());
            persistence.saveRunAggregated(wipedRun.getRunId(), aggregated);

            // Update lifetime stats
            persistence.updateLifetimeStats(snapshots);

            HCAutopsy.LOGGER.info("Wipe stats captured and lifetime stats updated");

            // Send Discord notification
            if (discordNotifier != null && discordNotifier.isConfigured()) {
                discordNotifier.sendWipeNotification(wipedRun, snapshots.size(), aggregated);
            }

        } catch (Exception e) {
            HCAutopsy.LOGGER.error("Failed to capture wipe stats: {}", e.getMessage(), e);
        } finally {
            wipeInProgress.set(false);
        }
    }

    /**
     * Continue a wiped run, striking the death from the record.
     *
     * @param reason The reason for continuing (e.g., "game bug", "fell through world")
     * @return true if successful, false if no wiped run exists
     */
    public boolean continueRun(String reason) {
        if (activeRun == null || activeRun.getState() != RunState.WIPED) {
            return false;
        }

        HCAutopsy.LOGGER.info("Continuing run {} - reason: {}", activeRun.getRunId(), reason);

        // Mark the run as continued (changes state from WIPED to ACTIVE)
        activeRun.continueRun(reason);
        persistence.saveMetadata(activeRun);

        // Recalculate lifetime stats from all WIPED runs (excluding this now-continued run)
        // This effectively "rolls back" the stats that were added when this run wiped
        persistence.recalculateLifetimeStats();

        HCAutopsy.LOGGER.info("Run continued and lifetime stats recalculated");

        return true;
    }

    /**
     * Get the currently active run, if any.
     */
    public RunMetadata getActiveRun() {
        return activeRun;
    }

    /**
     * Check if there's currently an active run.
     */
    public boolean hasActiveRun() {
        return activeRun != null && activeRun.getState().isTracking();
    }

    /**
     * Check if a wipe is currently being processed.
     */
    public boolean isWipeInProgress() {
        return wipeInProgress.get();
    }

    /**
     * Format a timestamp for logging.
     */
    private String formatTimestamp(long timestamp) {
        return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}