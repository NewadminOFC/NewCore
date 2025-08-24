// File: src/main/java/n/plugins/NewCore.java
package n.plugins;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewCore extends JavaPlugin {

    private NewCommandManager commandManager;

    @Override
    public void onEnable() {
        mostrartitle();

        // inicializa o gerenciador de comandos/módulos
        commandManager = new NewCommandManager(this);
        commandManager.registerAll();

        getLogger().info("[NewCore] módulos carregados com sucesso.");
    }

    @Override
    public void onDisable() {
        // chama shutdown dos módulos que precisam encerrar
        if (commandManager != null) {
            if (commandManager.getNewLogin() != null) {
                commandManager.getNewLogin().shutdown();
            }
            if (commandManager.getWorldEdit() != null) {
                commandManager.getWorldEdit().shutdown();
            }
            if (commandManager.getWorldGuard() != null) {
                commandManager.getWorldGuard().shutdown();
            }
            if (commandManager.getTpa() != null) {
                commandManager.getTpa().shutdown();
            }
            if (commandManager.getSpawn() != null) {
                commandManager.getSpawn().shutdown();
            }
            if (commandManager.getEconomy() != null) {
                commandManager.getEconomy().onDisable();
            }
        }
    }

    public void mostrartitle() {
        Bukkit.getConsoleSender().sendMessage("§f  _   _                 _____ ____  _____  ______ ");
        Bukkit.getConsoleSender().sendMessage("§f | \\ | |              / ____/ __ \\|  __ \\|  ____|");
        Bukkit.getConsoleSender().sendMessage("§f |  \\| | _____      _| |   | |  | | |__) | |__   ");
        Bukkit.getConsoleSender().sendMessage("§f | . ` |/ _ \\ \\ /\\ / / |   | |  | |  _  /|  __|  ");
        Bukkit.getConsoleSender().sendMessage("§f | |\\  |  __/\\ V  V /| |___| |__| | | \\ \\| |____ ");
        Bukkit.getConsoleSender().sendMessage("§f |_| \\_|\\___| \\_/\\_/  \\_____\\____/|_|  \\_\\______|");
        Bukkit.getConsoleSender().sendMessage("§a| §aPlugin carregado com sucesso!");
        Bukkit.getConsoleSender().sendMessage("§a| §aDesenvolvido Com ODIO por NewAdminOFC.");
    }

    // getter para acessar o gerenciador de comandos se precisar
    public NewCommandManager getCommandManager() {
        return commandManager;
    }
}
