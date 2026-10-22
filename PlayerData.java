package vn.astrax.quest.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bo nho dem du lieu nguoi choi dang online. */
public final class PlayerData {
    public final UUID uuid;
    public volatile String name;
    public volatile int level = 1;
    public volatile long exp;
    public volatile long tokens;
    public volatile String title;
    public final Set<String> titles = ConcurrentHashMap.newKeySet();
    /** Khoa "questId|period" cua cac nhiem vu da nhan thuong. */
    public final Set<String> claimed = ConcurrentHashMap.newKeySet();
    public final Set<String> inflight = ConcurrentHashMap.newKeySet();
    public volatile boolean loaded;
    public volatile String guildKey;
    public volatile long lastBarMs;
    public volatile long lastClaimMs;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
