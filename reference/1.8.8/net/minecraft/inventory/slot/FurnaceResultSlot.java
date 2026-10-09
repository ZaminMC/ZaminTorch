package net.minecraft.inventory.slot;

import net.minecraft.crafting.SmeltingManager;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.math.MathHelper;

public class FurnaceResultSlot extends InventorySlot {
    private PlayerEntity player;
    private int removeAmount;

    public FurnaceResultSlot(PlayerEntity player, Inventory resultInventory, int slot, int x, int y) {
        super(resultInventory, slot, x, y);
        this.player = player;
    }

    @Override
    public boolean isItemAllowed(ItemStack item) {
        return false;
    }

    @Override
    public ItemStack removeItem(int amount) {
        if (this.hasItem()) {
            this.removeAmount = this.removeAmount + Math.min(amount, this.getItem().size);
        }

        return super.removeItem(amount);
    }

    @Override
    public void onItemRemoved(PlayerEntity player, ItemStack item) {
        this.checkAchievements(item);
        super.onItemRemoved(player, item);
    }

    @Override
    protected void onQuickMoved(ItemStack item, int amount) {
        this.removeAmount += amount;
        this.checkAchievements(item);
    }

    @Override
    protected void checkAchievements(ItemStack item) {
        item.onResult(this.player.world, this.player, this.removeAmount);
        if (!this.player.world.isClient) {
            int i = this.removeAmount;
            float f = SmeltingManager.getInstance().getXp(item);
            if (f == 0.0F) {
                i = 0;
            } else if (f < 1.0F) {
                int j = MathHelper.floor(i * f);
                if (j < MathHelper.ceil(i * f) && Math.random() < i * f - j) {
                    j++;
                }

                i = j;
            }

            while (i > 0) {
                int k = ExperienceOrbEntity.roundSize(i);
                i -= k;
                this.player.world.addEntity(new ExperienceOrbEntity(this.player.world, this.player.x, this.player.y + 0.5, this.player.z + 0.5, k));
            }
        }

        this.removeAmount = 0;
        if (item.getItem() == Items.IRON_INGOT) {
            this.player.incrementStat(Achievements.GET_IRON_INGOT);
        }

        if (item.getItem() == Items.COOKED_FISH) {
            this.player.incrementStat(Achievements.COOK_FISH);
        }
    }
}
