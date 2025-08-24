package n.plugins.NewLogin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {

    private final NewLogin module;

    public LoginCommand(NewLogin module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "Use: /register <senha>");
                return true;
            }
            if (module.getLoginManager().isRegistered(p.getName())) {
                p.sendMessage(ChatColor.RED + "Você já está registrado!");
                return true;
            }
            String pass = args[0];
            if (pass.length() < module.getMinDigits() || pass.length() > module.getMaxDigits()) {
                p.sendMessage(ChatColor.RED + "A senha deve ter entre " + module.getMinDigits() + " e " + module.getMaxDigits() + " dígitos.");
                return true;
            }
            if (module.getLoginManager().register(p.getName(), pass)) {
                p.sendMessage(ChatColor.GREEN + "Registrado com sucesso! Agora use /login <senha>");
            } else {
                p.sendMessage(ChatColor.RED + "Erro ao registrar!");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "Use: /login <senha>");
                return true;
            }
            if (!module.getLoginManager().isRegistered(p.getName())) {
                p.sendMessage(ChatColor.RED + "Você não está registrado!");
                return true;
            }
            String pass = args[0];
            if (module.getLoginManager().checkPassword(p.getName(), pass)) {
                module.getLoginManager().setLogged(p, true);
                p.sendMessage(ChatColor.GREEN + "Logado com sucesso!");
                if (module.getSaida() != null) p.teleport(module.getSaida());
            } else {
                p.sendMessage(ChatColor.RED + "Senha incorreta!");
            }
            return true;
        }
        return false;
    }
}
