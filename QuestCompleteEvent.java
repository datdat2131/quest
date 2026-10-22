package vn.astrax.quest.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.astrax.quest.model.QuestDef;

/** Phat ra khi nguoi choi hoan thanh muc tieu (chua nhan thuong). */
public final class QuestCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final QuestDef quest;

    public QuestCompleteEvent(Player player, QuestDef quest) {
        this.player = player;
        this.quest = quest;
    }

    public Player getPlayer() {
        return player;
    }

    public QuestDef getQuest() {
        return quest;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
