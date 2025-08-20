package n.plugins.chat;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NewChat implements Listener, CommandExecutor {

    private final JavaPlugin core;

    private File cfgFile;
    private YamlConfiguration cfg;

    private double chatRadius;
    private final Map<UUID, UUID> lockedChats = new ConcurrentHashMap<UUID, UUID>();

    public NewChat(JavaPlugin core) {
        this.core = core;
        loadConfigChat();
        this.chatRadius = cfg.getDouble("chat-radius", 20.0);
        mostrarTitle();
        core.getLogger().info("[NewChat] pronto.");
        // Observação: registros de Listener/Comandos são feitos pelo NewCore.
    }

    /* ========= CONFIG ========= */
    private void loadConfigChat() {
        if (!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
        cfgFile = new File(core.getDataFolder(), "ConfigChat.yml");
        if (!cfgFile.exists()) {
            try {
                // copia do jar se existir (coloque ConfigChat.yml em resources)
                core.saveResource("ConfigChat.yml", false);
            } catch (Throwable ignore) {
                // se não tiver no jar, cria vazio e usa defaults no código
            }
        }
        cfg = YamlConfiguration.loadConfiguration(cfgFile);
    }

    private void reloadConfigChat() {
        if (cfgFile == null) cfgFile = new File(core.getDataFolder(), "ConfigChat.yml");
        cfg = YamlConfiguration.loadConfiguration(cfgFile);
    }

    /* ========= HELPERS ========= */
    public String getMessage(String path) {
        String raw = cfg.getString("messages." + path, "&cMensagem '" + path + "' não encontrada.");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    String getPrefix() {
        String prefix = cfg.getString("prefix", "&b[NewChat]&r ");
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    public String getPerm(String key) {
        return cfg.getString("permissions." + key, "newchat." + key);
    }

    /* ========= CHAT ========= */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();

        // permissão do chat normal
        if (!sender.hasPermission("newchat.chat")) {
            sender.sendMessage(getPrefix() + getMessage("no-permission"));
            event.setCancelled(true);
            return;
        }

        String playerMessage = ChatColor.translateAlternateColorCodes('&', event.getMessage());

        // chat travado (/tell <nick>)
        if (lockedChats.containsKey(sender.getUniqueId())) {
            UUID targetUUID = lockedChats.get(sender.getUniqueId());
            Player target = Bukkit.getPlayer(targetUUID);

            event.setCancelled(true);

            if (target != null && target.isOnline()) {
                String toTarget = getPrefix() + getMessage("tell-to-target")
                        .replace("{sender}", sender.getName())
                        .replace("{message}", playerMessage);
                String toSender = getPrefix() + getMessage("tell-to-sender")
                        .replace("{target}", target.getName())
                        .replace("{message}", playerMessage);

                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    toTarget = PlaceholderAPI.setPlaceholders(target, toTarget);
                    toSender = PlaceholderAPI.setPlaceholders(sender, toSender);
                }

                target.sendMessage(toTarget);
                sender.sendMessage(toSender);
            } else {
                sender.sendMessage(getPrefix() + getMessage("tell-not-found")
                        .replace("{target}", getMessage("offline-player")));
                lockedChats.remove(sender.getUniqueId());
            }
            return;
        }

        // chat local
        String format = getPrefix() + getMessage("local-format");
        String message = format.replace("{player}", sender.getDisplayName())
                .replace("{message}", playerMessage);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            message = PlaceholderAPI.setPlaceholders(sender, message);
        }

        event.setCancelled(true);

        List<Player> nearbyPlayers = new ArrayList<Player>();
        for (Player p : sender.getWorld().getPlayers()) {
            if (!p.equals(sender)) {
                try {
                    if (p.getLocation().distance(sender.getLocation()) <= chatRadius) {
                        nearbyPlayers.add(p);
                    }
                } catch (Throwable ignored) {}
            }
        }

        for (Player p : nearbyPlayers) p.sendMessage(message);
        sender.sendMessage(message);

        if (nearbyPlayers.isEmpty()) {
            sender.sendMessage(getPrefix() + getMessage("no-one-nearby"));
        }
    }

    /* ========= COMANDOS ========= */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (command.getName().equalsIgnoreCase("g")) {
            if (!sender.hasPermission("newchat.global")) {
                sender.sendMessage(getPrefix() + getMessage("no-permission"));
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(getPrefix() + getMessage("global-usage"));
                return true;
            }

            String msg = ChatColor.translateAlternateColorCodes('&', join(args, 0));
            String format = getPrefix() + getMessage("global-format")
                    .replace("{player}", (sender instanceof Player) ? ((Player) sender).getDisplayName() : sender.getName())
                    .replace("{message}", msg);

            if (sender instanceof Player && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                format = PlaceholderAPI.setPlaceholders((Player) sender, format);
            }

            for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(format);
            return true;
        }

        if (command.getName().equalsIgnoreCase("tell")) {
            if (!sender.hasPermission("newchat.tell")) {
                sender.sendMessage(getPrefix() + getMessage("no-permission"));
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(getPrefix() + getMessage("only-players"));
                return true;
            }

            Player playerSender = (Player) sender;

            // /tell <nick>
            if (args.length == 1) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(getPrefix() + getMessage("tell-not-found").replace("{target}", args[0]));
                    return true;
                }

                UUID senderUUID = playerSender.getUniqueId();
                UUID targetUUID = target.getUniqueId();

                if (lockedChats.containsKey(senderUUID) && lockedChats.get(senderUUID).equals(targetUUID)) {
                    lockedChats.remove(senderUUID);
                    sender.sendMessage(getPrefix() + getMessage("tell-unlocked").replace("{target}", target.getName()));
                } else {
                    lockedChats.put(senderUUID, targetUUID);
                    sender.sendMessage(getPrefix() + getMessage("tell-locked").replace("{target}", target.getName()));
                }
                return true;
            }

            // /tell <nick> <mensagem>
            if (args.length >= 2) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(getPrefix() + getMessage("tell-not-found").replace("{target}", args[0]));
                    return true;
                }

                String msg = ChatColor.translateAlternateColorCodes('&', join(args, 1));

                String toTarget = getPrefix() + getMessage("tell-to-target")
                        .replace("{sender}", sender.getName())
                        .replace("{message}", msg);
                String toSender = getPrefix() + getMessage("tell-to-sender")
                        .replace("{target}", target.getName())
                        .replace("{message}", msg);

                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    toTarget = PlaceholderAPI.setPlaceholders(target, toTarget);
                    toSender = PlaceholderAPI.setPlaceholders(playerSender, toSender);
                }

                target.sendMessage(toTarget);
                playerSender.sendMessage(toSender);
                return true;
            }

            sender.sendMessage(getPrefix() + getMessage("tell-usage"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("chat")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("newchat.reload")) {
                    sender.sendMessage(getPrefix() + getMessage("no-permission"));
                    return true;
                }
                reloadConfigChat();
                chatRadius = cfg.getDouble("chat-radius", 20.0);
                sender.sendMessage(getPrefix() + getMessage("reloaded"));
                return true;
            }
            sender.sendMessage(getPrefix() + getMessage("chat-usage"));
            return true;
        }

        return false;
    }

    private String join(String[] a, int from) {
        if (a == null || a.length <= from) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < a.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    /* ========= Banner ========= */
    public void mostrarTitle() {
        core.getLogger().info("  _   _                      _____   _               _   ");
        core.getLogger().info(" | \\ | |                    / ____| | |             | |  ");
        core.getLogger().info(" |  \\| |   ___  __      __ | |      | |__     __ _  | |_ ");
        core.getLogger().info(" | . ` |  / _ \\ \\ \\ /\\ / / | |      | '_ \\   / _` | | __|");
        core.getLogger().info(" | |\\  | |  __/  \\ V  V /  | |____  | | | | | (_| | | |_ ");
        core.getLogger().info(" |_| \\_|  \\___|   \\_/\\_/    \\_____| |_| |_|  \\__,_|  \\__|");
        core.getLogger().info("Website:   newplugins.shop     Discord:    discord.gg/animesverse");
    }
}
