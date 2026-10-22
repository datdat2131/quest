package vn.astrax.quest.model;

import java.time.Instant;

public record EventDef(String id, String name, boolean enabled, Instant start, Instant end, String keySuffix) {
    public boolean activeAt(Instant now) {
        return enabled && !now.isBefore(start) && now.isBefore(end);
    }
}
