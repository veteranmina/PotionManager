package me.veritasluxmea.PotionsManager;

import me.veritasluxmea.PotionsManager.Commands.Commands;
import me.veritasluxmea.PotionsManager.Commands.PotionCommand;
import me.veritasluxmea.PotionsManager.Listeners.PlayerJoinListener;
import me.veritasluxmea.PotionsManager.Utils.CooldownManager;
import me.veritasluxmea.PotionsManager.Utils.DataManager;
import me.veritasluxmea.PotionsManager.Utils.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
  public static SettingsManager settings = SettingsManager.getInstance();
  public static MessagesManager messages = MessagesManager.getInstance();
  public static CooldownManager cooldownManager;
  public static DataManager dataManager;

  public void onEnable() {
    settings.setup((Plugin)this);
    messages.setup((Plugin)this);

    // Initialize cooldown system
    cooldownManager = new CooldownManager();
    dataManager = new DataManager(this);

    // Load cooldowns from disk
    dataManager.loadCooldowns(cooldownManager);

    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.startup.separator"));
    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.startup.title"));
    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.startup.author"));
    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.startup.version",
        MessagesManager.placeholder("version", getDescription().getVersion())));
    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.startup.separator"));
    registerCommands();
    registerListeners();
    updateChecker();
  }

  public void onDisable() {
    // Save cooldowns to disk
    if (cooldownManager != null && dataManager != null) {
      dataManager.saveCooldowns(cooldownManager);
    }

    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.shutdown.separator"));
    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.shutdown.title"));
    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.shutdown.cooldowns_saved"));
    Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.shutdown.separator"));
  }

  public void registerCommands() {
    getCommand("PotionManager").setExecutor((CommandExecutor)new Commands(this));

    // Register PotionCommand as both executor and tab completer
    PotionCommand potionCommand = new PotionCommand(this);
    getCommand("Potion").setExecutor(potionCommand);
    getCommand("Potion").setTabCompleter(potionCommand);
  }


  public void registerListeners() {
    PluginManager pm = getServer().getPluginManager();

    pm.registerEvents((Listener)new PlayerJoinListener(this), (Plugin)this);
  }

  public void updateChecker() {
    if (settings.getConfig().getBoolean("Update_Checker")) {
      UpdateChecker updater = new UpdateChecker(this, "DaYV2289");
      try {
        if (updater.checkForUpdates()) {
          Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.update_checker.available",
              MessagesManager.placeholder("new_version", UpdateChecker.getLatestVersion())));
          Bukkit.getConsoleSender().sendMessage(messages.getMessage("system.update_checker.download",
              MessagesManager.placeholder("url", UpdateChecker.getResourceURL())));
        } else {
          getServer().getConsoleSender().sendMessage(messages.getMessage("system.update_checker.checking",
              MessagesManager.placeholder("version", getDescription().getVersion())));
        }
      } catch (Exception e) {
          if (Main.settings.getConfig().getBoolean("Debug")) {
              getLogger().info(messages.getConfig().getString("system.update_checker.error_debug"));
              e.printStackTrace();
          } else if (!Main.settings.getConfig().getBoolean("Debug")) {
              getLogger().info(messages.getConfig().getString("system.update_checker.error_no_debug"));
              getLogger().info(messages.getConfig().getString("system.update_checker.error_enable_debug"));
          }
      }
    }
  }
}