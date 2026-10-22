package vn.astrax.quest.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.astrax.quest.model.QuestDef;
import vn.astrax.quest.model.Reward;

/** Phat ra SAU khi phan thuong da duoc ghi nhan thanh cong (khong the huy). */
public final class QuestClaimEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final QuestDef quest;
    private final Reward reward;

    public QuestClaimEvent(Player player, QuestDef quest, Reward reward) {
        this.player = player;
        this.quest = quest;
        this.reward = reward;
    }

    public Player getPlayer() {
        return player;
    }

    public QuestDef getQuest() {
        return quest;
    }

    public Reward getReward() {
        return reward;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
