// MVCommand.java
package n.plugins.NewMultiverso;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.List;

public class MVCommand implements CommandExecutor {
    private final NewWorldsPlugin plugin;

    public MVCommand(NewWorldsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = (args.length > 0) ? args[0].toLowerCase() : "";

        switch (sub) {
            case "help":
                return sendHelp(sender);

            case "create":
                return handleCreate(sender, args);

            case "delete":
                return handleDelete(sender, args);

            case "setspawn":
                return handleSetSpawn(sender);

            case "reload":
                return handleReload(sender);

            case "tp":
                return handleTeleport(sender, args);

            case "load":
                return handleLoad(sender, args);

            case "unload":
                return handleUnload(sender, args);

            case "list":
                return handleList(sender);

            default:
                sender.sendMessage(color(plugin.getMessage("invalid-command")));
                return true;
        }
    }

    /* ================= HELP ================= */
    private boolean sendHelp(CommandSender sender) {
        if (!sender.hasPermission("newworlds.help")) {
            sender.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }
        sender.sendMessage(color(plugin.getMessage("prefix") + "§eComandos disponíveis:"));
        sender.sendMessage(color("§e/mv create <nome> <tipo>§7 - Cria um mundo (normal, plano, void, nether, end)"));
        sender.sendMessage(color("§e/mv delete <nome>§7 - Descarrega e APAGA a pasta do mundo"));
        sender.sendMessage(color("§e/mv setspawn§7 - Define o spawn do mundo atual"));
        sender.sendMessage(color("§e/mv reload§7 - Recarrega a configuração"));
        sender.sendMessage(color("§e/mv load <nome>§7 - Carrega um mundo EXISTENTE (não cria)"));
        sender.sendMessage(color("§e/mv unload <nome>§7 - Descarrega um mundo carregado"));
        sender.sendMessage(color("§e/mv list§7 - Lista os mundos carregados"));
        sender.sendMessage(color("§e/mv tp <nome>§7 - Teleporta para o spawn de um mundo"));
        sender.sendMessage(color("§e/mv help§7 - Mostra esta ajuda"));
        return true;
    }

