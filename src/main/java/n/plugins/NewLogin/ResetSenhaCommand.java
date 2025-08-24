package n.plugins.NewLogin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ResetSenhaCommand implements CommandExecutor {

    private final NewLogin module;

    public ResetSenhaCommand(NewLogin module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("newlogin.resetsenha")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + "Use: /resetsenha <player>");
            return true;
        }

        String targetName = args[0].toLowerCase();

        if (!module.getLoginManager().isRegistered(targetName)) {
            sender.sendMessage(ChatColor.RED + "O jogador não está registrado!");
            return true;
        }

        try {
            Connection conn = module.getLoginManager().getConnection();
            PreparedStatement ps = conn.prepareStatement("UPDATE accounts SET password=NULL WHERE name=?");
            ps.setString(1, targetName);
            ps.executeUpdate();
            ps.close();

            sender.sendMessage(ChatColor.GREEN + "Senha do jogador " + targetName + " foi resetada.");

            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                target.sendMessage(ChatColor.YELLOW + "Sua senha foi resetada. Use /register <senha> para criar uma nova.");
                module.getLoginManager().setLogged(target, false);

                // Teleporta para o spawn definido
                Location spawn = module.getSpawn();
                if (spawn != null) {
                    target.teleport(spawn);
                }
            }

        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "Ocorreu um erro ao resetar a senha.");
            e.printStackTrace();
        }

        return true;
    }
}
