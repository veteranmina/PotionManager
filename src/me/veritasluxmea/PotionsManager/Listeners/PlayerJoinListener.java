package me.veritasluxmea.PotionsManager.Listeners;

import me.veritasluxmea.PotionsManager.Main;
import me.veritasluxmea.PotionsManager.Methods;
import me.veritasluxmea.PotionsManager.Utils.UpdateChecker;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerJoinListener
  implements Listener {
  private Main plugin;

  public PlayerJoinListener(Main plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    Player player = e.getPlayer();

    String version = Methods.getPlugin().getDescription().getVersion();

    if (player.getName().equals("veritasluxmea")) {
      player.sendMessage(Methods.color(String.valueOf(Methods.getPrefix()) + ChatColor.GRAY + " This server is using the plugin" +
            ChatColor.RED + " Potion Manager " + ChatColor.GRAY + "Version " + ChatColor.RED +
            Methods.getPlugin().getDescription().getVersion() + ChatColor.GRAY + "."));
    }

    if (Main.settings.getConfig().getBoolean("Update_Checker") &&
      player.isOp()) {
      UpdateChecker updater = new UpdateChecker((JavaPlugin)this.plugin, "DaYV2289");
      try {
        if (updater.checkForUpdates()) {
          player.sendMessage("");
          player.sendMessage(Methods.color(String.valueOf(Methods.getPrefix()) + " &7An update is available for &cPotion Manager&7!"));
          player.sendMessage(Methods.color("&7Your server is running &cv" + version + "&7 and the newest version is &cv" + UpdateChecker.getLatestVersion() + "&7!"));
          player.sendMessage(Methods.color("&7Download: &c" + UpdateChecker.getResourceURL()));
          player.sendMessage("");
        }
      } catch (Exception exception) {}
    }
  }
}