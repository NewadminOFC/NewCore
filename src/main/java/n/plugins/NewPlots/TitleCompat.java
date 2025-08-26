package n.plugins.NewPlots;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class TitleCompat {
    private TitleCompat() {}

    public static void send(Player p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Method m = p.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            m.invoke(p, title, subtitle, fadeIn, stay, fadeOut);
            return;
        } catch (Throwable ignored) {}
        try {
            Method m2 = p.getClass().getMethod("sendTitle", String.class, String.class);
            m2.invoke(p, title, subtitle);
            return;
        } catch (Throwable ignored) {}
        if (title != null && !title.isEmpty()) p.sendMessage(title);
        if (subtitle != null && !subtitle.isEmpty()) p.sendMessage(subtitle);
    }
}
