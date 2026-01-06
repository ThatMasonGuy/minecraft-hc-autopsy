package tempeststudios.hcautopsy.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Metadata about a hardcore run.
 * This is persisted alongside player snapshots.
 */
public class RunMetadata {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String runId;
    private final String worldName;
    private final long startedAt;
    private long endedAt;
    private RunState state;
    private WipeCause wipeCause;
    private final Set<UUID> participatingPlayers;
    private final List<ContinueRecord> continueHistory;

    /**
     * Record of a run being continued after a wipe.
     */
    public record ContinueRecord(
            long timestamp,
            UUID struckPlayerUuid,
            String struckPlayerName,
            String reason
    ) {
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("timestamp", timestamp);
            json.addProperty("struckPlayerUuid", struckPlayerUuid.toString());
            json.addProperty("struckPlayerName", struckPlayerName);
            json.addProperty("reason", reason);
            return json;
        }

        public static ContinueRecord fromJson(JsonObject json) {
            return new ContinueRecord(
                    json.get("timestamp").getAsLong(),
                    UUID.fromString(json.get("struckPlayerUuid").getAsString()),
                    json.get("struckPlayerName").getAsString(),
                    json.get("reason").getAsString()
            );
        }
    }

    /**
     * Create new run metadata for a fresh run.
     */
    public RunMetadata(String runId, String worldName) {
        this.runId = runId;
        this.worldName = worldName;
        this.startedAt = System.currentTimeMillis();
        this.endedAt = 0;
        this.state = RunState.ACTIVE;
        this.wipeCause = null;
        this.participatingPlayers = new HashSet<>();
        this.continueHistory = new ArrayList<>();
    }

    /**
     * Private constructor for deserialization.
     */
    private RunMetadata(
            String runId,
            String worldName,
            long startedAt,
            long endedAt,
            RunState state,
            WipeCause wipeCause,
            Set<UUID> participatingPlayers,
            List<ContinueRecord> continueHistory
    ) {
        this.runId = runId;
        this.worldName = worldName;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.state = state;
        this.wipeCause = wipeCause;
        this.participatingPlayers = participatingPlayers;
        this.continueHistory = continueHistory;
    }

    public String getRunId() {
        return runId;
    }

    public String getWorldName() {
        return worldName;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    public RunState getState() {
        return state;
    }

    public WipeCause getWipeCause() {
        return wipeCause;
    }

    public Set<UUID> getParticipatingPlayers() {
        return new HashSet<>(participatingPlayers);
    }

    public List<ContinueRecord> getContinueHistory() {
        return new ArrayList<>(continueHistory);
    }

    /**
     * Register a player as having participated in this run.
     */
    public void addParticipant(UUID playerUuid) {
        participatingPlayers.add(playerUuid);
    }

    /**
     * Mark the run as wiped due to player death.
     */
    public void markWiped(WipeCause cause) {
        if (state != RunState.ACTIVE) {
            throw new IllegalStateException("Cannot wipe a run that is not active. Current state: " + state);
        }
        this.wipeCause = cause;
        this.endedAt = cause.timestamp();
        this.state = RunState.WIPED;
    }

    /**
     * Continue a wiped run, striking the death from the record.
     */
    public void continueRun(String reason) {
        if (state != RunState.WIPED) {
            throw new IllegalStateException("Cannot continue a run that is not wiped. Current state: " + state);
        }

        ContinueRecord record = new ContinueRecord(
                System.currentTimeMillis(),
                wipeCause.playerUuid(),
                wipeCause.playerName(),
                reason
        );
        continueHistory.add(record);

        this.wipeCause = null;
        this.endedAt = 0;
        this.state = RunState.ACTIVE;
    }

    /**
     * Calculate the duration of this run in milliseconds.
     * Returns current duration if still active.
     */
    public long getDurationMs() {
        long end = (endedAt > 0) ? endedAt : System.currentTimeMillis();
        return end - startedAt;
    }

    /**
     * Serialize to JSON for persistence.
     */
    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("runId", runId);
        json.addProperty("worldName", worldName);
        json.addProperty("startedAt", startedAt);
        json.addProperty("endedAt", endedAt);
        json.addProperty("state", state.name());

        if (wipeCause != null) {
            json.add("wipeCause", wipeCause.toJson());
        }

        JsonArray playersArray = new JsonArray();
        for (UUID uuid : participatingPlayers) {
            playersArray.add(uuid.toString());
        }
        json.add("participatingPlayers", playersArray);

        JsonArray continueArray = new JsonArray();
        for (ContinueRecord record : continueHistory) {
            continueArray.add(record.toJson());
        }
        json.add("continueHistory", continueArray);

        return GSON.toJson(json);
    }

    /**
     * Deserialize from JSON.
     */
    public static RunMetadata fromJson(String jsonString) {
        JsonObject json = GSON.fromJson(jsonString, JsonObject.class);

        String runId = json.get("runId").getAsString();
        String worldName = json.get("worldName").getAsString();
        long startedAt = json.get("startedAt").getAsLong();
        long endedAt = json.get("endedAt").getAsLong();
        RunState state = RunState.valueOf(json.get("state").getAsString());

        WipeCause wipeCause = null;
        if (json.has("wipeCause") && !json.get("wipeCause").isJsonNull()) {
            wipeCause = WipeCause.fromJson(json.getAsJsonObject("wipeCause"));
        }

        Set<UUID> participatingPlayers = new HashSet<>();
        JsonArray playersArray = json.getAsJsonArray("participatingPlayers");
        for (var element : playersArray) {
            participatingPlayers.add(UUID.fromString(element.getAsString()));
        }

        List<ContinueRecord> continueHistory = new ArrayList<>();
        if (json.has("continueHistory")) {
            JsonArray continueArray = json.getAsJsonArray("continueHistory");
            for (var element : continueArray) {
                continueHistory.add(ContinueRecord.fromJson(element.getAsJsonObject()));
            }
        }

        return new RunMetadata(
                runId,
                worldName,
                startedAt,
                endedAt,
                state,
                wipeCause,
                participatingPlayers,
                continueHistory
        );
    }
}
