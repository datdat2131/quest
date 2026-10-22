package vn.astrax.quest.model;

import java.util.Map;

public record CategorySettings(
        QuestCategory category,
        boolean enabled,
        String displayName,
        String icon,
        ResetRule reset,
        boolean autoClaim,
        Map<String, Integer> pick,
        boolean perPlayer,
        String eventId) {
}
