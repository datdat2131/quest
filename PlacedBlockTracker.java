package vn.astrax.quest.anticheat;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Ghi nho khoi do nguoi choi dat de khong tinh vao nhiem vu dao (chong dat-pha lap lai). */
public final class PlacedBlockTracker {
    public record Pos(UUID world, int x, int y, int z) {
    }

    private final int max;
    private final long ttlMs;
    private final long cooldownMs;
    private final LinkedHashMap<Pos, Long> map = new LinkedHashMap<>();

    public PlacedBlockTracker(int max, long ttlMs, long cooldownMs) {
        this.max = Math.max(100, max);
        this.ttlMs = ttlMs;
        this.cooldownMs = cooldownMs;
    }

    /** @return false neu day la lan dat lai qua nhanh tai cung vi tri (khong tinh tien do dat khoi). */
    public synchronized boolean markPlaced(Pos p, long now) {
        Long prev = map.remove(p);
        boolean spam = prev != null && now - prev < cooldownMs;
        map.put(p, now);
        Iterator<Pos> it = map.keySet().iterator();
        while (map.size() > max && it.hasNext()) {
            it.next();
            it.remove();
        }
        return !spam;
    }

    public synchronized boolean isPlaced(Pos p, long now) {
        Long t = map.get(p);
        if (t == null) return false;
        if (now - t > ttlMs) {
            map.remove(p);
            return false;
        }
        return true;
    }

    public synchronized boolean unmark(Pos p) {
        return map.remove(p) != null;
    }

    public synchronized int size() {
        return map.size();
    }
}
