package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedPicker;

public class LootEntry extends WeightedPicker.Entry {
    private ItemStack item;
    private int minItemChance;
    private int maxItemChance;

    public LootEntry(Item item, int metadata, int minItemChance, int maxItemChance, int weight) {
        super(weight);
        this.item = new ItemStack(item, 1, metadata);
        this.minItemChance = minItemChance;
        this.maxItemChance = maxItemChance;
    }

    public LootEntry(ItemStack item, int minItemChance, int maxItemChance, int weight) {
        super(weight);
        this.item = item;
        this.minItemChance = minItemChance;
        this.maxItemChance = maxItemChance;
    }

    public static void addLoot(Random random, List<LootEntry> entries, Inventory inventory, int amount) {
        for (int i = 0; i < amount; i++) {
            LootEntry lootentry = WeightedPicker.pick(random, entries);
            int j = lootentry.minItemChance + random.nextInt(lootentry.maxItemChance - lootentry.minItemChance + 1);
            if (lootentry.item.getMaxSize() >= j) {
                ItemStack itemstack1 = lootentry.item.copy();
                itemstack1.size = j;
                inventory.setItem(random.nextInt(inventory.getSize()), itemstack1);
            } else {
                for (int k = 0; k < j; k++) {
                    ItemStack itemstack = lootentry.item.copy();
                    itemstack.size = 1;
                    inventory.setItem(random.nextInt(inventory.getSize()), itemstack);
                }
            }
        }
    }

    public static void addLoot(Random random, List<LootEntry> entries, DispenserBlockEntity dispenser, int amount) {
        for (int i = 0; i < amount; i++) {
            LootEntry lootentry = WeightedPicker.pick(random, entries);
            int j = lootentry.minItemChance + random.nextInt(lootentry.maxItemChance - lootentry.minItemChance + 1);
            if (lootentry.item.getMaxSize() >= j) {
                ItemStack itemstack1 = lootentry.item.copy();
                itemstack1.size = j;
                dispenser.setItem(random.nextInt(dispenser.getSize()), itemstack1);
            } else {
                for (int k = 0; k < j; k++) {
                    ItemStack itemstack = lootentry.item.copy();
                    itemstack.size = 1;
                    dispenser.setItem(random.nextInt(dispenser.getSize()), itemstack);
                }
            }
        }
    }

    public static List<LootEntry> addAll(List<LootEntry> entries, LootEntry... moreEntries) {
        List<LootEntry> list = Lists.newArrayList(entries);
        Collections.addAll(list, moreEntries);
        return list;
    }
}
