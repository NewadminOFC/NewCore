package n.plugins.NewOrbs;

import n.plugins.NewCore;
import n.plugins.NewOrbs.ConfigManager.OrbDefinition;
import n.plugins.NewOrbs.ConfigManager.SlotPos;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_7_R4.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.minecraft.server.v1_7_R4.NBTTagCompound;
import net.minecraft.server.v1_7_R4.NBTTagInt;
import net.minecraft.server.v1_7_R4.NBTTagList;

import java.util.*;

public final class MenuListener implements Listener {

    private final NewCore plugin;
    private final ConfigManager config;
    private final TradeManager trade;

    // trava por jogador enquanto um menu do Orbs estiver aberto (1.7.10 OK)
    private final Set<UUID> lockedPlayers = new HashSet<UUID>();
    // orb ativa no menu de trade
    private final Map<UUID, String> currentOrb = new HashMap<UUID, String>();

    public MenuListener(NewCore plugin, ConfigManager config, TradeManager trade) {
        this.plugin = plugin;
        this.config = config;
        this.trade = trade;
    }

    private void lock(Player p)   { lockedPlayers.add(p.getUniqueId()); }
    private void unlock(Player p) { lockedPlayers.remove(p.getUniqueId()); currentOrb.remove(p.getUniqueId()); }
    private boolean isLocked(Player p) { return lockedPlayers.contains(p.getUniqueId()); }

    private void clearCursor(Player p, InventoryClickEvent e) {
        try { e.setCursor(null); } catch (Throwable ignored) {}
        try { p.setItemOnCursor(null); } catch (Throwable ignored) {}
    }
    private void clearCursor(Player p) {
        try { p.setItemOnCursor(null); } catch (Throwable ignored) {}
    }

