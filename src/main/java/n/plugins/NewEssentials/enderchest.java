package n.plugins.NewEssentials;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class enderchest implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando!");
            return true;
        }

        Player player = (Player) sender;

        // Se o jogador tiver permissão e passar argumento, abre de outro player
        if (args.length == 1 && player.hasPermission("newessentials.enderchest.others")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null && target.isOnline()) {
                player.openInventory(target.getEnderChest());
                player.sendMessage("§aVocê abriu o EnderChest de " + target.getName());
            } else {
                player.sendMessage("§cJogador não encontrado!");
            }
            return true;
        }

        // Abre o EnderChest do próprio jogador
        player.openInventory(player.getEnderChest());
        player.sendMessage("§aAbrindo seu EnderChest...");
        return true;
    }
}
