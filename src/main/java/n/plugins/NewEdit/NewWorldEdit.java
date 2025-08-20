package n.plugins.NewEdit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * NewWorldEdit (1.7.10 / Java 8) — componente:
 * - NÃO estende JavaPlugin
 * - Recebe o JavaPlugin "core" no construtor
 * - Usa um ÚNICO arquivo de config: dataFolder/ConfigNewEdit.yml
 *
 * A seção "edit" contém as configs do WorldEdit.
 * A seção "guard" é lida pelo NewWorldGuard (que também usa este mesmo arquivo).
 */
public final class NewWorldEdit implements Listener, CommandExecutor, TabCompleter {

    // ===== Core (plugin pai) =====
    private final JavaPlugin core;

    // ====== Config master (compartilhada) ======
    private File cfgFile;
    private YamlConfiguration cfg;

    // ===== Defaults =====
    private int BLOCKS_PER_TICK = 2000;
    private int MAX_BLOCKS = 500_000; // -1 desativa
    private int MAX_CLIPBOARD_BLOCKS = 800_000; // -1 desativa
    private int MAX_UNDO_PER_PLAYER = 10;
    private int MAX_UNDO_BLOCKS_TOTAL_PER_PLAYER = 2_000_000; // -1 desativa
    private boolean SOUNDS_ENABLED = true;
    private boolean REPLACE_REQUIRE_DATA_EXACT = false; // true: exige data igual; false: 0 é wildcard
    private boolean COPY_INCLUDE_AIR = true;

    private String MSG_NO_PERM = "&cVocê não tem permissão para usar este comando.";
    private String MSG_INCOMPLETE_SEL = "&cSeleção incompleta! Use a wand (/wand) ou /pos1 e /pos2.";
    private String MSG_OP_START = "&7Iniciando operação em &f%blocks% &7blocos...";
    private String MSG_OP_DONE = "&aOperação concluída!";
    private String MSG_UNDO_START = "&7Desfazendo %blocks% blocos em lotes...";
    private String MSG_UNDO_DONE = "&aUndo concluído!";
    private String MSG_REDO_DONE = "&aRedo concluído!";
    private String MSG_NOTHING_TO_UNDO = "&eNada para desfazer.";
    private String MSG_NOTHING_TO_REDO = "&eNada para refazer.";
    private String MSG_WAND_GIVEN = "&aWand entregue (machado de madeira).";
    private String MSG_POS1 = "&aPos1: &f%x%&7,&f%y%&7,&f%z%";
    private String MSG_POS2 = "&aPos2: &f%x%&7,&f%y%&7,&f%z%";
    private String MSG_SELECTION_CLEARED = "&aSeleção limpa.";
    private String MSG_WORLD_INVALID = "&cMundo inválido.";
    private String MSG_WORLD_MISMATCH = "&cSua seleção é em outro mundo. Vá para &f%world% &cou redefina /pos.";
    private String MSG_USAGE_SET = "&eUso: /set <id|NOME[:data]>";
    private String MSG_USAGE_REPLACE = "&eUso: /replace <de> <para>";
    private String MSG_COPY_DONE = "&aCopiados %blocks% blocos. Âncora = sua posição atual.";
    private String MSG_NOTHING_COPIED = "&eNada copiado. Use /copy.";
    private String MSG_PASTE_START = "&7Colando %blocks% blocos em lotes...";
    private String MSG_TOO_BIG = "&cA seleção (%blocks%) excede o limite (%max%). Operação cancelada.";
    private String MSG_ERROR_GENERIC = "&cFalha no comando. Veja console.";
    private String MSG_PARSE_ERROR_PREFIX = "&cErro: ";
    private String MSG_WAND_SET_START = "&7[Wand] Aplicando último set: &f%spec% &7em &f%blocks% &7blocos...";
    private String MSG_WAND_NO_LAST = "&eVocê ainda não usou /set. Segure um bloco na mão e agache com a wand para usar esse bloco.";
    private String MSG_UNDO_TRIMMED = "&eSeu histórico antigo foi descartado para respeitar os limites.";
    private String MSG_SIZE = "&7Seleção: &f%vol% &7blocos (&f%minx%&7,&f%miny%&7,&f%minz%&7) -> (&f%maxx%&7,&f%maxy%&7,&f%maxz%&7)";
    private String MSG_COUNT = "&7Total de &f%count% &7blocos de &f%spec%&7.";
    private String MSG_DISTR_HEADER = "&7Distribuição (top %n%):";
    private String MSG_DISTR_LINE = "&7- &f%mat%&7: &f%qtd%";

    // ===== NOVAS MENSAGENS =====
    private String MSG_SPHERE_ORIGIN_SET = "&aCentro da esfera definido em &f%x%&7,&f%y%&7,&f%z%";
    private String MSG_ELIPSE_ORIGIN_SET = "&aCentro da elipse definido em &f%x%&7,&f%y%&7,&f%z%";
    private String MSG_USAGE_SPHERE = "&eUso: /sphere origin | /sphere <raio>";
    private String MSG_USAGE_ELIPSE = "&eUso: /elipse origin | /elipse <rx> <ry> <rz>";
    private String MSG_UP_DONE = "&aElevado para &f%y%&a. Blocos colocados: &f%blocks%";

    private Material WAND_ITEM = Material.WOOD_AXE;

    // ===== Estado =====
    private final Map<UUID, Selection> selections = new HashMap<UUID, Selection>();
    private final Map<UUID, Deque<OperationHistory>> history = new HashMap<UUID, Deque<OperationHistory>>();
    private final Map<UUID, Deque<OperationHistory>> redoHistory = new HashMap<UUID, Deque<OperationHistory>>();
    private final Map<UUID, Clipboard> clipboards = new HashMap<UUID, Clipboard>();
    private final OperationScheduler scheduler;
    private final Db db;

    // cache do último /set
    private final Map<UUID, String> lastSetCache = new ConcurrentHashMap<UUID, String>();

    // ===== NOVO: centros de esfera/elipse por jogador =====
    private final Map<UUID, Vec3i> sphereCenter = new ConcurrentHashMap<UUID, Vec3i>();
    private final Map<UUID, String> sphereWorld = new ConcurrentHashMap<UUID, String>();

    // ===== Modelos =====
    static class Vec3i { int x,y,z; Vec3i(int x,int y,int z){this.x=x;this.y=y;this.z=z;} }

    static class Selection {
        String world; Vec3i p1,p2;
        boolean complete(){ return world!=null && p1!=null && p2!=null; }
        Vec3i min(){ return new Vec3i(Math.min(p1.x,p2.x), Math.min(p1.y,p2.y), Math.min(p1.z,p2.z)); }
        Vec3i max(){ return new Vec3i(Math.max(p1.x,p2.x), Math.max(p1.y,p2.y), Math.max(p1.z,p2.z)); }
        long volume(){ if(!complete())return 0; Vec3i a=min(),b=max(); return 1L*(b.x-a.x+1)*(b.y-a.y+1)*(b.z-a.z+1); }
        void clear(){ world=null; p1=null; p2=null; }
        void expandDir(String dir, int n){
            if(!complete()) return;
            Vec3i a=min(), b=max();
            if("up".equals(dir)) b.y=Math.min(255, b.y+n);
            else if("down".equals(dir)) a.y=Math.max(0, a.y-n);
            else if("north".equals(dir)) a.z-=n;
            else if("south".equals(dir)) b.z+=n;
            else if("west".equals(dir)) a.x-=n;
            else if("east".equals(dir)) b.x+=n;
            else if("vert".equals(dir)){ a.y=0; b.y=255; }
            p1=new Vec3i(a.x,a.y,a.z); p2=new Vec3i(b.x,b.y,b.z);
        }
        void contractDir(String dir, int n){
            if(!complete()) return;
            Vec3i a=min(), b=max();
            if("up".equals(dir)) b.y=Math.max(a.y, b.y-n);
            else if("down".equals(dir)) a.y=Math.min(b.y, a.y+n);
            else if("north".equals(dir)) a.z=Math.min(b.z, a.z+n);
            else if("south".equals(dir)) b.z=Math.max(a.z, b.z-n);
            else if("west".equals(dir)) a.x=Math.min(b.x, a.x+n);
            else if("east".equals(dir)) b.x=Math.max(a.x, b.x-n);
            p1=new Vec3i(a.x,a.y,a.z); p2=new Vec3i(b.x,b.y,b.z);
        }
    }

    static class BlockSpec {
        final int typeId; final byte data;
        BlockSpec(int id, byte data){ this.typeId=id; this.data=data; }
        static BlockSpec parse(String s){
            String[] sp=s.split(":");
            Integer id;
            try{ id=Integer.parseInt(sp[0]); }
            catch(NumberFormatException n){
                Material m=Material.matchMaterial(sp[0].toUpperCase());
                if(m==null) throw new IllegalArgumentException("Bloco inválido: "+s);
                id=m.getId();
            }
            byte data=0;
            if(sp.length>1) data=(byte)Integer.parseInt(sp[1]);
            if(Material.getMaterial(id)==null) throw new IllegalArgumentException("ID inválido: "+id);
            return new BlockSpec(id,data);
        }
        @Override public String toString(){ return Material.getMaterial(typeId).name()+":"+data; }
    }

    static class BlockSnapshot {
        final String world; final int x,y,z; final int typeId; final byte data;
        BlockSnapshot(Block b){ world=b.getWorld().getName(); x=b.getX(); y=b.getY(); z=b.getZ(); typeId=b.getTypeId(); data=b.getData(); }
    }

    static class Clipboard { final List<ClipBlock> blocks=new ArrayList<ClipBlock>(); int ax,ay,az; }
    static class ClipBlock { final int dx,dy,dz,typeId; final byte data; ClipBlock(int dx,int dy,int dz,int id,byte d){this.dx=dx;this.dy=dy;this.dz=dz;this.typeId=id;this.data=d;} }

