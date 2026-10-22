package vn.astrax.quest.api;

import org.bukkit.entity.Player;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.model.QuestType;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** API cong khai cho plugin khac (BoxMine, Guild, Cho dau gia...). Goi tren luong chinh cua server. */
public final class AstraxQuestAPI {
    private AstraxQuestAPI() {
    }

    private static AstraxQuestPlugin plugin() {
        AstraxQuestPlugin p = AstraxQuestPlugin.instance();
        if (p == null) throw new IllegalStateException("AstraxQuest chua duoc bat");
        return p;
    }

    /** Day tien do. {@code target} la ten muc tieu (vd ten hop BoxMine, id vat pham dau gia). */
    public static void addProgress(Player player, QuestType type, String target, long amount) {
        plugin().progress().add(player, type, target == null ? "ANY" : target, amount);
    }

    /** Dang ky nha cung cap guild: tra ve ten/id guild cua nguoi choi, hoac null neu khong co. */
    public static void setGuildProvider(Function<Player, String> provider) {
        plugin().guilds().setProvider(provider);
    }

    public static long getTokens(UUID uuid) {
        var pd = plugin().progress().data(uuid);
        return pd == null ? 0 : pd.tokens;
    }

    public static CompletableFuture<Long> addTokens(UUID uuid, String name, long delta, String reason) {
        return plugin().profiles().addTokens(uuid, name, delta, reason);
    }

    /** Tru token nguyen tu: tra ve so du moi hoac -1 neu khong du. */
    public static CompletableFuture<Long> takeTokens(UUID uuid, String name, long amount, String reason) {
        return plugin().profiles().addTokens(uuid, name, -Math.abs(amount), reason);
    }

    public static int getLevel(UUID uuid) {
        var pd = plugin().progress().data(uuid);
        return pd == null ? 1 : pd.level;
    }
}
