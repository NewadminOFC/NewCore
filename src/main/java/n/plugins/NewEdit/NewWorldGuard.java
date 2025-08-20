package n.plugins.NewEdit;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * NewWorldGuard (1.7.10 / Java 7)
 * Usa a MESMA configuração carregada pelo NewWorldEdit:
 *   raiz: guard.*
 *     - guard.messages.*
 *     - guard.global.flags.*
 *     - guard.worlds.<world>.regions.<id>...
 */
public final class NewWorldGuard implements Listener, CommandExecutor, TabCompleter {

    // ========= Integração / Config compartilhada =========
    private final NewWorldEdit we;        // componente WE (não é JavaPlugin)
    private JavaPlugin core;              // plugin Core (JavaPlugin) para registrar eventos/comandos/log

    public NewWorldGuard(NewWorldEdit we){
        this.we = we;
    }

    /** Chame isto a partir do NewCore.onEnable(): worldGuard.init(this); */
    public void init(JavaPlugin core){
        this.core = core;

        loadAll();

        // registrar eventos usando o Core
        Bukkit.getPluginManager().registerEvents(this, core);

        // comandos
        PluginCommand rg = core.getCommand("rg");
        if (rg == null) rg = core.getCommand("region");
        if (rg != null) {
            rg.setExecutor(this);
            rg.setTabCompleter(this);
        }
        PluginCommand flags = core.getCommand("flags");
        if (flags != null) {
            flags.setExecutor(this);
            flags.setTabCompleter(this);
        }

        core.getLogger().info("[NewWorldGuard] pronto. Regiões: " + countRegions());
    }

    public void shutdown(){ saveAll(); }

    private YamlConfiguration cfg(){ return we.cfg(); }
    private void saveCfg(){ we.saveCfg(); }
    private String p(String sub){ return "guard." + sub; } // path helper

    // ========= Seleção local (fallback) =========
    private static class Vec3i { int x,y,z; Vec3i(int x,int y,int z){this.x=x;this.y=y;this.z=z;} }
    private static class Sel { String world; Vec3i p1,p2; boolean complete(){return world!=null&&p1!=null&&p2!=null;} }
    private final Map<UUID, Sel> selections = new HashMap<UUID, Sel>();

    // ========= Região =========
    public static class Region {
        public String id, world;
        public int minX,minY,minZ,maxX,maxY,maxZ;
        public int priority;
        public Set<String> owners = new HashSet<String>();
        public Set<String> members = new HashSet<String>();
        public Map<String,String> flags = new HashMap<String, String>();

        boolean contains(Location l){
            if(l==null || world==null) return false;
            if(!l.getWorld().getName().equals(world)) return false;
            int x=l.getBlockX(), y=l.getBlockY(), z=l.getBlockZ();
            return x>=minX && x<=maxX && y>=minY && y<=maxY && z>=minZ && z<=maxZ;
        }
        boolean isOwner(String who){ return who!=null && owners.contains(who.toLowerCase()); }
        boolean isMember(String who){ return who!=null && (members.contains(who.toLowerCase()) || owners.contains(who.toLowerCase())); }
        int volume(){ return Math.max(1,(maxX-minX+1)) * Math.max(1,(maxY-minY+1)) * Math.max(1,(maxZ-minZ+1)); }
        String toStringShort(){ return id+"@"+world+"["+minX+","+minY+","+minZ+" -> "+maxX+","+maxY+","+maxZ+"]"; }
    }

    // ========= Flags =========
    private enum Flag {
        PASSTHROUGH("passthrough", true),
        BUILD("build", true), INTERACT("interact", true), BLOCK_BREAK("block-break", true), BLOCK_PLACE("block-place", true),
        USE("use", true), DAMAGE_ANIMALS("damage-animals", true), CHEST_ACCESS("chest-access", true),
        RIDE("ride", true), PVP("pvp", true), SLEEP("sleep", true), TNT("tnt", true),
        VEHICLE_PLACE("vehicle-place", true), VEHICLE_DESTROY("vehicle-destroy", true), LIGHTER("lighter", true),
        CREEPER_EXPLOSION("creeper-explosion", true), ENDERDRAGON_BLOCK_DAMAGE("enderdragon-block-damage", true),
        GHAST_FIREBALL("ghast-fireball", true), OTHER_EXPLOSION("other-explosion", true),
        FIRE_SPREAD("fire-spread", true), ENDERMAN_GRIEF("enderman-grief", true),
        MOB_DAMAGE("mob-damage", true), MOB_SPAWNING("mob-spawning", true),
        DENY_SPAWN("deny-spawn", false), ENTITY_PAINTING_DESTROY("entity-painting-destroy", true),
        ENTITY_ITEM_FRAME_DESTROY("entity-item-frame-destroy", true),
        LAVA_FIRE("lava-fire", true), LIGHTNING("lightning", true), WATER_FLOW("water-flow", true),
        LAVA_FLOW("lava-flow", true), SNOW_FALL("snow-fall", true), SNOW_MELT("snow-melt", true),
        ICE_FORM("ice-form", true), ICE_MELT("ice-melt", true), MUSHROOM_GROWTH("mushroom-growth", true),
        LEAF_DECAY("leaf-decay", true), GRASS_GROWTH("grass-growth", true),
        MYCELIUM_SPREAD("mycelium-spread", true), VINE_GROWTH("vine-growth", true), SOIL_DRY("soil-dry", true),
        ITEM_PICKUP("item-pickup", true), ITEM_DROP("item-drop", true), EXP_DROPS("exp-drops", true),
        DENY_MESSAGE("deny-message", false), ENTRY("entry", true), EXIT("exit", true),
        GREETING("greeting", false), FAREWELL("farewell", false), ENDERPEARL("enderpearl", true),
        INVINCIBLE("invincible", true), GAME_MODE("game-mode", false),
        TIME_LOCK("time-lock", false), WEATHER_LOCK("weather-lock", false),
        HEAL_DELAY("heal-delay", false), HEAL_AMOUNT("heal-amount", false),
        HEAL_MIN_HEALTH("heal-min-health", false), HEAL_MAX_HEALTH("heal-max-health", false),
        FEED_DELAY("feed-delay", false), FEED_AMOUNT("feed-amount", false),
        FEED_MIN_HUNGER("feed-min-hunger", false), FEED_MAX_HUNGER("feed-max-hunger", false),
        TELEPORT("teleport", false), SPAWN("spawn", false),
        BLOCKED_CMDS("blocked-cmds", false), ALLOWED_CMDS("allowed-cmds", false),
        PISTONS("pistons", true), SEND_CHAT("send-chat", true), RECEIVE_CHAT("receive-chat", true),
        POTION_SPLASH("potion-splash", true), NOTIFY_ENTER("notify-enter", true), NOTIFY_LEAVE("notify-leave", true),
        ALLOW_SHOP("allow-shop", false), BUYABLE("buyable", false), PRICE("price", false),
        FALL_DAMAGE("fall-damage", true), EXIT_VIA_TELEPORT("exit-via-teleport", true);

