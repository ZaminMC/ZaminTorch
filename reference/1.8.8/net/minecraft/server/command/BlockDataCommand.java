package net.minecraft.server.command;

import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockDataCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "blockdata";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.blockdata.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 4) {
            throw new IncorrectUsageException("commands.blockdata.usage");
        }

        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 0);
        BlockPos blockpos = parseBlockPos(source, args, 0, false);
        World world = source.getCommandSourceWorld();
        if (!world.isChunkLoaded(blockpos)) {
            throw new CommandException("commands.blockdata.outOfWorld");
        }

        BlockEntity blockentity = world.getBlockEntity(blockpos);
        if (blockentity == null) {
            throw new CommandException("commands.blockdata.notValid");
        }

        NbtCompound nbtcompound = new NbtCompound();
        blockentity.writeNbt(nbtcompound);
        NbtCompound nbtcompound1 = (NbtCompound)nbtcompound.copy();

        NbtCompound nbtcompound2;
        try {
            nbtcompound2 = SnbtParser.parse(parseText(source, args, 3).getString());
        } catch (NbtException nbtexception) {
            throw new CommandException("commands.blockdata.tagError", nbtexception.getMessage());
        }

        nbtcompound.merge(nbtcompound2);
        nbtcompound.putInt("x", blockpos.getX());
        nbtcompound.putInt("y", blockpos.getY());
        nbtcompound.putInt("z", blockpos.getZ());
        if (nbtcompound.equals(nbtcompound1)) {
            throw new CommandException("commands.blockdata.failed", nbtcompound.toString());
        }

        blockentity.readNbt(nbtcompound);
        blockentity.markDirty();
        world.notifyBlockChanged(blockpos);
        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 1);
        sendSuccess(source, this, "commands.blockdata.success", nbtcompound.toString());
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length > 0 && args.length <= 3 ? suggestCoordinate(args, 0, pos) : null;
    }
}
