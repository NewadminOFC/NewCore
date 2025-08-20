// NewWorldsPlugin.java
package n.plugins.NewMultiverso;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Adaptador do "Multiverso" para ser usado dentro do NewCore.
 * NÃO estende JavaPlugin. O NewCore cria: new NewWorldsPlugin(this)
 * e passa este adaptador para MVCommand/MVTPCommand.
 */
public class NewWorldsPlugin {

    private final JavaPlugin core;            // plugin principal (NewCore)
    private FileConfiguration settings;       // configMultiverso.yml

    public NewWorldsPlugin(JavaPlugin core) {
        this.core = core;
        ensureResources();    // tenta extrair yml do jar (se existirem)
        loadSettings();       // carrega configMultiverso.yml
    }

    /** Tenta salvar os recursos apenas se existirem no jar (não falha se não tiver). */
    private void ensureResources() {
        try { core.saveResource("configMultiverso.yml", false); } catch (Throwable ignored) {}
        try { core.saveResource("Newworlds.yml", false); } catch (Throwable ignored) {}
    }

    /** Carrega configMultiverso.yml do dataFolder do Core. */
    private void loadSettings() {
        if (!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
        File f = new File(core.getDataFolder(), "configMultiverso.yml");
        settings = YamlConfiguration.loadConfiguration(f);
    }

    public void reloadSettings() { loadSettings(); }

    /** Mensagens com prefixo da config. Usa § para cores. */
    public String getMessage(String key) {
        String prefix = settings.getString("messages.prefix", "&6[Multiverso] &f");
        String coloredPrefix = color(prefix);
        if ("prefix".equalsIgnoreCase(key)) return coloredPrefix;

        String msg = settings.getString("messages." + key, "&cMensagem '" + key + "' não encontrada.");
        return coloredPrefix + color(msg);
    }

    /**
     * Auto-carrega mundos existentes no diretório do servidor (pasta do jar).
     * Chame isto no onEnable() do NewCore.
     */
    public void carregarMundosExistentes() {
        File serverFolder = core.getServer().getWorldContainer();
        if (serverFolder == null || !serverFolder.exists() || !serverFolder.isDirectory()) {
            core.getLogger().warning(getMessage("worlds-folder-not-found"));
            return;
        }

        File[] files = serverFolder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isDirectory()) continue;

            File levelDat = new File(file, "level.dat");
            if (!levelDat.exists()) continue;

            String worldName = file.getName();
            if (Bukkit.getWorld(worldName) != null) continue;

            WorldCreator wc = new WorldCreator(worldName);
            World world = Bukkit.createWorld(wc);
            if (world != null) {
                core.getLogger().info(getMessage("world-loaded").replace("{world}", world.getName()));
                new GameRuleUtil(core).applyGamerules(world, null);
            }
        }
    }

    /** Config "pura" para MVCommand/MVTPCommand. */
    public FileConfiguration getSettings() { return settings; }

    /** Acesso ao plugin principal quando necessário. */
    public JavaPlugin getCore() { return core; }

    /** Arquivo Newworlds.yml (caso os comandos precisem ler/escrever). */
    public File getNewWorldsFile() { return new File(core.getDataFolder(), "Newworlds.yml"); }

    // ==== util ====
    private String color(String s) { return s == null ? "" : s.replace("&", "§"); }
}
