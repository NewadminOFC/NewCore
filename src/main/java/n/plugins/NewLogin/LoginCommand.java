package n.plugins.NewLogin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class LoginCommand implements CommandExecutor {

    private final NewLogin module;
    private final List<String> registerAliases = Arrays.asList("register", "registro");
    private final List<String> loginAliases = Arrays.asList("login", "logar");

    public LoginCommand(NewLogin module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        // Bloqueia comandos se já estiver logado
        if (module.getLoginManager().isLogged(p)) {
            if (registerAliases.contains(cmd.getName().toLowerCase())) {
                p.sendMessage(ChatColor.RED + "Você já está registrado e logado!");
                return true;
            }
            if (loginAliases.contains(cmd.getName().toLowerCase())) {
                p.sendMessage(ChatColor.RED + "Você já está logado!");
                return true;
            }
        }

        // ===== Registro =====
        if (registerAliases.contains(cmd.getName().toLowerCase())) {
            if (args.length != 2) {
                p.sendMessage(ChatColor.RED + "Use: /" + cmd.getName() + " <senha> <repita a senha>");
                return true;
            }
            if (module.getLoginManager().isRegistered(p.getName())) {
                p.sendMessage(module.getConfig().getString("mensagens.ja_registrado"));
                return true;
            }

            String pass = args[0];
            String repeat = args[1];

            if (!pass.equals(repeat)) {
                p.sendMessage(ChatColor.RED + "As senhas não coincidem!");
                return true;
            }

            if (pass.length() < module.getMinDigits() || pass.length() > module.getMaxDigits()) {
                String msg = module.getConfig().getString("mensagens.senha_invalida")
                        .replace("{min}", String.valueOf(module.getMinDigits()))
                        .replace("{max}", String.valueOf(module.getMaxDigits()));
                p.sendMessage(msg);
                return true;
            }

            if (module.getLoginManager().register(p.getName(), pass)) {
                p.sendMessage(module.getConfig().getString("mensagens.registrar_sucesso"));
                module.getLoginManager().setLogged(p, false); // ainda não logado
            } else {
                p.sendMessage(module.getConfig().getString("mensagens.registrar_erro"));
            }
            return true;
        }

        // ===== Login =====
        if (loginAliases.contains(cmd.getName().toLowerCase())) {
            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "Use: /" + cmd.getName() + " <senha>");
                return true;
            }
            if (!module.getLoginManager().isRegistered(p.getName())) {
                p.sendMessage(module.getConfig().getString("mensagens.nao_registrado"));
                return true;
            }

            String pass = args[0];
            if (module.getLoginManager().checkPassword(p.getName(), pass)) {
                module.getLoginManager().setLogged(p, true);
                module.getLoginTask().removePlayer(p);
                p.sendMessage(module.getConfig().getString("mensagens.login_sucesso"));
                if (module.getSaida() != null) p.teleport(module.getSaida());
            } else {
                p.sendMessage(module.getConfig().getString("mensagens.login_erro"));
            }
            return true;
        }

        return false;
    }
}
