package n.plugins.NewOrbs;

import n.plugins.NewCore;
import n.plugins.NewOrbs.ConfigManager.OrbDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public final class TradeManager {

    private final NewCore plugin;
    private final ConfigManager config;

    public TradeManager(NewCore plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public int countOrbsInInventory(Player p, OrbDefinition orb) {
        if (p == null) return 0;
        int count = 0;
        ItemStack[] contents = p.getInventory().getContents();
        if (contents == null) return 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null) continue;
            try {
                if (it.getTypeId() == orb.id && it.getDurability() == (short) orb.data) {
                    count += it.getAmount();
                }
            } catch (Throwable ignored) {}
        }
        return count;
    }

    public boolean removeOrbs(Player p, OrbDefinition orb, int amount) {
        if (p == null || amount <= 0) return false;
        int needed = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && needed > 0; i++) {
            ItemStack it = contents[i];
            if (it == null) continue;
            if (it.getTypeId() == orb.id && it.getDurability() == (short) orb.data) {
                int stackAmount = it.getAmount();
                if (stackAmount <= needed) {
                    p.getInventory().setItem(i, null);
                    needed -= stackAmount;
                } else {
                    it.setAmount(stackAmount - needed);
                    p.getInventory().setItem(i, it);
                    needed = 0;
                }
            }
        }
        if (needed <= 0) {
            p.updateInventory(); // 1.7.x
            return true;
        }
        return false;
    }

    public int getBonusPercent(Player p) {
        int best = 0;
        Map<String, String> perms = config.permissionLevelsSnapshot();
        if (perms != null) {
            Iterator<Map.Entry<String,String>> it = perms.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,String> e = it.next();
                String levelKey = e.getKey();
                String perm = e.getValue();
                if (perm != null && perm.length() > 0 && p.hasPermission(perm)) {
                    int bonus = config.getBonusForLevelKey(levelKey);
                    if (bonus > best) best = bonus;
                }
            }
        }
        return best;
    }

    public int computeValue(int baseValue, int amount, int bonusPercent) {
        long v = (long) baseValue * (long) amount * (100L + (long) bonusPercent) / 100L;
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) v;
    }

    public String formatNumber(int v) {
        try {
            NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("pt", "BR"));
            return nf.format(v);
        } catch (Throwable t) {
            return String.valueOf(v);
        }
    }

    public boolean trade(Player p, OrbDefinition orb, int amount) {
        if (p == null || amount <= 0) return false;

        int have = countOrbsInInventory(p, orb);
        if (have < amount) {
            sendMessage(p, config.msgInsufficient());
            return false;
        }

        int bonus = getBonusPercent(p);
        int value = computeValue(orb.baseValue, amount, bonus);

        if (!removeOrbs(p, orb, amount)) {
            sendMessage(p, config.msgInsufficient());
            return false;
        }

        // Executa o comando configurado (ex.: jrmctp %value% %player%)
        String cmd = config.tpCommand()
                .replace("%player%", p.getName())
                .replace("%value%", String.valueOf(value));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        // Mensagens
        sendMessage(p, config.msgTradeTitle());
        if (bonus > 0) {
            sendMessage(p, config.msgTradeWithBonus()
                    .replace("%prefix%", config.prefix())
                    .replace("%value%", formatNumber(value))
                    .replace("%bonus%", String.valueOf(bonus)));
        } else {
            sendMessage(p, config.msgTradeNoBonus()
                    .replace("%prefix%", config.prefix())
                    .replace("%value%", formatNumber(value)));
        }
        return true;
    }

    private void sendMessage(Player p, String raw) {
        if (raw == null || raw.length() == 0) return;
        String s = raw.replace("%prefix%", config.prefix());
        p.sendMessage(MessageUtils.color(s));
    }
}
