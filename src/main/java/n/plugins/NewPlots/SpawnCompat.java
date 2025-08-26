package n.plugins.NewPlots;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class SpawnCompat {
    private SpawnCompat() {}

    public static void setBedSpawn(Player p, Location loc) {
        try {
            Method m = p.getClass().getMethod("setBedSpawnLocation", Location.class, boolean.class);
            m.invoke(p, loc, true);
            return;
        } catch (Throwable ignored) {}
        try {
            Method m2 = p.getClass().getMethod("setBedSpawnLocation", Location.class);
            m2.invoke(p, loc);
        } catch (Throwable ignored) {}
    }
}