        final String key; final boolean isBoolean;
        Flag(String k, boolean bool){ this.key=k; this.isBoolean=bool; }
        static Flag byKey(String k){
            if(k==null) return null;
            String kk=k.toLowerCase().replace('_','-');
            for(Flag f:values()) if(f.key.equals(kk)) return f;
            return null;
        }
    }

    // ========= Armazenamento =========
    private final Map<String, List<Region>> regionsByWorld = new HashMap<String, List<Region>>();
    private final Map<String, String> defaultGlobalFlags = new HashMap<String, String>(); // top-level defaults
    private final Map<String, Map<String,String>> worldGlobalFlags = new HashMap<String, Map<String,String>>(); // por-mundo

    // ========= Estado util =========
    private final Map<UUID, Long> lastHeal = new HashMap<UUID, Long>();
    private final Map<UUID, Long> lastFeed = new HashMap<UUID, Long>();
    private final Map<UUID, GameMode> prevGm = new HashMap<UUID, GameMode>();
    private final Map<UUID, String> lastRegionId = new HashMap<UUID, String>();
    private final Map<UUID, Location> deathLocation = new HashMap<UUID, Location>();

    // ========= Mensagens =========
    private String MSG_PREFIX = "&7[WG]&r ";
    private String MSG_NO_PERM = "&cSem permissão.";
    private String MSG_SET_POS1 = "&aPos1 setada (&f%x%&7,&f%y%&7,&f%z%&a) no mundo &f%world%";
    private String MSG_SET_POS2 = "&aPos2 setada (&f%x%&7,&f%y%&7,&f%z%&a) no mundo &f%world%";
    private String MSG_SEL_INCOMPLETE = "&cSeleção incompleta (/rg pos1 e /rg pos2).";
    private String MSG_DEFINED = "&aRegião &f%id%&a definida! Pri=&f%pri%&a, Mundo=&f%world%";
    private String MSG_REMOVED = "&aRegião &f%id%&a removida.";
    private String MSG_UNKNOWN_REGION = "&cRegião não encontrada: &f%id%";
    private String MSG_FLAG_SET = "&aFlag &f%flag%&a de &f%id%&a = &f%val%";
    private String MSG_INFO_HEADER = "&6Região &f%id% &7(pri=%pri%, world=%world%)";
    private String MSG_INFO_MEMBERS = "&7Owners: &f%owners% &8| &7Members: &f%members%";
    private String MSG_INFO_BOUNDS = "&7Bounds: &f(%minx%,%miny%,%minz%)&7 -> &f(%maxx%,%maxy%,%maxz%)";
    private String MSG_INFO_FLAGS = "&7Flags: &f%flags%";
    private String MSG_LIST = "&7Regiões (%n%): &f%list%";
    private String MSG_PRIORITY_SET = "&aPriority de &f%id%&a = &f%pri%";
    private String MSG_MEMBER_CHANGED = "&a%what% &f%who% &aem &f%id%";
    private String MSG_ENTRY_DENY = "&cEntrada negada.";
    private String MSG_EXIT_DENY = "&cSaída negada.";

    private String countRegions(){
        int t=0;
        for(Map.Entry<String,List<Region>> e: regionsByWorld.entrySet()){ if(e.getValue()!=null) t+=e.getValue().size(); }
        return String.valueOf(t);
    }

    private void loadAll(){
        defaultGlobalFlags.clear();
        worldGlobalFlags.clear();
        regionsByWorld.clear();

        // mensagens (guard.messages.*)
        MSG_PREFIX = cfg().getString(p("messages.prefix"), MSG_PREFIX);
        MSG_NO_PERM = cfg().getString(p("messages.no-perm"), MSG_NO_PERM);
        MSG_SET_POS1 = cfg().getString(p("messages.pos1"), MSG_SET_POS1);
        MSG_SET_POS2 = cfg().getString(p("messages.pos2"), MSG_SET_POS2);
        MSG_SEL_INCOMPLETE = cfg().getString(p("messages.sel-incomplete"), MSG_SEL_INCOMPLETE);
        MSG_DEFINED = cfg().getString(p("messages.defined"), MSG_DEFINED);
        MSG_REMOVED = cfg().getString(p("messages.removed"), MSG_REMOVED);
        MSG_UNKNOWN_REGION = cfg().getString(p("messages.unknown-region"), MSG_UNKNOWN_REGION);
        MSG_FLAG_SET = cfg().getString(p("messages.flag-set"), MSG_FLAG_SET);
        MSG_INFO_HEADER = cfg().getString(p("messages.info-header"), MSG_INFO_HEADER);
        MSG_INFO_MEMBERS = cfg().getString(p("messages.info-members"), MSG_INFO_MEMBERS);
        MSG_INFO_BOUNDS = cfg().getString(p("messages.info-bounds"), MSG_INFO_BOUNDS);
        MSG_INFO_FLAGS = cfg().getString(p("messages.info-flags"), MSG_INFO_FLAGS);
        MSG_LIST = cfg().getString(p("messages.list"), MSG_LIST);
        MSG_PRIORITY_SET = cfg().getString(p("messages.priority-set"), MSG_PRIORITY_SET);
        MSG_ENTRY_DENY = cfg().getString(p("messages.entry-deny"), MSG_ENTRY_DENY);
        MSG_EXIT_DENY = cfg().getString(p("messages.exit-deny"), MSG_EXIT_DENY);

        // globais default (top-level)
        ConfigurationSection g = cfg().getConfigurationSection(p("global.flags"));
        if(g!=null){
            for(String k : g.getKeys(false)){
                defaultGlobalFlags.put(k.toLowerCase(), g.getString(k,"").toLowerCase());
            }
        }

        // mundos
        ConfigurationSection worlds = cfg().getConfigurationSection(p("worlds"));
        if(worlds!=null){
            for(String w : worlds.getKeys(false)){
                ConfigurationSection ws = worlds.getConfigurationSection(w);
                if(ws==null) continue;

                // globais por mundo
                ConfigurationSection wg = ws.getConfigurationSection("global.flags");
                if(wg!=null){
                    Map<String,String> map = new HashMap<String, String>();
                    for(String fk : wg.getKeys(false)){
                        map.put(fk.toLowerCase(), wg.getString(fk,"").toLowerCase());
                    }
                    if(!map.isEmpty()) worldGlobalFlags.put(w, map);
                }

                // regiões
                ConfigurationSection rs = ws.getConfigurationSection("regions");
                if(rs!=null){
                    List<Region> list = new ArrayList<Region>();
                    for(String id : rs.getKeys(false)){
                        ConfigurationSection cs = rs.getConfigurationSection(id);
                        if(cs==null) continue;
                        Region r = new Region();
                        r.id=id; r.world=w;
                        r.minX = cs.getInt("min.x"); r.minY = cs.getInt("min.y"); r.minZ = cs.getInt("min.z");
                        r.maxX = cs.getInt("max.x"); r.maxY = cs.getInt("max.y"); r.maxZ = cs.getInt("max.z");
                        r.priority = cs.getInt("priority",0);
                        r.owners.addAll(lowerAll(cs.getStringList("owners")));
                        r.members.addAll(lowerAll(cs.getStringList("members")));
                        ConfigurationSection fs = cs.getConfigurationSection("flags");
                        if(fs!=null){
                            for(String fk : fs.getKeys(false)){
                                r.flags.put(fk.toLowerCase(), fs.getString(fk,"").toLowerCase());
                            }
                        }
                        list.add(r);
                    }
                    regionsByWorld.put(w, list);
                }
            }
        }

        // garante nós mínimos
        if (!cfg().contains(p("global.flags"))) {
            cfg().set(p("global.flags.build"), "allow");
            cfg().set(p("global.flags.pvp"), "allow");
            cfg().set(p("global.flags.mob-spawning"), "allow");
            cfg().set(p("global.flags.fire-spread"), "allow");
        }
        if (!cfg().contains(p("worlds"))) cfg().set(p("worlds"), new LinkedHashMap<String, Object>());
        saveCfg();
    }

