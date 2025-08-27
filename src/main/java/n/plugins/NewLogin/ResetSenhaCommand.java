package n.plugins.NewLogin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ResetSenhaCommand implements CommandExecutor {

    private final NewLogin login;
    private final LoginConfig cfg;

    public ResetSenhaCommand(NewLogin login, LoginConfig cfg) {
        this.login = login;
        this.cfg = cfg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("newlogin.admin")) {
            sender.sendMessage(cfg.getPrefix() + "§cSem permissão.");
            return true;
        }

        if (args.length < 1) {
            // Reaproveita a msg de uso do register (ou crie uma "usage-resetsenha" se quiser)
            sender.sendMessage(cfg.msg("usage-register"));
            return true;
        }

        String alvo = args[0];
        LoginManager manager = login.getLoginManager();
        if (manager == null) { sender.sendMessage(cfg.msg("not-initialized")); return true; }

        String novaSenha = manager.resetPassword(alvo);
        if (novaSenha == null) {
            sender.sendMessage(cfg.msg("reset-fail").replace("%player%", alvo));
            return true;
        }

        sender.sendMessage(cfg.msg("reset-success").replace("%player%", alvo));
        sender.sendMessage(cfg.msg("new-password").replace("%senha%", novaSenha));
        return true;
    }
}
