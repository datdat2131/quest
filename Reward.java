package vn.astrax.quest.model;

import java.util.List;

public record Reward(double money, long tokens, long exp, List<ItemSpec> items, List<String> commands, String title) {
    public static final Reward EMPTY = new Reward(0, 0, 0, List.of(), List.of(), null);

    public boolean isEmpty() {
        return money <= 0 && tokens <= 0 && exp <= 0 && items.isEmpty() && commands.isEmpty() && title == null;
    }
}
