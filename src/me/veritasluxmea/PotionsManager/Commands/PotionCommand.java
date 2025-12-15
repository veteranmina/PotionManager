package me.veritasluxmea.PotionsManager.Commands;

import me.veritasluxmea.PotionsManager.Main;
import me.veritasluxmea.PotionsManager.MessagesManager;
import me.veritasluxmea.PotionsManager.Utils.CooldownManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PotionCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public PotionCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        FileConfiguration config = Main.settings.getConfig();

        if (args.length == 0) {
            Main.messages.sendMessage(sender, "potion.usage");
            return true;
        }

        // Handle list command (player-only)
        if (args[0].equalsIgnoreCase("list")) {
            if (!(sender instanceof Player)) {
                Main.messages.sendMessage(sender, "potion.list.console_only");
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
            Main.messages.sendMessage(sender, "potion.errors.invalid_effect_chars");
            return true;
        }

        PotionEffectType effectType = PotionEffectType.getByName(args[0].toUpperCase());
        if (effectType == null) {
            Main.messages.sendMessage(sender, "potion.errors.invalid_effect_unknown",
                Placeholder.unparsed("effect", args[0]));
            if (!isConsole) Main.messages.sendMessage(sender, "potion.errors.use_list");
            return true;
        }

        // Check if effect is enabled in config (console bypasses this)
        if (!isConsole && !isEffectEnabled(effectName)) {
            Main.messages.sendMessage(sender, "potion.errors.disabled_effect",
                Placeholder.unparsed("effect", formatEffectName(effectName)));
            Main.messages.sendMessage(sender, "potion.errors.use_list");
            return true;
        }

        // Check if player has permission for this effect (console bypasses this)
        if (!isConsole) {
            String requiredPermission = getEffectPermission(effectName);
            if (requiredPermission != null && !sender.hasPermission(requiredPermission)) {
                Main.messages.sendMessage(sender, "potion.errors.no_permission_effect",
                    Placeholder.unparsed("effect", formatEffectName(effectName)));
                Main.messages.sendMessage(sender, "potion.errors.use_list");
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
            Main.messages.sendMessage(sender, "potion.usage");
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
                    Main.messages.sendMessage(sender, "potion.errors.no_permission_self");
                    return true;
                }
            } else {
                if (!sender.hasPermission("potionmanager.other")) {
                    Main.messages.sendMessage(sender, "potion.errors.no_permission_other");
                    return true;
                }
            }
        }

        // Check max active potions limit (only when adding effects, not removing, and not for console)
        if (!isConsole && !targetPlayer.hasPotionEffect(effectType)) {
            int maxActivePotions = config.getInt("MaxActivePotions", 1);
            if (maxActivePotions > 0) { // 0 means bypass this check
                int activePotions = targetPlayer.getActivePotionEffects().size();
                if (activePotions >= maxActivePotions) {
                    Main.messages.sendMessage(sender, "potion.max_active.reached",
                        Placeholder.unparsed("max", String.valueOf(maxActivePotions)));
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

                Main.messages.sendMessage(sender, "potion.cooldown.active",
                    Placeholder.unparsed("prefix", Main.messages.getConfig().getString("prefix")),
                    Placeholder.unparsed("time", formattedTime),
                    Placeholder.unparsed("effect", formatEffectName(effectName)));
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
                Main.messages.sendMessage(sender, "potion.applied.self",
                    Placeholder.unparsed("effect", formatEffectName(effectName)),
                    Placeholder.unparsed("level", String.valueOf(power + 1)),
                    Placeholder.unparsed("duration", durationText));
            } else {
                Main.messages.sendMessage(sender, "potion.removed.self",
                    Placeholder.unparsed("effect", formatEffectName(effectName)));
            }
        } else {
            if (targetPlayer.hasPotionEffect(effect.getType())) {
                Main.messages.sendMessage(sender, "potion.applied.other_sender",
                    Placeholder.unparsed("effect", formatEffectName(effectName)),
                    Placeholder.unparsed("level", String.valueOf(power + 1)),
                    Placeholder.unparsed("duration", durationText),
                    Placeholder.unparsed("target", targetPlayer.getName()));
                Main.messages.sendMessage(targetPlayer, "potion.applied.other_target",
                    Placeholder.unparsed("effect", formatEffectName(effectName)));
            } else {
                Main.messages.sendMessage(sender, "potion.removed.other_sender",
                    Placeholder.unparsed("effect", formatEffectName(effectName)),
                    Placeholder.unparsed("target", targetPlayer.getName()));
                Main.messages.sendMessage(targetPlayer, "potion.removed.other_target",
                    Placeholder.unparsed("effect", formatEffectName(effectName)));
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
            Main.messages.sendMessage(player, "potion.list.no_effects");
            return;
        }

        // Send header
        List<String> headerLines = Main.messages.getConfig().getStringList("potion.list.header");
        for (String line : headerLines) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line));
        }

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
                String tierDisplayName = getTierDisplayName(effectName, tier);
                String tierColorTag = Main.messages.getConfig().getString("potion.tier_colors." + tier, "<white>");

                Main.messages.sendMessage(player, "potion.list.effect_line",
                    Placeholder.unparsed("tier_color", tierColorTag),
                    Placeholder.unparsed("effect_name", formatEffectName(effectName)),
                    Placeholder.unparsed("tier_label", tierDisplayName.toUpperCase()));
            }
        }

        // Send footer
        List<String> footerLines = Main.messages.getConfig().getStringList("potion.list.footer");
        for (String line : footerLines) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                line.replace("{count}", String.valueOf(availableCount))
            ));
        }
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
                    Main.messages.sendMessage(sender, "potion.duration.infinite_denied",
                        Placeholder.unparsed("tier", getTierDisplayName(effectName, tier).toUpperCase()),
                        Placeholder.unparsed("effect", formatEffectName(effectName)));
                    return -2; // Error code (changed from -1 to avoid confusion)
                }
            }

            if (seconds <= 0) {
                Main.messages.sendMessage(sender, "potion.duration.invalid_zero");
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
                Main.messages.sendMessage(sender, "potion.duration.too_long",
                    Placeholder.unparsed("tier", getTierDisplayName(effectName, tier).toUpperCase()),
                    Placeholder.unparsed("effect", formatEffectName(effectName)),
                    Placeholder.unparsed("max", String.valueOf(maxDuration)));
                return -2; // Error code
            }
            return seconds * 20; // Convert to ticks
        } catch (NumberFormatException e) {
            Main.messages.sendMessage(sender, "potion.duration.invalid_number",
                Placeholder.unparsed("value", str));
            return -2; // Error code
        }
    }

    private int parseAndValidatePower(String str, CommandSender sender, String effectName, boolean isConsole) {
        try {
            int power = Integer.parseInt(str);
            if (power < 0) {
                Main.messages.sendMessage(sender, "potion.power.invalid_negative");
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
                Main.messages.sendMessage(sender, "potion.power.too_high",
                    Placeholder.unparsed("tier", getTierDisplayName(effectName, tier).toUpperCase()),
                    Placeholder.unparsed("effect", formatEffectName(effectName)),
                    Placeholder.unparsed("max", String.valueOf(maxPower)),
                    Placeholder.unparsed("level", String.valueOf(maxPower + 1)));
                return -1;
            }
            return power;
        } catch (NumberFormatException e) {
            Main.messages.sendMessage(sender, "potion.power.invalid_number",
                Placeholder.unparsed("value", str));
            return -1;
        }
    }

    private Player parsePlayer(String name, CommandSender sender) {
        Player target = Bukkit.getPlayer(name);
        if (target == null) {
            Main.messages.sendMessage(sender, "potion.errors.player_not_found",
                Placeholder.unparsed("player", name));
            return null;
        }
        return target;
    }

    private Player getPlayerOrSelf(CommandSender sender, boolean isConsole) {
        if (isConsole) {
            Main.messages.sendMessage(sender, "potion.errors.console_needs_target");
            Main.messages.sendMessage(sender, "potion.errors.console_usage");
            return null;
        } else {
            return (Player) sender;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument: suggest "list" and all available effect names
            completions.add("list");

            ConfigurationSection effects = plugin.getConfig().getConfigurationSection("effects");
            if (effects != null) {
                for (String effectName : effects.getKeys(false)) {
                    ConfigurationSection effectSection = effects.getConfigurationSection(effectName);
                    if (effectSection == null) continue;

                    boolean enabled = effectSection.getBoolean("enabled", false);
                    if (!enabled) continue;

                    // For players, check permissions
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        String permission = effectSection.getString("permission");
                        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
                            continue;
                        }
                    }

                    completions.add(effectName);
                }
            }

            // Filter based on what the player has typed so far
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Second argument: could be duration or player name
            completions.add("<duration>");

            // Add online player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            // Third argument: could be power or player name
            completions.add("<power>");

            // Add online player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4) {
            // Fourth argument: player name only
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}