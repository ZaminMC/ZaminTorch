package net.minecraft.entity.vehicle;

import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.Item;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class ChestMinecartEntity extends InventoryMinecartEntity {
    public ChestMinecartEntity(World world) {
        super(world);
    }

    public ChestMinecartEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    public void dropItems(DamageSource damageSource) {
        super.dropItems(damageSource);
        if (this.world.getGameRules().getBoolean("doEntityDrops")) {
            this.dropItem(Item.byBlock(Blocks.CHEST), 1, 0.0F);
        }
    }

    @Override
    public int getSize() {
        return 27;
    }

    @Override
    public MinecartEntity.Type getMinecartType() {
        return MinecartEntity.Type.CHEST;
    }

    @Override
    public BlockState getDefaultDisplayBlock() {
        return Blocks.CHEST.defaultState().set(ChestBlock.FACING, Direction.NORTH);
    }

    @Override
    public int getDefaultDisplayBlockOffset() {
        return 8;
    }

    @Override
    public String getMenuType() {
        return "minecraft:chest";
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new ChestMenu(playerInventory, this, player);
    }
}
