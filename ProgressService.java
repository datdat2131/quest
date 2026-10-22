package vn.astrax.quest.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.api.QuestCompleteEvent;
import vn.astrax.quest.model.QuestCategory;
import vn.astrax.quest.model.QuestDef;
import vn.astrax.quest.model.QuestType;
import vn.astrax.quest.storage.Storage;
import vn.astrax.quest.util.Strings;
import vn.astrax.quest.util.TargetMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Bo nho dem tien do (chi may chu quyet dinh tien do). Tien do duoc ghi vao DB theo lo (cong don),
 * hoan thanh duoc ghi ngay lap tuc.
 */
public final class ProgressService {

    public static final class Entry {
        public final String owner;
        public final String questId;
        public final String period;
        public final long target;
        public final AtomicLong value = new AtomicLong();
        public final AtomicLong pending = new AtomicLong();
        public volatile boolean completed;

        Entry(String owner, String questId, String period, long target) {
            this.owner = owner;
            this.questId = questId;
            this.period = period;
            this.target = target;
        }
    }

    private final AstraxQuestPlugin plugin;
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Entry>> owners = new ConcurrentHashMap<>();
    private final Set<String> loadingOwners = ConcurrentHashMap.newKeySet();

    public ProgressService(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerData data(UUID id) {
        return data.get(id);
    }

    public Iterable<PlayerData> allData() {
        return data.values();
    }

    // ------------------------------------------------------------------ load / unload
    public void load(Player player) {
        load(player, 0);
    }

    private void load(Player player, int attempt) {
        UUID id = player.getUniqueId();
        PlayerData pd = data.computeIfAbsent(id, k -> new PlayerData(id, player.getName()));
        plugin.storage().loadPlayer(id, player.getName(), plugin.quests().relevantPeriods()).whenComplete((snap, err) ->
                plugin.sync(() -> {
                    if (!player.isOnline()) {
                        data.remove(id);
                        return;
                    }
                    if (err != null) {
                        plugin.getLogger().log(Level.SEVERE, "Khong nap duoc du lieu cua " + player.getName(), err);
                        if (attempt < 2) {
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (player.isOnline()) load(player, attempt + 1);
                            }, 100L);
                        } else {
                            plugin.messages().send(player, "data-load-failed");
                        }
                        return;
                    }
                    pd.name = player.getName();
                    pd.level = snap.profile().level();
                    pd.exp = snap.profile().exp();
                    pd.tokens = snap.profile().tokens();
                    pd.title = plugin.titles().exists(snap.profile().title()) ? snap.profile().title() : null;
                    pd.titles.clear();
                    pd.titles.addAll(snap.titles());
                    pd.claimed.clear();
                    pd.claimed.addAll(snap.claims());
                    Map<String, Entry> map = new ConcurrentHashMap<>();
                    for (Storage.ProgressRow r : snap.progress()) addRow(map, id.toString(), r);
                    owners.put(id.toString(), map);
                    pd.loaded = true;
                    plugin.claims().deliverPending(player, snap.pending());
                    refreshGuild(player);
                }));
    }

    private void addRow(Map<String, Entry> map, String owner, Storage.ProgressRow r) {
        QuestDef q = plugin.quests().get(r.questId());
        if (q == null) return;
        Entry e = new Entry(owner, r.questId(), r.period(), q.amount());
        e.value.set(r.progress());
        e.completed = r.progress() >= q.amount();
        map.put(key(r.questId(), r.period()), e);
    }

    public void unload(Player player) {
        UUID id = player.getUniqueId();
        data.remove(id);
        Map<String, Entry> map = owners.remove(id.toString());
        plugin.antiCheat().forget(id);
        if (map == null) return;
        List<Storage.Delta> deltas = new ArrayList<>();
        for (Entry e : map.values()) {
            long d = e.pending.getAndSet(0);
            if (d > 0) deltas.add(new Storage.Delta(id.toString(), e.questId, e.period, d, e.target, e.target));
        }
        plugin.storage().flush(deltas).whenComplete((v, err) -> {
            if (err != null) plugin.getLogger().log(Level.SEVERE, "Mat tien do khi luu " + player.getName(), err);
        });
    }

    // ------------------------------------------------------------------ guild owners
    public void refreshGuild(Player player) {
        PlayerData pd = data.get(player.getUniqueId());
        if (pd == null) return;
        String key = plugin.guilds().keyOf(player);
        pd.guildKey = key;
        if (key != null) ensureOwner("guild:" + key);
    }

    private boolean ensureOwner(String owner) {
        if (owners.containsKey(owner)) return true;
        if (!loadingOwners.add(owner)) return false;
        plugin.storage().loadProgress(owner, plugin.quests().relevantPeriods()).whenComplete((rows, err) ->
                plugin.sync(() -> {
                    loadingOwners.remove(owner);
                    if (err != null) {
                        plugin.getLogger().log(Level.WARNING, "Khong nap duoc tien do " + owner, err);
                        return;
                    }
                    Map<String, Entry> map = new ConcurrentHashMap<>();
                    for (Storage.ProgressRow r : rows) addRow(map, owner, r);
                    owners.putIfAbsent(owner, map);
                }));
        return false;
    }

    // ------------------------------------------------------------------ queries
    private static String key(String questId, String period) {
        return questId + "|" + period;
    }

    public String ownerKey(Player p, QuestDef q) {
        if (q.category() == QuestCategory.GUILD) {
            PlayerData pd = data.get(p.getUniqueId());
            return pd == null || pd.guildKey == null ? null : "guild:" + pd.guildKey;
        }
        return p.getUniqueId().toString();
    }

    public Entry entry(String owner, QuestDef q, String period) {
        Map<String, Entry> m = owners.get(owner);
        return m == null ? null : m.get(key(q.id(), period));
    }

    public long value(Player p, QuestDef q) {
        String owner = ownerKey(p, q);
        if (owner == null) return 0;
        Entry e = entry(owner, q, plugin.quests().periodKey(q));
        return e == null ? 0 : Math.min(q.amount(), e.value.get());
    }

    public boolean isComplete(Player p, QuestDef q) {
        String owner = ownerKey(p, q);
        if (owner == null) return false;
        Entry e = entry(owner, q, plugin.quests().periodKey(q));
        return e != null && e.value.get() >= q.amount();
    }

    // ------------------------------------------------------------------ increments
    /** Diem vao chung cho moi su kien trong game, bridge va API. Phai goi tren luong chinh. */
    public void add(Player player, QuestType type, String target, long amount) {
        if (player == null || amount <= 0 || !player.isOnline()) return;
        var s = plugin.settings();
        if (s.ignoredModes.contains(player.getGameMode().name())) return;
        if (s.disabledWorlds.contains(player.getWorld().getName())) return;
        PlayerData pd = data.get(player.getUniqueId());
        if (pd == null || !pd.loaded) return;
        List<QuestDef> list = plugin.quests().byType(type);
        if (list.isEmpty()) return;
        for (QuestDef q : list) {
            if (!q.worlds().isEmpty() && !q.worlds().contains(player.getWorld().getName())) continue;
            if (!TargetMatcher.matches(q.targets(), target)) continue;
            if (!plugin.quests().isActive(q, pd.uuid)) continue;
            applyTo(player, pd, q, amount);
        }
    }

    private void applyTo(Player player, PlayerData pd, QuestDef q, long amount) {
        String period = plugin.quests().periodKey(q);
        if (pd.claimed.contains(key(q.id(), period))) return;
        if (!plugin.quests().unlocked(player, q)) return;
        String owner = ownerKey(player, q);
        if (owner == null) return;
        if (!ensureOwner(owner)) return;
        Map<String, Entry> map = owners.get(owner);
        if (map == null) return;
        Entry e = map.computeIfAbsent(key(q.id(), period), k -> new Entry(owner, q.id(), period, q.amount()));
        if (e.completed) return;
        long before = e.value.get();
        long add = Math.min(amount, q.amount() - before);
        if (add <= 0) return;
        long now = e.value.addAndGet(add);
        e.pending.addAndGet(add);
        if (now >= q.amount()) {
            e.completed = true;
            onComplete(player, pd, q, e);
        } else if (plugin.settings().actionBar) {
            long t = System.currentTimeMillis();
            if (t - pd.lastBarMs > 1500) {
                pd.lastBarMs = t;
                player.sendActionBar(vn.astrax.quest.util.Text.color(plugin.messages().raw("progress-bar",
                        "quest", q.name(), "current", Strings.number(now), "max", Strings.number(q.amount()),
                        "percent", Strings.percent(now, q.amount()))));
            }
        }
    }

    private void onComplete(Player player, PlayerData pd, QuestDef q, Entry e) {
        flushEntry(e);
        plugin.messages().send(player, "quest-complete", "quest", vn.astrax.quest.util.Text.strip(q.name()));
        playSound(player, plugin.settings().soundComplete);
        if (q.category() == QuestCategory.GUILD && pd.guildKey != null) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                PlayerData od = data.get(other.getUniqueId());
                if (od != null && pd.guildKey.equals(od.guildKey)) {
                    plugin.messages().send(other, "guild-quest-complete", "quest",
                            vn.astrax.quest.util.Text.strip(q.name()), "player", player.getName());
                }
            }
        }
        Bukkit.getPluginManager().callEvent(new QuestCompleteEvent(player, q));
        if (q.autoClaim()) plugin.claims().claim(player, q, null);
    }

    private void flushEntry(Entry e) {
        long d = e.pending.getAndSet(0);
        if (d <= 0) return;
        plugin.storage().flush(List.of(new Storage.Delta(e.owner, e.questId, e.period, d, e.target, e.target)))
                .whenComplete((v, err) -> {
                    if (err != null) {
                        e.pending.addAndGet(d);
                        plugin.getLogger().log(Level.WARNING, "Ghi tien do that bai, se thu lai", err);
                    }
                });
    }

    public static void playSound(Player p, String key) {
        if (key == null || key.isBlank() || key.equalsIgnoreCase("none")) return;
        p.playSound(p.getLocation(), key, 1f, 1f);
    }

    // ------------------------------------------------------------------ admin helpers
    public void forceAdd(Player p, QuestDef q, long amount) {
        PlayerData pd = data.get(p.getUniqueId());
        if (pd == null || !pd.loaded) return;
        applyTo(p, pd, q, amount);
    }

    public void forgetPlayerEntries(UUID id, Set<String> questIds) {
        Map<String, Entry> m = owners.get(id.toString());
        if (m == null) return;
        m.values().removeIf(e -> questIds.isEmpty() || questIds.contains(e.questId));
        PlayerData pd = data.get(id);
        if (pd != null) pd.claimed.removeIf(k -> questIds.isEmpty() || questIds.contains(k.substring(0, k.indexOf('|'))));
    }

    // ------------------------------------------------------------------ flush
    private record Taken(Entry entry, long delta) {
    }

    public void flushAll(boolean blocking) {
        List<Taken> taken = new ArrayList<>();
        for (Map<String, Entry> m : owners.values()) {
            for (Entry e : m.values()) {
                long d = e.pending.getAndSet(0);
                if (d > 0) taken.add(new Taken(e, d));
            }
        }
        if (taken.isEmpty()) return;
        List<Storage.Delta> deltas = new ArrayList<>();
        for (Taken t : taken) {
            deltas.add(new Storage.Delta(t.entry.owner, t.entry.questId, t.entry.period, t.delta, t.entry.target, t.entry.target));
        }
        var f = plugin.storage().flush(deltas);
        f.whenComplete((v, err) -> {
            if (err != null) {
                for (Taken t : taken) t.entry.pending.addAndGet(t.delta);
                plugin.getLogger().log(Level.WARNING, "Ghi lo tien do that bai, se thu lai", err);
            }
        });
        if (blocking) {
            try {
                f.get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the luu tien do khi tat plugin", e);
            }
        }
    }
}
