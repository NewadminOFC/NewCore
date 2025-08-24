package n.plugins.warps;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NewWarps implements Listener, CommandExecutor, TabCompleter {

    private final JavaPlugin core;

    private File cfgFile;
    private YamlConfiguration cfg;

    public NewWarps(JavaPlugin core) {
        this.core = core;
        loadConfigWarps();
    }

    /* ==================== CONFIG ==================== */
    private void loadConfigWarps() {
        if (!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
        cfgFile = new File(core.getDataFolder(), "Configwarps.yml");
        if (!cfgFile.exists()) {
            try { core.saveResource("Configwarps.yml", false); } catch (Throwable ignore) {}
        }
        cfg = YamlConfiguration.loadConfiguration(cfgFile);
        if (!cfg.contains("warps")) cfg.set("warps", null); // garante nó
        saveCfgSilent();
    }
    private void reloadConfigWarps() {
        if (cfgFile == null) cfgFile = new File(core.getDataFolder(), "Configwarps.yml");
        cfg = YamlConfiguration.loadConfiguration(cfgFile);
    }
    private void saveCfgSilent() {
        if (cfg == null || cfgFile == null) return;
        try { cfg.save(cfgFile); } catch (IOException ignored) {}
    }

    /* ==================== COMANDOS ==================== */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(color(getMsg("apenas-jogadores")));
            return true;
        }
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("setwarp")) {
            if (args.length != 1) return false;
            String warp = args[0].toLowerCase();
            Location loc = p.getLocation();

            String base = "warps." + warp;
            cfg.set(base + ".world", loc.getWorld().getName());
            cfg.set(base + ".x", loc.getX());
            cfg.set(base + ".y", loc.getY());
            cfg.set(base + ".z", loc.getZ());
            cfg.set(base + ".yaw", loc.getYaw());
            cfg.set(base + ".pitch", loc.getPitch());
            if (!cfg.contains(base + ".permission")) cfg.set(base + ".permission", "warp." + warp);
            if (!cfg.contains(base + ".mensagem"))   cfg.set(base + ".mensagem", "&a" + warp);

            saveCfgSilent();
            p.sendMessage(color(getMsg("warp-set").replace("{warp}", warp)));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("warp")) {
            if (args.length == 1) {

                if (args[0].equalsIgnoreCase("reload")) {
                    if (!p.hasPermission("warp.reload")) {
                        p.sendMessage(color(getMsg("sem-permissao")));
                        return true;
                    }
                    reloadConfigWarps();
                    p.sendMessage(color(getMsg("reload")));
                    return true;
                }

                if (args[0].equalsIgnoreCase("list")) {
                    if (!cfg.contains("warps") ||
                            cfg.getConfigurationSection("warps") == null ||
                            cfg.getConfigurationSection("warps").getKeys(false).isEmpty()) {
                        p.sendMessage(color(getMsg("nenhum-warp")));
                        return true;
                    }
                    Set<String> warps = cfg.getConfigurationSection("warps").getKeys(false);
                    String lista = join(warps, ", ");
                    p.sendMessage(color(getMsg("warps-lista").replace("{lista}", lista)));
                    return true;
                }

                // /warp <nome>
                String warp = args[0].toLowerCase();
                String base = "warps." + warp;
                if (!cfg.contains(base)) {
                    p.sendMessage(color(getMsg("warp-nao-existe").replace("{warp}", warp)));
                    return true;
                }

                String perm = cfg.getString(base + ".permission", "warp." + warp);

                boolean bypassGeral = cfg.getBoolean("bypass.geral", false);
                boolean bypassVip   = cfg.getBoolean("bypass.vip", false);

                boolean hasBypass = false;
                if (bypassGeral) {
                    hasBypass = true;
                } else if (bypassVip && p.hasPermission("warp.vip.bypass")) {
                    hasBypass = true;
                }

                if (!p.hasPermission(perm) && !hasBypass) {
                    p.sendMessage(color(getMsg("sem-permissao")));
                    return true;
                }

                World w = Bukkit.getWorld(cfg.getString(base + ".world", "world"));
                if (w == null) {
                    p.sendMessage(color(getMsg("world-not-loaded")));
                    return true;
                }

                double x = cfg.getDouble(base + ".x");
                double y = cfg.getDouble(base + ".y");
                double z = cfg.getDouble(base + ".z");
                float yaw = (float) cfg.getDouble(base + ".yaw");
                float pitch = (float) cfg.getDouble(base + ".pitch");
                p.teleport(new Location(w, x, y, z, yaw, pitch));

                // título e msg
                String mensagemWarp = cfg.getString(base + ".mensagem", "&a" + warp);
                boolean mostrarTitulo      = cfg.getBoolean("geral.message-title", true);
                boolean mostrarCoordTitulo = cfg.getBoolean("geral.message-title-coord", false);

                String titulo = mensagemWarp.replace("&", "§");
                String subtitulo = "";
                if (mostrarCoordTitulo) {
                    subtitulo = "§7X: " + (int)x + "  Y: " + (int)y + "  Z: " + (int)z;
                }
                if (mostrarTitulo) {
                    enviarTitulo(p, titulo, subtitulo, 10, 40, 10);
                }
                p.sendMessage(color(getMsg("warp-teleport").replace("{warp}", titulo)));
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("removewarp")) {
            if (args.length != 1) return false;
            String warp = args[0].toLowerCase();
            String base = "warps." + warp;

            if (!cfg.contains(base)) {
                p.sendMessage(color(getMsg("warp-nao-existe").replace("{warp}", warp)));
                return true;
            }
            cfg.set(base, null);
            saveCfgSilent();
            p.sendMessage(color(getMsg("warp-remove").replace("{warp}", warp)));
            return true;
        }

        return false;
    }

    /* ==================== TAB COMPLETE ==================== */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("warp")) return Collections.emptyList();

        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            String q = args[0].toLowerCase();

            // subcomandos
            if ("reload".startsWith(q)) out.add("reload");
            if ("list".startsWith(q)) out.add("list");

            // nomes de warp
            if (cfg != null && cfg.getConfigurationSection("warps") != null) {
                for (String w : cfg.getConfigurationSection("warps").getKeys(false)) {
                    if (w.toLowerCase().startsWith(q)) out.add(w);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }

    /* ==================== HELPERS ==================== */
    private String color(String msg) {
        String prefix = cfg.getString("geral.prefix", "");
        return (prefix + (msg == null ? "" : msg)).replace("&", "§");
    }
    private String getMsg(String key) {
        return cfg.getString("messages." + key, "&cMensagem não configurada: " + key);
    }
    private String join(Iterable<String> it, String sep) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String s : it) {
            if (!first) sb.append(sep);
            sb.append(s);
            first = false;
        }
        return sb.toString();
    }

    // compat 1.8+
    public void enviarTitulo(Player p, String titulo, String subtitulo, int fadeIn, int stay, int fadeOut) {
        try {
            p.sendTitle(titulo, subtitulo);
        } catch (NoSuchMethodError e) {
            try {
                String version = core.getServer().getClass().getPackage().getName().split("\\.")[3];
                Object handle = p.getClass().getMethod("getHandle").invoke(p);
                Object connection = handle.getClass().getField("playerConnection").get(handle);

                Class<?> packetTitle = Class.forName("net.minecraft.server." + version + ".PacketPlayOutTitle");
                Class<?> enumTitleAction = packetTitle.getDeclaredClasses()[0];
                Object titleEnum = enumTitleAction.getEnumConstants()[0]; // TITLE
                Object subtitleEnum = enumTitleAction.getEnumConstants()[1]; // SUBTITLE

                Class<?> chatComponent = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
                Class<?> chatSerializer = chatComponent.getDeclaredClasses()[0];

                Object titleComponent = chatSerializer.getMethod("a", String.class)
                        .invoke(null, "{\"text\":\"" + titulo.replace("§", "\\u00A7") + "\"}");
                Object subtitleComponent = chatSerializer.getMethod("a", String.class)
                        .invoke(null, "{\"text\":\"" + subtitulo.replace("§", "\\u00A7") + "\"}");

                Object titlePacket = packetTitle.getConstructor(enumTitleAction, chatComponent, int.class, int.class, int.class)
                        .newInstance(titleEnum, titleComponent, fadeIn, stay, fadeOut);
                Object subtitlePacket = packetTitle.getConstructor(enumTitleAction, chatComponent, int.class, int.class, int.class)
                        .newInstance(subtitleEnum, subtitleComponent, fadeIn, stay, fadeOut);

                Class<?> packet = Class.forName("net.minecraft.server." + version + ".Packet");
                connection.getClass().getMethod("sendPacket", packet).invoke(connection, titlePacket);
                connection.getClass().getMethod("sendPacket", packet).invoke(connection, subtitlePacket);
            } catch (Exception ignored) {}
        }
    }
}
