package n.plugins.NewOrbs;

import n.plugins.NewCore;
import n.plugins.NewOrbs.ConfigManager.OrbDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.logging.Level;

public final class NewOrbs implements Listener, CommandExecutor {

    private final NewCore plugin;
    private ConfigManager config;
    private TradeManager trade;
    private MenuListener menu;

    public NewOrbs(NewCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            ConfigManager.ensureDefaultFile(plugin);
            this.config = new ConfigManager(plugin);
            this.trade  = new TradeManager(plugin, config);
            this.menu   = new MenuListener(plugin, config, trade);

            Bukkit.getPluginManager().registerEvents(menu, plugin);

            PluginCommand cmd = plugin.getCommand("neworbs");
            if (cmd != null) cmd.setExecutor(this);
            else plugin.getLogger().warning("[NewOrbs] Comando 'neworbs' não existe no plugin.yml do NewCore.");

            plugin.getLogger().info("[NewOrbs] módulo carregado (via NewCore).");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[NewOrbs] Falha ao registrar módulo", t);
        }
    }

    // helper: primeira orb do YML
    private OrbDefinition firstOrb() {
        LinkedHashMap<String, OrbDefinition> map = config.getOrbs();
        for (OrbDefinition o : map.values()) return o;
        return null;
    }

    // helper: orb pela mão do jogador
    private OrbDefinition orbFromHand(Player p) {
        ItemStack it = p.getItemInHand();
        if (it == null) return null;
        for (OrbDefinition orb : config.getOrbs().values()) {
            try {
                if (it.getTypeId() == orb.id && it.getDurability() == (short) orb.data &&
                        it.hasItemMeta() && it.getItemMeta().hasDisplayName() &&
                        ChatColor.stripColor(it.getItemMeta().getDisplayName()).equalsIgnoreCase(ChatColor.stripColor(orb.name))) {
                    return orb;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if ("reload".equalsIgnoreCase(args.length > 0 ? args[0] : "")) {
                config.reload();
                sender.sendMessage(ChatColor.GREEN + "[NewOrbs] Config recarregada.");
                return true;
            }

            if ("open".equalsIgnoreCase(args.length > 0 ? args[0] : "")) {
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { sender.sendMessage(ChatColor.RED + "Jogador offline: " + args[1]); return true; }
                    if (args.length >= 3) menu.openTradeMenu(target, args[2].toLowerCase());
                    else {
                        OrbDefinition byHand = orbFromHand(target);
                        OrbDefinition first  = byHand != null ? byHand : firstOrb();
                        if (first != null) menu.openTradeMenu(target, first.key);
                    }
                    sender.sendMessage(ChatColor.YELLOW + "Menu aberto para " + target.getName() + ".");
                    return true;
                }
                sender.sendMessage("§cUso: /" + label + " open <jogador> [orbKey]");
                return true;
            }

            // /neworbs sem argumentos -> abre trade da ORB na mão; se não houver, abre a primeira do YML
            if (sender instanceof Player) {
                Player p = (Player) sender;
                OrbDefinition byHand = orbFromHand(p);
                OrbDefinition first  = byHand != null ? byHand : firstOrb();
                if (first != null) {
                    menu.openTradeMenu(p, first.key);
                } else {
                    sender.sendMessage(ChatColor.RED + "[NewOrbs] Nenhuma orb configurada.");
                }
            } else {
                sender.sendMessage("§eUso: /" + label + " reload | open <jogador> [orbKey]");
            }
            return true;
        } catch (Throwable t) {
            sender.sendMessage("§c[NewOrbs] Ocorreu um erro. Verifique o console.");
            plugin.getLogger().log(Level.SEVERE, "[NewOrbs] Erro no comando", t);
            return true;
        }
    }
}
