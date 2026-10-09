package net.minecraft.block.entity;

import java.util.Random;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.EnchantingTableMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.MenuProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.MathHelper;

public class EnchantingTableBlockEntity extends BlockEntity implements Tickable, MenuProvider {
    public int ticks;
    public float pageAngle;
    public float lastPageAngle;
    public float flip;
    public float lastFlip;
    public float pageTurningSpeed;
    public float lastPageTurningSpeed;
    public float pageRotation;
    public float lastPageRotation;
    public float rotation;
    private static Random RANDOM = new Random();
    private String customName;

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (this.hasCustomName()) {
            nbt.putString("CustomName", this.customName);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("CustomName", 8)) {
            this.customName = nbt.getString("CustomName");
        }
    }

    @Override
    public void tick() {
        this.lastPageTurningSpeed = this.pageTurningSpeed;
        this.lastPageRotation = this.pageRotation;
        PlayerEntity playerentity = this.world.getNearestPlayer(this.pos.getX() + 0.5F, this.pos.getY() + 0.5F, this.pos.getZ() + 0.5F, 3.0);
        if (playerentity != null) {
            double d0 = playerentity.x - (this.pos.getX() + 0.5F);
            double d1 = playerentity.z - (this.pos.getZ() + 0.5F);
            this.rotation = (float)MathHelper.fastAtan2(d1, d0);
            this.pageTurningSpeed += 0.1F;
            if (this.pageTurningSpeed < 0.5F || RANDOM.nextInt(40) == 0) {
                float f1 = this.flip;

                do {
                    this.flip = this.flip + (RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (f1 == this.flip);
            }
        } else {
            this.rotation += 0.02F;
            this.pageTurningSpeed -= 0.1F;
        }

        while (this.pageRotation >= (float) Math.PI) {
            this.pageRotation -= (float) (Math.PI * 2);
        }

        while (this.pageRotation < (float) -Math.PI) {
            this.pageRotation += (float) (Math.PI * 2);
        }

        while (this.rotation >= (float) Math.PI) {
            this.rotation -= (float) (Math.PI * 2);
        }

        while (this.rotation < (float) -Math.PI) {
            this.rotation += (float) (Math.PI * 2);
        }

        float f2 = this.rotation - this.pageRotation;

        while (f2 >= (float) Math.PI) {
            f2 -= (float) (Math.PI * 2);
        }

        while (f2 < (float) -Math.PI) {
            f2 += (float) (Math.PI * 2);
        }

        this.pageRotation += f2 * 0.4F;
        this.pageTurningSpeed = MathHelper.clamp(this.pageTurningSpeed, 0.0F, 1.0F);
        this.ticks++;
        this.lastPageAngle = this.pageAngle;
        float f = (this.flip - this.pageAngle) * 0.4F;
        float f3 = 0.2F;
        f = MathHelper.clamp(f, -f3, f3);
        this.lastFlip = this.lastFlip + (f - this.lastFlip) * 0.9F;
        this.pageAngle = this.pageAngle + this.lastFlip;
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.enchant";
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null && this.customName.length() > 0;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public Text getDisplayName() {
        return this.hasCustomName() ? new LiteralText(this.getName()) : new TranslatableText(this.getName());
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new EnchantingTableMenu(playerInventory, this.world, this.pos);
    }

    @Override
    public String getMenuType() {
        return "minecraft:enchanting_table";
    }
}
