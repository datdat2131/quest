package vn.astrax.quest.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.model.QuestCategory;
import vn.astrax.quest.model.QuestDef;
import vn.astrax.quest.model.QuestState;
import vn.astrax.quest.service.PlayerData;
import vn.astrax.quest.util.Strings;
import vn.astrax.quest.util.Text;

import java.time.Instant;
import java.util.Locale;

/**
 * %astraxquest_level% %astraxquest_exp% %astraxquest_exp_next% %astraxquest_tokens% %astraxquest_title%
 * %astraxquest_title_plain% %astraxquest_claimable% %astraxquest_claimed_total%
 * %astraxquest_{daily|weekly|story|guild|pvp|event}_{done|total|claimable|reset}%
 * %astraxquest_progress_{questId}% %astraxquest_percent_{questId}%
 */
public final class AQExpansion extends PlaceholderExpansion {
    private final AstraxQuestPlugin plugin;

    public AQExpansion(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "astraxquest";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Astrax";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer op, @NotNull String params) {
        if (op == null) return "";
        PlayerData pd = plugin.progress().data(op.getUniqueId());
        if (pd == null || !pd.loaded) return "0";
        Player p = op.getPlayer();
        String k = params.toLowerCase(Locale.ROOT);
        var lc = plugin.settings().levels;
        switch (k) {
            case "level":
                return String.valueOf(pd.level);
            case "exp":
                return String.valueOf(lc.expIntoLevel(pd.exp));
            case "exp_total":
                return String.valueOf(pd.exp);
            case "exp_next":
                return String.valueOf(lc.expToNext(pd.exp));
            case "tokens":
                return String.valueOf(pd.tokens);
            case "title":
                return pd.title == null ? "" : Text.legacy(plugin.titles().display(pd.title));
            case "title_plain":
                return pd.title == null ? "" : Text.strip(plugin.titles().display(pd.title));
            case "title_id":
                return pd.title == null ? "" : pd.title;
            case "claimed_total":
                return String.valueOf(pd.claimed.size());
            case "claimable": {
                if (p == null) return "0";
                int n = 0;
                for (QuestCategory c : QuestCategory.values()) n += count(p, c, QuestState.CLAIMABLE);
                return String.valueOf(n);
            }
            default:
                break;
        }
        int us = k.indexOf('_');
        if (us > 0) {
            String head = k.substring(0, us);
            String tail = k.substring(us + 1);
            if (head.equals("progress") || head.equals("percent")) {
                QuestDef q = plugin.quests().get(params.substring(us + 1));
                if (q == null || p == null) return "0";
                long v = plugin.progress().value(p, q);
                return head.equals("progress") ? String.valueOf(v) : String.valueOf(Strings.percent(v, q.amount()));
            }
            QuestCategory c = QuestCategory.parse(head);
            if (c != null && p != null) {
                switch (tail) {
                    case "done":
                        return String.valueOf(count(p, c, QuestState.CLAIMED));
                    case "total":
                        return String.valueOf(plugin.quests().listFor(p, c).size());
                    case "claimable":
                        return String.valueOf(count(p, c, QuestState.CLAIMABLE));
                    case "reset": {
                        Instant next = plugin.quests().nextReset(c);
                        return next == null ? "-" : Strings.duration(Math.max(0, next.getEpochSecond() - Instant.now().getEpochSecond()));
                    }
                    default:
                        break;
                }
            }
        }
        return null;
    }

    private int count(Player p, QuestCategory c, QuestState st) {
        int n = 0;
        for (QuestDef q : plugin.quests().listFor(p, c)) if (plugin.quests().state(p, q) == st) n++;
        return n;
    }
}
