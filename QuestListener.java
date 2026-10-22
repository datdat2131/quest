package vn.astrax.quest.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import vn.astrax.quest.AstraxQuestPlugin;
import vn.astrax.quest.model.QuestType;

/** Chuyen su kien Minecraft thanh tien do nhiem vu (da loc gian lan). */
public final class QuestListener implements Listener {
    private final AstraxQuestPlugin plugin;

    public QuestListener(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean counts(Player p) {
        GameMode m = p.getGameMode();
        return m == GameMode.SURVIVAL || m == GameMode.ADVENTURE;
    }

    // ------------------------------------------------------------------ session
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        plugin.progress().load(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.progress().unload(e.getPlayer());
    }

    // ------------------------------------------------------------------ blocks
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        boolean natural = plugin.antiCheat().onBreakCounts(b);
        if (!counts(p) || !natural) return;
        if (b.getBlockData() instanceof Ageable a) {
            if (a.getAge() >= a.getMaximumAge()) {
                plugin.progress().add(p, QuestType.HARVEST, b.getType().name(), 1);
            }
            return;
        }
        plugin.progress().add(p, QuestType.BREAK_BLOCK, b.getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        boolean fresh = plugin.antiCheat().onPlace(e.getBlockPlaced());
        if (!counts(p) || !fresh) return;
        plugin.progress().add(p, QuestType.PLACE_BLOCK, e.getBlockPlaced().getType().name(), 1);
    }

    // ------------------------------------------------------------------ mobs / pvp
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        plugin.antiCheat().tagSpawn(e.getEntity(), e.getSpawnReason().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player) return;
        Player killer = dead.getKiller();
        if (killer == null || !counts(killer)) return;
        if (!plugin.antiCheat().mobCounts(dead)) return;
        plugin.progress().add(killer, QuestType.KILL_MOB, dead.getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || !counts(killer)) return;
        if (!plugin.antiCheat().pvpCounts(killer, victim)) return;
        plugin.progress().add(killer, QuestType.KILL_PLAYER, "PLAYER", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p && counts(p)) {
            plugin.progress().add(p, QuestType.BREED, e.getEntityType().name(), 1);
        }
    }

    // ------------------------------------------------------------------ items
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !counts(e.getPlayer())) return;
        Entity caught = e.getCaught();
        if (caught instanceof Item item) {
            plugin.progress().add(e.getPlayer(), QuestType.FISH, item.getItemStack().getType().name(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !counts(p)) return;
        ItemStack result = e.getRecipe().getResult();
        if (result.getType().isAir()) return;
        int perCraft = Math.max(1, result.getAmount());
        int crafts = 1;
        if (e.isShiftClick()) {
            int min = Integer.MAX_VALUE;
            for (ItemStack s : e.getInventory().getMatrix()) {
                if (s != null && !s.getType().isAir()) min = Math.min(min, s.getAmount());
            }
            crafts = min == Integer.MAX_VALUE ? 0 : min;
            int space = 0;
            int max = result.getMaxStackSize();
            for (ItemStack s : p.getInventory().getStorageContents()) {
                if (s == null || s.getType().isAir()) space += max;
                else if (s.isSimilar(result)) space += Math.max(0, max - s.getAmount());
            }
            crafts = Math.min(crafts, space / perCraft);
        }
        if (crafts > 0) plugin.progress().add(p, QuestType.CRAFT, result.getType().name(), (long) crafts * perCraft);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent e) {
        Player p = e.getPlayer();
        if (!counts(p) || e.getItemAmount() <= 0) return;
        plugin.progress().add(p, QuestType.SMELT, e.getItemType().name(), e.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        Player p = e.getEnchanter();
        if (!counts(p)) return;
        Material m = e.getItem().getType();
        plugin.progress().add(p, QuestType.ENCHANT, m.name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!counts(e.getPlayer())) return;
        plugin.progress().add(e.getPlayer(), QuestType.CONSUME, e.getItem().getType().name(), 1);
    }
}
