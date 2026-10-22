package vn.astrax.quest.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Tach rieng de lop chinh khong phu thuoc PlaceholderAPI khi plugin do vang mat. */
final class PapiBridge {
    private PapiBridge() {
    }

    static String parse(Player p, String text) {
        return PlaceholderAPI.setPlaceholders(p, text);
    }
}
