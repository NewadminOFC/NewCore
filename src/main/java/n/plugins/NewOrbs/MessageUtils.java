package n.plugins.NewOrbs;

import org.bukkit.ChatColor;
import java.util.Iterator;
import java.util.Map;

public final class MessageUtils {
    private MessageUtils() {}

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
    public static String stripColor(String s) {
        return ChatColor.stripColor(color(s == null ? "" : s));
    }
    public static String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null) return "";
        String out = input;
        if (placeholders != null) {
            Iterator<Map.Entry<String,String>> it = placeholders.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,String> e = it.next();
                out = out.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
            }
        }
        return color(out);
    }
}