    private void saveAll(){
        // zera mundos (somente o subtree)
        cfg().set(p("worlds"), null);

        // gravar por mundo
        for(World w : Bukkit.getWorlds()){
            String wn = w.getName();
            String base = p("worlds."+wn);

            // globais por mundo
            Map<String,String> gmap = worldGlobalFlags.get(wn);
            if(gmap!=null){
                for(Map.Entry<String,String> e : gmap.entrySet()){
                    cfg().set(base+".global.flags."+e.getKey(), e.getValue());
                }
            }

            // regiões
            List<Region> list = regionsByWorld.get(wn);
            if(list!=null){
                for(Region r : list){
                    String path = base+".regions."+r.id;
                    cfg().set(path+".min.x", r.minX);
                    cfg().set(path+".min.y", r.minY);
                    cfg().set(path+".min.z", r.minZ);
                    cfg().set(path+".max.x", r.maxX);
                    cfg().set(path+".max.y", r.maxY);
                    cfg().set(path+".max.z", r.maxZ);
                    cfg().set(path+".priority", r.priority);
                    cfg().set(path+".owners", new ArrayList<String>(r.owners));
                    cfg().set(path+".members", new ArrayList<String>(r.members));
                    for(Map.Entry<String,String> f : r.flags.entrySet()){
                        cfg().set(path+".flags."+f.getKey(), f.getValue());
                    }
                }
            }
        }

        // defaults top-level — já estão em guard.global.flags (não apagar)
        saveCfg();
    }

