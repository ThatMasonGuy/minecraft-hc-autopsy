package tempeststudios.hcautopsy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import tempeststudios.hcautopsy.HCAutopsy;
import tempeststudios.hcautopsy.compat.PlayerMessageCompat;
import tempeststudios.hcautopsy.compat.ServerPermissionCompat;
import tempeststudios.hcautopsy.compat.TextEventCompat;
import tempeststudios.hcautopsy.data.RunMetadata;
import tempeststudios.hcautopsy.data.RunState;
import tempeststudios.hcautopsy.data.WipeCause;
import tempeststudios.hcautopsy.lifecycle.RunManager;
import tempeststudios.hcautopsy.notification.DiscordNotifier;
import tempeststudios.hcautopsy.persistence.PersistenceManager;
import tempeststudios.hcautopsy.presentation.RunDisplayNames;
import tempeststudios.hcautopsy.stats.AggregationEngine;
import tempeststudios.hcautopsy.stats.WipeLeaderboardBuilder;
import tempeststudios.hcautopsy.stats.WipeLeaderboardRankingsReport;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command registration and handlers for /hcautopsy.
 */
public class CommandRegistry {
    private static final int LIST_LIMIT = 10;
    private static final int PLAYER_LIST_LIMIT = 20;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PersistenceManager persistence;
    private final AggregationEngine aggregationEngine;
    private final WipeLeaderboardBuilder wipeLeaderboardBuilder;

