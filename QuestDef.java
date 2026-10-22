package vn.astrax.quest.model;

import java.util.List;

public record QuestDef(
        String id,
        QuestCategory category,
        String name,
        List<String> description,
        String icon,
        String group,
        QuestType type,
        List<String> targets,
        long amount,
        ResetRule reset,
        Reward reward,
        List<String> requires,
        int minLevel,
        String permission,
        List<String> worlds,
        boolean autoClaim,
        String eventId,
        String chapter,
        boolean hidden,
        int order) {
}
