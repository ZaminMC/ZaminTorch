package net.minecraft.block.entity;

import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class NoteBlockBlockEntity extends BlockEntity {
    public byte note;
    public boolean powered;

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putByte("note", this.note);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.note = nbt.getByte("note");
        this.note = (byte)MathHelper.clamp(this.note, 0, 24);
    }

    public void tunePitch() {
        this.note = (byte)((this.note + 1) % 25);
        this.markDirty();
    }

    public void playNote(World world, BlockPos pos) {
        if (world.getBlockState(pos.up()).getBlock().getMaterial() == Material.AIR) {
            Material material = world.getBlockState(pos.down()).getBlock().getMaterial();
            int i = 0;
            if (material == Material.STONE) {
                i = 1;
            }

            if (material == Material.SAND) {
                i = 2;
            }

            if (material == Material.GLASS) {
                i = 3;
            }

            if (material == Material.WOOD) {
                i = 4;
            }

            world.addBlockEvent(pos, Blocks.NOTEBLOCK, i, this.note);
        }
    }
}
