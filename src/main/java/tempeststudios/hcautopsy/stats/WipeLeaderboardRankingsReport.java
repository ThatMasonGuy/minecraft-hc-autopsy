package tempeststudios.hcautopsy.stats;

import java.util.List;
import java.util.Objects;

/**
 * Full post-wipe stat rankings for every player in each tracked category.
 */
public record WipeLeaderboardRankingsReport(List<Category> categories) {
    public WipeLeaderboardRankingsReport {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    public boolean isEmpty() {
        return categories.isEmpty();
    }

    public int playerCount() {
        return categories.stream()
                .mapToInt(category -> category.entries().size())
                .max()
                .orElse(0);
    }

    public record Category(String label, List<RankedEntry> entries) {
        public Category {
            label = Objects.requireNonNullElse(label, "Unknown Stat");
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    public record RankedEntry(int rank, String playerName, long value, String formattedValue) {
        public RankedEntry {
            playerName = Objects.requireNonNullElse(playerName, "Unknown Player");
            formattedValue = Objects.requireNonNullElse(formattedValue, String.valueOf(value));
        }

        public String chatLine() {
            return rank + ". " + playerName + " | " + formattedValue;
        }
    }
}
