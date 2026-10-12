package net.minecraft.stat;

import net.minecraft.item.Item;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.text.Text;

public class ItemStat extends Stat {
    private final Item item;

    public ItemStat(String key, String itemKey, Text name, Item item) {
        super(key + itemKey, name);
        this.item = item;
        int i = Item.getId(item);
        if (i != 0) {
            ScoreboardCriterion.BY_NAME.put(key + i, this.getCriterion());
        }
    }

    public Item getItem() {
        return this.item;
    }
}
