// File: src/main/java/n/plugins/NewEssentials/Tpacmd.java
package n.plugins.NewEssentials;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Compat: Bukkit/Spigot 1.7.10 — Java 8
 * Config: NewEssentials.yml (seção tpa.*) — SOMENTE leitura do YML (copiado do resources)
 */
public final class Tpacmd implements CommandExecutor, TabCompleter {

    private final JavaPlugin core;

    private File cfgFile;
    private YamlConfiguration cfg;

    private boolean ENABLED = true;
    private boolean REQUIRE_PERM_TPA = true;
    private boolean REQUIRE_PERM_TPAHERE = true;
    private boolean REQUIRE_PERM_ACCEPT = true;
    private boolean REQUIRE_PERM_DENY = true;

    private int TIMEOUT_SECONDS = 60;
    private int COOLDOWN_SECONDS = 5;
    private boolean PLAY_SOUND = true;
    private String SOUND_REQUEST = "CLICK";
    private String SOUND_ACCEPT  = "LEVEL_UP";
    private String SOUND_DENY    = "VILLAGER_NO";

    private boolean ALLOW_CROSS_WORLD = true;
    private final Set<String> BLOCKED_WORLDS_SEND = new HashSet<String>();
    private final Set<String> BLOCKED_WORLDS_RECV = new HashSet<String>();

    private String MSG_ONLY_PLAYER     = "&cApenas jogadores podem usar este comando.";
    private String MSG_DISABLED        = "&cSistema de TPA está desativado.";
    private String MSG_NO_PERM         = "&cVocê não tem permissão para isso.";
    private String MSG_PLAYER_OFFLINE  = "&cJogador offline ou não encontrado.";
    private String MSG_SELF            = "&cVocê não pode enviar pedido para si mesmo.";
    private String MSG_WORLD_BLOCKED_S = "&cVocê não pode enviar TPA a partir deste mundo.";
    private String MSG_WORLD_BLOCKED_R = "&cEste jogador não pode receber TPA no mundo atual.";
    private String MSG_CROSS_WORLD     = "&cTPA entre mundos diferentes está desativado.";
    private String MSG_COOLDOWN        = "&eAguarde &f%sec%s &epara enviar outro pedido.";
    private String MSG_REQUEST_SENT    = "&aPedido de teleporte enviado para &f%target%&a. Use &f/tpcancel&7 (se implementado) &aou aguarde.";
    private String MSG_REQUEST_RCVD    = "&e%from% &7solicitou teleporte %mode% &epara você. &7Use &a/tpaccept &7ou &c/tpdeny &7(em %sec%s).";
    private String MSG_EXPIRED_SENDER  = "&eSeu pedido de TPA para &f%target% &eexpirou.";
    private String MSG_EXPIRED_TARGET  = "&eO pedido de TPA de &f%from% &eexpirou.";
    private String MSG_NO_PENDING      = "&eVocê não tem pedidos pendentes.";
    private String MSG_ACCEPTED_S      = "&a%target% aceitou seu pedido. Teleportando...";
    private String MSG_ACCEPTED_T      = "&aVocê aceitou o pedido de &f%from%&a.";
    private String MSG_DENIED_S        = "&c%target% recusou seu pedido de TPA.";
    private String MSG_DENIED_T        = "&eVocê recusou o pedido de &f%from%&e.";
    private String MSG_TP_DONE         = "&aTeleporte concluído.";

    private static final class Pending {
        final UUID from, to; final boolean here; final long createdAt, expireAt;
        Pending(UUID f, UUID t, boolean h, long now, long ttl){ this.from=f; this.to=t; this.here=h; this.createdAt=now; this.expireAt=now+ttl; }
    }

    private final Map<UUID, Deque<Pending>> pendingByTarget = new HashMap<UUID, Deque<Pending>>();
    private final Map<UUID, Long> lastSend = new HashMap<UUID, Long>();
    private int sweeperTaskId = -1;

    public Tpacmd(JavaPlugin core){
        this.core = core;
        loadConfig();   // lê do YML
        startSweeper();
    }

