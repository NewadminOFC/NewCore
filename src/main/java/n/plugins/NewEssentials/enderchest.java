// File: src/main/java/n/plugins/NewEssentials/enderchest.java
package n.plugins.NewEssentials;

import n.plugins.NewCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class enderchest implements CommandExecutor {

    private NewCore plugin;

    // config dedicada
    private File cfgFile;
    private YamlConfiguration cfg;

    private final Map<UUID, Long> cooldown = new HashMap<UUID, Long>();

    public enderchest() {
        // será injetado em onBind; mas para manter compat. com teu manager atual,
        // vamos tentar obter a instância estática se existir.
    }

    // chamado pelo teu CommandManager ao dar bind
    public void onBind(NewCore plugin) {
        if (this.plugin == null) {
            this.plugin = plugin;
            loadConfig();
        }
    }

    private void loadConfig() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfgFile = new File(plugin.getDataFolder(), "NewEssentials.yml");
            if (!cfgFile.exists()) {
                try { plugin.saveResource("NewEssentials.yml", false); } catch (IllegalArgumentException ignored) {}
            }
            cfg = new YamlConfiguration();
            cfg.load(cfgFile);
        } catch (IOException | InvalidConfigurationException e) {
            cfg = new YamlConfiguration(); // vazio -> usa defaults
        }
    }

    private String c(String path, String def) {
        return ChatColor.translateAlternateColorCodes('&', cfg.getString(path, def));
    }
    private boolean b(String p, boolean d){ return cfg.getBoolean(p, d); }
    private int i(String p, int d){ return cfg.getInt(p, d); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(c("enderchest.messages.only-player","&cComando apenas in-game."));
            return true;
        }
        // garantir init
        if (plugin == null) {
            // tentar resolver pelo comando
            plugin = (NewCore) Bukkit.getPluginManager().getPlugin("NewCore");
            loadConfig();
        }

        Player p = (Player) sender;

        if (!b("enderchest.enabled", true)) {
            p.sendMessage(c("enderchest.messages.disabled","&cO comando /ec está desativado neste servidor."));
            return true;
        }
        if (b("enderchest.require-permission", true) && !p.hasPermission("new.ec")) {
            p.sendMessage(c("enderchest.messages.no-permission","&cVocê não tem permissão para usar /ec."));
            return true;
        }

        // bloco de mundos
        List<String> blocked = cfg.getStringList("enderchest.blocked-worlds");
        if (blocked != null && blocked.contains(p.getWorld().getName())) {
            p.sendMessage(c("enderchest.messages.blocked-world","&cVocê não pode usar /ec neste mundo."));
            return true;
        }

        // cooldown
        int cd = i("enderchest.cooldown-seconds", 5);
        if (cd > 0) {
            long now = System.currentTimeMillis();
            Long last = cooldown.get(p.getUniqueId());
            if (last != null) {
                long left = cd - ((now - last) / 1000L);
                if (left > 0) {
                    p.sendMessage(c("enderchest.messages.cooldown","&eAguarde &f%sec%s &epara usar /ec novamente.")
                            .replace("%sec%", String.valueOf(left)));
                    return true;
                }
            }
            cooldown.put(p.getUniqueId(), now);
        }

        // abrir EC
        p.openInventory(p.getEnderChest());

        if (b("enderchest.play-sound", true)) {
            try {
                String s = cfg.getString("enderchest.sound", "CHEST_OPEN");
                p.playSound(p.getLocation(), Sound.valueOf(s), 1.0f, 1.0f);
            } catch (Throwable ignored) {}
        }
        if (!b("enderchest.silent-open", false)) {
            p.sendMessage(c("enderchest.messages.opened","&aEnder Chest aberto."));
        }
        return true;
    }
}
