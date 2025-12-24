package me.veritasluxmea.PotionsManager.Utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EffectConfig {
    private final JavaPlugin plugin;
    private final Map<String, EffectData> effectCache;
    private final Map<String, String> tierDisplayNames;

    public EffectConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.effectCache = new ConcurrentHashMap<>();
        this.tierDisplayNames = new HashMap<>();
        loadCache();
    }

    /**
     * Loads all effect configurations into memory cache
     */
    public void loadCache() {
        effectCache.clear();
        tierDisplayNames.clear();

        // Load tier display names
        ConfigurationSection tierNames = plugin.getConfig().getConfigurationSection("tier_display_names");
        if (tierNames != null) {
            for (String tier : tierNames.getKeys(false)) {
                tierDisplayNames.put(tier, tierNames.getString(tier));
            }
        }

        // Load effect configurations
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
        if (effects == null) return;

        for (String effectName : effects.getKeys(false)) {
            ConfigurationSection effectSection = effects.getConfigurationSection(effectName);
            if (effectSection == null) continue;

            EffectData data = new EffectData();
            data.enabled = effectSection.getBoolean("enabled", false);
            data.permission = effectSection.getString("permission");

            // Load tier data
            ConfigurationSection tiers = effectSection.getConfigurationSection("tiers");
            if (tiers != null) {
                for (int i = 1; i <= 4; i++) {
                    String tierKey = "tier" + i;
                    ConfigurationSection tierSection = tiers.getConfigurationSection(tierKey);
                    if (tierSection != null) {
                        TierData tierData = new TierData();
                        tierData.permission = tierSection.getString("permission");
                        tierData.maxDuration = tierSection.getInt("max_duration", 1000000);
                        tierData.maxPower = tierSection.getInt("max_power", 255);
                        tierData.cooldownSeconds = tierSection.getInt("cooldown_seconds", 0);
                        data.tiers.put(tierKey, tierData);
                    }
                }
            }

            effectCache.put(effectName.toLowerCase(), data);
        }
    }

    /**
     * Reload cache when config is reloaded
     */
    public void reload() {
        loadCache();
    }

    /**
     * Check if an effect is enabled
     */
    public boolean isEffectEnabled(String effectName) {
        EffectData data = effectCache.get(effectName.toLowerCase());
        return data != null && data.enabled;
    }

    /**
     * Get the permission required for an effect
     */
    public String getEffectPermission(String effectName) {
        EffectData data = effectCache.get(effectName.toLowerCase());
        if (data == null || data.permission == null || data.permission.isEmpty()) {
            return null;
        }
        return data.permission;
    }

    /**
     * Determine the highest tier a player has for a specific effect
     */
    public String getTierForPlayer(Player player, String effectName) {
        EffectData data = effectCache.get(effectName.toLowerCase());
        if (data == null) return "tier1";

        // Check from tier4 down to tier1 (highest to lowest)
        for (int i = 4; i >= 1; i--) {
            String tierKey = "tier" + i;
            TierData tierData = data.tiers.get(tierKey);
            if (tierData != null && tierData.permission != null && !tierData.permission.isEmpty()) {
                if (player.hasPermission(tierData.permission)) {
                    return tierKey;
                }
            }
        }

        return "tier1"; // Default tier
    }

    /**
     * Get max duration for a player's tier (in seconds, -1 for infinite)
     */
    public int getMaxDuration(String effectName, Player player) {
        EffectData data = effectCache.get(effectName.toLowerCase());
        if (data == null) return 1000000;

        String tier = getTierForPlayer(player, effectName);
        TierData tierData = data.tiers.get(tier);
        return tierData != null ? tierData.maxDuration : 1000000;
    }

    /**
     * Get max power (amplifier) for a player's tier
     */
    public int getMaxPower(String effectName, Player player) {
        EffectData data = effectCache.get(effectName.toLowerCase());
        if (data == null) return 255;

        String tier = getTierForPlayer(player, effectName);
        TierData tierData = data.tiers.get(tier);
        return tierData != null ? tierData.maxPower : 255;
    }

    /**
     * Get cooldown seconds for a specific tier
     */
    public int getCooldownSeconds(String effectName, String tier) {
        EffectData data = effectCache.get(effectName.toLowerCase());
        if (data == null) return 0;

        TierData tierData = data.tiers.get(tier);
        return tierData != null ? tierData.cooldownSeconds : 0;
    }

    /**
     * Get tier display name
     */
    public String getTierDisplayName(String tier) {
        String displayName = tierDisplayNames.get(tier);
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }

        // Fallback to default names
        switch (tier) {
            case "tier1": return "Basic";
            case "tier2": return "VIP";
            case "tier3": return "Premium";
            case "tier4": return "Ultimate";
            default: return tier.toUpperCase();
        }
    }

    /**
     * Get all enabled effect names
     */
    public Map<String, EffectData> getEnabledEffects() {
        Map<String, EffectData> enabled = new HashMap<>();
        for (Map.Entry<String, EffectData> entry : effectCache.entrySet()) {
            if (entry.getValue().enabled) {
                enabled.put(entry.getKey(), entry.getValue());
            }
        }
        return enabled;
    }

    /**
     * Data class for effect configuration
     */
    public static class EffectData {
        public boolean enabled;
        public String permission;
        public final Map<String, TierData> tiers = new HashMap<>();
    }

    /**
     * Data class for tier configuration
     */
    public static class TierData {
        public String permission;
        public int maxDuration;
        public int maxPower;
        public int cooldownSeconds;
    }
}
