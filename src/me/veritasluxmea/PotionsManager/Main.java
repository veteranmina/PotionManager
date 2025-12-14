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
  public static CooldownManager cooldownManager;
  public static DataManager dataManager;

  public void onEnable() {
    settings.setup((Plugin)this);

    // Initialize cooldown system
    cooldownManager = new CooldownManager();
    dataManager = new DataManager(this);

    // Load cooldowns from disk
    dataManager.loadCooldowns(cooldownManager);

    Bukkit.getConsoleSender().sendMessage("=========================");
    Bukkit.getConsoleSender().sendMessage("Potions Manager");
    Bukkit.getConsoleSender().sendMessage("Author: veritasluxmea");
    Bukkit.getConsoleSender().sendMessage("Version " + getDescription().getVersion());
    Bukkit.getConsoleSender().sendMessage("=========================");
    registerCommands();
    registerListeners();
    updateChecker();
  }

  public void onDisable() {
    // Save cooldowns to disk
    if (cooldownManager != null && dataManager != null) {
      dataManager.saveCooldowns(cooldownManager);
    }

    Bukkit.getConsoleSender().sendMessage("=========================");
    Bukkit.getConsoleSender().sendMessage("Potions Manager Disabled");
    Bukkit.getConsoleSender().sendMessage("Cooldowns saved to disk");
    Bukkit.getConsoleSender().sendMessage("=========================");
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
          getLogger().info("Potion Manager has a new update available! New version: " + UpdateChecker.getLatestVersion());
          getLogger().info("Download: " + UpdateChecker.getResourceURL());
        } else {
          getServer().getConsoleSender().sendMessage("Plugin is up to date - v" + getDescription().getVersion());
        }
      } catch (Exception e) {
          if (Main.settings.getConfig().getBoolean("Debug")) {
              getLogger().info("Could not check for updates! Stacktrace:");
              e.printStackTrace();
          } else if (!Main.settings.getConfig().getBoolean("Debug")) {
              getLogger().info("Error checking for updates!");
              getLogger().info("Enable debugging for stacktrace");
          }
      }
    }
  }
}