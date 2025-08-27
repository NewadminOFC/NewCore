package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class LoginManager {

    private final NewCore plugin;
    private Connection connection;
    private final Set<String> logged = new HashSet<String>();

    private final SecureRandom random = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public LoginManager(NewCore plugin) {
        this.plugin = plugin;
    }

    // Inicializa SQLite
    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/logins.db");
            Statement st = connection.createStatement();
            st.executeUpdate("CREATE TABLE IF NOT EXISTS accounts (name TEXT PRIMARY KEY, password TEXT)");
            st.close();
        } catch (Exception e) {
            // Erros de infra/log sem expor dados sensíveis
            plugin.getLogger().severe("[NewLogin] Erro ao iniciar SQLite: " + e.getMessage());
        }
    }

    public boolean isRegistered(String name) {
        PreparedStatement ps = null; ResultSet rs = null;
        try {
            ps = connection.prepareStatement("SELECT name FROM accounts WHERE name=?");
            ps.setString(1, name.toLowerCase());
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("[NewLogin] Erro em isRegistered: " + e.getMessage());
            return false;
        } finally {
            closeQuiet(rs); closeQuiet(ps);
        }
    }

    public boolean register(String name, String pass) {
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement("INSERT INTO accounts (name,password) VALUES(?,?)");
            ps.setString(1, name.toLowerCase());
            ps.setString(2, pass);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[NewLogin] Erro em register: " + e.getMessage());
            return false;
        } finally {
            closeQuiet(ps);
        }
    }

    public boolean checkPassword(String name, String pass) {
        PreparedStatement ps = null; ResultSet rs = null;
        try {
            ps = connection.prepareStatement("SELECT password FROM accounts WHERE name=?");
            ps.setString(1, name.toLowerCase());
            rs = ps.executeQuery();
            if (rs.next()) {
                String real = rs.getString("password");
                return real != null && real.equals(pass);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[NewLogin] Erro em checkPassword: " + e.getMessage());
        } finally {
            closeQuiet(rs); closeQuiet(ps);
        }
        return false;
    }

    /** Reseta a senha e retorna a nova, ou null se player não registrado */
    public String resetPassword(String name) {
        if (!isRegistered(name)) return null;
        String nova = gerarSenha(8);
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement("UPDATE accounts SET password=? WHERE name=?");
            ps.setString(1, nova);
            ps.setString(2, name.toLowerCase());
            int rows = ps.executeUpdate();
            if (rows > 0) return nova;
        } catch (SQLException e) {
            plugin.getLogger().severe("[NewLogin] Erro em resetPassword: " + e.getMessage());
        } finally {
            closeQuiet(ps);
        }
        return null;
    }

    public void setLogged(Player p, boolean state) {
        String key = p.getName().toLowerCase();
        if (state) logged.add(key);
        else logged.remove(key);
    }

    public boolean isLogged(Player p) {
        return logged.contains(p.getName().toLowerCase());
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException ignored) {}
    }

    public Connection getConnection() { return connection; }

    private String gerarSenha(int tamanho) {
        StringBuilder sb = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private void closeQuiet(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }
}
