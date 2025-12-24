package me.veritasluxmea.PotionsManager.Commands;

import me.veritasluxmea.PotionsManager.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.List;

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
        List<String> headerLines = Main.messages.getConfig().getStringList("commands.potionmanager.header");
        for (String line : headerLines) {
          sender.sendMessage(MiniMessage.miniMessage().deserialize(
              line,
              Placeholder.unparsed("version", this.main.getDescription().getVersion())
          ));
        }
        return true;
      }

      if (args[0].equalsIgnoreCase("Help")) {
        // Send help header
        List<String> helpHeader = Main.messages.getConfig().getStringList("commands.potionmanager.help.header");
        for (String line : helpHeader) {
          sender.sendMessage(MiniMessage.miniMessage().deserialize(line));
        }

        // Send regular commands
        List<String> commands = Main.messages.getConfig().getStringList("commands.potionmanager.help.commands");
        for (String line : commands) {
          sender.sendMessage(MiniMessage.miniMessage().deserialize(line));
        }

        // Send admin commands if player has permission
        if (sender.hasPermission("potionmanager.*")) {
          List<String> adminHeader = Main.messages.getConfig().getStringList("commands.potionmanager.help.admin_header");
          for (String line : adminHeader) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(line));
          }

          List<String> adminCommands = Main.messages.getConfig().getStringList("commands.potionmanager.help.admin_commands");
          for (String line : adminCommands) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(line));
          }
        }

        // Send footer
        List<String> footer = Main.messages.getConfig().getStringList("commands.potionmanager.help.footer");
        for (String line : footer) {
          sender.sendMessage(MiniMessage.miniMessage().deserialize(line));
        }

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

                        // Also reload messages.yml
                        Main.messages.reloadConfig();
                        Main.messages.setup((Plugin)this.main);

                        // Reload effect config cache
                        if (Main.effectConfig != null) {
                            Main.effectConfig.reload();
                        }

                        // Check configuration file versions after reload
                        this.main.checkConfigVersions();

                        Main.messages.sendMessage(sender, "commands.potionmanager.reload.success",
                            Placeholder.parsed("prefix", Main.messages.getConfig().getString("prefix")));
                        Main.messages.sendMessage(sender, "commands.potionmanager.reload.effect_count",
                            Placeholder.unparsed("count", String.valueOf(effectCount)));
                        this.main.getLogger().info(sender.getName() + " reloaded the configuration (" + effectCount + " effects)");

                    } catch (Exception e) {
                        Main.messages.sendMessage(sender, "commands.potionmanager.reload.failure");
                        if (Main.settings.getConfig().getBoolean("Debug")) {
                            Main.messages.sendMessage(sender, "commands.potionmanager.reload.check_console");
                            e.printStackTrace();
                        }
                        this.main.getLogger().severe("Failed to reload config: " + e.getMessage());
                        if (!Main.settings.getConfig().getBoolean("Debug")) {
                            this.main.getLogger().severe("Enable debugging for stacktrace");
                        }
                        return true;
                    }
                } else {
                    Main.messages.sendMessage(sender, "commands.potionmanager.reload.usage");
                    return true;
                }
            } else {
                Main.messages.sendMessage(sender, "generic.no_permission",
                    Placeholder.parsed("prefix", Main.messages.getConfig().getString("prefix")));
                return true;
            }
        }
        }
    return true;
  }
}