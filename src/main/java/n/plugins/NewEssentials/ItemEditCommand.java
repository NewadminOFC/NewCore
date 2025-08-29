package n.plugins.NewEssentials;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.v1_7_R4.NBTBase;
import net.minecraft.server.v1_7_R4.NBTTagCompound;
import net.minecraft.server.v1_7_R4.NBTTagDouble;
import net.minecraft.server.v1_7_R4.NBTTagInt;
import net.minecraft.server.v1_7_R4.NBTTagList;
import net.minecraft.server.v1_7_R4.NBTTagLong;
import net.minecraft.server.v1_7_R4.NBTTagString;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.craftbukkit.v1_7_R4.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * /item edit <subcomando>
 * Implementa "tudo" do Itemizer adaptado ao namespace /item edit ...
 *
 * Suporte: Spigot/Paper 1.7.10 (v1_7_R4)
 *
 * plugin.yml (exemplo):
 * commands:
 *   item:
 *     description: Edição de itens
 *     usage: "/item edit <subcomando>"
 * permissions:
 *   item.edit.*:
 *     description: Acesso total
 *     default: op
 *     children:
 *       item.edit.name: true
 *       item.edit.lore: true
 *       item.edit.advlore: true
 *       item.edit.potion: true
 *       item.edit.attribute: true
 *       item.edit.title: true
 *       item.edit.author: true
 *       item.edit.head: true
 *       item.edit.clear: true
 *       item.edit.damage: true
 */
public class ItemEditCommand implements CommandExecutor, TabCompleter {

    // Índices usados em clear/clearall (compat com Itemizer)
    public static final int NAME = 0;
    public static final int LORE = 1;
    public static final int SKULL_OWNER = 2;
    public static final int TITLE = 3;
    public static final int AUTHOR = 4;
    public static final int PAGES = 5;
    public static final int COLOR = 6;

    private final JavaPlugin plugin;

    public ItemEditCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Registre no seu onEnable():
    // ItemEditCommand cmd = new ItemEditCommand(this);
    // getCommand("item").setExecutor(cmd);
    // getCommand("item").setTabCompleter(cmd);

    // ===== HELP DESCRIPTORS =====
    private final CmdDesc[] helpRoot = new CmdDesc[] {
            new CmdDesc("/item edit help", "Mostra este menu", null),
            new CmdDesc("/item edit name <nome>", "Renomeia o item", "item.edit.name"),
            new CmdDesc("/item edit lore <lore>", "Define o lore do item (use \n para quebrar)", "item.edit.lore"),
            new CmdDesc("/item edit advlore", "Comandos avançados de lore", "item.edit.advlore"),
            new CmdDesc("/item edit potion", "Comandos de poção (add/remove/list)", "item.edit.potion"),
            new CmdDesc("/item edit attr", "Atributos NBT (add/remove/list/listall)", "item.edit.attribute"),
            new CmdDesc("/item edit damage <valor>", "Define ataque (generic.attackDamage)", "item.edit.damage"),
            new CmdDesc("/item edit title <título>", "Define título do livro assinado", "item.edit.title"),
            new CmdDesc("/item edit author <autor>", "Define autor do livro assinado", "item.edit.author"),
            new CmdDesc("/item edit head <nick>", "Define dono da cabeça de player", "item.edit.head"),
            new CmdDesc("/item edit clear <tipos...>", "Limpa metadados específicos", "item.edit.clear"),
            new CmdDesc("/item edit clearall", "Limpa todos metadados", "item.edit.clear")
    };

    private final CmdDesc[] advLoreHelp = new CmdDesc[] {
            new CmdDesc("/item edit advlore help", "Mostra este menu", null),
            new CmdDesc("/item edit advlore add <lore>", "Adiciona linha(s) de lore", "item.edit.advlore"),
            new CmdDesc("/item edit advlore remove [index]", "Remove linha do lore (padrão 1)", "item.edit.advlore"),
            new CmdDesc("/item edit advlore change <index> <texto>", "Troca lore na posição", "item.edit.advlore")
    };

    private final CmdDesc[] attrHelp = new CmdDesc[] {
            new CmdDesc("/item edit attr help", "Mostra este menu", null),
            new CmdDesc("/item edit attr add <nome> <tipo> <força> [op]", "Adiciona atributo", "item.edit.attribute"),
            new CmdDesc("/item edit attr remove <nome>", "Remove atributo por nome", "item.edit.attribute"),
            new CmdDesc("/item edit attr list", "Lista atributos do item", "item.edit.attribute"),
            new CmdDesc("/item edit attr listall", "Lista tipos suportados", "item.edit.attribute")
    };

