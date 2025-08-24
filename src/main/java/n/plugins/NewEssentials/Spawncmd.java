// File: src/main/java/n/plugins/NewEssentials/spawncmd.java
package n.plugins.NewEssentials;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public final class Spawncmd implements CommandExecutor, TabCompleter, Listener {

    private final JavaPlugin core;

    // ===== SQLite =====
    private Connection conn;

    // ===== Config =====
    private File cfgFile;
    private org.bukkit.configuration.file.YamlConfiguration cfg;

    // toggles
    private boolean ENABLED_SPAWN = true;
    private boolean ENABLED_SETSPAWN = true;
    private boolean REQUIRE_PERM_SPAWN = true;
    private boolean REQUIRE_PERM_SETSPAWN = true;
    private boolean PLAY_SOUND = true;
    private String SOUND_NAME = "LEVEL_UP"; // 1.7.10
    private int COOLDOWN_SECONDS = 0;       // /spawn
    private int WARMUP_SECONDS = 0;         // /spawn
    private boolean CANCEL_ON_MOVE = true;  // /spawn
    private boolean FIRST_JOIN_TP = false;  // teleporta no primeiro login
    private final Set<String> BLOCKED_WORLDS = new HashSet<String>(); // nomes exatos

    // messages
    private String MSG_ONLY_PLAYER   = "&cApenas jogadores podem usar esse comando.";
    private String MSG_NO_PERM_SET   = "&cVocê não tem permissão para usar /setspawn.";
    private String MSG_NO_PERM_SPAWN = "&cVocê não tem permissão para usar /spawn.";
    private String MSG_DISABLED      = "&cEste comando está desativado.";
    private String MSG_BLOCKED_WORLD = "&cVocê não pode usar este comando neste mundo.";
    private String MSG_SPAWN_SET     = "&aSpawn definido com sucesso!";
    private String MSG_SPAWN_TP      = "&aTeleportado para o spawn.";
    private String MSG_SPAWN_NOT_SET = "&cO spawn ainda não foi definido.";
    private String MSG_COOLDOWN      = "&eAguarde &f%sec%s &epara usar /spawn novamente.";
    private String MSG_WARMUP        = "&7Teleportando em &f%sec%s&7. Não se mova!";
    private String MSG_CANCELLED     = "&cTeleporte cancelado por movimento.";
    private String MSG_SQL_FAIL      = "&cFalha ao acessar banco de dados. Veja o console.";

    // ===== Estado (cooldown e warmup) =====
    private final Map<UUID, Long> lastSpawnUse = new HashMap<UUID, Long>();
    private final Map<UUID, Integer> warmupTask = new HashMap<UUID, Integer>();
    private final Map<UUID, Location> warmupStartLoc = new HashMap<UUID, Location>();

    public Spawncmd(JavaPlugin core){
        this.core = core;
        loadConfig();
        initDb();
        Bukkit.getPluginManager().registerEvents(this, core);
    }

    public void shutdown(){
        cancelAllWarmups();
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
    }

    // ===== Commands =====
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if ("setspawn".equalsIgnoreCase(label)) {
            if (!ENABLED_SETSPAWN){ sender.sendMessage(color(MSG_DISABLED)); return true; }
            if (!(sender instanceof Player)) { sender.sendMessage(color(MSG_ONLY_PLAYER)); return true; }
            Player p = (Player) sender;
            if (REQUIRE_PERM_SETSPAWN && !p.hasPermission("new.setspawn")) { p.sendMessage(color(MSG_NO_PERM_SET)); return true; }

            if (isBlockedWorld(p.getWorld().getName())) { p.sendMessage(color(MSG_BLOCKED_WORLD)); return true; }

            Location loc = p.getLocation();
            boolean ok = saveSpawn(loc);
            if (ok) {
                p.sendMessage(color(MSG_SPAWN_SET));
                if (PLAY_SOUND) playSoundSafe(p, SOUND_NAME, 1f, 1.2f);
            } else {
                p.sendMessage(color(MSG_SQL_FAIL));
            }
            return true;
        }

        if ("spawn".equalsIgnoreCase(label)) {
            if (!ENABLED_SPAWN){ sender.sendMessage(color(MSG_DISABLED)); return true; }
            if (!(sender instanceof Player)) { sender.sendMessage(color(MSG_ONLY_PLAYER)); return true; }
            final Player p = (Player) sender;
            if (REQUIRE_PERM_SPAWN && !p.hasPermission("new.spawn")) { p.sendMessage(color(MSG_NO_PERM_SPAWN)); return true; }

            if (isBlockedWorld(p.getWorld().getName())) { p.sendMessage(color(MSG_BLOCKED_WORLD)); return true; }

            final Location spawn = getSpawn();
            if (spawn == null) { p.sendMessage(color(MSG_SPAWN_NOT_SET)); return true; }

            // cooldown
            if (COOLDOWN_SECONDS > 0) {
                Long last = lastSpawnUse.get(p.getUniqueId());
                long now = System.currentTimeMillis();
                long wait = (last == null) ? 0L : (COOLDOWN_SECONDS*1000L - (now - last));
                if (wait > 0){
                    long secs = (wait + 999)/1000;
                    p.sendMessage(color(MSG_COOLDOWN.replace("%sec%", String.valueOf(secs))));
                    return true;
                }
            }

            // warmup
            if (WARMUP_SECONDS > 0) {
                startWarmup(p, spawn);
            } else {
                // teleporta direto
                doTeleport(p, spawn);
                if (COOLDOWN_SECONDS > 0) lastSpawnUse.put(p.getUniqueId(), System.currentTimeMillis());
            }
            return true;
        }

        return false;
    }

    // ===== Warmup =====
    private void startWarmup(final Player p, final Location target){
        // cancela warmup antigo
        cancelWarmup(p.getUniqueId());

        final int[] secondsLeft = new int[]{ WARMUP_SECONDS };
        warmupStartLoc.put(p.getUniqueId(), p.getLocation().clone());

        p.sendMessage(color(MSG_WARMUP.replace("%sec%", String.valueOf(secondsLeft[0]))));

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(core, new Runnable(){
            @Override public void run(){
                // checa cancelamento por movimento
                if (CANCEL_ON_MOVE && moved(p, warmupStartLoc.get(p.getUniqueId()))){
                    p.sendMessage(color(MSG_CANCELLED));
                    cancelWarmup(p.getUniqueId());
                    return;
                }

                secondsLeft[0]--;
                if (secondsLeft[0] <= 0){
                    cancelWarmup(p.getUniqueId()); // encerra tarefa
                    doTeleport(p, target);
                    if (COOLDOWN_SECONDS > 0) lastSpawnUse.put(p.getUniqueId(), System.currentTimeMillis());
                } else {
                    p.sendMessage(color(MSG_WARMUP.replace("%sec%", String.valueOf(secondsLeft[0]))));
                }
            }
        }, 20L, 20L);

        warmupTask.put(p.getUniqueId(), taskId);
    }

    private void cancelWarmup(UUID id){
        Integer t = warmupTask.remove(id);
        if (t != null) Bukkit.getScheduler().cancelTask(t);
        warmupStartLoc.remove(id);
    }

    private void cancelAllWarmups(){
        for (Integer id : new ArrayList<Integer>(warmupTask.values())){
            Bukkit.getScheduler().cancelTask(id);
        }
        warmupTask.clear();
        warmupStartLoc.clear();
    }

    private boolean moved(Player p, Location start){
        if (start == null) return false;
        Location now = p.getLocation();
        // cancela se mudou X/Z/Y inteiro (sensível)
        return now.getBlockX() != start.getBlockX()
                || now.getBlockY() != start.getBlockY()
                || now.getBlockZ() != start.getBlockZ();
    }

    private void doTeleport(Player p, Location to){
        p.teleport(to);
        p.sendMessage(color(MSG_SPAWN_TP));
        if (PLAY_SOUND) playSoundSafe(p, SOUND_NAME, 1f, 1.0f);
    }

    // ===== Listeners =====
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e){
        if (!CANCEL_ON_MOVE) return;
        if (!warmupTask.containsKey(e.getPlayer().getUniqueId())) return;
        // checagem fina já é feita no tick; aqui, se quiser, poderíamos cancelar imediatamente.
        // Mantemos lógica no tick para simplicidade/compat 1.7
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        if (!FIRST_JOIN_TP) return;
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()){
            Location s = getSpawn();
            if (s != null) {
                Bukkit.getScheduler().runTaskLater(core, new Runnable(){
                    @Override public void run(){ if (p.isOnline()) p.teleport(s); }
                }, 1L);
            }
        }
    }

    // ===== Tab =====
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return java.util.Collections.emptyList();
    }

    // ===== DB =====
    private void initDb(){
        try{
            if(!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
            File f = new File(core.getDataFolder(), "newessentials.db");
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());

            Statement st = null;
            try{
                st = conn.createStatement();
                st.executeUpdate("CREATE TABLE IF NOT EXISTS essentials_spawn (" +
                        "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                        "world TEXT NOT NULL," +
                        "x REAL NOT NULL," +
                        "y REAL NOT NULL," +
                        "z REAL NOT NULL," +
                        "yaw REAL NOT NULL," +
                        "pitch REAL NOT NULL," +
                        "updated_at INTEGER NOT NULL)");
            } finally {
                if(st!=null) try{ st.close(); } catch(Exception ignored){}
            }
            core.getLogger().info("[NewEssentials] SQLite OK (spawn)");
        }catch (Throwable t){
            core.getLogger().severe("[NewEssentials] ERRO SQLite (spawn): " + t.getMessage());
            conn = null;
        }
    }

    private boolean saveSpawn(Location loc){
        if (conn == null) return false;

        PreparedStatement ps = null;
        try{
            ps = conn.prepareStatement("UPDATE essentials_spawn SET world=?, x=?, y=?, z=?, yaw=?, pitch=?, updated_at=? WHERE id=1");
            ps.setString(1, loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setDouble(5, loc.getYaw());
            ps.setDouble(6, loc.getPitch());
            ps.setLong(7, System.currentTimeMillis());
            int rows = ps.executeUpdate();
            ps.close(); ps = null;

            if (rows == 0){
                ps = conn.prepareStatement("INSERT INTO essentials_spawn(id, world, x, y, z, yaw, pitch, updated_at) VALUES(1,?,?,?,?,?,?,?)");
                ps.setString(1, loc.getWorld().getName());
                ps.setDouble(2, loc.getX());
                ps.setDouble(3, loc.getY());
                ps.setDouble(4, loc.getZ());
                ps.setDouble(5, loc.getYaw());
                ps.setDouble(6, loc.getPitch());
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return true;
        }catch (SQLException e){
            core.getLogger().severe("[NewEssentials] Falha ao salvar spawn: " + e.getMessage());
            return false;
        } finally {
            if(ps!=null) try{ ps.close(); }catch(Exception ignored){}
        }
    }

    private Location getSpawn(){
        if (conn == null) return null;

        PreparedStatement ps = null;
        ResultSet rs = null;
        try{
            ps = conn.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM essentials_spawn WHERE id=1");
            rs = ps.executeQuery();
            if (!rs.next()) return null;

            String worldName = rs.getString(1);
            double x = rs.getDouble(2);
            double y = rs.getDouble(3);
            double z = rs.getDouble(4);
            float yaw = (float) rs.getDouble(5);
            float pitch = (float) rs.getDouble(6);

            World w = Bukkit.getWorld(worldName);
            if (w == null) return null;

            return new Location(w, x, y, z, yaw, pitch);
        }catch (SQLException e){
            core.getLogger().severe("[NewEssentials] Falha ao ler spawn: " + e.getMessage());
            return null;
        } finally {
            try{ if(rs!=null) rs.close(); }catch(Exception ignored){}
            try{ if(ps!=null) ps.close(); }catch(Exception ignored){}
        }
    }

    // ===== Config =====
    private void loadConfig(){
        if (!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
        cfgFile = new File(core.getDataFolder(), "NewEssentials.yml");
        if (!cfgFile.exists()){
            try { cfgFile.createNewFile(); } catch (IOException ignored) {}
        }
        cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile);

        // defaults
        setDef("spawn.enabled.spawn", ENABLED_SPAWN);
        setDef("spawn.enabled.setspawn", ENABLED_SETSPAWN);
        setDef("spawn.require-permission.spawn", REQUIRE_PERM_SPAWN);
        setDef("spawn.require-permission.setspawn", REQUIRE_PERM_SETSPAWN);
        setDef("spawn.cooldown-seconds", COOLDOWN_SECONDS);
        setDef("spawn.warmup-seconds", WARMUP_SECONDS);
        setDef("spawn.cancel-on-move", CANCEL_ON_MOVE);
        setDef("spawn.first-join-teleport", FIRST_JOIN_TP);
        setDef("spawn.play-sound", PLAY_SOUND);
        setDef("spawn.sound", SOUND_NAME);
        setDef("spawn.blocked-worlds", new ArrayList<String>());

        setDef("spawn.messages.only-player", MSG_ONLY_PLAYER);
        setDef("spawn.messages.no-perm-set", MSG_NO_PERM_SET);
        setDef("spawn.messages.no-perm-spawn", MSG_NO_PERM_SPAWN);
        setDef("spawn.messages.disabled", MSG_DISABLED);
        setDef("spawn.messages.blocked-world", MSG_BLOCKED_WORLD);
        setDef("spawn.messages.spawn-set", MSG_SPAWN_SET);
        setDef("spawn.messages.spawn-tp", MSG_SPAWN_TP);
        setDef("spawn.messages.spawn-not-set", MSG_SPAWN_NOT_SET);
        setDef("spawn.messages.cooldown", MSG_COOLDOWN);
        setDef("spawn.messages.warmup", MSG_WARMUP);
        setDef("spawn.messages.cancelled", MSG_CANCELLED);
        setDef("spawn.messages.sql-fail", MSG_SQL_FAIL);

        saveCfg();

        // read
        ENABLED_SPAWN = cfg.getBoolean("spawn.enabled.spawn", ENABLED_SPAWN);
        ENABLED_SETSPAWN = cfg.getBoolean("spawn.enabled.setspawn", ENABLED_SETSPAWN);
        REQUIRE_PERM_SPAWN = cfg.getBoolean("spawn.require-permission.spawn", REQUIRE_PERM_SPAWN);
        REQUIRE_PERM_SETSPAWN = cfg.getBoolean("spawn.require-permission.setspawn", REQUIRE_PERM_SETSPAWN);
        COOLDOWN_SECONDS = cfg.getInt("spawn.cooldown-seconds", COOLDOWN_SECONDS);
        WARMUP_SECONDS = cfg.getInt("spawn.warmup-seconds", WARMUP_SECONDS);
        CANCEL_ON_MOVE = cfg.getBoolean("spawn.cancel-on-move", CANCEL_ON_MOVE);
        FIRST_JOIN_TP = cfg.getBoolean("spawn.first-join-teleport", FIRST_JOIN_TP);
        PLAY_SOUND = cfg.getBoolean("spawn.play-sound", PLAY_SOUND);
        SOUND_NAME = cfg.getString("spawn.sound", SOUND_NAME);

        BLOCKED_WORLDS.clear();
        List<String> worlds = cfg.getStringList("spawn.blocked-worlds");
        if (worlds != null) for (String w : worlds) if (w != null) BLOCKED_WORLDS.add(w);

        MSG_ONLY_PLAYER   = cfg.getString("spawn.messages.only-player", MSG_ONLY_PLAYER);
        MSG_NO_PERM_SET   = cfg.getString("spawn.messages.no-perm-set", MSG_NO_PERM_SET);
        MSG_NO_PERM_SPAWN = cfg.getString("spawn.messages.no-perm-spawn", MSG_NO_PERM_SPAWN);
        MSG_DISABLED      = cfg.getString("spawn.messages.disabled", MSG_DISABLED);
        MSG_BLOCKED_WORLD = cfg.getString("spawn.messages.blocked-world", MSG_BLOCKED_WORLD);
        MSG_SPAWN_SET     = cfg.getString("spawn.messages.spawn-set", MSG_SPAWN_SET);
        MSG_SPAWN_TP      = cfg.getString("spawn.messages.spawn-tp", MSG_SPAWN_TP);
        MSG_SPAWN_NOT_SET = cfg.getString("spawn.messages.spawn-not-set", MSG_SPAWN_NOT_SET);
        MSG_COOLDOWN      = cfg.getString("spawn.messages.cooldown", MSG_COOLDOWN);
        MSG_WARMUP        = cfg.getString("spawn.messages.warmup", MSG_WARMUP);
        MSG_CANCELLED     = cfg.getString("spawn.messages.cancelled", MSG_CANCELLED);
        MSG_SQL_FAIL      = cfg.getString("spawn.messages.sql-fail", MSG_SQL_FAIL);
    }

    private void setDef(String path, Object val){
        if (!cfg.contains(path)) cfg.set(path, val);
    }
    private void saveCfg(){ try { cfg.save(cfgFile); } catch (IOException ignored) {} }

    private boolean isBlockedWorld(String w){ return BLOCKED_WORLDS.contains(w); }

    // ===== Utils =====
    private static String color(String s){ return ChatColor.translateAlternateColorCodes('&', s); }
    private void playSoundSafe(Player p, String name, float vol, float pit){
        try{
            Sound s = Sound.valueOf(name.toUpperCase());
            p.playSound(p.getLocation(), s, vol, pit);
        }catch(Throwable ignore){}
    }
}
