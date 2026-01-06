package tempeststudios.hcautopsy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import tempeststudios.hcautopsy.HCAutopsy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration holder for HC Autopsy.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Configuration values with defaults
    private String discordWebhookUrl = "";
    private boolean discordNotificationsEnabled = true;
    private int statSaveDelayMs = 500;

    // Transient - not serialized
    private transient Path configPath;

    /**
     * Private constructor - use load() to get instance.
     */
    private ModConfig() {
    }

    /**
     * Load configuration from disk, creating default if missing.
     */
    public static ModConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("hc-autopsy");
        Path configPath = configDir.resolve("config.json");

        ModConfig config;

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = GSON.fromJson(json, ModConfig.class);
                HCAutopsy.LOGGER.info("Loaded HC Autopsy configuration");
            } catch (IOException e) {
                HCAutopsy.LOGGER.error("Failed to load config, using defaults: {}", e.getMessage());
                config = new ModConfig();
            }
        } else {
            config = new ModConfig();
            HCAutopsy.LOGGER.info("Created default HC Autopsy configuration");
        }

        config.configPath = configPath;
        config.save(); // Ensure config file exists with all fields

        return config;
    }

    /**
     * Save configuration to disk.
     */
    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    // ==================== Getters ====================

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public boolean isDiscordNotificationsEnabled() {
        return discordNotificationsEnabled;
    }

    public int getStatSaveDelayMs() {
        return statSaveDelayMs;
    }

    // ==================== Setters ====================

    public void setDiscordWebhookUrl(String url) {
        this.discordWebhookUrl = url;
        save();
    }

    public void setDiscordNotificationsEnabled(boolean enabled) {
        this.discordNotificationsEnabled = enabled;
        save();
    }

    public void setStatSaveDelayMs(int delayMs) {
        this.statSaveDelayMs = delayMs;
        save();
    }
}
