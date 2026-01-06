package tempeststudios.hcautopsy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import tempeststudios.hcautopsy.HCAutopsy;
import tempeststudios.hcautopsy.data.Run;
import tempeststudios.hcautopsy.data.RunMetadata;
import tempeststudios.hcautopsy.data.RunState;
import tempeststudios.hcautopsy.data.WipeCause;
import tempeststudios.hcautopsy.persistence.PersistenceManager;
import tempeststudios.hcautopsy.stats.AggregationEngine;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Command registration and handlers for /hcautopsy.
 *
 * Commands:
 * - /hcautopsy status - Show current run status
 * - /hcautopsy run last - Show the last wiped run
 * - /hcautopsy run list - List all runs
 * - /hcautopsy run <id> - Show details for a specific run
 * - /hcautopsy run continue <reason> - Continue a wiped run
 * - /hcautopsy player <name> totals - Show player lifetime totals
 * - /hcautopsy server totals - Show server lifetime totals
 */
public class CommandRegistry {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PersistenceManager persistence;
    private final AggregationEngine aggregationEngine;

    public CommandRegistry(PersistenceManager persistence) {
        this.persistence = persistence;
        this.aggregationEngine = new AggregationEngine();
    }

    /**
     * Register all commands.
     */
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess,
                         CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("hcautopsy")
                        .requires(source -> true) // All players can read
                        .then(CommandManager.literal("status")
                                .executes(this::statusCommand))
                        .then(CommandManager.literal("run")
                                .then(CommandManager.literal("last")
                                        .executes(this::runLastCommand))
                                .then(CommandManager.literal("list")
                                        .executes(this::runListCommand))
                                .then(CommandManager.literal("continue")
                                        .requires(CommandRegistry::requiresOp) // OP only
                                        .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                                .executes(this::runContinueCommand)))
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(this::runDetailCommand)))
                        .then(CommandManager.literal("player")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .then(CommandManager.literal("totals")
                                                .executes(this::playerTotalsCommand))))
                        .then(CommandManager.literal("server")
                                .then(CommandManager.literal("totals")
                                        .executes(this::serverTotalsCommand)))
        );
    }

    /**
     * /hcautopsy status - Show current run status.
     */
    private int statusCommand(CommandContext<ServerCommandSource> ctx) {
        RunMetadata activeRun = HCAutopsy.getRunManager().getActiveRun();

        if (activeRun == null) {
            ctx.getSource().sendMessage(Text.literal("No active run.").formatted(Formatting.YELLOW));
            return 1;
        }

        MutableText message = Text.literal("=== HC Autopsy Status ===\n").formatted(Formatting.GOLD);

        message.append(Text.literal("Run: ").formatted(Formatting.GRAY))
                .append(Text.literal(activeRun.getRunId()).formatted(Formatting.WHITE))
                .append(Text.literal("\n"));

        message.append(Text.literal("World: ").formatted(Formatting.GRAY))
                .append(Text.literal(activeRun.getWorldName()).formatted(Formatting.GREEN))
                .append(Text.literal("\n"));

        message.append(Text.literal("State: ").formatted(Formatting.GRAY))
                .append(formatState(activeRun.getState()))
                .append(Text.literal("\n"));

        message.append(Text.literal("Started: ").formatted(Formatting.GRAY))
                .append(Text.literal(DATE_FORMAT.format(Instant.ofEpochMilli(activeRun.getStartedAt()))))
                .append(Text.literal("\n"));

        message.append(Text.literal("Duration: ").formatted(Formatting.GRAY))
                .append(Text.literal(formatDuration(activeRun.getDurationMs())))
                .append(Text.literal("\n"));

        message.append(Text.literal("Players: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(activeRun.getParticipatingPlayers().size())));

        if (activeRun.getState() == RunState.WIPED && activeRun.getWipeCause() != null) {
            WipeCause cause = activeRun.getWipeCause();
            message.append(Text.literal("\n\n").formatted(Formatting.RED))
                    .append(Text.literal("☠ WIPED by ").formatted(Formatting.RED))
                    .append(Text.literal(cause.playerName()).formatted(Formatting.RED, Formatting.BOLD))
                    .append(Text.literal("\n"))
                    .append(Text.literal(cause.deathMessage()).formatted(Formatting.GRAY, Formatting.ITALIC));
        }

        ctx.getSource().sendMessage(message);
        return 1;
    }

    /**
     * /hcautopsy run last - Show the most recent wiped run.
     */
    private int runLastCommand(CommandContext<ServerCommandSource> ctx) {
        RunMetadata lastWiped = persistence.getLastWipedRun();

        if (lastWiped == null) {
            ctx.getSource().sendMessage(Text.literal("No wiped runs found.").formatted(Formatting.YELLOW));
            return 1;
        }

        showRunDetails(ctx.getSource(), lastWiped);
        return 1;
    }

    /**
     * /hcautopsy run list - List all runs.
     */
    private int runListCommand(CommandContext<ServerCommandSource> ctx) {
        List<String> runIds = persistence.getAllRunIds();

        if (runIds.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No runs recorded.").formatted(Formatting.YELLOW));
            return 1;
        }

        MutableText message = Text.literal("=== All Runs (" + runIds.size() + ") ===\n").formatted(Formatting.GOLD);

        int shown = 0;
        for (String runId : runIds) {
            if (shown >= 10) {
                message.append(Text.literal("... and " + (runIds.size() - 10) + " more\n").formatted(Formatting.GRAY));
                break;
            }

            RunMetadata meta = persistence.loadMetadata(runId);
            if (meta == null) continue;

            MutableText runEntry = Text.literal("• ")
                    .append(Text.literal(meta.getWorldName()).formatted(Formatting.WHITE))
                    .append(Text.literal(" - ").formatted(Formatting.GRAY))
                    .append(formatState(meta.getState()))
                    .append(Text.literal(" - ").formatted(Formatting.GRAY))
                    .append(Text.literal(formatDuration(meta.getDurationMs())).formatted(Formatting.AQUA));

            // Make it clickable
            runEntry.setStyle(runEntry.getStyle()
                    .withClickEvent(new ClickEvent.RunCommand("/hcautopsy run " + runId))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click for details"))));

            message.append(runEntry).append(Text.literal("\n"));
            shown++;
        }

        ctx.getSource().sendMessage(message);
        return 1;
    }

    /**
     * /hcautopsy run <id> - Show details for a specific run.
     */
    private int runDetailCommand(CommandContext<ServerCommandSource> ctx) {
        String runId = StringArgumentType.getString(ctx, "id");
        RunMetadata meta = persistence.loadMetadata(runId);

        if (meta == null) {
            // Try partial match
            List<String> runIds = persistence.getAllRunIds();
            for (String id : runIds) {
                if (id.contains(runId)) {
                    meta = persistence.loadMetadata(id);
                    break;
                }
            }
        }

        if (meta == null) {
            ctx.getSource().sendMessage(Text.literal("Run not found: " + runId).formatted(Formatting.RED));
            return 0;
        }

        showRunDetails(ctx.getSource(), meta);
        return 1;
    }

    /**
     * /hcautopsy run continue <reason> - Continue a wiped run.
     */
    private int runContinueCommand(CommandContext<ServerCommandSource> ctx) {
        String reason = StringArgumentType.getString(ctx, "reason");

        boolean success = HCAutopsy.getRunManager().continueRun(reason);

        if (success) {
            ctx.getSource().sendMessage(Text.literal("✓ Run continued. Death struck from record.")
                    .formatted(Formatting.GREEN));
            ctx.getSource().sendMessage(Text.literal("Reason: " + reason).formatted(Formatting.GRAY));
        } else {
            ctx.getSource().sendMessage(Text.literal("✗ No wiped run to continue.")
                    .formatted(Formatting.RED));
        }

        return success ? 1 : 0;
    }

    /**
     * /hcautopsy player <name> totals - Show player lifetime totals.
     */
    private int playerTotalsCommand(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "name");

        // For now, we need to resolve name to UUID through online players or cached data
        // This is a simplified implementation
        UUID playerUuid = null;

        // Try to find the player online
        var player = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (player != null) {
            playerUuid = player.getUuid();
        }

        if (playerUuid == null) {
            // Check all lifetime players
            for (UUID uuid : persistence.getAllLifetimePlayerUuids()) {
                // We'd need a name cache here - for now just show error
            }
            ctx.getSource().sendMessage(Text.literal("Player not found. They must be online or have lifetime stats.")
                    .formatted(Formatting.RED));
            return 0;
        }

        String stats = persistence.loadLifetimePlayerStats(playerUuid);
        if (stats == null) {
            ctx.getSource().sendMessage(Text.literal("No lifetime stats for " + playerName)
                    .formatted(Formatting.YELLOW));
            return 1;
        }

        showStatsSummary(ctx.getSource(), "Lifetime Stats: " + playerName, stats);
        return 1;
    }

    /**
     * /hcautopsy server totals - Show server lifetime totals.
     */
    private int serverTotalsCommand(CommandContext<ServerCommandSource> ctx) {
        String stats = persistence.loadServerLifetimeStats();

        if (stats == null) {
            ctx.getSource().sendMessage(Text.literal("No server lifetime stats recorded yet.")
                    .formatted(Formatting.YELLOW));
            return 1;
        }

        showStatsSummary(ctx.getSource(), "Server Lifetime Totals", stats);
        return 1;
    }

    // ==================== Helper Methods ====================

    private void showRunDetails(ServerCommandSource source, RunMetadata meta) {
        MutableText message = Text.literal("=== Run: " + meta.getWorldName() + " ===\n").formatted(Formatting.GOLD);

        message.append(Text.literal("ID: ").formatted(Formatting.GRAY))
                .append(Text.literal(meta.getRunId()).formatted(Formatting.DARK_GRAY))
                .append(Text.literal("\n"));

        message.append(Text.literal("State: ").formatted(Formatting.GRAY))
                .append(formatState(meta.getState()))
                .append(Text.literal("\n"));

        message.append(Text.literal("Duration: ").formatted(Formatting.GRAY))
                .append(Text.literal(formatDuration(meta.getDurationMs())).formatted(Formatting.AQUA))
                .append(Text.literal("\n"));

        message.append(Text.literal("Started: ").formatted(Formatting.GRAY))
                .append(Text.literal(DATE_FORMAT.format(Instant.ofEpochMilli(meta.getStartedAt()))))
                .append(Text.literal("\n"));

        if (meta.getEndedAt() > 0) {
            message.append(Text.literal("Ended: ").formatted(Formatting.GRAY))
                    .append(Text.literal(DATE_FORMAT.format(Instant.ofEpochMilli(meta.getEndedAt()))))
                    .append(Text.literal("\n"));
        }

        message.append(Text.literal("Players: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(meta.getParticipatingPlayers().size())).formatted(Formatting.WHITE))
                .append(Text.literal("\n"));

        if (meta.getWipeCause() != null) {
            WipeCause cause = meta.getWipeCause();
            message.append(Text.literal("\n☠ Death: ").formatted(Formatting.RED))
                    .append(Text.literal(cause.playerName()).formatted(Formatting.RED, Formatting.BOLD))
                    .append(Text.literal("\n"))
                    .append(Text.literal(cause.deathMessage()).formatted(Formatting.GRAY, Formatting.ITALIC))
                    .append(Text.literal("\n"))
                    .append(Text.literal("Cause: ").formatted(Formatting.GRAY))
                    .append(Text.literal(cause.damageSource()).formatted(Formatting.WHITE));
        }

        if (!meta.getContinueHistory().isEmpty()) {
            message.append(Text.literal("\n\n⚠ Run was continued " + meta.getContinueHistory().size() + " time(s)")
                    .formatted(Formatting.YELLOW));
        }

        // Add aggregated stats summary if available
        String aggregated = persistence.loadRunAggregated(meta.getRunId());
        if (aggregated != null) {
            message.append(Text.literal("\n\n"));
            appendStatsSummary(message, aggregated);
        }

        source.sendMessage(message);
    }

    private void showStatsSummary(ServerCommandSource source, String title, String stats) {
        MutableText message = Text.literal("=== " + title + " ===\n").formatted(Formatting.GOLD);
        appendStatsSummary(message, stats);
        source.sendMessage(message);
    }

    private void appendStatsSummary(MutableText message, String stats) {
        // Play time
        Long playTime = aggregationEngine.extractStat(stats, "stats.minecraft:custom.minecraft:play_time");
        if (playTime != null) {
            long seconds = playTime / 20;
            message.append(Text.literal("Playtime: ").formatted(Formatting.GRAY))
                    .append(Text.literal(formatDuration(seconds * 1000)).formatted(Formatting.WHITE))
                    .append(Text.literal("\n"));
        }

        // Deaths
        Long deaths = aggregationEngine.extractStat(stats, "stats.minecraft:custom.minecraft:deaths");
        if (deaths != null) {
            message.append(Text.literal("Deaths: ").formatted(Formatting.GRAY))
                    .append(Text.literal(String.format("%,d", deaths)).formatted(Formatting.WHITE))
                    .append(Text.literal("\n"));
        }

        // Distance walked
        Long distance = aggregationEngine.extractStat(stats, "stats.minecraft:custom.minecraft:walk_one_cm");
        if (distance != null) {
            double km = distance / 100000.0;
            message.append(Text.literal("Distance Walked: ").formatted(Formatting.GRAY))
                    .append(Text.literal(String.format("%.1f km", km)).formatted(Formatting.WHITE))
                    .append(Text.literal("\n"));
        }

        // Jumps
        Long jumps = aggregationEngine.extractStat(stats, "stats.minecraft:custom.minecraft:jump");
        if (jumps != null) {
            message.append(Text.literal("Jumps: ").formatted(Formatting.GRAY))
                    .append(Text.literal(String.format("%,d", jumps)).formatted(Formatting.WHITE))
                    .append(Text.literal("\n"));
        }
    }

    private Text formatState(RunState state) {
        return switch (state) {
            case ACTIVE -> Text.literal("ACTIVE").formatted(Formatting.GREEN);
            case WIPED -> Text.literal("WIPED").formatted(Formatting.RED);
            case CONTINUED -> Text.literal("CONTINUED").formatted(Formatting.YELLOW);
        };
    }

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
     * Permission check for OP-level commands.
     * Returns true if the source is from console/command block or an OP player.
     */
    private static boolean requiresOp(ServerCommandSource source) {
        // For now, only allow console commands for admin operations
        // Players can be added later once we verify the correct API
        return source.getEntity() == null;
    }
}