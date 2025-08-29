// File: src/main/java/n/plugins/NewEssentials/Rename.java
package n.plugins.NewEssentials;

import n.plugins.NewCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Rename implements CommandExecutor, TabCompleter {

    private final NewCore plugin;

    // usa SEMPRE o YML do resources (copiado pra pasta de dados)
    private File neFile;
    private FileConfiguration neConfig;

    public Rename(NewCore plugin) {
        this.plugin = plugin;
        loadConfigFile();
    }

    private void loadConfigFile() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            neFile = new File(plugin.getDataFolder(), "NewEssentials.yml");
            if (!neFile.exists()) {
                try { plugin.saveResource("NewEssentials.yml", false); } catch (IllegalArgumentException ignored) {}
            }
            neConfig = new YamlConfiguration();
            neConfig.load(neFile);
        } catch (IOException | InvalidConfigurationException e) {
            neConfig = new YamlConfiguration();
        }
    }

    public void reloadConfig() { loadConfigFile(); }

    private String cfgStr(String path, String def) { return color(neConfig.getString(path, def)); }
    private int cfgInt(String path, int def) { return neConfig.getInt(path, def); }
    private boolean cfgBool(String path, boolean def) { return neConfig.getBoolean(path, def); }

    private boolean isEnabled(String section) { return cfgBool(section + ".enabled", true); }
    private String prefix() { return cfgStr("rename.prefix", "&3&lNew&bEssentials &7» "); }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) { if (i > start) sb.append(' '); sb.append(args[i]); }
        return sb.toString();
    }
    private ItemStack getInHand(Player p) { try { return p.getItemInHand(); } catch (Throwable t) { return null; } }
    private boolean isAir(ItemStack it) { return it == null || it.getType() == Material.AIR || it.getAmount() <= 0; }
    private void updateInv(Player p) { try { p.updateInventory(); } catch (Throwable ignored) {} }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(cfgStr("rename.messages.only-players", "&cApenas jogadores.")); return true; }
        Player p = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if ("rename".equals(cmd)) {
            if (!isEnabled("rename")) { p.sendMessage(prefix() + ChatColor.RED + "Função desativada."); return true; }
            if (!p.hasPermission("new.rename")) { p.sendMessage(prefix() + cfgStr("rename.messages.no-permission", "&cSem permissão.")); return true; }
            if (args.length == 0) { p.sendMessage(prefix() + cfgStr("rename.messages.usage", "&eUso: /rename <novo nome>")); return true; }
            ItemStack it = getInHand(p);
            if (isAir(it)) { p.sendMessage(prefix() + cfgStr("rename.messages.no-item", "&cSegure um item na mão.")); return true; }

            String name = join(args, 0);
            int max = cfgInt("rename.max-name-length", 128);
            if (name.length() > max) name = name.substring(0, max);

            ItemMeta meta = it.getItemMeta();
            if (meta == null) { p.sendMessage(prefix() + cfgStr("rename.messages.no-item", "&cEste item não aceita nome.")); return true; }
            meta.setDisplayName(color(name));
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);

            p.sendMessage(prefix() + cfgStr("rename.messages.renamed", "&aNome definido para: &f%name%").replace("%name%", color(name)));
            return true;
        }

        if ("addlore".equals(cmd)) {
            if (!isEnabled("addlore")) { p.sendMessage(prefix() + ChatColor.RED + "Função desativada."); return true; }
            if (!p.hasPermission("new.addlore")) { p.sendMessage(prefix() + cfgStr("addlore.messages.no-permission", "&cSem permissão.")); return true; }
            if (args.length == 0) { p.sendMessage(prefix() + cfgStr("addlore.messages.usage", "&eUso: /addlore <texto> (use \\n para quebrar)")); return true; }

            ItemStack it = getInHand(p);
            if (isAir(it)) { p.sendMessage(prefix() + cfgStr("addlore.messages.no-item", "&cSegure um item na mão.")); return true; }
            ItemMeta meta = it.getItemMeta();
            if (meta == null) { p.sendMessage(prefix() + cfgStr("addlore.messages.no-item", "&cEste item não aceita lore.")); return true; }

            String text = join(args, 0);
            String[] lines = text.split("\\\\n");
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
            int max = cfgInt("rename.max-lore-length", 256);
            for (String ln : lines) {
                String colored = color(ln == null ? "" : ln);
                if (colored.length() > max) colored = colored.substring(0, max);
                lore.add(colored);
            }
            meta.setLore(lore.isEmpty() ? null : lore);
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);

            p.sendMessage(prefix() + cfgStr("addlore.messages.added", "&aLore adicionada."));
            return true;
        }

        if ("removelore".equals(cmd)) {
            if (!isEnabled("removelore")) { p.sendMessage(prefix() + ChatColor.RED + "Função desativada."); return true; }
            if (!p.hasPermission("new.removelore")) { p.sendMessage(prefix() + cfgStr("removelore.messages.no-permission", "&cSem permissão.")); return true; }

            ItemStack it = getInHand(p);
            if (isAir(it)) { p.sendMessage(prefix() + cfgStr("removelore.messages.no-item", "&cSegure um item na mão.")); return true; }
            ItemMeta meta = it.getItemMeta();
            if (meta == null || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty()) {
                p.sendMessage(prefix() + cfgStr("removelore.messages.no-lore", "&cEste item não possui lore."));
                return true;
            }
            List<String> lore = new ArrayList<String>(meta.getLore());

            if (args.length == 0) {
                p.sendMessage(prefix() + cfgStr("removelore.messages.usage", "&eUso: /removelore <linha|all> [outras]"));
                for (int i = 0; i < lore.size(); i++) p.sendMessage(ChatColor.GRAY + String.valueOf(i + 1) + ": " + lore.get(i));
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
                lore.clear();
            } else {
                TreeSet<Integer> toRemove = new TreeSet<Integer>(Collections.reverseOrder());
                for (String a : args) {
                    try {
                        int idx = Integer.parseInt(a);
                        if (idx >= 1 && idx <= lore.size()) toRemove.add(idx - 1);
                    } catch (NumberFormatException ignored) {}
                }
                if (toRemove.isEmpty()) { p.sendMessage(prefix() + ChatColor.RED + "Linhas inválidas."); return true; }
                for (int idx : toRemove) lore.remove(idx);
            }

            meta.setLore(lore.isEmpty() ? null : lore);
            it.setItemMeta(meta);
            p.setItemInHand(it);
            updateInv(p);

            p.sendMessage(prefix() + cfgStr("removelore.messages.removed","&aLore atualizada. Linhas restantes: &f%lines%").replace("%lines%", String.valueOf(lore.size())));
            return true;
        }

        return true;
    }

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
                HashSet<String> used = new HashSet<String>(Arrays.asList(args));
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
