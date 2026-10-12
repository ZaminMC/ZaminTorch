package net.minecraft.server.command;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SetBlockCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "setblock";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.setblock.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 4) {
            throw new IncorrectUsageException("commands.setblock.usage");
        }

        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 0);
        BlockPos blockpos = parseBlockPos(source, args, 0, false);
        Block block = AbstractCommand.parseBlock(source, args[3]);
        int i = 0;
        if (args.length >= 5) {
            i = parseInt(args[4], 0, 15);
        }

        World world = source.getCommandSourceWorld();
        if (!world.isChunkLoaded(blockpos)) {
            throw new CommandException("commands.setblock.outOfWorld");
        }

        NbtCompound nbtcompound = new NbtCompound();
        boolean flag = false;
        if (args.length >= 7 && block.hasBlockEntity()) {
            String s = parseText(source, args, 6).getString();

            try {
                nbtcompound = SnbtParser.parse(s);
                flag = true;
            } catch (NbtException nbtexception) {
                throw new CommandException("commands.setblock.tagError", nbtexception.getMessage());
            }
        }

        if (args.length >= 6) {
            if (args[5].equals("destroy")) {
                world.breakBlock(blockpos, true);
                if (block == Blocks.AIR) {
                    sendSuccess(source, this, "commands.setblock.success");
                    return;
                }
            } else if (args[5].equals("keep") && !world.isAir(blockpos)) {
                throw new CommandException("commands.setblock.noChange");
            }
        }

        BlockEntity blockentity1 = world.getBlockEntity(blockpos);
        if (blockentity1 != null) {
            if (blockentity1 instanceof Inventory) {
                ((Inventory)blockentity1).clear();
            }

            world.setBlockState(blockpos, Blocks.AIR.defaultState(), block == Blocks.AIR ? 2 : 4);
        }

        BlockState blockstate = block.getStateFromMetadata(i);
        if (!world.setBlockState(blockpos, blockstate, 2)) {
            throw new CommandException("commands.setblock.noChange");
        }

        if (flag) {
            BlockEntity blockentity = world.getBlockEntity(blockpos);
            if (blockentity != null) {
                nbtcompound.putInt("x", blockpos.getX());
                nbtcompound.putInt("y", blockpos.getY());
                nbtcompound.putInt("z", blockpos.getZ());
                blockentity.readNbt(nbtcompound);
            }
        }

        world.onBlockChanged(blockpos, blockstate.getBlock());
        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 1);
        sendSuccess(source, this, "commands.setblock.success");
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length > 0 && args.length <= 3) {
            return suggestCoordinate(args, 0, pos);
        } else if (args.length == 4) {
            return suggestMatching(args, Block.REGISTRY.keySet());
        } else {
            return args.length == 6 ? suggestMatching(args, "replace", "destroy", "keep") : null;
        }
    }
}
