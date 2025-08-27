// File: src/main/java/n/plugins/NewCommandManager.java
package n.plugins;

import n.plugins.NewLogin.ResetSenhaCommand;
import n.plugins.chat.NewChat;
import n.plugins.chat.NewColors;
import n.plugins.NewEdit.NewWorldEdit;
import n.plugins.NewEdit.NewWorldGuard;
import n.plugins.NewMultiverso.MVCommand;
import n.plugins.NewMultiverso.MVTPCommand;
import n.plugins.NewMultiverso.NewWorldsPlugin;
import n.plugins.warps.NewWarps;
import n.plugins.NewTrash.NewTrash;
import n.plugins.NewEconomy.NewEconomy;
import n.plugins.NewEconomy.NewEconomyAPI;
import n.plugins.NewEssentials.enderchest;
import n.plugins.NewEssentials.Spawncmd;
import n.plugins.NewEssentials.Tpacmd;
import n.plugins.NewLogin.NewLogin;
import n.plugins.NewLogin.LoginCommand;
import n.plugins.NewLogin.NewLoginCommand;
import n.plugins.NewLogin.CommandHideListener; // oculta /login /register /resetsenha do console
import n.plugins.NewPlots.NewPlots;
import n.plugins.NewGroups.NewGroup;
import n.plugins.NewOrbs.NewOrbs;
import n.plugins.NewEssentials.Rename;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.event.Listener;

public class NewCommandManager {

    private final NewCore plugin;

    private NewChat chat;
    private NewColors colors;
    private NewWorldEdit worldEdit;
    private NewWorldGuard worldGuard;
    private NewWorldsPlugin mvCore;
    private MVCommand mv;
    private MVTPCommand mvtp;
    private NewWarps warps;
    private NewTrash trash;
    private enderchest ec;
    private Spawncmd spawn;
    private Tpacmd tpa;
    private NewEconomy economy;
    private NewLogin newLogin;
    private NewPlots plots;
    private NewGroup groups;
    private NewOrbs newOrbs;
    private Rename rename;

    public NewCommandManager(NewCore plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        // ===== Chat =====
        chat = new NewChat(plugin);
        registerListener(chat);
        bind("g", chat);
        bind("tell", chat);
        bind("chat", chat);

        colors = new NewColors(chat);
        registerListener(colors);
        bind("cores", colors);

        // ===== WorldEdit =====
        worldEdit = new NewWorldEdit(plugin);
        registerListener(worldEdit);
        bind("wand", worldEdit);
        bind("pos1", worldEdit);
        bind("pos2", worldEdit);
        bind("set", worldEdit);
        bind("replace", worldEdit);
        bind("undo", worldEdit);
        bind("redo", worldEdit);
        bind("copy", worldEdit);
        bind("paste", worldEdit);
        bind("cut", worldEdit);
        bind("pos0", worldEdit);
        bind("desel", worldEdit);
        bind("hpos1", worldEdit);
        bind("hpos2", worldEdit);
        bind("expand", worldEdit);
        bind("contract", worldEdit);
        bind("stack", worldEdit);
        bind("move", worldEdit);
        bind("rotate", worldEdit);
        bind("flip", worldEdit);
        bind("size", worldEdit);
        bind("count", worldEdit);
        bind("distr", worldEdit);
        bind("cyl", worldEdit);
        bind("sphere", worldEdit);
        bind("elipse", worldEdit);
        bind("ellipsoid", worldEdit);
        bind("up", worldEdit);

        // ===== WorldGuard =====
        worldGuard = new NewWorldGuard(worldEdit);
        worldGuard.init(plugin);
        bind("rg", worldGuard);
        bind("flags", worldGuard);

        // ===== Multiverso =====
        mvCore = new NewWorldsPlugin(plugin);
        mvCore.carregarMundosExistentes();

        mv = new MVCommand(mvCore);
        bind("mv", mv);

        mvtp = new MVTPCommand(mvCore);
        bind("mvtp", mvtp);

        // ===== Warps =====
        warps = new NewWarps(plugin);
        registerListener(warps);
        bind("setwarp", warps);
        bind("warp", warps);
        bind("removewarp", warps);

        // ===== NewTrash =====
        trash = new NewTrash(plugin);
        registerListener(trash);
        bind("newtrash", trash);

        // ===== NewEssentials =====
        ec = new enderchest();
        bind("ec", ec);

        spawn = new Spawncmd(plugin);
        bind("spawn", spawn);
        bind("setspawn", spawn);

        tpa = new Tpacmd(plugin);
        bind("tpa", tpa);
        bind("tpahere", tpa);
        bind("tpaccept", tpa);
        bind("tpdeny", tpa);

        // ===== NewEconomy =====
        economy = new NewEconomy();
        economy.onEnable(plugin);
        NewEconomyAPI.register(economy);
        bind("money", economy);

        // ===== NewLogin =====
        newLogin = new NewLogin(plugin);
        newLogin.init();

        // >>> PASSA O LoginConfig (newLogin.getConfig()) <<<
        bind("register",   new LoginCommand(newLogin, newLogin.getConfig()));
        bind("login",      new LoginCommand(newLogin, newLogin.getConfig()));
        bind("newlogin",   new NewLoginCommand(newLogin));
        bind("resetsenha", new ResetSenhaCommand(newLogin, newLogin.getConfig()));

        // Oculta comandos sensíveis do console conforme ConfigLogin.yml
        registerListener(new CommandHideListener(newLogin.getConfig()));

        // ===== NewPlots =====
        plots = new NewPlots(plugin);
        registerListener(plots);
        bind("plot", plots);

        // ===== NewGroups =====
        groups = new NewGroup(plugin);
        groups.init();
        registerListener(groups);
        bind("newgroups", groups);

        // ===== NewOrbs =====
        this.newOrbs = new NewOrbs(plugin);
        this.newOrbs.register();

        // ===== Rename / Lore =====
        rename = new Rename(plugin);
        bind("rename", rename);
        bind("addlore", rename);
        bind("removelore", rename);

        plugin.getLogger().info("[NewCommandManager] Todos comandos e listeners carregados.");
    }

    private void bind(String name, CommandExecutor exec) {
        PluginCommand c = plugin.getCommand(name);
        if (c == null) {
            plugin.getLogger().warning("Comando não encontrado no plugin.yml: /" + name);
            return;
        }
        c.setExecutor(exec);
        if (exec instanceof TabCompleter) {
            c.setTabCompleter((TabCompleter) exec);
        }
    }

    private void registerListener(Listener l) {
        Bukkit.getPluginManager().registerEvents(l, plugin);
    }

    // ==== Getters ====
    public NewEconomy getEconomy() { return economy; }
    public NewWorldEdit getWorldEdit() { return worldEdit; }
    public NewWorldGuard getWorldGuard() { return worldGuard; }
    public Tpacmd getTpa() { return tpa; }
    public Spawncmd getSpawn() { return spawn; }
    public NewLogin getNewLogin() { return newLogin; }
    public NewPlots getPlots() { return plots; }
    public NewGroup getGroups() { return groups; }
    public NewOrbs getNewOrbs() { return newOrbs; }
}