    private List<String> lowerAll(List<String> in){
        List<String> out=new ArrayList<String>();
        if(in!=null) for(String s:in) if(s!=null) out.add(s.toLowerCase());
        return out;
    }
    private String color(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
    private void msg(CommandSender s, String m, String[][] ph){
        String out = MSG_PREFIX + (m==null?"":m);
        if(ph!=null) for(int i=0;i<ph.length;i++) out = out.replace(ph[i][0], ph[i][1]);
        s.sendMessage(color(out));
    }
    private void msg(CommandSender s, String m){ msg(s,m,null); }
    private String join(List<String> l, String sep){ if(l==null||l.isEmpty()) return ""; StringBuilder sb=new StringBuilder(); for(int i=0;i<l.size();i++){ if(i>0) sb.append(sep); sb.append(l.get(i)); } return sb.toString(); }
    private int parseInt(String s, int def){ try{ return Integer.parseInt(s.trim()); }catch(Exception e){ return def; } }
    private double parseDouble(String s, double def){ try{ return Double.parseDouble(s.trim()); }catch(Exception e){ return def; } }
    private boolean isTrue(String raw, boolean dflt){
        if(raw==null) return dflt;
        String v=raw.toLowerCase();
        if(v.equals("allow")||v.equals("true")||v.equals("yes")||v.equals("on")) return true;
        if(v.equals("deny")||v.equals("false")||v.equals("no")||v.equals("off")) return false;
        return dflt;
    }
    private Map<String,String> ensureWorldGlobals(String world){
        Map<String,String> m = worldGlobalFlags.get(world);
        if(m==null){ m = new HashMap<String, String>(); worldGlobalFlags.put(world,m); }
        return m;
    }

    private List<Region> regionStackAt(Location l){
        List<Region> all = regionsByWorld.get(l.getWorld().getName());
        List<Region> res = new ArrayList<Region>();
        if(all!=null) for(Region r:all) if(r.contains(l)) res.add(r);
        Collections.sort(res, new Comparator<Region>(){
            public int compare(Region a, Region b){
                if(a.priority!=b.priority) return b.priority - a.priority; // desc
                return a.volume() - b.volume(); // asc
            }
        });
        return res;
    }
    private Region effectiveRegionAt(Location l){
        List<Region> stack = regionStackAt(l);
        for(Region r : stack){
            String pt = r.flags.get(Flag.PASSTHROUGH.key);
            if(isTrue(pt, false)) continue;
            return r;
        }
        return null;
    }

    private String resolveFlagRaw(Region region, Flag f, Location ctx){
        if(region!=null){
            String v = region.flags.get(f.key);
            if(v!=null) return v;
        }
        if(ctx!=null){
            Map<String,String> wm = worldGlobalFlags.get(ctx.getWorld().getName());
            if(wm!=null){
                String v=wm.get(f.key);
                if(v!=null) return v;
            }
        }
        return defaultGlobalFlags.get(f.key);
    }
    private boolean flagAllows(Region region, Flag f, boolean dflt, Location ctx){
        String raw = resolveFlagRaw(region, f, ctx);
        return isTrue(raw, dflt);
    }

    private String denyMessage(Region r, String fallback){
        if(r==null) return fallback;
        String m = r.flags.get(Flag.DENY_MESSAGE.key);
        return (m!=null && m.length()>0) ? color(MSG_PREFIX + m) : color(MSG_PREFIX + fallback);
    }

    private Region getRegion(String world, String id){
        List<Region> list = regionsByWorld.get(world);
        if(list==null) return null;
        for(Region r: list) if(r.id.equalsIgnoreCase(id)) return r;
        return null;
    }
    private List<Region> ensureWorldList(String world){
        List<Region> list = regionsByWorld.get(world);
        if(list==null){ list=new ArrayList<Region>(); regionsByWorld.put(world, list); }
        return list;
    }
    private boolean isBypass(Player p){ return p.hasPermission("newwg.bypass"); }

    private Sel sel(Player p){
        Sel s = selections.get(p.getUniqueId());
        if(s==null){ s = new Sel(); selections.put(p.getUniqueId(), s); }
        return s;
    }

    private String defaultWorld(){
        World w = Bukkit.getWorlds().isEmpty()? null : Bukkit.getWorlds().get(0);
        return w==null? "world" : w.getName();
    }

    private Location parseLocation(String raw, World fallback){
        if(raw==null||raw.trim().length()==0) return null;
        String[] sp = raw.split("[, ]+");
        try{
            int i=0;
            World w = (sp[0].matches("-?\\d+(\\.\\d+)?")) ? fallback : Bukkit.getWorld(sp[0]);
            if(w==null){ w=fallback; } else i=1;
            double x = Double.parseDouble(sp[i++]);
            double y = Double.parseDouble(sp[i++]);
            double z = Double.parseDouble(sp[i++]);
            float yaw = (sp.length>i)? Float.parseFloat(sp[i++]):0f;
            float pitch = (sp.length>i)? Float.parseFloat(sp[i++]):0f;
            Location loc = new Location(w,x,y,z,yaw,pitch);
            return loc;
        }catch(Exception ex){ return null; }
    }
    private Set<String> parseCmdList(String raw){
        Set<String> out = new HashSet<String>();
        if(raw==null) return out;
        String[] sp = raw.split("[,; ]+");
        for(int i=0;i<sp.length;i++){
            String s = sp[i].trim();
            if(s.length()==0) continue;
            if(s.startsWith("/")) s = s.substring(1);
            out.add(s.toLowerCase());
        }
        return out;
    }
    private Set<EntityType> parseEntityTypeList(String raw){
        Set<EntityType> out = new HashSet<EntityType>();
        if (raw == null) return out;

        String[] parts = raw.split("[,; ]+"); // Java 7 ok
        for (int i = 0; i < parts.length; i++){
            String s = parts[i]
                    .trim()
                    .toUpperCase(java.util.Locale.ENGLISH)
                    .replace('-', '_')
                    .replace(' ', '_'); // aceita "zombie pigman" -> "ZOMBIE_PIGMAN"
            try {
                out.add(EntityType.valueOf(s));
            } catch (Exception ignored) {}
        }
        return out;
    }

    // ========= EVENTS =========

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Location l=e.getBlock().getLocation();
        Region r=effectiveRegionAt(l);
        if(r==null) {
            if(!flagAllows(null, Flag.BLOCK_BREAK, true, l) || !flagAllows(null, Flag.BUILD, true, l)) e.setCancelled(true);
            return;
        }
        if(!r.isMember(p.getName().toLowerCase()) && (!flagAllows(r, Flag.BLOCK_BREAK,true,l) || !flagAllows(r, Flag.BUILD,true,l))){
            p.sendMessage(denyMessage(r,"Você não pode quebrar blocos aqui."));
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Location l=e.getBlock().getLocation();
        Region r=effectiveRegionAt(l);
        if(r==null){
            if(!flagAllows(null, Flag.BLOCK_PLACE, true, l) || !flagAllows(null, Flag.BUILD, true, l)) e.setCancelled(true);
            return;
        }
        if(!r.isMember(p.getName().toLowerCase()) && (!flagAllows(r, Flag.BLOCK_PLACE,true,l) || !flagAllows(r, Flag.BUILD,true,l))){
            p.sendMessage(denyMessage(r,"Você não pode construir aqui."));
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e){
        if(e.getAction()==Action.PHYSICAL) return;
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Location l = e.hasBlock()? e.getClickedBlock().getLocation() : p.getLocation();
        Region r=effectiveRegionAt(l);

        if(r==null){
            if(!flagAllows(null, Flag.INTERACT,true,l) || !flagAllows(null, Flag.USE,true,l)) e.setCancelled(true);
        }else{
            if(!r.isMember(p.getName().toLowerCase()) && (!flagAllows(r, Flag.INTERACT,true,l) || !flagAllows(r, Flag.USE,true,l))){
                p.sendMessage(denyMessage(r,"Você não pode usar/interagir aqui."));
                e.setCancelled(true);
            }
            if(e.hasBlock()){
                Material t=e.getClickedBlock().getType();
                if(t==Material.CHEST || t==Material.TRAPPED_CHEST || t==Material.ENDER_CHEST){
                    if(!flagAllows(r, Flag.CHEST_ACCESS,true,l) && !r.isMember(p.getName().toLowerCase())){
                        p.sendMessage(denyMessage(r,"Acesso ao baú negado."));
                        e.setCancelled(true);
                    }
                }
            }
        }

        if(p.getItemInHand()!=null && p.getItemInHand().getType()==Material.ENDER_PEARL){
            if(!flagAllows(r, Flag.ENDERPEARL, true, l)) e.setCancelled(true);
        }
        if(p.getItemInHand()!=null){
            Material it = p.getItemInHand().getType();
            if(it==Material.BOAT || it.name().contains("MINECART")){
                if(!flagAllows(r, Flag.VEHICLE_PLACE, true, l)){
                    e.setCancelled(true);
                }
            }
            if(it==Material.FLINT_AND_STEEL && !flagAllows(r, Flag.LIGHTER, true, l)) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onBedEnter(PlayerBedEnterEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Region r=effectiveRegionAt(e.getBed().getLocation());
        if(!flagAllows(r, Flag.SLEEP,true,e.getBed().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onVehicleEnter(VehicleEnterEvent e){
        if(!(e.getEntered() instanceof Player)) return;
        Player p=(Player)e.getEntered(); if(isBypass(p)) return;
        Region r=effectiveRegionAt(e.getVehicle().getLocation());
        if(!flagAllows(r, Flag.RIDE,true,e.getVehicle().getLocation())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent e){
        Entity attacker = e.getAttacker();
        Location l=e.getVehicle().getLocation();
        Region r=effectiveRegionAt(l);
        if(attacker instanceof Player && !isBypass((Player)attacker)){
            if(!flagAllows(r, Flag.VEHICLE_DESTROY,true,l)) e.setCancelled(true);
        } else {
            if(!flagAllows(r, Flag.VEHICLE_DESTROY,true,l)) e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onVehicleCreate(VehicleCreateEvent e){}

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent e){
        Entity ent=e.getEntity(); Location l=e.getLocation();
        Region r=effectiveRegionAt(l);
        if(ent instanceof Creeper){
            if(!flagAllows(r, Flag.CREEPER_EXPLOSION,true,l)) e.blockList().clear();
            return;
        }
        if(ent instanceof Ghast || (ent!=null && ent.getType()==EntityType.FIREBALL)){
            if(!flagAllows(r, Flag.GHAST_FIREBALL,true,l)) e.blockList().clear();
            return;
        }
        if(ent instanceof EnderDragon){
            if(!flagAllows(r, Flag.ENDERDRAGON_BLOCK_DAMAGE,true,l)) e.blockList().clear();
            return;
        }
        if(!flagAllows(r, Flag.TNT,true,l) || !flagAllows(r, Flag.OTHER_EXPLOSION,true,l)){
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onIgnite(BlockIgniteEvent e){
        Location l=e.getBlock().getLocation();
        Region r=effectiveRegionAt(l);
        BlockIgniteEvent.IgniteCause c=e.getCause();
        if(c==BlockIgniteEvent.IgniteCause.LAVA && !flagAllows(r, Flag.LAVA_FIRE,true,l)) e.setCancelled(true);
        if(c==BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && !flagAllows(r, Flag.LIGHTER,true,l)) e.setCancelled(true);
        if(c==BlockIgniteEvent.IgniteCause.SPREAD && !flagAllows(r, Flag.FIRE_SPREAD,true,l)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onSpread(BlockSpreadEvent e){
        Location l=e.getBlock().getLocation();
        Region r=effectiveRegionAt(l);
        Material src = e.getSource().getType();

        if(src==Material.FIRE && !flagAllows(r, Flag.FIRE_SPREAD,true,l)) { e.setCancelled(true); return; }
        if(src==Material.LONG_GRASS || src==Material.GRASS){
            if(!flagAllows(r, Flag.GRASS_GROWTH,true,l)) e.setCancelled(true);
        }
        if(src==Material.MYCEL && !flagAllows(r, Flag.MYCELIUM_SPREAD,true,l)) e.setCancelled(true);
        if((src==Material.RED_MUSHROOM || src==Material.BROWN_MUSHROOM) && !flagAllows(r, Flag.MUSHROOM_GROWTH,true,l)) e.setCancelled(true);
        if(src==Material.VINE && !flagAllows(r, Flag.VINE_GROWTH,true,l)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onFlow(BlockFromToEvent e){
        Location to=new Location(e.getBlock().getWorld(), e.getToBlock().getX(), e.getToBlock().getY(), e.getToBlock().getZ());
        Region r=effectiveRegionAt(to);
        Material t=e.getBlock().getType();
        if((t==Material.WATER || t==Material.STATIONARY_WATER) && !flagAllows(r, Flag.WATER_FLOW,true,to)) e.setCancelled(true);
        if((t==Material.LAVA || t==Material.STATIONARY_LAVA) && !flagAllows(r, Flag.LAVA_FLOW,true,to)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onForm(BlockFormEvent e){
        Location l=e.getBlock().getLocation(); Region r=effectiveRegionAt(l);
        Material newT = e.getNewState().getType();
        if(newT==Material.ICE && !flagAllows(r, Flag.ICE_FORM,true,l)) e.setCancelled(true);
        if((newT==Material.SNOW || newT==Material.SNOW_BLOCK) && !flagAllows(r, Flag.SNOW_FALL,true,l)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onFade(BlockFadeEvent e){
        Location l=e.getBlock().getLocation(); Region r=effectiveRegionAt(l);
        Material oldT = e.getBlock().getType();
        if(oldT==Material.ICE && !flagAllows(r, Flag.ICE_MELT,true,l)) e.setCancelled(true);
        if((oldT==Material.SNOW || oldT==Material.SNOW_BLOCK) && !flagAllows(r, Flag.SNOW_MELT,true,l)) e.setCancelled(true);
        if(oldT==Material.SOIL && !flagAllows(r, Flag.SOIL_DRY,true,l)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent e){
        Location l=e.getLocation(); Region r=effectiveRegionAt(l);
        if(!flagAllows(r, Flag.MOB_SPAWNING,true,l)) { e.setCancelled(true); return; }
        String raw = resolveFlagRaw(r, Flag.DENY_SPAWN, l);
        if(raw!=null){
            Set<EntityType> deny = parseEntityTypeList(raw);
            if(deny.contains(e.getEntityType())) e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent e){
        if(e.getEntity() instanceof Enderman){
            Location l=e.getBlock().getLocation(); Region r=effectiveRegionAt(l);
            if(!flagAllows(r, Flag.ENDERMAN_GRIEF,true,l)) e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onMobDamage(EntityDamageByEntityEvent e){
        Entity victim=e.getEntity();
        Location l=victim.getLocation(); Region r=effectiveRegionAt(l);

        if(victim instanceof Player){
            if(!flagAllows(r, Flag.PVP,true,l) || flagAllows(r, Flag.INVINCIBLE,false,l)){
                e.setCancelled(true); return;
            }
        } else {
            if(!flagAllows(r, Flag.MOB_DAMAGE,true,l)) { e.setCancelled(true); return; }
            if(victim instanceof Animals && e.getDamager() instanceof Player){
                if(!flagAllows(r, Flag.DAMAGE_ANIMALS,true,l)) { e.setCancelled(true); return; }
            }
        }
        if(victim instanceof ItemFrame){
            if(!flagAllows(r, Flag.ENTITY_ITEM_FRAME_DESTROY,true,l)) e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onPaintingBreak(HangingBreakByEntityEvent e){
        if(!(e.getRemover() instanceof Player)) return;
        Location l=e.getEntity().getLocation(); Region r=effectiveRegionAt(l);
        if(!flagAllows(r, Flag.ENTITY_PAINTING_DESTROY,true,l)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Region r=effectiveRegionAt(p.getLocation());
        if(!flagAllows(r, Flag.SEND_CHAT, true, p.getLocation())){
            e.setCancelled(true); return;
        }
        Iterator<Player> it = e.getRecipients().iterator();
        while(it.hasNext()){
            Player rec = it.next();
            Region rr = effectiveRegionAt(rec.getLocation());
            if(!flagAllows(rr, Flag.RECEIVE_CHAT, true, rec.getLocation())){
                it.remove();
            }
        }
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        String cmd = e.getMessage();
        if(cmd.startsWith("/")) cmd = cmd.substring(1);
        String root = cmd.split("[ ]+")[0].toLowerCase();
        Location l=p.getLocation(); Region r=effectiveRegionAt(l);

        String allowRaw = resolveFlagRaw(r, Flag.ALLOWED_CMDS, l);
        if(allowRaw!=null && allowRaw.trim().length()>0){
            Set<String> allow = parseCmdList(allowRaw);
            if(!allow.contains(root)) { e.setCancelled(true); p.sendMessage(denyMessage(r,"Comando bloqueado nesta região.")); return; }
        }
        String blockRaw = resolveFlagRaw(r, Flag.BLOCKED_CMDS, l);
        if(blockRaw!=null && blockRaw.trim().length()>0){
            Set<String> block = parseCmdList(blockRaw);
            if(block.contains(root)) { e.setCancelled(true); p.sendMessage(denyMessage(r,"Comando bloqueado nesta região.")); return; }
        }
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onPickup(PlayerPickupItemEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Region r=effectiveRegionAt(e.getItem().getLocation());
        if(!flagAllows(r, Flag.ITEM_PICKUP,true,e.getItem().getLocation())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Region r=effectiveRegionAt(e.getItemDrop().getLocation());
        if(!flagAllows(r, Flag.ITEM_DROP,true,e.getItemDrop().getLocation())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onExp(PlayerExpChangeEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Region r=effectiveRegionAt(p.getLocation());
        if(!flagAllows(r, Flag.EXP_DROPS,true,p.getLocation())) e.setAmount(0);
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent e){
        Location l=e.getEntity().getLocation(); Region r=effectiveRegionAt(l);
        if(!flagAllows(r, Flag.POTION_SPLASH,true,l)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onWeather(WeatherChangeEvent e){
        Map<String,String> wm = worldGlobalFlags.get(e.getWorld().getName());
        String lock = wm!=null? wm.get(Flag.WEATHER_LOCK.key) : defaultGlobalFlags.get(Flag.WEATHER_LOCK.key);
        if(lock!=null){
            if(lock.equalsIgnoreCase("clear") && e.toWeatherState()) e.setCancelled(true);
            if(lock.equalsIgnoreCase("downfall") && !e.toWeatherState()) e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onLightning(LightningStrikeEvent e){
        Map<String,String> wm = worldGlobalFlags.get(e.getWorld().getName());
        String raw = wm!=null? wm.get(Flag.LIGHTNING.key) : defaultGlobalFlags.get(Flag.LIGHTNING.key);
        if(raw!=null && !isTrue(raw,true)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player)) return;
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL){
            Player p=(Player)e.getEntity();
            Region r=effectiveRegionAt(p.getLocation());
            if(!flagAllows(r, Flag.FALL_DAMAGE,true,p.getLocation())) e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true, priority=EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent e){
        Player p=e.getPlayer(); if(isBypass(p)) return;
        Region from=effectiveRegionAt(e.getFrom());
        if(from!=null && !flagAllows(from, Flag.EXIT,true,e.getFrom())){
            if(flagAllows(from, Flag.EXIT_VIA_TELEPORT, false, e.getFrom())){
                return;
            }
            e.setCancelled(true);
            p.sendMessage(denyMessage(from, MSG_EXIT_DENY));
        }
    }

    @EventHandler(ignoreCancelled=false, priority=EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e){
        if(e.getFrom()==null||e.getTo()==null) return;
        if(e.getFrom().getBlockX()==e.getTo().getBlockX() && e.getFrom().getBlockY()==e.getTo().getBlockY() && e.getFrom().getBlockZ()==e.getTo().getBlockZ()) return;

        Player p=e.getPlayer(); if(isBypass(p)) return;

        Region from=effectiveRegionAt(e.getFrom());
        Region to=effectiveRegionAt(e.getTo());

        if(from!=to){
            if(from!=null && !flagAllows(from, Flag.EXIT,true,e.getFrom())){
                e.setTo(e.getFrom());
                p.sendMessage(denyMessage(from, MSG_EXIT_DENY));
                return;
            }
            if(to!=null && !flagAllows(to, Flag.ENTRY,true,e.getTo())){
                e.setTo(e.getFrom());
                p.sendMessage(denyMessage(to, MSG_ENTRY_DENY));
                return;
            }
            if(from!=null && flagAllows(from, Flag.NOTIFY_LEAVE,true,e.getFrom())){
                notifyStaff("&e" + p.getName() + " &7saiu de &f" + from.id + "&7.");
            }
            if(to!=null && flagAllows(to, Flag.NOTIFY_ENTER,true,e.getTo())){
                notifyStaff("&e" + p.getName() + " &7entrou em &f" + to.id + "&7.");
            }
            if(from!=null){
                String fw = resolveFlagRaw(from, Flag.FAREWELL, e.getFrom());
                if(fw!=null && fw.length()>0) p.sendMessage(color(MSG_PREFIX + fw));
            }
            if(to!=null){
                String gr = resolveFlagRaw(to, Flag.GREETING, e.getTo());
                if(gr!=null && gr.length()>0) p.sendMessage(color(MSG_PREFIX + gr));
            }
            applyGameModeOnEnter(p, to);
            applyTimeWeatherLocks(p, to);
        }
        applyHealFeed(p, to);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e){
        deathLocation.put(e.getEntity().getUniqueId(), e.getEntity().getLocation());
    }
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e){
        Player p=e.getPlayer();
        Location death = deathLocation.remove(p.getUniqueId());
        if(death==null) return;
        Region r = effectiveRegionAt(death);
        if(r==null) return;
        String raw = resolveFlagRaw(r, Flag.SPAWN, death);
        Location loc = parseLocation(raw, death.getWorld());
        if(loc!=null) e.setRespawnLocation(loc);
    }

    private void applyGameModeOnEnter(Player p, Region to){
        if(to==null || resolveFlagRaw(to, Flag.GAME_MODE, p.getLocation())==null){
            if(prevGm.containsKey(p.getUniqueId())){
                try{ p.setGameMode(prevGm.remove(p.getUniqueId())); }catch(Exception ignored){}
            }
            return;
        }
        String gmRaw = resolveFlagRaw(to, Flag.GAME_MODE, p.getLocation());
        if(gmRaw==null || gmRaw.trim().length()==0) return;
        GameMode target = p.getGameMode();
        if(gmRaw.equalsIgnoreCase("survival")) target=GameMode.SURVIVAL;
        else if(gmRaw.equalsIgnoreCase("creative")) target=GameMode.CREATIVE;
        else if(gmRaw.equalsIgnoreCase("adventure")) target=GameMode.ADVENTURE;
        else return;
        if(!prevGm.containsKey(p.getUniqueId())) prevGm.put(p.getUniqueId(), p.getGameMode());
        try{ p.setGameMode(target); }catch(Exception ignored){}
    }

    private void applyTimeWeatherLocks(Player p, Region to){
        if(to!=null){
            String tRaw = resolveFlagRaw(to, Flag.TIME_LOCK, p.getLocation());
            if(tRaw!=null && tRaw.trim().length()>0){
                long base = p.getWorld().getTime();
                try{
                    if(tRaw.startsWith("+")||tRaw.startsWith("-")){
                        long delta = Long.parseLong(tRaw);
                        p.setPlayerTime(base + delta, false);
                    }else{
                        long fixed = Long.parseLong(tRaw);
                        p.setPlayerTime(fixed, false);
                    }
                }catch(Exception ignored){}
            } else {
                p.resetPlayerTime();
            }
            String wRaw = resolveFlagRaw(to, Flag.WEATHER_LOCK, p.getLocation());
            if(wRaw!=null){
                if(wRaw.equalsIgnoreCase("clear")) p.setPlayerWeather(WeatherType.CLEAR);
                else if(wRaw.equalsIgnoreCase("downfall")) p.setPlayerWeather(WeatherType.DOWNFALL);
            } else {
                p.resetPlayerWeather();
            }
        } else {
            p.resetPlayerTime();
            p.resetPlayerWeather();
        }
    }

    private void applyHealFeed(Player p, Region to){
        long now = System.currentTimeMillis();
        if(to!=null){
            int delay = parseInt(resolveFlagRaw(to, Flag.HEAL_DELAY, p.getLocation()), -1);
            if(delay>=0){
                long last = lastHeal.containsKey(p.getUniqueId())? lastHeal.get(p.getUniqueId()):0L;
                if(now - last >= delay*1000L){
                    double amt = parseDouble(resolveFlagRaw(to, Flag.HEAL_AMOUNT, p.getLocation()), 0);
                    double min = parseDouble(resolveFlagRaw(to, Flag.HEAL_MIN_HEALTH, p.getLocation()), 0);
                    double max = parseDouble(resolveFlagRaw(to, Flag.HEAL_MAX_HEALTH, p.getLocation()), 20);
                    double newHp = Math.min(max, Math.max(min, p.getHealth()+amt));
                    try{ p.setHealth(newHp); }catch(Exception ignored){}
                    lastHeal.put(p.getUniqueId(), now);
                }
            }
            int fdelay = parseInt(resolveFlagRaw(to, Flag.FEED_DELAY, p.getLocation()), -1);
            if(fdelay>=0){
                long last = lastFeed.containsKey(p.getUniqueId())? lastFeed.get(p.getUniqueId()):0L;
                if(now - last >= fdelay*1000L){
                    int amt = (int)parseDouble(resolveFlagRaw(to, Flag.FEED_AMOUNT, p.getLocation()), 0);
                    int min = (int)parseDouble(resolveFlagRaw(to, Flag.FEED_MIN_HUNGER, p.getLocation()), 0);
                    int max = (int)parseDouble(resolveFlagRaw(to, Flag.FEED_MAX_HUNGER, p.getLocation()), 20);
                    int newFood = Math.min(max, Math.max(min, p.getFoodLevel()+amt));
                    p.setFoodLevel(newFood);
                    lastFeed.put(p.getUniqueId(), now);
                }
            }
        }
    }

    private void notifyStaff(String rawMsg){
        String m = color(MSG_PREFIX + rawMsg);
        for(Player pl : Bukkit.getOnlinePlayers()){
            if(pl.hasPermission("newwg.notify")) pl.sendMessage(m);
        }
    }

    // ========= Comandos =========
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String base = cmd.getName().toLowerCase();

        if (base.equals("flags")) {
            int page;
            if (args.length == 0) { page = 1;
            } else if ("list".equalsIgnoreCase(args[0])) { page = (args.length >= 2) ? parseInt(args[1], 1) : 1;
            } else { page = parseInt(args[0], 1); }
            listAllFlags(sender, page);
            return true;
        }

        if(!base.equals("region") && !base.equals("rg")) return false;
        if(args.length==0){ help(sender); return true; }
        String sub=args[0].toLowerCase();

        try{
            if("pos1".equals(sub)){
                if(!(sender instanceof Player)){ msg(sender,"&cSomente in-game."); return true; }
                Player p=(Player)sender;
                Sel s=sel(p);
                s.world=p.getWorld().getName();
                s.p1=new Vec3i(p.getLocation().getBlockX(),p.getLocation().getBlockY(),p.getLocation().getBlockZ());
                msg(sender, MSG_SET_POS1, new String[][]{{"%x%",""+s.p1.x},{"%y%",""+s.p1.y},{"%z%",""+s.p1.z},{"%world%",s.world}});
                return true;
            }
            if("pos2".equals(sub)){
                if(!(sender instanceof Player)){ msg(sender,"&cSomente in-game."); return true; }
                Player p=(Player)sender;
                Sel s=sel(p);
                s.world=p.getWorld().getName();
                s.p2=new Vec3i(p.getLocation().getBlockX(),p.getLocation().getBlockY(),p.getLocation().getBlockZ());
                msg(sender, MSG_SET_POS2, new String[][]{{"%x%",""+s.p2.x},{"%y%",""+s.p2.y},{"%z%",""+s.p2.z},{"%world%",s.world}});
                return true;
            }
            if("define".equals(sub)){
                if(!(sender instanceof Player)){ msg(sender,"&cSomente in-game."); return true; }
                if(args.length<2){ helpDefine(sender); return true; }
                Player p=(Player)sender;

                // integração direta com o WE componente (sem reflection)
                String worldSel = we.getSelectionWorld(p);
                int[] box = we.getSelectionBox(p);

                if(worldSel==null || box==null){
                    Sel sLocal = selections.get(p.getUniqueId());
                    if(sLocal==null || !sLocal.complete()){ msg(sender, MSG_SEL_INCOMPLETE); return true; }
                    worldSel = sLocal.world;
                    Vec3i a = new Vec3i(Math.min(sLocal.p1.x,sLocal.p2.x), Math.min(sLocal.p1.y,sLocal.p2.y), Math.min(sLocal.p1.z,sLocal.p2.z));
                    Vec3i b = new Vec3i(Math.max(sLocal.p1.x,sLocal.p2.x), Math.max(sLocal.p1.y,sLocal.p2.y), Math.max(sLocal.p1.z,sLocal.p2.z));
                    box = new int[]{a.x,a.y,a.z,b.x,b.y,b.z};
                }

                String id=args[1].toLowerCase();
                Region r=new Region();
                r.id=id; r.world=worldSel;
                r.minX=box[0]; r.minY=box[1]; r.minZ=box[2];
                r.maxX=box[3]; r.maxY=box[4]; r.maxZ=box[5];
                r.priority=0;
                r.owners.add(p.getName().toLowerCase());
                ensureWorldList(r.world).add(r);
                saveAll();
                msg(sender, MSG_DEFINED, new String[][]{{"%id%",id},{"%pri%",""+r.priority},{"%world%",r.world}});
                return true;
            }
            if("remove".equals(sub) || "delete".equals(sub)){
                if(args.length<2){ msg(sender,"&eUso: /rg remove <id>"); return true; }
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();
                Region r=getRegion(world, args[1]);
                if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",args[1]}}); return true; }
                regionsByWorld.get(world).remove(r);
                saveAll();
                msg(sender, MSG_REMOVED, new String[][]{{"%id%",r.id}});
                return true;
            }
            if("flag".equals(sub)){
                if(args.length<4){ msg(sender,"&eUso: /rg flag <id|__global__> <flag> <valor>"); return true; }
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();
                String target=args[1];
                String flagName=args[2];
                String val = join(Arrays.asList(Arrays.copyOfRange(args,3,args.length)), " ").toLowerCase();

                Flag f = Flag.byKey(flagName);
                String fk = (f!=null? f.key : flagName.toLowerCase());

                if("__global__".equalsIgnoreCase(target) || "global".equalsIgnoreCase(target)){
                    Map<String,String> m = ensureWorldGlobals(world);
                    m.put(fk, val);
                    saveAll();
                    msg(sender, "&aFlag global (&f"+fk+"&a) do mundo &f"+world+"&a = &f"+val, null);
                    return true;
                }

                Region r=getRegion(world, target);
                if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",target}}); return true; }
                r.flags.put(fk, val);
                saveAll();
                msg(sender, MSG_FLAG_SET, new String[][]{{"%flag%",fk},{"%id%",r.id},{"%val%",val}});
                return true;
            }
            if("info".equals(sub)){
                if(args.length<2){ msg(sender,"&eUso: /rg info <id|__global__>"); return true; }
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();

                if("__global__".equalsIgnoreCase(args[1]) || "global".equalsIgnoreCase(args[1])){
                    Map<String,String> m = worldGlobalFlags.get(world);
                    msg(sender, "&6__global__ &7(world="+world+")", null);
                    msg(sender, MSG_INFO_FLAGS, new String[][]{{"%flags%", (m==null||m.isEmpty())?"(vazio)": m.toString()}});
                    return true;
                }

                Region r=getRegion(world, args[1]);
                if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",args[1]}}); return true; }
                msg(sender, MSG_INFO_HEADER, new String[][]{{"%id%",r.id},{"%pri%",""+r.priority},{"%world%",r.world}});
                msg(sender, MSG_INFO_BOUNDS, new String[][]{
                        {"%minx%",""+r.minX},{"%miny%",""+r.minY},{"%minz%",""+r.minZ},
                        {"%maxx%",""+r.maxX},{"%maxy%",""+r.maxY},{"%maxz%",""+r.maxZ}
                });
                msg(sender, MSG_INFO_MEMBERS, new String[][]{
                        {"%owners%", join(new ArrayList<String>(r.owners),",")},
                        {"%members%", join(new ArrayList<String>(r.members),",")}
                });
                msg(sender, MSG_INFO_FLAGS, new String[][]{{"%flags%", r.flags.isEmpty()?"(vazio)": r.flags.toString()}});
                return true;
            }
            if("list".equals(sub)){
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();
                List<Region> ls=regionsByWorld.get(world);
                List<String> ids=new ArrayList<String>(); if(ls!=null) for(Region r:ls) ids.add(r.id);
                msg(sender, MSG_LIST, new String[][]{{"%n%",""+ids.size()},{"%list%", ids.isEmpty()?"(nenhuma)": join(ids,", ")}});
                return true;
            }
            if("addmember".equals(sub) || "addmem".equals(sub)){
                if(args.length<3){ msg(sender,"&eUso: /rg addmember <id> <jogador>"); return true; }
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();
                Region r=getRegion(world, args[1]); if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",args[1]}}); return true; }
                r.members.add(args[2].toLowerCase()); saveAll();
                msg(sender, MSG_MEMBER_CHANGED, new String[][]{{"%what%","Adicionado membro"},{"%who%",args[2]},{"%id%",r.id}});
                return true;
            }
            if("addowner".equals(sub)){
                if(args.length<3){ msg(sender,"&eUso: /rg addowner <id> <jogador>"); return true; }
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();
                Region r=getRegion(world, args[1]); if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",args[1]}}); return true; }
                r.owners.add(args[2].toLowerCase()); saveAll();
                msg(sender, MSG_MEMBER_CHANGED, new String[][]{{"%what%","Adicionado owner"},{"%who%",args[2]},{"%id%",r.id}});
                return true;
            }
            if("removemember".equals(sub) || "remmember".equals(sub)){
                if(args.length<3){ msg(sender,"&eUso: /rg removemember <id> <jogador>"); return true; }
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();
                Region r=getRegion(world, args[1]); if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",args[1]}}); return true; }
                r.members.remove(args[2].toLowerCase()); saveAll();
                msg(sender, MSG_MEMBER_CHANGED, new String[][]{{"%what%","Removido membro"},{"%who%",args[2]},{"%id%",r.id}});
                return true;
            }
            if("removeowner".equals(sub) || "remowner".equals(sub)){
                if(args.length<3){ msg(sender,"&eUso: /rg removeowner <id> <jogador>"); return true; }
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();
                Region r=getRegion(world, args[1]); if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",args[1]}}); return true; }
                r.owners.remove(args[2].toLowerCase()); saveAll();
                msg(sender, MSG_MEMBER_CHANGED, new String[][]{{"%what%","Removido owner"},{"%who%",args[2]},{"%id%",r.id}});
                return true;
            }
            if("setpriority".equals(sub) || "priority".equals(sub)){
                if(args.length<3){ msg(sender,"&eUso: /rg setpriority <id> <numero>"); return true; }
                String world = (sender instanceof Player)? ((Player)sender).getWorld().getName() : defaultWorld();
                Region r=getRegion(world, args[1]); if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",args[1]}}); return true; }
                int pri=parseInt(args[2],0); r.priority=pri; saveAll();
                msg(sender, MSG_PRIORITY_SET, new String[][]{{"%id%",r.id},{"%pri%",""+pri}});
                return true;
            }
            if("teleport".equals(sub)){
                if(!(sender instanceof Player)){ msg(sender,"&cSomente in-game."); return true; }
                if(args.length<2){ msg(sender,"&eUso: /rg teleport <id>"); return true; }
                Player p=(Player)sender;
                String world = p.getWorld().getName();
                Region r=getRegion(world, args[1]); if(r==null){ msg(sender, MSG_UNKNOWN_REGION, new String[][]{{"%id%",args[1]}}); return true; }
                String raw = resolveFlagRaw(r, Flag.TELEPORT, p.getLocation());
                if(raw==null) raw = resolveFlagRaw(r, Flag.SPAWN, p.getLocation());
                Location loc = parseLocation(raw, p.getWorld());
                if(loc==null){ msg(sender,"&cRegião não tem flag teleport/spawn válida."); return true; }
                p.teleport(loc);
                msg(sender, "&aTeleportado para &f"+r.id, null);
                return true;
            }

            help(sender);
            return true;
        }catch(Exception ex){
            ex.printStackTrace();
            msg(sender, "&cErro no comando. Veja console.");
            return true;
        }
    }

    private void helpDefine(CommandSender sender) { msg(sender, "&eUso: /rg define <id>"); }

    private void listAllFlags(CommandSender s, int page){
        List<String> keys=new ArrayList<String>();
        for(Flag f:Flag.values()) keys.add(f.key);
        Collections.sort(keys);
        int per=14; int pages=Math.max(1,(keys.size()+per-1)/per);
        if(page<1) page=1; if(page>pages) page=pages;
        int start=(page-1)*per, end=Math.min(keys.size(), start+per);

        s.sendMessage(color(MSG_PREFIX + "&6Flags ("+page+"/"+pages+")&7:"));
        StringBuilder line=new StringBuilder("&7");
        for(int i=start;i<end;i++){ if(i>start) line.append("&8, &7"); line.append(keys.get(i)); }
        s.sendMessage(color(MSG_PREFIX + line.toString()));
        if(page<pages) s.sendMessage(color(MSG_PREFIX + "&7Use &e/flags list "+(page+1)+" &7para próxima página."));
    }

    private void help(CommandSender s){
        String[] lines=new String[]{
                "&6/rg pos1 &7- marca pos1 | &6/rg pos2 &7- marca pos2",
                "&6/rg define <id> &7- cria região pela seleção (integra com NewWorldEdit se disponível)",
                "&6/rg remove <id> &7- remove região | &6/rg list &7- lista regiões do mundo",
                "&6/rg flag <id|__global__> <flag> <valor> &7- seta flag (allow/deny, texto, número ou lista)",
                "&6/rg info <id|__global__> &7- info de região/globais | &6/rg setpriority <id> <n>",
                "&6/rg teleport <id> &7- teleporta para flag teleport/spawn da região",
                "&6/flags list &7- lista todas as flags"
        };
        for(int i=0;i<lines.length;i++) s.sendMessage(color(MSG_PREFIX+lines[i]));
    }

    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        String base=c.getName().toLowerCase();
        List<String> out=new ArrayList<String>();

        if(base.equals("flags")){
            if(a.length==1){ if("list".startsWith(a[0].toLowerCase())) out.add("list"); return out; }
            if(a.length==2){ out.add("1"); out.add("2"); out.add("3"); }
            return out;
        }

        if(a.length==1){
            String q=a[0].toLowerCase();
            String[] subs={"pos1","pos2","define","remove","flag","info","list","addmember","removemember","addowner","removeowner","setpriority","teleport"};
            for(int i=0;i<subs.length;i++) if(subs[i].startsWith(q)) out.add(subs[i]);
            return out;
        }
        if(a.length==2){
            String world = (s instanceof Player)? ((Player)s).getWorld().getName() : defaultWorld();
            List<Region> list = regionsByWorld.get(world);
            if("flag".equalsIgnoreCase(a[0]) || "info".equalsIgnoreCase(a[0]) || "teleport".equalsIgnoreCase(a[0])){
                if("__global__".startsWith(a[1].toLowerCase())) out.add("__global__");
            }
            if(list!=null){
                String q=a[1].toLowerCase();
                for(Region r:list) if(r.id.toLowerCase().startsWith(q)) out.add(r.id);
            }
            return out;
        }
        if(a.length==3 && "flag".equalsIgnoreCase(a[0])){
            String q=a[2].toLowerCase();
            for(Flag f:Flag.values()) if(f.key.startsWith(q)) out.add(f.key);
            return out;
        }
        return Collections.emptyList();
    }
}
