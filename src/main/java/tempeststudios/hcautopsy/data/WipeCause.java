package tempeststudios.hcautopsy.data;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Immutable record capturing the cause of a hardcore world wipe.
 * This represents the first death that ended the run.
 */
public record WipeCause(
        UUID playerUuid,
        String playerName,
        String deathMessage,
        String damageSource,
        String attackerType,
        String attackerName,
        long timestamp
) {
    /**
     * Creates a WipeCause with current timestamp.
     */
    public static WipeCause create(
            UUID playerUuid,
            String playerName,
            String deathMessage,
            String damageSource,
            String attackerType,
            String attackerName
    ) {
        return new WipeCause(
                playerUuid,
                playerName,
                deathMessage,
                damageSource,
                attackerType,
                attackerName,
                System.currentTimeMillis()
        );
    }

    /**
     * Serialize to JSON for persistence.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("playerUuid", playerUuid.toString());
        json.addProperty("playerName", playerName);
        json.addProperty("deathMessage", deathMessage);
        json.addProperty("damageSource", damageSource);
        if (attackerType != null) {
            json.addProperty("attackerType", attackerType);
        }
        if (attackerName != null) {
            json.addProperty("attackerName", attackerName);
        }
        json.addProperty("timestamp", timestamp);
        return json;
    }

    /**
     * Deserialize from JSON.
     */
    public static WipeCause fromJson(JsonObject json) {
        return new WipeCause(
                UUID.fromString(json.get("playerUuid").getAsString()),
                json.get("playerName").getAsString(),
                json.get("deathMessage").getAsString(),
                json.get("damageSource").getAsString(),
                json.has("attackerType") ? json.get("attackerType").getAsString() : null,
                json.has("attackerName") ? json.get("attackerName").getAsString() : null,
                json.get("timestamp").getAsLong()
        );
    }
}
