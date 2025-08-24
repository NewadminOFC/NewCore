// File: src/main/java/n/plugins/NewEconomy/NewEconomy.java
package n.plugins.NewEconomy;

import n.plugins.NewCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public final class NewEconomy implements CommandExecutor, Listener {

    /* ============ SQLite ============ */
    private Connection conn;

    /* ============ Config cache ============ */
    private YamlConfiguration cfg;
    private File cfgFile;

    private String prefix;
    // currency
    private String curSymbol;
    private boolean curSpace;
    private int curDecimals;
    private char curDecimalSep;
    private char curThousandSep;
    private String curFormat;

    // start balance
    private boolean startEnabled;
    private long startAmountCents;
    private boolean startOnlyFirst;

    // payments
    private long minPay;
    private long maxPay;
    private boolean allowSelfPay;
    private boolean allowOfflinePay;
    private boolean feeEnabled;
    private double feePercent;

    // top
    private int topSize;

    // enable/disable commands
    private boolean enableBalanceSelf, enableBalanceOther, enablePay, enableGive, enableTake, enableTop, enableReload;

    // messages
    private final Map<String,String> M = new HashMap<>();
    private String permAdmin;

    // extras
    private String msgCmdDisabled;
    private String msgTopHeader, msgTopLine, msgTopFooter, msgTopEmpty;

    // core ref
    private NewCore core;

    /* ============ Lifecycle ============ */
    public void onEnable(NewCore core) {
        this.core = core;

        saveDefaultConfigEconomy();
        reloadEconomyConfig();

        open();
        createTable();

        PluginCommand c = core.getCommand("money");
        if (c != null) c.setExecutor(this);

        Bukkit.getPluginManager().registerEvents(this, core);

        NewEconomyAPI.register(this);

        core.getLogger().info("[NewEconomy] carregado.");
    }

    public void onDisable() {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
    }

    /* ============ Config ============ */

    private void saveDefaultConfigEconomy() {
        if (!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
        cfgFile = new File(core.getDataFolder(), "NewEconomy.yml");
        if (!cfgFile.exists()) {
            core.saveResource("NewEconomy.yml", false);
        }
    }

    private void reloadEconomyConfig() {
        if (cfgFile == null) cfgFile = new File(core.getDataFolder(), "NewEconomy.yml");
        cfg = YamlConfiguration.loadConfiguration(cfgFile);

        prefix = color(cfg.getString("prefix", "&b[Money]&r "));

        curSymbol      = cfg.getString("currency.symbol", "R$");
        curSpace       = cfg.getBoolean("currency.space_after_symbol", true);
        curDecimals    = cfg.getInt("currency.decimals", 2);
        curDecimalSep  = cfg.getString("currency.decimal_separator", ",").charAt(0);
        curThousandSep = cfg.getString("currency.thousand_separator", ".").charAt(0);
        curFormat      = cfg.getString("currency.format", "{symbol}{space}{value}");

        startEnabled     = cfg.getBoolean("start_balance.enabled", true);
        startAmountCents = cfg.getLong("start_balance.amount_cents", 10000L);
        startOnlyFirst   = cfg.getBoolean("start_balance.only_first_join", true);

        minPay          = cfg.getLong("payments.min_pay_cents", 100L);
        maxPay          = cfg.getLong("payments.max_pay_cents", 10000000L);
        allowSelfPay    = cfg.getBoolean("payments.allow_self_pay", false);
        allowOfflinePay = cfg.getBoolean("payments.allow_offline_pay", true);
        feeEnabled      = cfg.getBoolean("payments.fee.enabled", false);
        feePercent      = cfg.getDouble("payments.fee.percent", 5.0);

        topSize   = Math.max(1, cfg.getInt("top.size", 10));
        permAdmin = cfg.getString("permissions.admin", "neweconomy.admin");

        enableBalanceSelf  = cfg.getBoolean("commands.enable.balance_self", true);
        enableBalanceOther = cfg.getBoolean("commands.enable.balance_other", true);
        enablePay          = cfg.getBoolean("commands.enable.pay", true);
        enableGive         = cfg.getBoolean("commands.enable.give", true);
        enableTake         = cfg.getBoolean("commands.enable.take", true);
        enableTop          = cfg.getBoolean("commands.enable.top", true);
        enableReload       = cfg.getBoolean("commands.enable.reload", true);

        // map messages (with prefix in msg())
        loadMsg("messages.no_permission", "&cVocê não tem permissão.");
        loadMsg("messages.only_players", "&cApenas jogadores.");
        loadMsg("messages.invalid_value", "&cValor inválido.");
        loadMsg("messages.player_not_found", "&cJogador não encontrado.");
        loadMsg("messages.not_enough_money", "&cVocê não tem dinheiro suficiente.");
        loadMsg("messages.reloaded", "&aConfig recarregada.");
        loadMsg("messages.your_balance", "&aSeu saldo: &f{amount}");
        loadMsg("messages.other_balance", "&aSaldo de &f{player}&a: &f{amount}");
        loadMsg("messages.pay_usage", "&eUso: /money pay <nick> <valor>");
        loadMsg("messages.pay_self_blocked", "&cVocê não pode pagar a si mesmo.");
        loadMsg("messages.pay_sent", "&aVocê pagou &f{amount} &apara &f{target}");
        loadMsg("messages.pay_received", "&aVocê recebeu &f{amount} &ade &f{player}");
        loadMsg("messages.pay_offline_blocked", "&cNão é permitido pagar jogadores offline.");
        loadMsg("messages.pay_below_min", "&cValor mínimo para pagar é &f{amount}");
        loadMsg("messages.pay_above_max", "&cValor máximo para pagar é &f{amount}");
        loadMsg("messages.pay_fee_info", "&7Taxa aplicada: &f{amount}");
        loadMsg("messages.give_usage", "&eUso: /money give <nick> <valor>");
        loadMsg("messages.take_usage", "&eUso: /money take <nick> <valor>");
        loadMsg("messages.set_usage",  "&eUso: /money set <nick> <valor>");
        loadMsg("messages.gave_money", "&aVocê deu &f{amount} &apara &f{target}");
        loadMsg("messages.took_money", "&cVocê removeu &f{amount} &cde &f{target}");
        loadMsg("messages.set_money",  "&eVocê definiu o saldo de &f{target} &epara &f{amount}");
        loadMsg("messages.top_error", "&cErro ao carregar TOP.");

        // extras (no auto-prefix)
        msgCmdDisabled = color(cfg.getString("messages.command_disabled", "&cEste comando está desativado."));
        msgTopHeader   = color(cfg.getString("messages.top_header", "&6=== Top Money ==="));
        msgTopLine     = cfg.getString("messages.top_line", "&e#{pos} &f{player} &7- &f{amount}");
        msgTopFooter   = color(cfg.getString("messages.top_footer", ""));
        msgTopEmpty    = color(cfg.getString("messages.top_empty", "&7Ninguém no ranking ainda."));
    }

    private void loadMsg(String path, String def) { M.put(path, color(cfg.getString(path, def))); }
    private String msg(String path) {
        String s = M.get(path);
        return (s != null) ? (prefix + s) : (prefix + "§cMensagem faltando: " + path);
    }
    private static String color(String s) { return s == null ? "" : s.replace('&','§'); }

    /* ============ SQLite ============ */

    private void open() {
        try {
            if (!core.getDataFolder().exists()) core.getDataFolder().mkdirs();
            File dbFile = new File(core.getDataFolder(), "economy.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(url);
        } catch (Exception e) {
            core.getLogger().severe("Erro ao abrir SQLite: " + e.getMessage());
        }
    }

    private void createTable() {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS money (" +
                    "uuid TEXT PRIMARY KEY," +
                    "balance INTEGER NOT NULL DEFAULT 0)");
        } catch (SQLException e) {
            core.getLogger().severe("Erro ao criar tabela: " + e.getMessage());
        }
    }

    private long selectBalance(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance FROM money WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("balance");
        } catch (SQLException e) {
            core.getLogger().warning("selectBalance: " + e.getMessage());
        }
        return 0L;
    }

    private void upsertBalance(UUID uuid, long cents) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO money(uuid,balance) VALUES(?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET balance=excluded.balance")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, Math.max(0, cents));
            ps.executeUpdate();
        } catch (SQLException e) {
            try (PreparedStatement up = conn.prepareStatement("UPDATE money SET balance=? WHERE uuid=?")) {
                up.setLong(1, Math.max(0, cents));
                up.setString(2, uuid.toString());
                if (up.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement("INSERT INTO money(uuid,balance) VALUES(?,?)")) {
                        ins.setString(1, uuid.toString());
                        ins.setLong(2, Math.max(0, cents));
                        ins.executeUpdate();
                    }
                }
            } catch (SQLException ex) {
                core.getLogger().warning("upsertBalance fallback: " + ex.getMessage());
            }
        }
    }

    /* ============ API ============ */

    public long getBalance(UUID uuid) { return selectBalance(uuid); }
    public void setBalance(UUID uuid, long cents) { upsertBalance(uuid, cents); }
    public void deposit(UUID uuid, long cents) { if (cents > 0) upsertBalance(uuid, selectBalance(uuid) + cents); }
    public boolean withdraw(UUID uuid, long cents) {
        if (cents <= 0) return false;
        long cur = selectBalance(uuid);
        if (cur < cents) return false;
        upsertBalance(uuid, cur - cents);
        return true;
    }
    public boolean transfer(UUID from, UUID to, long cents) {
        if (cents <= 0) return false;
        long a = selectBalance(from);
        if (a < cents) return false;
        try {
            conn.setAutoCommit(false);
            upsertBalance(from, a - cents);
            long b = selectBalance(to);
            upsertBalance(to, b + cents);
            conn.commit();
            conn.setAutoCommit(true);
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); conn.setAutoCommit(true); } catch (Throwable ignored) {}
            core.getLogger().warning("transfer: " + e.getMessage());
            return false;
        }
    }

    /* ============ Formatação ============ */
    public String format(long cents) {
        double v = cents / 100.0D;
        DecimalFormatSymbols sym = new DecimalFormatSymbols(new Locale("pt","BR"));
        sym.setDecimalSeparator(curDecimalSep);
        sym.setGroupingSeparator(curThousandSep);

        String pattern = "#,##0";
        if (curDecimals > 0) {
            StringBuilder sb = new StringBuilder(pattern).append(".");
            for (int i=0;i<curDecimals;i++) sb.append("0");
            pattern = sb.toString();
        }
        DecimalFormat df = new DecimalFormat(pattern, sym);
        String value = df.format(v);

        String space = curSpace ? " " : "";
        return curFormat
                .replace("{symbol}", curSymbol)
                .replace("{space}", space)
                .replace("{value}", value);
    }

    /* ============ Placeholders ============ */
    public String replacePlaceholders(OfflinePlayer player, String input) {
        if (input == null) return "";
        String out = input;
        if (player != null) {
            long bal = getBalance(player.getUniqueId());
            String balFmt = format(bal);
            out = out.replace("{eco_balance_formatted}", balFmt)
                    .replace("{vault_eco_balance_formatted}", balFmt)
                    .replace("{player}", (player.getName() != null ? player.getName() : "player"));
        }
        return color(out);
    }

    public String replacePlaceholders(OfflinePlayer player, OfflinePlayer target, String input, Long amountCents) {
        String out = replacePlaceholders(player, input);
        if (target != null) out = out.replace("{target}", (target.getName() != null ? target.getName() : "target"));
        if (amountCents != null) out = out.replace("{amount}", format(amountCents));
        return out;
    }

    /* ============ Listener: saldo inicial ============ */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!startEnabled) return;
        if (startOnlyFirst && p.hasPlayedBefore()) return;
        deposit(p.getUniqueId(), startAmountCents);
        p.sendMessage(replacePlaceholders(p, msg("messages.your_balance")
                .replace("{amount}", format(getBalance(p.getUniqueId())))));
    }

    /* ============ Comandos ============ */
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] a) {
        if (!cmd.getName().equalsIgnoreCase("money")) return false;

        // /money -> próprio saldo
        if (a.length == 0) {
            if (!enableBalanceSelf) { s.sendMessage(prefix + msgCmdDisabled); return true; }
            if (!(s instanceof Player)) { s.sendMessage(msg("messages.only_players")); return true; }
            Player p = (Player) s;
            long bal = getBalance(p.getUniqueId());
            p.sendMessage(replacePlaceholders(p, msg("messages.your_balance").replace("{amount}", format(bal))));
            return true;
        }

        // ====== SUBCOMANDOS PRIMEIRO! ======
        String sub = a[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!enableReload) { s.sendMessage(prefix + msgCmdDisabled); return true; }
            if (!s.hasPermission(permAdmin)) { s.sendMessage(msg("messages.no_permission")); return true; }
            reloadEconomyConfig();
            s.sendMessage(msg("messages.reloaded"));
            return true;
        }

        if (sub.equals("top")) {
            if (!enableTop) { s.sendMessage(prefix + msgCmdDisabled); return true; }

            if (!msgTopHeader.isEmpty()) s.sendMessage(prefix + msgTopHeader);

            int count = 0;
            try (Statement st = conn.createStatement()) {
                ResultSet rs = st.executeQuery("SELECT uuid, balance FROM money ORDER BY balance DESC LIMIT " + topSize);
                while (rs.next()) {
                    count++;
                    UUID u = UUID.fromString(rs.getString("uuid"));
                    OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                    String name = (op.getName() != null ? op.getName() : u.toString());

                    String line = msgTopLine
                            .replace("{pos}", String.valueOf(count))
                            .replace("{player}", name)
                            .replace("{amount}", format(rs.getLong("balance")));

                    s.sendMessage(color(prefix + line));
                }
            } catch (SQLException e) {
                s.sendMessage(msg("messages.top_error"));
                return true;
            }

            if (count == 0) {
                s.sendMessage(prefix + msgTopEmpty);
            } else if (!msgTopFooter.isEmpty()) {
                s.sendMessage(prefix + msgTopFooter);
            }
            return true;
        }

        if (sub.equals("pay")) {
            if (!enablePay) { s.sendMessage(prefix + msgCmdDisabled); return true; }
            if (!(s instanceof Player)) { s.sendMessage(msg("messages.only_players")); return true; }
            if (a.length < 3) { s.sendMessage(msg("messages.pay_usage")); return true; }

            Player from = (Player) s;
            OfflinePlayer to = Bukkit.getOfflinePlayer(a[1]);
            if (to == null || (to.getName() == null && !to.hasPlayedBefore())) {
                s.sendMessage(msg("messages.player_not_found")); return true;
            }
            if (!allowSelfPay && from.getUniqueId().equals(to.getUniqueId())) {
                s.sendMessage(msg("messages.pay_self_blocked")); return true;
            }
            if (!allowOfflinePay && !to.isOnline()) {
                s.sendMessage(msg("messages.pay_offline_blocked")); return true;
            }

            long value = parseCents(a[2]);
            if (value <= 0) { s.sendMessage(msg("messages.invalid_value")); return true; }
            if (value < minPay) { s.sendMessage(replacePlaceholders(from, to, msg("messages.pay_below_min"), minPay)); return true; }
            if (value > maxPay) { s.sendMessage(replacePlaceholders(from, to, msg("messages.pay_above_max"), maxPay)); return true; }

            long feeCents = 0;
            if (feeEnabled && feePercent > 0) feeCents = Math.max(1L, Math.round(value * (feePercent / 100.0D)));
            long totalDebit = value + feeCents;

            if (!withdraw(from.getUniqueId(), totalDebit)) {
                s.sendMessage(msg("messages.not_enough_money")); return true;
            }
            deposit(to.getUniqueId(), value);

            from.sendMessage(replacePlaceholders(from, to, msg("messages.pay_sent"), value));
            if (feeCents > 0) from.sendMessage(replacePlaceholders(from, to, msg("messages.pay_fee_info"), feeCents));
            if (to.isOnline()) ((Player) to).sendMessage(replacePlaceholders(from, to, msg("messages.pay_received"), value));
            return true;
        }

        if (sub.equals("give")) {
            if (!enableGive) { s.sendMessage(prefix + msgCmdDisabled); return true; }
            if (!s.hasPermission(permAdmin)) { s.sendMessage(msg("messages.no_permission")); return true; }
            if (a.length < 3) { s.sendMessage(msg("messages.give_usage")); return true; }

            OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
            if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
                s.sendMessage(msg("messages.player_not_found")); return true;
            }
            long cents = parseCents(a[2]);
            if (cents <= 0) { s.sendMessage(msg("messages.invalid_value")); return true; }

            deposit(target.getUniqueId(), cents);
            s.sendMessage(replacePlaceholders(target, target, msg("messages.gave_money"), cents));
            return true;
        }

        if (sub.equals("take")) {
            if (!enableTake) { s.sendMessage(prefix + msgCmdDisabled); return true; }
            if (!s.hasPermission(permAdmin)) { s.sendMessage(msg("messages.no_permission")); return true; }
            if (a.length < 3) { s.sendMessage(msg("messages.take_usage")); return true; }

            OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
            if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
                s.sendMessage(msg("messages.player_not_found")); return true;
            }
            long cents = parseCents(a[2]);
            if (cents <= 0) { s.sendMessage(msg("messages.invalid_value")); return true; }

            withdraw(target.getUniqueId(), cents);
            s.sendMessage(replacePlaceholders(target, target, msg("messages.took_money"), cents));
            return true;
        }

        // ====== Fallback: /money <nick> ======
        if (a.length == 1) {
            if (!enableBalanceOther) { s.sendMessage(prefix + msgCmdDisabled); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(a[0]);
            if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
                s.sendMessage(msg("messages.player_not_found"));
                return true;
            }
            long bal = getBalance(target.getUniqueId());
            s.sendMessage(replacePlaceholders(target, msg("messages.other_balance")
                    .replace("{player}", target.getName())
                    .replace("{amount}", format(bal))));
            return true;
        }

        return true;
    }

    /* ============ util parse ============ */
    private long parseCents(String s) {
        try {
            double v = Double.parseDouble(s.replace(",", "."));
            return Math.round(v * 100.0D);
        } catch (Exception e) {
            return -1;
        }
    }
}
