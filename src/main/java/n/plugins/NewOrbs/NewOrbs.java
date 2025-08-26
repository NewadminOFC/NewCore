package n.plugins.NewOrbs;

import n.plugins.NewCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.logging.Level;

public final class NewOrbs implements Listener, CommandExecutor {

    private final NewCore plugin;
    private ConfigManager config;
    private TradeManager trade;
    private MenuListener menu;

    public NewOrbs(NewCore plugin) {
        this.plugin = plugin;
    }

    /** Chame isso no onEnable do NewCore (ou no NewCommandManager.registerAll). */
    public void register() {
        try {
            // Garante arquivo padrão
            ConfigManager.ensureDefaultFile(plugin);
            this.config = new ConfigManager(plugin);
            this.trade  = new TradeManager(plugin, config);
            this.menu   = new MenuListener(plugin, config, trade);

            // Listeners
            Bukkit.getPluginManager().registerEvents(menu, plugin);

            // Comando
            PluginCommand cmd = plugin.getCommand("neworbs");
            if (cmd != null) {
                cmd.setExecutor(this);
            } else {
                plugin.getLogger().warning("[NewOrbs] Comando 'neworbs' não existe no plugin.yml do NewCore.");
            }

            plugin.getLogger().info("[NewOrbs] módulo carregado (via NewCore).");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[NewOrbs] Falha ao registrar módulo", t);
        }
    }

    // /neworbs
    // /neworbs reload
    // /neworbs open [jogador] [orbKey]
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                if (sender instanceof Player) {
                    menu.openMainMenu((Player) sender);
                } else {
                    sender.sendMessage("§eUso: /" + label + " reload | open <jogador> [orbKey]");
                }
                return true;
            }

            if ("reload".equalsIgnoreCase(args[0])) {
                config.reload();
                sender.sendMessage(ChatColor.GREEN + "[NewOrbs] Config recarregada.");
                return true;
            }

            if ("open".equalsIgnoreCase(args[0])) {
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Jogador offline: " + args[1]);
                        return true;
                    }
                    if (args.length >= 3) {
                        menu.openTradeMenu(target, args[2].toLowerCase());
                    } else {
                        menu.openMainMenu(target);
                    }
                    sender.sendMessage(ChatColor.YELLOW + "Menu aberto para " + target.getName() + ".");
                    return true;
                } else if (sender instanceof Player) {
                    menu.openMainMenu((Player) sender);
                    return true;
                } else {
                    sender.sendMessage("§cUso: /" + label + " open <jogador> [orbKey]");
                    return true;
                }
            }

            sender.sendMessage("§cUso: /" + label + " [reload | open <jogador> [orbKey]]");
            return true;
        } catch (Throwable t) {
            sender.sendMessage("§c[NewOrbs] Ocorreu um erro. Verifique o console.");
            plugin.getLogger().log(Level.SEVERE, "[NewOrbs] Erro no comando", t);
            return true;
        }
    }
}
