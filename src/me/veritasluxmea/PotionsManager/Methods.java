package me.veritasluxmea.PotionsManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

public class Methods
{
  public static String color(String msg) {
    return ChatColor.translateAlternateColorCodes('&', msg);
  }

  public static String getPrefix() {
    return color(Main.settings.getConfig().getString("Messages.Prefix"));
  }

  public static String noPerm() {
    return Main.settings.getConfig().getString("Messages.No_Permission").replace("{Prefix}",
        Main.settings.getConfig().getString("Messages.Prefix"));
  }

  public static Plugin getPlugin() {
    return Bukkit.getPluginManager().getPlugin("PotionManager");
  }

  public static String GUIName() {
    return color(Main.settings.getConfig().getString("Potion_GUI.GUI_Name"));
  }

  public static int GUISize() {
    return Main.settings.getConfig().getInt("Potion_GUI.GUI_Size");
  }
}