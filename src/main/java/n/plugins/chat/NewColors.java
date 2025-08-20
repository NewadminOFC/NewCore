package n.plugins.chat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class NewColors implements Listener, CommandExecutor {

    private final NewChat plugin;

    public NewColors(NewChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        message = ChatColor.translateAlternateColorCodes('&', message);
        event.setMessage(message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("cores".equalsIgnoreCase(command.getName())) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessage("only-players"));
                return true;
            }
            if (!sender.hasPermission(plugin.getPerm("cores"))) {
                sender.sendMessage(plugin.getPrefix() + plugin.getMessage("no-permission"));
                return true;
            }

            Player player = (Player) sender;

            player.sendMessage(plugin.getMessage("colors-title"));
            player.sendMessage(plugin.getMessage("colors-line"));
            player.sendMessage(plugin.getMessage("colors-list1"));
            player.sendMessage(plugin.getMessage("colors-list2"));
            player.sendMessage(plugin.getMessage("colors-list3"));
            player.sendMessage(plugin.getMessage("colors-list4"));
            player.sendMessage(plugin.getMessage("colors-list5"));
            player.sendMessage(plugin.getMessage("colors-line"));
            player.sendMessage(plugin.getMessage("formats-title"));
            player.sendMessage(plugin.getMessage("formats-list"));
            player.sendMessage(plugin.getMessage("colors-line"));
            player.sendMessage(plugin.getMessage("colors-tip"));
            player.sendMessage(plugin.getMessage("colors-example"));

            return true;
        }
        return false;
    }
}
