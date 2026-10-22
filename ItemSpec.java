package vn.astrax.quest.model;

import java.util.List;

/** Mo ta vat pham thuong (khong phu thuoc Bukkit de de test). */
public record ItemSpec(String material, int amount, String name, List<String> lore, boolean glow) {
}
