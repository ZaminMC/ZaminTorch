package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

public class TradeWithVillagerGoal extends GoToEntityGoal {
    private int cooldown;
    private VillagerEntity target;

    public TradeWithVillagerGoal(VillagerEntity target) {
        super(target, VillagerEntity.class, 3.0F, 0.02F);
        this.target = target;
    }

    @Override
    public void start() {
        super.start();
        if (this.target.wantsMoreFood() && this.targetEntity instanceof VillagerEntity && ((VillagerEntity)this.targetEntity).hasRoomForHarvest()) {
            this.cooldown = 10;
        } else {
            this.cooldown = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown == 0) {
                SimpleInventory simpleinventory = this.target.getVillagerInventory();

                for (int i = 0; i < simpleinventory.getSize(); i++) {
                    ItemStack itemstack = simpleinventory.getItem(i);
                    ItemStack itemstack1 = null;
                    if (itemstack != null) {
                        Item item = itemstack.getItem();
                        if ((item == Items.BREAD || item == Items.POTATO || item == Items.CARROT) && itemstack.size > 3) {
                            int l = itemstack.size / 2;
                            itemstack.size -= l;
                            itemstack1 = new ItemStack(item, l, itemstack.getMetadata());
                        } else if (item == Items.WHEAT && itemstack.size > 5) {
                            int j = itemstack.size / 2 / 3 * 3;
                            int k = j / 3;
                            itemstack.size -= j;
                            itemstack1 = new ItemStack(Items.BREAD, k, 0);
                        }

                        if (itemstack.size <= 0) {
                            simpleinventory.setItem(i, null);
                        }
                    }

                    if (itemstack1 != null) {
                        double d0 = this.target.y - 0.3F + this.target.getEyeHeight();
                        ItemEntity itementity = new ItemEntity(this.target.world, this.target.x, d0, this.target.z, itemstack1);
                        float f = 0.3F;
                        float f1 = this.target.headYaw;
                        float f2 = this.target.pitch;
                        itementity.velocityX = -MathHelper.sin(f1 / 180.0F * (float) Math.PI) * MathHelper.cos(f2 / 180.0F * (float) Math.PI) * f;
                        itementity.velocityZ = MathHelper.cos(f1 / 180.0F * (float) Math.PI) * MathHelper.cos(f2 / 180.0F * (float) Math.PI) * f;
                        itementity.velocityY = -MathHelper.sin(f2 / 180.0F * (float) Math.PI) * f + 0.1F;
                        itementity.setDefaultPickUpDelay();
                        this.target.world.addEntity(itementity);
                        break;
                    }
                }
            }
        }
    }
}
