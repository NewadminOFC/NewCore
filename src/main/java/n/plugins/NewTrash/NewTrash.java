package n.plugins.NewTrash;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NewTrash implements Listener, CommandExecutor, TabCompleter {

    private final Plugin plugin;

    // Config
    private File configFile;
    private FileConfiguration config;
    private int intervalSeconds;
    private List<Integer> warnAt;
    private String prefix;
    private String msgWarn;
    private String msgCleared;
    private String msgNothing;
    private String msgStatus;
    private String msgReloaded;
    private String msgNoPerm;

    // Filtros (apenas DROPPED_ITEM)
    private boolean useWhitelist;           // true = remove somente materiais listados
    private Set<Material> materialList;     // lista para white/blacklist

    // Estado
    private BukkitTask task;
    private volatile int secondsLeft;
    private final Object lock = new Object();

    public NewTrash(Plugin plugin) {
        this.plugin = plugin;
        initConfig();
        startTask();
    }

    // ===================== CONFIG =====================
    private void initConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                //noinspection ResultOfMethodCallIgnored
                plugin.getDataFolder().mkdirs();
            }
            configFile = new File(plugin.getDataFolder(), "newtrash.yml");
            if (!configFile.exists()) {
                writeDefaultConfig(configFile);
            }
            config = new YamlConfiguration();
            config.load(configFile);
            readConfigValues();
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("[NewTrash] Erro ao carregar newtrash.yml: " + e.getMessage());
            setDefaults();
        }
    }

    private void readConfigValues() {
        // Intervalo e avisos
        intervalSeconds = Math.max(5, config.getInt("intervalo.segundos", 300));

        warnAt = new ArrayList<Integer>();
        List<?> raw = config.getList("avisos.segundos_antes", Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1));
        for (Object o : raw) {
            try { warnAt.add(Integer.parseInt(String.valueOf(o))); } catch (NumberFormatException ignored) {}
        }
        Collections.sort(warnAt);
        Collections.reverse(warnAt);

        // Mensagens
        prefix = color(config.getString("mensagens.prefix", "&7[&aLixeira&7]&r "));
        msgWarn = color(config.getString("mensagens.aviso", "&eLimpando itens dropados em &c{seg}s&e..."));
        msgCleared = color(config.getString("mensagens.limpo", "&aLixeira: &f{qtd} &aitens removidos."));
        msgNothing = color(config.getString("mensagens.nada", "&aLixeira: nada para limpar."));
        msgStatus = color(config.getString("mensagens.status", "&ePróxima limpeza em &c{seg}s&e."));
        msgReloaded = color(config.getString("mensagens.reload", "&aConfiguração recarregada."));
        msgNoPerm = color(config.getString("mensagens.sem_permissao", "&cSem permissão."));

        // Filtros de material (apenas DROPPED_ITEM)
        useWhitelist = config.getBoolean("filtros.usar_whitelist", false);
        materialList = new HashSet<Material>();
        List<String> mats = config.getStringList("filtros.materiais");
        for (String s : mats) {
            Material m = matSafe(s);
            if (m != null) materialList.add(m);
        }
    }

    private void setDefaults() {
        intervalSeconds = 300;
        warnAt = Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1);
        prefix = color("&7[&aLixeira&7]&r ");
        msgWarn = color("&eLimpando itens dropados em &c{seg}s&e...");
        msgCleared = color("&aLixeira: &f{qtd} &aitens removidos.");
        msgNothing = color("&aLixeira: nada para limpar.");
        msgStatus = color("&ePróxima limpeza em &c{seg}s&e.");
        msgReloaded = color("&aConfiguração recarregada.");
        msgNoPerm = color("&cSem permissão.");
        useWhitelist = false;
        materialList = new HashSet<Material>();
    }

    private void writeDefaultConfig(File file) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.set("intervalo.segundos", 300);
        y.set("avisos.segundos_antes", Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1));

        y.set("mensagens.prefix", "&7[&aLixeira&7]&r ");
        y.set("mensagens.aviso", "&eLimpando itens dropados em &c{seg}s&e...");
        y.set("mensagens.limpo", "&aLixeira: &f{qtd} &aitens removidos.");
        y.set("mensagens.nada", "&aLixeira: nada para limpar.");
        y.set("mensagens.status", "&ePróxima limpeza em &c{seg}s&e.");
        y.set("mensagens.reload", "&aConfiguração recarregada.");
        y.set("mensagens.sem_permissao", "&cSem permissão.");

        // Filtros: por padrão, blacklist com exemplos (COBBLESTONE, DIRT)
        y.set("filtros.usar_whitelist", false);
        y.set("filtros.materiais", Arrays.asList("COBBLESTONE", "DIRT"));

        y.save(file);
    }

    public void reload() {
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("[NewTrash] Erro ao recarregar: " + e.getMessage());
        }
        readConfigValues();
        restartTask();
    }

    // ===================== TAREFA =====================
    private void startTask() {
        stopTask();
        synchronized (lock) { secondsLeft = intervalSeconds; }
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tick(); }
        }, 20L, 20L); // a cada 1s
    }

    private void restartTask() { startTask(); }

    private void stopTask() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        int left;
        synchronized (lock) { secondsLeft--; left = secondsLeft; }

        if (warnAt.contains(left)) {
            broadcast(prefix + msgWarn.replace("{seg}", String.valueOf(left)));
        }

        if (left <= 0) {
            int total = clearDroppedItemsOnce();
            if (total > 0) broadcast(prefix + msgCleared.replace("{qtd}", String.valueOf(total)));
            else broadcast(prefix + msgNothing);
            synchronized (lock) { secondsLeft = intervalSeconds; }
        }
    }

    // ===================== LIMPEZA (somente itens dropados) =====================
    private int clearDroppedItemsOnce() {
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            // percorre cópia
            List<Entity> entities = new ArrayList<Entity>(world.getEntities());
            for (Entity e : entities) {
                if (e.getType() != EntityType.DROPPED_ITEM) continue;

                Item it = (Item) e;
                Material m = it.getItemStack().getType();

                // Lógica de white/blacklist
                if (useWhitelist) {
                    // remove somente se estiver na lista
                    if (!materialList.contains(m)) continue;
                } else {
                    // remove tudo, exceto se estiver na blacklist
                    if (materialList.contains(m)) continue;
                }

                e.remove();
                // contabiliza pela quantidade total de itens no stack
                removed += it.getItemStack().getAmount();
            }
        }
        return removed;
    }

    private void broadcast(String msg) { Bukkit.broadcastMessage(msg); }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private Material matSafe(String name) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===================== COMANDOS =====================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(prefix + color("&eComandos:"));
            sender.sendMessage(color("&7/newtrash &aagora &8- &fLimpa itens dropados agora (&7newtrash.use&f)"));
            sender.sendMessage(color("&7/newtrash &astatus &8- &fTempo restante"));
            sender.sendMessage(color("&7/newtrash &areload &8- &fRecarrega config (&7newtrash.admin&f)"));
            sender.sendMessage(color("&7/newtrash &asetinterval <s> &8- &fDefine intervalo (&7newtrash.admin&f)"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("agora")) {
            if (!sender.hasPermission("newtrash.use") && !sender.hasPermission("newtrash.admin")) {
                sender.sendMessage(prefix + msgNoPerm);
                return true;
            }
            int total = clearDroppedItemsOnce();
            if (total > 0) Bukkit.broadcastMessage(prefix + msgCleared.replace("{qtd}", String.valueOf(total)));
            else sender.sendMessage(prefix + msgNothing);
            synchronized (lock) { secondsLeft = intervalSeconds; }
            return true;
        }

        if (sub.equals("status")) {
            int left; synchronized (lock) { left = secondsLeft; }
            sender.sendMessage(prefix + msgStatus.replace("{seg}", String.valueOf(left)));
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("newtrash.admin")) {
                sender.sendMessage(prefix + msgNoPerm);
                return true;
            }
            reload();
            sender.sendMessage(prefix + msgReloaded);
            return true;
        }

        if (sub.equals("setinterval")) {
            if (!sender.hasPermission("newtrash.admin")) {
                sender.sendMessage(prefix + msgNoPerm);
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(color("&cUso: /newtrash setinterval <segundos>"));
                return true;
            }
            try {
                int v = Math.max(5, Integer.parseInt(args[1]));
                intervalSeconds = v;
                config.set("intervalo.segundos", v);
                saveConfigSafe();
                restartTask();
                sender.sendMessage(prefix + color("&aIntervalo definido para &f"+v+"s&a."));
            } catch (NumberFormatException e) {
                sender.sendMessage(color("&cValor inválido."));
            }
            return true;
        }

        sender.sendMessage(color("&cSubcomando desconhecido."));
        return true;
    }

    private void saveConfigSafe() {
        try { config.save(configFile); }
        catch (IOException e) {
            plugin.getLogger().severe("[NewTrash] Erro ao salvar newtrash.yml: " + e.getMessage());
        }
    }

    // ===================== TAB COMPLETE =====================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            String a = args[0].toLowerCase(Locale.ROOT);
            addIfMatch(out, "agora", a);
            addIfMatch(out, "status", a);
            if (sender.hasPermission("newtrash.admin")) {
                addIfMatch(out, "reload", a);
                addIfMatch(out, "setinterval", a);
            }
            return out;
        }
        return Collections.emptyList();
    }

    private void addIfMatch(List<String> list, String candidate, String prefix) {
        if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) list.add(candidate);
    }

    public void shutdown() { stopTask(); }
}
