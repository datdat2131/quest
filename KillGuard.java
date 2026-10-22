package vn.astrax.quest.anticheat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Chong farm PvP: thoi gian cho giua 2 lan giet cung mot nguoi + gioi han so lan trong cua so thoi gian. */
public final class KillGuard {
    private final long cooldownMs;
    private final int maxPerWindow;
    private final long windowMs;
    private final Map<String, Deque<Long>> history = new HashMap<>();

    public KillGuard(long cooldownMs, int maxPerWindow, long windowMs) {
        this.cooldownMs = cooldownMs;
        this.maxPerWindow = maxPerWindow;
        this.windowMs = windowMs;
    }

    public synchronized boolean allow(UUID killer, UUID victim, long now) {
        if (killer.equals(victim)) return false;
        String k = killer + ">" + victim;
        Deque<Long> q = history.computeIfAbsent(k, x -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
        if (!q.isEmpty() && now - q.peekLast() < cooldownMs) return false;
        if (maxPerWindow > 0 && q.size() >= maxPerWindow) return false;
        q.addLast(now);
        if (history.size() > 5000) purge(now);
        return true;
    }

    private void purge(long now) {
        Iterator<Map.Entry<String, Deque<Long>>> it = history.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> q = it.next().getValue();
            if (q.isEmpty() || now - q.peekLast() > windowMs) it.remove();
        }
    }
}
