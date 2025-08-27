// File: src/main/java/n/plugins/NewLogin/CommandHideListener.java
package n.plugins.NewLogin;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

public class CommandHideListener implements Listener {

    private final LoginConfig cfg;

    public CommandHideListener(LoginConfig cfg) {
        this.cfg = cfg;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage();
        if (raw == null || raw.isEmpty()) return;

        String msg = raw.toLowerCase();

        // comandos vindos do ConfigLogin.yml -> geral.hide-from-console (sem '/')
        List<String> hide = cfg.hideFromConsole();
        for (String c : hide) {
            if (c == null || c.isEmpty()) continue;
            String p1 = "/" + c + " ";
            String p2 = "/" + c; // caso digite só /login
            if (msg.startsWith(p1) || msg.equals(p2)) {
                // cancela para não logar no console
                e.setCancelled(true);
                // reexecuta sem passar pelo logger padrão
                Bukkit.dispatchCommand(e.getPlayer(), raw.substring(1)); // remove o '/'
                return;
            }
        }
    }
}
