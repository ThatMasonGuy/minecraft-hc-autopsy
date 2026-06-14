package tempeststudios.hcautopsy.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Map;

/**
 * Generic, schema-agnostic stat aggregation engine.
 * 
 * This engine recursively traverses JSON stat objects and sums all numeric values
 * at matching paths. It does not need to know the structure of Minecraft stats;
 * it simply merges any JSON objects by summing numbers wherever they appear.
 * 
 * This approach ensures that:
 * - Any stat Minecraft adds in the future is automatically handled
 * - Custom stats from other mods are preserved
 * - The aggregation logic never needs updating for schema changes
 */
public class AggregationEngine {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Aggregate multiple stat snapshots into a single combined result.
     * 
     * @param snapshots Collection of raw JSON stat strings
     * @return Combined stats as a JSON string
     */
    public String aggregate(Collection<String> snapshots) {
        JsonObject result = new JsonObject();

        for (String snapshot : snapshots) {
            if (snapshot == null || snapshot.isBlank()) {
                continue;
            }
            try {
                JsonObject stats = GSON.fromJson(snapshot, JsonObject.class);
                mergeInto(result, stats);
            } catch (Exception e) {
                // Skip malformed snapshots but log the issue
                tempeststudios.hcautopsy.HCAutopsy.LOGGER.warn(
                        "Skipping malformed stat snapshot during aggregation: {}", e.getMessage()
                );
            }
        }

        return GSON.toJson(result);
    }

    /**
     * Add a single snapshot to an existing aggregated result.
     * 
     * @param existing Current aggregated stats (will be modified)
     * @param toAdd New stats to add
     */
    public void addToAggregate(JsonObject existing, JsonObject toAdd) {
        mergeInto(existing, toAdd);
    }

    /**
     * Merge source JSON object into target, recursively summing all numeric values.
     * 
     * @param target Target object to merge into (modified in place)
     * @param source Source object to merge from
     */
    private void mergeInto(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            JsonElement sourceValue = entry.getValue();

            if (!target.has(key)) {
                // Key doesn't exist in target, copy it directly
                target.add(key, deepCopy(sourceValue));
            } else {
                // Key exists, need to merge
                JsonElement targetValue = target.get(key);
                target.add(key, mergeElements(targetValue, sourceValue));
            }
        }
    }

    /**
     * Merge two JSON elements together.
     * - Numbers are summed
     * - Objects are recursively merged
     * - Arrays and other types: source replaces target
     */
    private JsonElement mergeElements(JsonElement target, JsonElement source) {
        if (source.isJsonPrimitive() && source.getAsJsonPrimitive().isNumber()) {
            if (target.isJsonPrimitive() && target.getAsJsonPrimitive().isNumber()) {
                // Both are numbers - sum them
                double sum = target.getAsDouble() + source.getAsDouble();
                // Preserve integer type if both inputs were integers
                if (isInteger(target) && isInteger(source)) {
                    return GSON.toJsonTree((long) sum);
                }
                return GSON.toJsonTree(sum);
            }
            // Target isn't a number, source wins
            return deepCopy(source);
        }

        if (source.isJsonObject()) {
            if (target.isJsonObject()) {
                // Both are objects - recursive merge
                JsonObject merged = deepCopy(target).getAsJsonObject();
                mergeInto(merged, source.getAsJsonObject());
                return merged;
            }
            // Target isn't an object, source wins
            return deepCopy(source);
        }

        // For arrays, strings, booleans, nulls: source replaces target
        return deepCopy(source);
    }

    /**
     * Check if a JSON element represents an integer value.
     */
    private boolean isInteger(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        double value = element.getAsDouble();
        return value == Math.floor(value) && !Double.isInfinite(value);
    }

    /**
     * Create a deep copy of a JSON element.
     */
    private JsonElement deepCopy(JsonElement element) {
        return GSON.fromJson(GSON.toJson(element), JsonElement.class);
    }

    /**
     * Parse a stat snapshot string into a JsonObject.
     */
    public JsonObject parse(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return new JsonObject();
        }
        return GSON.fromJson(snapshot, JsonObject.class);
    }

    /**
     * Convert a JsonObject back to a formatted string.
     */
    public String serialize(JsonObject stats) {
        return GSON.toJson(stats);
    }

    /**
     * Extract a specific stat value from a snapshot.
     * 
     * @param snapshot Raw JSON snapshot
     * @param path Dot-separated path (e.g., "stats.minecraft:custom.minecraft:play_time")
     * @return The value at that path, or null if not found
     */
    public Long extractStat(String snapshot, String path) {
        try {
            JsonObject json = parse(snapshot);
            String[] parts = path.split("\\.");
            JsonElement current = json;

            for (String part : parts) {
                if (current == null || !current.isJsonObject()) {
                    return null;
                }
                current = current.getAsJsonObject().get(part);
            }

            if (current != null && current.isJsonPrimitive() && current.getAsJsonPrimitive().isNumber()) {
                return current.getAsLong();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract and sum the direct numeric values inside a stat category.
     *
     * @param snapshot Raw JSON snapshot
     * @param categoryPath Dot-separated path to a stat category
     * @return The category sum, or null if the category is not present
     */
    public Long extractStatCategory(String snapshot, String categoryPath) {
        try {
            JsonObject json = parse(snapshot);
            String[] parts = categoryPath.split("\\.");
            JsonElement current = json;

            for (String part : parts) {
                if (current == null || !current.isJsonObject()) {
                    return null;
                }
                current = current.getAsJsonObject().get(part);
            }

            if (current == null || !current.isJsonObject()) {
                return null;
            }

            long sum = 0;
            boolean foundValue = false;
            for (Map.Entry<String, JsonElement> entry : current.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    sum += value.getAsLong();
                    foundValue = true;
                }
            }
            return foundValue ? sum : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract and sum several exact stat paths.
     *
     * @param snapshot Raw JSON snapshot
     * @param paths Dot-separated stat paths
     * @return The summed value, or null if none of the paths are present
     */
    public Long extractStatSum(String snapshot, Collection<String> paths) {
        long sum = 0;
        boolean foundValue = false;

        for (String path : paths) {
            Long value = extractStat(snapshot, path);
            if (value != null) {
                sum += value;
                foundValue = true;
            }
        }

        return foundValue ? sum : null;
    }
}
