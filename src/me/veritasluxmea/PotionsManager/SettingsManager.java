package me.veritasluxmea.PotionsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;


public class SettingsManager
{
  static SettingsManager instance = new SettingsManager();

  public static SettingsManager getInstance() {
    return instance;
  }


  Plugin plugin;
  FileConfiguration config;
  File cfile;

  public void setup(Plugin plugin) {
    if (!plugin.getDataFolder().exists()) {
      plugin.getDataFolder().mkdirs();
    }

    this.cfile = new File(plugin.getDataFolder(), "config.yml");
    if (!this.cfile.exists()) {

      try {

        File en = new File(plugin.getDataFolder(), "/config.yml");
        InputStream E = getClass().getResourceAsStream("/config.yml");
        copyFile(E, en);
      }
      catch (Exception e) {

        e.printStackTrace();
      }
    }
    this.config = (FileConfiguration)YamlConfiguration.loadConfiguration(this.cfile);
  }

  public FileConfiguration getConfig() {
    return this.config;
  }

  public void saveConfig() {
    try {
      this.config.save(this.cfile);
    } catch (IOException e) {
      Bukkit.getServer().getLogger().severe(ChatColor.RED + "Could not save config.yml!");
    }
  }

  public void reloadConfig() {
    this.config = (FileConfiguration)YamlConfiguration.loadConfiguration(this.cfile);
  }

  public PluginDescriptionFile getDesc() {
    return this.plugin.getDescription();
  }

  public static void copyFile(InputStream in, File out) throws Exception {
    InputStream fis = in;
    FileOutputStream fos = new FileOutputStream(out);


    try {
      byte[] buf = new byte[1024];
      int i = 0;
      while ((i = fis.read(buf)) != -1) {
        fos.write(buf, 0, i);
      }
    }
    catch (Exception e) {

      throw e;
    }
    finally {

      if (fis != null) {
        fis.close();
      }

      if (fos != null)
        fos.close();
    }
  }
}