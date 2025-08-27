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

/**
 * NewGroups módulo (1.7.10)
 * - Usa YAML: plugins/NewCore/NewGroups.yml
 * - Usa SQLite: plugins/NewCore/newgroups.db
 * - Comando: /newgroups (reload|setgroup|whois|importyaml|exportyaml|editor)
 * - Permissão admin: newgroups.admin
 */
public final class NewGroup implements Listener, CommandExecutor, TabCompleter {

    private static final String YAML_NAME = "NewGroups.yml";
    private static final String NG_API_BASE = "http://192.168.15.200:25580";
    private static final String NG_API_TOKEN = "coloque_um_token_opcional";

    private final JavaPlugin plugin;

    private final Map<String, Group> groups = new LinkedHashMap<String, Group>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<UUID, PermissionAttachment>();

    private File groupsFile;
    private YamlConfiguration groupsConfig;

    private Connection conn;

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
            this.permissions = new LinkedHashSet<String>(permissions == null ? Collections.<String>emptyList() : permissions);
            this.parents = new ArrayList<String>(parents == null ? Collections.<String>emptyList() : parents);
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

            plugin.getLogger().info("[NewGroups] módulo iniciado. Grupos: " + listGroupNames());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao iniciar módulo NewGroups", e);
        }
    }

    public void shutdown() {
        Player[] online = Bukkit.getServer().getOnlinePlayers().toArray(new Player[0]);
        for (int i = 0; i < online.length; i++) {
            clearAttachment(online[i].getUniqueId());
        }
        groups.clear();
        attachments.clear();
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
        plugin.getLogger().info("[NewGroups] módulo finalizado.");
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
                y.set("groups.default.permissions", Arrays.asList(
                        "bukkit.command.help",
                        "bukkit.command.list"
                ));

                y.set("groups.admin.default", false);
                y.set("groups.admin.prefix", "&c[Admin] ");
                y.set("groups.admin.parents", Collections.singletonList("default"));
                y.set("groups.admin.permissions", Collections.singletonList("*"));

                y.createSection("players");

                y.save(groupsFile);
                plugin.getLogger().info("[NewGroups] Criado " + groupsFile.getAbsolutePath());
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
            plugin.getLogger().info("[NewGroups] Carregado YAML: " + groupsFile.getAbsolutePath());
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao carregar " + groupsFile.getName() + " (verifique sintaxe YAML)", e);
            groupsConfig = new YamlConfiguration();
            groupsConfig.createSection("groups");
            groupsConfig.createSection("players");
        }
    }

    private boolean saveYaml() {
        try { groupsConfig.save(groupsFile); return true; }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Erro salvando " + groupsFile.getName(), e); return false; }
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
            groupsConfig.set(path + "parents", new ArrayList<String>(g.parents));
            groupsConfig.set(path + "permissions", new ArrayList<String>(g.permissions));
        }
        if (!groupsConfig.isConfigurationSection("players")) groupsConfig.createSection("players");
        saveYaml();
    }

    private boolean hasAnyDefault() {
        for (Group g : groups.values()) if (g.isDefault) return true;
        return false;
    }

    private void seedDefaultInMemory() {
        Group def = new Group("default", true, "&7",
                Arrays.asList("bukkit.command.help", "bukkit.command.list"), Collections.<String>emptyList());
        Group admin = new Group("admin", false, "&c[Admin] ",
                Arrays.asList("*"), Arrays.asList("default"));
        groups.clear();
        groups.put(def.name, def);
        groups.put(admin.name, admin);
    }

    private String listGroupNames() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String k : groups.keySet()) {
            if (!first) sb.append(", ");
            sb.append(k);
            first = false;
        }
        return sb.toString();
    }

    private void setupSQLite() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Driver SQLite não encontrado. Inclua org.xerial:sqlite-jdbc no seu plugin (shade).");
        }
        File dbFile = new File(plugin.getDataFolder(), "newgroups.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        conn = DriverManager.getConnection(url);
        Statement st = conn.createStatement();
        try { st.execute("PRAGMA foreign_keys=ON"); } finally { try { st.close(); } catch (SQLException ignored) {} }
        conn.setAutoCommit(false);
    }

    private void ensureSchema() throws SQLException {
        Statement st = conn.createStatement();
        try {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS groups (" +
                    "name TEXT PRIMARY KEY," +
                    "is_default INTEGER NOT NULL," +
                    "prefix TEXT NOT NULL" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS group_permissions (" +
                    "group_name TEXT NOT NULL," +
                    "permission TEXT NOT NULL," +
                    "PRIMARY KEY (group_name, permission)" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS group_parents (" +
                    "group_name TEXT NOT NULL," +
                    "parent_name TEXT NOT NULL," +
                    "PRIMARY KEY (group_name, parent_name)" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY," +
                    "group_name TEXT NOT NULL" +
                    ")");
        } finally {
            try { st.close(); } catch (SQLException ignored) {}
        }
        conn.commit();
    }

    private boolean isDatabaseEmpty() throws SQLException {
        Statement st = conn.createStatement();
        try {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM groups");
            try {
                return rs.next() && rs.getInt("c") == 0;
            } finally { try { rs.close(); } catch (SQLException ignored) {} }
        } finally { try { st.close(); } catch (SQLException ignored) {} }
    }

    private void loadGroupsFromDatabaseToMemory() throws SQLException {
        groups.clear();

        Statement st1 = conn.createStatement();
        ResultSet rs1 = null;
        try {
            rs1 = st1.executeQuery("SELECT name, is_default, prefix FROM groups");
            while (rs1.next()) {
                String name = rs1.getString("name");
                boolean def = rs1.getInt("is_default") == 1;
                String prefix = rs1.getString("prefix");
                groups.put(name.toLowerCase(Locale.ENGLISH), new Group(name, def, prefix, Collections.<String>emptyList(), Collections.<String>emptyList()));
            }
        } finally {
            if (rs1 != null) try { rs1.close(); } catch (SQLException ignored) {}
            try { st1.close(); } catch (SQLException ignored) {}
        }

        Statement st2 = conn.createStatement();
        ResultSet rs2 = null;
        try {
            rs2 = st2.executeQuery("SELECT group_name, permission FROM group_permissions");
            while (rs2.next()) {
                String g = rs2.getString("group_name").toLowerCase(Locale.ENGLISH);
                String perm = rs2.getString("permission");
                Group grp = groups.get(g);
                if (grp != null) grp.permissions.add(perm);
            }
        } finally {
            if (rs2 != null) try { rs2.close(); } catch (SQLException ignored) {}
            try { st2.close(); } catch (SQLException ignored) {}
        }

        Statement st3 = conn.createStatement();
        ResultSet rs3 = null;
        try {
            rs3 = st3.executeQuery("SELECT group_name, parent_name FROM group_parents");
            while (rs3.next()) {
                String g = rs3.getString("group_name").toLowerCase(Locale.ENGLISH);
                String parent = rs3.getString("parent_name");
                Group grp = groups.get(g);
                if (grp != null) grp.parents.add(parent);
            }
        } finally {
            if (rs3 != null) try { rs3.close(); } catch (SQLException ignored) {}
            try { st3.close(); } catch (SQLException ignored) {}
        }

        if (!hasAnyDefault()) {
            if (groups.containsKey("default")) {
                Group g = groups.get("default");
                groups.put("default", new Group(g.name, true, g.prefix, g.permissions, g.parents));
            } else {
                seedDefaultInMemory();
                writeMemoryToDatabase(true);
            }
        }
    }

    private void writeMemoryToDatabase(boolean overwrite) throws SQLException {
        if (overwrite) {
            Statement st = conn.createStatement();
            try {
                st.executeUpdate("DELETE FROM group_permissions");
                st.executeUpdate("DELETE FROM group_parents");
                st.executeUpdate("DELETE FROM groups");
            } finally { try { st.close(); } catch (SQLException ignored) {} }
        }

        PreparedStatement upGroup = conn.prepareStatement("INSERT OR REPLACE INTO groups(name,is_default,prefix) VALUES(?,?,?)");
        PreparedStatement delPerms = conn.prepareStatement("DELETE FROM group_permissions WHERE group_name=?");
        PreparedStatement addPerm = conn.prepareStatement("INSERT OR REPLACE INTO group_permissions(group_name,permission) VALUES(?,?)");
        PreparedStatement delParents = conn.prepareStatement("DELETE FROM group_parents WHERE group_name=?");
        PreparedStatement addParent = conn.prepareStatement("INSERT OR REPLACE INTO group_parents(group_name,parent_name) VALUES(?,?)");

        try {
            for (Group g : groups.values()) {
                upGroup.setString(1, g.name);
                upGroup.setInt(2, g.isDefault ? 1 : 0);
                upGroup.setString(3, g.prefix);
                upGroup.addBatch();
            }
            upGroup.executeBatch();

            for (Group g : groups.values()) {
                delPerms.setString(1, g.name); delPerms.addBatch();
                delParents.setString(1, g.name); delParents.addBatch();
            }
            delPerms.executeBatch();
            delParents.executeBatch();

            for (Group g : groups.values()) {
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
            addPerm.executeBatch();
            addParent.executeBatch();

            conn.commit();
        } finally {
            try { upGroup.close(); } catch (SQLException ignored) {}
            try { delPerms.close(); } catch (SQLException ignored) {}
            try { addPerm.close(); } catch (SQLException ignored) {}
            try { delParents.close(); } catch (SQLException ignored) {}
            try { addParent.close(); } catch (SQLException ignored) {}
        }
    }

    private String getPlayerGroupFromDB(UUID uuid) {
        if (uuid == null) return null;
        PreparedStatement ps = null; ResultSet rs = null;
        try {
            ps = conn.prepareStatement("SELECT group_name FROM players WHERE uuid=?");
            ps.setString(1, uuid.toString());
            rs = ps.executeQuery();
            if (rs.next()) return rs.getString("group_name");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erro lendo grupo do jogador no SQLite", e);
        } finally {
            if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
            if (ps != null) try { ps.close(); } catch (SQLException ignored) {}
        }
        return null;
    }

    private void upsertPlayerGroup(UUID uuid, String group) {
        if (uuid == null || group == null) return;
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement("INSERT OR REPLACE INTO players(uuid,group_name) VALUES(?,?)");
            ps.setString(1, uuid.toString());
            ps.setString(2, group);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro salvando jogador no SQLite", e);
        } finally {
            if (ps != null) try { ps.close(); } catch (SQLException ignored) {}
        }
        groupsConfig.set("players." + uuid + ".group", group);
        saveYaml();
    }

    private String resolvePlayerGroup(UUID uuid) {
        String g = getPlayerGroupFromDB(uuid);
        if (g != null && groups.containsKey(g.toLowerCase(Locale.ENGLISH))) return g.toLowerCase(Locale.ENGLISH);

        String yp = groupsConfig.getString("players." + uuid + ".group", null);
        if (yp != null && groups.containsKey(yp.toLowerCase(Locale.ENGLISH))) return yp.toLowerCase(Locale.ENGLISH);

        for (Group gg : groups.values()) if (gg.isDefault) return gg.name;
        return "default";
    }

    private Set<String> resolvePermissions(String groupName, Set<String> visited) {
        String gname = (groupName == null ? "default" : groupName.toLowerCase(Locale.ENGLISH));
        if (!groups.containsKey(gname)) return Collections.<String>emptySet();
        if (visited.contains(gname)) return Collections.<String>emptySet();
        visited.add(gname);

        Group g = groups.get(gname);
        if (g.permissions.contains("*")) {
            LinkedHashSet<String> star = new LinkedHashSet<String>();
            star.add("*");
            return star;
        }

        LinkedHashSet<String> perms = new LinkedHashSet<String>(g.permissions);
        for (int i = 0; i < g.parents.size(); i++) {
            String parent = g.parents.get(i);
            Set<String> parentPerms = resolvePermissions(parent, visited);
            if (parentPerms.contains("*")) {
                LinkedHashSet<String> star = new LinkedHashSet<String>();
                star.add("*");
                return star;
            }
            perms.addAll(parentPerms);
        }
        return perms;
    }

    private void clearAttachment(UUID uuid) {
        PermissionAttachment at = attachments.remove(uuid);
        if (at != null) {
            try {
                Map<String, Boolean> map = new HashMap<String, Boolean>(at.getPermissions());
                for (String perm : map.keySet()) at.unsetPermission(perm);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.removeAttachment(at);
            } catch (Throwable ignored) {}
        }
    }

    private void applyToPlayer(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;

        clearAttachment(uuid);
        PermissionAttachment at = p.addAttachment(plugin);
        attachments.put(uuid, at);

        String groupName = resolvePlayerGroup(uuid);
        Set<String> perms = resolvePermissions(groupName, new HashSet<String>());

        if (perms.contains("*")) {
            at.setPermission("*", true);
        } else {
            for (String perm : perms) {
                boolean neg = perm.startsWith("-");
                String node = neg ? perm.substring(1) : perm;
                if (node.length() == 0) continue;
                at.setPermission(node, !neg);
            }
        }
        plugin.getLogger().info("[NewGroups] Aplicado grupo '" + groupName + "' a " + p.getName());
    }

    private void applyAllOnline() {
        Player[] arr = Bukkit.getServer().getOnlinePlayers().toArray(new Player[0]);
        for (int i = 0; i < arr.length; i++) {
            applyToPlayer(arr[i].getUniqueId());
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { applyToPlayer(e.getPlayer().getUniqueId()); }

    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!"newgroups".equalsIgnoreCase(command.getName()) && !"newgroup".equalsIgnoreCase(command.getName())) return false;

        if (args.length == 0) { sendHelp(sender, label); return true; }

        String sub = args[0].toLowerCase(Locale.ENGLISH);

        if ("editor".equals(sub)) {
            if (!(sender instanceof Player)) { sender.sendMessage(color("&cApenas no jogo.")); return true; }
            final Player p = (Player) sender;
            final String serverId = "srv-" + Bukkit.getPort();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                public void run() {
                    String url = createEditorSession(serverId);
                    if (url == null) {
                        Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            public void run() { p.sendMessage(color("&cFalha ao criar sessão.")); }
                        });
                    } else {
                        final String msg = color("&aLink do editor: &f" + url);
                        Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            public void run() { p.sendMessage(msg); }
                        });
                    }
                }
            });
            return true;
        }

        if ("reload".equals(sub)) {
            if (!hasAdmin(sender)) { deny(sender); return true; }
            try {
                loadGroupsFromDatabaseToMemory();
                writeMemoryToYaml(true);
                applyAllOnline();
                sender.sendMessage(color("&a[NewGroups] Recarregado do SQLite e exportado para YAML."));
            } catch (SQLException e) {
                sender.sendMessage(color("&cFalha ao recarregar do SQLite. Veja o console."));
                plugin.getLogger().log(Level.SEVERE, "reload()", e);
            }
            return true;
        }
        if ("setgroup".equals(sub)) {
            if (!hasAdmin(sender)) { deny(sender); return true; }
            if (args.length < 3) {
                sender.sendMessage(color("&cUso: /" + label + " setgroup <player> <group>"));
                return true;
            }
            String playerName = args[1];
            String groupName = args[2].toLowerCase(Locale.ENGLISH);
            if (!groups.containsKey(groupName)) {
                sender.sendMessage(color("&cGrupo '" + groupName + "' não existe."));
                return true;
            }
            OfflinePlayer target = findPlayerByName(playerName);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(color("&cJogador '" + playerName + "' não encontrado."));
                return true;
            }
            upsertPlayerGroup(target.getUniqueId(), groupName);
            Player online = target.getPlayer();
            if (online != null) applyToPlayer(online.getUniqueId());
            sender.sendMessage(color("&aGrupo de &f" + playerName + " &aatribuído para &f" + groupName + "&a."));
            return true;
        }
        if ("whois".equals(sub)) {
            String who = (args.length >= 2 ? args[1] : (sender instanceof Player ? ((Player) sender).getName() : null));
            if (who == null) { sender.sendMessage(color("&cUso: /" + label + " whois <player>")); return true; }
            OfflinePlayer target = findPlayerByName(who);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(color("&cJogador '" + who + "' não encontrado."));
                return true;
            }
            String g = resolvePlayerGroup(target.getUniqueId());
            sender.sendMessage(color("&e" + who + " &7-> grupo: &f" + g));
            return true;
        }
        if ("importyaml".equals(sub)) {
            if (!hasAdmin(sender)) { deny(sender); return true; }
            try {
                setupYamlFile();
                loadGroupsFromYamlToMemory();
                if (groups.isEmpty()) {
                    sender.sendMessage(color("&cYAML vazio. Nada a importar."));
                    return true;
                }
                writeMemoryToDatabase(true);
                applyAllOnline();
                sender.sendMessage(color("&aImportado do YAML para SQLite e reaplicado."));
            } catch (Exception e) {
                sender.sendMessage(color("&cFalha ao importar YAML -> SQLite. Veja o console."));
                plugin.getLogger().log(Level.SEVERE, "importyaml()", e);
            }
            return true;
        }
        if ("exportyaml".equals(sub)) {
            if (!hasAdmin(sender)) { deny(sender); return true; }
            try {
                loadGroupsFromDatabaseToMemory();
                writeMemoryToYaml(true);
                sender.sendMessage(color("&aExportado do SQLite para YAML."));
            } catch (SQLException e) {
                sender.sendMessage(color("&cFalha ao exportar SQLite -> YAML. Veja o console."));
                plugin.getLogger().log(Level.SEVERE, "exportyaml()", e);
            }
            return true;
        }

        sendHelp(sender, label);
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (!"newgroups".equalsIgnoreCase(command.getName()) && !"newgroup".equalsIgnoreCase(command.getName())) return out;

        if (args.length == 1) {
            out.add("editor");
            out.add("reload");
            out.add("setgroup");
            out.add("whois");
            out.add("importyaml");
            out.add("exportyaml");
            return out;
        }
        if (args.length == 3 && "setgroup".equalsIgnoreCase(args[0])) {
            for (String g : groups.keySet()) out.add(g);
            return out;
        }
        return out;
    }

    private void sendHelp(CommandSender s, String label) {
        s.sendMessage(color("&6=== NewGroups (1.7.10) ==="));
        s.sendMessage(color("&e/" + label + " editor &7- Abre sessão via API e te envia o link"));
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

    private void deny(CommandSender s) { s.sendMessage(color("&cVocê não tem permissão. (newgroups.admin)")); }

    private OfflinePlayer findPlayerByName(String name) {
        Player[] arr = Bukkit.getServer().getOnlinePlayers().toArray(new Player[0]);
        for (int i = 0; i < arr.length; i++) {
            Player p = arr[i];
            if (p != null && p.getName() != null && p.getName().equalsIgnoreCase(name)) return p;
        }
        OfflinePlayer[] all = Bukkit.getServer().getOfflinePlayers();
        for (int i = 0; i < all.length; i++) {
            OfflinePlayer op = all[i];
            if (op != null && op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    private static String color(String s) { return s.replace('&', '§'); }

    private String createEditorSession(String serverId) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(NG_API_BASE + "/api/v1/editor/sessions");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (NG_API_TOKEN != null && NG_API_TOKEN.length() > 0) {
                conn.setRequestProperty("Authorization", "Bearer " + NG_API_TOKEN);
            }
            String body = "{\"serverId\":\"" + escapeJson(serverId) + "\"}";
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code >= 200 && code < 300) {
                String urlField = extractJsonString(resp, "url");
                return urlField;
            } else {
                plugin.getLogger().warning("[NewGroups] API erro " + code + ": " + resp);
                return null;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[NewGroups] Falha chamando API de sessão", t);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[2048];
        int r;
        while ((r = br.read(buf)) != -1) sb.append(buf, 0, r);
        br.close();
        return sb.toString();
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
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
            else if (ch == '\n') sb.append("\\n");
            else if (ch == '\r') sb.append("\\r");
            else if (ch == '\t') sb.append("\\t");
            else sb.append(ch);
        }
        return sb.toString();
    }
}
