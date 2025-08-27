package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoginConfig {

    private final NewCore plugin;
    private File file;
    private FileConfiguration config;

    public LoginConfig(NewCore plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            file = new File(plugin.getDataFolder(), "ConfigLogin.yml");
            if (!file.exists()) {
                plugin.saveResource("ConfigLogin.yml", false);
            }
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().severe("[NewLogin] Falha ao carregar ConfigLogin.yml: " + e.getMessage());
        }
    }

    public void save() {
        try { if (config != null) config.save(file); }
        catch (IOException ignored) {}
    }

    // ===== Geral =====
    public boolean isEnabled() {
        return config.getBoolean("geral.enabled", true);
    }

    public String getPrefix() {
        return color(config.getString("geral.prefix", "&3&lNew&bLogin &7» "));
    }

    public int getMinLength() {
        return config.getInt("geral.min-password-length", 4);
    }

    public int getMaxLength() {
        return config.getInt("geral.max-password-length", 32);
    }

    public boolean blockCommandsBeforeLogin() {
        return config.getBoolean("geral.block-commands-before-login", true);
    }

    public List<String> allowedCommandsBeforeLogin() {
        List<String> list = config.getStringList("geral.allowed-commands-before-login");
        List<String> out = new ArrayList<String>();
        for (String s : list) if (s != null) out.add(s.toLowerCase());
        return out;
    }

    public List<String> hideFromConsole() {
        List<String> list = config.getStringList("geral.hide-from-console");
        List<String> out = new ArrayList<String>();
        for (String s : list) if (s != null) out.add(s.toLowerCase());
        return out;
    }

    // ===== Timeout =====
    public int getTimeoutSeconds() {
        return config.getInt("timeout.seconds", 60);
    }

    public String getTimeoutMessage() {
        return prefixed("timeout.message", "&cVocê foi kickado por inatividade ao logar!");
    }

    // ===== Join/Spawn/Saída =====
    public boolean teleportToSpawnOnJoin() {
        return config.getBoolean("on-join.teleport-to-spawn", true);
    }

    public Location getSpawn() {
        String s = config.getString("spawn", null);
        return deserializeLocation(s);
    }

    public void setSpawn(Location loc) {
        config.set("spawn", serializeLocation(loc));
        save();
    }

    public Location getSaida() {
        String s = config.getString("saida", null);
        return deserializeLocation(s);
    }

    public void setSaida(Location loc) {
        config.set("saida", serializeLocation(loc));
        save();
    }

    // ===== Mensagens =====
    public String msg(String key) {
        return getPrefix() + color(config.getString("mensagens." + key, "&cMensagem não definida: " + key));
    }

    public String prefixed(String path, String def) {
        return getPrefix() + color(config.getString(path, def));
    }

    // ===== Utils =====
    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location deserializeLocation(String s) {
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
