package tempeststudios.hcautopsy.stats;

import java.util.List;
import java.util.Objects;

/**
 * Presentation-ready post-wipe stat winners.
 */
public record WipeLeaderboardReport(List<Entry> entries) {
    public WipeLeaderboardReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public record Entry(String label, String playerName, long value, String formattedValue) {
        public Entry {
            label = Objects.requireNonNullElse(label, "Unknown Stat");
            playerName = Objects.requireNonNullElse(playerName, "Unknown Player");
            formattedValue = Objects.requireNonNullElse(formattedValue, String.valueOf(value));
        }

        public String pipeLine() {
            return label + " | " + playerName + " | " + formattedValue;
        }
    }
}
