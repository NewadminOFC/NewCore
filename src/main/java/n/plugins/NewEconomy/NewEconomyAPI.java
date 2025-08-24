package n.plugins.NewEconomy;

import org.bukkit.OfflinePlayer;

import java.util.UUID;

public final class NewEconomyAPI {

    private static NewEconomy econ;

    private NewEconomyAPI() {}

    /** Registra a instância do módulo de economia */
    public static void register(NewEconomy e) {
        econ = e;
    }

    /** Retorna a instância bruta do módulo */
    public static NewEconomy get() {
        return econ;
    }

    /* ================= Economia ================= */

    public static long getBalance(UUID u) {
        check();
        return econ.getBalance(u);
    }

    public static void setBalance(UUID u, long c) {
        check();
        econ.setBalance(u, c);
    }

    public static void deposit(UUID u, long c) {
        check();
        econ.deposit(u, c);
    }

    public static boolean withdraw(UUID u, long c) {
        check();
        return econ.withdraw(u, c);
    }

    public static boolean transfer(UUID a, UUID b, long c) {
        check();
        return econ.transfer(a, b, c);
    }

    /* ================= Formatação ================= */

    public static String format(long cents) {
        check();
        return econ.format(cents);
    }

    /* ================= Placeholders ================= */

    public static String replacePlaceholders(OfflinePlayer player, String input) {
        check();
        return econ.replacePlaceholders(player, input);
    }

    public static String replacePlaceholders(OfflinePlayer player, OfflinePlayer target, String input, Long amountCents) {
        check();
        return econ.replacePlaceholders(player, target, input, amountCents);
    }

    /* ================= Util ================= */

    private static void check() {
        if (econ == null) {
            throw new IllegalStateException("NewEconomyAPI não inicializada! Chame NewEconomyAPI.register() no onEnable do módulo.");
        }
    }
}
