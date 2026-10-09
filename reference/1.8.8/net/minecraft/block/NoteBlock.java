package net.minecraft.block;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.NoteBlockBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class NoteBlock extends BlockWithBlockEntity {
    private static final List<String> INSTRUMENTS = Lists.newArrayList("harp", "bd", "snare", "hat", "bassattack");

    public NoteBlock() {
        super(Material.WOOD);
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        boolean flag = world.hasNeighborSignal(pos);
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof NoteBlockBlockEntity) {
            NoteBlockBlockEntity noteblockblockentity = (NoteBlockBlockEntity)blockentity;
            if (noteblockblockentity.powered != flag) {
                if (flag) {
                    noteblockblockentity.playNote(world, pos);
                }

                noteblockblockentity.powered = flag;
            }
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof NoteBlockBlockEntity) {
            NoteBlockBlockEntity noteblockblockentity = (NoteBlockBlockEntity)blockentity;
            noteblockblockentity.tunePitch();
            noteblockblockentity.playNote(world, pos);
            player.incrementStat(Stats.NOTE_BLOCKS_TUNED);
        }

        return true;
    }

    @Override
    public void startMining(World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof NoteBlockBlockEntity) {
                ((NoteBlockBlockEntity)blockentity).playNote(world, pos);
                player.incrementStat(Stats.NOTE_BLOCKS_PLAYED);
            }
        }
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new NoteBlockBlockEntity();
    }

    private String getInstrument(int index) {
        if (index < 0 || index >= INSTRUMENTS.size()) {
            index = 0;
        }

        return INSTRUMENTS.get(index);
    }

    @Override
    public boolean doEvent(World world, BlockPos pos, BlockState state, int type, int data) {
        float f = (float)Math.pow(2.0, (data - 12) / 12.0);
        world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "note." + this.getInstrument(type), 3.0F, f);
        world.addParticle(ParticleType.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, data / 24.0, 0.0, 0.0);
        return true;
    }

    @Override
    public int getRenderType() {
        return 3;
    }
}
