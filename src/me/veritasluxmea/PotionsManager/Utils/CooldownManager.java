package me.veritasluxmea.PotionsManager.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cooldowns for potion effects per player per effect per tier.
 * Cooldowns are stored in memory and can be persisted to disk via DataManager.
 */
public class CooldownManager {

    // Structure: playerUUID -> effectName -> tierName -> expirationTimestamp
    private final Map<UUID, Map<String, Map<String, Long>>> cooldowns;

    public CooldownManager() {
        this.cooldowns = new ConcurrentHashMap<>();
    }

    /**
     * Sets a cooldown for a player's effect and tier.
     *
     * @param playerUUID The UUID of the player
     * @param effectName The name of the effect (e.g., "speed")
     * @param tierName The tier name (e.g., "tier1", "tier2", "tier3")
     * @param cooldownSeconds The cooldown duration in seconds
     */
    public void setCooldown(UUID playerUUID, String effectName, String tierName, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return; // No cooldown to set
        }

        long expirationTime = System.currentTimeMillis() + (cooldownSeconds * 1000L);

        cooldowns.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
                 .computeIfAbsent(effectName, k -> new ConcurrentHashMap<>())
                 .put(tierName, expirationTime);
    }

    /**
     * Checks if a player has an active cooldown for a specific effect and tier.
     *
     * @param playerUUID The UUID of the player
     * @param effectName The name of the effect
     * @param tierName The tier name
     * @return true if the player is on cooldown, false otherwise
     */
    public boolean hasCooldown(UUID playerUUID, String effectName, String tierName) {
        Map<String, Map<String, Long>> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns == null) {
            return false;
        }

        Map<String, Long> effectCooldowns = playerCooldowns.get(effectName);
        if (effectCooldowns == null) {
            return false;
        }

        Long expirationTime = effectCooldowns.get(tierName);
        if (expirationTime == null) {
            return false;
        }

        // Check if cooldown has expired
        if (System.currentTimeMillis() >= expirationTime) {
            // Cooldown expired, remove it
            effectCooldowns.remove(tierName);
            if (effectCooldowns.isEmpty()) {
                playerCooldowns.remove(effectName);
            }
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(playerUUID);
            }
            return false;
        }

        return true;
    }

    /**
     * Gets the remaining cooldown time in seconds for a player's effect and tier.
     *
     * @param playerUUID The UUID of the player
     * @param effectName The name of the effect
     * @param tierName The tier name
     * @return The remaining cooldown time in seconds, or 0 if no cooldown is active
     */
    public long getRemainingCooldown(UUID playerUUID, String effectName, String tierName) {
        Map<String, Map<String, Long>> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns == null) {
            return 0;
        }

        Map<String, Long> effectCooldowns = playerCooldowns.get(effectName);
        if (effectCooldowns == null) {
            return 0;
        }

        Long expirationTime = effectCooldowns.get(tierName);
        if (expirationTime == null) {
            return 0;
        }

        long remainingMs = expirationTime - System.currentTimeMillis();
        if (remainingMs <= 0) {
            // Cooldown expired
            effectCooldowns.remove(tierName);
            if (effectCooldowns.isEmpty()) {
                playerCooldowns.remove(effectName);
            }
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(playerUUID);
            }
            return 0;
        }

        return (remainingMs + 999) / 1000; // Round up to nearest second
    }

    /**
     * Formats remaining cooldown time into a human-readable string.
     *
     * @param seconds The number of seconds
     * @return A formatted string (e.g., "5m 30s", "45s", "2h 15m")
     */
    public String formatCooldownTime(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (secs > 0 || sb.length() == 0) {
            sb.append(secs).append("s");
        }

        return sb.toString().trim();
    }

    /**
     * Gets all cooldowns data for serialization.
     *
     * @return A map of all cooldowns
     */
    public Map<UUID, Map<String, Map<String, Long>>> getAllCooldowns() {
        return new HashMap<>(cooldowns);
    }

    /**
     * Loads cooldowns from a data structure (typically from disk).
     *
     * @param loadedCooldowns The cooldowns data to load
     */
    public void loadCooldowns(Map<UUID, Map<String, Map<String, Long>>> loadedCooldowns) {
        if (loadedCooldowns == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Only load cooldowns that haven't expired
        for (Map.Entry<UUID, Map<String, Map<String, Long>>> playerEntry : loadedCooldowns.entrySet()) {
            UUID playerUUID = playerEntry.getKey();

            for (Map.Entry<String, Map<String, Long>> effectEntry : playerEntry.getValue().entrySet()) {
                String effectName = effectEntry.getKey();

                for (Map.Entry<String, Long> tierEntry : effectEntry.getValue().entrySet()) {
                    String tierName = tierEntry.getKey();
                    Long expirationTime = tierEntry.getValue();

                    // Only load if cooldown hasn't expired
                    if (expirationTime != null && expirationTime > currentTime) {
                        cooldowns.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
                                 .computeIfAbsent(effectName, k -> new ConcurrentHashMap<>())
                                 .put(tierName, expirationTime);
                    }
                }
            }
        }
    }

    /**
     * Section below is not yet implemented but can be
     * called to if PotionManager is implemented in other plugins
     */

    /**
     * Removes expired cooldowns from memory.
     * This can be called periodically to clean up old data.
     */
    public void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();

        cooldowns.entrySet().removeIf(playerEntry -> {
            Map<String, Map<String, Long>> playerCooldowns = playerEntry.getValue();

            playerCooldowns.entrySet().removeIf(effectEntry -> {
                Map<String, Long> effectCooldowns = effectEntry.getValue();

                effectCooldowns.entrySet().removeIf(tierEntry ->
                    tierEntry.getValue() <= currentTime
                );

                return effectCooldowns.isEmpty();
            });

            return playerCooldowns.isEmpty();
        });
    }

    /**
     * Clears all cooldowns for all players (typically used on shutdown).
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
    }

    /**
     * Clears a specific cooldown for a player's effect and tier.
     *
     * @param playerUUID The UUID of the player
     * @param effectName The name of the effect
     * @param tierName The tier name
     */
    public void clearCooldown(UUID playerUUID, String effectName, String tierName) {
        Map<String, Map<String, Long>> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns == null) {
            return;
        }

        Map<String, Long> effectCooldowns = playerCooldowns.get(effectName);
        if (effectCooldowns == null) {
            return;
        }

        effectCooldowns.remove(tierName);
        if (effectCooldowns.isEmpty()) {
            playerCooldowns.remove(effectName);
        }
        if (playerCooldowns.isEmpty()) {
            cooldowns.remove(playerUUID);
        }
    }

    /**
     * Clears all cooldowns for a specific player.
     *
     * @param playerUUID The UUID of the player
     */
    public void clearPlayerCooldowns(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }
}
