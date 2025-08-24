package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class NewLogin {

    private final NewCore plugin;
    private LoginManager loginManager;
    private LoginListener loginListener;
    private LoginTask loginTask;

    private Location spawnLocation;
    private Location saidaLocation;

    private File configFile;
    private FileConfiguration config;

    public NewLogin(NewCore plugin) {
        this.plugin = plugin;
    }

    public void init() {
        loadConfig();

        loginManager = new LoginManager(plugin);
        loginManager.init();

        loginListener = new LoginListener(plugin, this);
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(loginListener, plugin);

        loginTask = new LoginTask(plugin, this);
        loginTask.start();

        reloadSpawnAndSaida();

        plugin.getLogger().info("[NewLogin] módulo de login carregado.");
    }

    public void shutdown() {
        if (loginManager != null) loginManager.close();
        if (loginTask != null) loginTask.stop();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "ConfigLogin.yml");
        if (!configFile.exists()) plugin.saveResource("ConfigLogin.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() { return config; }

    public void saveConfig() {
        try { config.save(configFile); }
        catch (IOException e) { e.printStackTrace(); }
    }

    public int getMinDigits() { return config.getInt("senha.min", 4); }
    public int getMaxDigits() { return config.getInt("senha.max", 16); }

    public int getTimeoutSeconds() { return config.getInt("timeout.tempo_segundos", 60); }
    public String getTimeoutMessage() { return config.getString("timeout.mensagem", "§cVocê foi kickado por inatividade ao logar!"); }

    public void setSpawn(Location loc) {
        config.set("spawn", serialize(loc));
        saveConfig();
        reloadSpawnAndSaida();
    }

    public void setSaida(Location loc) {
        config.set("saida", serialize(loc));
        saveConfig();
        reloadSpawnAndSaida();
    }

    public Location getSpawn() { return spawnLocation; }
    public Location getSaida() { return saidaLocation; }

    public void reloadSpawnAndSaida() {
        spawnLocation = deserialize(config.getString("spawn"));
        saidaLocation = deserialize(config.getString("saida"));
    }

    private String serialize(Location loc) {
        if (loc == null) return null;
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location deserialize(String s) {
        if (s == null) return null;
        try {
            String[] p = s.split(",");
            return new Location(Bukkit.getWorld(p[0]),
                    Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]),
                    Float.parseFloat(p[5]));
        } catch (Exception e) { return null; }
    }

    public LoginManager getLoginManager() { return loginManager; }
    public LoginTask getLoginTask() { return loginTask; }
}
