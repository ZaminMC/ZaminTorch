package net.minecraft.server.command;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBox;

public class TestForBlocksCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "testforblocks";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.compare.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 9) {
            throw new IncorrectUsageException("commands.compare.usage");
        }

        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 0);
        BlockPos blockpos = parseBlockPos(source, args, 0, false);
        BlockPos blockpos1 = parseBlockPos(source, args, 3, false);
        BlockPos blockpos2 = parseBlockPos(source, args, 6, false);
        StructureBox structurebox = new StructureBox(blockpos, blockpos1);
        StructureBox structurebox1 = new StructureBox(blockpos2, blockpos2.add(structurebox.getDiagonal()));
        int i = structurebox.getSpanX() * structurebox.getSpanY() * structurebox.getSpanZ();
        if (i > 524288) {
            throw new CommandException("commands.compare.tooManyBlocks", i, 524288);
        }

        if (structurebox.minY >= 0 && structurebox.maxY < 256 && structurebox1.minY >= 0 && structurebox1.maxY < 256) {
            World world = source.getCommandSourceWorld();
            if (world.isAreaLoaded(structurebox) && world.isAreaLoaded(structurebox1)) {
                boolean flag = false;
                if (args.length > 9 && args[9].equals("masked")) {
                    flag = true;
                }

                i = 0;
                BlockPos blockpos3 = new BlockPos(
                    structurebox1.minX - structurebox.minX, structurebox1.minY - structurebox.minY, structurebox1.minZ - structurebox.minZ
                );
                BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();
                BlockPos.Mutable blockpos$mutable1 = new BlockPos.Mutable();

                for (int j = structurebox.minZ; j <= structurebox.maxZ; j++) {
                    for (int k = structurebox.minY; k <= structurebox.maxY; k++) {
                        for (int l = structurebox.minX; l <= structurebox.maxX; l++) {
                            blockpos$mutable.set(l, k, j);
                            blockpos$mutable1.set(l + blockpos3.getX(), k + blockpos3.getY(), j + blockpos3.getZ());
                            boolean flag1 = false;
                            BlockState blockstate = world.getBlockState(blockpos$mutable);
                            if (!flag || blockstate.getBlock() != Blocks.AIR) {
                                if (blockstate == world.getBlockState(blockpos$mutable1)) {
                                    BlockEntity blockentity = world.getBlockEntity(blockpos$mutable);
                                    BlockEntity blockentity1 = world.getBlockEntity(blockpos$mutable1);
                                    if (blockentity != null && blockentity1 != null) {
                                        NbtCompound nbtcompound = new NbtCompound();
                                        blockentity.writeNbt(nbtcompound);
                                        nbtcompound.remove("x");
                                        nbtcompound.remove("y");
                                        nbtcompound.remove("z");
                                        NbtCompound nbtcompound1 = new NbtCompound();
                                        blockentity1.writeNbt(nbtcompound1);
                                        nbtcompound1.remove("x");
                                        nbtcompound1.remove("y");
                                        nbtcompound1.remove("z");
                                        if (!nbtcompound.equals(nbtcompound1)) {
                                            flag1 = true;
                                        }
                                    } else if (blockentity != null) {
                                        flag1 = true;
                                    }
                                } else {
                                    flag1 = true;
                                }

                                i++;
                                if (flag1) {
                                    throw new CommandException("commands.compare.failed");
                                }
                            }
                        }
                    }
                }

                source.addResult(CommandResults.Type.AFFECTED_BLOCKS, i);
                sendSuccess(source, this, "commands.compare.success", i);
            } else {
                throw new CommandException("commands.compare.outOfWorld");
            }
        } else {
            throw new CommandException("commands.compare.outOfWorld");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length > 0 && args.length <= 3) {
            return suggestCoordinate(args, 0, pos);
        } else if (args.length > 3 && args.length <= 6) {
            return suggestCoordinate(args, 3, pos);
        } else if (args.length > 6 && args.length <= 9) {
            return suggestCoordinate(args, 6, pos);
        } else {
            return args.length == 10 ? suggestMatching(args, "masked", "all") : null;
        }
    }
}
