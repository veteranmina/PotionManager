package me.veritasluxmea.PotionsManager.Commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandContext {
    private final CommandSender sender;
    private final Player player; // null if console
    private final boolean isConsole;
    private final String effectName;
    private String tier; // cached tier for this command execution

    public CommandContext(CommandSender sender, String effectName) {
        this.sender = sender;
        this.isConsole = !(sender instanceof Player);
        this.player = isConsole ? null : (Player) sender;
        this.effectName = effectName != null ? effectName.toLowerCase() : null;
    }

    public CommandSender getSender() {
        return sender;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isConsole() {
        return isConsole;
    }

    public String getEffectName() {
        return effectName;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public boolean hasPermission(String permission) {
        return isConsole || sender.hasPermission(permission);
    }
}
