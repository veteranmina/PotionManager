package me.veritasluxmea.PotionsManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MessagesManager {
    private static MessagesManager instance = new MessagesManager();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Plugin plugin;
    private FileConfiguration config;
    private File messagesFile;

    public static MessagesManager getInstance() {
        return instance;
    }

    public void setup(Plugin plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!this.messagesFile.exists()) {
            try {
                InputStream in = getClass().getResourceAsStream("/messages.yml");
                copyFile(in, messagesFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create messages.yml!");
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(this.messagesFile);
    }

    public FileConfiguration getConfig() {
        return this.config;
    }

    public void saveConfig() {
        try {
            this.config.save(this.messagesFile);
        } catch (Exception e) {
            Bukkit.getServer().getLogger().severe("Could not save messages.yml!");
        }
    }

    public void reloadConfig() {
        this.config = YamlConfiguration.loadConfiguration(this.messagesFile);
    }

    /**
     * Get a message Component from the messages.yml file with placeholder support
     * @param path The path to the message in messages.yml
     * @param resolvers Variable number of TagResolver placeholders
     * @return The formatted Component
     */
    public Component getMessage(String path, TagResolver... resolvers) {
        String rawMessage = config.getString(path, "<red>Missing message: " + path);

        // Combine all resolvers into one
        TagResolver combined = resolvers.length > 0 ? TagResolver.resolver(resolvers) : TagResolver.empty();

        return miniMessage.deserialize(rawMessage, combined);
    }

    /**
     * Get a message Component without placeholders
     * @param path The path to the message in messages.yml
     * @return The formatted Component
     */
    public Component getMessage(String path) {
        return getMessage(path, TagResolver.empty());
    }

    /**
     * Send a message to a CommandSender with placeholder support
     * @param sender The CommandSender to send the message to
     * @param path The path to the message in messages.yml
     * @param resolvers Variable number of TagResolver placeholders
     */
    public void sendMessage(CommandSender sender, String path, TagResolver... resolvers) {
        sender.sendMessage(getMessage(path, resolvers));
    }

    /**
     * Send a message to a CommandSender without placeholders
     * @param sender The CommandSender to send the message to
     * @param path The path to the message in messages.yml
     */
    public void sendMessage(CommandSender sender, String path) {
        sendMessage(sender, path, TagResolver.empty());
    }

    /**
     * Helper method for creating placeholders easily
     * @param key The placeholder key (without curly braces)
     * @param value The value to replace the placeholder with
     * @return A TagResolver placeholder
     */
    public static TagResolver placeholder(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    /**
     * Convert legacy color codes (&) to MiniMessage format
     * Useful for backward compatibility
     * @param legacyText Text with & color codes
     * @return Text with MiniMessage tags
     */
    public String convertLegacyToMiniMessage(String legacyText) {
        return legacyText
            .replace("&0", "<black>")
            .replace("&1", "<dark_blue>")
            .replace("&2", "<dark_green>")
            .replace("&3", "<dark_aqua>")
            .replace("&4", "<dark_red>")
            .replace("&5", "<dark_purple>")
            .replace("&6", "<gold>")
            .replace("&7", "<gray>")
            .replace("&8", "<dark_gray>")
            .replace("&9", "<blue>")
            .replace("&a", "<green>")
            .replace("&b", "<aqua>")
            .replace("&c", "<red>")
            .replace("&d", "<light_purple>")
            .replace("&e", "<yellow>")
            .replace("&f", "<white>")
            .replace("&l", "<bold>")
            .replace("&m", "<strikethrough>")
            .replace("&n", "<underlined>")
            .replace("&o", "<italic>")
            .replace("&k", "<obfuscated>")
            .replace("&r", "<reset>");
    }

    private static void copyFile(InputStream in, File out) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[1024];
            int i;
            while ((i = in.read(buf)) != -1) {
                fos.write(buf, 0, i);
            }
        } finally {
            if (in != null) in.close();
        }
    }
}
