package vn.astrax.quest.model;

import java.util.Locale;

public enum ResetRule {
    DAILY, WEEKLY, MONTHLY, NEVER, EVENT;

    public static ResetRule parse(String s, ResetRule def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
