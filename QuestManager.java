package vn.astrax.quest.service;

import org.bukkit.entity.Player;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.config.QuestLoader;
import vn.astrax.quest.model.CategorySettings;
import vn.astrax.quest.model.EventDef;
import vn.astrax.quest.model.QuestCategory;
import vn.astrax.quest.model.QuestDef;
import vn.astrax.quest.model.QuestState;
import vn.astrax.quest.model.QuestType;
import vn.astrax.quest.model.ResetRule;
import vn.astrax.quest.time.PeriodCalculator;
import vn.astrax.quest.util.QuestSelector;
import vn.astrax.quest.util.Strings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestManager {
    private final AstraxQuestPlugin plugin;

    private volatile QuestLoader.Result data = new QuestLoader.Result(new EnumMap<>(QuestCategory.class), Map.of(), Map.of());
    private volatile Map<QuestType, List<QuestDef>> byType = Map.of();
    private volatile Map<QuestCategory, List<QuestDef>> byCategory = Map.of();
    private volatile PeriodCalculator calc;
    private volatile PeriodCalculator.Snapshot snap;
    private volatile long validUntilMs;
    private final Map<String, Set<String>> selectionCache = new ConcurrentHashMap<>();

    public QuestManager(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(QuestLoader.Result result, PeriodCalculator calculator) {
        Map<QuestType, List<QuestDef>> t = new EnumMap<>(QuestType.class);
        Map<QuestCategory, List<QuestDef>> c = new EnumMap<>(QuestCategory.class);
        for (QuestDef q : result.quests().values()) {
            CategorySettings cs = result.categories().get(q.category());
            if (cs == null || !cs.enabled()) continue;
            t.computeIfAbsent(q.type(), k -> new ArrayList<>()).add(q);
            c.computeIfAbsent(q.category(), k -> new ArrayList<>()).add(q);
        }
        for (List<QuestDef> l : c.values()) l.sort(Comparator.comparingInt(QuestDef::order));
        this.calc = calculator;
        this.snap = null;
        this.validUntilMs = 0;
        this.selectionCache.clear();
        this.byType = t;
        this.byCategory = c;
        this.data = result;
    }

    // ------------------------------------------------------------------ lookup
    public Collection<QuestDef> all() {
        return data.quests().values();
    }

    public QuestDef get(String id) {
        return id == null ? null : data.quests().get(id);
    }

    public List<QuestDef> byType(QuestType type) {
        return byType.getOrDefault(type, List.of());
    }

    public List<QuestDef> inCategory(QuestCategory cat) {
        return byCategory.getOrDefault(cat, List.of());
    }

    public CategorySettings category(QuestCategory cat) {
        return data.categories().get(cat);
    }

    public Map<String, EventDef> events() {
        return data.events();
    }

    // ------------------------------------------------------------------ periods
    public PeriodCalculator.Snapshot snapshot() {
        long now = System.currentTimeMillis();
        PeriodCalculator.Snapshot s = snap;
        if (s == null || now >= validUntilMs) {
            synchronized (this) {
                s = snap;
                if (s == null || now >= validUntilMs) {
                    s = calc.at(Instant.ofEpochMilli(now));
                    snap = s;
                    validUntilMs = s.validUntil().toEpochMilli();
                    selectionCache.clear();
                }
            }
        }
        return s;
    }

    public boolean eventActive(String id) {
        EventDef e = data.events().get(id);
        return e != null && e.activeAt(Instant.now());
    }

    public String periodKey(QuestDef q) {
        if (q.reset() == ResetRule.EVENT) {
            EventDef e = data.events().get(q.eventId());
            return "e:" + q.eventId() + ":" + (e == null ? "x" : e.keySuffix());
        }
        return snapshot().key(q.reset());
    }

    /** Moc reset ke tiep cua nhiem vu (null neu khong reset / su kien: tra ve luc ket thuc). */
    public Instant nextReset(QuestDef q) {
        if (q.reset() == ResetRule.EVENT) {
            EventDef e = data.events().get(q.eventId());
            return e == null ? null : e.end();
        }
        return snapshot().next(q.reset());
    }

    public Instant nextReset(QuestCategory cat) {
        CategorySettings cs = category(cat);
        if (cs == null) return null;
        if (cs.reset() == ResetRule.EVENT) {
            String id = cs.eventId();
            if (id == null) {
                for (QuestDef q : inCategory(cat)) {
                    id = q.eventId();
                    if (id != null) break;
                }
            }
            EventDef e = id == null ? null : data.events().get(id);
            return e == null ? null : e.end();
        }
        return snapshot().next(cs.reset());
    }

    /** Tat ca khoa ky co the dang hieu luc: dung de nap tien do/claims tu DB. */
    public Set<String> relevantPeriods() {
        PeriodCalculator.Snapshot s = snapshot();
        Set<String> p = new HashSet<>();
        p.add(s.daily());
        p.add(s.weekly());
        p.add(s.monthly());
        p.add("once");
        for (EventDef e : data.events().values()) p.add("e:" + e.id() + ":" + e.keySuffix());
        return p;
    }

    // ------------------------------------------------------------------ activity / selection
    private Set<String> selected(CategorySettings cs, UUID uuid) {
        String period = snapshot().key(cs.reset());
        String key = cs.category() + "|" + period + (cs.perPlayer() ? "|" + uuid : "");
        return selectionCache.computeIfAbsent(key, k -> {
            List<QuestDef> pool = new ArrayList<>();
            for (QuestDef q : inCategory(cs.category())) if (q.reset() != ResetRule.EVENT) pool.add(q);
            return new LinkedHashSet<>(QuestSelector.select(pool, cs.pick(), QuestSelector.seed(k)));
        });
    }

    public boolean isActive(QuestDef q, UUID uuid) {
        CategorySettings cs = category(q.category());
        if (cs == null || !cs.enabled()) return false;
        if (q.reset() == ResetRule.EVENT) return eventActive(q.eventId());
        if (!cs.pick().isEmpty() && q.reset() != ResetRule.NEVER) return selected(cs, uuid).contains(q.id());
        return true;
    }

    /** Nhiem vu hien thi cho nguoi choi trong mot muc (da loc theo ky/su kien, an nhiem vu 'hidden' dang khoa). */
    public List<QuestDef> listFor(Player player, QuestCategory cat) {
        List<QuestDef> out = new ArrayList<>();
        for (QuestDef q : inCategory(cat)) {
            if (!isActive(q, player.getUniqueId())) continue;
            if (q.hidden() && lockOf(player, q) != null) continue;
            out.add(q);
        }
        return out;
    }

    // ------------------------------------------------------------------ state
    /** @return ly do bi khoa (da dinh dang mau) hoac null neu dung duoc. */
    public String lockOf(Player player, QuestDef q) {
        PlayerData pd = plugin.progress().data(player.getUniqueId());
        if (pd == null || !pd.loaded) return plugin.messages().raw("lock.loading");
        if (q.permission() != null && !q.permission().isBlank() && !player.hasPermission(q.permission())) {
            return plugin.messages().raw("lock.permission");
        }
        if (q.minLevel() > pd.level) return plugin.messages().raw("lock.level", "level", q.minLevel());
        for (String rid : q.requires()) {
            QuestDef r = get(rid);
            if (r == null) continue;
            if (!pd.claimed.contains(r.id() + "|" + periodKey(r))) {
                return plugin.messages().raw("lock.requires", "quest", r.name());
            }
        }
        if (q.category() == QuestCategory.GUILD && pd.guildKey == null) return plugin.messages().raw("lock.guild");
        return null;
    }

    /** Giong lockOf(...) == null nhung khong tao chuoi (dung trong duong nong: moi su kien game). */
    public boolean unlocked(Player player, QuestDef q) {
        PlayerData pd = plugin.progress().data(player.getUniqueId());
        if (pd == null || !pd.loaded) return false;
        if (q.permission() != null && !q.permission().isBlank() && !player.hasPermission(q.permission())) return false;
        if (q.minLevel() > pd.level) return false;
        for (String rid : q.requires()) {
            QuestDef r = get(rid);
            if (r != null && !pd.claimed.contains(r.id() + "|" + periodKey(r))) return false;
        }
        return q.category() != QuestCategory.GUILD || pd.guildKey != null;
    }

    public QuestState state(Player player, QuestDef q) {
        if (lockOf(player, q) != null) return QuestState.LOCKED;
        PlayerData pd = plugin.progress().data(player.getUniqueId());
        if (pd.claimed.contains(q.id() + "|" + periodKey(q))) return QuestState.CLAIMED;
        return plugin.progress().isComplete(player, q) ? QuestState.CLAIMABLE : QuestState.IN_PROGRESS;
    }

    // ------------------------------------------------------------------ text
    public String describe(QuestDef q) {
        String key = "objective." + q.type().name();
        String tpl = plugin.messages().raw(key, "amount", Strings.number(q.amount()), "target", targetText(q));
        return tpl;
    }

    public String targetText(QuestDef q) {
        List<String> t = q.targets();
        if (t.isEmpty() || t.contains("*") || t.contains("ANY") || t.contains("any")) {
            return plugin.messages().raw("objective.any-target");
        }
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(3, t.size()); i++) shown.add(Strings.pretty(t.get(i)));
        return String.join(", ", shown) + (t.size() > 3 ? "..." : "");
    }
}
