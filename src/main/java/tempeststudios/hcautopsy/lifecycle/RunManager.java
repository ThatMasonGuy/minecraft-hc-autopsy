package tempeststudios.hcautopsy.lifecycle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import tempeststudios.hcautopsy.HCAutopsy;
import tempeststudios.hcautopsy.config.ModConfig;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private static final int DEFAULT_STAT_SAVE_DELAY_MS = 500;
    private static final int STAT_SAVE_TIMEOUT_MS = 10_000;

    private final MinecraftServer server;
    private final PersistenceManager persistence;
    private final StatSnapshotService statService;
    private final AggregationEngine aggregationEngine;
    private final ScheduledExecutorService executor;
    private final Object runLock = new Object();

    private DiscordNotifier discordNotifier;
    private int statSaveDelayMs;
    private RunMetadata activeRun;
    private final AtomicBoolean wipeInProgress = new AtomicBoolean(false);

    public RunManager(
            MinecraftServer server,
            PersistenceManager persistence,
            DiscordNotifier discordNotifier,
            ModConfig config
    ) {
        this.server = server;
        this.persistence = persistence;
        this.statService = new StatSnapshotService(server);
        this.aggregationEngine = new AggregationEngine();
        this.discordNotifier = discordNotifier;
        this.statSaveDelayMs = config != null ? config.getStatSaveDelayMs() : DEFAULT_STAT_SAVE_DELAY_MS;
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

        synchronized (runLock) {
            activeRun = persistence.findActiveRunForWorld(worldName);

            if (activeRun != null) {
                HCAutopsy.LOGGER.info("Resuming existing run: {} (started {})",
                        activeRun.getRunId(), formatTimestamp(activeRun.getStartedAt()));
            } else {
                activeRun = persistence.createRun(worldName);
                HCAutopsy.LOGGER.info("Created new run: {}", activeRun.getRunId());
            }
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
        synchronized (runLock) {
            if (activeRun != null) {
                persistence.saveMetadata(activeRun);
            }
        }
    }

    /**
     * Register a player as participating in the current run.
     * Called when players join the server.
     */
    public void registerPlayer(ServerPlayer player) {
        persistence.rememberPlayer(player.getUUID(), player.getName().getString());

        synchronized (runLock) {
            if (activeRun != null && activeRun.getState().isTracking()) {
                activeRun.addParticipant(player.getUUID());
                persistence.saveMetadata(activeRun);
            }
        }
    }

    /**
     * Handle a player death. Returns true if this death caused a wipe.
     */
    public boolean onPlayerDeath(ServerPlayer player, Component deathMessage, String damageSourceType,
                                 String attackerType, String attackerName) {
        persistence.rememberPlayer(player.getUUID(), player.getName().getString());
        try {
            RunMetadata wipedRun;
            synchronized (runLock) {
                if (activeRun == null || !activeRun.getState().isTracking()) {
                    HCAutopsy.LOGGER.debug("Ignoring death - no active run");
                    return false;
                }

                if (!wipeInProgress.compareAndSet(false, true)) {
                    HCAutopsy.LOGGER.debug("Ignoring death - wipe already in progress");
                    return false;
                }

                HCAutopsy.LOGGER.info("=== WORLD WIPE DETECTED ===");
                HCAutopsy.LOGGER.info("Player {} died: {}", player.getName().getString(), deathMessage.getString());

                WipeCause wipeCause = WipeCause.create(
                        player.getUUID(),
                        player.getName().getString(),
                        deathMessage.getString(),
                        damageSourceType,
                        attackerType,
                        attackerName
                );

                activeRun.addParticipant(player.getUUID());
                activeRun.markWiped(wipeCause);
                persistence.saveMetadata(activeRun);
                wipedRun = activeRun;
            }

            executor.schedule(() -> captureWipeStats(wipedRun), statSaveDelayMs, TimeUnit.MILLISECONDS);

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

            forceAllStatSavesOnServerThread();
            Thread.sleep(statSaveDelayMs);

            Map<UUID, String> snapshots = statService.captureAllSnapshots();
            HCAutopsy.LOGGER.info("Captured {} player snapshots", snapshots.size());

            // Save individual snapshots
            for (Map.Entry<UUID, String> entry : snapshots.entrySet()) {
                persistence.savePlayerSnapshot(wipedRun.getRunId(), entry.getKey(), entry.getValue());
            }

            // Generate and save aggregated stats for this run
            String aggregated = aggregationEngine.aggregate(snapshots.values());
            persistence.saveRunAggregated(wipedRun.getRunId(), aggregated);

            synchronized (runLock) {
                if (activeRun != wipedRun || wipedRun.getState() != RunState.WIPED) {
                    HCAutopsy.LOGGER.info(
                            "Skipping lifetime update for {} because the run is no longer the active wiped run",
                            wipedRun.getRunId()
                    );
                    return;
                }
                persistence.updateLifetimeStats(snapshots);
            }

            HCAutopsy.LOGGER.info("Wipe stats captured and lifetime stats updated");
            broadcastWipeSummary(wipedRun, snapshots, aggregated);

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

    private void forceAllStatSavesOnServerThread() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> saveFuture = new CompletableFuture<>();
        server.execute(() -> {
            try {
                statService.forceAllStatSaves();
                saveFuture.complete(null);
            } catch (Throwable throwable) {
                saveFuture.completeExceptionally(throwable);
            }
        });
        saveFuture.get(Math.max(STAT_SAVE_TIMEOUT_MS, statSaveDelayMs * 4L), TimeUnit.MILLISECONDS);
    }

    private void broadcastWipeSummary(RunMetadata wipedRun, Map<UUID, String> snapshots, String aggregated) {
        WipeCause cause = wipedRun.getWipeCause();
        if (cause == null) {
            return;
        }

        String topPlayer = null;
        long topPlayTime = -1;
        for (Map.Entry<UUID, String> snapshot : snapshots.entrySet()) {
            Long playTime = aggregationEngine.extractStat(
                    snapshot.getValue(),
                    "stats.minecraft:custom.minecraft:play_time"
            );
            if (playTime != null && playTime > topPlayTime) {
                topPlayTime = playTime;
                topPlayer = playerDisplayName(snapshot.getKey());
            }
        }

        Long totalPlayTime = aggregationEngine.extractStat(aggregated, "stats.minecraft:custom.minecraft:play_time");
        Long deaths = aggregationEngine.extractStat(aggregated, "stats.minecraft:custom.minecraft:deaths");

        Component header = Component.literal("[HC Autopsy] Hardcore run wiped")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component death = Component.literal("Wipe: " + cause.playerName() + " - " + cause.deathMessage())
                .withStyle(ChatFormatting.GRAY);
        Component duration = Component.literal("Duration: " + formatDuration(wipedRun.getDurationMs())
                + " | Players snapshotted: " + snapshots.size())
                .withStyle(ChatFormatting.GOLD);
        Component totals = Component.literal("Total playtime: "
                + (totalPlayTime == null ? "unknown" : formatDuration((totalPlayTime / 20) * 1000))
                + " | Deaths recorded: "
                + (deaths == null ? "unknown" : deaths))
                .withStyle(ChatFormatting.AQUA);

        Component leader = topPlayer == null
                ? Component.literal("Top playtime: unknown").withStyle(ChatFormatting.YELLOW)
                : Component.literal("Top playtime: " + topPlayer + " ("
                + formatDuration((topPlayTime / 20) * 1000) + ")").withStyle(ChatFormatting.YELLOW);

        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(header);
                player.sendSystemMessage(death);
                player.sendSystemMessage(duration);
                player.sendSystemMessage(totals);
                player.sendSystemMessage(leader);
            }
        });
    }

    /**
     * Continue a wiped run, striking the death from the record.
     *
     * @param reason The reason for continuing (e.g., "game bug", "fell through world")
     * @return true if successful, false if no wiped run exists
     */
    public ContinueResult continueRun(String reason) {
        synchronized (runLock) {
            if (wipeInProgress.get()) {
                return ContinueResult.WIPE_FINALIZING;
            }
            if (activeRun == null || activeRun.getState() != RunState.WIPED) {
                return ContinueResult.NO_WIPED_RUN;
            }

            HCAutopsy.LOGGER.info("Continuing run {} - reason: {}", activeRun.getRunId(), reason);

            activeRun.continueRun(reason);
            persistence.saveMetadata(activeRun);
        }

        // Recalculate lifetime stats from all WIPED runs (excluding this now-continued run)
        // This effectively "rolls back" the stats that were added when this run wiped
        persistence.recalculateLifetimeStats();

        HCAutopsy.LOGGER.info("Run continued and lifetime stats recalculated");

        return ContinueResult.SUCCESS;
    }

    /**
     * Get the currently active run, if any.
     */
    public RunMetadata getActiveRun() {
        synchronized (runLock) {
            return activeRun;
        }
    }

    /**
     * Check if there's currently an active run.
     */
    public boolean hasActiveRun() {
        synchronized (runLock) {
            return activeRun != null && activeRun.getState().isTracking();
        }
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

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private String playerDisplayName(UUID playerUuid) {
        String knownName = persistence.getKnownPlayerName(playerUuid);
        if (knownName != null && !knownName.isBlank()) {
            return knownName;
        }
        return playerUuid.toString();
    }

    public void setDiscordNotifier(DiscordNotifier discordNotifier) {
        this.discordNotifier = discordNotifier;
    }

    public void setStatSaveDelayMs(int statSaveDelayMs) {
        this.statSaveDelayMs = Math.max(0, statSaveDelayMs);
    }

    public enum ContinueResult {
        SUCCESS,
        NO_WIPED_RUN,
        WIPE_FINALIZING
    }
}
