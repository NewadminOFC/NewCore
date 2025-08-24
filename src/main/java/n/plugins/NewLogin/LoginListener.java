package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.ChatColor;
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

    private boolean check(Player p) {
        return !module.getLoginManager().isLogged(p);
    }

    private void forceBack(Player p) {
        if (module.getSpawn() != null) {
            p.teleport(module.getSpawn());
            p.sendMessage(ChatColor.RED + "Você deve logar primeiro! (/login <senha>)");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (check(p)) {
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ() || e.getFrom().getY() != e.getTo().getY()) {
                forceBack(p);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (check(e.getPlayer())) {
            e.setCancelled(true);
            forceBack(e.getPlayer());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (check(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Você deve logar primeiro!");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            if (check(p)) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (check(e.getEntity())) e.setKeepInventory(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (check(e.getPlayer())) {
            e.setCancelled(true);
            forceBack(e.getPlayer());
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (check(e.getPlayer())) {
            e.setCancelled(true);
            forceBack(e.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        module.getLoginManager().setLogged(p, false);
        if (module.getSpawn() != null) {
            p.teleport(module.getSpawn());
        }
        p.sendMessage(ChatColor.YELLOW + "Use /register <senha> ou /login <senha>");
    }
}
