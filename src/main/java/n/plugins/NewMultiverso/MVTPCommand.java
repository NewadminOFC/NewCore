// MVTPCommand.java
package n.plugins.NewMultiverso;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MVTPCommand implements CommandExecutor {

    private final NewWorldsPlugin plugin;

    public MVTPCommand(NewWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(plugin.getMessage("only-players")));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(color(plugin.getMessage("invalid-usage").replace("{usage}", "/mvtp <mundo>")));
            return true;
        }

        Player player = (Player) sender;
        String worldName = args[0];

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(color(plugin.getMessage("world-not-found").replace("{world}", worldName)));
            return true;
        }

        boolean hasPerm = player.hasPermission("mv.tp." + worldName.toLowerCase());

        boolean bypass = false;
        if (player.hasPermission("mv.Bypass")) {
            if (player.hasPermission("grupo.vip") && plugin.getSettings().getBoolean("Bypass-vip", true)) {
                bypass = true;
            } else if (player.hasPermission("grupo.membro") && plugin.getSettings().getBoolean("Bypass-membro", false)) {
                bypass = true;
            }
        }

        if (!hasPerm && !bypass) {
            player.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }

        player.teleport(world.getSpawnLocation());
        player.sendMessage(color(plugin.getMessage("teleport-success").replace("{world}", worldName)));
        return true;
    }

    private String color(String msg) {
        return msg == null ? "" : msg.replace("&", "§");
    }
}
