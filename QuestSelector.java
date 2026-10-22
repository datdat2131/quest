package vn.astrax.quest.util;

import vn.astrax.quest.model.QuestDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Chon ngau nhien nhung on dinh (cung seed => cung ket qua) cac nhiem vu cua mot ky. */
public final class QuestSelector {
    private QuestSelector() {
    }

    public static long seed(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    public static List<String> select(List<QuestDef> pool, Map<String, Integer> pick, long seed) {
        List<QuestDef> sorted = new ArrayList<>(pool);
        sorted.sort(Comparator.comparing(QuestDef::id));
        List<String> out = new ArrayList<>();
        if (pick == null || pick.isEmpty()) {
            for (QuestDef q : sorted) out.add(q.id());
            return out;
        }
        Map<String, List<QuestDef>> groups = new TreeMap<>();
        for (QuestDef q : sorted) groups.computeIfAbsent(q.group(), k -> new ArrayList<>()).add(q);
        for (Map.Entry<String, Integer> e : pick.entrySet()) {
            List<QuestDef> g = groups.get(e.getKey());
            if (g == null || e.getValue() <= 0) continue;
            List<QuestDef> copy = new ArrayList<>(g);
            Collections.shuffle(copy, new Random(seed ^ seed(e.getKey())));
            for (int i = 0; i < Math.min(e.getValue(), copy.size()); i++) out.add(copy.get(i).id());
        }
        return out;
    }
}
