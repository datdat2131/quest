package vn.astrax.quest.model;

import java.util.Locale;

public enum QuestCategory {
    DAILY, WEEKLY, STORY, GUILD, PVP, EVENT;

    public static QuestCategory parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
