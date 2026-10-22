package vn.astrax.quest.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.model.QuestCategory;
import vn.astrax.quest.model.QuestDef;
import vn.astrax.quest.model.QuestType;
import vn.astrax.quest.service.PlayerData;
import vn.astrax.quest.util.Strings;
import vn.astrax.quest.util.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class QuestCommand implements TabExecutor {
    private final AstraxQuestPlugin plugin;

    public QuestCommand(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String key, Object... kv) {
        plugin.messages().send(s, key, kv);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!sender.hasPermission("astraxquest.use")) {
            msg(sender, "no-permission");
            return true;
        }
        if (a.length == 0) {
            if (sender instanceof Player p) plugin.menus().openMain(p);
            else msg(sender, "player-only");
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        QuestCategory cat = QuestCategory.parse(sub);
        if (cat != null) {
            if (sender instanceof Player p) plugin.menus().openCategory(p, cat, 0);
            else msg(sender, "player-only");
            return true;
        }
        switch (sub) {
            case "help" -> sendList(sender, sender.hasPermission("astraxquest.admin") ? "help.admin" : "help.player");
            case "menu" -> {
                if (sender instanceof Player p) plugin.menus().openMain(p);
                else msg(sender, "player-only");
            }
            case "profile" -> profile(sender);
            case "tokens" -> tokens(sender);
            case "top" -> top(sender);
            case "claim" -> claim(sender, a);
            case "claimall" -> claimAll(sender, a);
            case "title" -> title(sender, a);
            case "admin" -> {
                if (!sender.hasPermission("astraxquest.admin")) msg(sender, "no-permission");
                else admin(sender, a);
            }
            default -> msg(sender, "unknown-sub");
        }
        return true;
    }

    private void sendList(CommandSender s, String key) {
        for (String l : plugin.messages().list(key)) s.sendMessage(Text.color(l));
    }

    // ------------------------------------------------------------------ player commands
    private void profile(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg(sender, "player-only");
            return;
        }
        PlayerData pd = plugin.progress().data(p.getUniqueId());
        if (pd == null || !pd.loaded) {
            msg(sender, "data-loading");
            return;
        }
        var lc = plugin.settings().levels;
        String title = pd.title == null ? plugin.messages().raw("none") : plugin.titles().display(pd.title);
        for (String l : plugin.messages().list("profile", "level", pd.level, "exp", Strings.number(lc.expIntoLevel(pd.exp)),
                "need", pd.level >= lc.maxLevel() ? "MAX" : Strings.number(lc.costOf(pd.level)),
                "tokens", Strings.number(pd.tokens), "title", title, "claimed", pd.claimed.size())) {
            sender.sendMessage(Text.color(l));
        }
    }

    private void tokens(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg(sender, "player-only");
            return;
        }
        PlayerData pd = plugin.progress().data(p.getUniqueId());
        msg(sender, "tokens-balance", "tokens", Strings.number(pd == null ? 0 : pd.tokens));
    }

    private void top(CommandSender sender) {
        if (!sender.hasPermission("astraxquest.top")) {
            msg(sender, "no-permission");
            return;
        }
        plugin.storage().top(10).whenComplete((rows, err) -> plugin.sync(() -> {
            if (err != null) {
                msg(sender, "claim-error");
                return;
            }
            msg(sender, "top.header");
            int i = 1;
            for (var r : rows) {
                msg(sender, "top.line", "rank", i++, "name", r.name(), "level", r.level(), "exp", Strings.number(r.exp()));
            }
        }));
    }

    private void claim(CommandSender sender, String[] a) {
        if (!(sender instanceof Player p)) {
            msg(sender, "player-only");
            return;
        }
        if (a.length < 2) {
            msg(sender, "usage.claim");
            return;
        }
        QuestDef q = plugin.quests().get(a[1]);
        if (q == null) {
            msg(sender, "quest-not-found");
            return;
        }
        plugin.claims().claim(p, q, null);
    }

    private void claimAll(CommandSender sender, String[] a) {
        if (!(sender instanceof Player p)) {
            msg(sender, "player-only");
            return;
        }
        if (a.length >= 2) {
            QuestCategory c = QuestCategory.parse(a[1]);
            if (c == null) {
                msg(sender, "usage.claimall");
                return;
            }
            plugin.claims().claimAll(p, c, null);
            return;
        }
        for (QuestCategory c : QuestCategory.values()) plugin.claims().claimAll(p, c, null);
    }

    private void title(CommandSender sender, String[] a) {
        if (!(sender instanceof Player p)) {
            msg(sender, "player-only");
            return;
        }
        if (a.length < 2) {
            plugin.menus().openTitles(p, 0);
            return;
        }
        if (a[1].equalsIgnoreCase("off")) {
            plugin.profiles().select(p, null);
            msg(sender, "title-removed");
            return;
        }
        if (plugin.profiles().select(p, a[1])) {
            msg(sender, "title-selected", "title", plugin.titles().display(a[1]));
        } else {
            msg(sender, "title-not-owned");
        }
    }

    // ------------------------------------------------------------------ admin
    private OfflinePlayer resolve(CommandSender s, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        if (off == null) msg(s, "player-not-found", "player", name);
        return off;
    }

    private Player online(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) msg(s, "player-not-online", "player", name);
        return p;
    }

    private static Long parseLong(String s) {
        try {
            long v = Long.parseLong(s);
            return v > 0 && v <= 1_000_000_000_000L ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void admin(CommandSender sender, String[] a) {
        if (a.length < 2) {
            sendList(sender, "help.admin");
            return;
        }
        String op = a[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "admin.reloaded", "quests", plugin.quests().all().size());
            }
            case "events" -> {
                for (var e : plugin.quests().events().values()) {
                    msg(sender, "admin.events-line", "id", e.id(), "name", e.name(),
                            "active", plugin.quests().eventActive(e.id()) ? "&aBAT" : "&cTAT",
                            "start", e.start(), "end", e.end());
                }
            }
            case "reset" -> adminReset(sender, a);
            case "addprogress", "complete" -> adminProgress(sender, a, op.equals("complete"));
            case "trigger" -> adminTrigger(sender, a);
            case "token", "exp" -> adminCurrency(sender, a, op.equals("token"));
            case "title" -> adminTitle(sender, a);
            case "audit" -> adminAudit(sender, a);
            default -> sendList(sender, "help.admin");
        }
    }

    private void adminReset(CommandSender sender, String[] a) {
        if (a.length < 3) {
            msg(sender, "usage.admin-reset");
            return;
        }
        OfflinePlayer t = resolve(sender, a[2]);
        if (t == null) return;
        Set<String> ids = new HashSet<>();
        String scope = a.length > 3 ? a[3] : "all";
        if (!scope.equalsIgnoreCase("all")) {
            QuestCategory c = QuestCategory.parse(scope);
            if (c != null) {
                for (QuestDef q : plugin.quests().all()) if (q.category() == c) ids.add(q.id());
            } else if (plugin.quests().get(scope) != null) {
                ids.add(scope);
            } else {
                msg(sender, "quest-not-found");
                return;
            }
        }
        UUID id = t.getUniqueId();
        plugin.storage().resetQuests(id, ids).whenComplete((v, err) -> plugin.sync(() -> {
            if (err != null) {
                msg(sender, "claim-error");
                plugin.getLogger().warning("Reset that bai: " + err);
                return;
            }
            plugin.progress().forgetPlayerEntries(id, ids);
            plugin.storage().logTx(id, t.getName(), null, null, "ADMIN_RESET", 0, 0, 0, null, "OK",
                    sender.getName() + " -> " + scope);
            msg(sender, "admin.reset-done", "player", a[2], "scope", scope);
        }));
    }

    private void adminProgress(CommandSender sender, String[] a, boolean complete) {
        if (a.length < (complete ? 4 : 5)) {
            msg(sender, complete ? "usage.admin-complete" : "usage.admin-addprogress");
            return;
        }
        Player t = online(sender, a[2]);
        if (t == null) return;
        QuestDef q = plugin.quests().get(a[3]);
        if (q == null) {
            msg(sender, "quest-not-found");
            return;
        }
        long amount = q.amount();
        if (!complete) {
            Long v = parseLong(a[4]);
            if (v == null) {
                msg(sender, "admin.invalid-number");
                return;
            }
            amount = v;
        }
        plugin.progress().forceAdd(t, q, amount);
        plugin.storage().logTx(t.getUniqueId(), t.getName(), q.id(), plugin.quests().periodKey(q), "ADMIN_PROGRESS", 0, 0, 0,
                null, "OK", sender.getName() + " +" + amount);
        msg(sender, "admin.progress-added", "player", t.getName(), "quest", q.id(), "amount", Strings.number(amount));
    }

    private void adminTrigger(CommandSender sender, String[] a) {
        if (a.length < 6) {
            msg(sender, "usage.admin-trigger");
            return;
        }
        Player t = online(sender, a[2]);
        if (t == null) return;
        QuestType type = QuestType.parse(a[3]);
        Long amount = parseLong(a[5]);
        if (type == null || amount == null) {
            msg(sender, "admin.invalid-number");
            return;
        }
        plugin.progress().add(t, type, a[4], amount);
        msg(sender, "admin.triggered", "player", t.getName(), "type", type.name(), "target", a[4], "amount", amount);
    }

    private void adminCurrency(CommandSender sender, String[] a, boolean token) {
        if (a.length < 5) {
            msg(sender, token ? "usage.admin-token" : "usage.admin-exp");
            return;
        }
        String mode = a[2].toLowerCase(Locale.ROOT);
        if (!mode.equals("give") && !mode.equals("take")) {
            msg(sender, token ? "usage.admin-token" : "usage.admin-exp");
            return;
        }
        OfflinePlayer t = resolve(sender, a[3]);
        if (t == null) return;
        Long v = parseLong(a[4]);
        if (v == null) {
            msg(sender, "admin.invalid-number");
            return;
        }
        long delta = mode.equals("give") ? v : -v;
        String reason = "admin:" + sender.getName();
        if (token) {
            plugin.profiles().addTokens(t.getUniqueId(), t.getName(), delta, reason).whenComplete((bal, err) -> plugin.sync(() -> {
                if (err != null) msg(sender, "claim-error");
                else if (bal < 0) msg(sender, "admin.token-insufficient", "player", a[3]);
                else msg(sender, "admin.token-done", "mode", mode, "player", a[3], "amount", Strings.number(v),
                            "balance", Strings.number(bal));
            }));
        } else {
            plugin.profiles().addExp(t.getUniqueId(), t.getName(), delta, reason).whenComplete((x, err) -> plugin.sync(() -> {
                if (err != null) msg(sender, "claim-error");
                else msg(sender, "admin.exp-done", "mode", mode, "player", a[3], "amount", Strings.number(v));
            }));
        }
    }

    private void adminTitle(CommandSender sender, String[] a) {
        if (a.length < 5) {
            msg(sender, "usage.admin-title");
            return;
        }
        String mode = a[2].toLowerCase(Locale.ROOT);
        OfflinePlayer t = resolve(sender, a[3]);
        if (t == null) return;
        if (!plugin.titles().exists(a[4])) {
            msg(sender, "admin.title-unknown");
            return;
        }
        var f = mode.equals("give")
                ? plugin.profiles().giveTitle(t.getUniqueId(), t.getName(), a[4])
                : plugin.profiles().takeTitle(t.getUniqueId(), t.getName(), a[4]);
        f.whenComplete((changed, err) -> plugin.sync(() -> {
            if (err != null) msg(sender, "claim-error");
            else msg(sender, "admin.title-done", "mode", mode, "player", a[3], "title", a[4], "changed", changed);
        }));
    }

    private void adminAudit(CommandSender sender, String[] a) {
        if (a.length < 3) {
            msg(sender, "usage.admin-audit");
            return;
        }
        OfflinePlayer t = resolve(sender, a[2]);
        if (t == null) return;
        int limit = 10;
        if (a.length > 3) {
            Long v = parseLong(a[3]);
            if (v != null) limit = (int) Math.min(50, v);
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm:ss");
        fmt.setTimeZone(java.util.TimeZone.getTimeZone(plugin.settings().zone));
        plugin.storage().recentTx(t.getUniqueId(), limit).whenComplete((rows, err) -> plugin.sync(() -> {
            if (err != null) {
                msg(sender, "claim-error");
                return;
            }
            if (rows.isEmpty()) {
                msg(sender, "admin.audit-empty");
                return;
            }
            msg(sender, "admin.audit-header", "player", a[2]);
            for (var r : rows) {
                msg(sender, "admin.audit-line", "id", r.id(), "time", fmt.format(new Date(r.at())), "action", r.action(),
                        "quest", r.questId() == null ? "-" : r.questId(), "money", Strings.money(r.money()),
                        "tokens", r.tokens(), "exp", r.exp(), "status", r.status());
            }
        }));
    }

    // ------------------------------------------------------------------ tab
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("astraxquest.admin");
        if (a.length == 1) {
            for (QuestCategory c : QuestCategory.values()) out.add(c.key());
            out.addAll(List.of("menu", "profile", "tokens", "top", "claim", "claimall", "title", "help"));
            if (admin) out.add("admin");
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "claim" -> plugin.quests().all().forEach(q -> out.add(q.id()));
                case "claimall" -> {
                    for (QuestCategory c : QuestCategory.values()) out.add(c.key());
                }
                case "title" -> {
                    out.add("off");
                    plugin.titles().all().forEach(t -> out.add(t.id()));
                }
                case "admin" -> {
                    if (admin) out.addAll(List.of("reload", "reset", "addprogress", "complete", "trigger", "token", "exp",
                            "title", "audit", "events"));
                }
                default -> {
                }
            }
        } else if (admin && a[0].equalsIgnoreCase("admin")) {
            String op = a[1].toLowerCase(Locale.ROOT);
            if (a.length == 3) {
                if (op.equals("token") || op.equals("exp") || op.equals("title")) out.addAll(List.of("give", "take"));
                else Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (a.length == 4) {
                switch (op) {
                    case "reset" -> {
                        out.add("all");
                        for (QuestCategory c : QuestCategory.values()) out.add(c.key());
                        plugin.quests().all().forEach(q -> out.add(q.id()));
                    }
                    case "addprogress", "complete" -> plugin.quests().all().forEach(q -> out.add(q.id()));
                    case "trigger" -> {
                        for (QuestType t : QuestType.values()) out.add(t.name());
                    }
                    case "token", "exp", "title" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    default -> {
                    }
                }
            } else if (a.length == 5 && op.equals("title")) {
                plugin.titles().all().forEach(t -> out.add(t.id()));
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
