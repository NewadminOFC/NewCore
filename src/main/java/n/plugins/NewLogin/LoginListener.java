package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;

public class LoginListener implements Listener {

    private final NewCore plugin;
    private final NewLogin module;

    public LoginListener(NewCore plugin, NewLogin module) {
        this.plugin = plugin;
        this.module = module;
    }

    private boolean notLogged(Player p) {
        return !module.getLoginManager().isLogged(p);
    }

    private void teleportToSpawnIfConfigured(Player p) {
        if (module.getConfig().teleportToSpawnOnJoin() && module.getSpawn() != null) {
            p.teleport(module.getSpawn());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (notLogged(p)) {
            // Se moveu de fato?
            if (e.getFrom().getX() != e.getTo().getX() ||
                    e.getFrom().getY() != e.getTo().getY() ||
                    e.getFrom().getZ() != e.getTo().getZ()) {
                teleportToSpawnIfConfigured(p);
            }
            e.setCancelled(true);
        }
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) { if (notLogged(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) { if (notLogged(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player && notLogged((Player) e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onBreak(BlockBreakEvent e) { if (notLogged(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (notLogged(e.getPlayer())) e.setCancelled(true); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        module.getLoginManager().setLogged(p, false);
        module.getLoginTask().addPlayer(p);

        // Teleporta ao entrar, se configurado
        teleportToSpawnIfConfigured(p);

        // Mensagem de help
        if (!module.getLoginManager().isRegistered(p.getName())) {
            p.sendMessage(module.getConfig().msg("joined-register"));
        } else {
            p.sendMessage(module.getConfig().msg("joined-login"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        module.getLoginTask().removePlayer(e.getPlayer());
    }
}
