package net.minecraft.server.command;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.handler.CommandHandler;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ExecuteCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "execute";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.execute.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 5) {
            throw new IncorrectUsageException("commands.execute.usage");
        }

        final Entity entity = parseEntity(source, args[0], Entity.class);
        final double d0 = parseCoordinate(entity.x, args[1], false);
        final double d1 = parseCoordinate(entity.y, args[2], false);
        final double d2 = parseCoordinate(entity.z, args[3], false);
        final BlockPos blockpos = new BlockPos(d0, d1, d2);
        int i = 4;
        if ("detect".equals(args[4]) && args.length > 10) {
            World world = entity.getCommandSourceWorld();
            double d3 = parseCoordinate(d0, args[5], false);
            double d4 = parseCoordinate(d1, args[6], false);
            double d5 = parseCoordinate(d2, args[7], false);
            Block block = parseBlock(source, args[8]);
            int k = parseInt(args[9], -1, 15);
            BlockPos blockpos1 = new BlockPos(d3, d4, d5);
            BlockState blockstate = world.getBlockState(blockpos1);
            if (blockstate.getBlock() != block || k >= 0 && blockstate.getBlock().getMetadataFromState(blockstate) != k) {
                throw new CommandException("commands.execute.failed", "detect", entity.getName());
            }

            i = 10;
        }

        String s = parseString(args, i);
        final CommandSource commandsource1 = source;
        CommandSource commandsource = new CommandSource() {
            @Override
            public String getName() {
                return entity.getName();
            }

            @Override
            public Text getDisplayName() {
                return entity.getDisplayName();
            }

            @Override
            public void sendMessage(Text message) {
                commandsource1.sendMessage(message);
            }

            @Override
            public boolean canUseCommand(int permissionLevel, String command) {
                return commandsource1.canUseCommand(permissionLevel, command);
            }

            @Override
            public BlockPos getCommandSourceBlockPos() {
                return blockpos;
            }

            @Override
            public Vec3d getCommandSourcePos() {
                return new Vec3d(d0, d1, d2);
            }

            @Override
            public World getCommandSourceWorld() {
                return entity.world;
            }

            @Override
            public Entity asEntity() {
                return entity;
            }

            @Override
            public boolean sendCommandSuccessToOps() {
                MinecraftServer minecraftserver = MinecraftServer.getInstance();
                return minecraftserver == null || minecraftserver.worlds[0].getGameRules().getBoolean("commandBlockOutput");
            }

            @Override
            public void addResult(CommandResults.Type type, int result) {
                entity.addResult(type, result);
            }
        };
        CommandHandler commandhandler = MinecraftServer.getInstance().getCommandHandler();

        try {
            int j = commandhandler.run(commandsource, s);
            if (j < 1) {
                throw new CommandException("commands.execute.allInvocationsFailed", s);
            }
        } catch (Throwable throwable) {
            throw new CommandException("commands.execute.failed", s, entity.getName());
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
        } else if (args.length > 1 && args.length <= 4) {
            return suggestCoordinate(args, 1, pos);
        } else if (args.length > 5 && args.length <= 8 && "detect".equals(args[4])) {
            return suggestCoordinate(args, 5, pos);
        } else {
            return args.length == 9 && "detect".equals(args[4]) ? suggestMatching(args, Block.REGISTRY.keySet()) : null;
        }
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }
}
