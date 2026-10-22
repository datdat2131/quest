package vn.astrax.quest.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.model.QuestType;

import java.io.File;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Cau noi su kien cua plugin ngoai (BoxMine, Guild, Cho dau gia, ...) thanh tien do nhiem vu ma KHONG can
 * biet API cua ho trong luc bien dich: cau hinh trong integrations.yml, goi getter bang reflection.
 */
public final class BridgeManager {
    private final AstraxQuestPlugin plugin;
    private final Listener marker = new Listener() {
    };

    public BridgeManager(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void unregister() {
        HandlerList.unregisterAll(marker);
    }

    public void reload() {
        unregister();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "integrations.yml"));
        ConfigurationSection root = y.getConfigurationSection("bridges");
        if (root == null) return;
        int ok = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", false)) continue;
            try {
                register(id, s);
                ok++;
            } catch (Exception e) {
                plugin.getLogger().warning("[bridge:" + id + "] khong dang ky duoc: " + e.getMessage());
            }
        }
        if (ok > 0) plugin.getLogger().info("Da dang ky " + ok + " bridge su kien ngoai.");
    }

    private void register(String id, ConfigurationSection s) throws Exception {
        String pluginName = s.getString("plugin", "");
        String eventClass = s.getString("event", "");
        QuestType type = QuestType.parse(s.getString("type"));
        if (eventClass.isBlank() || type == null) throw new IllegalArgumentException("thieu 'event' hoac 'type' hop le");
        ClassLoader loader = plugin.getClass().getClassLoader();
        if (!pluginName.isBlank()) {
            Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
            if (target == null || !target.isEnabled()) throw new IllegalStateException("plugin '" + pluginName + "' chua bat");
            loader = target.getClass().getClassLoader();
        }
        Class<?> raw = Class.forName(eventClass, true, loader);
        if (!Event.class.isAssignableFrom(raw)) throw new IllegalArgumentException(eventClass + " khong phai Event");
        Class<? extends Event> clazz = raw.asSubclass(Event.class);

        String playerChain = s.getString("player", "getPlayer");
        String targetChain = s.getString("target", "");
        String amountChain = s.getString("amount", "");
        String fixedTarget = s.getString("fixed-target", "ANY");
        long fixedAmount = Math.max(1, s.getLong("fixed-amount", 1));

        Bukkit.getPluginManager().registerEvent(clazz, marker, EventPriority.MONITOR, (l, ev) -> {
            if (!clazz.isInstance(ev)) return;
            Runnable job = () -> {
                try {
                    Player p = toPlayer(invoke(ev, playerChain));
                    if (p == null) return;
                    String target = fixedTarget;
                    if (!targetChain.isBlank()) {
                        Object t = invoke(ev, targetChain);
                        if (t != null) target = String.valueOf(t);
                    }
                    long amount = fixedAmount;
                    if (!amountChain.isBlank() && invoke(ev, amountChain) instanceof Number n) amount = n.longValue();
                    plugin.progress().add(p, type, target, amount);
                } catch (Exception e) {
                    plugin.getLogger().fine("[bridge:" + id + "] loi doc su kien: " + e);
                }
            };
            if (ev.isAsynchronous()) Bukkit.getScheduler().runTask(plugin, job);
            else job.run();
        }, plugin, true);
    }

    private static Object invoke(Object o, String chain) throws Exception {
        Object cur = o;
        for (String m : chain.split("\\.")) {
            if (cur == null || m.isBlank()) return null;
            Method me = cur.getClass().getMethod(m.trim().replace("()", ""));
            cur = me.invoke(cur);
        }
        return cur;
    }

    private static Player toPlayer(Object o) {
        if (o instanceof Player p) return p;
        if (o instanceof OfflinePlayer op) return op.getPlayer();
        if (o instanceof UUID u) return Bukkit.getPlayer(u);
        if (o instanceof String s) return Bukkit.getPlayerExact(s);
        return null;
    }
}
