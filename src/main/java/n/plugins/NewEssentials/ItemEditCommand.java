package n.plugins.NewEssentials;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class ItemEditCommand implements CommandExecutor, TabCompleter {

    private final org.bukkit.plugin.Plugin plugin;
    private final FileConfiguration cfg;

    public ItemEditCommand(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig(); // itemedit está no config principal (NewEssentials.yml carregado pelo plugin)
    }

    /* =======================
       Comando /item edit ...
       ======================= */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Aceita: /item edit damage <valor>
        //         /item edit damage add <valor>
        //         /item edit damage clear
        //         /item edit info

        // Checagens básicas
        if (!cfg.getBoolean("itemedit.enabled", true)) {
            msg(sender, path("messages.disabled"));
            return true;
        }

        if (!(sender instanceof Player)) {
            msg(sender, path("messages.only-player"));
            return true;
        }

        Player p = (Player) sender;

        if (needsPerm("permissions.base") && !p.hasPermission(getPerm("permissions.base"))) {
            msg(p, path("messages.no-permission"));
            return true;
        }

        // /item edit ...
        if (args.length < 1 || !args[0].equalsIgnoreCase("edit")) {
            sendUsage(p, label);
            return true;
        }

        if (isBlockedWorld(p) && !p.hasPermission(getPerm("permissions.bypass-world"))) {
            msg(p, path("messages.blocked-world"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(p, label);
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("info")) {
            if (needsPerm("permissions.info") && !p.hasPermission(getPerm("permissions.info"))) {
                msg(p, path("messages.no-permission"));
                return true;
            }
            handleInfo(p);
            return true;
        }

        if (p.getItemInHand() == null || p.getItemInHand().getType() == Material.AIR) {
            msg(p, path("messages.no-item"));
            return true;
        }

        if (isMaterialBlocked(p.getItemInHand().getType()) && !p.hasPermission(getPerm("permissions.bypass-materials"))) {
            msg(p, path("messages.material-blocked"));
            return true;
        }

        if (sub.equals("damage")) {
            if (needsPerm("permissions.damage") && !p.hasPermission(getPerm("permissions.damage"))) {
                msg(p, path("messages.no-permission"));
                return true;
            }
            return handleDamage(p, Arrays.copyOfRange(args, 2, args.length));
        }

        if (sub.equals("clear")) {
            if (needsPerm("permissions.clear") && !p.hasPermission(getPerm("permissions.clear"))) {
                msg(p, path("messages.no-permission"));
                return true;
            }
            return handleClear(p);
        }

        // Futuro: outros "etc" poderiam ser adicionados aqui (name, lore, glow, ench, etc.)
        sendUsage(p, label);
        return true;
    }

    private void sendUsage(Player p, String label) {
        final boolean reqPerm = cfg.getBoolean("itemedit.require-permission", true);
        msg(p, "&7&m--------------------------------");
        msg(p, "&bUso: &f/" + label + " edit &7<subcomando>");

        // /item edit info
        if (!reqPerm || p.hasPermission(getPerm("permissions.info"))) {
            msg(p, "&f/" + label + " edit info &7- Mostra informações do item na mão.");
        }

        // /item edit damage <valor> | add <valor> | clear
        if (!reqPerm || p.hasPermission(getPerm("permissions.damage"))) {
            msg(p, "&f/" + label + " edit damage &b<valor> &7- Define o bônus de dano (soma direta).");
            msg(p, "&f/" + label + " edit damage add &b<valor> &7- Soma ao bônus de dano atual.");
            msg(p, "&f/" + label + " edit damage clear &7- Remove o bônus de dano aplicado.");
        }

        // /item edit clear (alias geral)
        if (!reqPerm || p.hasPermission(getPerm("permissions.clear"))) {
            msg(p, "&f/" + label + " edit clear &7- Alias para remover o bônus de dano.");
        }

        double limit = cfg.getDouble("itemedit.max-damage-add", 20.0);
        if (!p.hasPermission(getPerm("permissions.bypass-limit"))) {
            msg(p, "&7Limite sem bypass: &f" + limit);
        }
        msg(p, "&7&m--------------------------------");
    }


    /* =========================
       /item edit damage ...
       ========================= */
    private boolean handleDamage(Player p, String[] args) {
        // Modos:
        //  - damage <valor>     -> SET (substitui)
        //  - damage add <valor> -> ADD (soma)
        //  - damage clear       -> alias para /item edit clear

        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            return handleClear(p);
        }

        boolean isAdd = false;
        String numStr;

        if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
            isAdd = true;
            numStr = args[1];
        } else if (args.length >= 1) {
            numStr = args[0];
        } else {
            msg(p, "&eUso: &f/item edit damage <valor> &7| &f/item edit damage add <valor> &7| &f/item edit damage clear");
            return true;
        }

        Double value = parseDouble(numStr);
        if (value == null) {
            msg(p, path("messages.invalid-number"));
            return true;
        }

        // Limite
        double limit = cfg.getDouble("itemedit.max-damage-add", 20.0);
        if (!p.hasPermission(getPerm("permissions.bypass-limit")) && Math.abs(value) > limit) {
            msg(p, path("messages.over-limit").replace("%limit%", String.valueOf(limit)));
            return true;
        }

        ItemStack hand = p.getItemInHand();

        // Le bônus atual (se existir) para ADD
        double current = readDamageModifierAmount(hand);
        double target = isAdd ? (current + value) : value;

        // Tenta aplicar por NBT
        boolean nbtOk = false;
        if (cfg.getBoolean("itemedit.use-nbt-attributes", true)) {
            try {
                ItemStack updated = applyDamageModifierNBT(hand, target);
                if (updated != null) {
                    p.setItemInHand(updated);
                    nbtOk = true;
                }
            } catch (Throwable t) {
                // Loga silenciosamente
                plugin.getLogger().warning("[ItemEdit] Falha NBT: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        }

        // Fallback: Sharpness aproximado (1.7: ~1.25 dano por nível)
        if (!nbtOk && cfg.getBoolean("itemedit.fallback-to-sharpness", true)) {
            int lvl = (int) Math.ceil(Math.max(0, target) / 1.25D);
            if (lvl == 0) {
                // remove sharpness
                ItemMeta meta = hand.getItemMeta();
                if (meta != null && meta.hasEnchants()) {
                    meta.removeEnchant(Enchantment.DAMAGE_ALL);
                    hand.setItemMeta(meta);
                }
            } else {
                hand.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, Math.min(lvl, 32767));
            }
            p.setItemInHand(hand);
            msg(p, path("messages.damage-fallback").replace("%amount%", String.valueOf(target)));
            return true;
        }

        // Mensagem final
        if (isAdd) {
            msg(p, path("messages.damage-added")
                    .replace("%amount%", String.valueOf(value))
                    .replace("%total%", String.valueOf(target)));
        } else {
            msg(p, path("messages.damage-set").replace("%amount%", String.valueOf(target)));
        }
        return true;
    }

    /* =========================
       /item edit clear
       ========================= */
    private boolean handleClear(Player p) {
        ItemStack hand = p.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            msg(p, path("messages.no-item"));
            return true;
        }

        boolean changed = false;

        try {
            ItemStack updated = removeOurDamageModifierNBT(hand);
            if (updated != null) {
                p.setItemInHand(updated);
                changed = true;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[ItemEdit] Falha ao limpar NBT: " + t.getMessage());
        }

        // Também remove o Sharpness se tivermos usado fallback
        ItemMeta meta = p.getItemInHand().getItemMeta();
        if (meta != null && meta.hasEnchant(Enchantment.DAMAGE_ALL)) {
            meta.removeEnchant(Enchantment.DAMAGE_ALL);
            p.getItemInHand().setItemMeta(meta);
            changed = true;
        }

        if (changed) {
            msg(p, path("messages.damage-cleared"));
        } else {
            msg(p, path("messages.damage-cleared")); // idempotente
        }
        return true;
    }

    /* =========================
       /item edit info
       ========================= */
    private void handleInfo(Player p) {
        ItemStack hand = p.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            msg(p, path("messages.no-item"));
            return;
        }
        double bonus = readDamageModifierAmount(hand);
        int sharp = hand.getEnchantmentLevel(Enchantment.DAMAGE_ALL);

        msg(p, path("messages.info-header"));
        msg(p, path("messages.info-damage").replace("%amount%", String.valueOf(bonus)));
        msg(p, path("messages.info-sharpness").replace("%lvl%", String.valueOf(sharp)));
    }

    /* =========================
       Helpers de Config/Msgs
       ========================= */
    private String path(String p) {
        return cfg.getString("itemedit." + p, "&c(" + p + " não definido)");
    }

    private boolean needsPerm(String k) {
        return cfg.getBoolean("itemedit.require-permission", true) && cfg.isString("itemedit." + k);
    }

    private String getPerm(String k) {
        return cfg.getString("itemedit." + k, "newess.itemedit");
    }

    private void msg(CommandSender to, String raw) {
        String prefix = cfg.getString("itemedit.messages.prefix", "");
        to.sendMessage(color(prefix + raw));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private boolean isBlockedWorld(Player p) {
        List<String> worlds = cfg.getStringList("itemedit.blocked-worlds");
        return worlds != null && !worlds.isEmpty() && worlds.contains(p.getWorld().getName());
    }

    private boolean isMaterialBlocked(Material m) {
        List<String> list = cfg.getStringList("itemedit.allowed-materials");
        boolean blacklist = cfg.getBoolean("itemedit.materials-blacklist", false);
        if (list == null || list.isEmpty()) return false; // sem restrição
        boolean contains = list.stream().anyMatch(s -> s.equalsIgnoreCase(m.name()));
        return blacklist ? contains : !contains;
    }

    private Double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    /* =========================
       NBT / AttributeModifiers
       ========================= */
    private double readDamageModifierAmount(ItemStack bukkitStack) {
        try {
            Object nms = asNMSCopy(bukkitStack);
            if (nms == null) return 0D;
            Object tag = getTag(nms);
            if (tag == null) return 0D;

            Object list = getList(tag, "AttributeModifiers", 10 /*NBTTagCompound id*/);
            if (list == null) return 0D;

            int size = (int) invoke(list, "size");
            for (int i = 0; i < size; i++) {
                Object comp = invoke(list, "get", i);
                if (comp == null) continue;
                String attr = getString(comp, "AttributeName");
                String name = getString(comp, "Name");
                if ("generic.attackDamage".equals(attr) && isOurName(name)) {
                    return getDouble(comp, "Amount");
                }
            }
        } catch (Throwable ignored) { }
        return 0D;
    }

    private ItemStack applyDamageModifierNBT(ItemStack bukkitStack, double amount) throws Exception {
        Object nms = asNMSCopy(bukkitStack);
        if (nms == null) return null;

        Object tag = getTag(nms);
        if (tag == null) {
            tag = newNBTTagCompound();
        }

        Object list = getList(tag, "AttributeModifiers", 10);
        if (list == null) {
            list = newNBTTagList();
        } else {
            // remove os nossos anteriores
            list = removeOurModifiers(list);
        }

        // cria composto do nosso modificador
        Object mod = newNBTTagCompound();
        setString(mod, "AttributeName", "generic.attackDamage");
        setString(mod, "Name", getDamageModifierName());
        setDouble(mod, "Amount", amount);
        setInt(mod, "Operation", 0); // 0 = soma direta

        long most = cfg.getLong("itemedit.damage-modifier-uuid-most", 123456789L);
        long least = cfg.getLong("itemedit.damage-modifier-uuid-least", 987654321L);
        setLong(mod, "UUIDMost", most);
        setLong(mod, "UUIDLeast", least);

        // adiciona no list
        invoke(list, "add", mod);

        // escreve no tag e item
        set(tag, "AttributeModifiers", list);
        setTag(nms, tag);

        // retorna bukkit
        return asBukkitCopy(nms);
    }

    private ItemStack removeOurDamageModifierNBT(ItemStack bukkitStack) throws Exception {
        Object nms = asNMSCopy(bukkitStack);
        if (nms == null) return null;

        Object tag = getTag(nms);
        if (tag == null) return bukkitStack;

        Object list = getList(tag, "AttributeModifiers", 10);
        if (list == null) return bukkitStack;

        Object cleaned = removeOurModifiers(list);
        set(tag, "AttributeModifiers", cleaned);
        setTag(nms, tag);
        return asBukkitCopy(nms);
    }

    private boolean isOurName(String name) {
        return name != null && name.equalsIgnoreCase(getDamageModifierName());
    }

    private String getDamageModifierName() {
        return cfg.getString("itemedit.damage-modifier-name", "NewEssentials-Damage");
    }

    private Object removeOurModifiers(Object list) throws Exception {
        int size = (int) invoke(list, "size");
        Object newList = newNBTTagList();
        for (int i = 0; i < size; i++) {
            Object comp = invoke(list, "get", i);
            if (comp == null) continue;
            String attr = getString(comp, "AttributeName");
            String name = getString(comp, "Name");
            if ("generic.attackDamage".equals(attr) && isOurName(name)) {
                // skip (remover)
                continue;
            }
            invoke(newList, "add", comp);
        }
        return newList;
    }

    /* =========================
       Reflection util (1.7/1.8)
       ========================= */
    private String getCBVersion() {
        String pkg = Bukkit.getServer().getClass().getPackage().getName();
        // org.bukkit.craftbukkit.v1_7_R4
        return pkg.substring(pkg.lastIndexOf('.') + 1);
    }

    private Class<?> nms(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + getCBVersion() + "." + name);
    }

    private Class<?> cb(String name) throws ClassNotFoundException {
        return Class.forName("org.bukkit.craftbukkit." + getCBVersion() + "." + name);
    }

    private Object asNMSCopy(ItemStack stack) throws Exception {
        Class<?> craftItem = cb("inventory.CraftItemStack");
        Method m = craftItem.getMethod("asNMSCopy", ItemStack.class);
        return m.invoke(null, stack);
    }

    private ItemStack asBukkitCopy(Object nmsItem) throws Exception {
        Class<?> craftItem = cb("inventory.CraftItemStack");
        Method m = craftItem.getMethod("asBukkitCopy", nms("ItemStack"));
        return (ItemStack) m.invoke(null, nmsItem);
    }

    private Object getTag(Object nmsItem) throws Exception {
        Method getTag = nmsItem.getClass().getMethod("getTag");
        return getTag.invoke(nmsItem);
    }

    private void setTag(Object nmsItem, Object tag) throws Exception {
        Method setTag = nmsItem.getClass().getMethod("setTag", nms("NBTTagCompound"));
        setTag.invoke(nmsItem, tag);
    }

    private Object newNBTTagCompound() throws Exception {
        return nms("NBTTagCompound").newInstance();
    }

    private Object newNBTTagList() throws Exception {
        return nms("NBTTagList").newInstance();
    }

    private Object getList(Object tag, String key, int typeId) throws Exception {
        try {
            // getList(String, int)
            Method m = tag.getClass().getMethod("getList", String.class, int.class);
            return m.invoke(tag, key, typeId);
        } catch (NoSuchMethodException e) {
            // 1.7 tem getTagList(String, int)
            Method m = tag.getClass().getMethod("getTagList", String.class, int.class);
            return m.invoke(tag, key, typeId);
        }
    }

    private void set(Object tag, String key, Object nbtBase) throws Exception {
        Method m = tag.getClass().getMethod("set", String.class, nms("NBTBase"));
        m.invoke(tag, key, nbtBase);
    }

    private void setString(Object comp, String key, String val) throws Exception {
        Method m = comp.getClass().getMethod("setString", String.class, String.class);
        m.invoke(comp, key, val);
    }

    private void setDouble(Object comp, String key, double val) throws Exception {
        Method m = comp.getClass().getMethod("setDouble", String.class, double.class);
        m.invoke(comp, key, val);
    }

    private void setInt(Object comp, String key, int val) throws Exception {
        Method m = comp.getClass().getMethod("setInt", String.class, int.class);
        m.invoke(comp, key, val);
    }

    private void setLong(Object comp, String key, long val) throws Exception {
        Method m = comp.getClass().getMethod("setLong", String.class, long.class);
        m.invoke(comp, key, val);
    }

    private String getString(Object comp, String key) throws Exception {
        Method m = comp.getClass().getMethod("getString", String.class);
        return (String) m.invoke(comp, key);
    }

    private double getDouble(Object comp, String key) throws Exception {
        Method m = comp.getClass().getMethod("getDouble", String.class);
        return (double) m.invoke(comp, key);
    }

    private Object invoke(Object obj, String method, Object... args) throws Exception {
        for (Method m : obj.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterTypes().length == args.length) {
                return m.invoke(obj, args);
            }
        }
        throw new NoSuchMethodException(method);
    }

    /* =========================
       Tab Complete
       ========================= */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return startsWith(args[0], Arrays.asList("edit"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            return startsWith(args[1], Arrays.asList("damage", "info", "clear"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("edit") && args[1].equalsIgnoreCase("damage")) {
            return startsWith(args[2], Arrays.asList("add", "clear"));
        }
        return Collections.emptyList();
    }

    private List<String> startsWith(String token, List<String> options) {
        final String t = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t))
                .collect(Collectors.toList());
    }

    /* =========================
       Registro (chamar no onEnable)
       =========================
       Exemplo no seu onEnable():
           getCommand("item").setExecutor(new ItemEditCommand(this));
           getCommand("item").setTabCompleter(new ItemEditCommand(this));
       E no plugin.yml:
           commands:
             item:
               description: "Editar itens (NewEssentials)"
               usage: "/item edit ..."
               aliases: [i, itemedit]
     */
}
