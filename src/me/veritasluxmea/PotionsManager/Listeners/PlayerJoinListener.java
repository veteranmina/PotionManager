package me.veritasluxmea.PotionsManager.Listeners;

import me.veritasluxmea.PotionsManager.Main;
import me.veritasluxmea.PotionsManager.Utils.UpdateChecker;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PlayerJoinListener
  implements Listener {
  private Main plugin;

  public PlayerJoinListener(Main plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    Player player = e.getPlayer();

    String version = this.plugin.getDescription().getVersion();

    if (Main.settings.getConfig().getBoolean("Update_Checker") &&
      player.isOp()) {
      UpdateChecker updater = new UpdateChecker((JavaPlugin)this.plugin, "DaYV2289");
      try {
        if (updater.checkForUpdates()) {
          List<String> updateLines = Main.messages.getConfig().getStringList("player.join.update_available");
          for (String line : updateLines) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                line,
                Placeholder.parsed("prefix", Main.messages.getConfig().getString("prefix")),
                Placeholder.unparsed("current", version),
                Placeholder.unparsed("latest", UpdateChecker.getLatestVersion()),
                Placeholder.unparsed("url", UpdateChecker.getResourceURL())
            ));
          }
        }
      } catch (Exception exception) {}
    }
  }
}