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

    private void teleportBack(Player p) {
        if (module.getSpawn() != null) {
            // Teleporta o jogador para o spawn definido
            p.teleport(module.getSpawn());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (check(p)) {
            // Apenas teleporta se o jogador mudou de posição (X, Y, Z)
            if (e.getFrom().getX() != e.getTo().getX() ||
                    e.getFrom().getY() != e.getTo().getY() ||
                    e.getFrom().getZ() != e.getTo().getZ()) {
                teleportBack(p);
            }
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (check(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (check(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && check((Player) e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (check(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (check(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        module.getLoginManager().setLogged(p, false);
        module.getLoginTask().addPlayer(p);
        if (module.getSpawn() != null) p.teleport(module.getSpawn());

        // Mensagem de registro ou login
        if (!module.getLoginManager().isRegistered(p.getName())) {
            p.sendMessage(ChatColor.YELLOW + "Bem-vindo! Use /register <senha> <repita a senha> ou /registro <senha> <repita a senha> para se registrar.");
        } else {
            p.sendMessage(ChatColor.YELLOW + "Você já está registrado. Use /login <senha> ou /logar <senha> para entrar.");
        }
    }


    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        module.getLoginTask().removePlayer(e.getPlayer());
    }
}
