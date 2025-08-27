// File: src/main/java/n/plugins/NewEssentials/Rename.java
package n.plugins.NewEssentials;

import n.plugins.NewCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class Rename implements CommandExecutor, TabCompleter {

    private final NewCore plugin;
    private final String prefix = ChatColor.translateAlternateColorCodes('&', "&3&lNew&bEssentials &7» ");

    public Rename(NewCore plugin) {
        this.plugin = plugin;
    }

    // ===== Helpers =====
    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
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
        try { return p.getItemInHand(); } catch (Throwable t) { return null; } // 1.7.x
    }

    private boolean isAir(ItemStack it) {
        return it == null || it.getType() == Material.AIR || it.getAmount() <= 0;
    }

    private void updateInv(Player p) {
        try { p.updateInventory(); } catch (Throwable ignored) {} // 1.7.x
    }

    // ===== Commands =====
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + ChatColor.RED + "Apenas jogadores.");
            return true;
        }
        Player p = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if ("rename".equals(cmd)) {
            if (!p.hasPermission("new.rename")) {
                p.sendMessage(prefix + ChatColor.RED + "Sem permissão.");
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(prefix + ChatColor.YELLOW + "Uso: /rename <novo nome com & cores>");
                return true;
            }
            ItemStack it = getInHand(p);
            if (isAir(it)) {
                p.sendMessage(prefix + ChatColor.RED + "Segure um item na mão.");
                return true;
            }
            String name = join(args, 0);
            // opcional: limitar tamanho para evitar nomes absurdos
            if (name.length() > 128) name = name.substring(0, 128);

            ItemMeta meta = it.getItemMeta();
            if (meta == null) {
                p.sendMessage(prefix + ChatColor.RED + "Este item não aceita nome customizado.");
                return true;
            }
            meta.setDisplayName(color(name));
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);
            p.sendMessage(prefix + ChatColor.GREEN + "Nome definido para: " + color(name));
            return true;
        }

        if ("addlore".equals(cmd)) {
            if (!p.hasPermission("new.addlore")) {
                p.sendMessage(prefix + ChatColor.RED + "Sem permissão.");
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(prefix + ChatColor.YELLOW + "Uso: /addlore <texto da lore (use \\n para quebrar linha)>");
                return true;
            }
            ItemStack it = getInHand(p);
            if (isAir(it)) {
                p.sendMessage(prefix + ChatColor.RED + "Segure um item na mão.");
                return true;
            }
            ItemMeta meta = it.getItemMeta();
            if (meta == null) {
                p.sendMessage(prefix + ChatColor.RED + "Este item não aceita lore.");
                return true;
            }
            String text = join(args, 0);

            // Suporta múltiplas linhas usando "\n"
            String[] lines = text.split("\\\\n"); // literal "\n" no chat
            List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
            for (String ln : lines) {
                if (ln == null) continue;
                String colored = color(ln);
                // opcional: limitar tamanho por linha
                if (colored.length() > 256) colored = colored.substring(0, 256);
                lore.add(colored);
            }
            meta.setLore(lore);
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);
            p.sendMessage(prefix + ChatColor.GREEN + "Lore adicionada" + (lines.length > 1 ? " (" + lines.length + " linhas)." : "."));
            return true;
        }

        if ("removelore".equals(cmd)) {
            if (!p.hasPermission("new.removelore")) {
                p.sendMessage(prefix + ChatColor.RED + "Sem permissão.");
                return true;
            }
            ItemStack it = getInHand(p);
            if (isAir(it)) {
                p.sendMessage(prefix + ChatColor.RED + "Segure um item na mão.");
                return true;
            }
            ItemMeta meta = it.getItemMeta();
            if (meta == null || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty()) {
                p.sendMessage(prefix + ChatColor.RED + "Este item não possui lore.");
                return true;
            }
            List<String> lore = new ArrayList<String>(meta.getLore());

            if (args.length == 0) {
                // Mostra as linhas disponíveis
                p.sendMessage(prefix + ChatColor.YELLOW + "Uso: /removelore <linha|all> [outras linhas]");
                for (int i = 0; i < lore.size(); i++) {
                    p.sendMessage(ChatColor.GRAY + String.valueOf(i + 1) + ": " + lore.get(i));
                }
                return true;
            }

            // Suporta "all" para limpar tudo, ou múltiplos números (1-based)
            if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
                lore.clear();
            } else {
                // coletar índices válidos
                TreeSet<Integer> toRemove = new TreeSet<Integer>(Collections.reverseOrder()); // remover do fim p/ não deslocar
                for (String a : args) {
                    try {
                        int idx = Integer.parseInt(a);
                        if (idx >= 1 && idx <= lore.size()) {
                            toRemove.add(idx - 1); // 0-based
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (toRemove.isEmpty()) {
                    p.sendMessage(prefix + ChatColor.RED + "Informe linhas válidas (1.." + lore.size() + ") ou 'all'.");
                    return true;
                }
                for (int idx : toRemove) {
                    lore.remove(idx);
                }
            }

            meta.setLore(lore.isEmpty() ? null : lore);
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);
            p.sendMessage(prefix + ChatColor.GREEN + "Lore atualizada. Linhas restantes: " + (lore == null ? 0 : lore.size()));
            return true;
        }

        // Fallback (não deve acontecer pois só bindamos 3 comandos)
        p.sendMessage(prefix + ChatColor.RED + "Comando inválido.");
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
            if (meta == null || !meta.hasLore() || meta.getLore() == null) return Collections.emptyList();

            List<String> out = new ArrayList<String>();
            if (args.length == 1) {
                out.add("all");
                int n = meta.getLore().size();
                for (int i = 1; i <= n; i++) out.add(String.valueOf(i));
                return filterPrefix(out, args[0]);
            } else if (args.length > 1) {
                // sugere próximos índices que ainda não foram digitados
                java.util.HashSet<String> used = new java.util.HashSet<String>(Arrays.asList(args));
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
        List<String> out = new ArrayList<String>();
        for (String s : list) if (s.toLowerCase().startsWith(low)) out.add(s);
        return out;
    }
}
