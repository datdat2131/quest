package vn.astrax.quest.service;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.api.QuestClaimEvent;
import vn.astrax.quest.model.ItemSpec;
import vn.astrax.quest.model.QuestCategory;
import vn.astrax.quest.model.QuestDef;
import vn.astrax.quest.model.QuestState;
import vn.astrax.quest.model.Reward;
import vn.astrax.quest.storage.Storage;
import vn.astrax.quest.util.ItemBuilder;
import vn.astrax.quest.util.Strings;
import vn.astrax.quest.util.Text;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Nhan thuong. Chong nhan trung o 3 lop: (1) khoa "inflight" tren luong chinh, (2) bo nho dem da nhan,
 * (3) PRIMARY KEY (uuid, quest, period) trong DB + giao dich nguyen tu.
 */
public final class ClaimService {
    public enum Status { SUCCESS, NOT_READY, LOCKED, ALREADY, BUSY, ERROR }

    private final AstraxQuestPlugin plugin;

    public ClaimService(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ text helpers
    public List<String> describe(Reward r) {
        var m = plugin.messages();
        List<String> out = new ArrayList<>();
        if (r.money() > 0) out.add(m.raw("reward.money", "amount", Strings.money(r.money())));
        if (r.tokens() > 0) out.add(m.raw("reward.tokens", "amount", Strings.number(r.tokens())));
        if (r.exp() > 0) out.add(m.raw("reward.exp", "amount", Strings.number(r.exp())));
        for (ItemSpec i : r.items()) {
            String name = i.name() != null && !i.name().isBlank() ? i.name() : Strings.pretty(i.material());
            out.add(m.raw("reward.item", "amount", i.amount(), "item", name));
        }
        if (r.title() != null) {
            String d = plugin.titles().display(r.title());
            out.add(m.raw("reward.title", "title", d.isEmpty() ? r.title() : d));
        }
        return out;
    }

    private String itemsSummary(Reward r) {
        if (r.items().isEmpty()) return null;
        List<String> p = new ArrayList<>();
        for (ItemSpec i : r.items()) p.add(i.material() + "x" + i.amount());
        return String.join(",", p);
    }

    // ------------------------------------------------------------------ claim
    public void claim(Player player, QuestDef q, Consumer<Status> done) {
        Status s = claimInternal(player, q, done);
        if (s != null && done != null) done.accept(s);
    }

    /** @return trang thai neu ket thuc ngay (loi/khong du dieu kien), null neu dang xu ly bat dong bo. */
    private Status claimInternal(Player player, QuestDef q, Consumer<Status> done) {
        var msg = plugin.messages();
        PlayerData pd = plugin.progress().data(player.getUniqueId());
        if (pd == null || !pd.loaded) {
            msg.send(player, "data-loading");
            return Status.ERROR;
        }
        if (!plugin.quests().isActive(q, pd.uuid)) {
            msg.send(player, "quest-inactive");
            return Status.LOCKED;
        }
        String lock = plugin.quests().lockOf(player, q);
        if (lock != null) {
            msg.send(player, "quest-locked", "reason", lock);
            return Status.LOCKED;
        }
        String period = plugin.quests().periodKey(q);
        String ck = q.id() + "|" + period;
        if (pd.claimed.contains(ck)) {
            msg.send(player, "already-claimed");
            return Status.ALREADY;
        }
        if (!plugin.progress().isComplete(player, q)) {
            msg.send(player, "not-complete");
            return Status.NOT_READY;
        }
        String owner = plugin.progress().ownerKey(player, q);
        if (owner == null) {
            msg.send(player, "quest-locked", "reason", msg.raw("lock.guild"));
            return Status.LOCKED;
        }
        if (!pd.inflight.add(ck)) return Status.BUSY;

        ProgressService.Entry e = plugin.progress().entry(owner, q, period);
        long pending = e == null ? 0 : e.pending.getAndSet(0);
        Reward r = q.reward();
        UUID uuid = player.getUniqueId();
        var req = new Storage.ClaimRequest(uuid, player.getName(), owner, q.id(), period, pending, q.amount(), q.amount(),
                r.money(), r.tokens(), r.exp(), r.title(), itemsSummary(r), q.category().name());

        plugin.storage().claim(req).whenComplete((res, err) -> plugin.sync(() -> {
            pd.inflight.remove(ck);
            Status status;
            if (err != null) {
                if (e != null && pending > 0) e.pending.addAndGet(pending);
                plugin.getLogger().log(Level.SEVERE, "Loi nhan thuong " + q.id() + " cua " + player.getName(), err);
                msg.send(player, "claim-error");
                status = Status.ERROR;
            } else if (res.outcome() == Storage.ClaimOutcome.ALREADY_CLAIMED) {
                pd.claimed.add(ck);
                msg.send(player, "already-claimed");
                status = Status.ALREADY;
            } else if (res.outcome() == Storage.ClaimOutcome.NOT_COMPLETE) {
                plugin.getLogger().warning("DB tu choi nhan thuong (tien do chua du): " + player.getName() + " / " + q.id());
                msg.send(player, "not-complete");
                status = Status.NOT_READY;
            } else {
                onSuccess(player, pd, q, r, ck, res);
                status = Status.SUCCESS;
            }
            if (done != null) done.accept(status);
        }));
        return null;
    }

    private void onSuccess(Player player, PlayerData pd, QuestDef q, Reward r, String ck, Storage.ClaimResult res) {
        pd.claimed.add(ck);
        pd.level = res.newLevel();
        pd.exp = res.exp();
        pd.tokens = res.tokens();
        if (r.title() != null) pd.titles.add(r.title());

        deliver(player.getUniqueId(), player.getName(), q, r, res.txId());

        plugin.messages().send(player, "claim-success", "quest", Text.strip(q.name()),
                "rewards", String.join("&7, ", describe(r)));
        ProgressService.playSound(player, plugin.settings().soundClaim);
        if (res.newLevel() > res.oldLevel()) announceLevelUp(player, res.newLevel());
        Bukkit.getPluginManager().callEvent(new QuestClaimEvent(player, q, r));
    }

    public void announceLevelUp(Player player, int level) {
        var m = plugin.messages();
        player.showTitle(Title.title(m.get("level-up.title"), m.get("level-up.subtitle", "level", level),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
        m.send(player, "level-up.chat", "level", level);
        ProgressService.playSound(player, plugin.settings().soundLevelUp);
    }

    // ------------------------------------------------------------------ claim all
    public void claimAll(Player player, QuestCategory cat, Runnable done) {
        List<QuestDef> todo = new ArrayList<>();
        for (QuestDef q : plugin.quests().listFor(player, cat)) {
            if (plugin.quests().state(player, q) == QuestState.CLAIMABLE) todo.add(q);
        }
        if (todo.isEmpty()) {
            plugin.messages().send(player, "nothing-to-claim");
            if (done != null) done.run();
            return;
        }
        claimNext(player, todo, 0, done);
    }

    private void claimNext(Player player, List<QuestDef> todo, int idx, Runnable done) {
        if (idx >= todo.size() || !player.isOnline()) {
            if (done != null) done.run();
            return;
        }
        claim(player, todo.get(idx), st -> claimNext(player, todo, idx + 1, done));
    }

    // ------------------------------------------------------------------ delivery (main thread)
    private void deliver(UUID uuid, String name, QuestDef q, Reward r, long txId) {
        List<String> failed = new ArrayList<>();
        try {
            if (r.money() > 0 && !plugin.economy().deposit(uuid, name, r.money())) failed.add("money");
            for (ItemSpec spec : r.items()) {
                ItemStack is = ItemBuilder.fromSpec(spec);
                if (is == null) {
                    failed.add("item:" + spec.material());
                    continue;
                }
                giveItem(uuid, is);
            }
            for (String cmd : r.commands()) {
                String c = Text.replace(cmd, "player", name, "uuid", uuid, "quest", q.id());
                if (c.startsWith("/")) c = c.substring(1);
                try {
                    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c)) failed.add("cmd:" + c);
                } catch (Exception ex) {
                    failed.add("cmd:" + c);
                    plugin.getLogger().log(Level.WARNING, "Lenh thuong loi: " + c, ex);
                }
            }
        } catch (Exception ex) {
            failed.add("exception:" + ex.getClass().getSimpleName());
            plugin.getLogger().log(Level.SEVERE, "Loi giao thuong cho " + name, ex);
        }
        if (failed.isEmpty()) {
            plugin.storage().updateTx(txId, "DELIVERED", null);
        } else {
            plugin.getLogger().severe("Giao thuong that bai mot phan cho " + name + " (tx " + txId + "): " + failed);
            plugin.storage().updateTx(txId, "PARTIAL", String.join(";", failed));
        }
    }

    private void giveItem(UUID uuid, ItemStack is) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) {
            storePending(uuid, is);
            return;
        }
        var left = p.getInventory().addItem(is);
        for (ItemStack rest : left.values()) {
            if (plugin.settings().fullInventoryPending) {
                storePending(uuid, rest);
                plugin.messages().send(p, "inventory-full-pending");
            } else {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
                plugin.messages().send(p, "inventory-full-dropped");
            }
        }
    }

    private void storePending(UUID uuid, ItemStack is) {
        String payload = Base64.getEncoder().encodeToString(is.serializeAsBytes());
        plugin.storage().addPending(uuid, payload).whenComplete((id, err) -> {
            if (err != null) plugin.getLogger().log(Level.SEVERE, "Khong luu duoc vat pham cho " + uuid + ": " + is, err);
        });
    }

    public void deliverPending(Player player, List<Storage.Pending> list) {
        for (Storage.Pending p : list) {
            plugin.storage().deletePending(p.id()).whenComplete((deleted, err) -> {
                if (err != null || !Boolean.TRUE.equals(deleted)) return;
                plugin.sync(() -> {
                    try {
                        ItemStack is = ItemStack.deserializeBytes(Base64.getDecoder().decode(p.payload()));
                        var left = player.getInventory().addItem(is);
                        for (ItemStack rest : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
                        plugin.messages().send(player, "pending-delivered");
                    } catch (Exception ex) {
                        plugin.getLogger().log(Level.SEVERE, "Vat pham cho bi hong (id " + p.id() + ")", ex);
                    }
                });
            });
        }
    }
}
