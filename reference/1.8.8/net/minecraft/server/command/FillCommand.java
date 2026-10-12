package net.minecraft.server.command;

import com.google.common.collect.Lists;
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

public class FillCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "fill";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.fill.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 7) {
            throw new IncorrectUsageException("commands.fill.usage");
        }

        source.addResult(CommandResults.Type.AFFECTED_BLOCKS, 0);
        BlockPos blockpos = parseBlockPos(source, args, 0, false);
        BlockPos blockpos1 = parseBlockPos(source, args, 3, false);
        Block block = AbstractCommand.parseBlock(source, args[6]);
        int i = 0;
        if (args.length >= 8) {
            i = parseInt(args[7], 0, 15);
        }

        BlockPos blockpos2 = new BlockPos(
            Math.min(blockpos.getX(), blockpos1.getX()), Math.min(blockpos.getY(), blockpos1.getY()), Math.min(blockpos.getZ(), blockpos1.getZ())
        );
        BlockPos blockpos3 = new BlockPos(
            Math.max(blockpos.getX(), blockpos1.getX()), Math.max(blockpos.getY(), blockpos1.getY()), Math.max(blockpos.getZ(), blockpos1.getZ())
        );
        int j = (blockpos3.getX() - blockpos2.getX() + 1) * (blockpos3.getY() - blockpos2.getY() + 1) * (blockpos3.getZ() - blockpos2.getZ() + 1);
        if (j > 32768) {
            throw new CommandException("commands.fill.tooManyBlocks", j, 32768);
        }

        if (blockpos2.getY() >= 0 && blockpos3.getY() < 256) {
            World world = source.getCommandSourceWorld();

            for (int k = blockpos2.getZ(); k < blockpos3.getZ() + 16; k += 16) {
                for (int l = blockpos2.getX(); l < blockpos3.getX() + 16; l += 16) {
                    if (!world.isChunkLoaded(new BlockPos(l, blockpos3.getY() - blockpos2.getY(), k))) {
                        throw new CommandException("commands.fill.outOfWorld");
                    }
                }
            }

            NbtCompound nbtcompound = new NbtCompound();
            boolean flag = false;
            if (args.length >= 10 && block.hasBlockEntity()) {
                String s = parseText(source, args, 9).getString();

                try {
                    nbtcompound = SnbtParser.parse(s);
                    flag = true;
                } catch (NbtException nbtexception) {
                    throw new CommandException("commands.fill.tagError", nbtexception.getMessage());
                }
            }

            List<BlockPos> list = Lists.newArrayList();
            j = 0;

            for (int i1 = blockpos2.getZ(); i1 <= blockpos3.getZ(); i1++) {
                for (int j1 = blockpos2.getY(); j1 <= blockpos3.getY(); j1++) {
                    for (int k1 = blockpos2.getX(); k1 <= blockpos3.getX(); k1++) {
                        BlockPos blockpos4 = new BlockPos(k1, j1, i1);
                        if (args.length >= 9) {
                            if (!args[8].equals("outline") && !args[8].equals("hollow")) {
                                if (args[8].equals("destroy")) {
                                    world.breakBlock(blockpos4, true);
                                } else if (args[8].equals("keep")) {
                                    if (!world.isAir(blockpos4)) {
                                        continue;
                                    }
                                } else if (args[8].equals("replace") && !block.hasBlockEntity()) {
                                    if (args.length > 9) {
                                        Block block1 = AbstractCommand.parseBlock(source, args[9]);
                                        if (world.getBlockState(blockpos4).getBlock() != block1) {
                                            continue;
                                        }
                                    }

                                    if (args.length > 10) {
                                        int l1 = AbstractCommand.parseInt(args[10]);
                                        BlockState blockstate = world.getBlockState(blockpos4);
                                        if (blockstate.getBlock().getMetadataFromState(blockstate) != l1) {
                                            continue;
                                        }
                                    }
                                }
                            } else if (k1 != blockpos2.getX()
                                && k1 != blockpos3.getX()
                                && j1 != blockpos2.getY()
                                && j1 != blockpos3.getY()
                                && i1 != blockpos2.getZ()
                                && i1 != blockpos3.getZ()) {
                                if (args[8].equals("hollow")) {
                                    world.setBlockState(blockpos4, Blocks.AIR.defaultState(), 2);
                                    list.add(blockpos4);
                                }
                                continue;
                            }
                        }

                        BlockEntity blockentity1 = world.getBlockEntity(blockpos4);
                        if (blockentity1 != null) {
                            if (blockentity1 instanceof Inventory) {
                                ((Inventory)blockentity1).clear();
                            }

                            world.setBlockState(blockpos4, Blocks.BARRIER.defaultState(), block == Blocks.BARRIER ? 2 : 4);
                        }

                        BlockState blockstate1 = block.getStateFromMetadata(i);
                        if (world.setBlockState(blockpos4, blockstate1, 2)) {
                            list.add(blockpos4);
                            j++;
                            if (flag) {
                                BlockEntity blockentity = world.getBlockEntity(blockpos4);
                                if (blockentity != null) {
                                    nbtcompound.putInt("x", blockpos4.getX());
                                    nbtcompound.putInt("y", blockpos4.getY());
                                    nbtcompound.putInt("z", blockpos4.getZ());
                                    blockentity.readNbt(nbtcompound);
                                }
                            }
                        }
                    }
                }
            }

            for (BlockPos blockpos5 : list) {
                Block block2 = world.getBlockState(blockpos5).getBlock();
                world.onBlockChanged(blockpos5, block2);
            }

            if (j <= 0) {
                throw new CommandException("commands.fill.failed");
            }

            source.addResult(CommandResults.Type.AFFECTED_BLOCKS, j);
            sendSuccess(source, this, "commands.fill.success", j);
        } else {
            throw new CommandException("commands.fill.outOfWorld");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length > 0 && args.length <= 3) {
            return suggestCoordinate(args, 0, pos);
        } else if (args.length > 3 && args.length <= 6) {
            return suggestCoordinate(args, 3, pos);
        } else if (args.length == 7) {
            return suggestMatching(args, Block.REGISTRY.keySet());
        } else if (args.length == 9) {
            return suggestMatching(args, "replace", "destroy", "keep", "hollow", "outline");
        } else {
            return args.length == 10 && "replace".equals(args[8]) ? suggestMatching(args, Block.REGISTRY.keySet()) : null;
        }
    }
}