    public CommandRegistry(PersistenceManager persistence) {
        this.persistence = persistence;
        this.aggregationEngine = new AggregationEngine();
        this.wipeLeaderboardBuilder = new WipeLeaderboardBuilder();
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess,
                         Commands.CommandSelection environment) {
        dispatcher.register(
                Commands.literal("hcautopsy")
                        .requires(source -> true)
                        .executes(this::statusCommand)
                        .then(Commands.literal("status")
                                .executes(this::statusCommand))
                        .then(Commands.literal("players")
                                .executes(this::playersCommand))
                        .then(Commands.literal("leaderboard")
                                .then(Commands.literal("playtime")
                                        .executes(ctx -> leaderboardCommand(ctx, LeaderboardStat.PLAYTIME)))
                                .then(Commands.literal("deaths")
                                        .executes(ctx -> leaderboardCommand(ctx, LeaderboardStat.DEATHS)))
                                .then(Commands.literal("walked")
                                        .executes(ctx -> leaderboardCommand(ctx, LeaderboardStat.WALKED)))
                                .then(Commands.literal("jumps")
                                        .executes(ctx -> leaderboardCommand(ctx, LeaderboardStat.JUMPS)))
                                .then(Commands.literal("postwipe")
                                        .requires(CommandRegistry::requiresAdminCommandSource)
                                        .executes(this::postWipeLeaderboardCommand)))
                        .then(Commands.literal("run")
                                .then(Commands.literal("last")
                                        .executes(this::runLastCommand))
                                .then(Commands.literal("list")
                                        .executes(this::runListCommand))
                                .then(Commands.literal("continue")
                                        .requires(CommandRegistry::requiresAdminCommandSource)
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(this::runContinueCommand)))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(this::suggestRunIds)
                                        .executes(this::runDetailCommand)))
                        .then(Commands.literal("player")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(this::suggestPlayerNames)
                                        .then(Commands.literal("totals")
                                                .executes(this::playerTotalsCommand))))
                        .then(Commands.literal("server")
                                .then(Commands.literal("totals")
                                        .executes(this::serverTotalsCommand)))
                        .then(Commands.literal("recalc")
                                .requires(CommandRegistry::requiresAdminCommandSource)
                                .executes(this::recalculateCommand))
                        .then(Commands.literal("config")
                                .requires(CommandRegistry::requiresAdminCommandSource)
                                .then(Commands.literal("reload")
                                        .executes(this::configReloadCommand)))
                        .then(Commands.literal("discord")
                                .requires(CommandRegistry::requiresAdminCommandSource)
                                .then(Commands.literal("test")
                                        .executes(this::discordTestCommand)))
        );
    }

    private int statusCommand(CommandContext<CommandSourceStack> ctx) {
        RunManager runManager = HCAutopsy.getRunManager();
        if (runManager == null) {
            ctx.getSource().sendSystemMessage(Component.literal("HC Autopsy is not attached to a running server yet.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        RunMetadata activeRun = runManager.getActiveRun();
        if (activeRun == null) {
            ctx.getSource().sendSystemMessage(Component.literal("No active run.").withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        MutableComponent message = Component.literal("=== HC Autopsy Status ===\n").withStyle(ChatFormatting.GOLD);

        message.append(Component.literal("Run: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(RunDisplayNames.runId(activeRun.getRunId())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"));

        message.append(Component.literal("World: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(RunDisplayNames.world(activeRun.getWorldName())).withStyle(ChatFormatting.GREEN))
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
            message.append(Component.literal("\n\nWIPED by ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(cause.playerName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal("\n"))
                    .append(Component.literal(cause.deathMessage()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        ctx.getSource().sendSystemMessage(message);
        return 1;
    }

    private int runLastCommand(CommandContext<CommandSourceStack> ctx) {
        RunMetadata lastWiped = persistence.getLastWipedRun();

        if (lastWiped == null) {
            ctx.getSource().sendSystemMessage(Component.literal("No wiped runs found.").withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        showRunDetails(ctx.getSource(), lastWiped);
        return 1;
    }

    private int runListCommand(CommandContext<CommandSourceStack> ctx) {
        List<String> runIds = persistence.getAllRunIds();

        if (runIds.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No runs recorded.").withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        MutableComponent message = Component.literal("=== All Runs (" + runIds.size() + ") ===\n").withStyle(ChatFormatting.GOLD);

        int shown = 0;
        for (String runId : runIds) {
            if (shown >= LIST_LIMIT) {
                message.append(Component.literal("... and " + (runIds.size() - LIST_LIMIT) + " more\n")
                        .withStyle(ChatFormatting.GRAY));
                break;
            }

            RunMetadata meta = persistence.loadMetadata(runId);
            if (meta == null) {
                continue;
            }

            MutableComponent runEntry = Component.literal("- ")
                    .append(Component.literal(RunDisplayNames.world(meta.getWorldName())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(formatState(meta.getState()))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formatDuration(meta.getDurationMs())).withStyle(ChatFormatting.AQUA));

            TextEventCompat.applyRunCommand(runEntry, "/hcautopsy run " + runId,
                    Component.literal("Click for details"));

            message.append(runEntry).append(Component.literal("\n"));
            shown++;
        }

        ctx.getSource().sendSystemMessage(message);
        return 1;
    }

    private int runDetailCommand(CommandContext<CommandSourceStack> ctx) {
        String runId = StringArgumentType.getString(ctx, "id");
        RunMetadata meta = findRun(runId);

        if (meta == null) {
            ctx.getSource().sendSystemMessage(Component.literal("Run not found: " + runId).withStyle(ChatFormatting.RED));
            return 0;
        }

        showRunDetails(ctx.getSource(), meta);
        return 1;
    }

    private int runContinueCommand(CommandContext<CommandSourceStack> ctx) {
        String reason = StringArgumentType.getString(ctx, "reason");
        RunManager runManager = HCAutopsy.getRunManager();
        if (runManager == null) {
            ctx.getSource().sendSystemMessage(Component.literal("HC Autopsy is not ready yet.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        RunManager.ContinueResult result = runManager.continueRun(reason);
        if (result == RunManager.ContinueResult.SUCCESS) {
            ctx.getSource().sendSystemMessage(Component.literal("Run continued. Death struck from record.")
                    .withStyle(ChatFormatting.GREEN));
            ctx.getSource().sendSystemMessage(Component.literal("Reason: " + reason).withStyle(ChatFormatting.GRAY));
            return 1;
        }
        if (result == RunManager.ContinueResult.WIPE_FINALIZING) {
            ctx.getSource().sendSystemMessage(Component.literal("The wipe is still finalizing. Try again after snapshots finish.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        ctx.getSource().sendSystemMessage(Component.literal("No wiped run to continue.")
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    private int playerTotalsCommand(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "name");
        UUID playerUuid = resolvePlayerUuid(ctx.getSource(), playerName);

        if (playerUuid == null) {
            ctx.getSource().sendSystemMessage(Component.literal("Player not found in online players or HC Autopsy's name cache.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        String stats = persistence.loadLifetimePlayerStats(playerUuid);
        if (stats == null) {
            ctx.getSource().sendSystemMessage(Component.literal("No lifetime stats for " + playerDisplayName(playerUuid, playerName))
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        showStatsSummary(ctx.getSource(), "Lifetime Stats: " + playerDisplayName(playerUuid, playerName), stats);
        return 1;
    }

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

    private int playersCommand(CommandContext<CommandSourceStack> ctx) {
        List<String> names = persistence.getKnownPlayerNames();
        if (names.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No cached HC Autopsy players yet.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        MutableComponent message = Component.literal("=== Cached Players (" + names.size() + ") ===\n")
                .withStyle(ChatFormatting.GOLD);
        int shown = 0;
        for (String name : names) {
            if (shown >= PLAYER_LIST_LIMIT) {
                message.append(Component.literal("... and " + (names.size() - PLAYER_LIST_LIMIT) + " more\n")
                        .withStyle(ChatFormatting.GRAY));
                break;
            }
            message.append(Component.literal("- " + name + "\n").withStyle(ChatFormatting.WHITE));
            shown++;
        }
        ctx.getSource().sendSystemMessage(message);
        return 1;
    }

    private int leaderboardCommand(CommandContext<CommandSourceStack> ctx, LeaderboardStat stat) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (UUID uuid : persistence.getAllLifetimePlayerUuids()) {
            String stats = persistence.loadLifetimePlayerStats(uuid);
            Long value = aggregationEngine.extractStat(stats, stat.path);
            if (value != null) {
                entries.add(new LeaderboardEntry(uuid, value));
            }
        }

        if (entries.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No lifetime data for " + stat.label + " yet.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        entries.sort(Comparator.comparingLong(LeaderboardEntry::value).reversed());

        MutableComponent message = Component.literal("=== " + stat.label + " Leaderboard ===\n")
                .withStyle(ChatFormatting.GOLD);
        int rank = 1;
        for (LeaderboardEntry entry : entries.stream().limit(LIST_LIMIT).toList()) {
            message.append(Component.literal(rank + ". ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(playerDisplayName(entry.playerUuid(), null)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(stat.format(entry.value())).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n"));
            rank++;
        }

        ctx.getSource().sendSystemMessage(message);
        return 1;
    }

    private int postWipeLeaderboardCommand(CommandContext<CommandSourceStack> ctx) {
        RunMetadata lastWiped = persistence.getLastWipedRun();
        if (lastWiped == null) {
            ctx.getSource().sendSystemMessage(Component.literal("No wiped runs found.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        Map<UUID, String> snapshots = persistence.loadAllPlayerSnapshots(lastWiped.getRunId());
        if (snapshots.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No player snapshots found for run "
                            + RunDisplayNames.runId(lastWiped.getRunId()) + ".")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        WipeLeaderboardRankingsReport rankings = wipeLeaderboardBuilder.buildRankings(
                snapshots,
                uuid -> playerDisplayName(uuid, null)
        );
        if (rankings.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No leaderboard stats found for run "
                            + RunDisplayNames.runId(lastWiped.getRunId()) + ".")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        int playerMessagesFailed = broadcastPostWipeLeaderboard(ctx.getSource(), lastWiped, rankings);
        DiscordNotifier notifier = HCAutopsy.getDiscordNotifier();
        boolean discordQueued = false;
        if (notifier != null && notifier.isConfigured()) {
            notifier.sendWipeLeaderboard(lastWiped, rankings);
            discordQueued = true;
        }

        MutableComponent confirmation = Component.literal("Post-wipe leaderboard broadcast for run ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(RunDisplayNames.runId(lastWiped.getRunId())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(".").withStyle(ChatFormatting.GREEN));
        ctx.getSource().sendSystemMessage(confirmation);

        if (!discordQueued) {
            ctx.getSource().sendSystemMessage(Component.literal("Discord notifications are disabled or missing a webhook URL.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (playerMessagesFailed > 0) {
            ctx.getSource().sendSystemMessage(Component.literal("Failed to send " + playerMessagesFailed + " in-game leaderboard message(s).")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private int broadcastPostWipeLeaderboard(
            CommandSourceStack source,
            RunMetadata run,
            WipeLeaderboardRankingsReport rankings
    ) {
        List<Component> lines = buildPostWipeLeaderboardLines(run, rankings);
        int failedMessages = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            for (Component line : lines) {
                if (!PlayerMessageCompat.sendSystemMessage(player, line)) {
                    failedMessages++;
                }
            }
        }
        return failedMessages;
    }

    private List<Component> buildPostWipeLeaderboardLines(RunMetadata run, WipeLeaderboardRankingsReport rankings) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[HC Autopsy] Full post-wipe leaderboard")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal("Run: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(RunDisplayNames.world(run.getWorldName())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | Players: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(rankings.playerCount())).withStyle(ChatFormatting.AQUA)));

        for (WipeLeaderboardRankingsReport.Category category : rankings.categories()) {
            lines.add(Component.literal(category.label()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            for (WipeLeaderboardRankingsReport.RankedEntry entry : category.entries()) {
                MutableComponent line = Component.literal(entry.rank() + ". ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(entry.playerName()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(entry.formattedValue()).withStyle(ChatFormatting.AQUA));
                lines.add(line);
            }
        }
        return lines;
    }

    private int recalculateCommand(CommandContext<CommandSourceStack> ctx) {
        persistence.recalculateLifetimeStats();
        ctx.getSource().sendSystemMessage(Component.literal("HC Autopsy lifetime totals recalculated from wiped runs.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int configReloadCommand(CommandContext<CommandSourceStack> ctx) {
        HCAutopsy.reloadConfig();
        ctx.getSource().sendSystemMessage(Component.literal("HC Autopsy config reloaded.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int discordTestCommand(CommandContext<CommandSourceStack> ctx) {
        DiscordNotifier notifier = HCAutopsy.getDiscordNotifier();
        if (notifier == null || !notifier.isConfigured()) {
            ctx.getSource().sendSystemMessage(Component.literal("Discord notifications are disabled or missing a webhook URL.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        RunManager runManager = HCAutopsy.getRunManager();
        RunMetadata activeRun = runManager == null ? null : runManager.getActiveRun();
        String worldName = activeRun == null ? "unknown" : activeRun.getWorldName();
        notifier.sendTestNotification(sourceName(ctx.getSource()), worldName);
        ctx.getSource().sendSystemMessage(Component.literal("Discord test notification queued.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private void showRunDetails(CommandSourceStack source, RunMetadata meta) {
        MutableComponent message = Component.literal("=== Run: " + RunDisplayNames.world(meta.getWorldName()) + " ===\n")
                .withStyle(ChatFormatting.GOLD);

        message.append(Component.literal("ID: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(RunDisplayNames.runId(meta.getRunId())).withStyle(ChatFormatting.DARK_GRAY))
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
            message.append(Component.literal("\nDeath: ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(cause.playerName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal("\n"))
                    .append(Component.literal(cause.deathMessage()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                    .append(Component.literal("\n"))
                    .append(Component.literal("Cause: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(cause.damageSource()).withStyle(ChatFormatting.WHITE));
        }

        if (!meta.getContinueHistory().isEmpty()) {
            message.append(Component.literal("\n\nRun was continued " + meta.getContinueHistory().size() + " time(s)")
                    .withStyle(ChatFormatting.YELLOW));
        }

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
        Long playTime = aggregationEngine.extractStat(stats, LeaderboardStat.PLAYTIME.path);
        if (playTime != null) {
            message.append(Component.literal("Playtime: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formatDuration((playTime / 20) * 1000)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }

        Long deaths = aggregationEngine.extractStat(stats, LeaderboardStat.DEATHS.path);
        if (deaths != null) {
            message.append(Component.literal("Deaths: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%,d", deaths)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }

        Long distance = aggregationEngine.extractStat(stats, LeaderboardStat.WALKED.path);
        if (distance != null) {
            message.append(Component.literal("Distance Walked: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(LeaderboardStat.WALKED.format(distance)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }

        Long jumps = aggregationEngine.extractStat(stats, LeaderboardStat.JUMPS.path);
        if (jumps != null) {
            message.append(Component.literal("Jumps: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%,d", jumps)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));
        }
    }

    private RunMetadata findRun(String runId) {
        RunMetadata meta = persistence.loadMetadata(runId);
        if (meta != null) {
            return meta;
        }

        for (String id : persistence.getAllRunIds()) {
            if (id.contains(runId)) {
                return persistence.loadMetadata(id);
            }
        }
        return null;
    }

    private UUID resolvePlayerUuid(CommandSourceStack source, String playerName) {
        ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            persistence.rememberPlayer(onlinePlayer.getUUID(), onlinePlayer.getName().getString());
            return onlinePlayer.getUUID();
        }
        return persistence.findKnownPlayerUuidByName(playerName);
    }

    private String playerDisplayName(UUID playerUuid, String fallback) {
        String knownName = persistence.getKnownPlayerName(playerUuid);
        if (knownName != null && !knownName.isBlank()) {
            return knownName;
        }
        return fallback == null || fallback.isBlank() ? playerUuid.toString() : fallback;
    }

    private CompletableFuture<Suggestions> suggestPlayerNames(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String prefix = builder.getRemainingLowerCase();
        List<String> suggestions = new ArrayList<>(persistence.getKnownPlayerNames());
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            suggestions.add(player.getName().getString());
        }

        suggestions.stream()
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRunIds(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        String prefix = builder.getRemainingLowerCase();
        persistence.getAllRunIds().stream()
                .filter(runId -> runId.toLowerCase(Locale.ROOT).contains(prefix))
                .forEach(builder::suggest);
        return builder.buildFuture();
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

    private String sourceName(CommandSourceStack source) {
        try {
            return source.getTextName();
        } catch (RuntimeException e) {
            return "command source";
        }
    }

    private static boolean requiresAdminCommandSource(CommandSourceStack source) {
        return ServerPermissionCompat.hasCommandLevel(source, 2);
    }

    private enum LeaderboardStat {
        PLAYTIME("Playtime", "stats.minecraft:custom.minecraft:play_time", ValueFormat.TICKS),
        DEATHS("Deaths", "stats.minecraft:custom.minecraft:deaths", ValueFormat.NUMBER),
        WALKED("Distance Walked", "stats.minecraft:custom.minecraft:walk_one_cm", ValueFormat.CENTIMETERS),
        JUMPS("Jumps", "stats.minecraft:custom.minecraft:jump", ValueFormat.NUMBER);

        private final String label;
        private final String path;
        private final ValueFormat format;

        LeaderboardStat(String label, String path, ValueFormat format) {
            this.label = label;
            this.path = path;
            this.format = format;
        }

        private String format(long value) {
            return switch (format) {
                case TICKS -> formatDurationStatic((value / 20) * 1000);
                case CENTIMETERS -> String.format("%.1f km", value / 100000.0);
                case NUMBER -> String.format("%,d", value);
            };
        }
    }

    private enum ValueFormat {
        TICKS,
        CENTIMETERS,
        NUMBER
    }

    private record LeaderboardEntry(UUID playerUuid, long value) {
    }

    private static String formatDurationStatic(long ms) {
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
}
