package me.veritasluxmea.PotionsManager.Commands;

import me.veritasluxmea.PotionsManager.Main;
import me.veritasluxmea.PotionsManager.Utils.CooldownManager;
import me.veritasluxmea.PotionsManager.Utils.EffectConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        // Handle clear command (clear all potion effects)
        if (args[0].equalsIgnoreCase("clear")) {
            return handleClearCommand(sender, args);
        }

        // Create command context
        String effectName = args[0].toLowerCase();
        CommandContext context = new CommandContext(sender, effectName);

        if (!effectName.matches("[a-zA-Z_]+")) {
            Main.messages.sendMessage(sender, "potion.errors.invalid_effect_chars");
            return true;
        }

        PotionEffectType effectType = PotionEffectType.getByName(args[0].toUpperCase());
        if (effectType == null) {
            Main.messages.sendMessage(sender, "potion.errors.invalid_effect_unknown",
                Placeholder.unparsed("effect", args[0]));
            if (!context.isConsole()) Main.messages.sendMessage(sender, "potion.errors.use_list");
            return true;
        }

        // Check if effect is enabled in config (console bypasses this)
        if (!context.isConsole() && !Main.effectConfig.isEffectEnabled(effectName)) {
            Main.messages.sendMessage(sender, "potion.errors.disabled_effect",
                Placeholder.unparsed("effect", formatEffectName(effectName)));
            Main.messages.sendMessage(sender, "potion.errors.use_list");
            return true;
        }

        // Check if player has permission for this effect (console bypasses this)
        String requiredPermission = Main.effectConfig.getEffectPermission(effectName);
        if (requiredPermission != null && !context.hasPermission(requiredPermission)) {
            Main.messages.sendMessage(sender, "potion.errors.no_permission_effect",
                Placeholder.unparsed("effect", formatEffectName(effectName)));
            Main.messages.sendMessage(sender, "potion.errors.use_list");
            return true;
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
                duration = parseAndValidateDuration(args[1], context);
                if (duration == -2) return true; // Error check (changed from -1)
                durationProvided = true;
                targetPlayer = getPlayerOrSelf(context);
            } else {
                targetPlayer = parsePlayer(args[1], sender);
            }
        } else if (args.length == 3) {
            duration = parseAndValidateDuration(args[1], context);
            if (duration == -2) return true; // Error check (changed from -1)
            durationProvided = true;

            if (isInteger(args[2])) {
                power = parseAndValidatePower(args[2], context);
                if (power == -1) return true;
                powerProvided = true;
                targetPlayer = getPlayerOrSelf(context);
            } else {
                targetPlayer = parsePlayer(args[2], sender);
            }
        } else if (args.length == 4) {
            duration = parseAndValidateDuration(args[1], context);
            if (duration == -2) return true; // Error check (changed from -1)
            durationProvided = true;

            power = parseAndValidatePower(args[2], context);
            if (power == -1) return true;
            powerProvided = true;

            targetPlayer = parsePlayer(args[3], sender);
        } else if (args.length == 1) {
            targetPlayer = getPlayerOrSelf(context);
        } else {
            Main.messages.sendMessage(sender, "potion.usage");
            return true;
        }

        if (targetPlayer == null) {
            return true;
        }

        // Set defaults to max tier values if not explicitly provided (console bypasses this)
        if (!context.isConsole()) {
            if (!durationProvided) {
                int maxDuration = Main.effectConfig.getMaxDuration(effectName, context.getPlayer());
                if (maxDuration == -1) {
                    // Infinite duration allowed for this tier
                    duration = -1;
                } else {
                    duration = maxDuration * 20; // Convert seconds to ticks
                }
            }

            if (!powerProvided) {
                power = Main.effectConfig.getMaxPower(effectName, context.getPlayer());
            }
        }

        // Permission check (console bypasses this)
        if (!context.isConsole()) {
            boolean isSelf = targetPlayer.equals(sender);
            if (isSelf) {
                if (!context.hasPermission("potionmanager.self")) {
                    Main.messages.sendMessage(sender, "potion.errors.no_permission_self");
                    return true;
                }
            } else {
                if (!context.hasPermission("potionmanager.other")) {
                    Main.messages.sendMessage(sender, "potion.errors.no_permission_other");
                    return true;
                }
            }
        }

        // Check max active potions limit (only when adding effects, not removing, and not for console)
        if (!context.isConsole() && !targetPlayer.hasPotionEffect(effectType)) {
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
        if (!context.isConsole() && !targetPlayer.hasPotionEffect(effectType)) {
            // Cache tier in context for reuse
            String tier = Main.effectConfig.getTierForPlayer(context.getPlayer(), effectName);
            context.setTier(tier);
            CooldownManager cooldownManager = Main.cooldownManager;

            if (cooldownManager != null && cooldownManager.hasCooldown(targetPlayer.getUniqueId(), effectName, tier)) {
                long remainingSeconds = cooldownManager.getRemainingCooldown(targetPlayer.getUniqueId(), effectName, tier);
                String formattedTime = cooldownManager.formatCooldownTime(remainingSeconds);

                Main.messages.sendMessage(sender, "potion.cooldown.active",
                    Placeholder.parsed("prefix", Main.messages.getConfig().getString("prefix")),
                    Placeholder.unparsed("time", formattedTime),
                    Placeholder.unparsed("effect", formatEffectName(effectName)));
                return true;
            }
        }

        PotionEffect effect = new PotionEffect(effectType, duration, power);
        boolean wasApplied = false;

        if (targetPlayer.hasPotionEffect(effect.getType())) {
            Main.messages.sendMessage(sender, "potion.applied.exists");
            return true;
            //targetPlayer.removePotionEffect(effectType);
        }
        else if (!targetPlayer.hasPotionEffect(effect.getType())) {
            targetPlayer.addPotionEffect(effect);
            wasApplied = true;
        }

        // Set cooldown after successfully applying the effect (not for console)
        if (wasApplied && !context.isConsole()) {
            // Use cached tier from context (set during cooldown check)
            String tier = context.getTier();
            if (tier == null) {
                // Tier wasn't cached (e.g., effect wasn't on cooldown), calculate it now
                tier = Main.effectConfig.getTierForPlayer(context.getPlayer(), effectName);
            }
            int cooldownSeconds = Main.effectConfig.getCooldownSeconds(effectName, tier);

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


    private void listAvailableEffects(Player player) {
        Map<String, EffectConfig.EffectData> enabledEffects = Main.effectConfig.getEnabledEffects();

        if (enabledEffects.isEmpty()) {
            Main.messages.sendMessage(player, "potion.list.no_effects");
            return;
        }

        // Send header
        List<String> headerLines = Main.messages.getConfig().getStringList("potion.list.header");
        for (String line : headerLines) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line));
        }

        int availableCount = 0;

        for (Map.Entry<String, EffectConfig.EffectData> entry : enabledEffects.entrySet()) {
            String effectName = entry.getKey();
            EffectConfig.EffectData effectData = entry.getValue();

            String permission = effectData.permission;
            boolean hasPermission = (permission == null || permission.isEmpty() || player.hasPermission(permission));

            if (hasPermission) {
                availableCount++;

                // Determine tier
                String tier = Main.effectConfig.getTierForPlayer(player, effectName);
                String tierDisplayName = Main.effectConfig.getTierDisplayName(tier);
                String tierColorTag = Main.messages.getConfig().getString("potion.tier_colors." + tier, "<white>");

                Main.messages.sendMessage(player, "potion.list.effect_line",
                    Placeholder.parsed("tier_color", tierColorTag),
                    Placeholder.unparsed("effect_name", formatEffectName(effectName)),
                    Placeholder.unparsed("tier_label", tierDisplayName.toUpperCase()));
            }
        }

        // Send footer
        List<String> footerLines = Main.messages.getConfig().getStringList("potion.list.footer");
        for (String line : footerLines) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                line,
                Placeholder.unparsed("count", String.valueOf(availableCount))
            ));
        }
    }

    private boolean handleClearCommand(CommandSender sender, String[] args) {
        // Create context without effect name (not relevant for clear command)
        CommandContext context = new CommandContext(sender, null);
        Player targetPlayer = null;
        String effectName = null;
        boolean clearAll = false;

        // Parse arguments: /potion clear <effect|all> [playername]
        if (args.length < 2) {
            // Not enough arguments
            Main.messages.sendMessage(sender, "potion.clear.usage");
            return true;
        }

        // Get effect name or "all"
        effectName = args[1].toLowerCase();
        clearAll = effectName.equals("all");

        // Determine target player
        if (args.length == 2) {
            // No target specified - use self (if player) or error (if console)
            if (context.isConsole()) {
                Main.messages.sendMessage(sender, "potion.clear.console_needs_target");
                Main.messages.sendMessage(sender, "potion.clear.console_usage");
                return true;
            }
            targetPlayer = context.getPlayer();
        } else if (args.length == 3) {
            // Target specified
            targetPlayer = parsePlayer(args[2], sender);
            if (targetPlayer == null) {
                return true; // Error already sent by parsePlayer
            }
        } else {
            // Too many arguments
            Main.messages.sendMessage(sender, "potion.clear.usage");
            return true;
        }

        // Validate effect name if not clearing all
        PotionEffectType effectType = null;
        if (!clearAll) {
            effectType = PotionEffectType.getByName(effectName.toUpperCase());
            if (effectType == null) {
                Main.messages.sendMessage(sender, "potion.errors.invalid_effect_unknown",
                    Placeholder.unparsed("effect", effectName));
                if (!context.isConsole()) Main.messages.sendMessage(sender, "potion.errors.use_list");
                return true;
            }
        }

        // Permission checks (console bypasses these)
        if (!context.isConsole()) {
            boolean isSelf = targetPlayer.equals(sender);
            if (isSelf) {
                if (!context.hasPermission("potionmanager.self.clear")) {
                    Main.messages.sendMessage(sender, "potion.errors.no_permission_self_clear");
                    return true;
                }
            } else {
                if (!context.hasPermission("potionmanager.other.clear")) {
                    Main.messages.sendMessage(sender, "potion.errors.no_permission_other_clear");
                    return true;
                }
            }
        }

        if (clearAll) {
            // Clear all potion effects
            int effectCount = targetPlayer.getActivePotionEffects().size();

            if (effectCount == 0) {
                // No effects to clear
                if (targetPlayer.equals(sender)) {
                    Main.messages.sendMessage(sender, "potion.clear.no_effects_self");
                } else {
                    Main.messages.sendMessage(sender, "potion.clear.no_effects_other",
                        Placeholder.unparsed("target", targetPlayer.getName()));
                }
                return true;
            }

            // Clear all potion effects
            for (PotionEffect effect : targetPlayer.getActivePotionEffects()) {
                targetPlayer.removePotionEffect(effect.getType());
            }

            // Send success messages
            if (targetPlayer.equals(sender)) {
                Main.messages.sendMessage(sender, "potion.clear.success_self",
                    Placeholder.unparsed("count", String.valueOf(effectCount)));
            } else {
                Main.messages.sendMessage(sender, "potion.clear.success_other_sender",
                    Placeholder.unparsed("count", String.valueOf(effectCount)),
                    Placeholder.unparsed("target", targetPlayer.getName()));
                Main.messages.sendMessage(targetPlayer, "potion.clear.success_other_target");
            }
        } else {
            // Clear specific effect
            if (!targetPlayer.hasPotionEffect(effectType)) {
                // Player doesn't have this effect
                if (targetPlayer.equals(sender)) {
                    Main.messages.sendMessage(sender, "potion.clear.no_effect_self",
                        Placeholder.unparsed("effect", formatEffectName(effectName)));
                } else {
                    Main.messages.sendMessage(sender, "potion.clear.no_effect_other",
                        Placeholder.unparsed("effect", formatEffectName(effectName)),
                        Placeholder.unparsed("target", targetPlayer.getName()));
                }
                return true;
            }

            // Remove the specific effect
            targetPlayer.removePotionEffect(effectType);

            // Send success messages
            if (targetPlayer.equals(sender)) {
                Main.messages.sendMessage(sender, "potion.clear.success_effect_self",
                    Placeholder.unparsed("effect", formatEffectName(effectName)));
            } else {
                Main.messages.sendMessage(sender, "potion.clear.success_effect_other_sender",
                    Placeholder.unparsed("effect", formatEffectName(effectName)),
                    Placeholder.unparsed("target", targetPlayer.getName()));
                Main.messages.sendMessage(targetPlayer, "potion.clear.success_effect_other_target",
                    Placeholder.unparsed("effect", formatEffectName(effectName)));
            }
        }

        return true;
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


    private boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int parseAndValidateDuration(String str, CommandContext context) {
        try {
            int seconds = Integer.parseInt(str);

            // Allow -1 for infinite duration, reject other non-positive values
            if (seconds == -1) {
                // User wants infinite duration
                if (context.isConsole()) {
                    // Console can always use infinite
                    return -1;
                }

                // Check if player's tier allows infinite duration
                int maxDuration = Main.effectConfig.getMaxDuration(context.getEffectName(), context.getPlayer());
                String tier = Main.effectConfig.getTierForPlayer(context.getPlayer(), context.getEffectName());

                if (maxDuration == -1) {
                    // Player's tier allows infinite duration
                    return -1;
                } else {
                    Main.messages.sendMessage(context.getSender(), "potion.duration.infinite_denied",
                        Placeholder.unparsed("tier", Main.effectConfig.getTierDisplayName(tier).toUpperCase()),
                        Placeholder.unparsed("effect", formatEffectName(context.getEffectName())));
                    return -2; // Error code (changed from -1 to avoid confusion)
                }
            }

            if (seconds <= 0) {
                Main.messages.sendMessage(context.getSender(), "potion.duration.invalid_zero");
                return -2; // Error code
            }

            // Console bypasses tier restrictions
            if (context.isConsole()) {
                return seconds * 20; // Convert to ticks
            }

            int maxDuration = Main.effectConfig.getMaxDuration(context.getEffectName(), context.getPlayer());
            String tier = Main.effectConfig.getTierForPlayer(context.getPlayer(), context.getEffectName());

            // Handle infinite duration (-1 in config)
            if (maxDuration == -1) {
                // Infinite duration is allowed for this tier, any positive duration is ok
                return seconds * 20;
            }

            if (seconds > maxDuration) {
                Main.messages.sendMessage(context.getSender(), "potion.duration.too_long",
                    Placeholder.unparsed("tier", Main.effectConfig.getTierDisplayName(tier).toUpperCase()),
                    Placeholder.unparsed("effect", formatEffectName(context.getEffectName())),
                    Placeholder.unparsed("max", String.valueOf(maxDuration)));
                return -2; // Error code
            }
            return seconds * 20; // Convert to ticks
        } catch (NumberFormatException e) {
            Main.messages.sendMessage(context.getSender(), "potion.duration.invalid_number",
                Placeholder.unparsed("value", str));
            return -2; // Error code
        }
    }

    private int parseAndValidatePower(String str, CommandContext context) {
        try {
            int power = Integer.parseInt(str);
            if (power < 0) {
                Main.messages.sendMessage(context.getSender(), "potion.power.invalid_negative");
                return -1;
            }

            // Console bypasses tier restrictions
            if (context.isConsole()) {
                return power;
            }

            int maxPower = Main.effectConfig.getMaxPower(context.getEffectName(), context.getPlayer());
            String tier = Main.effectConfig.getTierForPlayer(context.getPlayer(), context.getEffectName());

            if (power > maxPower) {
                Main.messages.sendMessage(context.getSender(), "potion.power.too_high",
                    Placeholder.unparsed("tier", Main.effectConfig.getTierDisplayName(tier).toUpperCase()),
                    Placeholder.unparsed("effect", formatEffectName(context.getEffectName())),
                    Placeholder.unparsed("max", String.valueOf(maxPower)),
                    Placeholder.unparsed("level", String.valueOf(maxPower + 1)));
                return -1;
            }
            return power;
        } catch (NumberFormatException e) {
            Main.messages.sendMessage(context.getSender(), "potion.power.invalid_number",
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

    private Player getPlayerOrSelf(CommandContext context) {
        if (context.isConsole()) {
            Main.messages.sendMessage(context.getSender(), "potion.errors.console_needs_target");
            Main.messages.sendMessage(context.getSender(), "potion.errors.console_usage");
            return null;
        } else {
            return context.getPlayer();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument: suggest "list", "clear" and all available effect names
            completions.add("list");
            completions.add("clear");

            Map<String, EffectConfig.EffectData> enabledEffects = Main.effectConfig.getEnabledEffects();
            for (Map.Entry<String, EffectConfig.EffectData> entry : enabledEffects.entrySet()) {
                String effectName = entry.getKey();
                EffectConfig.EffectData effectData = entry.getValue();

                // For players, check permissions
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    String permission = effectData.permission;
                    if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
                        continue;
                    }
                }

                completions.add(effectName);
            }

            // Filter based on what the player has typed so far
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Second argument depends on first argument
            if (args[0].equalsIgnoreCase("clear")) {
                // For clear command, second argument is effect name or "all"
                completions.add("all");

                // Add all available effect names
                Map<String, EffectConfig.EffectData> enabledEffects = Main.effectConfig.getEnabledEffects();
                for (String effectName : enabledEffects.keySet()) {
                    completions.add(effectName);
                }
            } else {
                // For effect commands, could be duration or player name
                completions.add("<duration>");

                // Add online player names
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            // Third argument depends on first argument
            if (args[0].equalsIgnoreCase("clear")) {
                // For clear command, third argument is player name
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else {
                // For effect commands, could be power or player name
                completions.add("<power>");

                // Add online player names
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
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