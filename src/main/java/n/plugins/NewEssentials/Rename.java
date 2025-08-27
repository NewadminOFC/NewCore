package n.plugins.NewEssentials;

import n.plugins.NewCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class Rename implements CommandExecutor, TabCompleter {

    private final NewCore plugin;
    private FileConfiguration config;

    public Rename(NewCore plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    // ===== Config =====
    public void reloadConfig() {
        try {
            plugin.saveResource("NewEssentials.yml", false);
        } catch (Exception ignored) {}
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    private String getCfg(String path) {
        String s = config.getString(path, "");
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    private boolean isEnabled(String section) {
        return config.getBoolean(section + ".enabled", true);
    }

    private String prefix() {
        return getCfg("rename.prefix");
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private ItemStack getInHand(Player p) {
        try { return p.getItemInHand(); } catch (Throwable t) { return null; }
    }

    private boolean isAir(ItemStack it) {
        return it == null || it.getType() == Material.AIR || it.getAmount() <= 0;
    }

    private void updateInv(Player p) {
        try { p.updateInventory(); } catch (Throwable ignored) {}
    }

    // ===== Commands =====
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getCfg("rename.messages.only-players"));
            return true;
        }
        Player p = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if ("rename".equals(cmd)) {
            if (!isEnabled("rename")) {
                p.sendMessage(prefix() + ChatColor.RED + "Função desativada.");
                return true;
            }
            if (!p.hasPermission("new.rename")) {
                p.sendMessage(prefix() + getCfg("rename.messages.no-permission"));
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(prefix() + getCfg("rename.messages.usage"));
                return true;
            }
            ItemStack it = getInHand(p);
            if (isAir(it)) {
                p.sendMessage(prefix() + getCfg("rename.messages.no-item"));
                return true;
            }
            String name = join(args, 0);
            int max = getInt("rename.max-name-length", 128);
            if (name.length() > max) name = name.substring(0, max);

            ItemMeta meta = it.getItemMeta();
            if (meta == null) {
                p.sendMessage(prefix() + getCfg("rename.messages.no-item"));
                return true;
            }
            meta.setDisplayName(color(name));
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);

            p.sendMessage(prefix() + getCfg("rename.messages.renamed").replace("%name%", color(name)));
            return true;
        }

        if ("addlore".equals(cmd)) {
            if (!isEnabled("addlore")) {
                p.sendMessage(prefix() + ChatColor.RED + "Função desativada.");
                return true;
            }
            if (!p.hasPermission("new.addlore")) {
                p.sendMessage(prefix() + getCfg("addlore.messages.no-permission"));
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(prefix() + getCfg("addlore.messages.usage"));
                return true;
            }
            ItemStack it = getInHand(p);
            if (isAir(it)) {
                p.sendMessage(prefix() + getCfg("addlore.messages.no-item"));
                return true;
            }
            ItemMeta meta = it.getItemMeta();
            if (meta == null) {
                p.sendMessage(prefix() + getCfg("addlore.messages.no-item"));
                return true;
            }
            String text = join(args, 0);
            String[] lines = text.split("\\\\n"); // suporta "\n" no chat
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            int max = getInt("rename.max-lore-length", 256);
            for (String ln : lines) {
                if (ln == null) continue;
                String colored = color(ln);
                if (colored.length() > max) colored = colored.substring(0, max);
                lore.add(colored);
            }
            meta.setLore(lore);
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);

            p.sendMessage(prefix() + getCfg("addlore.messages.added"));
            return true;
        }

        if ("removelore".equals(cmd)) {
            if (!isEnabled("removelore")) {
                p.sendMessage(prefix() + ChatColor.RED + "Função desativada.");
                return true;
            }
            if (!p.hasPermission("new.removelore")) {
                p.sendMessage(prefix() + getCfg("removelore.messages.no-permission"));
                return true;
            }
            ItemStack it = getInHand(p);
            if (isAir(it)) {
                p.sendMessage(prefix() + getCfg("removelore.messages.no-item"));
                return true;
            }
            ItemMeta meta = it.getItemMeta();
            if (meta == null || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty()) {
                p.sendMessage(prefix() + getCfg("removelore.messages.no-lore"));
                return true;
            }
            List<String> lore = new ArrayList<>(meta.getLore());

            if (args.length == 0) {
                p.sendMessage(prefix() + getCfg("removelore.messages.usage"));
                for (int i = 0; i < lore.size(); i++) {
                    p.sendMessage(ChatColor.GRAY + String.valueOf(i + 1) + ": " + lore.get(i));
                }
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
                lore.clear();
            } else {
                TreeSet<Integer> toRemove = new TreeSet<>(Collections.reverseOrder());
                for (String a : args) {
                    try {
                        int idx = Integer.parseInt(a);
                        if (idx >= 1 && idx <= lore.size()) {
                            toRemove.add(idx - 1);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (toRemove.isEmpty()) {
                    p.sendMessage(prefix() + ChatColor.RED + "Linhas inválidas.");
                    return true;
                }
                for (int idx : toRemove) lore.remove(idx);
            }

            meta.setLore(lore.isEmpty() ? null : lore);
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);

            p.sendMessage(prefix() + getCfg("removelore.messages.removed")
                    .replace("%lines%", String.valueOf(lore.size())));
            return true;
        }

        return true;
    }

    // ===== TabCompleter =====
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player p = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if ("removelore".equals(cmd)) {
            ItemStack it = getInHand(p);
            if (isAir(it)) return Collections.emptyList();
            ItemMeta meta = it.getItemMeta();
            if (meta == null || !meta.hasLore()) return Collections.emptyList();

            List<String> out = new ArrayList<>();
            if (args.length == 1) {
                out.add("all");
                int n = meta.getLore().size();
                for (int i = 1; i <= n; i++) out.add(String.valueOf(i));
                return filterPrefix(out, args[0]);
            } else if (args.length > 1) {
                HashSet<String> used = new HashSet<>(Arrays.asList(args));
                int n = meta.getLore().size();
                for (int i = 1; i <= n; i++) {
                    String s = String.valueOf(i);
                    if (!used.contains(s)) out.add(s);
                }
                return out;
            }
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        if (prefix == null || prefix.isEmpty()) return list;
        String low = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase().startsWith(low)) out.add(s);
        return out;
    }
}
