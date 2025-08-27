package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoginTask implements Runnable, Listener {

    private final NewCore plugin;
    private final NewLogin module;
    private boolean running = true;
    private final Map<String, Long> joinTimestamps = new HashMap<String, Long>();

    public LoginTask(NewCore plugin, NewLogin module) {
        this.plugin = plugin;
        this.module = module;
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

    public void stop() { running = false; }

    @Override
    public void run() {
        if (!running) return;

        int timeout = module.getConfig().getTimeoutSeconds();
        long now = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!module.getLoginManager().isLogged(p)) {
                Long joinTime = joinTimestamps.get(p.getName().toLowerCase());
                if (joinTime != null) {
                    long elapsed = (now - joinTime) / 1000L;
                    if (elapsed >= timeout) {
                        p.kickPlayer(module.getConfig().getTimeoutMessage());
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
        if (!module.getConfig().blockCommandsBeforeLogin()) return;
        Player p = e.getPlayer();
        if (module.getLoginManager().isLogged(p)) return;

        String msg = e.getMessage().toLowerCase();
        List<String> allow = module.getConfig().allowedCommandsBeforeLogin();

        // Permite se começar com algum comando permitido
        for (String c : allow) {
            if (msg.startsWith("/" + c)) return;
        }

        e.setCancelled(true);
        p.sendMessage(module.getConfig().msg("must-login"));
    }
}