    static class RegionIterator implements Iterator<Vec3i>{
        final int minX,minY,minZ,maxX,maxY,maxZ; int x,y,z; boolean ok;
        RegionIterator(Vec3i a,Vec3i b){
            minX=a.x; minY=Math.max(0,a.y); minZ=a.z;
            maxX=b.x; maxY=Math.min(255,b.y); maxZ=b.z;
            x=minX;y=minY;z=minZ; ok=minX<=maxX&&minY<=maxY&&minZ<=maxZ;
        }
        public boolean hasNext(){ return ok; }
        public Vec3i next(){
            if(!ok) throw new NoSuchElementException();
            Vec3i out=new Vec3i(x,y,z);
            z++; if(z>maxZ){z=minZ;x++;} if(x>maxX){x=minX;y++;} if(y>maxY){ok=false;}
            return out;
        }
        public void remove(){ throw new UnsupportedOperationException(); }
    }

    static class OperationHistory {
        final String label;
        final List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        OperationHistory(String label){ this.label = label; }
        int size(){ return snapshots.size(); }
    }

    // ===== Construtor/Lifecycle =====
    public NewWorldEdit(JavaPlugin core) {
        this.core = core;
        this.scheduler = new OperationScheduler();
        this.db = new Db();

        loadCombinedConfig();   // cria/abre ConfigNewEdit.yml
        loadConfigValues();     // carrega seção edit.*
        db.init();
        scheduler.start();

        core.getLogger().info("[NewWorldEdit] Ativo. blocks-per-tick=" + BLOCKS_PER_TICK);
    }

    /** Chamado pelo NewCore.onDisable() */
    public void shutdown() {
        scheduler.stop();
        db.close();
        saveCfg();
    }

