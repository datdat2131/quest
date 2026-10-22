package vn.astrax.quest.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.config.TitleManager;
import vn.astrax.quest.model.CategorySettings;
import vn.astrax.quest.model.QuestCategory;
import vn.astrax.quest.model.QuestDef;
import vn.astrax.quest.model.QuestState;
import vn.astrax.quest.service.PlayerData;
import vn.astrax.quest.util.ItemBuilder;
import vn.astrax.quest.util.Strings;
import vn.astrax.quest.util.Text;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Toan bo GUI (menu chinh, danh sach nhiem vu theo muc, danh hieu). Bo cuc/van ban nam trong gui.yml. */
public final class MenuManager implements Listener {

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] DEFAULT_QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final AstraxQuestPlugin plugin;
    private YamlConfiguration gui = new YamlConfiguration();

    public MenuManager(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        gui = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gui.yml"));
        try (var in = plugin.getResource("gui.yml")) {
            if (in != null) {
                gui.setDefaults(YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Khong doc duoc gui.yml mac dinh: " + e.getMessage());
        }
    }

    private String s(String path, Object... kv) {
        return Text.replace(gui.getString(path, ""), kv);
    }

    private ItemStack filler() {
        return new ItemBuilder(ItemBuilder.material(gui.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE))
                .name(gui.getString("filler.name", " ")).build();
    }

    private Inventory create(Holder h, int rows, String title) {
        Inventory inv = Bukkit.createInventory(h, rows * 9, Text.color(title));
        h.inventory = inv;
        ItemStack f = filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
        return inv;
    }

    private void put(Holder h, int slot, ItemStack item, Consumer<ClickType> action) {
        if (slot < 0 || slot >= h.inventory.getSize()) return;
        h.inventory.setItem(slot, item);
        if (action != null) h.actions.put(slot, action);
        else h.actions.remove(slot);
    }

    // ------------------------------------------------------------------ main menu
    public void openMain(Player player) {
        PlayerData pd = plugin.progress().data(player.getUniqueId());
        if (pd == null || !pd.loaded) {
            plugin.messages().send(player, "data-loading");
            return;
        }
        Holder h = new Holder();
        create(h, 6, s("main.title"));

        var lc = plugin.settings().levels;
        long into = lc.expIntoLevel(pd.exp);
        long cost = lc.costOf(pd.level);
        boolean max = pd.level >= lc.maxLevel();
        String bar = Strings.bar(max ? 1 : into, max ? 1 : cost, 20, "&a\u258c", "&7\u258c");
        String titleText = pd.title == null ? s("main.no-title") : plugin.titles().display(pd.title);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(player);
            sm.displayName(Text.color(s("main.profile-name", "player", player.getName())));
            List<String> lore = new ArrayList<>();
            for (String l : gui.getStringList("main.profile-lore")) {
                lore.add(Text.replace(l, "level", pd.level, "exp", Strings.number(into),
                        "need", max ? "MAX" : Strings.number(cost), "bar", bar,
                        "tokens", Strings.number(pd.tokens), "title", titleText));
            }
            sm.lore(Text.colorAll(lore));
            head.setItemMeta(sm);
        }
        put(h, gui.getInt("main.profile-slot", 4), head, null);

        for (QuestCategory cat : QuestCategory.values()) {
            CategorySettings cs = plugin.quests().category(cat);
            if (cs == null || !cs.enabled()) continue;
            ConfigurationSection c = gui.getConfigurationSection("categories." + cat.name());
            if (c == null) continue;
            List<QuestDef> list = plugin.quests().listFor(player, cat);
            int done = 0;
            int claimable = 0;
            for (QuestDef q : list) {
                QuestState st = plugin.quests().state(player, q);
                if (st == QuestState.CLAIMED) done++;
                else if (st == QuestState.CLAIMABLE) claimable++;
            }
            Instant next = plugin.quests().nextReset(cat);
            String reset = next == null ? s("reset-none")
                    : Strings.duration(Math.max(0, next.getEpochSecond() - Instant.now().getEpochSecond()));
            List<String> lore = Text.replaceAll(c.getStringList("lore"), "done", done, "total", list.size(),
                    "claimable", claimable, "reset", reset);
            ItemBuilder b = new ItemBuilder(ItemBuilder.material(c.getString("material"), Material.PAPER))
                    .name(c.getString("name", cat.name())).lore(lore).glow(claimable > 0);
            put(h, c.getInt("slot", 20), b.build(), click -> openCategory(player, cat, 0));
        }

        put(h, gui.getInt("main.titles-slot", 40), new ItemBuilder(Material.NAME_TAG)
                .name(s("main.titles-name")).lore(gui.getStringList("main.titles-lore")).build(),
                click -> openTitles(player, 0));
        put(h, gui.getInt("main.close-slot", 49), new ItemBuilder(Material.BARRIER).name(s("close-name")).build(),
                click -> player.closeInventory());
        player.openInventory(h.inventory);
    }

    // ------------------------------------------------------------------ category menu
    public void openCategory(Player player, QuestCategory cat, int page) {
        PlayerData pd = plugin.progress().data(player.getUniqueId());
        if (pd == null || !pd.loaded) {
            plugin.messages().send(player, "data-loading");
            return;
        }
        CategorySettings cs = plugin.quests().category(cat);
        if (cs == null || !cs.enabled()) {
            plugin.messages().send(player, "category-disabled");
            return;
        }
        List<QuestDef> list = plugin.quests().listFor(player, cat);
        List<Integer> slotList = gui.getIntegerList("quest-menu.slots");
        int[] slots = slotList.isEmpty() ? DEFAULT_QUEST_SLOTS : slotList.stream().mapToInt(Integer::intValue).toArray();
        int pages = Math.max(1, (list.size() + slots.length - 1) / slots.length);
        int pg = Math.max(0, Math.min(page, pages - 1));

        Holder h = new Holder();
        create(h, 6, s("quest-menu.title", "category", cs.displayName(), "page", pg + 1, "pages", pages));

        for (int i = 0; i < slots.length; i++) {
            int idx = pg * slots.length + i;
            if (idx >= list.size()) break;
            QuestDef q = list.get(idx);
            put(h, slots[i], questItem(player, pd, q), click -> {
                long now = System.currentTimeMillis();
                if (now - pd.lastClaimMs < plugin.settings().claimCooldownMs) return;
                pd.lastClaimMs = now;
                plugin.claims().claim(player, q, st -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                        openCategory(player, cat, pg);
                    }
                });
            });
        }

        put(h, gui.getInt("quest-menu.back-slot", 45), new ItemBuilder(Material.ARROW).name(s("back-name")).build(),
                click -> openMain(player));
        if (pg > 0) {
            put(h, gui.getInt("quest-menu.prev-slot", 48), new ItemBuilder(Material.ARROW).name(s("prev-name")).build(),
                    click -> openCategory(player, cat, pg - 1));
        }
        if (pg < pages - 1) {
            put(h, gui.getInt("quest-menu.next-slot", 50), new ItemBuilder(Material.ARROW).name(s("next-name")).build(),
                    click -> openCategory(player, cat, pg + 1));
        }
        Instant next = plugin.quests().nextReset(cat);
        String reset = next == null ? s("reset-none")
                : Strings.duration(Math.max(0, next.getEpochSecond() - Instant.now().getEpochSecond()));
        put(h, gui.getInt("quest-menu.info-slot", 49), new ItemBuilder(Material.CLOCK).name(s("quest-menu.info-name"))
                .lore(Text.replaceAll(gui.getStringList("quest-menu.info-lore"), "reset", reset, "total", list.size())).build(), null);
        put(h, gui.getInt("quest-menu.claim-all-slot", 53), new ItemBuilder(Material.CHEST).name(s("quest-menu.claim-all-name"))
                .lore(gui.getStringList("quest-menu.claim-all-lore")).glow(true).build(), click -> {
            long now = System.currentTimeMillis();
            if (now - pd.lastClaimMs < plugin.settings().claimCooldownMs) return;
            pd.lastClaimMs = now;
            plugin.claims().claimAll(player, cat, () -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                    openCategory(player, cat, pg);
                }
            });
        });
        player.openInventory(h.inventory);
    }

    private ItemStack questItem(Player player, PlayerData pd, QuestDef q) {
        QuestState st = plugin.quests().state(player, q);
        long cur = plugin.progress().value(player, q);
        String status;
        String color;
        Material mat = ItemBuilder.material(q.icon(), Material.PAPER);
        switch (st) {
            case LOCKED -> {
                String reason = plugin.quests().lockOf(player, q);
                status = s("quest-item.status.locked", "reason", reason == null ? "" : reason);
                color = s("quest-item.color.locked");
                mat = ItemBuilder.material(gui.getString("quest-item.locked-material"), Material.BARRIER);
            }
            case CLAIMED -> {
                status = s("quest-item.status.claimed");
                color = s("quest-item.color.claimed");
            }
            case CLAIMABLE -> {
                status = s("quest-item.status.claimable");
                color = s("quest-item.color.claimable");
            }
            default -> {
                status = s("quest-item.status.progress");
                color = s("quest-item.color.progress");
            }
        }
        List<String> lore = new ArrayList<>();
        for (String d : q.description()) lore.add(s("quest-item.desc-prefix") + d);
        if (!q.description().isEmpty()) lore.add(" ");
        lore.add(s("quest-item.objective", "objective", plugin.quests().describe(q)));
        if (st != QuestState.LOCKED) {
            long shown = st == QuestState.CLAIMED ? q.amount() : cur;
            lore.add(s("quest-item.progress", "bar", Strings.bar(shown, q.amount(), 20, "&a\u258c", "&7\u258c"),
                    "current", Strings.number(shown), "max", Strings.number(q.amount()),
                    "percent", Strings.percent(shown, q.amount())));
        }
        List<String> rewards = plugin.claims().describe(q.reward());
        if (!rewards.isEmpty()) {
            lore.add(" ");
            lore.add(s("quest-item.rewards-title"));
            for (String r : rewards) lore.add(s("quest-item.reward-prefix") + r);
        }
        if (q.category() == QuestCategory.GUILD && pd.guildKey != null) {
            lore.add(" ");
            lore.add(s("quest-item.guild-note", "guild", pd.guildKey));
        }
        lore.add(" ");
        lore.add(status);
        return new ItemBuilder(mat).name(color + q.name()).lore(lore).glow(st == QuestState.CLAIMABLE).build();
    }

    // ------------------------------------------------------------------ titles menu
    public void openTitles(Player player, int page) {
        PlayerData pd = plugin.progress().data(player.getUniqueId());
        if (pd == null || !pd.loaded) {
            plugin.messages().send(player, "data-loading");
            return;
        }
        List<TitleManager.TitleDef> all = new ArrayList<>(plugin.titles().all());
        int[] slots = DEFAULT_QUEST_SLOTS;
        int pages = Math.max(1, (all.size() + slots.length - 1) / slots.length);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder();
        create(h, 6, s("titles-menu.title", "page", pg + 1, "pages", pages));

        for (int i = 0; i < slots.length; i++) {
            int idx = pg * slots.length + i;
            if (idx >= all.size()) break;
            TitleManager.TitleDef t = all.get(idx);
            boolean owned = pd.titles.contains(t.id());
            boolean selected = t.id().equals(pd.title);
            List<String> lore = new ArrayList<>();
            if (!t.description().isBlank()) lore.add(s("titles-menu.desc-prefix") + t.description());
            lore.add(" ");
            lore.add(selected ? s("titles-menu.selected") : owned ? s("titles-menu.click-select") : s("titles-menu.locked"));
            ItemBuilder b = new ItemBuilder(owned ? ItemBuilder.material(t.material(), Material.NAME_TAG) : Material.GRAY_DYE)
                    .name(owned ? t.display() : s("titles-menu.locked-name")).lore(lore).glow(selected);
            put(h, slots[i], b.build(), owned ? click -> {
                if (selected) {
                    plugin.profiles().select(player, null);
                    plugin.messages().send(player, "title-removed");
                } else if (plugin.profiles().select(player, t.id())) {
                    plugin.messages().send(player, "title-selected", "title", t.display());
                }
                openTitles(player, pg);
            } : null);
        }
        put(h, 45, new ItemBuilder(Material.ARROW).name(s("back-name")).build(), click -> openMain(player));
        if (pg > 0) put(h, 48, new ItemBuilder(Material.ARROW).name(s("prev-name")).build(), click -> openTitles(player, pg - 1));
        if (pg < pages - 1) put(h, 50, new ItemBuilder(Material.ARROW).name(s("next-name")).build(), click -> openTitles(player, pg + 1));
        player.openInventory(h.inventory);
    }

    // ------------------------------------------------------------------ events
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
        Consumer<ClickType> a = h.actions.get(e.getSlot());
        if (a == null) return;
        try {
            a.accept(e.getClick());
        } catch (Exception ex) {
            plugin.getLogger().warning("Loi GUI: " + ex);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
