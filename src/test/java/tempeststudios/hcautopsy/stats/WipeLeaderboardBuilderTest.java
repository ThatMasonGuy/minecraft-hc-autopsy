package tempeststudios.hcautopsy.stats;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WipeLeaderboardBuilderTest {
    private final WipeLeaderboardBuilder builder = new WipeLeaderboardBuilder();

    @Test
    void buildsRequestedPostWipeAwardsFromPlayerSnapshots() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID clara = UUID.fromString("00000000-0000-0000-0000-000000000003");

        WipeLeaderboardReport report = builder.build(Map.of(
                alice, """
                        {
                          "stats": {
                            "minecraft:custom": {
                              "minecraft:play_time": 2400,
                              "minecraft:damage_taken": 35,
                              "minecraft:damage_dealt": 100
                            },
                            "minecraft:mined": {
                              "minecraft:stone": 10,
                              "minecraft:diamond_ore": 1
                            }
                          }
                        }
                        """,
                bob, """
                        {
                          "stats": {
                            "minecraft:custom": {
                              "minecraft:play_time": 72000,
                              "minecraft:damage_taken": 12,
                              "minecraft:damage_dealt": 250
                            },
                            "minecraft:mined": {
                              "minecraft:stone": 20,
                              "minecraft:deepslate_diamond_ore": 3
                            }
                          }
                        }
                        """,
                clara, """
                        {
                          "stats": {
                            "minecraft:custom": {
                              "minecraft:play_time": 1200,
                              "minecraft:damage_taken": 75,
                              "minecraft:damage_dealt": 10
                            },
                            "minecraft:mined": {
                              "minecraft:stone": 50,
                              "minecraft:diamond_ore": 2
                            }
                          }
                        }
                        """
        ), uuid -> {
            if (uuid.equals(alice)) {
                return "Alice";
            }
            if (uuid.equals(bob)) {
                return "Bob";
            }
            if (uuid.equals(clara)) {
                return "Clara";
            }
            return uuid.toString();
        });

        assertEquals(5, report.entries().size());
        assertEquals("Most Time Played | Bob | 1h 0m", report.entries().get(0).pipeLine());
        assertEquals("Most Blocks Broken | Clara | 52", report.entries().get(1).pipeLine());
        assertEquals("Most Damage Taken | Clara | 7.5 damage", report.entries().get(2).pipeLine());
        assertEquals("Most Damage Dealt | Bob | 25.0 damage", report.entries().get(3).pipeLine());
        assertEquals("Most Diamonds Mined | Bob | 3", report.entries().get(4).pipeLine());
    }

    @Test
    void buildsFullRankedCategoriesForEveryPlayer() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID clara = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID devin = UUID.fromString("00000000-0000-0000-0000-000000000004");

        WipeLeaderboardRankingsReport report = builder.buildRankings(Map.of(
                alice, """
                        {
                          "stats": {
                            "minecraft:custom": {
                              "minecraft:play_time": 2400,
                              "minecraft:damage_taken": 35,
                              "minecraft:damage_dealt": 100
                            },
                            "minecraft:mined": {
                              "minecraft:stone": 10,
                              "minecraft:diamond_ore": 1
                            }
                          }
                        }
                        """,
                bob, """
                        {
                          "stats": {
                            "minecraft:custom": {
                              "minecraft:play_time": 72000,
                              "minecraft:damage_taken": 12,
                              "minecraft:damage_dealt": 250
                            },
                            "minecraft:mined": {
                              "minecraft:stone": 20,
                              "minecraft:deepslate_diamond_ore": 3
                            }
                          }
                        }
                        """,
                clara, """
                        {
                          "stats": {
                            "minecraft:custom": {
                              "minecraft:play_time": 1200,
                              "minecraft:damage_taken": 75,
                              "minecraft:damage_dealt": 10
                            },
                            "minecraft:mined": {
                              "minecraft:stone": 50,
                              "minecraft:diamond_ore": 2
                            }
                          }
                        }
                        """,
                devin, """
                        {
                          "stats": {
                            "minecraft:custom": {
                              "minecraft:play_time": 600,
                              "minecraft:damage_taken": 0,
                              "minecraft:damage_dealt": 0
                            },
                            "minecraft:mined": {
                              "minecraft:stone": 1
                            }
                          }
                        }
                        """
        ), uuid -> {
            if (uuid.equals(alice)) {
                return "Alice";
            }
            if (uuid.equals(bob)) {
                return "Bob";
            }
            if (uuid.equals(clara)) {
                return "Clara";
            }
            if (uuid.equals(devin)) {
                return "Devin";
            }
            return uuid.toString();
        });

        assertEquals(5, report.categories().size());
        assertEquals("Time Played", report.categories().get(0).label());
        assertEquals("1. Bob | 1h 0m", report.categories().get(0).entries().get(0).chatLine());
        assertEquals("2. Alice | 2m 0s", report.categories().get(0).entries().get(1).chatLine());
        assertEquals("3. Clara | 1m 0s", report.categories().get(0).entries().get(2).chatLine());
        assertEquals("4. Devin | 30s", report.categories().get(0).entries().get(3).chatLine());
        assertEquals("Blocks Broken", report.categories().get(1).label());
        assertEquals("1. Clara | 52", report.categories().get(1).entries().get(0).chatLine());
        assertEquals("Diamonds Mined", report.categories().get(4).label());
        assertEquals("1. Bob | 3", report.categories().get(4).entries().get(0).chatLine());
        assertEquals("4. Devin | 0", report.categories().get(4).entries().get(3).chatLine());
        assertEquals(4, report.playerCount());
    }

    @Test
    void skipsLeaderboardWhenNoSnapshotsWereCaptured() {
        WipeLeaderboardReport report = builder.build(Map.of(), UUID::toString);

        assertTrue(report.isEmpty());
        assertTrue(builder.buildRankings(Map.of(), UUID::toString).isEmpty());
    }
}
