package me.veritasluxmea.PotionsManager.Commands;

import me.veritasluxmea.PotionsManager.Main;
import me.veritasluxmea.PotionsManager.Methods;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class Commands
  implements CommandExecutor {
  public Main main;

  public Commands(Main main) {
    this.main = main;
  }


  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    FileConfiguration config = Main.settings.getConfig();
    if (cmd.getName().equalsIgnoreCase("PotionManager")) {
      if (args.length == 0) {
        sender.sendMessage(ChatColor.DARK_GRAY + "╔════════════════════════════════╗");
        sender.sendMessage(ChatColor.AQUA + "  " + ChatColor.BOLD + "Potion Manager " + ChatColor.RESET +
            ChatColor.GRAY + "v" + this.main.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "  By " + ChatColor.AQUA + ChatColor.BOLD + "veritasluxmea");
        sender.sendMessage(ChatColor.DARK_GRAY + "╚════════════════════════════════╝");
        sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/potionmanager help" + ChatColor.GRAY + " for commands");
        return true;
      }

      if (args[0].equalsIgnoreCase("Help")) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "╔═══════════════════════════════════╗");
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "║ " + ChatColor.WHITE + "" + ChatColor.BOLD + "Potion Manager Commands" + ChatColor.AQUA + "" + ChatColor.BOLD + "       ║");
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "╚═══════════════════════════════════╝");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "  /potion " + ChatColor.WHITE + "<effect> " + ChatColor.GRAY + "[duration] [power] [player]");
        sender.sendMessage(ChatColor.DARK_GRAY + "  ├─ " + ChatColor.GRAY + "Apply or remove potion effects");
        sender.sendMessage(ChatColor.DARK_GRAY + "  └─ " + ChatColor.GRAY + "Example: " + ChatColor.YELLOW + "/potion speed 60 2");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "  /potion list");
        sender.sendMessage(ChatColor.DARK_GRAY + "  └─ " + ChatColor.GRAY + "View all available effects and your tier");
        if (sender.hasPermission("potionmanager.*")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Admin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "  /potionmanager reload");
            sender.sendMessage(ChatColor.DARK_GRAY + "  └─ " + ChatColor.GRAY + "Reload the configuration");
        }
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "═══════════════════════════════════");

        return true;
      }

        if (args[0].equalsIgnoreCase("Reload")) {
            if (sender.hasPermission("PotionManager.reload")) {
                if (args.length == 1) {
                    try {
                        Main.settings.reloadConfig();
                        Main.settings.setup((Plugin)this.main);

                        // Validate the config loaded properly
                        FileConfiguration reloadedConfig = Main.settings.getConfig();
                        if (reloadedConfig == null || reloadedConfig.getKeys(false).isEmpty()) {
                            throw new Exception("Config file is empty or failed to load - check for YAML syntax errors");
                        }

                        // Additional validation - check for required keys
                        if (!reloadedConfig.contains("Messages.Prefix")) {
                            throw new Exception("Config is missing required sections - check for YAML syntax errors");
                        }

                        // Count loaded effects for feedback
                        int effectCount = 0;
                        if (reloadedConfig.contains("effects")) {
                            effectCount = reloadedConfig.getConfigurationSection("effects").getKeys(false).size();
                        }

                        sender.sendMessage(Methods.color(reloadedConfig.getString("Messages.Reload")
                                .replace("{Prefix}", reloadedConfig.getString("Messages.Prefix"))));
                        sender.sendMessage(ChatColor.GRAY + "Loaded " + ChatColor.AQUA + "" + ChatColor.BOLD + effectCount + ChatColor.RESET + ChatColor.GRAY + " effects from config");
                        this.main.getLogger().info(sender.getName() + " reloaded the configuration (" + effectCount + " effects)");

                    } catch (Exception e) {
                        sender.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Error! " + ChatColor.RESET + ChatColor.GRAY + "Failed to reload configuration!");
                        if (Main.settings.getConfig().getBoolean("Debug")) {
                            sender.sendMessage(ChatColor.GRAY + "Check console for details");
                            e.printStackTrace();
                        }
                        this.main.getLogger().severe("Failed to reload config: " + e.getMessage());
                        if (!Main.settings.getConfig().getBoolean("Debug")) {
                            this.main.getLogger().severe("Enable debugging for stacktrace");
                        }
                        return true;
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Usage: " + ChatColor.RESET + ChatColor.GRAY + "/potionmanager reload");
                    return true;
                }
            } else {
                sender.sendMessage(Methods.color(Methods.noPerm()));
                return true;
            }
        }
        }
    return true;
  }
}