package me.veritasluxmea.PotionsManager.Commands;

import me.veritasluxmea.PotionsManager.Main;
import me.veritasluxmea.PotionsManager.Utils.CooldownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;

public class PotionCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public PotionCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        FileConfiguration config = Main.settings.getConfig();

        if (args.length == 0) {
            sender.sendMessage("§c§lUsage: §7/potion §e<effect> §7[duration] [power] [player]");
            return true;
        }

        // Handle list command (player-only)
        if (args[0].equalsIgnoreCase("list")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c§lError! §7The list command can only be used in-game.");
                return true;
            }
            listAvailableEffects((Player)sender);
            return true;
        }

        // For console, player must be specified as the last argument
        boolean isConsole = !(sender instanceof Player);
        Player commandSender = isConsole ? null : (Player)sender;

        String effectName = args[0].toLowerCase();

        if (!effectName.matches("[a-zA-Z_]+")) {
            sender.sendMessage("§c§lInvalid Effect! §7Effect names can only contain letters and underscores.");
            return true;
        }

        PotionEffectType effectType = PotionEffectType.getByName(args[0].toUpperCase());
        if (effectType == null) {
            sender.sendMessage("§c§lInvalid Effect! §7Unknown potion effect: §e" + args[0]);
            if (!isConsole) sender.sendMessage("§7Use §e/potion list §7to view available effects.");
            return true;
        }

        // Check if effect is enabled in config (console bypasses this)
        if (!isConsole && !isEffectEnabled(effectName)) {
            sender.sendMessage("§c§lDisabled Effect! §7The §e" + formatEffectName(effectName) + " §7effect is not enabled.");
            sender.sendMessage("§7Use §e/potion list §7to view available effects.");
            return true;
        }

        // Check if player has permission for this effect (console bypasses this)
        if (!isConsole) {
            String requiredPermission = getEffectPermission(effectName);
            if (requiredPermission != null && !sender.hasPermission(requiredPermission)) {
                sender.sendMessage("§c§lPermission Denied! §7You lack permission for §e" + formatEffectName(effectName) + "§7.");
                sender.sendMessage("§7Use §e/potion list §7to view available effects.");
                return true;
            }
        }

        // Default values
        int duration = 30 * 20;
        int power = 0;

        // Track whether duration/power were explicitly provided
        boolean durationProvided = false;
        boolean powerProvided = false;

        Player targetPlayer = null;

        // Parse arguments based on length
        if (args.length == 2) {
            if (isInteger(args[1])) {
                duration = parseAndValidateDuration(args[1], sender, effectName, isConsole);
                if (duration == -2) return true; // Error check (changed from -1)
                durationProvided = true;
                targetPlayer = getPlayerOrSelf(sender, isConsole);
            } else {
                targetPlayer = parsePlayer(args[1], sender);
            }
        } else if (args.length == 3) {
            duration = parseAndValidateDuration(args[1], sender, effectName, isConsole);
            if (duration == -2) return true; // Error check (changed from -1)
            durationProvided = true;

            if (isInteger(args[2])) {
                power = parseAndValidatePower(args[2], sender, effectName, isConsole);
                if (power == -1) return true;
                powerProvided = true;
                targetPlayer = getPlayerOrSelf(sender, isConsole);
            } else {
                targetPlayer = parsePlayer(args[2], sender);
            }
        } else if (args.length == 4) {
            duration = parseAndValidateDuration(args[1], sender, effectName, isConsole);
            if (duration == -2) return true; // Error check (changed from -1)
            durationProvided = true;

            power = parseAndValidatePower(args[2], sender, effectName, isConsole);
            if (power == -1) return true;
            powerProvided = true;

            targetPlayer = parsePlayer(args[3], sender);
        } else if (args.length == 1) {
            targetPlayer = getPlayerOrSelf(sender, isConsole);
        } else {
            sender.sendMessage("§c§lUsage: §7/potion §e<effect> §7[duration] [power] [player]");
            return true;
        }

        if (targetPlayer == null) {
            return true;
        }

        // Set defaults to max tier values if not explicitly provided (console bypasses this)
        if (!isConsole) {
            if (!durationProvided) {
                int maxDuration = getMaxDuration(effectName, commandSender);
                if (maxDuration == -1) {
                    // Infinite duration allowed for this tier
                    duration = -1;
                } else {
                    duration = maxDuration * 20; // Convert seconds to ticks
                }
            }

            if (!powerProvided) {
                power = getMaxPower(effectName, commandSender);
            }
        }

        // Permission check (console bypasses this)
        if (!isConsole) {
            boolean isSelf = targetPlayer.equals(sender);
            if (isSelf) {
                if (!sender.hasPermission("potionmanager.self")) {
                    sender.sendMessage("§c§lPermission Denied! §7You cannot use potions on yourself.");
                    return true;
                }
            } else {
                if (!sender.hasPermission("potionmanager.other")) {
                    sender.sendMessage("§c§lPermission Denied! §7You cannot use potions on other players.");
                    return true;
                }
            }
        }

        // Cooldown check (only for applying effects, not removing, and not for console)
        if (!isConsole && !targetPlayer.hasPotionEffect(effectType)) {
            String tier = getTierForPlayer(commandSender, effectName);
            CooldownManager cooldownManager = Main.cooldownManager;

            if (cooldownManager != null && cooldownManager.hasCooldown(targetPlayer.getUniqueId(), effectName, tier)) {
                long remainingSeconds = cooldownManager.getRemainingCooldown(targetPlayer.getUniqueId(), effectName, tier);
                String formattedTime = cooldownManager.formatCooldownTime(remainingSeconds);

                String cooldownMessage = config.getString("Messages.Cooldown", "{Prefix} &cYou must wait &e{time} &cbefore using &a{Effect} &cagain!");
                cooldownMessage = cooldownMessage.replace("{Prefix}", config.getString("Messages.Prefix", ""))
                                                 .replace("{time}", formattedTime)
                                                 .replace("{Effect}", formatEffectName(effectName));
                cooldownMessage = ChatColor.translateAlternateColorCodes('&', cooldownMessage);

                sender.sendMessage(cooldownMessage);
                return true;
            }
        }

        PotionEffect effect = new PotionEffect(effectType, duration, power);
        boolean wasApplied = false;

        if (targetPlayer.hasPotionEffect(effect.getType())) {
            targetPlayer.removePotionEffect(effectType);
        }
        else if (!targetPlayer.hasPotionEffect(effect.getType())) {
            targetPlayer.addPotionEffect(effect);
            wasApplied = true;
        }

        // Set cooldown after successfully applying the effect (not for console)
        if (wasApplied && !isConsole) {
            String tier = getTierForPlayer(commandSender, effectName);
            int cooldownSeconds = getCooldownSeconds(effectName, tier);

            if (cooldownSeconds > 0 && Main.cooldownManager != null) {
                Main.cooldownManager.setCooldown(targetPlayer.getUniqueId(), effectName, tier, cooldownSeconds);
            }
        }

        // Display appropriate message based on duration
        String durationText;
        if (duration == -1) {
            durationText = "∞"; // Infinity symbol for infinite duration
        } else {
            durationText = (duration / 20) + "s";
        }

        if (targetPlayer == sender) {
            if (targetPlayer.hasPotionEffect(effect.getType())) {
                sender.sendMessage("§a§l✓ Applied §7" + formatEffectName(effectName) +
                        " §8(§fLevel " + (power + 1) + "§8, §f" + durationText + "§8) §7to yourself!");
            } else {
                sender.sendMessage("§c§l✗ Removed §7" + formatEffectName(effectName) + " §7from yourself!");
            }
        } else {
            if (targetPlayer.hasPotionEffect(effect.getType())) {
                sender.sendMessage("§a§l✓ Applied §7" + formatEffectName(effectName) +
                        " §8(§fLevel " + (power + 1) + "§8, §f" + durationText + "§8) §7to §e" + targetPlayer.getName() + "§7!");
                targetPlayer.sendMessage("§a§l✓ Received §7" + formatEffectName(effectName) + " §7effect!");
            } else {
                sender.sendMessage("§c§l✗ Removed §7" + formatEffectName(effectName) +
                        " §7from §e" + targetPlayer.getName() + "§7!");
                targetPlayer.sendMessage("§c§l✗ Removed §7" + formatEffectName(effectName) + " §7effect!");
            }
        }

        return true;
    }

    private boolean isEffectEnabled(String effectName) {
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
        if (effects == null) {
            return false;
        }

        ConfigurationSection effectSection = effects.getConfigurationSection(effectName);
        if (effectSection == null) {
            return false;
        }

        return effectSection.getBoolean("enabled", false);
    }

    private void listAvailableEffects(Player player) {
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");

        if (effects == null) {
            player.sendMessage("§c§lError! §7No effects are configured.");
            return;
        }

        player.sendMessage("§b§l╔══════════════════════════════╗");
        player.sendMessage("§b§l║ §f§lAvailable Potion Effects §b§l                              ║");
        player.sendMessage("§b§l╚══════════════════════════════╝");

        int availableCount = 0;

        for (String effectName : effects.getKeys(false)) {
            ConfigurationSection effectSection = effects.getConfigurationSection(effectName);

            if (effectSection == null) continue;

            boolean enabled = effectSection.getBoolean("enabled", false);
            String permission = effectSection.getString("permission");

            if (!enabled) {
                continue;
            }

            boolean hasPermission = (permission == null || permission.isEmpty() || player.hasPermission(permission));

            if (hasPermission) {
                availableCount++;

                // Determine tier
                String tier = getTierForPlayer(player, effectName);
                String tierColor;
                String tierDisplayName = getTierDisplayName(effectName, tier);
                String tierLabel;

                if (tier.equals("tier4")) {
                    tierColor = "§d"; // Light purple for tier 4 (unlimited)
                    tierLabel = "§d§l" + tierDisplayName.toUpperCase();
                } else if (tier.equals("tier3")) {
                    tierColor = "§6"; // Gold for tier 3
                    tierLabel = "§6§l" + tierDisplayName.toUpperCase();
                } else if (tier.equals("tier2")) {
                    tierColor = "§e"; // Yellow for tier 2
                    tierLabel = "§e§l" + tierDisplayName.toUpperCase();
                } else {
                    tierColor = "§f"; // White for tier 1
                    tierLabel = "§7" + tierDisplayName.toUpperCase();
                }
                player.sendMessage("  §a§l✓ " + tierColor + "§l" + formatEffectName(effectName) + " §8[" + tierLabel + "§8]");
            }
        }

        player.sendMessage("");
        player.sendMessage("§7You have access to §b§l" + availableCount + " §7effect(s).");
        player.sendMessage("§7Usage: §e/potion §b<effect> §7[duration] [power] [player]");
        player.sendMessage("§b§l══════════════════════════════");
    }

    private String formatEffectName(String effectName) {
        String[] parts = effectName.split("_");
        StringBuilder formatted = new StringBuilder();

        for (String part : parts) {
            if (formatted.length() > 0) {
                formatted.append(" ");
            }
            formatted.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
        }

        return formatted.toString();
    }

    private String getEffectPermission(String effectName) {
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
        if (effects == null) {
            return null;
        }

        ConfigurationSection effectSection = effects.getConfigurationSection(effectName);
        if (effectSection == null) {
            return null;
        }

        String permission = effectSection.getString("permission");
        return (permission != null && !permission.isEmpty()) ? permission : null;
    }

    private String getTierForPlayer(Player player, String effectName) {
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
        if (effects == null) {
            return "tier1";
        }

        ConfigurationSection effectSection = effects.getConfigurationSection(effectName);
        if (effectSection == null) {
            return "tier1";
        }

        ConfigurationSection tiers = effectSection.getConfigurationSection("tiers");
        if (tiers == null) {
            return "tier1";
        }

        // Check tier 4 first (highest - unlimited)
        String tier4Perm = tiers.getString("tier4.permission");
        if (tier4Perm != null && !tier4Perm.isEmpty() && player.hasPermission(tier4Perm)) {
            return "tier4";
        }

        // Check tier 3
        String tier3Perm = tiers.getString("tier3.permission");
        if (tier3Perm != null && !tier3Perm.isEmpty() && player.hasPermission(tier3Perm)) {
            return "tier3";
        }

        // Check tier 2
        String tier2Perm = tiers.getString("tier2.permission");
        if (tier2Perm != null && !tier2Perm.isEmpty() && player.hasPermission(tier2Perm)) {
            return "tier2";
        }

        // Default to tier 1
        return "tier1";
    }

    private int getMaxDuration(String effectName, Player player) {
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
        if (effects == null) {
            return 1000000; // Default max
        }

        ConfigurationSection effectSection = effects.getConfigurationSection(effectName);
        if (effectSection == null) {
            return 1000000; // Default max
        }

        String tier = getTierForPlayer(player, effectName);
        ConfigurationSection tiers = effectSection.getConfigurationSection("tiers");

        if (tiers == null) {
            return effectSection.getInt("max_duration", 1000000);
        }

        return tiers.getInt(tier + ".max_duration", 1000000);
    }

    private int getMaxPower(String effectName, Player player) {
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
        if (effects == null) {
            return 255; // Default max
        }

        ConfigurationSection effectSection = effects.getConfigurationSection(effectName);
        if (effectSection == null) {
            return 255; // Default max
        }

        String tier = getTierForPlayer(player, effectName);
        ConfigurationSection tiers = effectSection.getConfigurationSection("tiers");

        if (tiers == null) {
            return effectSection.getInt("max_power", 255);
        }

        return tiers.getInt(tier + ".max_power", 255);
    }

    private int getCooldownSeconds(String effectName, String tier) {
        ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
        if (effects == null) {
            return 0;
        }

        ConfigurationSection effectSection = effects.getConfigurationSection(effectName);
        if (effectSection == null) {
            return 0;
        }

        ConfigurationSection tiers = effectSection.getConfigurationSection("tiers");
        if (tiers == null) {
            return 0;
        }

        return tiers.getInt(tier + ".cooldown_seconds", 0);
    }

    private String getTierDisplayName(String effectName, String tier) {
        // Read from global tier_display_names section
        ConfigurationSection tierDisplayNames = plugin.getConfig().getConfigurationSection("tier_display_names");

        if (tierDisplayNames != null) {
            String displayName = tierDisplayNames.getString(tier);
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        }

        // Fallback to default names if not configured globally
        switch (tier) {
            case "tier1": return "Basic";
            case "tier2": return "VIP";
            case "tier3": return "Premium";
            case "tier4": return "Ultimate";
            default: return tier.toUpperCase();
        }
    }

    private boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int parseAndValidateDuration(String str, CommandSender sender, String effectName, boolean isConsole) {
        try {
            int seconds = Integer.parseInt(str);

            // Allow -1 for infinite duration, reject other non-positive values
            if (seconds == -1) {
                // User wants infinite duration
                if (isConsole) {
                    // Console can always use infinite
                    return -1;
                }

                // Check if player's tier allows infinite duration
                Player player = (Player) sender;
                int maxDuration = getMaxDuration(effectName, player);
                String tier = getTierForPlayer(player, effectName);

                if (maxDuration == -1) {
                    // Player's tier allows infinite duration
                    return -1;
                } else {
                    sender.sendMessage("§c§lInfinite Duration Denied! §7Your §e" + getTierDisplayName(effectName, tier).toUpperCase() +
                                     " §7tier doesn't allow infinite duration for §b" + formatEffectName(effectName) + "§7.");
                    return -2; // Error code (changed from -1 to avoid confusion)
                }
            }

            if (seconds <= 0) {
                sender.sendMessage("§c§lInvalid Duration! §7Duration must be greater than 0 seconds (or -1 for infinite).");
                return -2; // Error code
            }

            // Console bypasses tier restrictions
            if (isConsole) {
                return seconds * 20; // Convert to ticks
            }

            Player player = (Player) sender;
            int maxDuration = getMaxDuration(effectName, player);
            String tier = getTierForPlayer(player, effectName);

            // Handle infinite duration (-1 in config)
            if (maxDuration == -1) {
                // Infinite duration is allowed for this tier, any positive duration is ok
                return seconds * 20;
            }

            if (seconds > maxDuration) {
                sender.sendMessage("§c§lDuration Too Long! §7Your §e" + getTierDisplayName(effectName, tier).toUpperCase() + " §7tier limit for §b" +
                                 formatEffectName(effectName) + " §7is §e" + maxDuration + "s §7(or -1 for infinite)§7.");
                return -2; // Error code
            }
            return seconds * 20; // Convert to ticks
        } catch (NumberFormatException e) {
            sender.sendMessage("§c§lInvalid Duration! §7Must be a number, got: §e" + str);
            return -2; // Error code
        }
    }

    private int parseAndValidatePower(String str, CommandSender sender, String effectName, boolean isConsole) {
        try {
            int power = Integer.parseInt(str);
            if (power < 0) {
                sender.sendMessage("§c§lInvalid Power! §7Power must be 0 or greater.");
                return -1;
            }

            // Console bypasses tier restrictions
            if (isConsole) {
                return power;
            }

            Player player = (Player) sender;
            int maxPower = getMaxPower(effectName, player);
            String tier = getTierForPlayer(player, effectName);

            if (power > maxPower) {
                sender.sendMessage("§c§lPower Too High! §7Your §e" + getTierDisplayName(effectName, tier).toUpperCase() + " §7tier limit for §b" +
                                 formatEffectName(effectName) + " §7is §e" + maxPower + " §7(Level " + (maxPower + 1) + ")§7.");
                return -1;
            }
            return power;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c§lInvalid Power! §7Must be a number, got: §e" + str);
            return -1;
        }
    }

    private Player parsePlayer(String name, CommandSender sender) {
        Player target = Bukkit.getPlayer(name);
        if (target == null) {
            sender.sendMessage("§c§lPlayer Not Found! §7Cannot find player: §e" + name);
            return null;
        }
        return target;
    }

    private Player getPlayerOrSelf(CommandSender sender, boolean isConsole) {
        if (isConsole) {
            sender.sendMessage("§c§lError! §7Console must specify a target player.");
            sender.sendMessage("§7Usage: §e/potion <effect> [duration] [power] <player>");
            return null;
        } else {
            return (Player) sender;
        }
    }
}