    private final CmdDesc[] potionHelp = new CmdDesc[] {
            new CmdDesc("/item edit potion help", "Mostra este menu", null),
            new CmdDesc("/item edit potion add <efeito> [nível] <seg>", "Adiciona efeito custom no frasco", "item.edit.potion"),
            new CmdDesc("/item edit potion remove <efeito>", "Remove efeito custom", "item.edit.potion"),
            new CmdDesc("/item edit potion list", "Lista efeitos válidos", "item.edit.potion")
    };

    // ===== Comando principal =====
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("item")) return false;

        if (args.length == 0 || !args[0].equalsIgnoreCase("edit")) {
            return usage(sender);
        }

        if (args.length == 1) {
            return usage(sender);
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("help")) {
            return helpCmd(sender, bumpArgs(args), helpRoot, "Item Edit Help");
        }

        if (!(sender instanceof Player)) {
            return msg(sender, ChatColor.RED + "Este comando só pode ser usado in-game.");
        }
        Player player = (Player) sender;

        switch (sub) {
            case "name":
                if (noPerm(sender, "item.edit.name")) return true;
                if (args.length < 3) return msg(sender, ChatColor.RED + "Uso: /" + label + " edit name <nome>");
                return setName(player, col(join(args, 2)));

            case "lore":
                if (noPerm(sender, "item.edit.lore")) return true;
                if (args.length < 3) return msg(sender, ChatColor.RED + "Uso: /" + label + " edit lore <texto> (use \n)");
                return setLore(player, col(join(args, 2)));

            case "advlore":
                if (noPerm(sender, "item.edit.advlore")) return true;
                return advLoreCmd(sender, args);

            case "potion":
                if (noPerm(sender, "item.edit.potion")) return true;
                return potionCmd(sender, args);

            case "attr":
                if (noPerm(sender, "item.edit.attribute")) return true;
                return attrCmd(sender, args);

            case "damage":
                if (noPerm(sender, "item.edit.damage")) return true;
                if (args.length < 3) return msg(sender, ChatColor.RED + "Uso: /" + label + " edit damage <valor>");
                double amount;
                try { amount = Double.parseDouble(args[2]); } catch (NumberFormatException e) { return msg(sender, ChatColor.RED + "\"" + args[2] + "\" não é número."); }
                return setAttackDamage(player, amount);

            case "title":
                if (noPerm(sender, "item.edit.title")) return true;
                return bookCmd(sender, args, false);

            case "author":
                if (noPerm(sender, "item.edit.author")) return true;
                return bookCmd(sender, args, true);

            case "head":
                if (noPerm(sender, "item.edit.head")) return true;
                return headCmd(sender, args);

            case "clear":
                if (noPerm(sender, "item.edit.clear")) return true;
                return clearCmd(sender, args);

            case "clearall":
                if (noPerm(sender, "item.edit.clear")) return true;
                return clearAllCmd(sender, args);

            default:
                return msg(sender, ChatColor.RED + "Subcomando desconhecido: " + sub);
        }
    }

    // ====== NAME / LORE ======
    private boolean setName(Player p, String name) {
        ItemStack item = p.getItemInHand();
        if (item == null || item.getType() == Material.AIR) return msg(p, ChatColor.RED + "Segure um item na mão.");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        p.setItemInHand(item);
        return msg(p, ChatColor.GREEN + "Nome definido: " + ChatColor.RESET + name);
    }

    private boolean setLore(Player p, String text) {
        ItemStack item = p.getItemInHand();
        if (item == null || item.getType() == Material.AIR) return msg(p, ChatColor.RED + "Segure um item na mão.");
        displayAction(item, text, 1);
        p.setItemInHand(item);
        return msg(p, ChatColor.GREEN + "Lore atualizado.");
    }

    // ====== DAMAGE (separado) ======
    /**
     * Define (ou substitui) o atributo generic.attackDamage com operação 0 (ADD_NUMBER).
     */
    private boolean setAttackDamage(Player p, double amount) {
        ItemStack item = p.getItemInHand();
        if (item == null || item.getType() == Material.AIR) return msg(p, ChatColor.RED + "Segure um item na mão.");

        net.minecraft.server.v1_7_R4.ItemStack nms = CraftItemStack.asNMSCopy(item);
        if (nms.tag == null) nms.tag = new NBTTagCompound();

        // remove mods existentes de attackDamage
        NBTTagList list = getAttrList(nms);
        NBTTagList novo = new NBTTagList();
        for (int i = 0; i < list.size(); i++) {
            NBTTagCompound c = list.get(i);
            String attrName = c.getString("AttributeName");
            if (!"generic.attackDamage".equalsIgnoreCase(attrName)) {
                novo.add(c);
            }
        }

        // cria novo modificador
        NBTTagCompound tag = new NBTTagCompound();
        tag.set("Name", new NBTTagString("item_edit_damage"));
        tag.set("AttributeName", new NBTTagString("generic.attackDamage"));
        tag.set("Amount", new NBTTagDouble(amount));
        tag.set("Operation", new NBTTagInt(0)); // ADD_NUMBER
        UUID id = UUID.randomUUID();
        tag.set("UUIDMost", new NBTTagLong(id.getMostSignificantBits()));
        tag.set("UUIDLeast", new NBTTagLong(id.getLeastSignificantBits()));

        novo.add(tag);
        nms.tag.set("AttributeModifiers", novo);

        ItemStack craft = CraftItemStack.asCraftMirror(nms);
        p.setItemInHand(craft);
        return msg(p, ChatColor.GREEN + "Dano definido para " + amount + ".");
    }

    // ====== ADV LORE ======
    private boolean advLoreCmd(CommandSender sender, String[] args2) {
        String[] args = bumpArgs(args2);
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("help")) {
                return helpCmd(sender, args, advLoreHelp, "Adv. Lore Help");
            } else if (args[1].equalsIgnoreCase("add")) {
                return advLoreAddCmd(sender, args);
            } else if (args[1].equalsIgnoreCase("remove")) {
                return advLoreRemoveCmd(sender, args);
            } else if (args[1].equalsIgnoreCase("change")) {
                return advLoreChangeCmd(sender, args);
            }
        }
        return helpCmd(sender, args, advLoreHelp, "Adv. Lore Help");
    }

    private boolean advLoreAddCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        Player player = (Player) sender;
        if (args.length <= 2) return usage(sender, "item edit advlore add <lore>");
        ItemStack item = player.getItemInHand();
        if (item == null) return msg(sender, ChatColor.RED + "Segure um item.");
        ItemMeta im = item.getItemMeta();
        List<String> lore = im.hasLore() ? im.getLore() : new ArrayList<String>();
        String name = join(args, 2);
        String[] lines = name.split("\n");
        for (String line : lines) lore.add(ChatColor.translateAlternateColorCodes('&', line));
        im.setLore(lore);
        item.setItemMeta(im);
        player.setItemInHand(item);
        sender.sendMessage(ChatColor.GREEN + "Linha(s) adicionada(s) ao lore!");
        return true;
    }

    private boolean advLoreChangeCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        if (args.length <= 3) return usage(sender, "item edit advlore change <index> <lore>");
        Player player = (Player) sender;
        int index;
        try { index = Integer.parseInt(args[2]); } catch (NumberFormatException nfe) { return msg(sender, ChatColor.RED + "\"" + args[2] + "\" não é número."); }
        ItemStack item = player.getItemInHand();
        if (item == null || !item.hasItemMeta()) return msg(sender, ChatColor.RED + "Este item não tem lore!");
        ItemMeta im = item.getItemMeta();
        if (!im.hasLore()) return msg(sender, ChatColor.RED + "Este item não tem lore!");
        String name = join(args, 3);
        List<String> lore = im.getLore();
        int tindex = index - 1;
        if (tindex < 0 || tindex >= lore.size()) return msg(sender, ChatColor.RED + "Não há lore no índice " + index);
        String[] lines = name.split("\n");
        lore.set(tindex, lines[0]);
        for (int j = 1; j < lines.length; j++) lore.add(tindex + j, ChatColor.translateAlternateColorCodes('&', lines[j]));
        im.setLore(lore);
        item.setItemMeta(im);
        player.setItemInHand(item);
        sender.sendMessage(ChatColor.GREEN + "Lore atualizado.");
        return true;
    }

    private boolean advLoreRemoveCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        Player player = (Player) sender;
        int index = 1;
        if (args.length >= 3) {
            try { index = Integer.parseInt(args[2]); } catch (NumberFormatException nfe) { return msg(sender, ChatColor.RED + "\"" + args[2] + "\" não é número."); }
        }
        ItemStack item = player.getItemInHand();
        if (item == null || !item.hasItemMeta()) return msg(sender, ChatColor.RED + "Este item não tem lore!");
        ItemMeta im = item.getItemMeta();
        if (!im.hasLore()) return msg(sender, ChatColor.RED + "Este item não tem lore!");
        List<String> lore = im.getLore();
        int tindex = index - 1;
        if (tindex < 0 || tindex >= lore.size()) return msg(sender, ChatColor.RED + "Não há lore no índice " + index);
        lore.remove(tindex);
        im.setLore(lore);
        item.setItemMeta(im);
        player.setItemInHand(item);
        player.sendMessage(ChatColor.GREEN + "Lore removido no índice " + index + ".");
        return true;
    }

    // ====== ATTRIBUTE ======
    private boolean attrCmd(CommandSender sender, String[] args2) {
        String[] args = bumpArgs(args2);
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("help")) {
                return helpCmd(sender, args, attrHelp, "Attribute Help");
            } else if (args[1].equalsIgnoreCase("add")) {
                return attrAddCmd(sender, args);
            } else if (args[1].equalsIgnoreCase("remove")) {
                return attrRemoveCmd(sender, args);
            } else if (args[1].equalsIgnoreCase("list")) {
                return attrListCmd(sender, args);
            } else if (args[1].equalsIgnoreCase("listall")) {
                return attrListAllCmd(sender, args);
            }
        }
        return helpCmd(sender, args, attrHelp, "Attribute Help");
    }

    private boolean attrAddCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        if (args.length < 5) return usage(sender, "item edit attr add <nome> <tipo> <força> [add|addmult|mult]");
        Player player = (Player) sender;
        double amount;
        int op = -1;
        if (args.length == 6) {
            String raw = args[5].toLowerCase();
            if (raw.equals("add")) op = 0; else if (raw.equals("addmult") || raw.equals("addmultiplier")) op = 1; else if (raw.equals("mult") || raw.equals("multiplier")) op = 2; else return msg(sender, ChatColor.RED + args[5] + " não é uma operação válida.");
        }
        Attributes a = Attributes.get(args[3]);
        if (a == null) return msg(sender, ChatColor.RED + "\"" + args[3] + "\" não é um tipo de atributo válido.");
        try { amount = Double.parseDouble(args[4]); } catch (NumberFormatException nfe) { return msg(sender, ChatColor.RED + "\"" + args[4] + "\" não é número."); }

        net.minecraft.server.v1_7_R4.ItemStack nms = CraftItemStack.asNMSCopy(player.getItemInHand());
        NBTTagList attrmod = getAttrList(nms);
        for (int i = 0; i < attrmod.size(); i++) {
            NBTTagCompound c = attrmod.get(i);
            if (c.getString("Name").equals(args[2])) return msg(sender, ChatColor.RED + "Já existe atributo com o nome \"" + args[2] + "\"!");
        }
        NBTTagCompound c = new NBTTagCompound();
        c.set("Name", (NBTBase) new NBTTagString(args[2]));
        c.set("AttributeName", (NBTBase) new NBTTagString(a.name));
        c.set("Amount", (NBTBase) new NBTTagDouble(amount));
        if (op == -1) op = a.op;
        c.set("Operation", (NBTBase) new NBTTagInt(op));
        UUID id = UUID.randomUUID();
        c.set("UUIDMost", (NBTBase) new NBTTagLong(id.getMostSignificantBits()));
        c.set("UUIDLeast", (NBTBase) new NBTTagLong(id.getLeastSignificantBits()));
        attrmod.add((NBTBase) c);
        nms.tag.set("AttributeModifiers", (NBTBase) attrmod);
        ItemStack craft = CraftItemStack.asCraftMirror(nms);
        player.setItemInHand(craft);
        player.sendMessage(ChatColor.GREEN + "Atributo adicionado!");
        return true;
    }

    private boolean attrRemoveCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        if (args.length != 3) return usage(sender, "item edit attr remove <nome>");
        Player player = (Player) sender;
        net.minecraft.server.v1_7_R4.ItemStack nms = CraftItemStack.asNMSCopy(player.getItemInHand());
        NBTTagList attrmod = getAttrList(nms);
        NBTTagList novo = new NBTTagList();
        boolean removed = false;
        for (int i = 0; i < attrmod.size(); i++) {
            NBTTagCompound c = attrmod.get(i);
            if (!c.getString("Name").equals(args[2])) {
                novo.add((NBTBase) attrmod.get(i));
            } else {
                removed = true;
            }
        }
        if (!removed) return msg(sender, ChatColor.RED + "Atributo \"" + args[2] + "\" não existe!");
        nms.tag.set("AttributeModifiers", (NBTBase) novo);
        ItemStack craft = CraftItemStack.asCraftMirror(nms);
        player.setItemInHand(craft);
        player.sendMessage(ChatColor.GREEN + "Atributo removido!");
        return true;
    }

    private boolean attrListCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        if (args.length != 2 && args.length != 3) return usage(sender, "item edit attr list");
        Player player = (Player) sender;
        net.minecraft.server.v1_7_R4.ItemStack nms = CraftItemStack.asNMSCopy(player.getItemInHand());
        NBTTagList attrmod = getAttrList(nms);
        if (attrmod.size() == 0) return msg(sender, ChatColor.YELLOW + "Este item não possui atributos.");
        player.sendMessage(ChatColor.GREEN + "Atributos do Item:");
        for (int i = 0; i < attrmod.size(); i++) {
            NBTTagCompound c = attrmod.get(i);
            player.sendMessage(ChatColor.YELLOW + c.getString("Name") + ": " + Attributes.getByMCName(c.getString("AttributeName")) + "," + c.getDouble("Amount"));
        }
        return true;
    }

    private boolean attrListAllCmd(CommandSender sender, String[] args) {
        StringBuilder sb = new StringBuilder();
        for (Attributes s : Attributes.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        sender.sendMessage(ChatColor.GREEN + "Tipos suportados:");
        sender.sendMessage(ChatColor.YELLOW + sb.toString());
        return true;
    }

    // ====== POTION ======
    private boolean potionCmd(CommandSender sender, String[] args2) {
        String[] args = bumpArgs(args2);
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("help")) {
                return helpCmd(sender, args, potionHelp, "Potion Help");
            } else if (args[1].equalsIgnoreCase("add")) {
                return potionAddCmd(sender, args);
            } else if (args[1].equalsIgnoreCase("remove")) {
                return potionRemoveCmd(sender, args);
            } else if (args[1].equalsIgnoreCase("list")) {
                return potionListCmd(sender, args);
            }
        }
        return helpCmd(sender, args, potionHelp, "Potion Help");
    }

    private boolean potionAddCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        Player player = (Player) sender;
        if (args.length != 4 && args.length != 5) return usage(sender, "item edit potion add <efeito> [nível] <segundos>");
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.POTION) return msg(sender, ChatColor.RED + "Segure um frasco de poção.");
        try {
            PotionEffect pot;
            if (args.length == 4) {
                pot = parsePotionEffect(args[2], args[3], null);
            } else {
                pot = parsePotionEffect(args[2], args[4], args[3]);
            }
            PotionMeta pm = (PotionMeta) item.getItemMeta();
            if (pm.hasCustomEffect(pot.getType())) return msg(sender, ChatColor.RED + "Esta poção já possui " + pot.getType().getName() + ".");
            pm.addCustomEffect(pot, false);
            item.setItemMeta(pm);
            player.setItemInHand(item);
            player.sendMessage(ChatColor.GREEN + pot.getType().getName() + " adicionado à poção.");
        } catch (IllegalArgumentException iae) {
            sender.sendMessage(ChatColor.RED + iae.getMessage());
        }
        return true;
    }

    private boolean potionRemoveCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        if (args.length != 3) return usage(sender, "item edit potion remove <efeito>");
        Player player = (Player) sender;
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.POTION) return msg(sender, ChatColor.RED + "Segure um frasco de poção.");
        PotionEffectType t = PotionEffectType.getByName(args[2].toUpperCase());
        if (t == null) return msg(sender, ChatColor.RED + "Efeito inválido: " + args[2]);
        PotionMeta pm = (PotionMeta) item.getItemMeta();
        if (!pm.hasCustomEffect(t)) return msg(sender, ChatColor.RED + "Esta poção não possui " + t.getName() + ".");
        pm.removeCustomEffect(t);
        item.setItemMeta(pm);
        player.setItemInHand(item);
        player.sendMessage(ChatColor.GREEN + t.getName() + " removido da poção.");
        return true;
    }

    private boolean potionListCmd(CommandSender sender, String[] args) {
        StringBuilder sb = new StringBuilder();
        ArrayList<String> n = new ArrayList<String>();
        for (PotionEffectType e : PotionEffectType.values()) {
            if (e != null) n.add(e.getName().toLowerCase());
        }
        Collections.sort(n);
        boolean first = true;
        for (String s : n) {
            if (first) { sb.append(s); first = false; } else { sb.append(", ").append(s); }
        }
        sender.sendMessage(sb.toString());
        return true;
    }

    private PotionEffect parsePotionEffect(String effect, String seconds, String level) throws IllegalArgumentException {
        PotionEffectType t;
        int sec;
        if (effect.equalsIgnoreCase("strength")) {
            t = PotionEffectType.INCREASE_DAMAGE;
        } else if (effect.equalsIgnoreCase("health")) {
            t = PotionEffectType.HEAL;
        } else {
            t = PotionEffectType.getByName(effect.toUpperCase());
            if (t == null) throw new IllegalArgumentException("Efeito não existe: " + effect);
        }
        int lvl = 1;
        try { sec = Integer.parseInt(seconds); } catch (NumberFormatException nfe) { throw new IllegalArgumentException("\"" + seconds + "\" não é número."); }
        if (sec <= 0) throw new IllegalArgumentException("Segundos devem ser positivos.");
        if (level != null) {
            try { lvl = Integer.parseInt(level); } catch (NumberFormatException nfe) { throw new IllegalArgumentException("\"" + level + "\" não é número."); }
        }
        if (lvl < 1) throw new IllegalArgumentException("Nível deve ser positivo.");
        return new PotionEffect(t, sec * 20, lvl - 1, true);
    }

    // ====== BOOK (TITLE/AUTHOR) ======
    private boolean bookCmd(CommandSender sender, String[] args, boolean author) {
        if (!(sender instanceof Player)) return noConsole(sender);
        String a = author ? "author" : "title";
        if (args.length <= 2) return usage(sender, "item edit " + a + " <" + a + ">");
        Player player = (Player) sender;
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return msg(sender, ChatColor.RED + "Segure um livro assinado.");
        String name = col(join(args, 2));
        BookMeta bm = (BookMeta) item.getItemMeta();
        if (author) bm.setAuthor(name); else bm.setTitle(name);
        item.setItemMeta((ItemMeta) bm);
        sender.sendMessage(ChatColor.GREEN + (author ? "Autor" : "Título") + " definido para \"" + name + "\"!");
        return true;
    }

    // ====== HEAD ======
    private boolean headCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        if (args.length != 3) return usage(sender, "item edit head <nick>");
        Player player = (Player) sender;
        ItemStack item = player.getItemInHand();
        if (item.getType() != Material.SKULL_ITEM || item.getDurability() != 3) return msg(sender, ChatColor.RED + "Segure uma cabeça de player.");
        SkullMeta sm = (SkullMeta) item.getItemMeta();
        sm.setOwner(args[2]);
        item.setItemMeta((ItemMeta) sm);
        sender.sendMessage(ChatColor.GREEN + "Dono da cabeça definido: \"" + args[2] + "\"!");
        return true;
    }

    // ====== CLEAR ======
    private boolean clearAllCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        if (args.length != 2) return usage(sender, "item edit clearall");
        Player player = (Player) sender;
        ItemStack item = player.getItemInHand();
        if (item == null) return msg(sender, ChatColor.RED + "Segure um item na mão.");
        clearAllData(item);
        player.setItemInHand(item);
        player.sendMessage(ChatColor.GREEN + "Metadados limpos!");
        return true;
    }

    private boolean clearCmd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return noConsole(sender);
        if (args.length < 3) return usage(sender, "item edit clear <types...>");
        Player player = (Player) sender;
        ItemStack item = player.getItemInHand();
        if (item == null) return msg(sender, ChatColor.RED + "Segure um item na mão.");
        ArrayList<Integer> params = new ArrayList<Integer>();
        for (int i = 2; i < args.length; i++) {
            String s = args[i];
            if (s.equalsIgnoreCase("name")) { if (!params.contains(NAME)) params.add(NAME); }
            else if (s.equalsIgnoreCase("lore")) { if (!params.contains(LORE)) params.add(LORE); }
            else if (s.equalsIgnoreCase("head")) { if (!params.contains(SKULL_OWNER)) params.add(SKULL_OWNER); }
            else if (s.equalsIgnoreCase("author")) { if (!params.contains(AUTHOR)) params.add(AUTHOR); }
            else if (s.equalsIgnoreCase("color")) { if (!params.contains(COLOR)) params.add(COLOR); }
            else if (s.equalsIgnoreCase("title")) { if (!params.contains(TITLE)) params.add(TITLE); }
            else if (s.equalsIgnoreCase("pages")) { if (!params.contains(PAGES)) params.add(PAGES); }
            else { return msg(sender, ChatColor.RED + "Tipo desconhecido: " + s); }
        }
        if (params.isEmpty()) return msg(sender, ChatColor.RED + "Nenhum metadado especificado.");
        int[] ints = new int[params.size()];
        for (int j = 0; j < params.size(); j++) ints[j] = params.get(j);
        clearData(item, ints);
        player.setItemInHand(item);
        sender.sendMessage(ChatColor.GREEN + "Metadados especificados limpos.");
        return true;
    }

    private void clearAllData(ItemStack item) {
        item.setItemMeta(null);
    }

    private void clearData(ItemStack item, int... params) {
        ItemMeta meta = item.getItemMeta();
        for (int i : params) {
            if (i == NAME) meta.setDisplayName(null);
            else if (i == LORE) meta.setLore(null);
            else if (i == SKULL_OWNER && meta instanceof SkullMeta) ((SkullMeta) meta).setOwner(null);
            else if (i == TITLE && meta instanceof BookMeta) ((BookMeta) meta).setTitle(null);
            else if (i == AUTHOR && meta instanceof BookMeta) ((BookMeta) meta).setAuthor(null);
            else if (i == PAGES && meta instanceof BookMeta) ((BookMeta) meta).setPages(new String[0]);
            else if (i == COLOR && meta instanceof LeatherArmorMeta) ((LeatherArmorMeta) meta).setColor(null);
        }
        item.setItemMeta(meta);
    }

    // ====== UTIL ======
    private String[] bumpArgs(String[] args) {
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 1; i < args.length; i++) list.add(args[i]);
        return list.toArray(new String[list.size()]);
    }

    private String join(String[] arr, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < arr.length; i++) { if (sb.length() > 0) sb.append(' '); sb.append(arr[i]); }
        return sb.toString();
    }

    private String col(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private boolean msg(CommandSender sender, String s) { sender.sendMessage(s); return true; }

    private boolean noConsole(CommandSender sender) {
        if (sender instanceof Player) return false;
        sender.sendMessage(ChatColor.RED + "Este comando só pode ser usado in-game!");
        return true;
    }

    private boolean noPerm(CommandSender sender, String perm) {
        if (sender.hasPermission(perm) || sender.hasPermission("item.edit.*")) return false;
        sender.sendMessage(ChatColor.RED + "Você não tem permissão: " + perm);
        return true;
    }

    private boolean usage(CommandSender sender) {
        return helpCmd(sender, new String[] {"edit"}, helpRoot, "Item Edit Help");
    }

    private boolean usage(CommandSender sender, String cmd) {
        return msg(sender, ChatColor.RED + "Uso: /" + cmd);
    }

    private boolean helpCmd(CommandSender sender, String[] args, CmdDesc[] help, String title) {
        int page = 1;
        if (args.length >= 3) {
            try { page = Integer.parseInt(args[2]); } catch (NumberFormatException nfe) { return msg(sender, ChatColor.RED + "\"" + args[2] + "\" não é número"); }
        }
        ArrayList<String> lines = new ArrayList<String>();
        int max = 1; int count = 0;
        for (int i = 0; i < help.length; i++) {
            CmdDesc c = help[i];
            if (c.getPerm() == null || sender.hasPermission(c.getPerm()) || sender.hasPermission("item.edit.*")) {
                if (lines.size() < 10 && i >= (page - 1) * 10 && i <= (page - 1) * 10 + 9) lines.add(c.asDef());
                if (count > 10 && count % 10 == 1) max++;
                count++;
            }
        }
        sender.sendMessage(ChatColor.GOLD + title + " (" + ChatColor.AQUA + page + ChatColor.GOLD + "/" + ChatColor.AQUA + max + ChatColor.GOLD + ")");
        for (String s : lines) sender.sendMessage(s);
        return true;
    }

    // ====== Display helper (como no Itemizer) ======
    private void displayAction(ItemStack item, String data, int action) {
        ItemMeta meta = item.getItemMeta();
        if (action == 0) {
            meta.setDisplayName(data);
        } else if (action == 1) {
            String[] d = data.split(" ");
            String temp = null;
            ArrayList<String> n = new ArrayList<String>();
            for (String s : d) {
                if (temp == null) {
                    temp = "" + s;
                } else {
                    int sl = ChatColor.stripColor(s).length();
                    if (sl >= 24) {
                        n.add(temp); temp = null; n.add(s);
                    } else {
                        int nl = sl + ChatColor.stripColor(temp).length();
                        if (nl >= 24) { n.add(temp); temp = "" + s; } else { temp = temp + " " + s; }
                    }
                }
            }
            if (temp != null) n.add(temp);
            ArrayList<String> fin = new ArrayList<String>();
            for (String s : n) {
                String[] t = s.split("\n");
                for (String ti : t) fin.add(ChatColor.translateAlternateColorCodes('&', ti));
            }
            meta.setLore(fin);
        }
        item.setItemMeta(meta);
    }

    // ====== ATTR LIST HANDLING ======
    private NBTTagList getAttrList(net.minecraft.server.v1_7_R4.ItemStack nms) {
        if (nms.tag == null) nms.tag = new NBTTagCompound();
        NBTTagList attr = nms.tag.getList("AttributeModifiers", 10);
        if (attr == null) { attr = new NBTTagList(); nms.tag.set("AttributeModifiers", attr); }
        return nms.tag.getList("AttributeModifiers", 10);
    }

    // ====== ENUM de atributos (igual ao Itemizer) ======
    private enum Attributes {
        DAMAGE(0, "generic.attackDamage"),
        MOVEMENT_SPEED(2, "generic.movementSpeed"),
        KNOCKBACK_RESISTANCE(2, "generic.knockbackResistance"),
        MAX_HEALTH(0, "generic.maxHealth");
        private final int op; private final String name;
        Attributes(int op, String name) { this.op = op; this.name = name; }
        private static Attributes get(String s) { for (Attributes a : values()) { if (a.name().toLowerCase().equalsIgnoreCase(s)) return a; } return null; }
        private static Attributes getByMCName(String s) { for (Attributes a : values()) { if (a.name.equalsIgnoreCase(s)) return a; } return null; }
    }

    // ====== Help descriptor interno ======
    private class CmdDesc {
        private final String cmd; private final String desc; private final String perm;
        public CmdDesc(String cmd, String desc, String perm) { this.cmd = cmd; this.desc = desc; this.perm = perm; }
        public String asDef() { return ChatColor.AQUA + this.cmd + ChatColor.RED + " - " + ChatColor.GOLD + this.desc; }
        public String getPerm() { return this.perm; }
    }

    // ====== TAB COMPLETER ======
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("item")) return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            if ("edit".startsWith(args[0].toLowerCase())) out.add("edit");
            return out;
        }
        if (!args[0].equalsIgnoreCase("edit")) return Collections.emptyList();
        if (args.length == 2) {
            for (String s : Arrays.asList("help","name","lore","advlore","potion","attr","damage","title","author","head","clear","clearall")) {
                if (s.startsWith(args[1].toLowerCase())) out.add(s);
            }
            return out;
        }
        String sub = args[1].toLowerCase();
        if (sub.equals("advlore") && args.length == 3) {
            for (String s : Arrays.asList("help","add","remove","change")) if (s.startsWith(args[2].toLowerCase())) out.add(s);
            return out;
        }
        if (sub.equals("potion") && args.length == 3) {
            for (String s : Arrays.asList("help","add","remove","list")) if (s.startsWith(args[2].toLowerCase())) out.add(s);
            return out;
        }
        if (sub.equals("attr") && args.length == 3) {
            for (String s : Arrays.asList("help","add","remove","list","listall")) if (s.startsWith(args[2].toLowerCase())) out.add(s);
            return out;
        }
        if (sub.equals("attr") && args.length == 7 && args[2].equalsIgnoreCase("add")) {
            for (String s : Arrays.asList("add","addmult","mult")) if (s.startsWith(args[6].toLowerCase())) out.add(s);
            return out;
        }
        if (sub.equals("clear") && args.length >= 3) {
            for (String s : Arrays.asList("name","lore","head","author","color","title","pages")) if (s.startsWith(args[args.length-1].toLowerCase())) out.add(s);
            return out;
        }
        return Collections.emptyList();
    }
}
