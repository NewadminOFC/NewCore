package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;

public class NewLogin {

    private final NewCore plugin;
    private LoginManager loginManager;
    private Location spawnLocation;
    private Location saidaLocation;

    public NewLogin(NewCore plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.saveDefaultConfig();

        // inicializa SQLite
        loginManager = new LoginManager(plugin);
        loginManager.init();

        // listeners
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new LoginListener(plugin, this), plugin);

        // carrega spawns do config
        reloadSpawnAndSaida();
        plugin.getLogger().info("[NewLogin] módulo de login carregado.");
    }

    public void shutdown() {
        if (loginManager != null) loginManager.close();
    }

    public LoginManager getLoginManager() {
        return loginManager;
    }

    public int getMinDigits() {
        return plugin.getConfig().getInt("senha.min", 4);
    }

    public int getMaxDigits() {
        return plugin.getConfig().getInt("senha.max", 16);
    }

    public void setSpawn(Location loc) {
        plugin.getConfig().set("spawn", serialize(loc));
        plugin.saveConfig();
        reloadSpawnAndSaida();
    }

    public void setSaida(Location loc) {
        plugin.getConfig().set("saida", serialize(loc));
        plugin.saveConfig();
        reloadSpawnAndSaida();
    }

    public Location getSpawn() {
        return spawnLocation;
    }

    public Location getSaida() {
        return saidaLocation;
    }

    public void reloadSpawnAndSaida() {
        spawnLocation = deserialize(plugin.getConfig().getString("spawn"));
        saidaLocation = deserialize(plugin.getConfig().getString("saida"));
    }

    private String serialize(Location loc) {
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
        } catch (Exception e) {
            return null;
        }
    }
}
