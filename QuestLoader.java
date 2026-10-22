package vn.astrax.quest.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.astrax.quest.model.CategorySettings;
import vn.astrax.quest.model.EventDef;
import vn.astrax.quest.model.ItemSpec;
import vn.astrax.quest.model.QuestCategory;
import vn.astrax.quest.model.QuestDef;
import vn.astrax.quest.model.QuestType;
import vn.astrax.quest.model.ResetRule;
import vn.astrax.quest.model.Reward;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Doc quests/*.yml + events.yml, kiem tra hop le va bo qua muc loi (kem canh bao). */
public final class QuestLoader {
    public record Result(Map<QuestCategory, CategorySettings> categories, Map<String, QuestDef> quests,
                         Map<String, EventDef> events) {
    }

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_\\-]{1,60}");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private QuestLoader() {
    }

    public static Result load(File folder, ZoneId zone, Logger log) {
        Map<String, EventDef> events = loadEvents(new File(folder, "events.yml"), zone, log);
        Map<QuestCategory, CategorySettings> cats = new EnumMap<>(QuestCategory.class);
        Map<String, QuestDef> quests = new LinkedHashMap<>();

        File dir = new File(folder, "quests");
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName));
        int counter = 0;

        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String base = f.getName().substring(0, f.getName().length() - 4);
            QuestCategory cat = QuestCategory.parse(y.getString("settings.category", base));
            if (cat == null) {
                log.warning("[" + f.getName() + "] khong xac dinh duoc loai nhiem vu (settings.category). Bo qua file.");
                continue;
            }
            CategorySettings cs = cats.computeIfAbsent(cat, k -> readSettings(y, cat));
            ConfigurationSection qs = y.getConfigurationSection("quests");
            if (qs == null) continue;
            for (String id : qs.getKeys(false)) {
                ConfigurationSection s = qs.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) continue;
                String where = "[" + f.getName() + ":" + id + "] ";
                if (!ID.matcher(id).matches()) {
                    log.warning(where + "id chi duoc chua chu/so/_/- (toi da 60 ky tu). Bo qua.");
                    continue;
                }
                if (quests.containsKey(id)) {
                    log.warning(where + "id bi trung voi nhiem vu khac. Bo qua.");
                    continue;
                }
                QuestType type = QuestType.parse(s.getString("type"));
                if (type == null) {
                    log.warning(where + "type khong hop le: " + s.getString("type"));
                    continue;
                }
                long amount = s.getLong("amount", 1);
                if (amount < 1) {
                    log.warning(where + "amount phai >= 1");
                    continue;
                }
                ResetRule reset = ResetRule.parse(s.getString("reset"), cs.reset());
                if (cat == QuestCategory.EVENT) reset = ResetRule.EVENT;
                String eventId = s.getString("event", cs.eventId());
                if (reset == ResetRule.EVENT && (eventId == null || !events.containsKey(eventId))) {
                    log.warning(where + "nhiem vu su kien can 'event' tro toi su kien co trong events.yml");
                    continue;
                }
                String icon = s.getString("icon", "PAPER");
                Material im = Material.matchMaterial(icon);
                if (im == null || im.isAir() || !im.isItem()) {
                    log.warning(where + "icon khong hop le '" + icon + "', dung PAPER");
                    icon = "PAPER";
                }
                List<String> targets = new ArrayList<>(s.getStringList("targets"));
                if (s.isString("target")) targets.add(s.getString("target"));
                Reward reward = readReward(s.getConfigurationSection("rewards"), where, log);
                quests.put(id, new QuestDef(id, cat,
                        s.getString("name", id),
                        s.getStringList("description"),
                        icon,
                        s.getString("group", "default"),
                        type, List.copyOf(targets), amount, reset, reward,
                        List.copyOf(s.getStringList("requires")),
                        s.getInt("min-level", 0),
                        s.getString("permission"),
                        List.copyOf(s.getStringList("worlds")),
                        s.getBoolean("auto-claim", cs.autoClaim()),
                        reset == ResetRule.EVENT ? eventId : null,
                        s.getString("chapter"),
                        s.getBoolean("hidden", false),
                        s.getInt("order", ++counter)));
            }
        }
        for (QuestDef q : quests.values()) {
            for (String r : q.requires()) {
                if (!quests.containsKey(r)) log.warning("Nhiem vu '" + q.id() + "' yeu cau '" + r + "' khong ton tai.");
            }
        }
        for (QuestCategory c : QuestCategory.values()) {
            cats.computeIfAbsent(c, k -> new CategorySettings(c, false, c.name(), "PAPER", defaultReset(c), false,
                    Map.of(), false, null));
        }
        return new Result(cats, quests, events);
    }

    private static ResetRule defaultReset(QuestCategory c) {
        return switch (c) {
            case DAILY, PVP -> ResetRule.DAILY;
            case WEEKLY, GUILD -> ResetRule.WEEKLY;
            case STORY -> ResetRule.NEVER;
            case EVENT -> ResetRule.EVENT;
        };
    }

    private static CategorySettings readSettings(YamlConfiguration y, QuestCategory cat) {
        ConfigurationSection s = y.getConfigurationSection("settings");
        Map<String, Integer> pick = new LinkedHashMap<>();
        if (s != null && s.getConfigurationSection("pick") != null) {
            ConfigurationSection p = s.getConfigurationSection("pick");
            for (String k : p.getKeys(false)) pick.put(k, p.getInt(k));
        }
        ResetRule reset = ResetRule.parse(s == null ? null : s.getString("reset"), defaultReset(cat));
        return new CategorySettings(cat,
                s == null || s.getBoolean("enabled", true),
                s == null ? cat.name() : s.getString("display-name", cat.name()),
                s == null ? "PAPER" : s.getString("icon", "PAPER"),
                reset,
                s != null && s.getBoolean("auto-claim", false),
                pick,
                s != null && s.getBoolean("per-player", false),
                s == null ? null : s.getString("event"));
    }

    private static Reward readReward(ConfigurationSection r, String where, Logger log) {
        if (r == null) return Reward.EMPTY;
        List<ItemSpec> items = new ArrayList<>();
        List<?> raw = r.getList("items");
        if (raw != null) {
            for (Object o : raw) {
                ItemSpec spec = parseItem(o);
                Material m = spec == null ? null : Material.matchMaterial(spec.material());
                if (spec == null || m == null || m.isAir() || !m.isItem()) {
                    log.warning(where + "vat pham thuong khong hop le: " + o);
                    continue;
                }
                items.add(spec);
            }
        }
        String title = r.getString("title");
        return new Reward(Math.max(0, r.getDouble("money", 0)), Math.max(0, r.getLong("tokens", 0)),
                Math.max(0, r.getLong("exp", 0)), List.copyOf(items), List.copyOf(r.getStringList("commands")),
                title == null || title.isBlank() ? null : title);
    }

    /** "DIAMOND:3" hoac map {material, amount, name, lore, glow}. */
    private static ItemSpec parseItem(Object o) {
        if (o instanceof String s) {
            String[] p = s.split(":");
            int amt = 1;
            if (p.length > 1) {
                try {
                    amt = Integer.parseInt(p[1].trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return new ItemSpec(p[0].trim(), amt, null, List.of(), false);
        }
        if (o instanceof Map<?, ?> m) {
            Object mat = m.get("material");
            if (mat == null) return null;
            int amt = 1;
            if (m.get("amount") instanceof Number n) amt = n.intValue();
            List<String> lore = new ArrayList<>();
            if (m.get("lore") instanceof List<?> l) for (Object x : l) lore.add(String.valueOf(x));
            return new ItemSpec(String.valueOf(mat), amt, m.get("name") == null ? null : String.valueOf(m.get("name")),
                    lore, Boolean.parseBoolean(String.valueOf(m.get("glow"))));
        }
        return null;
    }

    private static Map<String, EventDef> loadEvents(File f, ZoneId zone, Logger log) {
        Map<String, EventDef> map = new LinkedHashMap<>();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection s = y.getConfigurationSection("events");
        if (s == null) return map;
        for (String id : s.getKeys(false)) {
            ConfigurationSection e = s.getConfigurationSection(id);
            if (e == null) continue;
            try {
                LocalDateTime start = LocalDateTime.parse(e.getString("start", ""), FMT);
                LocalDateTime end = LocalDateTime.parse(e.getString("end", ""), FMT);
                if (!end.isAfter(start)) {
                    log.warning("[events.yml:" + id + "] end phai sau start");
                    continue;
                }
                map.put(id, new EventDef(id, e.getString("name", id), e.getBoolean("enabled", true),
                        start.atZone(zone).toInstant(), end.atZone(zone).toInstant(),
                        start.toLocalDate().toString()));
            } catch (DateTimeParseException ex) {
                log.warning("[events.yml:" + id + "] start/end phai co dang yyyy-MM-dd HH:mm");
            }
        }
        return map;
    }
}
