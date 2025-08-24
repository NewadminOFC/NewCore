package n.plugins.NewLogin;

import n.plugins.NewCore;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class LoginManager {

    private final NewCore plugin;
    private Connection connection;
    private final Set<String> logged = new HashSet<>();

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
            e.printStackTrace();
        }
    }

    // Verifica se o jogador já está registrado
    public boolean isRegistered(String name) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT name FROM accounts WHERE name=?");
            ps.setString(1, name.toLowerCase());
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            rs.close();
            ps.close();
            return exists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Registra jogador
    public boolean register(String name, String pass) {
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO accounts (name,password) VALUES(?,?)");
            ps.setString(1, name.toLowerCase());
            ps.setString(2, pass);
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Checa se senha está correta
    public boolean checkPassword(String name, String pass) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT password FROM accounts WHERE name=?");
            ps.setString(1, name.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String real = rs.getString("password");
                rs.close();
                ps.close();
                return real.equals(pass);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Reseta a senha de um jogador (utilizado pelo /resetsenha)
    public boolean resetPassword(String name) {
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE accounts SET password=NULL WHERE name=?");
            ps.setString(1, name.toLowerCase());
            int rows = ps.executeUpdate();
            ps.close();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Marca jogador como logado ou não
    public void setLogged(Player p, boolean state) {
        String key = p.getName().toLowerCase();
        if (state) logged.add(key);
        else logged.remove(key);
    }

    public boolean isLogged(Player p) {
        return logged.contains(p.getName().toLowerCase());
    }

    // Fecha conexão com banco
    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {}
    }

    // Permite acessar a conexão (para comandos administrativos)
    public Connection getConnection() {
        return connection;
    }
}
