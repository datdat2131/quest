package vn.astrax.quest.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.astrax.quest.AstraxQuestPlugin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Danh hieu (titles.yml). */
public final class TitleManager {
    public record TitleDef(String id, String display, String description, String material) {
    }

    private final AstraxQuestPlugin plugin;
    private volatile Map<String, TitleDef> titles = Map.of();

    public TitleManager(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "titles.yml"));
        Map<String, TitleDef> map = new LinkedHashMap<>();
        ConfigurationSection s = y.getConfigurationSection("titles");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection t = s.getConfigurationSection(id);
                if (t == null) continue;
                map.put(id, new TitleDef(id, t.getString("display", "&7[" + id + "]"),
                        t.getString("description", ""), t.getString("material", "NAME_TAG")));
            }
        }
        titles = map;
    }

    public TitleDef get(String id) {
        return id == null ? null : titles.get(id);
    }

    public boolean exists(String id) {
        return id != null && titles.containsKey(id);
    }

    public String display(String id) {
        TitleDef t = get(id);
        return t == null ? "" : t.display();
    }

    public Collection<TitleDef> all() {
        return titles.values();
    }
}
