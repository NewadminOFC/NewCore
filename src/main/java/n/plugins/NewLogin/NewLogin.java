package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.Location;

public class NewLogin {

    private final NewCore plugin;
    private LoginManager loginManager;
    private LoginListener loginListener;
    private LoginTask loginTask;
    private LoginConfig cfg;

    public NewLogin(NewCore plugin) {
        this.plugin = plugin;
    }

    public void init() {
        cfg = new LoginConfig(plugin);

        loginManager = new LoginManager(plugin);
        loginManager.init();

        loginListener = new LoginListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(loginListener, plugin);

        loginTask = new LoginTask(plugin, this);
        loginTask.start();

        plugin.getLogger().info("[NewLogin] módulo de login carregado.");
    }

    public void shutdown() {
        if (loginManager != null) loginManager.close();
        if (loginTask != null) loginTask.stop();
    }

    // ===== Delegações de spawn/saída para o Config =====
    public void setSpawn(Location loc) { cfg.setSpawn(loc); }
    public void setSaida(Location loc) { cfg.setSaida(loc); }
    public Location getSpawn() { return cfg.getSpawn(); }
    public Location getSaida() { return cfg.getSaida(); }

    // ===== Getters =====
    public LoginManager getLoginManager() { return loginManager; }
    public LoginTask getLoginTask() { return loginTask; }
    public LoginConfig getConfig() { return cfg; }
}