    // ====== Config ======
    private void loadCombinedConfig(){
        if (!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
        cfgFile = new File(core.getDataFolder(), "ConfigNewEdit.yml");
        if (!cfgFile.exists()) {
            try { core.saveResource("ConfigNewEdit.yml", false); }
            catch (Throwable ignore) {
                YamlConfiguration bootstrap = new YamlConfiguration();
                // edit.* (WE)
                bootstrap.set("edit.blocks-per-tick", BLOCKS_PER_TICK);
                bootstrap.set("edit.max-blocks", MAX_BLOCKS);
                bootstrap.set("edit.max-clipboard-blocks", MAX_CLIPBOARD_BLOCKS);
                bootstrap.set("edit.max-undo-per-player", MAX_UNDO_PER_PLAYER);
                bootstrap.set("edit.undo.max-blocks-total-per-player", MAX_UNDO_BLOCKS_TOTAL_PER_PLAYER);
                bootstrap.set("edit.copy.include-air", COPY_INCLUDE_AIR);
                bootstrap.set("edit.sounds-enabled", SOUNDS_ENABLED);
                bootstrap.set("edit.wand-item", WAND_ITEM.name());
                bootstrap.set("edit.replace.require-data-exact", REPLACE_REQUIRE_DATA_EXACT);
                // edit.messages.*
                bootstrap.set("edit.messages.no-permission", MSG_NO_PERM);
                bootstrap.set("edit.messages.incomplete-selection", MSG_INCOMPLETE_SEL);
                bootstrap.set("edit.messages.operation-start", MSG_OP_START);
                bootstrap.set("edit.messages.operation-done", MSG_OP_DONE);
                bootstrap.set("edit.messages.undo-start", MSG_UNDO_START);
                bootstrap.set("edit.messages.undo-done", MSG_UNDO_DONE);
                bootstrap.set("edit.messages.redo-done", MSG_REDO_DONE);
                bootstrap.set("edit.messages.nothing-to-undo", MSG_NOTHING_TO_UNDO);
                bootstrap.set("edit.messages.nothing-to-redo", MSG_NOTHING_TO_REDO);
                bootstrap.set("edit.messages.wand-given", MSG_WAND_GIVEN);
                bootstrap.set("edit.messages.pos1", MSG_POS1);
                bootstrap.set("edit.messages.pos2", MSG_POS2);
                bootstrap.set("edit.messages.selection-cleared", MSG_SELECTION_CLEARED);
                bootstrap.set("edit.messages.world-invalid", MSG_WORLD_INVALID);
                bootstrap.set("edit.messages.world-mismatch", MSG_WORLD_MISMATCH);
                bootstrap.set("edit.messages.usage-set", MSG_USAGE_SET);
                bootstrap.set("edit.messages.usage-replace", MSG_USAGE_REPLACE);
                bootstrap.set("edit.messages.copy-done", MSG_COPY_DONE);
                bootstrap.set("edit.messages.nothing-copied", MSG_NOTHING_COPIED);
                bootstrap.set("edit.messages.paste-start", MSG_PASTE_START);
                bootstrap.set("edit.messages.too-big", MSG_TOO_BIG);
                bootstrap.set("edit.messages.error-generic", MSG_ERROR_GENERIC);
                bootstrap.set("edit.messages.parse-error-prefix", MSG_PARSE_ERROR_PREFIX);
                bootstrap.set("edit.messages.wand-set-start", MSG_WAND_SET_START);
                bootstrap.set("edit.messages.wand-no-last", MSG_WAND_NO_LAST);
                bootstrap.set("edit.messages.undo-trimmed", MSG_UNDO_TRIMMED);
                bootstrap.set("edit.messages.size", MSG_SIZE);
                bootstrap.set("edit.messages.count", MSG_COUNT);
                bootstrap.set("edit.messages.distr-header", MSG_DISTR_HEADER);
                bootstrap.set("edit.messages.distr-line", MSG_DISTR_LINE);
                // guard.* mínimos
                bootstrap.set("guard.messages.prefix", "&7[WG]&r ");
                bootstrap.set("guard.global.flags.build", "allow");
                bootstrap.set("guard.global.flags.pvp", "allow");
                bootstrap.set("guard.global.flags.mob-spawning", "allow");
                bootstrap.set("guard.global.flags.fire-spread", "allow");
                bootstrap.set("guard.worlds", new LinkedHashMap<String, Object>());
                try { bootstrap.save(cfgFile); } catch (IOException ignored2) {}
            }
        }
        cfg = YamlConfiguration.loadConfiguration(cfgFile);
    }

    public YamlConfiguration cfg(){ return cfg; }

    public void saveCfg(){
        if (cfg == null || cfgFile == null) return;
        try { cfg.save(cfgFile); }
        catch (IOException e){ core.getLogger().log(Level.WARNING, "Falha ao salvar ConfigNewEdit.yml", e); }
    }

    private void loadConfigValues() {
        BLOCKS_PER_TICK = readInt("edit.blocks-per-tick", BLOCKS_PER_TICK);
        MAX_BLOCKS = readInt("edit.max-blocks", MAX_BLOCKS);
        MAX_CLIPBOARD_BLOCKS = readInt("edit.max-clipboard-blocks", MAX_CLIPBOARD_BLOCKS);
        MAX_UNDO_PER_PLAYER = readInt("edit.max-undo-per-player", MAX_UNDO_PER_PLAYER);
        MAX_UNDO_BLOCKS_TOTAL_PER_PLAYER = readInt("edit.undo.max-blocks-total-per-player", MAX_UNDO_BLOCKS_TOTAL_PER_PLAYER);

        COPY_INCLUDE_AIR = readBool("edit.copy.include-air", COPY_INCLUDE_AIR);
        SOUNDS_ENABLED = readBool("edit.sounds-enabled", SOUNDS_ENABLED);
        REPLACE_REQUIRE_DATA_EXACT = readBool("edit.replace.require-data-exact", REPLACE_REQUIRE_DATA_EXACT);

        String wandName = readStr("edit.wand-item", WAND_ITEM.name());
        Material m = Material.matchMaterial(wandName.toUpperCase());
        if (m != null) WAND_ITEM = m;

        // msgs
        MSG_NO_PERM = readStr("edit.messages.no-permission", MSG_NO_PERM);
        MSG_INCOMPLETE_SEL = readStr("edit.messages.incomplete-selection", MSG_INCOMPLETE_SEL);
        MSG_OP_START = readStr("edit.messages.operation-start", MSG_OP_START);
        MSG_OP_DONE = readStr("edit.messages.operation-done", MSG_OP_DONE);
        MSG_UNDO_START = readStr("edit.messages.undo-start", MSG_UNDO_START);
        MSG_UNDO_DONE = readStr("edit.messages.undo-done", MSG_UNDO_DONE);
        MSG_REDO_DONE = readStr("edit.messages.redo-done", MSG_REDO_DONE);
        MSG_NOTHING_TO_UNDO = readStr("edit.messages.nothing-to-undo", MSG_NOTHING_TO_UNDO);
        MSG_NOTHING_TO_REDO = readStr("edit.messages.nothing-to-redo", MSG_NOTHING_TO_REDO);
        MSG_WAND_GIVEN = readStr("edit.messages.wand-given", MSG_WAND_GIVEN);
        MSG_POS1 = readStr("edit.messages.pos1", MSG_POS1);
        MSG_POS2 = readStr("edit.messages.pos2", MSG_POS2);
        MSG_SELECTION_CLEARED = readStr("edit.messages.selection-cleared", MSG_SELECTION_CLEARED);
        MSG_WORLD_INVALID = readStr("edit.messages.world-invalid", MSG_WORLD_INVALID);
        MSG_WORLD_MISMATCH = readStr("edit.messages.world-mismatch", MSG_WORLD_MISMATCH);
        MSG_USAGE_SET = readStr("edit.messages.usage-set", MSG_USAGE_SET);
        MSG_USAGE_REPLACE = readStr("edit.messages.usage-replace", MSG_USAGE_REPLACE);
        MSG_COPY_DONE = readStr("edit.messages.copy-done", MSG_COPY_DONE);
        MSG_NOTHING_COPIED = readStr("edit.messages.nothing-copied", MSG_NOTHING_COPIED);
        MSG_PASTE_START = readStr("edit.messages.paste-start", MSG_PASTE_START);
        MSG_TOO_BIG = readStr("edit.messages.too-big", MSG_TOO_BIG);
        MSG_ERROR_GENERIC = readStr("edit.messages.error-generic", MSG_ERROR_GENERIC);
        MSG_PARSE_ERROR_PREFIX = readStr("edit.messages.parse-error-prefix", MSG_PARSE_ERROR_PREFIX);
        MSG_WAND_SET_START = readStr("edit.messages.wand-set-start", MSG_WAND_SET_START);
        MSG_WAND_NO_LAST = readStr("edit.messages.wand-no-last", MSG_WAND_NO_LAST);
        MSG_UNDO_TRIMMED = readStr("edit.messages.undo-trimmed", MSG_UNDO_TRIMMED);
        MSG_SIZE = readStr("edit.messages.size", MSG_SIZE);
        MSG_COUNT = readStr("edit.messages.count", MSG_COUNT);
        MSG_DISTR_HEADER = readStr("edit.messages.distr-header", MSG_DISTR_HEADER);
        MSG_DISTR_LINE = readStr("edit.messages.distr-line", MSG_DISTR_LINE);

        // novas msgs
        if(!cfg.contains("edit.messages.sphere-origin-set")) cfg.set("edit.messages.sphere-origin-set", MSG_SPHERE_ORIGIN_SET);
        if(!cfg.contains("edit.messages.elipse-origin-set")) cfg.set("edit.messages.elipse-origin-set", MSG_ELIPSE_ORIGIN_SET);
        if(!cfg.contains("edit.messages.usage-sphere")) cfg.set("edit.messages.usage-sphere", MSG_USAGE_SPHERE);
        if(!cfg.contains("edit.messages.usage-elipse")) cfg.set("edit.messages.usage-elipse", MSG_USAGE_ELIPSE);
        if(!cfg.contains("edit.messages.up-done")) cfg.set("edit.messages.up-done", MSG_UP_DONE);

        MSG_SPHERE_ORIGIN_SET = readStr("edit.messages.sphere-origin-set", MSG_SPHERE_ORIGIN_SET);
        MSG_ELIPSE_ORIGIN_SET = readStr("edit.messages.elipse-origin-set", MSG_ELIPSE_ORIGIN_SET);
        MSG_USAGE_SPHERE = readStr("edit.messages.usage-sphere", MSG_USAGE_SPHERE);
        MSG_USAGE_ELIPSE = readStr("edit.messages.usage-elipse", MSG_USAGE_ELIPSE);
        MSG_UP_DONE = readStr("edit.messages.up-done", MSG_UP_DONE);

        saveCfg(); // grava defaults que não existiam
    }

    private int readInt(String path, int def){ if(!cfg.contains(path)) cfg.set(path, def); return cfg.getInt(path, def); }
    private boolean readBool(String path, boolean def){ if(!cfg.contains(path)) cfg.set(path, def); return cfg.getBoolean(path, def); }
    private String readStr(String path, String def){ if(!cfg.contains(path)) cfg.set(path, def); return cfg.getString(path, def); }

    // ===== Dest fora da inner class (compat Java 8) =====
    private enum Dest { UNDO, REDO }

    // ===== Scheduler por tick =====
    private class OperationScheduler {
        private final Queue<RunningTask> queue=new ConcurrentLinkedQueue<RunningTask>();
        private int taskId=-1;

        private class RunningTask {
            final TickTask task; final OperationHistory opHist; final Dest dest;
            RunningTask(TickTask task, Dest dest){ this.task = task; this.opHist = new OperationHistory(task.label()); this.dest = dest; }
        }

        void start(){
            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(core, new Runnable(){
                @Override public void run(){
                    int budget=BLOCKS_PER_TICK;
                    while(budget>0){
                        RunningTask rt=queue.peek();
                        if(rt==null) break;
                        List<BlockSnapshot> tickHist=new ArrayList<BlockSnapshot>(Math.min(2048,budget));
                        int consumed=rt.task.step(budget,tickHist);
                        if(!tickHist.isEmpty()) rt.opHist.snapshots.addAll(tickHist);
                        budget-=Math.max(1,consumed);
                        if(rt.task.done()){
                            queue.poll();
                            if(rt.dest==Dest.UNDO) pushHistory(rt.task.owner, rt.opHist);
                            else if(rt.dest==Dest.REDO) pushRedo(rt.task.owner, rt.opHist);
                            Player pl=Bukkit.getPlayer(rt.task.owner);
                            if(pl!=null){
                                if("undo".equals(rt.task.label())) send(pl, MSG_UNDO_DONE);
                                else if("redo".equals(rt.task.label())) send(pl, MSG_REDO_DONE);
                                else send(pl, MSG_OP_DONE);
                            }
                        }
                    }
                }
            },1L,1L);
        }
        void stop(){ if(taskId!=-1) Bukkit.getScheduler().cancelTask(taskId); queue.clear(); }
        void enqueue(TickTask t){ enqueue(t, Dest.UNDO); } // padrão: operações novas vão para UNDO
        void enqueue(TickTask t, Dest dest){ queue.add(new RunningTask(t, dest)); }
    }

    // ===== Histórico com limites =====
    private void pushHistory(UUID player, OperationHistory opHist){
        if(opHist==null || opHist.snapshots.isEmpty()) return;
        Deque<OperationHistory> dq = history.get(player);
        if (dq == null) { dq = new ArrayDeque<OperationHistory>(); history.put(player, dq); }
        dq.addLast(opHist);
        while(dq.size()>MAX_UNDO_PER_PLAYER) dq.removeFirst();

        if (MAX_UNDO_BLOCKS_TOTAL_PER_PLAYER >= 0){
            int total = 0; for (OperationHistory h : dq) total += h.size();
            boolean trimmed = false;
            while (total > MAX_UNDO_BLOCKS_TOTAL_PER_PLAYER && !dq.isEmpty()){
                OperationHistory removed = dq.removeFirst();
                total -= removed.size(); trimmed = true;
            }
            if (trimmed){
                Player p = Bukkit.getPlayer(player);
                if (p != null) send(p, MSG_UNDO_TRIMMED);
            }
        }
    }

    private void pushRedo(UUID player, OperationHistory opHist){
        if(opHist==null || opHist.snapshots.isEmpty()) return;
        Deque<OperationHistory> dq = redoHistory.get(player);
        if (dq == null) { dq = new ArrayDeque<OperationHistory>(); redoHistory.put(player, dq); }
        dq.addLast(opHist);
    }

    private void clearRedo(Player p){
        Deque<OperationHistory> dq = redoHistory.get(p.getUniqueId());
        if(dq!=null) dq.clear();
    }

    private abstract static class TickTask {
        final UUID owner; final World world;
        TickTask(UUID owner, World world){ this.owner=owner; this.world=world; }
        abstract int step(int budget, List<BlockSnapshot> hist);
        abstract boolean done();
        abstract String label();
        void setFast(int x,int y,int z,int id,byte data){
            if(y<0||y>255) return;
            Block b=world.getBlockAt(x,y,z);
            if(b.getTypeId()!=id || b.getData()!=data) b.setTypeIdAndData(id,data,false);
        }
    }

    private static class RegionSetTask extends TickTask {
        private final BlockSpec spec; private final Iterator<Vec3i> it; private boolean finished=false;
        RegionSetTask(UUID o,World w,Vec3i min,Vec3i max,BlockSpec s){ super(o,w); spec=s; it=new RegionIterator(min,max); }
        int step(int budget,List<BlockSnapshot> hist){
            int c=0;
            while(c<budget&&it.hasNext()){
                Vec3i v=it.next(); Block b=world.getBlockAt(v.x,v.y,v.z);
                hist.add(new BlockSnapshot(b));
                setFast(v.x,v.y,v.z,spec.typeId,spec.data); c++;
            }
            if(!it.hasNext()) finished=true;
            return c;
        }
        boolean done(){ return finished; }
        String label(){ return "set"; }
    }

    private class RegionReplaceTask extends TickTask {
        private final BlockSpec from,to; private final Iterator<Vec3i> it; private boolean finished=false;
        RegionReplaceTask(UUID o,World w,Vec3i min,Vec3i max,BlockSpec f,BlockSpec t){ super(o,w); from=f; to=t; it=new RegionIterator(min,max); }
        int step(int budget,List<BlockSnapshot> hist){
            int c=0;
            while(c<budget&&it.hasNext()){
                Vec3i v=it.next(); Block b=world.getBlockAt(v.x,v.y,v.z);
                boolean match=(b.getTypeId()==from.typeId);
                if(match){
                    if(REPLACE_REQUIRE_DATA_EXACT) match=(b.getData()==from.data);
                    else match=(from.data==0 || b.getData()==from.data);
                }
                if(match){ hist.add(new BlockSnapshot(b)); setFast(v.x,v.y,v.z,to.typeId,to.data); c++; }
            }
            if(!it.hasNext()) finished=true;
            return c;
        }
        boolean done(){ return finished; }
        String label(){ return "replace"; }
    }

    private static class UndoTask extends TickTask {
        private final List<BlockSnapshot> list; private int i=0;
        UndoTask(UUID o,World _unused,List<BlockSnapshot> reverse){ super(o,_unused); this.list=reverse; }
        int step(int budget,List<BlockSnapshot> hist){
            int c=0;
            while(c<budget&&i<list.size()){
                BlockSnapshot s=list.get(i++);
                World w=Bukkit.getWorld(s.world);
                if(w!=null && s.y>=0&&s.y<=255){
                    Block b=w.getBlockAt(s.x,s.y,s.z);
                    hist.add(new BlockSnapshot(b)); // para REDO
                    b.setTypeIdAndData(s.typeId,s.data,false); // desfaz
                }
                c++;
            }
            return c;
        }
        boolean done(){ return i>=list.size(); }
        String label(){ return "undo"; }
    }

    private static class RedoTask extends TickTask {
        private final List<BlockSnapshot> list; private int i=0; private boolean finished=false;
        RedoTask(UUID o,World _unused,List<BlockSnapshot> reverse){ super(o,_unused); this.list=reverse; }
        int step(int budget,List<BlockSnapshot> hist){
            int c=0;
            while(c<budget&&i<list.size()){
                BlockSnapshot s=list.get(i++);
                World w=Bukkit.getWorld(s.world);
                if(w!=null && s.y>=0&&s.y<=255){
                    Block b=w.getBlockAt(s.x,s.y,s.z);
                    hist.add(new BlockSnapshot(b)); // para UNDO do redo
                    b.setTypeIdAndData(s.typeId,s.data,false); // refaz
                }
                c++;
            }
            if(i>=list.size()) finished=true;
            return c;
        }
        boolean done(){ return finished; }
        String label(){ return "redo"; }
    }

    private static class PasteTask extends TickTask {
        private final Clipboard clip; private final Vec3i origin; private int i=0; private boolean finished=false;
        PasteTask(UUID o,World w,Vec3i origin,Clipboard clip){ super(o,w); this.origin=origin; this.clip=clip; }
        int step(int budget,List<BlockSnapshot> hist){
            int c=0;
            while(c<budget&&i<clip.blocks.size()){
                ClipBlock cb=clip.blocks.get(i++);
                int x=origin.x+cb.dx,y=origin.y+cb.dy,z=origin.z+cb.dz;
                if(y<0||y>255) continue;
                Block b=world.getBlockAt(x,y,z);
                hist.add(new BlockSnapshot(b));
                setFast(x,y,z,cb.typeId,cb.data); c++;
            }
            if(i>=clip.blocks.size()) finished=true;
            return c;
        }
        boolean done(){ return finished; }
        String label(){ return "paste"; }
    }

    private static class MultiPasteTask extends TickTask {
        private final List<ClipBlock> blocks; private final Vec3i origin; private int i=0; private boolean finished=false;
        MultiPasteTask(UUID o,World w,Vec3i origin,List<ClipBlock> blocks){ super(o,w); this.origin=origin; this.blocks=blocks; }
        int step(int budget,List<BlockSnapshot> hist){
            int c=0;
            while(c<budget && i<blocks.size()){
                ClipBlock cb=blocks.get(i++);
                int x=origin.x+cb.dx, y=origin.y+cb.dy, z=origin.z+cb.dz;
                if(y<0||y>255) continue;
                Block b=world.getBlockAt(x,y,z);
                hist.add(new BlockSnapshot(b));
                setFast(x,y,z,cb.typeId,cb.data); c++;
            }
            if(i>=blocks.size()) finished=true;
            return c;
        }
        boolean done(){ return finished; }
        String label(){ return "paste"; }
    }

    // ====== NOVO: Task de Cilindro OCO ======
    private static class CylinderTask extends TickTask {
        private final int cx, cz, yBase, height, radius;
        private final BlockSpec spec;
        private int y, x, z;
        private boolean finished = false;

        CylinderTask(UUID owner, World w, int cx, int yBase, int cz, int radius, int height, BlockSpec spec) {
            super(owner, w);
            this.cx = cx; this.cz = cz;
            this.yBase = Math.max(0, yBase);
            this.height = Math.max(1, height);
            this.radius = Math.max(1, radius);
            this.spec = spec;
            this.y = this.yBase;
            this.x = cx - this.radius;
            this.z = cz - this.radius;
        }

        int step(int budget, List<BlockSnapshot> hist){
            int placed = 0;
            int r2 = radius * radius;
            int innerR2 = (radius - 1) * (radius - 1); // oco: parede de 1 bloco

            while (placed < budget && !finished){
                if (y >= yBase + height || y > 255){ finished = true; break; }

                int dx = x - cx;
                int dz = z - cz;
                int d2 = dx*dx + dz*dz;

                boolean in = d2 <= r2;
                boolean ring = in && (d2 >= innerR2); // só borda

                if (ring && y >= 0){
                    Block b = world.getBlockAt(x, y, z);
                    hist.add(new BlockSnapshot(b));
                    setFast(x, y, z, spec.typeId, spec.data);
                    placed++;
                }

                // avança cursor no disco
                z++;
                if (z > cz + radius){
                    z = cz - radius;
                    x++;
                    if (x > cx + radius){
                        x = cx - radius;
                        y++;
                    }
                }
            }
            return placed;
        }

        boolean done(){ return finished; }
        String label(){ return "cyl"; }
    }

    // ====== NOVO: Task de Elipsóide (base para esfera) ======
    private static class EllipsoidTask extends TickTask {
        private final int cx, cy, cz, rx, ry, rz;
        private final BlockSpec spec;
        private int x, y, z;
        private boolean finished=false;

        EllipsoidTask(UUID owner, World w, int cx, int cy, int cz, int rx, int ry, int rz, BlockSpec spec){
            super(owner, w);
            this.cx=cx; this.cy=cy; this.cz=cz;
            this.rx=Math.max(1, rx); this.ry=Math.max(1, ry); this.rz=Math.max(1, rz);
            this.spec=spec;
            this.x = cx - this.rx;
            this.y = Math.max(0, cy - this.ry);
            this.z = cz - this.rz;
        }

        int step(int budget, List<BlockSnapshot> hist){
            int placed=0;
            double invRx2 = 1.0 / (rx*1.0*rx);
            double invRy2 = 1.0 / (ry*1.0*ry);
            double invRz2 = 1.0 / (rz*1.0*rz);

            while(placed<budget && !finished){
                if(y>255){ finished=true; break; }

                int dx = x - cx;
                int dy = y - cy;
                int dz = z - cz;

                double eq = (dx*dx)*invRx2 + (dy*dy)*invRy2 + (dz*dz)*invRz2;
                if(eq <= 1.0 && y>=0){
                    Block b = world.getBlockAt(x,y,z);
                    hist.add(new BlockSnapshot(b));
                    setFast(x,y,z,spec.typeId,spec.data);
                    placed++;
                }

                // avança bounding box
                z++;
                if(z>cz+rz){
                    z=cz-rz;
                    x++;
                    if(x>cx+rx){
                        x=cx-rx;
                        y++;
                    }
                }
            }
            return placed;
        }

        boolean done(){ return finished; }
        String label(){ return "ellipsoid"; }
    }

    private Selection sel(Player p){
        Selection s = selections.get(p.getUniqueId());
        if (s == null){ s = new Selection(); selections.put(p.getUniqueId(), s); }
        return s;
    }
    private Deque<OperationHistory> stack(Player p){
        Deque<OperationHistory> d = history.get(p.getUniqueId());
        if (d == null){ d=new ArrayDeque<OperationHistory>(); history.put(p.getUniqueId(), d); }
        return d;
    }
    private Deque<OperationHistory> redoStack(Player p){
        Deque<OperationHistory> d = redoHistory.get(p.getUniqueId());
        if (d == null){ d=new ArrayDeque<OperationHistory>(); redoHistory.put(p.getUniqueId(), d); }
        return d;
    }

    // ===== Listener (wand/pos + set com machado) =====
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onWand(PlayerInteractEvent e){
        if(e.getItem()==null || e.getItem().getType()!=WAND_ITEM) return;
        if(e.getAction()!=Action.LEFT_CLICK_BLOCK && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;

        Player p=e.getPlayer();
        if(!p.hasPermission("new.wand")) return;

        Block clicked=e.getClickedBlock();
        if(clicked==null) return;

        Selection sel = selections.get(p.getUniqueId());
        if (sel == null){ sel = new Selection(); selections.put(p.getUniqueId(), sel); }

        // Sneak = aplicar último set
        if(p.isSneaking()){
            if(!p.hasPermission("new.set")) { e.setCancelled(true); return; }
            if(!sel.complete()) { send(p, MSG_INCOMPLETE_SEL); e.setCancelled(true); return; }
            if (!clicked.getWorld().getName().equals(sel.world)){
                send(p, MSG_WORLD_MISMATCH, ph("%world%", sel.world==null?"?":sel.world));
                e.setCancelled(true); return;
            }

            String last = lastSetCache.get(p.getUniqueId());
            if(last == null){
                last = db.getLastSetSpec(p.getUniqueId());
                if(last != null) lastSetCache.put(p.getUniqueId(), last);
            }

            BlockSpec spec = null;
            if(last!=null){ try{ spec = BlockSpec.parse(last); }catch (Exception ex){ spec=null; } }
            if(spec==null){
                ItemStack inHand = p.getItemInHand();
                if(inHand==null || inHand.getType()==Material.AIR){
                    send(p, MSG_WAND_NO_LAST); e.setCancelled(true); return;
                }
                int id = inHand.getType().getId();
                byte data = (byte) inHand.getDurability();
                spec = new BlockSpec(id, data);
            }

            long vol = sel.volume();
            if(MAX_BLOCKS>=0 && vol>MAX_BLOCKS){
                send(p,MSG_TOO_BIG, ph("%blocks%",vol), ph("%max%",MAX_BLOCKS));
                e.setCancelled(true); return;
            }

            World w = Bukkit.getWorld(sel.world);
            if(w==null){ send(p,MSG_WORLD_INVALID); e.setCancelled(true); return; }

            send(p, MSG_WAND_SET_START, ph("%spec%", spec.toString()), ph("%blocks%", vol));
            clearRedo(p);
            scheduler.enqueue(new RegionSetTask(p.getUniqueId(), w, sel.min(), sel.max(), spec));

            final String persist = spec.typeId+":"+spec.data;
            lastSetCache.put(p.getUniqueId(), persist);
            db.saveLastSetSpecAsync(p.getUniqueId(), persist);

            if(SOUNDS_ENABLED) p.playSound(p.getLocation(), Sound.CLICK, 1f, 1.2f);
            e.setCancelled(true);
            return;
        }

        // Seleção
        if(e.getAction()==Action.LEFT_CLICK_BLOCK){
            sel.world=clicked.getWorld().getName();
            sel.p1=new Vec3i(clicked.getX(),clicked.getY(),clicked.getZ());
            send(p, MSG_POS1, ph("%x%",clicked.getX()), ph("%y%",clicked.getY()), ph("%z%",clicked.getZ()));
        } else {
            sel.world=clicked.getWorld().getName();
            sel.p2=new Vec3i(clicked.getX(),clicked.getY(),clicked.getZ());
            send(p, MSG_POS2, ph("%x%",clicked.getX()), ph("%y%",clicked.getY()), ph("%z%",clicked.getZ()));
        }

        if(SOUNDS_ENABLED) p.playSound(p.getLocation(), Sound.CLICK, 1f, 1.6f);
        e.setCancelled(true);
    }

    // ===== Helpers de alvo/olhar =====
    @SuppressWarnings("deprecation")
    private Block getTarget(Player p){
        Block b = p.getTargetBlock((HashSet<Byte>) null, 120);
        return (b!=null && b.getType()!=Material.AIR) ? b : null;
    }

    private String facing4(Player p){
        float yaw = p.getLocation().getYaw();
        yaw = (yaw%360+360)%360;
        if(yaw>=45 && yaw<135) return "west";
        if(yaw>=135 && yaw<225) return "north";
        if(yaw>=225 && yaw<315) return "east";
        return "south";
    }
    // ===== CÁLCULOS / HELPERS GEOMÉTRICOS =====
    private long estimateEllipsoidBlocks(int rx, int ry, int rz){
        // Aproximação do volume: 4/3 * π * rx * ry * rz
        double vol = (4.0/3.0) * Math.PI * rx * ry * rz;
        return (long)Math.ceil(vol);
    }

    private boolean checkWorldAndCenter(Player p, String worldName, Vec3i center){
        if(worldName == null || center == null){ send(p, "&eDefina o centro primeiro: /sphere origin ou /elipse origin"); return false; }
        if(!p.getWorld().getName().equals(worldName)){ send(p, MSG_WORLD_MISMATCH, ph("%world%", worldName)); return false; }
        return true;
    }

    // ===== NOVO: /up N  (coloca bloco e teleporta) =====
    private void doUp(Player p, int n){
        World w = p.getWorld();
        int bx = p.getLocation().getBlockX();
        int by = p.getLocation().getBlockY();
        int bz = p.getLocation().getBlockZ();

        int targetY = Math.min(255, by + Math.max(1, n));
        // Procura primeira altura livre para colocar bloco logo abaixo do destino se já houver bloco
        // Estratégia simples: coloca bloco no targetY, se já houver sólido, empurra 1 para cima
        while(targetY <= 255){
            Block b = w.getBlockAt(bx, targetY, bz);
            if(b.getTypeId() == 0){ // ar no local do bloco
                // Assegurar que o espaço acima para o player ficar esteja livre
                Block above = (targetY+1<=255) ? w.getBlockAt(bx, targetY+1, bz) : null;
                if(above == null || above.getTypeId() == 0){
                    // histórico
                    OperationHistory oh = new OperationHistory("up");
                    oh.snapshots.add(new BlockSnapshot(b));
                    pushHistory(p.getUniqueId(), oh);
                    // coloca vidro (GLASS id 20)
                    b.setTypeIdAndData(20, (byte)0, false);
                    // Teleporta player para cima do bloco
                    org.bukkit.Location tp = new org.bukkit.Location(w, bx + 0.5, targetY + 1.0, bz + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch());
                    p.teleport(tp);
                    send(p, MSG_UP_DONE, ph("%y%", targetY+1), ph("%blocks%", 1));
                    if(SOUNDS_ENABLED) p.playSound(tp, Sound.ORB_PICKUP, 1f, 1.4f);
                    return;
                }
            }
            targetY++;
        }
        send(p, "&cSem espaço suficiente acima para /up.");
    }

    // ===== NOVO: Comandos auxiliares para esfera/elipse =====
    private void setCenter(Player p, boolean sphere){
        Vec3i c = new Vec3i(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
        sphereCenter.put(p.getUniqueId(), c);
        sphereWorld.put(p.getUniqueId(), p.getWorld().getName());
        if(sphere) send(p, MSG_SPHERE_ORIGIN_SET, ph("%x%", c.x), ph("%y%", c.y), ph("%z%", c.z));
        else send(p, MSG_ELIPSE_ORIGIN_SET, ph("%x%", c.x), ph("%y%", c.y), ph("%z%", c.z));
        if(SOUNDS_ENABLED) p.playSound(p.getLocation(), Sound.CLICK, 1f, 1.2f);
    }

    private void startEllipsoid(Player p, BlockSpec spec, int rx, int ry, int rz){
        Vec3i c = sphereCenter.get(p.getUniqueId());
        String wName = sphereWorld.get(p.getUniqueId());
        if(!checkWorldAndCenter(p, wName, c)) return;
        World w = Bukkit.getWorld(wName);
        if(w == null){ send(p, MSG_WORLD_INVALID); return; }

        long estimate = estimateEllipsoidBlocks(rx, ry, rz);
        if(MAX_BLOCKS >= 0 && estimate > MAX_BLOCKS){
            send(p, MSG_TOO_BIG, ph("%blocks%", estimate), ph("%max%", MAX_BLOCKS));
            return;
        }

        clearRedo(p);
        send(p, MSG_OP_START, ph("%blocks%", estimate));
        scheduler.enqueue(new EllipsoidTask(p.getUniqueId(), w, c.x, c.y, c.z, rx, ry, rz, spec));
    }

    private void startSphere(Player p, BlockSpec spec, int r){
        startEllipsoid(p, spec, r, r, r);
    }

    // ====== REGISTRO/HOOKS ======
    /** Deve ser chamado pelo core para registrar listener e executor/tab */
    public void register(JavaPlugin core){
        Bukkit.getPluginManager().registerEvents(this, core);
        // O Core deve setar este executor/tab para os comandos configurados no plugin.yml
        // Ex.: core.getCommand("sphere").setExecutor(this); etc.
    }

    // ===== Comandos (SEGUNDA VERSÃO) =====
    // IMPORTANTE: Se você já colou a primeira parte, remova o método onCommand anterior
    // e mantenha ESTE, que inclui /sphere, /elipse e /up.
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if(!(s instanceof Player)){ s.sendMessage(color("&cSomente in-game.")); return true; }
        Player p=(Player)s;
        String cmd=c.getName().toLowerCase();

        try{
            if("wand".equals(cmd)){
                if(!p.hasPermission("new.wand")){ send(p,MSG_NO_PERM); return true; }
                p.getInventory().addItem(new ItemStack(WAND_ITEM,1));
                send(p,MSG_WAND_GIVEN);
                return true;
            }
            if("pos1".equals(cmd)){
                if(!p.hasPermission("new.pos")){ send(p,MSG_NO_PERM); return true; }
                Selection s1=sel(p);
                s1.world=p.getWorld().getName();
                s1.p1=new Vec3i(p.getLocation().getBlockX(),p.getLocation().getBlockY(),p.getLocation().getBlockZ());
                send(p,MSG_POS1, ph("%x%",s1.p1.x), ph("%y%",s1.p1.y), ph("%z%",s1.p1.z));
                return true;
            }
            if("pos2".equals(cmd)){
                if(!p.hasPermission("new.pos")){ send(p,MSG_NO_PERM); return true; }
                Selection s2=sel(p);
                s2.world=p.getWorld().getName();
                s2.p2=new Vec3i(p.getLocation().getBlockX(),p.getLocation().getBlockY(),p.getLocation().getBlockZ());
                send(p,MSG_POS2, ph("%x%",s2.p2.x), ph("%y%",s2.p2.y), ph("%z%",s2.p2.z));
                return true;
            }
            if("pos0".equals(cmd) || "desel".equals(cmd)){
                Selection s0=sel(p);
                s0.clear();
                send(p, MSG_SELECTION_CLEARED);
                return true;
            }
            if("hpos1".equals(cmd) || "hpos2".equals(cmd)){
                if(!p.hasPermission("new.pos")){ send(p,MSG_NO_PERM); return true; }
                Block b = getTarget(p);
                if(b==null){ send(p,"&eNenhum bloco no alvo."); return true; }
                Selection sh=sel(p);
                sh.world=b.getWorld().getName();
                if("hpos1".equals(cmd)) sh.p1=new Vec3i(b.getX(),b.getY(),b.getZ());
                else sh.p2=new Vec3i(b.getX(),b.getY(),b.getZ());
                send(p, "hpos1".equals(cmd)?MSG_POS1:MSG_POS2, ph("%x%",b.getX()), ph("%y%",b.getY()), ph("%z%",b.getZ()));
                return true;
            }
            if("set".equals(cmd)){
                if(!p.hasPermission("new.set")){ send(p,MSG_NO_PERM); return true; }
                if(a.length<1){ send(p,MSG_USAGE_SET); return true; }
                Selection ss=sel(p);
                if(!ss.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                if(!p.getWorld().getName().equals(ss.world)){ send(p, MSG_WORLD_MISMATCH, ph("%world%", ss.world)); return true; }
                long setVol=ss.volume();
                if(MAX_BLOCKS>=0 && setVol>MAX_BLOCKS){ send(p,MSG_TOO_BIG, ph("%blocks%",setVol), ph("%max%",MAX_BLOCKS)); return true; }
                BlockSpec spec=BlockSpec.parse(a[0]);
                World w=Bukkit.getWorld(ss.world);
                if(w==null){ send(p,MSG_WORLD_INVALID); return true; }
                clearRedo(p);
                send(p,MSG_OP_START, ph("%blocks%",setVol));
                scheduler.enqueue(new RegionSetTask(p.getUniqueId(), w, ss.min(), ss.max(), spec));
                final String persist = spec.typeId+":"+spec.data;
                lastSetCache.put(p.getUniqueId(), persist);
                db.saveLastSetSpecAsync(p.getUniqueId(), persist);
                return true;
            }
            if("replace".equals(cmd)){
                if(!p.hasPermission("new.replace")){ send(p,MSG_NO_PERM); return true; }
                if(a.length<2){ send(p,MSG_USAGE_REPLACE); return true; }
                Selection sr=sel(p);
                if(!sr.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                if(!p.getWorld().getName().equals(sr.world)){ send(p, MSG_WORLD_MISMATCH, ph("%world%", sr.world)); return true; }
                long repVol=sr.volume();
                if(MAX_BLOCKS>=0 && repVol>MAX_BLOCKS){ send(p,MSG_TOO_BIG, ph("%blocks%",repVol), ph("%max%",MAX_BLOCKS)); return true; }
                BlockSpec from=BlockSpec.parse(a[0]);
                BlockSpec to=BlockSpec.parse(a[1]);
                World wr=Bukkit.getWorld(sr.world);
                if(wr==null){ send(p,MSG_WORLD_INVALID); return true; }
                clearRedo(p);
                send(p,MSG_OP_START, ph("%blocks%",repVol));
                scheduler.enqueue(new RegionReplaceTask(p.getUniqueId(), wr, sr.min(), sr.max(), from, to));
                return true;
            }
            if("undo".equals(cmd)){
                if(!p.hasPermission("new.undo")){ send(p,MSG_NO_PERM); return true; }
                int n=1; if(a.length>=1) try{ n=Math.max(1,Integer.parseInt(a[0])); }catch(Exception ignore){}
                Deque<OperationHistory> st=stack(p);
                if(st.isEmpty()){ send(p,MSG_NOTHING_TO_UNDO); return true; }
                int done=0;
                while(done<n && !st.isEmpty()){
                    OperationHistory lastOp = st.removeLast();
                    List<BlockSnapshot> rev = new ArrayList<BlockSnapshot>(lastOp.snapshots);
                    Collections.reverse(rev);
                    World wu = !rev.isEmpty() ? Bukkit.getWorld(rev.get(0).world) : p.getWorld();
                    if(done==0) send(p,MSG_UNDO_START, ph("%blocks%", rev.size()));
                    scheduler.enqueue(new UndoTask(p.getUniqueId(), wu, rev), Dest.REDO);
                    done++;
                }
                return true;
            }
            if("redo".equals(cmd)){
                if(!p.hasPermission("new.redo")){ send(p,MSG_NO_PERM); return true; }
                int n=1; if(a.length>=1) try{ n=Math.max(1,Integer.parseInt(a[0])); }catch(Exception ignore){}
                Deque<OperationHistory> rd=redoStack(p);
                if(rd.isEmpty()){ send(p,MSG_NOTHING_TO_REDO); return true; }
                int done=0;
                while(done<n && !rd.isEmpty()){
                    OperationHistory op = rd.removeLast();
                    List<BlockSnapshot> rev = new ArrayList<BlockSnapshot>(op.snapshots);
                    Collections.reverse(rev);
                    World w = !rev.isEmpty() ? Bukkit.getWorld(rev.get(0).world) : p.getWorld();
                    scheduler.enqueue(new RedoTask(p.getUniqueId(), w, rev), Dest.UNDO);
                    done++;
                }
                return true;
            }
            if("copy".equals(cmd)){
                if(!p.hasPermission("new.copy")){ send(p,MSG_NO_PERM); return true; }
                Selection sc=sel(p);
                if(!sc.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                if(!p.getWorld().getName().equals(sc.world)){ send(p, MSG_WORLD_MISMATCH, ph("%world%", sc.world)); return true; }
                long copyVol=sc.volume();
                if(MAX_CLIPBOARD_BLOCKS>=0 && copyVol>MAX_CLIPBOARD_BLOCKS){ send(p,MSG_TOO_BIG, ph("%blocks%",copyVol), ph("%max%",MAX_CLIPBOARD_BLOCKS)); return true; }
                World wc=Bukkit.getWorld(sc.world);
                if(wc==null){ send(p,MSG_WORLD_INVALID); return true; }
                Clipboard clip=new Clipboard();
                clip.ax=p.getLocation().getBlockX();
                clip.ay=p.getLocation().getBlockY();
                clip.az=p.getLocation().getBlockZ();
                RegionIterator it=new RegionIterator(sc.min(), sc.max());
                int copied=0;
                while(it.hasNext()){
                    if(MAX_CLIPBOARD_BLOCKS>=0 && copied>=MAX_CLIPBOARD_BLOCKS) break;
                    Vec3i v=it.next();
                    Block b=wc.getBlockAt(v.x,v.y,v.z);
                    if (!COPY_INCLUDE_AIR && b.getTypeId()==0) { continue; }
                    clip.blocks.add(new ClipBlock(v.x-clip.ax, v.y-clip.ay, v.z-clip.az, b.getTypeId(), b.getData()));
                    copied++;
                }
                clipboards.put(p.getUniqueId(), clip);
                send(p,MSG_COPY_DONE, ph("%blocks%",copied));
                return true;
            }
            if("paste".equals(cmd)){
                if(!p.hasPermission("new.paste")){ send(p,MSG_NO_PERM); return true; }
                Clipboard cp=clipboards.get(p.getUniqueId());
                if(cp==null || cp.blocks.isEmpty()){ send(p,MSG_NOTHING_COPIED); return true; }
                World wp=p.getWorld();
                Vec3i origin=new Vec3i(p.getLocation().getBlockX(),p.getLocation().getBlockY(),p.getLocation().getBlockZ());
                long pasteVol=cp.blocks.size();
                if(MAX_BLOCKS>=0 && pasteVol>MAX_BLOCKS){ send(p,MSG_TOO_BIG, ph("%blocks%",pasteVol), ph("%max%",MAX_BLOCKS)); return true; }
                clearRedo(p);
                send(p,MSG_PASTE_START, ph("%blocks%",cp.blocks.size()));
                scheduler.enqueue(new PasteTask(p.getUniqueId(), wp, origin, cp));
                return true;
            }
            if("cut".equals(cmd)){
                if(!p.hasPermission("new.cut")){ send(p,MSG_NO_PERM); return true; }
                Selection sc=sel(p);
                if(!sc.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                if(!p.getWorld().getName().equals(sc.world)){ send(p, MSG_WORLD_MISMATCH, ph("%world%", sc.world)); return true; }
                long vol=sc.volume();
                if(MAX_BLOCKS>=0 && vol>MAX_BLOCKS){ send(p,MSG_TOO_BIG, ph("%blocks%",vol), ph("%max%",MAX_BLOCKS)); return true; }
                Bukkit.dispatchCommand(p, "copy");
                World w=Bukkit.getWorld(sc.world);
                if(w==null){ send(p,MSG_WORLD_INVALID); return true; }
                clearRedo(p);
                send(p,MSG_OP_START, ph("%blocks%",vol));
                scheduler.enqueue(new RegionSetTask(p.getUniqueId(), w, sc.min(), sc.max(), new BlockSpec(0,(byte)0)));
                return true;
            }
            if("expand".equals(cmd) || "contract".equals(cmd)){
                if(!p.hasPermission("new.pos")){ send(p,MSG_NO_PERM); return true; }
                Selection ss=sel(p);
                if(!ss.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                int n=1; if(a.length>=1) try{ n=Math.max(1,Integer.parseInt(a[0])); }catch(Exception ignore){}
                String dir = (a.length>=2) ? a[1].toLowerCase() : null;
                if(dir==null){ String f=facing4(p); dir=f; }
                else if("vert".equals(dir) || "v".equals(dir)) dir="vert";
                if("expand".equals(cmd)) ss.expandDir(dir, n);
                else ss.contractDir(dir, n);
                Vec3i mn=ss.min(), mx=ss.max();
                send(p, MSG_SIZE, ph("%vol%", ss.volume()), ph("%minx%", mn.x), ph("%miny%", mn.y), ph("%minz%", mn.z),
                        ph("%maxx%", mx.x), ph("%maxy%", mx.y), ph("%maxz%", mx.z));
                return true;
            }
            if("stack".equals(cmd)){
                if(!p.hasPermission("new.stack")){ send(p,MSG_NO_PERM); return true; }
                Selection ss=sel(p);
                if(!ss.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                if(!p.getWorld().getName().equals(ss.world)){ send(p, MSG_WORLD_MISMATCH, ph("%world%", ss.world)); return true; }
                int count = 1; if(a.length>=1) try{ count=Math.max(1,Integer.parseInt(a[0])); }catch(Exception ignore){}
                int dx=0,dy=0,dz=0;
                if(a.length>=4){
                    try{ dx=Integer.parseInt(a[1]); dy=Integer.parseInt(a[2]); dz=Integer.parseInt(a[3]); }
                    catch(Exception e){ dx=dy=dz=0; }
                } else {
                    String f=facing4(p); Vec3i mn=ss.min(), mx=ss.max();
                    if("north".equals(f)){ dz=-(mx.z-mn.z+1); }
                    else if("south".equals(f)){ dz=(mx.z-mn.z+1); }
                    else if("west".equals(f)){ dx=-(mx.x-mn.x+1); }
                    else { dx=(mx.x-mn.x+1); }
                }
                World w=Bukkit.getWorld(ss.world);
                if(w==null){ send(p,MSG_WORLD_INVALID); return true; }

                List<ClipBlock> base = captureRegionAsClipBlocks(w, ss.min(), ss.max());
                long total = (long) base.size() * count;
                if(MAX_BLOCKS>=0 && total>MAX_BLOCKS){ send(p,MSG_TOO_BIG, ph("%blocks%",total), ph("%max%",MAX_BLOCKS)); return true; }

                List<ClipBlock> all = new ArrayList<ClipBlock>(base.size()*count);
                for(int i=1;i<=count;i++){
                    int ox = dx*i, oy = dy*i, oz = dz*i;
                    for(ClipBlock cb: base){ all.add(new ClipBlock(cb.dx+ox, cb.dy+oy, cb.dz+oz, cb.typeId, cb.data)); }
                }
                clearRedo(p);
                Vec3i origin = new Vec3i(ss.min().x, ss.min().y, ss.min().z);
                send(p, MSG_OP_START, ph("%blocks%", all.size()));
                scheduler.enqueue(new MultiPasteTask(p.getUniqueId(), w, origin, all));
                return true;
            }
            if("move".equals(cmd)){
                if(!p.hasPermission("new.move")){ send(p,MSG_NO_PERM); return true; }
                Selection ss=sel(p);
                if(!ss.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                if(!p.getWorld().getName().equals(ss.world)){ send(p, MSG_WORLD_MISMATCH, ph("%world%", ss.world)); return true; }
                int count = 1; if(a.length>=1) try{ count=Math.max(1,Integer.parseInt(a[0])); }catch(Exception ignore){}
                int dx=0,dy=0,dz=0;
                if(a.length>=4){
                    try{ dx=Integer.parseInt(a[1]); dy=Integer.parseInt(a[2]); dz=Integer.parseInt(a[3]); }
                    catch(Exception e){ dx=dy=dz=0; }
                } else {
                    String f=facing4(p);
                    if("north".equals(f)) dz=-1; else if("south".equals(f)) dz=1; else if("west".equals(f)) dx=-1; else dx=1;
                }
                BlockSpec mask = null;
                if(a.length>=5){ try{ mask = BlockSpec.parse(a[4]); }catch(Exception ex){ mask=null; } }

                World w=Bukkit.getWorld(ss.world);
                if(w==null){ send(p,MSG_WORLD_INVALID); return true; }

                List<ClipBlock> base = captureRegionAsClipBlocks(w, ss.min(), ss.max(), mask);
                long total = (long) base.size() * count + (ss.volume());
                if(MAX_BLOCKS>=0 && total>MAX_BLOCKS){ send(p,MSG_TOO_BIG, ph("%blocks%",total), ph("%max%",MAX_BLOCKS)); return true; }

                clearRedo(p);
                scheduler.enqueue(new RegionSetTask(p.getUniqueId(), w, ss.min(), ss.max(), new BlockSpec(0,(byte)0)));

                List<ClipBlock> all = new ArrayList<ClipBlock>(base.size()*count);
                for(int i=1;i<=count;i++){
                    int ox = dx*i, oy = dy*i, oz = dz*i;
                    for(ClipBlock cb: base){ all.add(new ClipBlock(cb.dx+ox, cb.dy+oy, cb.dz+oz, cb.typeId, cb.data)); }
                }
                Vec3i origin = new Vec3i(ss.min().x, ss.min().y, ss.min().z);
                scheduler.enqueue(new MultiPasteTask(p.getUniqueId(), w, origin, all));
                send(p, MSG_OP_START, ph("%blocks%", all.size()));
                return true;
            }
            if("rotate".equals(cmd)){
                if(!p.hasPermission("new.rotate")){ send(p,MSG_NO_PERM); return true; }
                Clipboard cp=clipboards.get(p.getUniqueId());
                if(cp==null || cp.blocks.isEmpty()){ send(p,MSG_NOTHING_COPIED); return true; }
                if(a.length<1){ send(p,"&eUso: /rotate <graus: 90|180|270>"); return true; }
                int deg;
                try{ deg=Integer.parseInt(a[0]); }catch(Exception ex){ send(p,"&cÂngulo inválido."); return true; }
                deg=((deg%360)+360)%360;
                if(!(deg==90||deg==180||deg==270)){ send(p,"&cUse 90, 180 ou 270."); return true; }
                rotateClipboardY(cp, deg);
                send(p,"&aClipboard rotacionada "+deg+"° em Y.");
                return true;
            }
            if("flip".equals(cmd)){
                if(!p.hasPermission("new.flip")){ send(p,MSG_NO_PERM); return true; }
                Clipboard cp=clipboards.get(p.getUniqueId());
                if(cp==null || cp.blocks.isEmpty()){ send(p,MSG_NOTHING_COPIED); return true; }
                if(a.length<1){ send(p,"&eUso: /flip <x|y|z>"); return true; }
                String axis=a[0].toLowerCase();
                if(!("x".equals(axis)||"y".equals(axis)||"z".equals(axis))){ send(p,"&cEixo inválido."); return true; }
                flipClipboard(cp, axis);
                send(p,"&aClipboard espelhada no eixo "+axis.toUpperCase()+".");
                return true;
            }
            if("size".equals(cmd)){
                Selection s0=sel(p);
                if(!s0.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                Vec3i mn=s0.min(), mx=s0.max();
                send(p, MSG_SIZE, ph("%vol%", s0.volume()), ph("%minx%", mn.x), ph("%miny%", mn.y), ph("%minz%", mn.z),
                        ph("%maxx%", mx.x), ph("%maxy%", mx.y), ph("%maxz%", mx.z));
                return true;
            }
            if("count".equals(cmd)){
                if(a.length<1){ send(p,"&eUso: /count <id|NOME[:data]>"); return true; }
                Selection s0=sel(p);
                if(!s0.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                BlockSpec spec = BlockSpec.parse(a[0]);
                World w=Bukkit.getWorld(s0.world);
                if(w==null){ send(p,MSG_WORLD_INVALID); return true; }
                long count=0;
                RegionIterator it=new RegionIterator(s0.min(), s0.max());
                while(it.hasNext()){
                    Vec3i v=it.next();
                    Block b=w.getBlockAt(v.x,v.y,v.z);
                    if(b.getTypeId()==spec.typeId){
                        if(REPLACE_REQUIRE_DATA_EXACT){
                            if(b.getData()==spec.data) count++;
                        } else {
                            if(spec.data==0 || b.getData()==spec.data) count++;
                        }
                    }
                }
                send(p, MSG_COUNT, ph("%count%", count), ph("%spec%", spec.toString()));
                return true;
            }
            if("distr".equals(cmd)){
                Selection s0=sel(p);
                if(!s0.complete()){ send(p,MSG_INCOMPLETE_SEL); return true; }
                World w=Bukkit.getWorld(s0.world);
                if(w==null){ send(p,MSG_WORLD_INVALID); return true; }
                Map<Integer, Long> map=new HashMap<Integer, Long>();
                RegionIterator it=new RegionIterator(s0.min(), s0.max());
                while(it.hasNext()){
                    Vec3i v=it.next();
                    Block b=w.getBlockAt(v.x,v.y,v.z);
                    int id=b.getTypeId();
                    Long cur=map.get(id);
                    map.put(id, cur==null?1L:cur+1L);
                }
                List<Map.Entry<Integer,Long>> list=new ArrayList<Map.Entry<Integer,Long>>(map.entrySet());
                Collections.sort(list, new Comparator<Map.Entry<Integer,Long>>(){
                    public int compare(Map.Entry<Integer,Long> a, Map.Entry<Integer,Long> b){
                        return -Long.compare(a.getValue(), b.getValue());
                    }
                });
                int top = Math.min(10, list.size());
                p.sendMessage(color(MSG_DISTR_HEADER.replace("%n%", String.valueOf(top))));
                for(int i=0;i<top;i++){
                    int id=list.get(i).getKey();
                    long qtd=list.get(i).getValue();
                    Material mat=Material.getMaterial(id);
                    String name = (mat!=null?mat.name():"ID"+id);
                    p.sendMessage(color(MSG_DISTR_LINE.replace("%mat%", name).replace("%qtd%", String.valueOf(qtd))));
                }
                return true;
            }
            if("cyl".equals(cmd)) {
                if (!p.hasPermission("new.cyl")) { send(p, MSG_NO_PERM); return true; }
                if (a.length < 2) {
                    send(p, "&eUso: /cyl <id|NOME[:data]> <raio> [altura]");
                    return true;
                }
                BlockSpec spec = BlockSpec.parse(a[0]);
                int radius; try { radius = Math.max(1, Integer.parseInt(a[1])); } catch (Exception ex) { send(p, "&cRaio inválido."); return true; }
                int height = 1; if (a.length >= 3) { try { height = Math.max(1, Integer.parseInt(a[2])); } catch (Exception ignore) {} }
                long shell = Math.round(Math.PI * Math.max(1, (2L*radius - 1))) * height;
                if (MAX_BLOCKS >= 0 && shell > MAX_BLOCKS) {
                    send(p, MSG_TOO_BIG, ph("%blocks%", shell), ph("%max%", MAX_BLOCKS));
                    return true;
                }
                World w = p.getWorld();
                int cx = p.getLocation().getBlockX();
                int cz = p.getLocation().getBlockZ();
                int yBase = p.getLocation().getBlockY();
                clearRedo(p);
                send(p, MSG_OP_START, ph("%blocks%", shell));
                scheduler.enqueue(new CylinderTask(p.getUniqueId(), w, cx, yBase, cz, radius, height, spec));
                return true;
            }

            // ===== NOVOS COMANDOS: /sphere e /elipse =====
            if("sphere".equals(cmd)){
                if(!p.hasPermission("new.sphere")){ send(p, MSG_NO_PERM); return true; }
                if(a.length < 1){ send(p, MSG_USAGE_SPHERE); return true; }
                if("origin".equalsIgnoreCase(a[0])){
                    setCenter(p, true);
                    return true;
                }
                // sphere <r> [bloco]
                int r;
                try{ r = Math.max(1, Integer.parseInt(a[0])); }catch(Exception ex){ send(p, "&cRaio inválido."); return true; }
                BlockSpec spec;
                if(a.length >= 2){
                    spec = BlockSpec.parse(a[1]);
                } else {
                    ItemStack hand = p.getItemInHand();
                    if(hand==null || hand.getType()==Material.AIR){ send(p, "&eInforme o bloco: /sphere <r> <id|NOME[:data]> ou segure um bloco na mão."); return true; }
                    spec = new BlockSpec(hand.getTypeId(), (byte)hand.getDurability());
                }
                startSphere(p, spec, r);
                return true;
            }

            if("elipse".equals(cmd) || "ellipsoid".equals(cmd)){
                if(!p.hasPermission("new.elipse")){ send(p, MSG_NO_PERM); return true; }
                if(a.length < 1){ send(p, MSG_USAGE_ELIPSE); return true; }
                if("origin".equalsIgnoreCase(a[0])){
                    setCenter(p, false);
                    return true;
                }
                if(a.length < 3){ send(p, MSG_USAGE_ELIPSE); return true; }
                int rx, ry, rz;
                try{
                    rx = Math.max(1, Integer.parseInt(a[0]));
                    ry = Math.max(1, Integer.parseInt(a[1]));
                    rz = Math.max(1, Integer.parseInt(a[2]));
                }catch(Exception ex){
                    send(p, "&cRaios inválidos."); return true;
                }
                BlockSpec spec;
                if(a.length >= 4){
                    spec = BlockSpec.parse(a[3]);
                } else {
                    ItemStack hand = p.getItemInHand();
                    if(hand==null || hand.getType()==Material.AIR){ send(p, "&eInforme o bloco: /elipse <rx> <ry> <rz> <id|NOME[:data]> ou segure um bloco na mão."); return true; }
                    spec = new BlockSpec(hand.getTypeId(), (byte)hand.getDurability());
                }
                startEllipsoid(p, spec, rx, ry, rz);
                return true;
            }

            // ===== NOVO: /up =====
            if("up".equals(cmd)){
                if(!p.hasPermission("new.up")){ send(p, MSG_NO_PERM); return true; }
                int n = 1; if(a.length>=1) try{ n = Math.max(1, Integer.parseInt(a[0])); }catch(Exception ignore){}
                doUp(p, n);
                return true;
            }

        }catch(IllegalArgumentException ex){
            send(p, MSG_PARSE_ERROR_PREFIX + ex.getMessage());
            return true;
        }catch(Exception ex){
            send(p, MSG_ERROR_GENERIC);
            ex.printStackTrace();
            return true;
        }
        return false;
    }

    // ===== Tab complete (SEGUNDA VERSÃO COM NOVOS COMANDOS) =====
    // Remova o método anterior e mantenha este para incluir sphere, elipse e up.
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        String cmd=c.getName().toLowerCase();

        if(cmd.equals("set")||cmd.equals("replace")||cmd.equals("count")||cmd.equals("cyl")){
            if(a.length==1||a.length==2){
                String q=a[a.length-1].toUpperCase();
                List<String> out=new ArrayList<String>();
                for(Material m:Material.values()) if(m!=null && m.name().startsWith(q)) out.add(m.name());
                return out.size()>60?out.subList(0,60):out;
            }
            if(cmd.equals("cyl") && a.length==2){ return Arrays.asList("3","5","7","9","11"); }
            if(cmd.equals("cyl") && a.length==3){ return Arrays.asList("1","3","5"); }
        }
        if(cmd.equals("expand")||cmd.equals("contract")){
            if(a.length==2){ return Arrays.asList("up","down","north","south","west","east","vert","h","v"); }
        }
        if(cmd.equals("flip")){ if(a.length==1) return Arrays.asList("x","y","z"); }
        if(cmd.equals("rotate")){ if(a.length==1) return Arrays.asList("90","180","270"); }

        if(cmd.equals("sphere")){
            if(a.length==1){ return Arrays.asList("origin","5","7","9","11"); }
            if(a.length==2 && !"origin".equalsIgnoreCase(a[0])){
                String q=a[1].toUpperCase();
                List<String> out=new ArrayList<String>();
                for(Material m:Material.values()) if(m!=null && m.name().startsWith(q)) out.add(m.name());
                return out.size()>60?out.subList(0,60):out;
            }
        }
        if(cmd.equals("elipse")||cmd.equals("ellipsoid")){
            if(a.length==1){ return Arrays.asList("origin","5"); }
            if(a.length==2){ return Arrays.asList("5","7","9"); }
            if(a.length==3){ return Arrays.asList("5","7","9"); }
            if(a.length==4){
                String q=a[3].toUpperCase();
                List<String> out=new ArrayList<String>();
                for(Material m:Material.values()) if(m!=null && m.name().startsWith(q)) out.add(m.name());
                return out.size()>60?out.subList(0,60):out;
            }
        }
        if(cmd.equals("up")){
            if(a.length==1){ return Arrays.asList("1","2","3","5","10"); }
        }

        return Collections.emptyList();
    }

    // ===== Msg helpers =====
    private static String color(String s){ return ChatColor.translateAlternateColorCodes('&', s); }

    static class Pair{ final String k; final String v; Pair(String k,Object v){this.k=k;this.v=String.valueOf(v);} }
    static Pair ph(String k,Object v){ return new Pair(k,v); }
    private void send(Player p,String raw,Pair... phs){
        String m=raw; for(Pair ph:phs) m=m.replace(ph.k,ph.v);
        p.sendMessage(color(m));
    }

    // ====== GETTERS para o Guard usar ======
    public String getSelectionWorld(Player p){
        Selection s = selections.get(p.getUniqueId());
        return (s!=null && s.complete()) ? s.world : null;
    }
    /** @return {minX,minY,minZ,maxX,maxY,maxZ} ou null */
    public int[] getSelectionBox(Player p){
        Selection s = selections.get(p.getUniqueId());
        if(s==null || !s.complete()) return null;
        Vec3i a = s.min(), b = s.max();
        return new int[]{a.x,a.y,a.z,b.x,b.y,b.z};
    }

    // ===== Utils de clipboard =====
    private List<ClipBlock> captureRegionAsClipBlocks(World w, Vec3i mn, Vec3i mx){ return captureRegionAsClipBlocks(w, mn, mx, null); }
    private List<ClipBlock> captureRegionAsClipBlocks(World w, Vec3i mn, Vec3i mx, BlockSpec mask){
        List<ClipBlock> list=new ArrayList<ClipBlock>();
        int ax=mn.x, ay=mn.y, az=mn.z;
        RegionIterator it=new RegionIterator(mn, mx);
        while(it.hasNext()){
            Vec3i v=it.next();
            Block b=w.getBlockAt(v.x,v.y,v.z);
            if(mask!=null){
                if(b.getTypeId()!=mask.typeId) continue;
                if(REPLACE_REQUIRE_DATA_EXACT){
                    if(b.getData()!=mask.data) continue;
                } else {
                    if(!(mask.data==0 || b.getData()==mask.data)) continue;
                }
            }
            list.add(new ClipBlock(v.x-ax, v.y-ay, v.z-az, b.getTypeId(), b.getData()));
        }
        return list;
    }

    private void rotateClipboardY(Clipboard cp, int deg){
        for(int i=0;i<cp.blocks.size();i++){
            ClipBlock cb=cp.blocks.get(i);
            int x=cb.dx, y=cb.dy, z=cb.dz;
            int nx=x, nz=z;
            if(deg==90){ nx = -z; nz = x; }
            else if(deg==180){ nx = -x; nz = -z; }
            else if(deg==270){ nx = z; nz = -x; }
            cp.blocks.set(i, new ClipBlock(nx, y, nz, cb.typeId, cb.data));
        }
    }

    private void flipClipboard(Clipboard cp, String axis){
        for(int i=0;i<cp.blocks.size();i++){
            ClipBlock cb=cp.blocks.get(i);
            int x=cb.dx, y=cb.dy, z=cb.dz;
            if("x".equals(axis)) x=-x; else if("y".equals(axis)) y=-y; else z=-z;
            cp.blocks.set(i, new ClipBlock(x, y, z, cb.typeId, cb.data));
        }
    }

    // ===== DB (SQLite) com cache e gravação assíncrona =====
    private class Db {
        private Connection conn;

        void init(){
            try{
                if(!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
                File f = new File(core.getDataFolder(), "newworldedit.db");
                Class.forName("org.sqlite.JDBC");
                conn = DriverManager.getConnection("jdbc:sqlite:"+f.getAbsolutePath());
                Statement st=null;
                try{
                    st=conn.createStatement();
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS player_last_action (player_uuid TEXT PRIMARY KEY, last_set TEXT, last_label TEXT, last_at INTEGER)");
                } finally { if(st!=null) try{st.close();}catch(Exception ignored){} }
                core.getLogger().info("[NewWorldEdit] SQLite ok em " + core.getDataFolder().getAbsolutePath());
            }catch (Throwable t){
                core.getLogger().warning("SQLite indisponível. Último /set não será persistido entre reinícios. " + t.getMessage());
                conn = null;
            }
        }

        void close(){ try{ if(conn!=null) conn.close(); }catch(Exception ignored){} }

        String getLastSetSpec(UUID uuid){
            if(conn==null) return null;
            PreparedStatement ps = null; ResultSet rs = null;
            try{
                ps = conn.prepareStatement("SELECT last_set FROM player_last_action WHERE player_uuid=?");
                ps.setString(1, uuid.toString());
                rs = ps.executeQuery();
                if(rs.next()) return rs.getString(1);
            }catch (SQLException ex){
                core.getLogger().warning("Falha ao ler last_set: "+ex.getMessage());
            } finally{
                try{ if(rs!=null) rs.close(); }catch(Exception ignored){}
                try{ if(ps!=null) ps.close(); }catch(Exception ignored){}
            }
            return null;
        }

        void saveLastSetSpecAsync(final UUID uuid, final String spec){
            if(conn==null) return;
            Bukkit.getScheduler().runTaskAsynchronously(core, new Runnable(){
                public void run(){
                    PreparedStatement ps = null;
                    try{
                        ps = conn.prepareStatement("UPDATE player_last_action SET last_set=?, last_label=?, last_at=? WHERE player_uuid=?");
                        ps.setString(1, spec);
                        ps.setString(2, "set");
                        ps.setLong(3, System.currentTimeMillis());
                        ps.setString(4, uuid.toString());
                        int rows = ps.executeUpdate();
                        ps.close(); ps=null;
                        if (rows == 0){
                            ps = conn.prepareStatement("INSERT INTO player_last_action(player_uuid,last_set,last_label,last_at) VALUES(?,?,?,?)");
                            ps.setString(1, uuid.toString());
                            ps.setString(2, spec);
                            ps.setString(3, "set");
                            ps.setLong(4, System.currentTimeMillis());
                            ps.executeUpdate();
                        }
                    }catch (SQLException ex){
                        core.getLogger().warning("Falha ao salvar last_set (async): "+ex.getMessage());
                    } finally{
                        try{ if(ps!=null) ps.close(); }catch(Exception ignored){}
                    }
                }
            });
        }
    }
}
