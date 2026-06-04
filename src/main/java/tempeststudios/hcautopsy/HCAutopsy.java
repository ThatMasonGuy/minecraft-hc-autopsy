package tempeststudios.hcautopsy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tempeststudios.hcautopsy.command.CommandRegistry;
import tempeststudios.hcautopsy.config.ModConfig;
import tempeststudios.hcautopsy.lifecycle.RunManager;
import tempeststudios.hcautopsy.notification.DiscordNotifier;
import tempeststudios.hcautopsy.persistence.PersistenceManager;

/**
 * HC Autopsy - Hardcore Minecraft Analytics and Postmortem System
 * 
 * This mod tracks hardcore world runs, captures complete player statistics
 * at the moment of wipe (first death), and maintains historical data for
 * long-term analysis.
 */
public class HCAutopsy implements ModInitializer {
	public static final String MOD_ID = "hc-autopsy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Core components - initialized on server start
	private static ModConfig config;
	private static PersistenceManager persistence;
	private static RunManager runManager;
	private static DiscordNotifier discordNotifier;
	private static CommandRegistry commandRegistry;

	@Override
	public void onInitialize() {
		LOGGER.info("HC Autopsy initializing...");

		// Load configuration
		config = ModConfig.load();

		// Initialize persistence (creates directories)
		persistence = new PersistenceManager();

		// Initialize Discord notifier
		discordNotifier = new DiscordNotifier(config);

		// Initialize command registry
		commandRegistry = new CommandRegistry(persistence);

		// Register commands
		CommandRegistrationCallback.EVENT.register(commandRegistry::register);

		// Register server lifecycle events
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("Server started - initializing run manager");
			runManager = new RunManager(server, persistence, discordNotifier);
			runManager.onServerStart();
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Server stopping - cleaning up run manager");
			if (runManager != null) {
				runManager.onServerStop();
			}
		});

		// Register player join events to track participants
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (runManager != null) {
				runManager.registerPlayer(handler.getPlayer());
			}
		});

		HCAutopsyServerSmokeTest.registerIfEnabled();

		LOGGER.info("HC Autopsy initialized successfully");
	}

	/**
	 * Get the run manager instance.
	 * May return null if server hasn't started yet.
	 */
	public static RunManager getRunManager() {
		return runManager;
	}

	/**
	 * Get the persistence manager instance.
	 */
	public static PersistenceManager getPersistence() {
		return persistence;
	}

	/**
	 * Get the mod configuration.
	 */
	public static ModConfig getConfig() {
		return config;
	}

	/**
	 * Get the Discord notifier instance.
	 */
	public static DiscordNotifier getDiscordNotifier() {
		return discordNotifier;
	}
}