    public void shutdown(){
        stopSweeper();
        pendingByTarget.clear();
        lastSend.clear();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        if(!(s instanceof Player)){ s.sendMessage(color(MSG_ONLY_PLAYER)); return true; }
        Player p = (Player) s;

        if(!ENABLED){ p.sendMessage(color(MSG_DISABLED)); return true; }

        if("tpa".equals(name) || "tpahere".equals(name)){
            boolean isHere = "tpahere".equals(name);

            if(isHere ? (REQUIRE_PERM_TPAHERE && !p.hasPermission("new.tpahere"))
                    : (REQUIRE_PERM_TPA && !p.hasPermission("new.tpa"))){
                p.sendMessage(color(MSG_NO_PERM)); return true;
            }

            if(args.length<1){ p.sendMessage(color("&eUso: /"+name+" <jogador>")); return true; }

            Player target = Bukkit.getPlayerExact(args[0]);
            if(target==null || !target.isOnline()){ p.sendMessage(color(MSG_PLAYER_OFFLINE)); return true; }
            if(target.getUniqueId().equals(p.getUniqueId())){ p.sendMessage(color(MSG_SELF)); return true; }

            if(BLOCKED_WORLDS_SEND.contains(p.getWorld().getName())){ p.sendMessage(color(MSG_WORLD_BLOCKED_S)); return true; }
            if(BLOCKED_WORLDS_RECV.contains(target.getWorld().getName())){ p.sendMessage(color(MSG_WORLD_BLOCKED_R)); return true; }
            if(!ALLOW_CROSS_WORLD && !p.getWorld().equals(target.getWorld())){ p.sendMessage(color(MSG_CROSS_WORLD)); return true; }

            if(COOLDOWN_SECONDS>0){
                long now=System.currentTimeMillis();
                Long last = lastSend.get(p.getUniqueId());
                long wait = (last==null)?0L : (COOLDOWN_SECONDS*1000L - (now-last));
                if(wait>0){
                    long secs=(wait+999)/1000;
                    p.sendMessage(color(MSG_COOLDOWN.replace("%sec%", String.valueOf(secs))));
                    return true;
                }
                lastSend.put(p.getUniqueId(), now);
            }

            long now=System.currentTimeMillis();
            Pending pend = new Pending(p.getUniqueId(), target.getUniqueId(), isHere, now, TIMEOUT_SECONDS*1000L);
            Deque<Pending> dq = pendingByTarget.get(target.getUniqueId());
            if(dq==null){ dq=new ArrayDeque<Pending>(); pendingByTarget.put(target.getUniqueId(), dq); }
            dq.addLast(pend);

            p.sendMessage(color(MSG_REQUEST_SENT.replace("%target%", target.getName())));
            String mode = isHere ? "aqui" : "até você";
            String msgR = MSG_REQUEST_RCVD.replace("%from%", p.getName())
                    .replace("%mode%", mode)
                    .replace("%sec%", String.valueOf(TIMEOUT_SECONDS));
            target.sendMessage(color(msgR));

            if(PLAY_SOUND){ playSoundSafe(target, SOUND_REQUEST, 1f, 1.2f); }
            return true;
        }

        if("tpaccept".equals(name)){
            if(REQUIRE_PERM_ACCEPT && !p.hasPermission("new.tpaccept")){ p.sendMessage(color(MSG_NO_PERM)); return true; }
            Pending pend = popLatestValid(p.getUniqueId());
            if(pend==null){ p.sendMessage(color(MSG_NO_PENDING)); return true; }

            Player from = Bukkit.getPlayer(pend.from);
            Player to   = Bukkit.getPlayer(pend.to);
            if(from==null || !from.isOnline() || to==null || !to.isOnline()){
                p.sendMessage(color(MSG_NO_PENDING)); return true;
            }

            if(pend.here){ to.teleport(from.getLocation()); } else { from.teleport(to.getLocation()); }

            from.sendMessage(color(MSG_ACCEPTED_S.replace("%target%", to.getName())));
            p.sendMessage(color(MSG_ACCEPTED_T.replace("%from%", from.getName())));
            if(PLAY_SOUND){ playSoundSafe(from, SOUND_ACCEPT, 1f, 1f); playSoundSafe(p, SOUND_ACCEPT, 1f, 1f); }
            from.sendMessage(color(MSG_TP_DONE));
            p.sendMessage(color(MSG_TP_DONE));
            return true;
        }

        if("tpdeny".equals(name)){
            if(REQUIRE_PERM_DENY && !p.hasPermission("new.tpdeny")){ p.sendMessage(color(MSG_NO_PERM)); return true; }
            Pending pend = popLatestValid(p.getUniqueId());
            if(pend==null){ p.sendMessage(color(MSG_NO_PENDING)); return true; }

            Player from = Bukkit.getPlayer(pend.from);
            if(from!=null && from.isOnline()){
                from.sendMessage(color(MSG_DENIED_S.replace("%target%", p.getName())));
                if(PLAY_SOUND) playSoundSafe(from, SOUND_DENY, 1f, 1f);
            }
            p.sendMessage(color(MSG_DENIED_T.replace("%from%", (from!=null?from.getName():"?"))));
            if(PLAY_SOUND) playSoundSafe(p, SOUND_DENY, 1f, 1f);
            return true;
        }

        return false;
    }

