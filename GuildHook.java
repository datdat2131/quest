package vn.astrax.quest.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vn.astrax.quest.AstraxQuestPlugin;

import java.util.Locale;
import java.util.function.Function;

/**
 * Xac dinh guild cua nguoi choi: (1) provider dang ky qua API, hoac (2) placeholder PlaceholderAPI
 * (mac dinh %guild_name%) - dung duoc voi moi plugin Guild co expansion PAPI.
 */
public final class GuildHook {
    private final AstraxQuestPlugin plugin;
    private volatile Function<Player, String> provider;

    public GuildHook(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void setProvider(Function<Player, String> provider) {
        this.provider = provider;
    }

    /** @return khoa guild (chu thuong) hoac null neu khong co guild. */
    public String keyOf(Player p) {
        try {
            String raw = null;
            Function<Player, String> prov = provider;
            if (prov != null) {
                raw = prov.apply(p);
            } else {
                String ph = plugin.settings().guildPlaceholder;
                if (plugin.settings().papiEnabled && ph != null && !ph.isBlank()
                        && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    raw = PapiBridge.parse(p, ph);
                    if (ph.equals(raw)) raw = null; // placeholder khong duoc phan giai
                }
            }
            if (raw == null) return null;
            String plain = vn.astrax.quest.util.Text.strip(raw).trim();
            if (plain.isEmpty()) return null;
            String lower = plain.toLowerCase(Locale.ROOT);
            if (plugin.settings().guildNoneValues.contains(lower)) return null;
            return lower.length() > 48 ? lower.substring(0, 48) : lower;
        } catch (Exception e) {
            plugin.getLogger().fine("Loi doc guild cua " + p.getName() + ": " + e);
            return null;
        }
    }
}
