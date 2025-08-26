package n.plugins.NewOrbs;

import n.plugins.NewCore;
import n.plugins.NewOrbs.ConfigManager.OrbDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class MenuListener implements Listener {

    private final NewCore plugin;
    private final ConfigManager config;
    private final TradeManager trade;

    private final HashMap<UUID, String> currentOrb = new HashMap<UUID, String>();
    private final HashMap<UUID, Inventory> lastInv = new HashMap<UUID, Inventory>();

    public MenuListener(NewCore plugin, ConfigManager config, TradeManager trade) {
        this.plugin = plugin;
        this.config = config;
        this.trade  = trade;
    }

    // ===========================================
    //  abrir menu com clique direito na orb
    // ===========================================
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        if (e.getItem() == null) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        // Apenas cliques direitos
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Verifica se o item corresponde exatamente a alguma orb do config
        for (OrbDefinition orb : config.getOrbs().values()) {
            if (item.getTypeId() == orb.id && item.getDurability() == (short) orb.data) {
                // Verifica nome do item
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    String display = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                    String expected = ChatColor.stripColor(MessageUtils.color(orb.name));
                    if (display.equalsIgnoreCase(expected)) {
                        e.setCancelled(true); // impede uso padrão
                        openTradeMenu(p, orb.key);
                        p.playSound(p.getLocation(), Sound.CLICK, 1.0f, 1.0f);
                        return;
                    }
                }
            }
        }
    }


    public void openMainMenu(Player p) {
        LinkedHashMap<String, OrbDefinition> orbs = config.getOrbs();
        int size = orbs.size() <= 9 ? 9 : (orbs.size() <= 18 ? 18 : 27);
        Inventory inv = Bukkit.createInventory(p, size, MessageUtils.color(config.menuTitleMain()));

        // Item de informação no centro
        ItemStack info = buildInfoItem(p, -1);
        int center = Math.min(size - 1, 4);
        inv.setItem(center, info);

        int slot = 0;
        for (Map.Entry<String,OrbDefinition> e : orbs.entrySet()) {
            OrbDefinition orb = e.getValue();
            ItemStack icon;
            try {
                icon = new ItemStack(orb.id, 1, (short) orb.data);
            } catch (Throwable t) {
                icon = new ItemStack(Material.EMERALD, 1);
            }
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(MessageUtils.color("&f" + orb.name));
            ArrayList<String> lore = new ArrayList<String>();
            lore.add(MessageUtils.color("&7Clique para trocar " + orb.name));
            lore.add(MessageUtils.color("&7Você possui: &e" + trade.countOrbsInInventory(p, orb)));
            meta.setLore(lore);
            icon.setItemMeta(meta);

            if (slot == center) slot++; // não sobrescrever o info central
            if (slot >= inv.getSize()) break;
            inv.setItem(slot, icon);
            slot++;
        }

        lastInv.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    public void openTradeMenu(Player p, String orbKey) {
        LinkedHashMap<String, OrbDefinition> orbs = config.getOrbs();
        OrbDefinition orb = orbs.get(orbKey.toLowerCase());
        if (orb == null) {
            for (Map.Entry<String,OrbDefinition> e : orbs.entrySet()) {
                String disp = ChatColor.stripColor(MessageUtils.color(e.getValue().name));
                if (disp.equalsIgnoreCase(ChatColor.stripColor(orbKey))) {
                    orb = e.getValue();
                    break;
                }
            }
        }
        if (orb == null) {
            openMainMenu(p);
            return;
        }
        currentOrb.put(p.getUniqueId(), orb.key);

        int size = 27;
        Inventory inv = Bukkit.createInventory(p, size, MessageUtils.color(orb.menuTitle));

        // Info topo
        inv.setItem(4, buildInfoItem(p, orb.baseValue));

        // Opções
        int[] amounts = config.optionAmounts();
        int[] slots = new int[] { 10, 12, 14, 16 };
        for (int i = 0; i < amounts.length && i < slots.length; i++) {
            inv.setItem(slots[i], buildOptionItem(p, orb, amounts[i], false));
        }

        // Todos
        inv.setItem(13, buildOptionItem(p, orb, trade.countOrbsInInventory(p, orb), true));

        // Voltar
        ItemStack back = new ItemStack(Material.ARROW, 1);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(MessageUtils.color("&cVoltar"));
        bm.setLore(Collections.singletonList(MessageUtils.color("&7Retornar ao menu principal")));
        back.setItemMeta(bm);
        inv.setItem(size - 1, back);

        lastInv.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    private ItemStack buildInfoItem(Player p, int baseValue) {
        ItemStack info = new ItemStack(Material.BOOK, 1);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(MessageUtils.color(config.menuInfoTitle()));
        ArrayList<String> lore = new ArrayList<String>();
        lore.add(MessageUtils.color(config.menuInfoLore1()));
        int bonus = trade.getBonusPercent(p);
        lore.add(MessageUtils.color(config.menuInfoLoreBonus().replace("%bonus%", String.valueOf(bonus))));
        if (baseValue >= 0) lore.add(MessageUtils.color(config.menuInfoLoreBase().replace("%base%", String.valueOf(baseValue))));
        im.setLore(lore);
        info.setItemMeta(im);
        return info;
    }

    private ItemStack buildOptionItem(Player p, OrbDefinition orb, int amount, boolean all) {
        int have = trade.countOrbsInInventory(p, orb);
        if (all) amount = have;
        if (amount < 0) amount = 0;

        ItemStack paper = new ItemStack(Material.PAPER, 1);
        ItemMeta pm = paper.getItemMeta();

        int bonus = trade.getBonusPercent(p);
        int value = trade.computeValue(orb.baseValue, amount <= 0 ? 0 : amount, bonus);

        java.util.HashMap<String,String> ph = new java.util.HashMap<String,String>();
        ph.put("amount", String.valueOf(amount));
        ph.put("value", trade.formatNumber(value));

        String title = all ? config.optionAllTitle() : config.optionTitle();
        pm.setDisplayName(MessageUtils.applyPlaceholders(title, ph));

        ArrayList<String> lore = new ArrayList<String>();
        String l1 = all ? config.optionAllLore() : config.optionLore();
        lore.add(MessageUtils.applyPlaceholders(l1, ph));
        java.util.HashMap<String,String> mapAmt = new java.util.HashMap<String,String>();
        mapAmt.put("amount", String.valueOf(have));
        lore.add(MessageUtils.applyPlaceholders(config.optionAmount(), mapAmt));
        lore.add(MessageUtils.applyPlaceholders(config.optionReceive(), ph));

        pm.setLore(lore);
        paper.setItemMeta(pm);
        return paper;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        Inventory inv = e.getInventory();
        if (inv == null) return;

        String title;
        try { title = inv.getTitle(); } catch (Throwable t) { title = inv.getName(); }
        if (title == null) return;

        String clean = ChatColor.stripColor(title);
        String mainClean = ChatColor.stripColor(MessageUtils.color(config.menuTitleMain()));

        // Menu principal
        if (clean.equalsIgnoreCase(mainClean)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;
            String dn = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            for (Map.Entry<String,OrbDefinition> en : config.getOrbs().entrySet()) {
                OrbDefinition orb = en.getValue();
                if (ChatColor.stripColor(MessageUtils.color(orb.name)).equalsIgnoreCase(dn)) {
                    openTradeMenu(p, orb.key);
                    p.playSound(p.getLocation(), Sound.CLICK, 1.0f, 1.0f);
                    return;
                }
            }
            return;
        }

        // Menus de orb
        for (Map.Entry<String,OrbDefinition> en : config.getOrbs().entrySet()) {
            OrbDefinition orb = en.getValue();
            String t = ChatColor.stripColor(MessageUtils.color(orb.menuTitle));
            if (clean.equalsIgnoreCase(t)) {
                e.setCancelled(true);
                ItemStack clicked = e.getCurrentItem();
                if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

                // Voltar
                if (clicked.getType() == Material.ARROW) {
                    openMainMenu(p);
                    p.playSound(p.getLocation(), Sound.CLICK, 1.0f, 0.8f);
                    return;
                }

                int amount = 0;
                try {
                    String digits = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).replaceAll("[^0-9]", "");
                    if (digits.length() > 0) amount = Integer.parseInt(digits);
                } catch (Throwable ignored) {}

                boolean isAll = ChatColor.stripColor(MessageUtils.color(config.optionAllTitle()))
                        .equalsIgnoreCase(ChatColor.stripColor(clicked.getItemMeta().getDisplayName()));
                if (isAll || amount <= 0) {
                    amount = trade.countOrbsInInventory(p, orb);
                }

                if (amount <= 0) {
                    p.sendMessage(MessageUtils.color(config.prefix() + "&cVocê não possui orbs para trocar."));
                    return;
                }

                boolean ok = trade.trade(p, orb, amount);
                if (!ok) {
                    p.sendMessage(MessageUtils.color(config.msgInsufficient().replace("%prefix%", config.prefix())));
                } else {
                    p.playSound(p.getLocation(), Sound.LEVEL_UP, 1.0f, 1.2f);
                }
                openTradeMenu(p, orb.key);
                return;
            }
        }
    }
}