    // =========================================
    // Abrir trade direto clicando com a ORB
    // =========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getItem() == null) return;
        Player p = e.getPlayer();
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        for (OrbDefinition orb : config.getOrbs().values()) {
            try {
                if (item.getTypeId() == orb.id && item.getDurability() == (short) orb.data) {
                    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                        String display = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                        String expected = ChatColor.stripColor(orb.name);
                        if (display.equalsIgnoreCase(expected)) {
                            e.setCancelled(true);
                            clearCursor(p);
                            openTradeMenu(p, orb.key);
                            p.playSound(p.getLocation(), Sound.CLICK, 1.0f, 1.0f);
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    // =========================================
    // Somente menu de TRADE por ORB
    // =========================================
    public void openTradeMenu(Player p, String orbKey) {
        OrbDefinition orb = getOrbByKey(orbKey);
        if (orb == null) {
            orb = firstOrb(); // fallback
            if (orb == null) return;
        }
        currentOrb.put(p.getUniqueId(), orb.key);

        int size = computeTradeInvSize();
        String menuTitle = ChatColor.translateAlternateColorCodes('&', orb.menuTitle);
        Inventory inv = Bukkit.createInventory(p, size, menuTitle);

        setAtSlotOrDefault(inv, "info-orb", buildInfoItem(p, orb.baseValue), 4);
        setAtSlotOrDefault(inv, "orb 1",  buildOptionItem(p, orb, 1,  false), 10);
        setAtSlotOrDefault(inv, "orb 16", buildOptionItem(p, orb, 16, false), 12);
        setAtSlotOrDefault(inv, "orb64",  buildOptionItem(p, orb, 64, false), 14);
        setAtSlotOrDefault(inv, "orb all", buildOptionItem(p, orb, trade.countOrbsInInventory(p, orb, true), true), 13);

        // botão fecha
        ItemStack close = new ItemStack(Material.ARROW, 1);
        ItemMeta bm = close.getItemMeta();
        bm.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&cFechar"));
        bm.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', "&7Fechar este menu")));
        close.setItemMeta(bm);
        inv.setItem(size - 1, close);

        lock(p);
        clearCursor(p);
        p.openInventory(inv);
    }

    private OrbDefinition getOrbByKey(String k) {
        if (k == null) return null;
        return config.getOrbs().get(k.toLowerCase());
    }
    private OrbDefinition firstOrb() {
        LinkedHashMap<String, OrbDefinition> map = config.getOrbs();
        for (OrbDefinition o : map.values()) return o;
        return null;
    }

    private int computeTradeInvSize() {
        int maxY = 0;
        for (String k : new String[]{"info-orb", "orb 1", "orb 16", "orb64", "orb all"}) {
            SlotPos sp = config.getSlot(k);
            if (sp != null && sp.y > maxY) maxY = sp.y;
        }
        int rows = Math.max(3, maxY);
        return Math.min(54, Math.max(9, rows * 9));
    }

    private void setAtSlotOrDefault(Inventory inv, String key, ItemStack item, int fallbackIndex) {
        int idx = indexFor(inv, key, fallbackIndex);
        if (idx >= 0) inv.setItem(idx, item);
    }

    private int indexFor(Inventory inv, String key, int fallbackIndex) {
        SlotPos sp = config.getSlot(key);
        int idx = sp != null ? sp.toIndex() : fallbackIndex;
        return (idx >= 0 && idx < inv.getSize()) ? idx : -1;
    }

    private ItemStack buildInfoItem(Player p, int baseValue) {
        ItemStack info = new ItemStack(Material.BOOK, 1);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.menuInfoTitle()));
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.translateAlternateColorCodes('&', config.menuInfoLore1()));
        int bonus = trade.getBonusPercent(p);
        lore.add(ChatColor.translateAlternateColorCodes('&',
                config.menuInfoLoreBonus().replace("%bonus%", String.valueOf(bonus))));
        if (baseValue >= 0) {
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    config.menuInfoLoreBase().replace("%base%", String.valueOf(baseValue))));
        }
        im.setLore(lore);
        info.setItemMeta(im);
        return info;
    }

    private ItemStack buildOptionItem(Player p, OrbDefinition orb, int amount, boolean all) {
        int have = trade.countOrbsInInventory(p, orb, true);
        if (all) amount = have;
        if (amount < 0) amount = 0;

        ItemStack item = new ItemStack(orb.id, 1, (short) orb.data);
        List<String> lore = new ArrayList<String>();
        int bonus = trade.getBonusPercent(p);
        int value = trade.computeValue(orb.baseValue, amount <= 0 ? 0 : amount, bonus);

        Map<String, String> ph = new HashMap<String, String>();
        ph.put("amount", String.valueOf(amount));
        ph.put("value", trade.formatNumber(value));

        String title = all ? config.optionAllTitle() : config.optionTitle();
        ItemMeta im = item.getItemMeta();
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', MessageUtils.applyPlaceholders(title, ph)));

        // Empilhar 1/16/64 com a mesma quantidade
        if (!all) {
            int stack = amount <= 0 ? 1 : (amount > 64 ? 64 : amount);
            try { item.setAmount(stack); } catch (Throwable ignored) {}
        } else {
            try { item.setAmount(1); } catch (Throwable ignored) {}
        }

        if (all) {
            item = addGlowWithoutLore(item);
            lore.add("");
        }

        lore.add(ChatColor.translateAlternateColorCodes('&', MessageUtils.applyPlaceholders(config.optionLore(), ph)));
        Map<String, String> mapAmt = new HashMap<String, String>();
        mapAmt.put("amount", String.valueOf(have));
        lore.add(ChatColor.translateAlternateColorCodes('&', MessageUtils.applyPlaceholders(config.optionAmount(), mapAmt)));
        lore.add(ChatColor.translateAlternateColorCodes('&', MessageUtils.applyPlaceholders(config.optionReceive(), ph)));

        im.setLore(lore);
        item.setItemMeta(im);
        return item;
    }

    private ItemStack addGlowWithoutLore(ItemStack item) {
        try {
            net.minecraft.server.v1_7_R4.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
            NBTTagCompound tag = nmsItem.hasTag() ? nmsItem.getTag() : new NBTTagCompound();
            NBTTagList enchants = new NBTTagList();
            NBTTagCompound enchant = new NBTTagCompound();
            enchant.set("id", new NBTTagInt(Enchantment.DURABILITY.getId()));
            enchant.set("lvl", new NBTTagInt(1));
            enchants.add(enchant);
            tag.set("ench", enchants);
            nmsItem.setTag(tag);
            return CraftItemStack.asBukkitCopy(nmsItem);
        } catch (Exception e) {
            return item;
        }
    }

    // Só consideramos menus das ORBs (sem menu principal)
    private boolean isOurMenu(Inventory top) {
        if (top == null) return false;
        String title = ChatColor.stripColor(top.getTitle());
        for (OrbDefinition orb : config.getOrbs().values()) {
            if (title.equalsIgnoreCase(ChatColor.stripColor(orb.menuTitle))) return true;
        }
        return false;
    }

    // Lock open/close
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        if (isOurMenu(e.getView().getTopInventory())) lock(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        final Player p = (Player) e.getPlayer();

        // desbloqueio tardio (1 tick) – evita “gap” entre fechar e abrir
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                try {
                    Inventory top = p.getOpenInventory() != null ? p.getOpenInventory().getTopInventory() : null;
                    if (isOurMenu(top)) {
                        lock(p); // ainda está num menu nosso, mantém travado
                    } else {
                        unlock(p); // saiu do nosso menu
                    }
                } catch (Throwable ignored) { unlock(p); }
            }
        });
    }

    // Anti-roubo + lógica por slot
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        Inventory top = e.getView().getTopInventory();
        if (!isOurMenu(top) && !isLocked(p)) return;

        e.setCancelled(true);
        clearCursor(p, e); // evita “ghost item” no mouse

        int raw = e.getRawSlot();
        if (raw < 0 || top == null || raw >= top.getSize()) return;

        // TRADE MENU
        OrbDefinition active = getActiveOrb(p, top);
        if (active == null) return;

        int idxInfo = indexFor(top, "info-orb", 4);
        int idx1    = indexFor(top, "orb 1", 10);
        int idx16   = indexFor(top, "orb 16", 12);
        int idx64   = indexFor(top, "orb64", 14);
        int idxAll  = indexFor(top, "orb all", 13);
        int idxClose = top.getSize() - 1;

        if (raw == idxClose) { p.closeInventory(); return; }
        if (raw == idxInfo)  { p.playSound(p.getLocation(), Sound.CLICK, 1.0f, 1.2f); return; }

        int amount = 0;
        if      (raw == idx1)  amount = 1;
        else if (raw == idx16) amount = 16;
        else if (raw == idx64) amount = 64;
        else if (raw == idxAll) amount = trade.countOrbsInInventory(p, active, true);
        else return;

        if (amount <= 0) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', config.prefix() + "&cVocê não possui orbs para trocar."));
            return;
        }

        // === TROCOU: fecha o menu ===
        trade.trade(p, active, amount);
        p.playSound(p.getLocation(), Sound.LEVEL_UP, 1.0f, 1.2f);
        clearCursor(p);
        p.closeInventory(); // <- fecha após a troca
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (isLocked(p) || isOurMenu(e.getView().getTopInventory())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!isLocked(p) && !isOurMenu(e.getView().getTopInventory())) return;

        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) { e.setCancelled(true); clearCursor(p); return; }
        }
        e.setCancelled(true);
        clearCursor(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent e) {
        if (isLocked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(PlayerPickupItemEvent e) {
        if (isLocked(e.getPlayer())) e.setCancelled(true);
    }

    private OrbDefinition getActiveOrb(Player p, Inventory top) {
        String key = currentOrb.get(p.getUniqueId());
        if (key != null) {
            OrbDefinition byKey = config.getOrbs().get(key);
            if (byKey != null) return byKey;
        }
        String title = top == null ? "" : ChatColor.stripColor(top.getTitle());
        for (OrbDefinition orb : config.getOrbs().values()) {
            if (title.equalsIgnoreCase(ChatColor.stripColor(orb.menuTitle))) return orb;
        }
        return null;
    }
}
