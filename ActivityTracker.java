package vn.astrax.quest.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.model.QuestType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Lay mau moi 5 giay de tinh quang duong di bo va thoi gian choi (bo qua AFK/teleport/phuong tien). */
public final class ActivityTracker implements Listener {
    public static final int SAMPLE_SECONDS = 5;

    private static final class S {
        Location last;
        float lastYaw;
        double walk;
        int idleSec;
        int playSec;
    }

    private final AstraxQuestPlugin plugin;
    private final Map<UUID, S> states = new ConcurrentHashMap<>();

    public ActivityTracker(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        var cfg = plugin.settings();
        for (Player p : Bukkit.getOnlinePlayers()) {
            S s = states.computeIfAbsent(p.getUniqueId(), k -> new S());
            Location cur = p.getLocation();
            boolean validStep = false;
            double d = 0;
            if (s.last != null && s.last.getWorld() != null && s.last.getWorld().equals(cur.getWorld())) {
                double dx = cur.getX() - s.last.getX();
                double dz = cur.getZ() - s.last.getZ();
                d = Math.hypot(dx, dz);
                double dy = Math.abs(cur.getY() - s.last.getY());
                float yawDiff = Math.abs(cur.getYaw() - s.lastYaw);
                boolean moved = d >= 0.5 || dy >= 0.5 || yawDiff > 2f;
                if (moved) s.idleSec = 0;
                else s.idleSec += SAMPLE_SECONDS;
                validStep = d >= 0.5 && d <= cfg.walkMaxPerSample
                        && !p.isGliding() && !p.isFlying()
                        && (cfg.walkAllowVehicle || !p.isInsideVehicle());
            } else {
                s.idleSec = 0;
            }
            s.last = cur;
            s.lastYaw = cur.getYaw();

            boolean afk = s.idleSec >= cfg.afkSeconds;
            if (afk) continue;
            s.playSec += SAMPLE_SECONDS;
            if (s.playSec >= 60) {
                plugin.progress().add(p, QuestType.PLAYTIME, "ANY", s.playSec / 60);
                s.playSec %= 60;
            }
            if (validStep) {
                s.walk += d;
                int whole = (int) s.walk;
                if (whole >= 1) {
                    plugin.progress().add(p, QuestType.WALK, "ANY", whole);
                    s.walk -= whole;
                }
            }
        }
        states.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        S s = states.get(e.getPlayer().getUniqueId());
        if (s != null) s.last = null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }
}
