package n.plugins.NewLogin;

import org.bukkit.Bukkit;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores podem usar este comando.");
            return true;
        }

        Player p = (Player) sender;

        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "Comandos do NewLogin:");
            p.sendMessage(ChatColor.YELLOW + "/newlogin setspawn - Define o spawn do login.");
            p.sendMessage(ChatColor.YELLOW + "/newlogin setsaida - Define a saída após login.");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "setspawn":
                Location spawnLoc = p.getLocation();
                module.setSpawn(spawnLoc);
                p.sendMessage(ChatColor.GREEN + "Spawn de login definido!");
                break;

            case "setsaida":
                Location saidaLoc = p.getLocation();
                module.setSaida(saidaLoc);
                p.sendMessage(ChatColor.GREEN + "Saída após login definida!");
                break;

            default:
                p.sendMessage(ChatColor.RED + "Subcomando inválido. Use /newlogin para ver os comandos disponíveis.");
                break;
        }

        return true;
    }
}
