package tempeststudios.hcautopsy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess,
                         Commands.CommandSelection environment) {
        dispatcher.register(
                Commands.literal("hcautopsy")
                        .requires(source -> true) // All players can read
                        .then(Commands.literal("status")
                                .executes(this::statusCommand))
                        .then(Commands.literal("run")
                                .then(Commands.literal("last")
                                        .executes(this::runLastCommand))
                                .then(Commands.literal("list")
                                        .executes(this::runListCommand))
                                .then(Commands.literal("continue")
                                        .requires(CommandRegistry::requiresOp) // OP only
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(this::runContinueCommand)))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(this::runDetailCommand)))
                        .then(Commands.literal("player")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.literal("totals")
                                                .executes(this::playerTotalsCommand))))
                        .then(Commands.literal("server")
                                .then(Commands.literal("totals")
                                        .executes(this::serverTotalsCommand)))
        );
    }

    /**
     * /hcautopsy status - Show current run status.
     */
    private int statusCommand(CommandContext<CommandSourceStack> ctx) {
        RunMetadata activeRun = HCAutopsy.getRunManager().getActiveRun();

        if (activeRun == null) {
            ctx.getSource().sendSystemMessage(Component.literal("No active run.").withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        MutableComponent message = Component.literal("=== HC Autopsy Status ===\n").withStyle(ChatFormatting.GOLD);

        message.append(Component.literal("Run: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(activeRun.getRunId()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"));

        message.append(Component.literal("World: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(activeRun.getWorldName()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\n"));

        message.append(Component.literal("State: ").withStyle(ChatFormatting.GRAY))
                .append(formatState(activeRun.getState()))
                .append(Component.literal("\n"));

        message.append(Component.literal("Started: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(DATE_FORMAT.format(Instant.ofEpochMilli(activeRun.getStartedAt()))))
                .append(Component.literal("\n"));

        message.append(Component.literal("Duration: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatDuration(activeRun.getDurationMs())))
                .append(Component.literal("\n"));

        message.append(Component.literal("Players: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(activeRun.getParticipatingPlayers().size())));

        if (activeRun.getState() == RunState.WIPED && activeRun.getWipeCause() != null) {
            WipeCause cause = activeRun.getWipeCause();
            message.append(Component.literal("\n\n").withStyle(ChatFormatting.RED))
                    .append(Component.literal("☠ WIPED by ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(cause.playerName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal("\n"))
                    .append(Component.literal(cause.deathMessage()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        ctx.getSource().sendSystemMessage(message);
        return 1;
    }

    /**
     * /hcautopsy run last - Show the most recent wiped run.
     */
    private int runLastCommand(CommandContext<CommandSourceStack> ctx) {
        RunMetadata lastWiped = persistence.getLastWipedRun();

        if (lastWiped == null) {
            ctx.getSource().sendSystemMessage(Component.literal("No wiped runs found.").withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        showRunDetails(ctx.getSource(), lastWiped);
        return 1;
    }

    /**
     * /hcautopsy run list - List all runs.
     */
    private int runListCommand(CommandContext<CommandSourceStack> ctx) {
        List<String> runIds = persistence.getAllRunIds();

        if (runIds.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No runs recorded.").withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        MutableComponent message = Component.literal("=== All Runs (" + runIds.size() + ") ===\n").withStyle(ChatFormatting.GOLD);

        int shown = 0;
        for (String runId : runIds) {
            if (shown >= 10) {
                message.append(Component.literal("... and " + (runIds.size() - 10) + " more\n").withStyle(ChatFormatting.GRAY));
                break;
            }

            RunMetadata meta = persistence.loadMetadata(runId);
            if (meta == null) continue;

            MutableComponent runEntry = Component.literal("• ")
                    .append(Component.literal(meta.getWorldName()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(formatState(meta.getState()))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formatDuration(meta.getDurationMs())).withStyle(ChatFormatting.AQUA));

            // Make it clickable
            runEntry.setStyle(runEntry.getStyle()
                    .withClickEvent(new ClickEvent.RunCommand("/hcautopsy run " + runId))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click for details"))));

            message.append(runEntry).append(Component.literal("\n"));
            shown++;
        }

        ctx.getSource().sendSystemMessage(message);
        return 1;
    }

    /**
     * /hcautopsy run <id> - Show details for a specific run.
     */
    private int runDetailCommand(CommandContext<CommandSourceStack> ctx) {
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
            ctx.getSource().sendSystemMessage(Component.literal("Run not found: " + runId).withStyle(ChatFormatting.RED));
            return 0;
        }

        showRunDetails(ctx.getSource(), meta);
        return 1;
    }

    /**
     * /hcautopsy run continue <reason> - Continue a wiped run.
     */
    private int runContinueCommand(CommandContext<CommandSourceStack> ctx) {
        String reason = StringArgumentType.getString(ctx, "reason");

        boolean success = HCAutopsy.getRunManager().continueRun(reason);

        if (success) {
            ctx.getSource().sendSystemMessage(Component.literal("✓ Run continued. Death struck from record.")
                    .withStyle(ChatFormatting.GREEN));
            ctx.getSource().sendSystemMessage(Component.literal("Reason: " + reason).withStyle(ChatFormatting.GRAY));
        } else {
            ctx.getSource().sendSystemMessage(Component.literal("✗ No wiped run to continue.")
                    .withStyle(ChatFormatting.RED));
        }

        return success ? 1 : 0;
    }

    /**
     * /hcautopsy player <name> totals - Show player lifetime totals.
     */
    private int playerTotalsCommand(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "name");

        // For now, we need to resolve name to UUID through online players or cached data
        // This is a simplified implementation
        UUID playerUuid = null;

        // Try to find the player online
        var player = ctx.getSource().getServer().getPlayerList().getPlayer(playerName);
        if (player != null) {
            playerUuid = player.getUUID();
        }

        if (playerUuid == null) {
            // Check all lifetime players
            for (UUID uuid : persistence.getAllLifetimePlayerUuids()) {
                // We'd need a name cache here - for now just show error
            }
            ctx.getSource().sendSystemMessage(Component.literal("Player not found. They must be online or have lifetime stats.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        String stats = persistence.loadLifetimePlayerStats(playerUuid);
        if (stats == null) {
            ctx.getSource().sendSystemMessage(Component.literal("No lifetime stats for " + playerName)
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        showStatsSummary(ctx.getSource(), "Lifetime Stats: " + playerName, stats);
        return 1;
    }

    /**
     * /hcautopsy server totals - Show server lifetime totals.
     */
    private int serverTotalsCommand(CommandContext<CommandSourceStack> ctx) {
        String stats = persistence.loadServerLifetimeStats();

        if (stats == null) {
            ctx.getSource().sendSystemMessage(Component.literal("No server lifetime stats recorded yet.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        showStatsSummary(ctx.getSource(), "Server Lifetime Totals", stats);
        return 1;
    }

    // ==================== Helper Methods ====================

    private void showRunDetails(CommandSourceStack source, RunMetadata meta) {
        MutableComponent message = Component.literal("=== Run: " + meta.getWorldName() + " ===\n").withStyle(ChatFormatting.GOLD);

        message.append(Component.literal("ID: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(meta.getRunId()).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"));

        message.append(Component.literal("State: ").withStyle(ChatFormatting.GRAY))
                .append(formatState(meta.getState()))
                .append(Component.literal("\n"));

        message.append(Component.literal("Duration: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatDuration(meta.getDurationMs())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"));

        message.append(Component.literal("Started: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(DATE_FORMAT.format(Instant.ofEpochMilli(meta.getStartedAt()))))
                .append(Component.literal("\n"));

        if (meta.getEndedAt() > 0) {
            message.append(Component.literal("Ended: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(DATE_FORMAT.format(Instant.ofEpochMilli(meta.getEndedAt()))))
                    .append(Component.literal("\n"));
        }

        message.append(Component.literal("Players: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(meta.getParticipatingPlayers().size())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"));

        if (meta.getWipeCause() != null) {
            WipeCause cause = meta.getWipeCause();
            message.append(Component.literal("\n☠ Death: ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(cause.playerName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal("\n"))
                    .append(Component.literal(cause.deathMessage()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                    .append(Component.literal("\n"))
                    .append(Component.literal("Cause: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(cause.damageSource()).withStyle(ChatFormatting.WHITE));
        }

        if (!meta.getContinueHistory().isEmpty()) {
            message.append(Component.literal("\n\n⚠ Run was continued " + meta.getContinueHistory().size() + " time(s)")
                    .withStyle(ChatFormatting.YELLOW));
        }

        // Add aggregated stats summary if available
        String aggregated = persistence.loadRunAggregated(meta.getRunId());
        if (aggregated != null) {
            message.append(Component.literal("\n\n"));
            appendStatsSummary(message, aggregated);
        }

        source.sendSystemMessage(message);
    }

    private void showStatsSummary(CommandSourceStack source, String title, String stats) {
        MutableComponent message = Component.literal("=== " + title + " ===\n").withStyle(ChatFormatting.GOLD);
        appendStatsSummary(message, stats);
        source.sendSystemMessage(message);
    }

    private void appendStatsSummary(MutableComponent message, String stats) {
        // Play time
        Long playTime = aggregationEngine.extractStat(stats, "stats.minecraft:custom.minecraft:play_time");
        if (playTime != null) {
            long seconds = playTime / 20;
            message.append(Component.literal("Playtime: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formatDuration(seconds * 1000)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }

        // Deaths
        Long deaths = aggregationEngine.extractStat(stats, "stats.minecraft:custom.minecraft:deaths");
        if (deaths != null) {
            message.append(Component.literal("Deaths: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%,d", deaths)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }

        // Distance walked
        Long distance = aggregationEngine.extractStat(stats, "stats.minecraft:custom.minecraft:walk_one_cm");
        if (distance != null) {
            double km = distance / 100000.0;
            message.append(Component.literal("Distance Walked: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.1f km", km)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }

        // Jumps
        Long jumps = aggregationEngine.extractStat(stats, "stats.minecraft:custom.minecraft:jump");
        if (jumps != null) {
            message.append(Component.literal("Jumps: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%,d", jumps)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }
    }

    private Component formatState(RunState state) {
        return switch (state) {
            case ACTIVE -> Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN);
            case WIPED -> Component.literal("WIPED").withStyle(ChatFormatting.RED);
            case CONTINUED -> Component.literal("CONTINUED").withStyle(ChatFormatting.YELLOW);
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
    private static boolean requiresOp(CommandSourceStack source) {
        // For now, only allow console commands for admin operations
        // Players can be added later once we verify the correct API
        return source.getEntity() == null;
    }
}