    /* ================= CREATE ================= */
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("newworlds.create")) {
            sender.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(color(plugin.getMessage("invalid-usage").replace("{usage}", "/mv create <nome> <tipo>")));
            return true;
        }
        return criarMundo(sender, args[1], args[2]);
    }

    private boolean criarMundo(CommandSender sender, String worldName, String type) {
        World.Environment env = World.Environment.NORMAL;
        WorldType wtype = WorldType.NORMAL;
        ChunkGenerator gen = null;

        switch (type.toLowerCase()) {
            case "void": gen = new VoidGenerator(); break;
            case "end": env = World.Environment.THE_END; break;
            case "nether": env = World.Environment.NETHER; break;
            case "plano": wtype = WorldType.FLAT; break;
            case "normal": break;
            default:
                sender.sendMessage(color(plugin.getMessage("invalid-environment").replace("{env}", type)));
                return true;
        }

        WorldCreator wc = new WorldCreator(worldName).environment(env).type(wtype);
        if (gen != null) wc.generator(gen);

        World world = Bukkit.createWorld(wc);
        if (world != null) {
            new GameRuleUtil(plugin.getCore()).applyGamerules(world);
            sender.sendMessage(color(plugin.getMessage("world-created").replace("{world}", worldName)));
        } else {
            sender.sendMessage(color(plugin.getMessage("creation-error").replace("{world}", worldName)));
        }
        return true;
    }

    /* ================= DELETE ================= */
    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("newworlds.delete")) {
            sender.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color(plugin.getMessage("invalid-usage").replace("{usage}", "/mv delete <nome>")));
            return true;
        }
        return deletarMundo(sender, args[1]);
    }

    private boolean deletarMundo(CommandSender sender, String worldName) {
        // impedi deletar o primeiro mundo carregado (geralmente o principal)
        World mainWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (mainWorld != null && mainWorld.getName().equalsIgnoreCase(worldName)) {
            sender.sendMessage(color(plugin.getMessage("cannot-delete-main")));
            return true;
        }

        World w = Bukkit.getWorld(worldName);
        if (w != null) {
            // mover jogadores para o mundo principal antes de descarregar
            for (Player p : w.getPlayers()) {
                if (mainWorld != null) p.teleport(mainWorld.getSpawnLocation());
            }
            if (!Bukkit.unloadWorld(w, false)) {
                sender.sendMessage(color(plugin.getMessage("unknown-error")));
                return true;
            }
        }

        File container = Bukkit.getWorldContainer();
        File worldFolder = new File(container, worldName);
        if (!worldFolder.exists()) {
            sender.sendMessage(color(plugin.getMessage("world-not-found").replace("{world}", worldName)));
            return true;
        }

        boolean deleted = deleteFolder(worldFolder);
        if (deleted) {
            sender.sendMessage(color(plugin.getMessage("world-deleted").replace("{world}", worldName)));
        } else {
            sender.sendMessage(color(plugin.getMessage("deletion-error").replace("{world}", worldName)));
        }
        return true;
    }

    private boolean deleteFolder(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (!deleteFolder(c)) return false;
                }
            }
        }
        return file.delete();
    }

    /* ================= SETSPAWN ================= */
    private boolean handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(plugin.getMessage("only-players")));
            return true;
        }
        if (!sender.hasPermission("newworlds.setspawn")) {
            sender.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }
        Player player = (Player) sender;
        Location loc = player.getLocation();
        player.getWorld().setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        sender.sendMessage(color(plugin.getMessage("spawn-set")));
        return true;
    }

    /* ================= RELOAD ================= */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("newworlds.reload")) {
            sender.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }
        plugin.reloadSettings();
        sender.sendMessage(color(plugin.getMessage("reloaded")));
        return true;
    }

    /* ================= TELEPORT ================= */
    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color(plugin.getMessage("only-players")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color(plugin.getMessage("invalid-usage").replace("{usage}", "/mv tp <mundo>")));
            return true;
        }
        return teleportar((Player) sender, args[1]);
    }

    private boolean teleportar(Player player, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            player.sendMessage(color(plugin.getMessage("world-not-found").replace("{world}", worldName)));
            return true;
        }

        boolean hasPerm = player.hasPermission("mv.tp." + worldName.toLowerCase());
        boolean bypass = false;

        if (player.hasPermission("mv.Bypass")) {
            if (player.hasPermission("grupo.vip") && plugin.getSettings().getBoolean("Bypass-vip", true)) {
                bypass = true;
            } else if (player.hasPermission("grupo.membro") && plugin.getSettings().getBoolean("Bypass-membro", false)) {
                bypass = true;
            }
        }

        if (!hasPerm && !bypass) {
            player.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }

        player.teleport(w.getSpawnLocation());
        player.sendMessage(color(plugin.getMessage("teleport-success").replace("{world}", worldName)));
        return true;
    }

    /* ================= LOAD ================= */
    private boolean handleLoad(CommandSender sender, String[] args) {
        if (!sender.hasPermission("newworlds.load")) {
            sender.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color(plugin.getMessage("invalid-usage").replace("{usage}", "/mv load <nome>")));
            return true;
        }
        return carregarMundo(sender, args[1]);
    }

    private boolean carregarMundo(CommandSender sender, String nomeMundo) {
        if (Bukkit.getWorld(nomeMundo) != null) {
            sender.sendMessage(color(plugin.getMessage("world-already-exists").replace("{world}", nomeMundo)));
            return true;
        }

        // SÓ carregar se a pasta existir e tiver level.dat
        File container = Bukkit.getWorldContainer();
        File worldFolder = new File(container, nomeMundo);
        File levelDat = new File(worldFolder, "level.dat");
        if (!worldFolder.exists() || !levelDat.exists()) {
            sender.sendMessage(color(plugin.getMessage("world-not-found").replace("{world}", nomeMundo)));
            return true;
        }

        World world = Bukkit.createWorld(new WorldCreator(nomeMundo));
        if (world == null) {
            sender.sendMessage(color(plugin.getMessage("creation-error").replace("{world}", nomeMundo)));
            return true;
        }

        new GameRuleUtil(plugin.getCore()).applyGamerules(world);
        sender.sendMessage(color(plugin.getMessage("world-created").replace("{world}", nomeMundo)));
        return true;
    }

    /* ================= UNLOAD ================= */
    private boolean handleUnload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("newworlds.unload")) {
            sender.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color(plugin.getMessage("invalid-usage").replace("{usage}", "/mv unload <nome>")));
            return true;
        }
        return descarregarMundo(sender, args[1]);
    }

    private boolean descarregarMundo(CommandSender sender, String nomeMundo) {
        World w = Bukkit.getWorld(nomeMundo);
        if (w == null) {
            sender.sendMessage(color(plugin.getMessage("world-not-loaded").replace("{world}", nomeMundo)));
            return true;
        }
        if (!w.getPlayers().isEmpty()) {
            sender.sendMessage(color(plugin.getMessage("players-in-world")));
            return true;
        }

        if (Bukkit.unloadWorld(w, false)) {
            sender.sendMessage(color(plugin.getMessage("world-unloaded").replace("{world}", nomeMundo)));
        } else {
            sender.sendMessage(color(plugin.getMessage("unknown-error")));
        }
        return true;
    }

    /* ================= LIST ================= */
    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("newworlds.list")) {
            sender.sendMessage(color(plugin.getMessage("no-permission")));
            return true;
        }

        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            sender.sendMessage(color(plugin.getMessage("no-worlds-loaded")));
            return true;
        }

        sender.sendMessage(color(plugin.getMessage("prefix") + "§6Mundos carregados:"));
        for (World w : worlds) {
            sender.sendMessage(color("§e- " + w.getName()));
        }
        return true;
    }

    /* ================= UTILS ================= */
    private String color(String msg) {
        if (msg == null) return "";
        return msg.replace("&", "§");
    }
}
