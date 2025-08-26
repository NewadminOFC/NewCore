package n.plugins.NewPlots;

import n.plugins.NewCore;
import n.plugins.NewEconomy.NewEconomyAPI;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NewPlots implements CommandExecutor, TabCompleter, Listener {

    private final NewCore plugin;
    private final NewPlotsDB db;

    private String worldName;
    private int plotSize;
    private int roadWidth;
    private int pitch;
    private long priceCents;

    private int roadId;
    private byte roadData;
    private int roadY;

    private BukkitTask daylockTask;

    private final Map<UUID, String> lastIndexKey = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotify = new ConcurrentHashMap<>();

    private static final int AIR = 0;
    private static final int GRASS = 2;
    private static final int DIRT = 3;

    private static final String P = "§8§l[§a§lPLOTS§8§l]§r ";

    public NewPlots(NewCore plugin) {
        this.plugin = plugin;

        File f = new File(plugin.getDataFolder(), "newplots.yml");
        if (!f.exists()) plugin.saveResource("newplots.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        this.worldName = cfg.getString("world", "terrenos");
        this.plotSize = cfg.getInt("plotSize", 64);
        this.roadWidth = 5;
        this.pitch = plotSize + roadWidth;

        this.priceCents = cfg.getLong("price_cents", 100000L);
        this.roadId = cfg.getInt("road_id", 1);
        this.roadData = (byte) cfg.getInt("road_data", 0);
        this.roadY = 5;

        ensureWorld();
        startDaylock();

        Bukkit.getScheduler().runTaskLater(plugin, this::repaintLoadedChunks, 1L);

        this.db = new NewPlotsDB(plugin);
    }

    public void shutdown() {
        if (daylockTask != null) daylockTask.cancel();
        db.close();
    }

    private void ensureWorld() {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            WorldCreator wc = new WorldCreator(worldName);
            wc.type(WorldType.FLAT);
            wc.generateStructures(false);
            w = Bukkit.createWorld(wc);
        }
        if (w != null) {
            w.setSpawnFlags(false, false);
            w.setStorm(false);
            w.setThundering(false);
            w.setWeatherDuration(9999999);
            w.setTime(6000);
            w.setSpawnLocation(0, roadY + 1, 0);
        }
    }

    private void startDaylock() {
        daylockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                w.setTime(6000);
                w.setStorm(false);
                w.setThundering(false);
            }
        }, 20L, 200L);
    }

    private void repaintLoadedChunks() {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;
        for (Chunk c : w.getLoadedChunks()) paintChunk(c);
    }

    private static int floorDiv(int a, int b) {
        int q = a / b;
        int r = a % b;
        if ((r != 0) && ((a ^ b) < 0)) q--;
        return q;
    }

    private static int floorMod(int a, int b) {
        int r = a % b;
        return (r >= 0) ? r : r + Math.abs(b);
    }

    private boolean isRoad(int x, int z) {
        int mx = floorMod(x, pitch);
        int mz = floorMod(z, pitch);
        return (mx >= plotSize) || (mz >= plotSize);
    }

    private Index toIndex(int x, int z) {
        return new Index(floorDiv(x, pitch), floorDiv(z, pitch));
    }

    private boolean isInsidePlotCell(int x, int z) {
        int mx = floorMod(x, pitch);
        int mz = floorMod(z, pitch);
        return mx >= 0 && mx < plotSize && mz >= 0 && mz < plotSize;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.getWorld().getName().equals(worldName)) return;
        paintChunkLater(e.getChunk());
    }

    private void paintChunkLater(Chunk c) {
        Bukkit.getScheduler().runTask(plugin, () -> paintChunk(c));
    }

    private void paintChunk(Chunk c) {
        World w = c.getWorld();
        int baseX = c.getX() << 4;
        int baseZ = c.getZ() << 4;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                if (isRoad(x, z)) paveRoad(w, x, z);
                else flattenPlot(w, x, z);
            }
        }
    }

    private void setBlockNoPhysics(World w, int x, int y, int z, int id, byte data) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getTypeId() == id && (id == AIR || b.getData() == data)) return;
        b.setTypeIdAndData(id, data, false);
    }

    private void setAir(World w, int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getTypeId() != AIR) b.setTypeIdAndData(AIR, (byte) 0, false);
    }

    private void flattenPlot(World w, int x, int z) {
        setAir(w, x, roadY + 2, z);
        setAir(w, x, roadY + 1, z);
        setBlockNoPhysics(w, x, roadY, z, GRASS, (byte) 0);
        setBlockNoPhysics(w, x, roadY - 1, z, DIRT, (byte) 0);
        setBlockNoPhysics(w, x, roadY - 2, z, DIRT, (byte) 0);
    }

    private void paveRoad(World w, int x, int z) {
        setAir(w, x, roadY + 2, z);
        setAir(w, x, roadY + 1, z);
        setBlockNoPhysics(w, x, roadY, z, roadId, roadData);
        setBlockNoPhysics(w, x, roadY - 1, z, DIRT, (byte) 0);
        setBlockNoPhysics(w, x, roadY - 2, z, DIRT, (byte) 0);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (!shouldAllow(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(P + "§cVocê não pode construir aqui.");
        }
        if (e.getBlock().getWorld().getName().equals(worldName) && isRoad(e.getBlock().getX(), e.getBlock().getZ())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(P + "§cVocê não pode construir na rua.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (!shouldAllow(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(P + "§cVocê não pode quebrar aqui.");
        }
        if (e.getBlock().getWorld().getName().equals(worldName) && isRoad(e.getBlock().getX(), e.getBlock().getZ())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(P + "§cVocê não pode quebrar a rua.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().sendMessage(P + "§7Use §a/plot auto §7para ir ao mundo §f" + worldName + " §7e pegar seu §eprimeiro plot grátis§7!");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (!e.getTo().getWorld().getName().equals(worldName)) return;

        int x = e.getTo().getBlockX();
        int z = e.getTo().getBlockZ();
        Index idx = toIndex(x, z);

        UUID u = e.getPlayer().getUniqueId();
        String key = idx.ix + ":" + idx.iz + ":" + (isRoad(x, z) ? "R" : "P");
        if (key.equals(lastIndexKey.get(u))) return;
        lastIndexKey.put(u, key);

        long now = System.currentTimeMillis();
        Long prev = lastNotify.get(u);
        if (prev != null && now - prev < 800) return;
        lastNotify.put(u, now);

        if (isRoad(x, z)) return;

        Optional<PlotRecord> rec = db.findByIndex(worldName, idx.ix, idx.iz);
        if (rec.isPresent()) {
            String ownerName = prettifyOwner(rec.get().owner);
            e.getPlayer().sendMessage(P + "§aPlot de §f" + ownerName + " §7em §8(" + idx.ix + "," + idx.iz + ")");
        } else {
            e.getPlayer().sendMessage(P + "§ePlot livre §7em §8(" + idx.ix + "," + idx.iz + ") §7→ §a/plot claim §7ou §a/plot auto");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (e.getLocation().getWorld() != null && e.getLocation().getWorld().getName().equals(worldName)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onWeather(WeatherChangeEvent e) {
        if (e.getWorld().getName().equals(worldName) && e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onThunder(ThunderChangeEvent e) {
        if (e.getWorld().getName().equals(worldName) && e.toThunderState()) e.setCancelled(true);
    }

    private boolean shouldAllow(Player p, Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        if (!loc.getWorld().getName().equals(worldName)) return true;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        if (isRoad(x, z)) return false;
        Index idx = toIndex(x, z);
        Optional<PlotRecord> claim = db.findByIndex(worldName, idx.ix, idx.iz);
        if (!claim.isPresent()) return false;
        if (claim.get().owner.equals(p.getUniqueId().toString())) return true;
        return db.isTrusted(worldName, idx.ix, idx.iz, p.getUniqueId().toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Somente jogadores.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0) {
            help(p, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("auto")) { handleAuto(p); return true; }
        if (sub.equals("claim")) { handleClaim(p); return true; }
        if (sub.equals("tp")) { if (args.length == 1) handleTpSelf(p); else handleTpOther(p, args[1]); return true; }
        if (sub.equals("info")) { handleInfo(p); return true; }
        if (sub.equals("unclaim")) { handleUnclaim(p); return true; }
        if (sub.equals("where")) { handleWhere(p); return true; }
        if (sub.equals("trust")) { if (args.length >= 2) { handleTrust(p, args[1]); } else { p.sendMessage(P + "§cUso: /" + label + " trust <nick>"); } return true; }
        if (sub.equals("untrust")) { if (args.length >= 2) { handleUntrust(p, args[1]); } else { p.sendMessage(P + "§cUso: /" + label + " untrust <nick>"); } return true; }
        help(p, label);
        return true;
    }

    private void help(Player p, String label) {
        p.sendMessage(P + "§7Comandos:");
        p.sendMessage(" §a/" + label + " auto §7→ pega o plot livre mais próximo e te leva lá §8(§e1º grátis§8)");
        p.sendMessage(" §a/" + label + " claim §7→ compra o §fplot onde você está§7, se estiver livre");
        p.sendMessage(" §a/" + label + " tp §7→ vai ao seu plot");
        p.sendMessage(" §a/" + label + " tp <jogador> §7→ vai ao plot do jogador");
        p.sendMessage(" §a/" + label + " info §7→ mostra dono do plot atual");
        p.sendMessage(" §a/" + label + " where §7→ mostra o índice do seu plot");
        p.sendMessage(" §a/" + label + " trust <nick> §7→ permite construir");
        p.sendMessage(" §a/" + label + " untrust <nick> §7→ remove permissão");
        p.sendMessage(" §a/" + label + " unclaim §7→ libera o seu plot atual");
    }

    private void handleAuto(Player p) {
        ensureWorld();
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            p.sendMessage(P + "§cFalha ao preparar o mundo §f" + worldName);
            return;
        }
        Index freeIdx = findNearestFreeIndex(0, 0, 1000);
        if (freeIdx == null) {
            p.sendMessage(P + "§cNão há plots livres próximos.");
            return;
        }
        buyPlotAndTeleport(p, w, freeIdx.ix, freeIdx.iz);
    }

    private void handleClaim(Player p) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            p.sendMessage(P + "§cMundo não encontrado: §f" + worldName);
            return;
        }
        if (!p.getWorld().getName().equals(worldName)) {
            p.sendMessage(P + "§cUse em §f" + worldName + " §cou §a/plot auto");
            return;
        }
        Index idx = toIndex(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
        if (!isInsidePlotCell(p.getLocation().getBlockX(), p.getLocation().getBlockZ())) {
            p.sendMessage(P + "§cFique dentro da área do plot para comprar.");
            return;
        }
        if (db.findByIndex(worldName, idx.ix, idx.iz).isPresent()) {
            p.sendMessage(P + "§cEste plot §8(" + idx.ix + "," + idx.iz + ") §cjá tem dono.");
            return;
        }
        buyPlotAndTeleport(p, w, idx.ix, idx.iz);
    }

    private void buyPlotAndTeleport(Player p, World w, int ix, int iz) {
        int owned = db.countByOwner(worldName, p.getUniqueId().toString());
        boolean free = owned == 0;
        long priceToPay = free ? 0L : priceCents;
        long bal = NewEconomyAPI.getBalance(p.getUniqueId());
        if (bal < priceToPay) {
            p.sendMessage(P + "§cSaldo insuficiente. Precisa de §f" + NewEconomyAPI.format(priceToPay) + "§c • Seu saldo: §f" + NewEconomyAPI.format(bal));
            return;
        }
        if (db.findByIndex(worldName, ix, iz).isPresent()) {
            p.sendMessage(P + "§cAlguém acabou de pegar este plot. Tente novamente.");
            return;
        }
        long id = db.insert(p.getUniqueId().toString(), worldName, ix, iz);
        if (id <= 0) {
            p.sendMessage(P + "§cErro ao registrar a compra. Tente novamente.");
            return;
        }
        if (priceToPay > 0) {
            boolean paid = NewEconomyAPI.withdraw(p.getUniqueId(), priceToPay);
            if (!paid) {
                db.deleteByIndex(worldName, ix, iz);
                p.sendMessage(P + "§cNão foi possível debitar o valor. Operação cancelada.");
                return;
            }
        }
        int minX = ix * pitch;
        int minZ = iz * pitch;
        int maxX = minX + pitch - 1;
        int maxZ = minZ + pitch - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (isRoad(x, z)) paveRoad(w, x, z); else flattenPlot(w, x, z);
            }
        }
        Location center = toPlotCenter(ix, iz);
        center.setWorld(w);
        center.setY(roadY + 1);
        p.teleport(center);
        SpawnCompat.setBedSpawn(p, center);
        String priceMsg = free ? "§eGRÁTIS" : "§f" + NewEconomyAPI.format(priceToPay);
        p.sendMessage(P + "§aPlot §8(" + ix + "," + iz + ") §aadquirido por " + priceMsg + "§a.");
        TitleCompat.send(p, "§a§lPLOT ADQUIRIDO", "§7" + (free ? "Seu primeiro plot!" : "Boa compra!"), 10, 40, 10);
    }

    private Index findNearestFreeIndex(int originIx, int originIz, int maxRings) {
        for (int r = 0; r <= maxRings; r++) {
            for (int ix = originIx - r; ix <= originIx + r; ix++) {
                for (int iz = originIz - r; iz <= originIz + r; iz++) {
                    if (db.findByIndex(worldName, ix, iz).isPresent()) continue;
                    return new Index(ix, iz);
                }
            }
        }
        return null;
    }

    private Location toPlotCenter(int ix, int iz) {
        int baseX = ix * pitch;
        int baseZ = iz * pitch;
        int cx = baseX + plotSize / 2;
        int cz = baseZ + plotSize / 2;
        return new Location(null, cx + 0.5, 100.0, cz + 0.5, 0f, 0f);
    }

    private void handleTpSelf(Player p) {
        Optional<PlotRecord> my = db.findFirstByOwner(worldName, p.getUniqueId().toString());
        if (!my.isPresent()) {
            p.sendMessage(P + "§cVocê não possui plot.");
            return;
        }
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            p.sendMessage(P + "§cMundo não encontrado: §f" + worldName);
            return;
        }
        Location center = toPlotCenter(my.get().ix, my.get().iz);
        center.setWorld(w);
        center.setY(roadY + 1);
        p.teleport(center);
        p.sendMessage(P + "§aTeleportado para §8(" + my.get().ix + "," + my.get().iz + ")§a.");
    }

    private void handleTpOther(Player p, String targetName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            p.sendMessage(P + "§cMundo não encontrado: §f" + worldName);
            return;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
        if (off == null || off.getUniqueId() == null) {
            p.sendMessage(P + "§cJogador não encontrado.");
            return;
        }
        Optional<PlotRecord> rec = db.findFirstByOwner(worldName, off.getUniqueId().toString());
        if (!rec.isPresent()) {
            p.sendMessage(P + "§eEsse jogador não possui plot.");
            return;
        }
        Location center = toPlotCenter(rec.get().ix, rec.get().iz);
        center.setWorld(w);
        center.setY(roadY + 1);
        p.teleport(center);
        p.sendMessage(P + "§aTeleportado para o plot de §f" + (off.getName() != null ? off.getName() : targetName) + " §7em §8(" + rec.get().ix + "," + rec.get().iz + ")§a.");
    }

    private void handleInfo(Player p) {
        if (!p.getWorld().getName().equals(worldName)) {
            p.sendMessage(P + "§7Você não está no mundo §f" + worldName + "§7.");
            return;
        }
        int x = p.getLocation().getBlockX();
        int z = p.getLocation().getBlockZ();
        if (isRoad(x, z)) {
            p.sendMessage(P + "§7Você está na rua.");
            return;
        }
        Index idx = toIndex(x, z);
        Optional<PlotRecord> rec = db.findByIndex(worldName, idx.ix, idx.iz);
        if (!rec.isPresent()) {
            p.sendMessage(P + "§ePlot livre §7em §8(" + idx.ix + "," + idx.iz + ")");
            return;
        }
        String ownerName = prettifyOwner(rec.get().owner);
        p.sendMessage(P + "§aPlot de §f" + ownerName + " §7em §8(" + idx.ix + "," + idx.iz + ")");
    }

    private void handleUnclaim(Player p) {
        if (!p.getWorld().getName().equals(worldName)) {
            p.sendMessage(P + "§cUse dentro do seu plot em §f" + worldName + "§c.");
            return;
        }
        int x = p.getLocation().getBlockX();
        int z = p.getLocation().getBlockZ();
        if (isRoad(x, z)) {
            p.sendMessage(P + "§cVocê não está dentro de um plot.");
            return;
        }
        Index idx = toIndex(x, z);
        Optional<PlotRecord> rec = db.findByIndex(worldName, idx.ix, idx.iz);
        if (!rec.isPresent()) {
            p.sendMessage(P + "§cEste plot não está reivindicado.");
            return;
        }
        if (!rec.get().owner.equals(p.getUniqueId().toString())) {
            p.sendMessage(P + "§cApenas o dono pode liberar este plot.");
            return;
        }
        db.deleteTrusts(worldName, idx.ix, idx.iz);
        boolean ok = db.deleteByIndex(worldName, idx.ix, idx.iz);
        if (ok) p.sendMessage(P + "§aPlot §8(" + idx.ix + "," + idx.iz + ") §aliberado.");
        else p.sendMessage(P + "§cFalha ao liberar plot.");
    }

    private void handleWhere(Player p) {
        Optional<PlotRecord> my = db.findFirstByOwner(worldName, p.getUniqueId().toString());
        if (!my.isPresent()) {
            p.sendMessage(P + "§cVocê não possui plot.");
            return;
        }
        p.sendMessage(P + "§aSeu plot principal: §8(" + my.get().ix + "," + my.get().iz + ")");
    }

    private void handleTrust(Player p, String targetName) {
        if (!p.getWorld().getName().equals(worldName)) {
            p.sendMessage(P + "§cUse dentro do seu plot em §f" + worldName + "§c.");
            return;
        }
        int x = p.getLocation().getBlockX();
        int z = p.getLocation().getBlockZ();
        if (isRoad(x, z)) {
            p.sendMessage(P + "§cVocê precisa estar dentro do seu plot.");
            return;
        }
        Index idx = toIndex(x, z);
        Optional<PlotRecord> rec = db.findByIndex(worldName, idx.ix, idx.iz);
        if (!rec.isPresent() || !rec.get().owner.equals(p.getUniqueId().toString())) {
            p.sendMessage(P + "§cVocê precisa estar dentro do seu plot.");
            return;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
        if (off == null || off.getUniqueId() == null) {
            p.sendMessage(P + "§cJogador não encontrado.");
            return;
        }
        boolean ok = db.addTrust(worldName, idx.ix, idx.iz, off.getUniqueId().toString());
        if (ok) p.sendMessage(P + (off.getName() != null ? off.getName() : targetName) + " §7agora pode construir neste plot.");
        else p.sendMessage(P + "§cNão foi possível adicionar permissão.");
    }

    private void handleUntrust(Player p, String targetName) {
        if (!p.getWorld().getName().equals(worldName)) {
            p.sendMessage(P + "§cUse dentro do seu plot em §f" + worldName + "§c.");
            return;
        }
        int x = p.getLocation().getBlockX();
        int z = p.getLocation().getBlockZ();
        if (isRoad(x, z)) {
            p.sendMessage(P + "§cVocê precisa estar dentro do seu plot.");
            return;
        }
        Index idx = toIndex(x, z);
        Optional<PlotRecord> rec = db.findByIndex(worldName, idx.ix, idx.iz);
        if (!rec.isPresent() || !rec.get().owner.equals(p.getUniqueId().toString())) {
            p.sendMessage(P + "§cVocê precisa estar dentro do seu plot.");
            return;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
        if (off == null || off.getUniqueId() == null) {
            p.sendMessage(P + "§cJogador não encontrado.");
            return;
        }
        boolean ok = db.removeTrust(worldName, idx.ix, idx.iz, off.getUniqueId().toString());
        if (ok) p.sendMessage(P + (off.getName() != null ? off.getName() : targetName) + " §7não pode mais construir neste plot.");
        else p.sendMessage(P + "§cNão foi possível remover permissão.");
    }

    private static final List<String> SUBS = Arrays.asList("auto", "claim", "tp", "info", "unclaim", "where", "trust", "untrust");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return StringUtil.copyPartialMatches(args[0], SUBS, new ArrayList<String>());
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            List<String> names = new ArrayList<String>();
            for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<String>());
        }
        return Collections.emptyList();
    }

    private String prettifyOwner(String ownerUuidStr) {
        try {
            UUID u = UUID.fromString(ownerUuidStr);
            OfflinePlayer off = Bukkit.getOfflinePlayer(u);
            if (off != null && off.getName() != null) return off.getName();
        } catch (IllegalArgumentException ignored) {}
        return ownerUuidStr;
    }

    private static final class Index {
        final int ix, iz;
        Index(int ix, int iz) { this.ix = ix; this.iz = iz; }
    }
}
