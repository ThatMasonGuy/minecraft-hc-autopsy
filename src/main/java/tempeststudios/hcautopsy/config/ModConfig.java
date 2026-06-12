package tempeststudios.hcautopsy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import tempeststudios.hcautopsy.HCAutopsy;
import tempeststudios.hcautopsy.storage.HCAutopsyData;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        Path configDir = HCAutopsyData.dataDir();
        Path configPath = configDir.resolve("config.json");

        ModConfig config;

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = GSON.fromJson(json, ModConfig.class);
                if (config == null) {
                    throw new IllegalStateException("Config file parsed to null");
                }
                HCAutopsy.LOGGER.info("Loaded HC Autopsy configuration");
            } catch (IOException | RuntimeException e) {
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
            writeStringAtomic(configPath, GSON.toJson(this));
        } catch (IOException e) {
            HCAutopsy.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    private static void writeStringAtomic(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path tempPath = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tempPath, content);
            try {
                Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
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
        return Math.max(0, statSaveDelayMs);
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
        this.statSaveDelayMs = Math.max(0, delayMs);
        save();
    }
}
