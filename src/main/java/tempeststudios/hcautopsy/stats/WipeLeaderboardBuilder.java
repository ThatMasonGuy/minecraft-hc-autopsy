package tempeststudios.hcautopsy.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the automatic stat awards shown after a wipe completes.
 */
public class WipeLeaderboardBuilder {
    private static final List<String> DIAMOND_ORE_PATHS = List.of(
            "stats.minecraft:mined.minecraft:diamond_ore",
            "stats.minecraft:mined.minecraft:deepslate_diamond_ore"
    );

    private static final List<AwardStat> AWARD_STATS = List.of(
            new AwardStat(
                    "Most Time Played",
                    "Time Played",
                    (engine, snapshot) -> engine.extractStat(snapshot, "stats.minecraft:custom.minecraft:play_time"),
                    ValueFormat.TICKS
            ),
            new AwardStat(
                    "Most Blocks Broken",
                    "Blocks Broken",
                    (engine, snapshot) -> engine.extractStatCategory(snapshot, "stats.minecraft:mined"),
                    ValueFormat.NUMBER
            ),
            new AwardStat(
                    "Most Damage Taken",
                    "Damage Taken",
                    (engine, snapshot) -> engine.extractStat(snapshot, "stats.minecraft:custom.minecraft:damage_taken"),
                    ValueFormat.DAMAGE_TENTHS
            ),
            new AwardStat(
                    "Most Damage Dealt",
                    "Damage Dealt",
                    (engine, snapshot) -> engine.extractStat(snapshot, "stats.minecraft:custom.minecraft:damage_dealt"),
                    ValueFormat.DAMAGE_TENTHS
            ),
            new AwardStat(
                    "Most Diamonds Mined",
                    "Diamonds Mined",
                    (engine, snapshot) -> engine.extractStatSum(snapshot, DIAMOND_ORE_PATHS),
                    ValueFormat.NUMBER
            )
    );

    private final AggregationEngine aggregationEngine;

    public WipeLeaderboardBuilder() {
        this(new AggregationEngine());
    }

    WipeLeaderboardBuilder(AggregationEngine aggregationEngine) {
        this.aggregationEngine = aggregationEngine;
    }

    public WipeLeaderboardReport build(
            Map<UUID, String> snapshots,
            Function<UUID, String> playerNameResolver
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new WipeLeaderboardReport(List.of());
        }

        List<WipeLeaderboardReport.Entry> entries = new ArrayList<>();
        for (AwardStat stat : AWARD_STATS) {
            rankedCandidates(stat, snapshots, playerNameResolver, false).stream()
                    .findFirst()
                    .map(winner -> new WipeLeaderboardReport.Entry(
                            stat.awardLabel(),
                            winner.playerName(),
                            winner.value(),
                            stat.format(winner.value())
                    ))
                    .ifPresent(entries::add);
        }

        return new WipeLeaderboardReport(entries);
    }

    public WipeLeaderboardRankingsReport buildRankings(
            Map<UUID, String> snapshots,
            Function<UUID, String> playerNameResolver
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new WipeLeaderboardRankingsReport(List.of());
        }

        List<WipeLeaderboardRankingsReport.Category> categories = new ArrayList<>();
        for (AwardStat stat : AWARD_STATS) {
            List<Winner> winners = rankedCandidates(stat, snapshots, playerNameResolver, true);
            if (winners.isEmpty()) {
                continue;
            }

            List<WipeLeaderboardRankingsReport.RankedEntry> entries = new ArrayList<>();
            int rank = 1;
            for (Winner winner : winners) {
                entries.add(new WipeLeaderboardRankingsReport.RankedEntry(
                        rank,
                        winner.playerName(),
                        winner.value(),
                        stat.format(winner.value())
                ));
                rank++;
            }
            categories.add(new WipeLeaderboardRankingsReport.Category(stat.categoryLabel(), entries));
        }

        return new WipeLeaderboardRankingsReport(categories);
    }

    private List<Winner> rankedCandidates(
            AwardStat stat,
            Map<UUID, String> snapshots,
            Function<UUID, String> playerNameResolver,
            boolean includeMissingAsZero
    ) {
        return snapshots.entrySet().stream()
                .map(entry -> toCandidate(stat, entry, playerNameResolver, includeMissingAsZero))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingLong(Winner::value)
                        .reversed()
                        .thenComparing(winner -> winner.playerName().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private Optional<Winner> toCandidate(
            AwardStat stat,
            Map.Entry<UUID, String> entry,
            Function<UUID, String> playerNameResolver,
            boolean includeMissingAsZero
    ) {
        Long value = stat.extract(aggregationEngine, entry.getValue());
        if (value == null) {
            if (!includeMissingAsZero) {
                return Optional.empty();
            }
            value = 0L;
        }
        return Optional.of(new Winner(displayName(entry.getKey(), playerNameResolver), value));
    }

    private String displayName(UUID playerUuid, Function<UUID, String> playerNameResolver) {
        if (playerNameResolver != null) {
            String resolved = playerNameResolver.apply(playerUuid);
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        return playerUuid.toString();
    }

    private record AwardStat(String awardLabel, String categoryLabel, StatExtractor extractor, ValueFormat format) {
        private Long extract(AggregationEngine engine, String snapshot) {
            return extractor.extract(engine, snapshot);
        }

        private String format(long value) {
            return format.format(value);
        }
    }

    @FunctionalInterface
    private interface StatExtractor {
        Long extract(AggregationEngine engine, String snapshot);
    }

    private enum ValueFormat {
        TICKS {
            @Override
            String format(long value) {
                return formatDuration((value / 20) * 1000);
            }
        },
        NUMBER {
            @Override
            String format(long value) {
                return String.format(Locale.US, "%,d", value);
            }
        },
        DAMAGE_TENTHS {
            @Override
            String format(long value) {
                return String.format(Locale.US, "%.1f damage", value / 10.0);
            }
        };

        abstract String format(long value);
    }

    private record Winner(String playerName, long value) {
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format(Locale.US, "%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format(Locale.US, "%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format(Locale.US, "%dm %ds", minutes, seconds % 60);
        } else {
            return String.format(Locale.US, "%ds", seconds);
        }
    }
}
