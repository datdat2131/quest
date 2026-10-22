package vn.astrax.quest.service;

import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.anticheat.KillGuard;
import vn.astrax.quest.anticheat.PlacedBlockTracker;
import vn.astrax.quest.anticheat.PlacedBlockTracker.Pos;

import java.net.InetSocketAddress;
import java.util.UUID;

/** Cac kiem tra chong gian lan tien do (ket noi logic thuan trong package anticheat voi Bukkit). */
public final class AntiCheat {
    private final AstraxQuestPlugin plugin;
    private final org.bukkit.NamespacedKey noQuestKey;
    private volatile PlacedBlockTracker tracker;
    private volatile KillGuard killGuard;

    public AntiCheat(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
        this.noQuestKey = new org.bukkit.NamespacedKey(plugin, "no_quest");
    }

    public void reload() {
        var s = plugin.settings();
        tracker = new PlacedBlockTracker(s.placedMax, s.placedTtlMs, s.placeCooldownMs);
        killGuard = new KillGuard(s.pvpCooldownMs, s.pvpMaxPerVictim, s.pvpWindowMs);
    }

    public void forget(UUID id) {
        // KillGuard/Tracker tu don dep theo thoi gian, khong can xoa theo nguoi choi.
    }

    private static Pos pos(Block b) {
        return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }

    /** Ghi nhan khoi vua dat. @return true neu duoc tinh tien do dat khoi. */
    public boolean onPlace(Block b) {
        if (!plugin.settings().trackPlaced) return true;
        return tracker.markPlaced(pos(b), System.currentTimeMillis());
    }

    /** @return true neu khoi nay khong phai do nguoi choi dat (duoc tinh tien do dao). Luon go danh dau. */
    public boolean onBreakCounts(Block b) {
        if (!plugin.settings().trackPlaced) return true;
        Pos p = pos(b);
        boolean placed = tracker.isPlaced(p, System.currentTimeMillis());
        tracker.unmark(p);
        return !placed;
    }

    public void tagSpawn(Entity e, String reason) {
        if (plugin.settings().ignoredSpawnReasons.contains(reason)) {
            e.getPersistentDataContainer().set(noQuestKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public boolean mobCounts(Entity e) {
        return !e.getPersistentDataContainer().has(noQuestKey, PersistentDataType.BYTE);
    }

    /** Kiem tra gian lan PvP; true = hop le, duoc tinh tien do. */
    public boolean pvpCounts(Player killer, Player victim) {
        var s = plugin.settings();
        if (killer.getUniqueId().equals(victim.getUniqueId())) return false;
        if (s.pvpBlockSameIp) {
            InetSocketAddress a = killer.getAddress();
            InetSocketAddress b = victim.getAddress();
            if (a != null && b != null && a.getAddress() != null && a.getAddress().equals(b.getAddress())) return false;
        }
        if (s.pvpMinVictimPlayMinutes > 0) {
            long minutes = victim.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
            if (minutes < s.pvpMinVictimPlayMinutes) return false;
        }
        return killGuard.allow(killer.getUniqueId(), victim.getUniqueId(), System.currentTimeMillis());
    }
}
