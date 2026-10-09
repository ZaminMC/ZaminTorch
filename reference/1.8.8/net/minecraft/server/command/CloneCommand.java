package net.minecraft.server.command;

import com.google.common.collect.Lists;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.world.ScheduledTick;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBox;

public class CloneCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "clone";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.clone.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 9) {
            throw new IncorrectUsageException("commands.clone.usage");
        }

        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 0);
        BlockPos blockpos = parseBlockPos(source, args, 0, false);
        BlockPos blockpos1 = parseBlockPos(source, args, 3, false);
        BlockPos blockpos2 = parseBlockPos(source, args, 6, false);
        StructureBox structurebox = new StructureBox(blockpos, blockpos1);
        StructureBox structurebox1 = new StructureBox(blockpos2, blockpos2.add(structurebox.getDiagonal()));
        int i = structurebox.getSpanX() * structurebox.getSpanY() * structurebox.getSpanZ();
        if (i > 32768) {
            throw new CommandException("commands.clone.tooManyBlocks", i, 32768);
        }

        boolean flag = false;
        Block block = null;
        int j = -1;
        if ((args.length < 11 || !args[10].equals("force") && !args[10].equals("move")) && structurebox.intersects(structurebox1)) {
            throw new CommandException("commands.clone.noOverlap");
        }

        if (args.length >= 11 && args[10].equals("move")) {
            flag = true;
        }

        if (structurebox.minY >= 0 && structurebox.maxY < 256 && structurebox1.minY >= 0 && structurebox1.maxY < 256) {
            World world = source.getCommandSourceWorld();
            if (world.isAreaLoaded(structurebox) && world.isAreaLoaded(structurebox1)) {
                boolean flag1 = false;
                if (args.length >= 10) {
                    if (args[9].equals("masked")) {
                        flag1 = true;
                    } else if (args[9].equals("filtered")) {
                        if (args.length < 12) {
                            throw new IncorrectUsageException("commands.clone.usage");
                        }

                        block = parseBlock(source, args[11]);
                        if (args.length >= 13) {
                            j = parseInt(args[12], 0, 15);
                        }
                    }
                }

                List<CloneCommand.ClonedBlock> list = Lists.newArrayList();
                List<CloneCommand.ClonedBlock> list1 = Lists.newArrayList();
                List<CloneCommand.ClonedBlock> list2 = Lists.newArrayList();
                LinkedList<BlockPos> linkedlist = Lists.newLinkedList();
                BlockPos blockpos3 = new BlockPos(
                    structurebox1.minX - structurebox.minX, structurebox1.minY - structurebox.minY, structurebox1.minZ - structurebox.minZ
                );

                for (int k = structurebox.minZ; k <= structurebox.maxZ; k++) {
                    for (int l = structurebox.minY; l <= structurebox.maxY; l++) {
                        for (int i1 = structurebox.minX; i1 <= structurebox.maxX; i1++) {
                            BlockPos blockpos4 = new BlockPos(i1, l, k);
                            BlockPos blockpos5 = blockpos4.add(blockpos3);
                            BlockState blockstate = world.getBlockState(blockpos4);
                            if ((!flag1 || blockstate.getBlock() != Blocks.AIR)
                                && (block == null || blockstate.getBlock() == block && (j < 0 || blockstate.getBlock().getMetadataFromState(blockstate) == j))) {
                                BlockEntity blockentity = world.getBlockEntity(blockpos4);
                                if (blockentity != null) {
                                    NbtCompound nbtcompound = new NbtCompound();
                                    blockentity.writeNbt(nbtcompound);
                                    list1.add(new CloneCommand.ClonedBlock(blockpos5, blockstate, nbtcompound));
                                    linkedlist.addLast(blockpos4);
                                } else if (!blockstate.getBlock().isOpaque() && !blockstate.getBlock().isCube()) {
                                    list2.add(new CloneCommand.ClonedBlock(blockpos5, blockstate, null));
                                    linkedlist.addFirst(blockpos4);
                                } else {
                                    list.add(new CloneCommand.ClonedBlock(blockpos5, blockstate, null));
                                    linkedlist.addLast(blockpos4);
                                }
                            }
                        }
                    }
                }

                if (flag) {
                    for (BlockPos blockpos6 : linkedlist) {
                        BlockEntity blockentity1 = world.getBlockEntity(blockpos6);
                        if (blockentity1 instanceof Inventory) {
                            ((Inventory)blockentity1).clear();
                        }

                        world.setBlockState(blockpos6, Blocks.BARRIER.defaultState(), 2);
                    }

                    for (BlockPos blockpos7 : linkedlist) {
                        world.setBlockState(blockpos7, Blocks.AIR.defaultState(), 3);
                    }
                }

                List<CloneCommand.ClonedBlock> list3 = Lists.newArrayList();
                list3.addAll(list);
                list3.addAll(list1);
                list3.addAll(list2);
                List<CloneCommand.ClonedBlock> list4 = Lists.reverse(list3);

                for (CloneCommand.ClonedBlock clonecommand$clonedblock : list4) {
                    BlockEntity blockentity2 = world.getBlockEntity(clonecommand$clonedblock.dstPos);
                    if (blockentity2 instanceof Inventory) {
                        ((Inventory)blockentity2).clear();
                    }

                    world.setBlockState(clonecommand$clonedblock.dstPos, Blocks.BARRIER.defaultState(), 2);
                }

                i = 0;

                for (CloneCommand.ClonedBlock clonecommand$clonedblock1 : list3) {
                    if (world.setBlockState(clonecommand$clonedblock1.dstPos, clonecommand$clonedblock1.state, 2)) {
                        i++;
                    }
                }

                for (CloneCommand.ClonedBlock clonecommand$clonedblock2 : list1) {
                    BlockEntity blockentity3 = world.getBlockEntity(clonecommand$clonedblock2.dstPos);
                    if (clonecommand$clonedblock2.nbt != null && blockentity3 != null) {
                        clonecommand$clonedblock2.nbt.putInt("x", clonecommand$clonedblock2.dstPos.getX());
                        clonecommand$clonedblock2.nbt.putInt("y", clonecommand$clonedblock2.dstPos.getY());
                        clonecommand$clonedblock2.nbt.putInt("z", clonecommand$clonedblock2.dstPos.getZ());
                        blockentity3.readNbt(clonecommand$clonedblock2.nbt);
                        blockentity3.markDirty();
                    }

                    world.setBlockState(clonecommand$clonedblock2.dstPos, clonecommand$clonedblock2.state, 2);
                }

                for (CloneCommand.ClonedBlock clonecommand$clonedblock3 : list4) {
                    world.onBlockChanged(clonecommand$clonedblock3.dstPos, clonecommand$clonedblock3.state.getBlock());
                }

                List<ScheduledTick> list5 = world.getScheduledTicks(structurebox, false);
                if (list5 != null) {
                    for (ScheduledTick scheduledtick : list5) {
                        if (structurebox.contains(scheduledtick.pos)) {
                            BlockPos blockpos8 = scheduledtick.pos.add(blockpos3);
                            world.loadScheduledTick(
                                blockpos8, scheduledtick.getBlock(), (int)(scheduledtick.time - world.getData().getTime()), scheduledtick.priority
                            );
                        }
                    }
                }

                if (i <= 0) {
                    throw new CommandException("commands.clone.failed");
                }

                source.addResult(CommandResults.Type.AFFECTED_BLOCKS, i);
                sendSuccess(source, this, "commands.clone.success", i);
            } else {
                throw new CommandException("commands.clone.outOfWorld");
            }
        } else {
            throw new CommandException("commands.clone.outOfWorld");
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
        } else if (args.length == 10) {
            return suggestMatching(args, "replace", "masked", "filtered");
        } else if (args.length == 11) {
            return suggestMatching(args, "normal", "force", "move");
        } else {
            return args.length == 12 && "filtered".equals(args[9]) ? suggestMatching(args, Block.REGISTRY.keySet()) : null;
        }
    }

    static class ClonedBlock {
        public final BlockPos dstPos;
        public final BlockState state;
        public final NbtCompound nbt;

        public ClonedBlock(BlockPos pos, BlockState state, NbtCompound nbt) {
            this.dstPos = pos;
            this.state = state;
            this.nbt = nbt;
        }
    }
}
