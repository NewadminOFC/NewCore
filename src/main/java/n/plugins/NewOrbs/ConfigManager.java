package n.plugins.NewOrbs;

import n.plugins.NewCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {

    public static final String FILE_NAME = "NewOrbs.yml";

    private final NewCore plugin;
    private File file;
    private FileConfiguration cfg;

    private final LinkedHashMap<String, OrbDefinition> orbs = new LinkedHashMap<String, OrbDefinition>();
    private final LinkedHashMap<String, Integer> levelsBonus = new LinkedHashMap<String, Integer>();
    private final LinkedHashMap<String, String> levelsPerm  = new LinkedHashMap<String, String>();

    public ConfigManager(NewCore plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Garante que o recurso NewOrbs.yml exista no disco (na pasta do NewCore). */
    public static void ensureDefaultFile(NewCore plugin) {
        try {
            File f = new File(plugin.getDataFolder(), FILE_NAME);
            if (!f.exists()) {
                // NewOrbs.yml precisa estar empacotado dentro do JAR do NewCore (resources)
                plugin.saveResource(FILE_NAME, false);
            }
        } catch (IllegalArgumentException ignored) {
            try {
                File f = new File(plugin.getDataFolder(), FILE_NAME);
                if (!f.exists()) f.createNewFile();
            } catch (Exception ignored2) {}
        }
    }

    public void reload() {
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) ensureDefaultFile(plugin);
        this.cfg  = YamlConfiguration.loadConfiguration(file);
        loadOrbs();
        loadLevels();
    }

    public FileConfiguration raw() { return cfg; }

    // ====== geral/config ======
    public String prefix()    { return cfg.getString("config.server-prefix", "&3&lWOrbs "); }
    public String tpCommand() { return cfg.getString("config.tp-command", "jrmctp %value% %player%"); }

    // ====== menu ======
    public String menuTitleMain()     { return cfg.getString("menu.title", "&cTrocar Orbs"); }
    public String menuInfoTitle()     { return cfg.getString("menu.info-title", "&6Informações"); }
    public String menuInfoLore1()     { return cfg.getString("menu.info-lore1", "&eTroque seus orbs por TPs!"); }
    public String menuInfoLoreBonus() { return cfg.getString("menu.info-lore-bonus", "&eBônus atual: &f%bonus%%"); }
    public String menuInfoLoreBase()  { return cfg.getString("menu.info-lore-base", "&eValor base: &f%base% TPs por orb"); }

    public String optionTitle()       { return cfg.getString("menu.option-title", "&aTrocar %amount% Orbs"); }
    public String optionAllTitle()    { return cfg.getString("menu.option-all-title", "&aTrocar TODOS Orbs"); }
    public String optionLore()        { return cfg.getString("menu.option-lore", "&7Clique para trocar %amount% orbs"); }
    public String optionAllLore()     { return cfg.getString("menu.option-all-lore", "&7Clique para trocar todos os seus orbs"); }
    public String optionAmount()      { return cfg.getString("menu.option-amount", "&7Você possui: &e%amount%"); }
    public String optionReceive()     { return cfg.getString("menu.option-receive", "&7Você receberá &e%value% TPs"); }

    // ====== mensagens ======
    public String msgInsufficient()   { return cfg.getString("messages.insufficient-orbs", "%prefix% &cVocê não possui orbs suficientes para trocar."); }
    public String msgTradeTitle()     { return cfg.getString("messages.trade-title", "%prefix% &aVocê trocou seus orbs por TPs com sucesso!"); }
    public String msgTradeWithBonus() { return cfg.getString("messages.trade-with-bonus", "%prefix% &eVocê recebeu &f%value% TPs &e(Bônus de &f%bonus%%&e)"); }
    public String msgTradeNoBonus()   { return cfg.getString("messages.trade-no-bonus", "%prefix% &eVocê recebeu &f%value% TPs"); }
    public String phBonusLine()       { return cfg.getString("messages.bonus", "&a&e     &bBônus &f(%bonus%.0%%)"); }
    public String phValueLine()       { return cfg.getString("messages.value", "&e     &f+%value% &eTPs."); }

    // ====== fallback item ======
    public int    fallbackId()        { return cfg.getInt("item.id", 4146); }
    public int    fallbackData()      { return cfg.getInt("item.data", 0); }
    public String fallbackName()      { return cfg.getString("item.name", "Orbe"); }
    public int    fallbackBaseValue() { return cfg.getInt("item.base-value", 1350); }

    // ====== níveis de bônus/permissões ======
    private void loadLevels() {
        levelsBonus.clear();
        levelsPerm.clear();
        ConfigurationSection bonusSec = cfg.getConfigurationSection("bonus");
        ConfigurationSection permSec  = cfg.getConfigurationSection("permissions");
        if (bonusSec != null) for (String k : bonusSec.getKeys(false)) levelsBonus.put(k.toLowerCase(), bonusSec.getInt(k, 0));
        if (permSec  != null) for (String k : permSec.getKeys(false))  levelsPerm.put(k.toLowerCase(),  permSec.getString(k, ""));
    }
    public int    getBonusForLevelKey(String key)      { Integer v = levelsBonus.get(key.toLowerCase()); return v == null ? 0 : v; }
    public String getPermissionForLevelKey(String key) { String s = levelsPerm.get(key.toLowerCase());  return s == null ? "" : s; }
    public Map<String,Integer> bonusLevelsSnapshot()   { return new LinkedHashMap<String,Integer>(levelsBonus); }
    public Map<String,String>  permissionLevelsSnapshot(){ return new LinkedHashMap<String,String>(levelsPerm); }

    // ====== orbs ======
    public static final class OrbDefinition {
        public final String key;
        public final int id;
        public final int data;
        public final String name;
        public final int baseValue;
        public final String menuTitle;

        public OrbDefinition(String key, int id, int data, String name, int baseValue, String menuTitle) {
            this.key = key; this.id = id; this.data = data; this.name = name; this.baseValue = baseValue; this.menuTitle = menuTitle;
        }
    }

    private void loadOrbs() {
        orbs.clear();
        ConfigurationSection sec = cfg.getConfigurationSection("orbs");
        if (sec != null) {
            Set<String> keys = sec.getKeys(false);
            for (String key : keys) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                int id      = s.getInt("id", fallbackId());
                int data    = s.getInt("data", fallbackData());
                String name = s.getString("name", fallbackName());
                int base    = s.getInt("base-value", fallbackBaseValue());
                String mt   = s.getString("menu-title", "&aTrocar Orbs");
                orbs.put(key.toLowerCase(), new OrbDefinition(key.toLowerCase(), id, data, name, base, mt));
            }
        }
        if (orbs.isEmpty()) {
            orbs.put("default", new OrbDefinition("default", fallbackId(), fallbackData(), fallbackName(), fallbackBaseValue(), "&aTrocar Orbs"));
        }
    }

    public LinkedHashMap<String, OrbDefinition> getOrbs() { return new LinkedHashMap<String, OrbDefinition>(orbs); }

    /** Quantidades padrão dos botões do menu. */
    public int[] optionAmounts() { return new int[] { 1, 16, 32, 64 }; }
}