    private Pending popLatestValid(UUID target){
        Deque<Pending> dq = pendingByTarget.get(target);
        if(dq==null || dq.isEmpty()) return null;
        long now = System.currentTimeMillis();
        while(!dq.isEmpty()){
            Pending peek = dq.peekLast();
            if(peek.expireAt < now){ dq.removeLast(); notifyExpire(peek); } else break;
        }
        if(dq.isEmpty()) return null;
        return dq.removeLast();
    }

    private void notifyExpire(Pending pend){
        Player from = Bukkit.getPlayer(pend.from);
        Player to   = Bukkit.getPlayer(pend.to);
        if(from!=null && from.isOnline()){ from.sendMessage(color(MSG_EXPIRED_SENDER.replace("%target%", (to!=null?to.getName():"?")))); }
        if(to!=null && to.isOnline()){ to.sendMessage(color(MSG_EXPIRED_TARGET.replace("%from%", (from!=null?from.getName():"?")))); }
    }

    private void startSweeper(){
        stopSweeper();
        sweeperTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(core, new Runnable(){
            @Override public void run(){
                long now = System.currentTimeMillis();
                for(Iterator<Map.Entry<UUID,Deque<Pending>>> it = pendingByTarget.entrySet().iterator(); it.hasNext(); ){
                    Map.Entry<UUID,Deque<Pending>> e = it.next();
                    Deque<Pending> dq = e.getValue();
                    if(dq==null || dq.isEmpty()){ it.remove(); continue; }
                    while(!dq.isEmpty()){
                        Pending p = dq.peekFirst();
                        if(p.expireAt < now){ dq.removeFirst(); notifyExpire(p); } else break;
                    }
                    if(dq.isEmpty()) it.remove();
                }
            }
        }, 20L, 20L);
    }

    private void stopSweeper(){
        if(sweeperTaskId!=-1){ Bukkit.getScheduler().cancelTask(sweeperTaskId); sweeperTaskId=-1; }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        String name=c.getName().toLowerCase();
        if(!(s instanceof Player)) return Collections.emptyList();
        if(name.equals("tpa") || name.equals("tpahere")){
            if(a.length==1){
                String q=a[0].toLowerCase();
                List<String> out=new ArrayList<String>();
                for(Player p: Bukkit.getOnlinePlayers()){
                    if(p.getName().toLowerCase().startsWith(q)) out.add(p.getName());
                }
                Collections.sort(out);
                return out.size()>50?out.subList(0,50):out;
            }
        }
        return Collections.emptyList();
    }

