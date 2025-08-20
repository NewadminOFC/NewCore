// GameRuleUtil.java
package n.plugins.NewMultiverso;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;

public class GameRuleUtil {

    private final NewWorldsPlugin mv; // pode ser null
    private final JavaPlugin core;

    private final File file;
    private YamlConfiguration config;

    public GameRuleUtil(NewWorldsPlugin plugin) {
        this.mv = plugin;
        this.core = plugin.getCore();
        this.file = new File(core.getDataFolder(), "Newworlds.yml");
        ensureDataFolder();
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public GameRuleUtil(JavaPlugin core) {
        this.mv = null;
        this.core = core;
        this.file = new File(core.getDataFolder(), "Newworlds.yml");
        ensureDataFolder();
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private void ensureDataFolder() {
        try {
            if (!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
            if (!file.exists()) file.createNewFile();
        } catch (IOException e) {
            core.getLogger().log(Level.WARNING, "Não foi possível preparar Newworlds.yml", e);
        }
    }

    public void ensureWorldSection(World world, CommandSender sender) {
        String path = "mundos." + world.getName();

        if (config.getConfigurationSection("default-gamerules") == null) {
            core.getLogger().warning("Seção default-gamerules não encontrada no Newworlds.yml!");
            return;
        }

        if (!config.contains(path)) {
            Map<String, Object> defaults = config.getConfigurationSection("default-gamerules").getValues(true);
            config.createSection(path, defaults);
            saveWithFeedback(sender, world);
            sendMessage(sender, "gamerules.created-default-section", "{world}", world.getName());
        } else {
            Map<String, Object> defaults = config.getConfigurationSection("default-gamerules").getValues(true);
            boolean changed = false;
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                String key = e.getKey();
                if (!config.contains(path + "." + key)) {
                    config.set(path + "." + key, e.getValue());
                    changed = true;
                }
            }
            if (changed) saveWithFeedback(sender, world);
        }
    }

    public void applyGamerules(World world, CommandSender sender) {
        ensureWorldSection(world, sender);
        String path = "mundos." + world.getName();

        if (config.getConfigurationSection(path) == null) {
            core.getLogger().warning("Configuração de gamerules para o mundo " + world.getName() + " não encontrada!");
            return;
        }

        Map<String, Object> rules = config.getConfigurationSection(path).getValues(true);

        for (Map.Entry<String, Object> entry : rules.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());

            // Bukkit 1.7.10 usa setGameRuleValue(String,String)
            if (isKnownGamerule(key)) {
                try {
                    world.setGameRuleValue(key, value);
                } catch (Throwable t) {
                    core.getLogger().log(Level.WARNING, "Falha ao aplicar gamerule " + key + "=" + value +
                            " no mundo " + world.getName(), t);
                }
            }
        }
        sendMessage(sender, "gamerules.applied-gamerules", "{world}", world.getName());
    }

    public void applyGamerules(World world) {
        applyGamerules(world, null);
    }

    /* ========================= Internos ========================= */

    private void saveWithFeedback(CommandSender sender, World world) {
        try {
            config.save(file);
        } catch (Exception e) {
            core.getLogger().log(Level.SEVERE, "Erro ao salvar configuração para o mundo " + world.getName(), e);
            sendMessage(sender, "gamerules.save-error", "{world}", world.getName());
        }
    }

    private void sendMessage(CommandSender sender, String key, String ph, String val) {
        if (sender == null) return;
        String msg = getMessage(key);
        if (msg == null) return;
        sender.sendMessage(msg.replace(ph, val));
    }

    private String getMessage(String key) {
        if (mv != null) {
            return mv.getMessage(key);
        }
        // Fallback quando usado direto pelo Core
        String prefix = "§6[Multiverso] §f";
        if ("gamerules.created-default-section".equalsIgnoreCase(key)) return prefix + "Seção padrão criada para {world}.";
        if ("gamerules.applied-gamerules".equalsIgnoreCase(key))      return prefix + "Gamerules aplicadas em {world}.";
        if ("gamerules.save-error".equalsIgnoreCase(key))              return prefix + "Erro ao salvar gamerules de {world}.";
        return prefix + key;
    }

    private boolean isKnownGamerule(String key) {
        // Gamerules presentes no 1.7.x
        if (key == null) return false;
        switch (key) {
            case "doFireTick":
            case "mobGriefing":
            case "keepInventory":
            case "doMobSpawning":
            case "doMobLoot":
            case "doTileDrops":
            case "doEntityDrops":
            case "commandBlockOutput":
            case "naturalRegeneration":
            case "doDaylightCycle":
            case "logAdminCommands":
            case "showDeathMessages":
                return true;
            default:
                return false;
        }
    }
}
