package vn.astrax.quest.model;

import java.util.Locale;

/** Loai muc tieu. Cac loai tu OPEN_BOXMINE tro xuong do plugin ngoai (bridge/API) day tien do vao. */
public enum QuestType {
    BREAK_BLOCK, PLACE_BLOCK, HARVEST, KILL_MOB, KILL_PLAYER, FISH, CRAFT, SMELT, BREED, ENCHANT, CONSUME,
    WALK, PLAYTIME,
    OPEN_BOXMINE, AUCTION_SELL, AUCTION_BUY, GUILD_DONATE, CUSTOM;

    public static QuestType parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
