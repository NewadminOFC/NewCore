// File: src/main/java/n/plugins/NewPlots/NewPlotsDB.java
package n.plugins.NewPlots;

import n.plugins.NewCore;

import java.io.File;
import java.sql.*;
import java.util.*;

public class NewPlotsDB {

    private final NewCore plugin;
    private Connection conn;

    public NewPlotsDB(NewCore plugin) {
        this.plugin = plugin;
        open();
        initSchema();
    }

    private void open() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "newplots.db");
            dbFile.getParentFile().mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            plugin.getLogger().severe("[NewPlotsDB] Erro ao abrir SQLite: " + e.getMessage());
        }
    }

    private void initSchema() {
        String sql1 = "CREATE TABLE IF NOT EXISTS plots (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner TEXT NOT NULL," +
                "world TEXT NOT NULL," +
                "ix INTEGER NOT NULL," +
                "iz INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(world, ix, iz)" +
                ");";
        String sql2 = "CREATE TABLE IF NOT EXISTS trusts (" +
                "world TEXT NOT NULL," +
                "ix INTEGER NOT NULL," +
                "iz INTEGER NOT NULL," +
                "trusted TEXT NOT NULL," +
                "PRIMARY KEY(world, ix, iz, trusted)" +
                ");";
        String idx1 = "CREATE INDEX IF NOT EXISTS idx_plots_owner ON plots(owner);";
        String idx2 = "CREATE INDEX IF NOT EXISTS idx_plots_world ON plots(world);";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql1);
            st.executeUpdate(sql2);
            st.executeUpdate(idx1);
            st.executeUpdate(idx2);
        } catch (SQLException e) {
            plugin.getLogger().severe("[NewPlotsDB] Erro schema: " + e.getMessage());
        }
    }

    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    public Optional<PlotRecord> findByIndex(String world, int ix, int iz) {
        String q = "SELECT * FROM plots WHERE world=? AND ix=? AND iz=? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setInt(2, ix);
            ps.setInt(3, iz);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromRS(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] findByIndex: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<PlotRecord> findFirstByOwner(String world, String ownerUuid) {
        String q = "SELECT * FROM plots WHERE world=? AND owner=? ORDER BY created_at ASC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setString(2, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromRS(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] findFirstByOwner: " + e.getMessage());
        }
        return Optional.empty();
    }

    public int countByOwner(String world, String ownerUuid) {
        String q = "SELECT COUNT(*) AS c FROM plots WHERE world=? AND owner=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setString(2, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] countByOwner: " + e.getMessage());
        }
        return 0;
    }

    public long insert(String ownerUuid, String world, int ix, int iz) {
        String q = "INSERT OR IGNORE INTO plots(owner, world, ix, iz, created_at) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(q, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, world);
            ps.setInt(3, ix);
            ps.setInt(4, iz);
            ps.setLong(5, System.currentTimeMillis());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] insert: " + e.getMessage());
        }
        return -1;
    }

    public boolean deleteByIndex(String world, int ix, int iz) {
        String q = "DELETE FROM plots WHERE world=? AND ix=? AND iz=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setInt(2, ix);
            ps.setInt(3, iz);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] deleteByIndex: " + e.getMessage());
        }
        return false;
    }

    public List<String> listOwners(String world) {
        String q = "SELECT DISTINCT owner FROM plots WHERE world=?";
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("owner"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] listOwners: " + e.getMessage());
        }
        return out;
    }

    public boolean addTrust(String world, int ix, int iz, String trustedUuid) {
        String q = "INSERT OR IGNORE INTO trusts(world, ix, iz, trusted) VALUES(?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setInt(2, ix);
            ps.setInt(3, iz);
            ps.setString(4, trustedUuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] addTrust: " + e.getMessage());
        }
        return false;
    }

    public boolean removeTrust(String world, int ix, int iz, String trustedUuid) {
        String q = "DELETE FROM trusts WHERE world=? AND ix=? AND iz=? AND trusted=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setInt(2, ix);
            ps.setInt(3, iz);
            ps.setString(4, trustedUuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] removeTrust: " + e.getMessage());
        }
        return false;
    }

    public boolean isTrusted(String world, int ix, int iz, String trustedUuid) {
        String q = "SELECT 1 FROM trusts WHERE world=? AND ix=? AND iz=? AND trusted=? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setInt(2, ix);
            ps.setInt(3, iz);
            ps.setString(4, trustedUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] isTrusted: " + e.getMessage());
        }
        return false;
    }

    public List<String> listTrusted(String world, int ix, int iz) {
        String q = "SELECT trusted FROM trusts WHERE world=? AND ix=? AND iz=?";
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setInt(2, ix);
            ps.setInt(3, iz);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("trusted"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] listTrusted: " + e.getMessage());
        }
        return out;
    }

    public boolean deleteTrusts(String world, int ix, int iz) {
        String q = "DELETE FROM trusts WHERE world=? AND ix=? AND iz=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, world);
            ps.setInt(2, ix);
            ps.setInt(3, iz);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("[NewPlotsDB] deleteTrusts: " + e.getMessage());
        }
        return false;
    }

    private PlotRecord fromRS(ResultSet rs) throws SQLException {
        return new PlotRecord(
                rs.getLong("id"),
                rs.getString("owner"),
                rs.getString("world"),
                rs.getInt("ix"),
                rs.getInt("iz"),
                rs.getLong("created_at")
        );
    }
}