    // ===== Config (somente leitura do YML copiado do resources) =====
    private void loadConfig(){
        if(!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
        cfgFile = new File(core.getDataFolder(), "NewEssentials.yml");
        if(!cfgFile.exists()){
            try { core.saveResource("NewEssentials.yml", false); } catch (IllegalArgumentException ignored) {}
        }
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        ENABLED            = getBool("tpa.enabled", ENABLED);
        REQUIRE_PERM_TPA   = getBool("tpa.require-permission.tpa", REQUIRE_PERM_TPA);
        REQUIRE_PERM_TPAHERE = getBool("tpa.require-permission.tpahere", REQUIRE_PERM_TPAHERE);
        REQUIRE_PERM_ACCEPT= getBool("tpa.require-permission.tpaccept", REQUIRE_PERM_ACCEPT);
        REQUIRE_PERM_DENY  = getBool("tpa.require-permission.tpdeny", REQUIRE_PERM_DENY);

        TIMEOUT_SECONDS    = getInt("tpa.timeout-seconds", TIMEOUT_SECONDS);
        COOLDOWN_SECONDS   = getInt("tpa.cooldown-seconds", COOLDOWN_SECONDS);
        PLAY_SOUND         = getBool("tpa.play-sound", PLAY_SOUND);
        SOUND_REQUEST      = getStr("tpa.sound.request", SOUND_REQUEST);
        SOUND_ACCEPT       = getStr("tpa.sound.accept",  SOUND_ACCEPT);
        SOUND_DENY         = getStr("tpa.sound.deny",    SOUND_DENY);

        ALLOW_CROSS_WORLD  = getBool("tpa.allow-cross-world", ALLOW_CROSS_WORLD);

        BLOCKED_WORLDS_SEND.clear();
        BLOCKED_WORLDS_RECV.clear();
        List<String> ws = cfg.getStringList("tpa.blocked-worlds.send");
        if(ws!=null) for(String w: ws) if(w!=null) BLOCKED_WORLDS_SEND.add(w);
        ws = cfg.getStringList("tpa.blocked-worlds.recv");
        if(ws!=null) for(String w: ws) if(w!=null) BLOCKED_WORLDS_RECV.add(w);

        MSG_ONLY_PLAYER     = getStr("tpa.messages.only-player",     MSG_ONLY_PLAYER);
        MSG_DISABLED        = getStr("tpa.messages.disabled",        MSG_DISABLED);
        MSG_NO_PERM         = getStr("tpa.messages.no-perm",         MSG_NO_PERM);
        MSG_PLAYER_OFFLINE  = getStr("tpa.messages.player-offline",  MSG_PLAYER_OFFLINE);
        MSG_SELF            = getStr("tpa.messages.self",            MSG_SELF);
        MSG_WORLD_BLOCKED_S = getStr("tpa.messages.world-blocked-send", MSG_WORLD_BLOCKED_S);
        MSG_WORLD_BLOCKED_R = getStr("tpa.messages.world-blocked-recv", MSG_WORLD_BLOCKED_R);
        MSG_CROSS_WORLD     = getStr("tpa.messages.cross-world",     MSG_CROSS_WORLD);
        MSG_COOLDOWN        = getStr("tpa.messages.cooldown",        MSG_COOLDOWN);
        MSG_REQUEST_SENT    = getStr("tpa.messages.request-sent",    MSG_REQUEST_SENT);
        MSG_REQUEST_RCVD    = getStr("tpa.messages.request-received",MSG_REQUEST_RCVD);
        MSG_EXPIRED_SENDER  = getStr("tpa.messages.expired-sender",  MSG_EXPIRED_SENDER);
        MSG_EXPIRED_TARGET  = getStr("tpa.messages.expired-target",  MSG_EXPIRED_TARGET);
        MSG_NO_PENDING      = getStr("tpa.messages.no-pending",      MSG_NO_PENDING);
        MSG_ACCEPTED_S      = getStr("tpa.messages.accepted-sender", MSG_ACCEPTED_S);
        MSG_ACCEPTED_T      = getStr("tpa.messages.accepted-target", MSG_ACCEPTED_T);
        MSG_DENIED_S        = getStr("tpa.messages.denied-sender",   MSG_DENIED_S);
        MSG_DENIED_T        = getStr("tpa.messages.denied-target",   MSG_DENIED_T);
        MSG_TP_DONE         = getStr("tpa.messages.tp-done",         MSG_TP_DONE);
    }

    private int getInt(String path, int def){ return cfg.getInt(path, def); }
    private boolean getBool(String path, boolean def){ return cfg.getBoolean(path, def); }
    private String getStr(String path, String def){ String v = cfg.getString(path); return v!=null? v : def; }

    private static String color(String s){ return ChatColor.translateAlternateColorCodes('&', s); }
    private void playSoundSafe(Player p, String name, float vol, float pitch){
        try{ Sound s = Sound.valueOf(name.toUpperCase()); p.playSound(p.getLocation(), s, vol, pitch); }catch(Throwable ignore){}
    }
}
