package vn.astrax.quest.config;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.util.Text;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Messages {
    private final AstraxQuestPlugin plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    private String prefix = "";

    public Messages(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        try (var in = plugin.getResource("messages.yml")) {
            if (in != null) {
                Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
                cfg.setDefaults(YamlConfiguration.loadConfiguration(r));
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Khong doc duoc messages mac dinh: " + e.getMessage());
        }
        prefix = cfg.getString("prefix", "&6[AstraxQuest] &7");
    }

    public String raw(String key, Object... kv) {
        String s = cfg.getString(key);
        if (s == null) s = "&c[thieu thong diep: " + key + "]";
        return Text.replace(s.replace("{prefix}", prefix), kv);
    }

    public List<String> list(String key, Object... kv) {
        List<String> l = cfg.getStringList(key);
        return Text.replaceAll(l, kv);
    }

    public Component get(String key, Object... kv) {
        return Text.color(raw(key, kv));
    }

    public void send(CommandSender to, String key, Object... kv) {
        if (to != null) to.sendMessage(get(key, kv));
    }
}
