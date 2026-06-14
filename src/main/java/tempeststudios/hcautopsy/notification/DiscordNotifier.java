package tempeststudios.hcautopsy.notification;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import tempeststudios.hcautopsy.HCAutopsy;
import tempeststudios.hcautopsy.config.ModConfig;
import tempeststudios.hcautopsy.data.RunMetadata;
import tempeststudios.hcautopsy.data.WipeCause;
import tempeststudios.hcautopsy.stats.AggregationEngine;
import tempeststudios.hcautopsy.stats.WipeLeaderboardRankingsReport;
import tempeststudios.hcautopsy.stats.WipeLeaderboardReport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sends wipe notifications to Discord via webhook.
 * 
 * This is purely a presentation layer - the notification contains a summary,
 * not the complete data. The authoritative data remains on disk.
 */
public class DiscordNotifier {
    private static final Gson GSON = new GsonBuilder().create();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM dd, yyyy 'at' HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ModConfig config;
    private final AggregationEngine aggregationEngine;
    private final HttpClient httpClient;

    public DiscordNotifier(ModConfig config) {
        this.config = config;
        this.aggregationEngine = new AggregationEngine();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Check if Discord notifications are configured.
     */
    public boolean isConfigured() {
        String webhookUrl = config.getDiscordWebhookUrl();
        return config.isDiscordNotificationsEnabled() && webhookUrl != null && !webhookUrl.isBlank();
    }

    /**
     * Send a wipe notification to Discord.
     * 
     * @param run The wiped run metadata
     * @param playerCount Number of players in the run
     * @param aggregatedStats Aggregated stats JSON for extracting headlines
     */
    public CompletableFuture<Void> sendWipeNotification(RunMetadata run, int playerCount, String aggregatedStats) {
        return sendWipeNotification(run, playerCount, aggregatedStats, null);
    }

    public CompletableFuture<Void> sendWipeNotification(
            RunMetadata run,
            int playerCount,
            String aggregatedStats,
            WipeLeaderboardReport leaderboard
    ) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                JsonObject embed = buildWipeEmbed(run, playerCount, aggregatedStats);
                sendWebhook(embed);
                if (leaderboard != null && !leaderboard.isEmpty()) {
                    sendWebhook(buildLeaderboardEmbed(run, leaderboard));
                }
                HCAutopsy.LOGGER.info("Discord notification sent for wipe");
            } catch (Exception e) {
                HCAutopsy.LOGGER.error("Failed to send Discord notification: {}", e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> sendTestNotification(String sourceName, String worldName) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", "HC Autopsy Discord test");
                embed.addProperty("description", "Webhook notifications are configured and reachable.");
                embed.addProperty("color", 0x46B450);

                JsonArray fields = new JsonArray();
                fields.add(createField("Source", sourceName == null || sourceName.isBlank() ? "Unknown" : sourceName, true));
                fields.add(createField("World", worldName == null || worldName.isBlank() ? "Unknown" : worldName, true));
                embed.add("fields", fields);
                embed.addProperty("timestamp", Instant.now().toString());

                sendWebhook(embed);
                HCAutopsy.LOGGER.info("Discord test notification sent");
            } catch (Exception e) {
                HCAutopsy.LOGGER.error("Failed to send Discord test notification: {}", e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> sendWipeLeaderboard(
            RunMetadata run,
            WipeLeaderboardRankingsReport rankings
    ) {
        if (!isConfigured() || rankings == null || rankings.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                for (WipeLeaderboardRankingsReport.Category category : rankings.categories()) {
                    for (String description : chunkCategoryDescriptions(category)) {
                        sendWebhook(buildRankingEmbed(run, category.label(), description));
                    }
                }
                HCAutopsy.LOGGER.info("Discord leaderboard notification sent for run {}", run.getRunId());
            } catch (Exception e) {
                HCAutopsy.LOGGER.error("Failed to send Discord leaderboard notification: {}", e.getMessage());
            }
        });
    }

    /**
     * Build the Discord embed for a wipe notification.
     */
    private JsonObject buildWipeEmbed(RunMetadata run, int playerCount, String aggregatedStats) {
        WipeCause cause = run.getWipeCause();

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "☠️ World Wiped: " + run.getWorldName());
        embed.addProperty("color", 0xFF0000); // Red

        StringBuilder description = new StringBuilder();
        description.append("**").append(cause.playerName()).append("** has ended this run.\n\n");
        description.append("*").append(cause.deathMessage()).append("*");
        embed.addProperty("description", description.toString());

        JsonArray fields = new JsonArray();

        // Run duration
        fields.add(createField("Duration", formatDuration(run.getDurationMs()), true));

        // Player count
        fields.add(createField("Players", String.valueOf(playerCount), true));

        // Cause of death
        fields.add(createField("Cause", cause.damageSource(), true));

        // Extract headline stats
        addHeadlineStats(fields, aggregatedStats);

        // Timestamps
        fields.add(createField("Started", DATE_FORMAT.format(Instant.ofEpochMilli(run.getStartedAt())), true));
        fields.add(createField("Ended", DATE_FORMAT.format(Instant.ofEpochMilli(run.getEndedAt())), true));

        embed.add("fields", fields);

        // Footer with run ID
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Run ID: " + run.getRunId());
        embed.add("footer", footer);

        embed.addProperty("timestamp", Instant.ofEpochMilli(run.getEndedAt()).toString());

        return embed;
    }

