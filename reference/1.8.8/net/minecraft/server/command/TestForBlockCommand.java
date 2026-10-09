package net.minecraft.server.command;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.InvalidNumberException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TestForBlockCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "testforblock";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.testforblock.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 4) {
            throw new IncorrectUsageException("commands.testforblock.usage");
        }

        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 0);
        BlockPos blockpos = parseBlockPos(source, args, 0, false);
        Block block = Block.byKey(args[3]);
        if (block == null) {
            throw new InvalidNumberException("commands.setblock.notFound", args[3]);
        }

        int i = -1;
        if (args.length >= 5) {
            i = parseInt(args[4], -1, 15);
        }

        World world = source.getCommandSourceWorld();
        if (!world.isChunkLoaded(blockpos)) {
            throw new CommandException("commands.testforblock.outOfWorld");
        }

        NbtCompound nbtcompound = new NbtCompound();
        boolean flag = false;
        if (args.length >= 6 && block.hasBlockEntity()) {
            String s = parseText(source, args, 5).getString();

            try {
                nbtcompound = SnbtParser.parse(s);
                flag = true;
            } catch (NbtException nbtexception) {
                throw new CommandException("commands.setblock.tagError", nbtexception.getMessage());
            }
        }

        BlockState blockstate = world.getBlockState(blockpos);
        Block block1 = blockstate.getBlock();
        if (block1 != block) {
            throw new CommandException(
                "commands.testforblock.failed.tile", blockpos.getX(), blockpos.getY(), blockpos.getZ(), block1.getName(), block.getName()
            );
        }

        if (i > -1) {
            int j = blockstate.getBlock().getMetadataFromState(blockstate);
            if (j != i) {
                throw new CommandException("commands.testforblock.failed.data", blockpos.getX(), blockpos.getY(), blockpos.getZ(), j, i);
            }
        }

        if (flag) {
            BlockEntity blockentity = world.getBlockEntity(blockpos);
            if (blockentity == null) {
                throw new CommandException("commands.testforblock.failed.tileEntity", blockpos.getX(), blockpos.getY(), blockpos.getZ());
            }

            NbtCompound nbtcompound1 = new NbtCompound();
            blockentity.writeNbt(nbtcompound1);
            if (!NbtUtils.matches(nbtcompound, nbtcompound1, true)) {
                throw new CommandException("commands.testforblock.failed.nbt", blockpos.getX(), blockpos.getY(), blockpos.getZ());
            }
        }

        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 1);
        sendSuccess(source, this, "commands.testforblock.success", blockpos.getX(), blockpos.getY(), blockpos.getZ());
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length > 0 && args.length <= 3) {
            return suggestCoordinate(args, 0, pos);
        } else {
            return args.length == 4 ? suggestMatching(args, Block.REGISTRY.keySet()) : null;
        }
    }
}
