package n.plugins.NewLogin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {

    private final NewLogin login;
    private final LoginConfig cfg;

    public LoginCommand(NewLogin login, LoginConfig cfg) {
        this.login = login;
        this.cfg = cfg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(cfg.msg("only-player"));
            return true;
        }
        Player p = (Player) sender;
        String cmd = label.toLowerCase();

        if (cmd.equals("login")) {
            if (args.length < 1) {
                p.sendMessage(cfg.msg("usage-login"));
                return true;
            }
            String senha = args[0];
            LoginManager manager = login.getLoginManager();
            if (manager == null) { p.sendMessage(cfg.msg("not-initialized")); return true; }

            if (manager.isLogged(p)) {
                p.sendMessage(cfg.msg("already-logged"));
                return true;
            }

            if (manager.checkPassword(p.getName(), senha)) {
                manager.setLogged(p, true);
                p.sendMessage(cfg.msg("login-success"));
                // teleporta para saída, se configurada
                if (login.getSaida() != null) p.teleport(login.getSaida());
            } else {
                p.sendMessage(cfg.msg("login-fail"));
            }
            return true;
        }

        if (cmd.equals("register")) {
            if (args.length < 1) {
                p.sendMessage(cfg.msg("usage-register"));
                return true;
            }
            String senha = args[0];
            LoginManager manager = login.getLoginManager();
            if (manager == null) { p.sendMessage(cfg.msg("not-initialized")); return true; }

            if (manager.isRegistered(p.getName())) {
                p.sendMessage(cfg.msg("already-registered"));
                return true;
            }

            if (senha.length() < cfg.getMinLength()) {
                p.sendMessage(cfg.msg("password-too-short").replace("%min%", String.valueOf(cfg.getMinLength())));
                return true;
            }

            if (senha.length() > cfg.getMaxLength()) {
                p.sendMessage(cfg.msg("password-too-long").replace("%max%", String.valueOf(cfg.getMaxLength())));
                return true;
            }

            manager.register(p.getName(), senha);
            p.sendMessage(cfg.msg("register-success"));
            return true;
        }

        return false;
    }
}
