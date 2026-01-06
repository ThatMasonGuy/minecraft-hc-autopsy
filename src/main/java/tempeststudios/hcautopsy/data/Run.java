package tempeststudios.hcautopsy.data;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a complete hardcore run with its metadata and player snapshots.
 * This is primarily a data transfer object used when loading run data.
 */
public class Run {
    private final RunMetadata metadata;
    private final Map<UUID, String> playerSnapshots; // UUID -> raw JSON snapshot

    public Run(RunMetadata metadata, Map<UUID, String> playerSnapshots) {
        this.metadata = metadata;
        this.playerSnapshots = playerSnapshots;
    }

    public RunMetadata getMetadata() {
        return metadata;
    }

    public String getRunId() {
        return metadata.getRunId();
    }

    public String getWorldName() {
        return metadata.getWorldName();
    }

    public RunState getState() {
        return metadata.getState();
    }

    public boolean isActive() {
        return metadata.getState().isTracking();
    }

    public boolean isWiped() {
        return metadata.getState().isTerminated();
    }

    public Set<UUID> getParticipatingPlayers() {
        return metadata.getParticipatingPlayers();
    }

    public int getPlayerCount() {
        return metadata.getParticipatingPlayers().size();
    }

    /**
     * Get the raw JSON snapshot for a specific player.
     * Returns null if no snapshot exists for this player.
     */
    public String getPlayerSnapshot(UUID playerUuid) {
        return playerSnapshots.get(playerUuid);
    }

    /**
     * Get all player snapshots.
     * Returns a map of player UUID to their raw JSON stat snapshot.
     */
    public Map<UUID, String> getAllPlayerSnapshots() {
        return Map.copyOf(playerSnapshots);
    }

    /**
     * Check if we have a snapshot for a specific player.
     */
    public boolean hasPlayerSnapshot(UUID playerUuid) {
        return playerSnapshots.containsKey(playerUuid);
    }
}
