package net.minecraft.item;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class BlockItem extends Item {
    protected final Block block;

    public BlockItem(Block block) {
        this.block = block;
    }

    public BlockItem setKey(String string) {
        super.setKey(string);
        return this;
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (!block.canBeReplaced(world, pos)) {
            pos = pos.offset(face);
        }

        if (stack.size == 0) {
            return false;
        }

        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        if (world.canPlace(this.block, pos, false, face, null, stack)) {
            int i = this.getBlockMetadata(stack.getMetadata());
            BlockState blockstate1 = this.block.getPlacementState(world, pos, face, faceX, faceY, faceZ, i, player);
            if (world.setBlockState(pos, blockstate1, 3)) {
                blockstate1 = world.getBlockState(pos);
                if (blockstate1.getBlock() == this.block) {
                    setBlockNbt(world, player, pos, stack);
                    this.block.onPlaced(world, pos, blockstate1, player, stack);
                }

                world.playSound(
                    pos.getX() + 0.5F,
                    pos.getY() + 0.5F,
                    pos.getZ() + 0.5F,
                    this.block.sounds.getPlacing(),
                    (this.block.sounds.getVolume() + 1.0F) / 2.0F,
                    this.block.sounds.getPitch() * 0.8F
                );
                stack.size--;
            }

            return true;
        } else {
            return false;
        }
    }

    public static boolean setBlockNbt(World world, PlayerEntity player, BlockPos pos, ItemStack stack) {
        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        if (minecraftserver == null) {
            return false;
        }

        if (stack.hasNbt() && stack.getNbt().contains("BlockEntityTag", 10)) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity != null) {
                if (!world.isClient && blockentity.requireOpForPlacingWithNbt() && !minecraftserver.getPlayerManager().isOp(player.getGameProfile())) {
                    return false;
                }

                NbtCompound nbtcompound = new NbtCompound();
                NbtCompound nbtcompound1 = (NbtCompound)nbtcompound.copy();
                blockentity.writeNbt(nbtcompound);
                NbtCompound nbtcompound2 = (NbtCompound)stack.getNbt().get("BlockEntityTag");
                nbtcompound.merge(nbtcompound2);
                nbtcompound.putInt("x", pos.getX());
                nbtcompound.putInt("y", pos.getY());
                nbtcompound.putInt("z", pos.getZ());
                if (!nbtcompound.equals(nbtcompound1)) {
                    blockentity.readNbt(nbtcompound);
                    blockentity.markDirty();
                    return true;
                }
            }
        }

        return false;
    }

    public boolean onPlace(World world, BlockPos pos, Direction dir, PlayerEntity player, ItemStack stack) {
        Block block = world.getBlockState(pos).getBlock();
        if (block == Blocks.SNOW_LAYER) {
            dir = Direction.UP;
        } else if (!block.canBeReplaced(world, pos)) {
            pos = pos.offset(dir);
        }

        return world.canPlace(this.block, pos, false, dir, null, stack);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return this.block.getTranslationKey();
    }

    @Override
    public String getTranslationKey() {
        return this.block.getTranslationKey();
    }

    @Override
    public CreativeModeTab getCreativeModeTab() {
        return this.block.getCreativeModeTab();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        this.block.addToCreativeMenu(item, tab, inventory);
    }

    public Block getBlock() {
        return this.block;
    }
}
