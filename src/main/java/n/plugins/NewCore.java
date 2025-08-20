package n.plugins;

import n.plugins.chat.NewChat;
import n.plugins.chat.NewColors;
import n.plugins.NewEdit.NewWorldEdit;
import n.plugins.NewEdit.NewWorldGuard;
import n.plugins.NewMultiverso.MVCommand;
import n.plugins.NewMultiverso.MVTPCommand;
import n.plugins.NewMultiverso.NewWorldsPlugin;
import n.plugins.warps.NewWarps;
import n.plugins.NewTrash.NewTrash;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewCore extends JavaPlugin {

    private NewChat chat;
    private NewColors colors;
    private NewWorldEdit worldEdit;
    private NewWorldGuard worldGuard;
    private NewWorldsPlugin mvCore;
    private MVCommand mv;
    private MVTPCommand mvtp;
    private NewWarps warps;
    private NewTrash trash;

    @Override
    public void onEnable() {

        // ===== CHAT =====
        chat = new NewChat(this);
        registerListener(chat);
        bind("g", chat);
        bind("tell", chat);
        bind("chat", chat);

        colors = new NewColors(chat);
        registerListener(colors);
        bind("cores", colors);

        // ===== WORLD EDIT / GUARD =====
        worldEdit = new NewWorldEdit(this);
        registerListener(worldEdit);

        // comandos básicos
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

        // seleção e manipulação
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

        // info / análises
        bind("size", worldEdit);
        bind("count", worldEdit);
        bind("distr", worldEdit);

        // formas geométricas
        bind("cyl", worldEdit);
        bind("sphere", worldEdit);
        bind("elipse", worldEdit);
        bind("ellipsoid", worldEdit);
        bind("up", worldEdit);

        worldGuard = new NewWorldGuard(worldEdit);
        worldGuard.init(this);

        // ===== MULTIVERSO =====
        mvCore = new NewWorldsPlugin(this);
        mvCore.carregarMundosExistentes();

        mv = new MVCommand(mvCore);
        bind("mv", mv);

        mvtp = new MVTPCommand(mvCore);
        bind("mvtp", mvtp);

        // ===== WARPS =====
        warps = new NewWarps(this);
        registerListener(warps);
        bind("setwarp", warps);
        bind("warp", warps);
        bind("removewarp", warps);

        // ===== NEW TRASH =====
        trash = new NewTrash(this);
        registerListener(trash);
        bind("newtrash", trash);

        getLogger().info("[NewCore] módulos carregados com sucesso.");
    }

    @Override
    public void onDisable() {
        if (worldEdit != null) worldEdit.shutdown();
        if (worldGuard != null) worldGuard.shutdown();
        if (trash != null) trash.shutdown();
    }

    // ===== HELPERS =====
    private void bind(String name, CommandExecutor exec) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("Comando não encontrado no plugin.yml: /" + name);
            return;
        }
        c.setExecutor(exec);
        if (exec instanceof TabCompleter) {
            c.setTabCompleter((TabCompleter) exec);
        }
    }

    private void registerListener(Listener l) {
        Bukkit.getPluginManager().registerEvents(l, this);
    }
}