    private JsonObject buildLeaderboardEmbed(RunMetadata run, WipeLeaderboardReport leaderboard) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "HC Autopsy Run Leaderboard: " + run.getWorldName());
        embed.addProperty("description", buildLeaderboardDescription(leaderboard));
        embed.addProperty("color", 0xF1C40F);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Run ID: " + run.getRunId());
        embed.add("footer", footer);

        long timestamp = run.getEndedAt() > 0 ? run.getEndedAt() : Instant.now().toEpochMilli();
        embed.addProperty("timestamp", Instant.ofEpochMilli(timestamp).toString());

        return embed;
    }

    private JsonObject buildRankingEmbed(RunMetadata run, String categoryLabel, String description) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "HC Autopsy " + categoryLabel + " Leaderboard");
        embed.addProperty("description", description);
        embed.addProperty("color", 0x3498DB);

        JsonArray fields = new JsonArray();
        fields.add(createField("World", run.getWorldName(), true));
        fields.add(createField("Run Duration", formatDuration(run.getDurationMs()), true));
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Run ID: " + run.getRunId());
        embed.add("footer", footer);

        long timestamp = run.getEndedAt() > 0 ? run.getEndedAt() : Instant.now().toEpochMilli();
        embed.addProperty("timestamp", Instant.ofEpochMilli(timestamp).toString());

        return embed;
    }

    private String buildLeaderboardDescription(WipeLeaderboardReport leaderboard) {
        StringBuilder description = new StringBuilder();
        for (WipeLeaderboardReport.Entry entry : leaderboard.entries()) {
            description.append("**")
                    .append(entry.label())
                    .append("** | ")
                    .append(entry.playerName())
                    .append(" | ")
                    .append(entry.formattedValue())
                    .append("\n");
        }
        return description.toString().trim();
    }

    private List<String> chunkCategoryDescriptions(WipeLeaderboardRankingsReport.Category category) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (WipeLeaderboardRankingsReport.RankedEntry entry : category.entries()) {
            String line = "**#" + entry.rank() + "** "
                    + entry.playerName()
                    + " | "
                    + entry.formattedValue()
                    + "\n";
            if (current.length() > 0 && current.length() + line.length() > 3800) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks.isEmpty() ? List.of("No entries.") : chunks;
    }

    /**
     * Add headline stats to the embed fields.
     */
    private void addHeadlineStats(JsonArray fields, String aggregatedStats) {
        // Total play time
        Long playTime = aggregationEngine.extractStat(aggregatedStats,
                "stats.minecraft:custom.minecraft:play_time");
        if (playTime != null) {
            // Play time is in ticks (20 per second)
            long seconds = playTime / 20;
            fields.add(createField("Total Playtime", formatDuration(seconds * 1000), true));
        }

        // Total blocks mined (sum of all mined blocks)
        Long blocksMined = aggregationEngine.extractStatCategory(aggregatedStats, "stats.minecraft:mined");
        if (blocksMined != null && blocksMined > 0) {
            fields.add(createField("Blocks Mined", formatNumber(blocksMined), true));
        }

        // Total mobs killed
        Long mobsKilled = aggregationEngine.extractStatCategory(aggregatedStats, "stats.minecraft:killed");
        if (mobsKilled != null && mobsKilled > 0) {
            fields.add(createField("Mobs Killed", formatNumber(mobsKilled), true));
        }

        // Distance walked
        Long distanceWalked = aggregationEngine.extractStat(aggregatedStats,
                "stats.minecraft:custom.minecraft:walk_one_cm");
        if (distanceWalked != null) {
            // Distance is in centimeters
            double km = distanceWalked / 100000.0;
            fields.add(createField("Distance Walked", String.format("%.1f km", km), true));
        }

        // Deaths (should be 1 for valid hardcore)
        Long deaths = aggregationEngine.extractStat(aggregatedStats,
                "stats.minecraft:custom.minecraft:deaths");
        if (deaths != null) {
            fields.add(createField("Total Deaths", String.valueOf(deaths), true));
        }
    }

    /**
     * Create an embed field.
     */
    private JsonObject createField(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    /**
     * Send a webhook request to Discord.
     */
    private void sendWebhook(JsonObject embed) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "HC Autopsy");

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getDiscordWebhookUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Discord webhook returned status " + response.statusCode()
                    + ": " + response.body());
        }
    }

    /**
     * Format a duration in milliseconds to human-readable string.
     */
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

    /**
     * Format a large number with commas.
     */
    private String formatNumber(long number) {
        return String.format("%,d", number);
    }
}
