package me.veritasluxmea.PotionsManager.Utils;

import me.veritasluxmea.PotionsManager.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages persistence of cooldown data to disk.
 * Saves cooldowns on shutdown and loads them on startup.
 */
public class DataManager {

    private final JavaPlugin plugin;
    private final File cooldownsFile;
    private FileConfiguration cooldownsConfig;

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cooldownsFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        loadCooldownsFile();
    }

    /**
     * Loads or creates the cooldowns.yml file.
     */
    private void loadCooldownsFile() {
        if (!cooldownsFile.exists()) {
            try {
                cooldownsFile.getParentFile().mkdirs();
                cooldownsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, Main.messages.getConfig().getString("data.cooldowns.file_create_error"), e);
            }
        }
        cooldownsConfig = YamlConfiguration.loadConfiguration(cooldownsFile);
    }

    /**
     * Saves cooldowns to disk.
     *
     * @param cooldownManager The CooldownManager instance containing cooldowns to save
     */
    public void saveCooldowns(CooldownManager cooldownManager) {
        try {
            // Clear the existing configuration
            for (String key : cooldownsConfig.getKeys(false)) {
                cooldownsConfig.set(key, null);
            }

            // Get all cooldowns from the manager
            Map<UUID, Map<String, Map<String, Long>>> allCooldowns = cooldownManager.getAllCooldowns();

            // Clean up expired cooldowns before saving
            long currentTime = System.currentTimeMillis();

            // Save each player's cooldowns
            for (Map.Entry<UUID, Map<String, Map<String, Long>>> playerEntry : allCooldowns.entrySet()) {
                UUID playerUUID = playerEntry.getKey();
                String playerPath = "cooldowns." + playerUUID.toString();

                for (Map.Entry<String, Map<String, Long>> effectEntry : playerEntry.getValue().entrySet()) {
                    String effectName = effectEntry.getKey();

                    for (Map.Entry<String, Long> tierEntry : effectEntry.getValue().entrySet()) {
                        String tierName = tierEntry.getKey();
                        Long expirationTime = tierEntry.getValue();

                        // Only save cooldowns that haven't expired
                        if (expirationTime != null && expirationTime > currentTime) {
                            String path = playerPath + "." + effectName + "." + tierName;
                            cooldownsConfig.set(path, expirationTime);
                        }
                    }
                }
            }

            // Save to file
            cooldownsConfig.save(cooldownsFile);
            plugin.getLogger().info(Main.messages.getConfig().getString("data.cooldowns.save_success"));

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, Main.messages.getConfig().getString("data.cooldowns.save_error"), e);
        }
    }

    /**
     * Loads cooldowns from disk.
     *
     * @param cooldownManager The CooldownManager instance to load cooldowns into
     */
    public void loadCooldowns(CooldownManager cooldownManager) {
        try {
            // Reload the file to get latest data
            cooldownsConfig = YamlConfiguration.loadConfiguration(cooldownsFile);

            ConfigurationSection cooldownsSection = cooldownsConfig.getConfigurationSection("cooldowns");
            if (cooldownsSection == null) {
                plugin.getLogger().info(Main.messages.getConfig().getString("data.cooldowns.load_none"));
                return;
            }

            Map<UUID, Map<String, Map<String, Long>>> loadedCooldowns = new HashMap<>();
            long currentTime = System.currentTimeMillis();
            int loadedCount = 0;
            int expiredCount = 0;

            // Load each player's cooldowns
            for (String playerUUIDString : cooldownsSection.getKeys(false)) {
                UUID playerUUID;
                try {
                    playerUUID = UUID.fromString(playerUUIDString);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning(Main.messages.getConfig().getString("data.cooldowns.invalid_uuid")
                        .replace("{uuid}", playerUUIDString));
                    continue;
                }

                ConfigurationSection playerSection = cooldownsSection.getConfigurationSection(playerUUIDString);
                if (playerSection == null) {
                    continue;
                }

                Map<String, Map<String, Long>> playerCooldowns = new HashMap<>();

                // Load each effect's cooldowns
                for (String effectName : playerSection.getKeys(false)) {
                    ConfigurationSection effectSection = playerSection.getConfigurationSection(effectName);
                    if (effectSection == null) {
                        continue;
                    }

                    Map<String, Long> effectCooldowns = new HashMap<>();

                    // Load each tier's cooldown
                    for (String tierName : effectSection.getKeys(false)) {
                        long expirationTime = effectSection.getLong(tierName, 0L);

                        // Only load cooldowns that haven't expired
                        if (expirationTime > currentTime) {
                            effectCooldowns.put(tierName, expirationTime);
                            loadedCount++;
                        } else {
                            expiredCount++;
                        }
                    }

                    if (!effectCooldowns.isEmpty()) {
                        playerCooldowns.put(effectName, effectCooldowns);
                    }
                }

                if (!playerCooldowns.isEmpty()) {
                    loadedCooldowns.put(playerUUID, playerCooldowns);
                }
            }

            // Load the cooldowns into the manager
            cooldownManager.loadCooldowns(loadedCooldowns);

            plugin.getLogger().info(Main.messages.getConfig().getString("data.cooldowns.load_success")
                .replace("{loaded}", String.valueOf(loadedCount))
                .replace("{expired}", String.valueOf(expiredCount)));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, Main.messages.getConfig().getString("data.cooldowns.load_error"), e);
        }
    }

    /**
     * Deletes the cooldowns file (useful for cleanup or reset).
     */
    public void deleteCooldownsFile() {
        if (cooldownsFile.exists()) {
            cooldownsFile.delete();
            plugin.getLogger().info(Main.messages.getConfig().getString("data.cooldowns.file_deleted"));
        }
    }

    /**
     * Gets the cooldowns file for direct access if needed.
     *
     * @return The cooldowns file
     */
    public File getCooldownsFile() {
        return cooldownsFile;
    }
}
