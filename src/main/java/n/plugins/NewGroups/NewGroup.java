// File: src/main/java/n/plugins/NewGroups/NewGroup.java
package n.plugins.NewGroups;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public final class NewGroup implements Listener, CommandExecutor, TabCompleter {

    private static final String YAML_NAME = "NewGroups.yml";
    private static final String NG_API_BASE = "https://perms.newplugins.shop";
    private static final String NG_API_TOKEN = "";

    private final JavaPlugin plugin;

    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Integer> editorTasks = new HashMap<>();

    private File groupsFile;
    private YamlConfiguration groupsConfig;

    private Connection conn;

    private volatile String serverId;

    private static final class Group {
        final String name;
        final boolean isDefault;
        final String prefix;
        final LinkedHashSet<String> permissions;
        final ArrayList<String> parents;

        Group(String name, boolean isDefault, String prefix, Collection<String> permissions, Collection<String> parents) {
            this.name = name == null ? "default" : name.toLowerCase(Locale.ENGLISH);
            this.isDefault = isDefault;
            this.prefix = prefix == null ? "" : prefix;
            this.permissions = new LinkedHashSet<>(permissions == null ? Collections.emptyList() : permissions);
            this.parents = new ArrayList<>(parents == null ? Collections.emptyList() : parents);
        }
    }

    public NewGroup(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            setupYamlFile();
            setupSQLite();
            ensureSchema();

            boolean dbEmpty = isDatabaseEmpty();
            if (dbEmpty) {
                loadGroupsFromYamlToMemory();
                if (groups.isEmpty()) seedDefaultInMemory();
                writeMemoryToDatabase(true);
                writeMemoryToYaml(true);
            } else {
                loadGroupsFromDatabaseToMemory();
                writeMemoryToYaml(true);
            }

            Bukkit.getPluginManager().registerEvents(this, plugin);
            applyAllOnline();

            serverId = "srv-" + Bukkit.getPort();
            startBridgeTasks();

            plugin.getLogger().info("[NewGroups] iniciado. Grupos: " + listGroupNames());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao iniciar módulo NewGroups", e);
        }
    }

    public void shutdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            clearAttachment(onlinePlayer.getUniqueId());
        }
        groups.clear();
        attachments.clear();
        editorTasks.clear();
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        plugin.getLogger().info("[NewGroups] finalizado.");
    }

    private void setupYamlFile() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        groupsFile = new File(plugin.getDataFolder(), YAML_NAME);
        if (!groupsFile.exists()) {
            try {
                groupsFile.createNewFile();
                YamlConfiguration y = new YamlConfiguration();
                y.set("groups.default.default", true);
                y.set("groups.default.prefix", "&7");
                y.set("groups.default.parents", Collections.emptyList());
                y.set("groups.default.permissions", Arrays.asList("bukkit.command.help", "bukkit.command.list"));
                y.set("groups.admin.default", false);
                y.set("groups.admin.prefix", "&c[Admin] ");
                y.set("groups.admin.parents", Collections.singletonList("default"));
                y.set("groups.admin.permissions", Collections.singletonList("*"));
                y.createSection("players");
                y.save(groupsFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Não foi possível criar " + groupsFile.getName(), e);
            }
        }
        groupsConfig = new YamlConfiguration();
        try {
            groupsConfig.load(groupsFile);
            if (!groupsConfig.isConfigurationSection("players")) {
                groupsConfig.createSection("players");
                saveYaml();
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao carregar " + groupsFile.getName(), e);
            groupsConfig = new YamlConfiguration();
            groupsConfig.createSection("groups");
            groupsConfig.createSection("players");
        }
    }

    private boolean saveYaml() {
        try {
            groupsConfig.save(groupsFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro salvando " + groupsFile.getName(), e);
            return false;
        }
    }

    private void loadGroupsFromYamlToMemory() {
        groups.clear();
        if (!groupsConfig.isConfigurationSection("groups")) return;
        Set<String> keys = groupsConfig.getConfigurationSection("groups").getKeys(false);
        for (String key : keys) {
            String base = "groups." + key + ".";
            boolean def = groupsConfig.getBoolean(base + "default", false);
            String prefix = groupsConfig.getString(base + "prefix", "");
            List<String> parents = groupsConfig.getStringList(base + "parents");
            List<String> perms = groupsConfig.getStringList(base + "permissions");
            groups.put(key.toLowerCase(Locale.ENGLISH), new Group(key, def, prefix, perms, parents));
        }
        if (!hasAnyDefault()) {
            if (groups.containsKey("default")) {
                Group g = groups.get("default");
                groups.put("default", new Group(g.name, true, g.prefix, g.permissions, g.parents));
            } else {
                seedDefaultInMemory();
            }
        }
    }

    private void writeMemoryToYaml(boolean overwrite) {
        if (overwrite) groupsConfig = new YamlConfiguration();
        groupsConfig.createSection("groups");
        for (Group g : groups.values()) {
            String path = "groups." + g.name + ".";
            groupsConfig.set(path + "default", g.isDefault);
            groupsConfig.set(path + "prefix", g.prefix);
            groupsConfig.set(path + "parents", new ArrayList<>(g.parents));
            groupsConfig.set(path + "permissions", new ArrayList<>(g.permissions));
        }
        groupsConfig.createSection("players");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, group_name FROM players")) {
            while (rs.next()) {
                groupsConfig.set("players." + rs.getString("uuid") + ".group", rs.getString("group_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Não foi possível escrever jogadores para YAML", e);
        }
        saveYaml();
    }

    private boolean hasAnyDefault() {
        for (Group g : groups.values()) if (g.isDefault) return true;
        return false;
    }

    private void seedDefaultInMemory() {
        Group def = new Group("default", true, "&7", Arrays.asList("bukkit.command.help", "bukkit.command.list"), Collections.emptyList());
        Group admin = new Group("admin", false, "&c[Admin] ", Collections.singletonList("*"), Collections.singletonList("default"));
        groups.clear();
        groups.put(def.name, def);
        groups.put(admin.name, admin);
    }

    private String listGroupNames() {
        return String.join(", ", groups.keySet());
    }

    private void setupSQLite() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Driver SQLite não encontrado.");
        }
        File dbFile = new File(plugin.getDataFolder(), "newgroups.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
        }
        conn.setAutoCommit(false);
    }

    private void ensureSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS groups (name TEXT PRIMARY KEY, is_default INTEGER NOT NULL, prefix TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS group_permissions (group_name TEXT NOT NULL, permission TEXT NOT NULL, PRIMARY KEY (group_name, permission))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS group_parents (group_name TEXT NOT NULL, parent_name TEXT NOT NULL, PRIMARY KEY (group_name, parent_name))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, group_name TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS player_permissions (uuid TEXT NOT NULL, permission TEXT NOT NULL, PRIMARY KEY (uuid, permission))");
        }
        conn.commit();
    }

    private boolean isDatabaseEmpty() throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM groups")) {
            return rs.next() && rs.getInt("c") == 0;
        }
    }

    private void loadGroupsFromDatabaseToMemory() throws SQLException {
        groups.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT name, is_default, prefix FROM groups")) {
            while (rs.next()) {
                String name = rs.getString("name");
                groups.put(name.toLowerCase(Locale.ENGLISH), new Group(name, rs.getInt("is_default") == 1, rs.getString("prefix"), new ArrayList<>(), new ArrayList<>()));
            }
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT group_name, permission FROM group_permissions")) {
            while (rs.next()) {
                Group g = groups.get(rs.getString("group_name").toLowerCase(Locale.ENGLISH));
                if (g != null) g.permissions.add(rs.getString("permission"));
            }
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT group_name, parent_name FROM group_parents")) {
            while (rs.next()) {
                Group g = groups.get(rs.getString("group_name").toLowerCase(Locale.ENGLISH));
                if (g != null) g.parents.add(rs.getString("parent_name"));
            }
        }
        if (!hasAnyDefault() && !groups.isEmpty()) {
            Group g = groups.values().iterator().next();
            groups.put(g.name, new Group(g.name, true, g.prefix, g.permissions, g.parents));
            plugin.getLogger().warning("Nenhum grupo padrão encontrado. Marcando '" + g.name + "' como padrão.");
        }
    }

    private void writeMemoryToDatabase(boolean overwrite) throws SQLException {
        if (overwrite) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM group_permissions");
                st.executeUpdate("DELETE FROM group_parents");
                st.executeUpdate("DELETE FROM groups");
            }
        }
        try (PreparedStatement upGroup = conn.prepareStatement("INSERT OR REPLACE INTO groups(name,is_default,prefix) VALUES(?,?,?)");
             PreparedStatement addPerm = conn.prepareStatement("INSERT OR REPLACE INTO group_permissions(group_name,permission) VALUES(?,?)");
             PreparedStatement addParent = conn.prepareStatement("INSERT OR REPLACE INTO group_parents(group_name,parent_name) VALUES(?,?)")) {
            for (Group g : groups.values()) {
                upGroup.setString(1, g.name);
                upGroup.setInt(2, g.isDefault ? 1 : 0);
                upGroup.setString(3, g.prefix);
                upGroup.addBatch();
                for (String p : g.permissions) {
                    addPerm.setString(1, g.name);
                    addPerm.setString(2, p);
                    addPerm.addBatch();
                }
                for (String parent : g.parents) {
                    addParent.setString(1, g.name);
                    addParent.setString(2, parent);
                    addParent.addBatch();
                }
            }
            upGroup.executeBatch();
            addPerm.executeBatch();
            addParent.executeBatch();
            conn.commit();
        }
    }

    private String getPlayerGroupFromDB(UUID uuid) {
        if (uuid == null) return null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT group_name FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("group_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro lendo grupo do jogador no SQLite", e);
        }
        return null;
    }

    private Set<String> getPlayerPermissionsFromDB(UUID uuid) {
        Set<String> perms = new HashSet<>();
        if (uuid == null) return perms;
        try (PreparedStatement ps = conn.prepareStatement("SELECT permission FROM player_permissions WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    perms.add(rs.getString("permission"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro lendo permissões do jogador no SQLite", e);
        }
        return perms;
    }

    private void upsertPlayerGroup(UUID uuid, String group) {
        if (uuid == null) return;
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO players(uuid,group_name) VALUES(?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, group);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro salvando jogador no SQLite", e);
        }
        groupsConfig.set("players." + uuid.toString() + ".group", group);
        saveYaml();
    }

    private String resolvePlayerGroup(UUID uuid) {
        String g = getPlayerGroupFromDB(uuid);
        if (g != null && groups.containsKey(g.toLowerCase(Locale.ENGLISH))) return g.toLowerCase(Locale.ENGLISH);
        for (Group gg : groups.values()) if (gg.isDefault) return gg.name;
        return "default";
    }

    private Set<String> resolvePermissions(String groupName, Set<String> visited) {
        String gname = (groupName == null ? "default" : groupName.toLowerCase(Locale.ENGLISH));
        if (!groups.containsKey(gname) || visited.contains(gname)) return Collections.emptySet();
        visited.add(gname);
        Group g = groups.get(gname);
        if (g.permissions.contains("*")) return new HashSet<>(Collections.singletonList("*"));
        LinkedHashSet<String> perms = new LinkedHashSet<>(g.permissions);
        for (String parent : g.parents) {
            perms.addAll(resolvePermissions(parent, visited));
        }
        return perms;
    }

    private void clearAttachment(UUID uuid) {
        PermissionAttachment at = attachments.remove(uuid);
        if (at != null) {
            try {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.removeAttachment(at);
            } catch (Exception ignored) {}
        }
    }

    private void applyToPlayer(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;
        clearAttachment(uuid);
        PermissionAttachment at = p.addAttachment(plugin);
        attachments.put(uuid, at);
        String groupName = resolvePlayerGroup(uuid);
        Set<String> groupPerms = resolvePermissions(groupName, new HashSet<>());
        Set<String> playerPerms = getPlayerPermissionsFromDB(uuid);

        Set<String> finalPerms = new HashSet<>(groupPerms);
        for (String perm : playerPerms) {
            boolean neg = perm.startsWith("-");
            String node = neg ? perm.substring(1) : perm;
            finalPerms.remove(node);
            finalPerms.remove("-" + node);
            finalPerms.add(perm);
        }

        if (finalPerms.contains("*")) {
            at.setPermission("*", true);
        } else {
            for (String perm : finalPerms) {
                boolean neg = perm.startsWith("-");
                String node = neg ? perm.substring(1) : perm;
                if (!node.isEmpty()) at.setPermission(node, !neg);
            }
        }
        plugin.getLogger().info("[NewGroups] Permissões aplicadas para " + p.getName() + " (Grupo: " + groupName + ")");
    }

    private void applyAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyToPlayer(p.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        applyToPlayer(e.getPlayer().getUniqueId());
    }

    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("newgroups") && !command.getName().equalsIgnoreCase("newgroup")) return false;
        if (sender instanceof Player && !sender.hasPermission("newgroups.usar.*")) {
            sender.sendMessage(color("&cVocê não tem permissão para usar este comando."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ENGLISH);
        switch (sub) {
            case "editor":
                handleEditorCommand(sender, label, args);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;
            case "setgroup":
                handleSetGroupCommand(sender, label, args);
                break;
            case "whois":
                handleWhoisCommand(sender, label, args);
                break;
            case "importyaml":
                handleImportYamlCommand(sender);
                break;
            case "exportyaml":
                handleExportYamlCommand(sender);
                break;
            default:
                sendHelp(sender, label);
                break;
        }
        return true;
    }

    private void handleEditorCommand(final CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color("&cApenas no jogo."));
            return;
        }
        final Player p = (Player) sender;
        if (args.length > 1 && "fechar".equalsIgnoreCase(args[1])) {
            Integer taskId = editorTasks.remove(p.getUniqueId());
            if (taskId != null) {
                Bukkit.getScheduler().cancelTask(taskId);
                p.sendMessage(color("&e[NewGroups] Verificação em tempo real desativada."));
            } else {
                p.sendMessage(color("&c[NewGroups] Verificação em tempo real não estava ativa."));
            }
            return;
        }
        if (editorTasks.containsKey(p.getUniqueId())) {
            p.sendMessage(color("&c[NewGroups] A verificação já está ativa. Use /" + label + " editor fechar para parar."));
            return;
        }
        final String sid = serverId != null ? serverId : ("srv-" + Bukkit.getPort());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ensureRegistered(sid);
            String url = createEditorSession(sid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (url != null) {
                    p.sendMessage(color("&aLink do editor: &f" + url));
                    p.sendMessage(color("&e[NewGroups] Verificação em tempo real ativada (a cada 1s). Use /ng editor fechar para parar."));
                    int taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> pullAndApplyVerbose(sid), 20L, 20L).getTaskId();
                    editorTasks.put(p.getUniqueId(), taskId);
                } else {
                    p.sendMessage(color("&cFalha ao criar sessão."));
                }
            });
        });
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!hasAdmin(sender)) { deny(sender); return; }
        try {
            loadGroupsFromDatabaseToMemory();
            writeMemoryToYaml(true);
            applyAllOnline();
            sender.sendMessage(color("&a[NewGroups] Recarregado do SQLite e exportado para YAML."));
        } catch (SQLException e) {
            sender.sendMessage(color("&cFalha ao recarregar do SQLite. Veja o console."));
            plugin.getLogger().log(Level.SEVERE, "reload()", e);
        }
    }

    private void handleSetGroupCommand(CommandSender sender, String label, String[] args) {
        if (!hasAdmin(sender)) { deny(sender); return; }
        if (args.length < 3) {
            sender.sendMessage(color("&cUso: /" + label + " setgroup <player> <group>"));
            return;
        }
        String playerName = args[1];
        String groupName = args[2].toLowerCase(Locale.ENGLISH);
        if (!groups.containsKey(groupName)) {
            sender.sendMessage(color("&cGrupo '" + groupName + "' não existe."));
            return;
        }
        OfflinePlayer target = findPlayerByName(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(color("&cJogador '" + playerName + "' não encontrado."));
            return;
        }
        upsertPlayerGroup(target.getUniqueId(), groupName);
        if (target.isOnline()) {
            applyToPlayer(target.getUniqueId());
        }
        sender.sendMessage(color("&aGrupo de &f" + target.getName() + " &aatribuído para &f" + groupName + "&a."));
    }

    private void handleWhoisCommand(CommandSender sender, String label, String[] args) {
        String who = (args.length >= 2) ? args[1] : (sender instanceof Player ? sender.getName() : null);
        if (who == null) {
            sender.sendMessage(color("&cUso: /" + label + " whois <player>"));
            return;
        }
        OfflinePlayer target = findPlayerByName(who);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(color("&cJogador '" + who + "' não encontrado."));
            return;
        }
        String g = resolvePlayerGroup(target.getUniqueId());
        sender.sendMessage(color("&e" + target.getName() + " &7-> grupo: &f" + g));
    }

    private void handleImportYamlCommand(CommandSender sender) {
        if (!hasAdmin(sender)) { deny(sender); return; }
        try {
            setupYamlFile();
            loadGroupsFromYamlToMemory();
            if (groups.isEmpty()) {
                sender.sendMessage(color("&cYAML vazio. Nada a importar."));
                return;
            }
            writeMemoryToDatabase(true);
            applyAllOnline();
            sender.sendMessage(color("&aImportado do YAML para SQLite e reaplicado."));
        } catch (Exception e) {
            sender.sendMessage(color("&cFalha ao importar YAML -> SQLite. Veja o console."));
            plugin.getLogger().log(Level.SEVERE, "importyaml()", e);
        }
    }

    private void handleExportYamlCommand(CommandSender sender) {
        if (!hasAdmin(sender)) { deny(sender); return; }
        try {
            loadGroupsFromDatabaseToMemory();
            writeMemoryToYaml(true);
            sender.sendMessage(color("&aExportado do SQLite para YAML."));
        } catch (SQLException e) {
            sender.sendMessage(color("&cFalha ao exportar SQLite -> YAML. Veja o console."));
            plugin.getLogger().log(Level.SEVERE, "exportyaml()", e);
        }
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("newgroups") && !command.getName().equalsIgnoreCase("newgroup")) return Collections.emptyList();
        if (sender instanceof Player && !sender.hasPermission("newgroups.usar.*")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ENGLISH);
            for (String sub : Arrays.asList("editor", "reload", "setgroup", "whois", "importyaml", "exportyaml")) {
                if (sub.startsWith(partial)) completions.add(sub);
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("editor")) {
                if ("fechar".startsWith(args[1].toLowerCase(Locale.ENGLISH))) completions.add("fechar");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setgroup")) {
            String partial = args[2].toLowerCase(Locale.ENGLISH);
            for (String g : groups.keySet()) {
                if (g.startsWith(partial)) completions.add(g);
            }
        }
        return completions;
    }

    private void sendHelp(CommandSender s, String label) {
        s.sendMessage(color("&6=== NewGroups (1.7.10) ==="));
        s.sendMessage(color("&e/" + label + " editor &7- Abre a sessão e ativa a verificação."));
        s.sendMessage(color("&e/" + label + " editor fechar &7- Desativa a verificação em tempo real."));
        s.sendMessage(color("&e/" + label + " reload &7- Recarrega do SQLite e exporta pro YAML"));
        s.sendMessage(color("&e/" + label + " setgroup <player> <group> &7- Define grupo do jogador"));
        s.sendMessage(color("&e/" + label + " whois <player> &7- Mostra o grupo do jogador"));
        s.sendMessage(color("&e/" + label + " importyaml &7- YAML -> SQLite"));
        s.sendMessage(color("&e/" + label + " exportyaml &7- SQLite -> YAML"));
        s.sendMessage(color("&7Grupos: &f" + listGroupNames()));
    }

    private boolean hasAdmin(CommandSender s) {
        if (s instanceof ConsoleCommandSender) return true;
        return s.hasPermission("newgroups.admin") || s.isOp();
    }

    private void deny(CommandSender s) {
        s.sendMessage(color("&cVocê não tem permissão. (newgroups.admin)"));
    }

    private OfflinePlayer findPlayerByName(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        return Bukkit.getOfflinePlayer(name);
    }

    private static String color(String s) {
        return s.replace('&', '§');
    }

    private void startBridgeTasks() {
        final String sid = serverId != null ? serverId : ("srv-" + Bukkit.getPort());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ensureRegistered(sid);
            pushSnapshot(sid);
            syncPlayers(sid);
        });
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                ensureRegistered(sid);
                pullAndApply(sid);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[NewGroups] pull falhou", t);
            }
        }, 20L * 10L, 20L * 5L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                ensureRegistered(sid);
                pushSnapshot(sid);
                syncPlayers(sid);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[NewGroups] push/sync falhou", t);
            }
        }, 20L * 60L, 20L * 60L);
    }

    private void ensureRegistered(String sid) {
        try {
            String body = "{\"serverId\":\"" + escapeJson(sid) + "\",\"name\":\"" + escapeJson(Bukkit.getServerName()) + "\"}";
            httpPostJson("/api/v1/servers/register", body);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NewGroups] registro: " + t);
        }
    }

    private String createEditorSession(String sid) {
        try {
            String body = "{\"serverId\":\"" + escapeJson(sid) + "\"}";
            String resp = httpPostJson("/api/v1/editor/sessions", body);
            return resp != null ? extractJsonString(resp, "url") : null;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[NewGroups] Falha chamando API de sessão", t);
            return null;
        }
    }

    private void pushSnapshot(String sid) {
        try {
            Map<String, Object> snap = dumpSnapshot();
            String body = "{\"serverId\":\"" + escapeJson(sid) + "\",\"groups\":" + snap.get("groupsJson") + ",\"users\":" + snap.get("usersJson") + "}";
            httpPostJson("/bridge/push", body);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NewGroups] bridge/push falhou: " + t.getMessage());
        }
    }

    private void syncPlayers(String sid) {
        try {
            OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();
            StringBuilder playersJson = new StringBuilder("[");
            boolean first = true;
            for (OfflinePlayer p : allPlayers) {
                if (p != null && p.getName() != null) {
                    if (!first) {
                        playersJson.append(",");
                    }
                    playersJson.append("{\"uuid\":\"").append(p.getUniqueId().toString()).append("\",\"username\":\"").append(escapeJson(p.getName())).append("\"}");
                    first = false;
                }
            }
            playersJson.append("]");
            String body = "{\"serverId\":\"" + escapeJson(sid) + "\",\"players\":" + playersJson.toString() + "}";
            httpPostJson("/bridge/sync-players", body);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NewGroups] /bridge/sync-players falhou: " + t.getMessage());
        }
    }

    private void pullAndApply(String sid) {
        String resp = httpGet("/bridge/pull?serverId=" + urlEncode(sid));
        if (resp == null || resp.length() == 0 || resp.contains("\"empty\":true")) return;

        List<PGroup> newGroups = parseEditorGroups(resp);
        List<PUser> newUsers = parseEditorUsers(resp);
        if (newGroups == null || newUsers == null) return;

        try {
            applyPulledGroups(newGroups);
            applyPulledUsers(newUsers);
            loadGroupsFromDatabaseToMemory();
            writeMemoryToYaml(true);
            applyAllOnline();
            plugin.getLogger().info("[NewGroups] Aplicadas mudanças do editor (" + newGroups.size() + " grupos, " + newUsers.size() + " usuários).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[NewGroups] Falha aplicando payload do editor", e);
        }
    }

    private void pullAndApplyVerbose(String sid) {
        final Map<String, Set<String>> beforeGroups = new HashMap<>();
        for (Group g : groups.values()) {
            beforeGroups.put(g.name, new HashSet<>(g.permissions));
        }
        final Map<UUID, Set<String>> beforeUserPerms = new HashMap<>();
        final Map<UUID, String> beforeUserGroups = new HashMap<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT uuid, group_name FROM players")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                beforeUserGroups.put(uuid, rs.getString("group_name"));
                beforeUserPerms.put(uuid, getPlayerPermissionsFromDB(uuid));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao criar snapshot de usuários", e);
        }

        String resp = httpGet("/bridge/pull?serverId=" + urlEncode(sid));
        if (resp == null || resp.length() == 0 || resp.contains("\"empty\":true")) return;

        List<PGroup> newGroups = parseEditorGroups(resp);
        List<PUser> newUsers = parseEditorUsers(resp);
        if (newGroups == null || newUsers == null) return;

        try {
            applyPulledGroups(newGroups);
            applyPulledUsers(newUsers);
            loadGroupsFromDatabaseToMemory();
            writeMemoryToYaml(true);
            applyAllOnline();

            final Map<String, Set<String>> afterGroups = new HashMap<>();
            for (Group g : groups.values()) {
                afterGroups.put(g.name, new HashSet<>(g.permissions));
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                List<String> changes = new ArrayList<>();
                compareMaps(beforeGroups, afterGroups, "Grupo", "Permissão", changes);

                final Map<UUID, String> afterUserGroups = new HashMap<>();
                try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT uuid, group_name FROM players")) {
                    while (rs.next()) {
                        afterUserGroups.put(UUID.fromString(rs.getString("uuid")), rs.getString("group_name"));
                    }
                } catch (SQLException ignored) {}

                Set<UUID> allUsers = new HashSet<>(beforeUserGroups.keySet());
                allUsers.addAll(afterUserGroups.keySet());

                for (UUID uuid : allUsers) {
                    String beforeGroup = beforeUserGroups.get(uuid);
                    String afterGroup = afterUserGroups.get(uuid);
                    if (!Objects.equals(beforeGroup, afterGroup)) {
                        changes.add(color("&6[NG] &fGrupo de &e" + getPlayerName(uuid) + " &falterado para &e" + (afterGroup != null ? afterGroup : "Padrão")));
                    }

                    Set<String> beforePerms = beforeUserPerms.getOrDefault(uuid, Collections.emptySet());
                    Set<String> afterPerms = getPlayerPermissionsFromDB(uuid);
                    comparePerms(beforePerms, afterPerms, "Permissão do jogador &e" + getPlayerName(uuid), changes);
                }


                if (!changes.isEmpty()) broadcastToStaff(changes);
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[NewGroups] Falha aplicando payload do editor (verbose)", e);
        }
    }

    private void comparePerms(Set<String> before, Set<String> after, String context, List<String> changes) {
        for (String perm : after) {
            if (!before.contains(perm)) {
                changes.add(color("&6[NG] &f" + context + " &e" + perm + " &afoi adicionada&f."));
            }
        }
        for (String perm : before) {
            if (!after.contains(perm)) {
                changes.add(color("&6[NG] &f" + context + " &e" + perm + " &cfoi removida&f."));
            }
        }
    }

    private void compareMaps(Map<String, Set<String>> before, Map<String, Set<String>> after, String entityType, String permType, List<String> changes) {
        for (String name : after.keySet()) {
            if (!before.containsKey(name)) {
                changes.add(color("&6[NG] &f" + entityType + " &e" + name + " &afoi criado&f."));
            }
        }
        for (String name : before.keySet()) {
            if (!after.containsKey(name)) {
                changes.add(color("&6[NG] &f" + entityType + " &e" + name + " &cfoi deletado&f."));
            } else {
                comparePerms(before.get(name), after.get(name), permType + " no grupo &e" + name, changes);
            }
        }
    }

    private String getPlayerName(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return p != null && p.getName() != null ? p.getName() : uuid.toString().substring(0, 8);
    }


    private void broadcastToStaff(final List<String> messages) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("newgroups.chat")) {
                    for (String message : messages) player.sendMessage(message);
                }
            }
        });
    }

    private Map<String, Object> dumpSnapshot() throws SQLException {
        StringBuilder gsb = new StringBuilder("[");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT name, is_default, prefix FROM groups")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) gsb.append(",");
                first = false;
                String name = rs.getString(1);
                gsb.append("{\"id\":\"").append(escapeJson(name)).append("\",");
                gsb.append("\"name\":\"").append(escapeJson(name)).append("\",");
                gsb.append("\"weight\":").append(rs.getInt(2) == 1 ? "1" : "10").append(",");
                gsb.append("\"prefix\":\"").append(escapeJson(rs.getString(3))).append("\",");
                gsb.append("\"inherits\":").append(getJsonArray("SELECT parent_name FROM group_parents WHERE group_name=?", name)).append(",");
                gsb.append("\"permissions\":").append(getPermsJson("SELECT permission FROM group_permissions WHERE group_name=?", name)).append("}");
            }
        }
        gsb.append("]");

        StringBuilder usb = new StringBuilder("[");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT uuid, group_name FROM players")) {
            boolean first = true;
            while (rs.next()) {
                if (!first) usb.append(",");
                first = false;
                String uuid = rs.getString(1);
                String group = rs.getString(2);
                usb.append("{\"uuid\":\"").append(escapeJson(uuid)).append("\",");
                if (group != null) usb.append("\"group\":\"").append(escapeJson(group)).append("\",");
                usb.append("\"permissions\":").append(getPermsJson("SELECT permission FROM player_permissions WHERE uuid=?", uuid)).append("}");
            }
        }
        usb.append("]");

        Map<String, Object> out = new HashMap<>();
        out.put("groupsJson", gsb.toString());
        out.put("usersJson", usb.toString());
        return out;
    }

    private String getJsonArray(String sql, String param) throws SQLException {
        StringBuilder sb = new StringBuilder("[");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("\"").append(escapeJson(rs.getString(1))).append("\"");
                }
            }
        }
        return sb.append("]").toString();
    }

    private String getPermsJson(String sql, String param) throws SQLException {
        StringBuilder sb = new StringBuilder("[");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    String node = rs.getString(1);
                    boolean val = !node.startsWith("-");
                    String pure = val ? node : node.substring(1);
                    sb.append("{\"node\":\"").append(escapeJson(pure)).append("\",\"value\":").append(val).append("}");
                }
            }
        }
        return sb.append("]").toString();
    }

    private static final class PGroup { String name; int weight; List<String> inherits = new ArrayList<>(); List<PNode> permissions = new ArrayList<>(); String prefix = ""; boolean isDefault = false; }
    private static final class PUser { String uuid; String group; List<PNode> permissions = new ArrayList<>(); }
    private static final class PNode { String node; boolean value; }

    private List<PGroup> parseEditorGroups(String json) {
        String arrJson = extractJsonArrayBody(json, "groups");
        if (arrJson == null) return null;
        List<PGroup> list = new ArrayList<>();
        int idx = 0;
        while (true) {
            String obj = extractJsonObject(arrJson, idx);
            if (obj == null) break;
            idx = arrJson.indexOf(obj, idx) + obj.length();
            PGroup g = new PGroup();
            g.name = extractJsonString(obj, "name", extractJsonString(obj, "id"));
            g.prefix = extractJsonString(obj, "prefix", "");
            g.weight = Integer.parseInt(extractJsonNumber(obj, "weight", "1"));
            g.inherits = extractStringArray(obj, "inherits");
            g.permissions = extractPermNodes(obj, "permissions");
            g.isDefault = g.weight <= 1;
            if (g.name != null) list.add(g);
        }
        return list;
    }

    private List<PUser> parseEditorUsers(String json) {
        String arrJson = extractJsonArrayBody(json, "users");
        if (arrJson == null) return new ArrayList<>();
        List<PUser> list = new ArrayList<>();
        int idx = 0;
        while (true) {
            String obj = extractJsonObject(arrJson, idx);
            if (obj == null) break;
            idx = arrJson.indexOf(obj, idx) + obj.length();
            PUser u = new PUser();
            u.uuid = extractJsonString(obj, "uuid", null);
            u.group = extractJsonString(obj, "group", null);
            u.permissions = extractPermNodes(obj, "permissions");
            if (u.uuid != null) list.add(u);
        }
        return list;
    }

    private void applyPulledGroups(List<PGroup> newGroups) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM group_permissions");
            st.executeUpdate("DELETE FROM group_parents");
            st.executeUpdate("DELETE FROM groups");
        }
        try (PreparedStatement upG = conn.prepareStatement("INSERT OR REPLACE INTO groups(name,is_default,prefix) VALUES(?,?,?)");
             PreparedStatement upP = conn.prepareStatement("INSERT OR REPLACE INTO group_permissions(group_name,permission) VALUES(?,?)");
             PreparedStatement upPar = conn.prepareStatement("INSERT OR REPLACE INTO group_parents(group_name,parent_name) VALUES(?,?)")) {
            for (PGroup g : newGroups) {
                upG.setString(1, g.name);
                upG.setInt(2, g.isDefault ? 1 : 0);
                upG.setString(3, g.prefix == null ? "" : g.prefix);
                upG.addBatch();
                for (String parent : g.inherits) {
                    upPar.setString(1, g.name); upPar.setString(2, parent); upPar.addBatch();
                }
                for (PNode n : g.permissions) {
                    upP.setString(1, g.name); upP.setString(2, n.value ? n.node : "-" + n.node); upP.addBatch();
                }
            }
            upG.executeBatch();
            upPar.executeBatch();
            upP.executeBatch();
            conn.commit();
        }
    }

    private void applyPulledUsers(List<PUser> newUsers) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM player_permissions");
            st.executeUpdate("DELETE FROM players");
        }
        try (PreparedStatement upPl = conn.prepareStatement("INSERT INTO players(uuid,group_name) VALUES(?,?)");
             PreparedStatement upP = conn.prepareStatement("INSERT INTO player_permissions(uuid,permission) VALUES(?,?)")) {
            for (PUser u : newUsers) {
                upPl.setString(1, u.uuid);
                upPl.setString(2, u.group);
                upPl.addBatch();
                for (PNode n : u.permissions) {
                    upP.setString(1, u.uuid);
                    upP.setString(2, n.value ? n.node : "-" + n.node);
                    upP.addBatch();
                }
            }
            upPl.executeBatch();
            upP.executeBatch();
            conn.commit();
        }
    }

    private String httpPostJson(String path, String body) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(NG_API_BASE + path);
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (NG_API_TOKEN != null && !NG_API_TOKEN.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + NG_API_TOKEN);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }
            int code = c.getResponseCode();
            try (InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream()) {
                String resp = readAll(is);
                if (code >= 200 && code < 300) return resp;
                plugin.getLogger().warning("[NewGroups] POST " + path + " -> " + code + ": " + resp);
                return null;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NewGroups] POST falhou " + path, t);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String httpGet(String path) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(NG_API_BASE + path);
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            if (NG_API_TOKEN != null && !NG_API_TOKEN.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + NG_API_TOKEN);
            int code = c.getResponseCode();
            try (InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream()) {
                String resp = readAll(is);
                if (code >= 200 && code < 300) return resp;
                plugin.getLogger().warning("[NewGroups] GET " + path + " -> " + code + ": " + resp);
                return null;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NewGroups] GET falhou " + path, t);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[2048];
            int r;
            while ((r = br.read(buf)) != -1) sb.append(buf, 0, r);
            return sb.toString();
        }
    }

    private static String extractJsonObject(String json, int startIndex) {
        int objStart = json.indexOf('{', startIndex);
        if (objStart < 0) return null;
        int depth = 0;
        boolean inStr = false;
        for (int i = objStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inStr = !inStr;
            if (!inStr) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return json.substring(objStart, i + 1);
                }
            }
        }
        return null;
    }

    private static String extractJsonArrayBody(String json, String field) {
        int fieldIndex = json.indexOf("\"" + field + "\"");
        if (fieldIndex < 0) return null;
        int arrStart = json.indexOf('[', fieldIndex);
        if (arrStart < 0) return null;
        int depth = 0;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(arrStart + 1, i);
            }
        }
        return null;
    }

    private static String extractJsonString(String json, String field, String def) {
        String val = extractJsonString(json, field);
        return val != null ? val : def;
    }
    private static String extractJsonString(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int i = json.indexOf(key);
        if (i < 0) return null;
        int c = json.indexOf(':', i + key.length());
        if (c < 0) return null;
        int q1 = json.indexOf('"', c + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        while (q2 > 0 && json.charAt(q2 - 1) == '\\') q2 = json.indexOf('"', q2 + 1);
        if (q2 < 0) return null;
        return unescapeJson(json.substring(q1 + 1, q2));
    }

    private static String extractJsonNumber(String json, String field, String def) {
        String val = extractJsonNumber(json, field);
        return val != null ? val : def;
    }
    private static String extractJsonNumber(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int i = json.indexOf(key);
        if (i < 0) return null;
        int c = json.indexOf(':', i + key.length());
        if (c < 0) return null;
        int s = c + 1;
        while (s < json.length() && Character.isWhitespace(json.charAt(s))) s++;
        int e = s;
        while (e < json.length() && "-0123456789".indexOf(json.charAt(e)) >= 0) e++;
        return e > s ? json.substring(s, e) : null;
    }

    private static List<String> extractStringArray(String json, String field) {
        List<String> out = new ArrayList<>();
        String arrBody = extractJsonArrayBody(json, field);
        if (arrBody == null) return out;
        int idx = 0;
        while (idx < arrBody.length()) {
            int q1 = arrBody.indexOf('"', idx);
            if (q1 < 0) break;
            int q2 = arrBody.indexOf('"', q1 + 1);
            while (q2 > 0 && arrBody.charAt(q2-1) == '\\') q2 = arrBody.indexOf('"', q2 + 1);
            if (q2 < 0) break;
            out.add(unescapeJson(arrBody.substring(q1 + 1, q2)));
            idx = q2 + 1;
        }
        return out;
    }

    private static List<PNode> extractPermNodes(String json, String field) {
        List<PNode> out = new ArrayList<>();
        String arrBody = extractJsonArrayBody(json, field);
        if (arrBody == null) return out;
        int idx = 0;
        while (true) {
            String obj = extractJsonObject(arrBody, idx);
            if (obj == null) break;
            idx = arrBody.indexOf(obj, idx) + obj.length();
            PNode pn = new PNode();
            pn.node = extractJsonString(obj, "node");
            String valueStr = extractJsonString(obj, "value");
            pn.value = valueStr != null ? Boolean.parseBoolean(valueStr) : (obj.contains("\"value\":true"));
            if (pn.node != null) out.add(pn);
        }
        return out;
    }

    private static String unescapeJson(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}