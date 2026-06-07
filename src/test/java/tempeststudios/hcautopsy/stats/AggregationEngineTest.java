package tempeststudios.hcautopsy.stats;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AggregationEngineTest {
    private final AggregationEngine aggregationEngine = new AggregationEngine();

    @Test
    void aggregatesNestedMinecraftStats() {
        String first = """
                {
                  "stats": {
                    "minecraft:custom": {
                      "minecraft:play_time": 40,
                      "minecraft:jump": 3
                    },
                    "minecraft:mined": {
                      "minecraft:stone": 12
                    }
                  },
                  "DataVersion": 1
                }
                """;
        String second = """
                {
                  "stats": {
                    "minecraft:custom": {
                      "minecraft:play_time": 60,
                      "minecraft:jump": 7
                    },
                    "minecraft:mined": {
                      "minecraft:stone": 8,
                      "minecraft:dirt": 5
                    }
                  },
                  "DataVersion": 2
                }
                """;

        String aggregated = aggregationEngine.aggregate(List.of(first, second));

        assertEquals(100, aggregationEngine.extractStat(aggregated, "stats.minecraft:custom.minecraft:play_time"));
        assertEquals(10, aggregationEngine.extractStat(aggregated, "stats.minecraft:custom.minecraft:jump"));
        assertEquals(20, aggregationEngine.extractStat(aggregated, "stats.minecraft:mined.minecraft:stone"));
        assertEquals(5, aggregationEngine.extractStat(aggregated, "stats.minecraft:mined.minecraft:dirt"));
    }

    @Test
    void skipsBlankSnapshots() {
        String aggregated = aggregationEngine.aggregate(List.of("", "   "));

        JsonObject json = aggregationEngine.parse(aggregated);
        assertNotNull(json);
        assertEquals(0, json.entrySet().size());
    }
}
