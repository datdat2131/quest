package vn.astrax.quest.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vn.astrax.quest.AstraxQuestPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Token / EXP / danh hieu: thay doi DB truoc, roi cap nhat bo nho dem, va luon ghi log giao dich. */
public final class ProfileService {
    private final AstraxQuestPlugin plugin;

    public ProfileService(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    private String nameOf(UUID id, String name) {
        if (name != null) return name;
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "unknown" : n;
    }

    /** @return so du moi, hoac -1 neu tru vuot qua so du. */
    public CompletableFuture<Long> addTokens(UUID id, String name, long delta, String reason) {
        String n = nameOf(id, name);
        return plugin.storage().ensurePlayer(id, n)
                .thenCompose(v -> plugin.storage().addTokens(id, delta))
                .thenApply(bal -> {
                    if (bal >= 0) {
                        plugin.sync(() -> {
                            PlayerData pd = plugin.progress().data(id);
                            if (pd != null) pd.tokens = bal;
                        });
                        plugin.storage().logTx(id, n, null, null, delta >= 0 ? "TOKEN_ADD" : "TOKEN_TAKE",
                                0, delta, 0, null, "OK", reason);
                    }
                    return bal;
                });
    }

    public CompletableFuture<Void> addExp(UUID id, String name, long delta, String reason) {
        String n = nameOf(id, name);
        return plugin.storage().ensurePlayer(id, n)
                .thenCompose(v -> plugin.storage().addExp(id, delta))
                .thenAccept(ch -> {
                    plugin.storage().logTx(id, n, null, null, delta >= 0 ? "EXP_ADD" : "EXP_TAKE",
                            0, 0, delta, null, "OK", reason);
                    plugin.sync(() -> {
                        PlayerData pd = plugin.progress().data(id);
                        if (pd != null) {
                            pd.exp = ch.exp();
                            pd.level = ch.newLevel();
                        }
                        Player p = Bukkit.getPlayer(id);
                        if (p != null && ch.newLevel() > ch.oldLevel()) plugin.claims().announceLevelUp(p, ch.newLevel());
                    });
                });
    }

    public CompletableFuture<Boolean> giveTitle(UUID id, String name, String titleId) {
        String n = nameOf(id, name);
        return plugin.storage().ensurePlayer(id, n)
                .thenCompose(v -> plugin.storage().unlockTitle(id, titleId))
                .thenApply(added -> {
                    plugin.storage().logTx(id, n, null, null, "TITLE_GIVE", 0, 0, 0, titleId, "OK", "admin");
                    plugin.sync(() -> {
                        PlayerData pd = plugin.progress().data(id);
                        if (pd != null) pd.titles.add(titleId);
                    });
                    return added;
                });
    }

    public CompletableFuture<Boolean> takeTitle(UUID id, String name, String titleId) {
        String n = nameOf(id, name);
        return plugin.storage().removeTitle(id, titleId).thenApply(removed -> {
            plugin.storage().logTx(id, n, null, null, "TITLE_TAKE", 0, 0, 0, titleId, "OK", "admin");
            plugin.sync(() -> {
                PlayerData pd = plugin.progress().data(id);
                if (pd != null) {
                    pd.titles.remove(titleId);
                    if (titleId.equals(pd.title)) pd.title = null;
                }
            });
            return removed;
        });
    }

    /** Chon danh hieu dang deo (null de go). Yeu cau da so huu. */
    public boolean select(Player player, String titleIdOrNull) {
        PlayerData pd = plugin.progress().data(player.getUniqueId());
        if (pd == null || !pd.loaded) return false;
        if (titleIdOrNull != null && !pd.titles.contains(titleIdOrNull)) return false;
        String old = pd.title;
        pd.title = titleIdOrNull;
        plugin.storage().setTitle(player.getUniqueId(), titleIdOrNull).whenComplete((v, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Khong luu duoc danh hieu", err);
                plugin.sync(() -> pd.title = old);
            }
        });
        return true;
    }
}
