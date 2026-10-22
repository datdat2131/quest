package vn.astrax.quest.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vn.astrax.quest.model.ItemSpec;

import java.util.List;

public final class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material == null || material.isAir() ? Material.STONE : material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder amount(int n) {
        item.setAmount(Math.max(1, Math.min(64, n)));
        return this;
    }

    public ItemBuilder name(String s) {
        if (meta != null && s != null) meta.displayName(Text.color(s));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) meta.lore(Text.colorAll(lines));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (meta != null && glow) meta.setEnchantmentGlintOverride(true);
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                    ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static Material material(String name, Material def) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name.trim());
        return m == null || m.isAir() ? def : m;
    }

    /** Tao vat pham thuong that su (khong an flag); null neu material sai. */
    public static ItemStack fromSpec(ItemSpec spec) {
        Material m = Material.matchMaterial(spec.material());
        if (m == null || m.isAir() || !m.isItem()) return null;
        ItemStack is = new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize() * 36, spec.amount())));
        ItemMeta im = is.getItemMeta();
        if (im != null) {
            if (spec.name() != null && !spec.name().isBlank()) im.displayName(Text.color(spec.name()));
            if (spec.lore() != null && !spec.lore().isEmpty()) im.lore(Text.colorAll(spec.lore()));
            if (spec.glow()) im.setEnchantmentGlintOverride(true);
            is.setItemMeta(im);
        }
        return is;
    }
}
