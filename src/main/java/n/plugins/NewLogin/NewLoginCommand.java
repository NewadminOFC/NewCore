package n.plugins.NewLogin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NewLoginCommand implements CommandExecutor {

    private final NewLogin module;

    public NewLoginCommand(NewLogin module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "/newlogin setspawn");
            p.sendMessage(ChatColor.YELLOW + "/newlogin setsaida");
            return true;
        }

        if (args[0].equalsIgnoreCase("setspawn")) {
            Location loc = p.getLocation();
            module.setSpawn(loc);
            p.sendMessage(ChatColor.GREEN + "Spawn de login definido!");
            return true;
        }

        if (args[0].equalsIgnoreCase("setsaida")) {
            Location loc = p.getLocation();
            module.setSaida(loc);
            p.sendMessage(ChatColor.GREEN + "Saída após login definida!");
            return true;
        }

        return false;
    }
}
