package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.Map;

public class LoginTask implements Runnable, Listener {

    private final NewCore plugin;
    private final NewLogin module;
    private boolean running = true;
    private final Map<String, Long> joinTimestamps = new HashMap<>();

    public LoginTask(NewCore plugin, NewLogin module) {
        this.plugin = plugin;
        this.module = module;

        // Registrar listener dentro da própria classe
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void addPlayer(Player p) {
        joinTimestamps.put(p.getName().toLowerCase(), System.currentTimeMillis());
    }

    public void removePlayer(Player p) {
        joinTimestamps.remove(p.getName().toLowerCase());
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        if (!running) return;

        int timeout = module.getTimeoutSeconds();
        long now = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!module.getLoginManager().isLogged(p)) {
                Long joinTime = joinTimestamps.get(p.getName().toLowerCase());
                if (joinTime != null) {
                    long elapsed = (now - joinTime) / 1000L;
                    if (elapsed >= timeout) {
                        p.kickPlayer(module.getTimeoutMessage());
                        joinTimestamps.remove(p.getName().toLowerCase());
                    }
                } else {
                    joinTimestamps.put(p.getName().toLowerCase(), now);
                }
            } else {
                joinTimestamps.remove(p.getName().toLowerCase());
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!module.getLoginManager().isLogged(p)) {
            String msg = e.getMessage().toLowerCase();
            // Permitir apenas login/register
            if (!(msg.startsWith("/login") || msg.startsWith("/logar") || msg.startsWith("/register") || msg.startsWith("/registro"))) {
                e.setCancelled(true);
                p.sendMessage("§cVocê precisa se logar primeiro! Use /login ou /register.");
            }
        }
    }
}
