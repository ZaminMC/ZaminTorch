package net.minecraft.server.command;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StatsCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.stats.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new IncorrectUsageException("commands.stats.usage");
        }

        boolean flag;
        if (args[0].equals("entity")) {
            flag = false;
        } else {
            if (!args[0].equals("block")) {
                throw new IncorrectUsageException("commands.stats.usage");
            }

            flag = true;
        }

        int i;
        if (flag) {
            if (args.length < 5) {
                throw new IncorrectUsageException("commands.stats.block.usage");
            }

            i = 4;
        } else {
            if (args.length < 3) {
                throw new IncorrectUsageException("commands.stats.entity.usage");
            }

            i = 2;
        }

        String s = args[i++];
        if ("set".equals(s)) {
            if (args.length < i + 3) {
                if (i == 5) {
                    throw new IncorrectUsageException("commands.stats.block.set.usage");
                }

                throw new IncorrectUsageException("commands.stats.entity.set.usage");
            }
        } else {
            if (!"clear".equals(s)) {
                throw new IncorrectUsageException("commands.stats.usage");
            }

            if (args.length < i + 1) {
                if (i == 5) {
                    throw new IncorrectUsageException("commands.stats.block.clear.usage");
                }

                throw new IncorrectUsageException("commands.stats.entity.clear.usage");
            }
        }

        CommandResults.Type commandresults$type = CommandResults.Type.byName(args[i++]);
        if (commandresults$type == null) {
            throw new CommandException("commands.stats.failed");
        }

        World world = source.getCommandSourceWorld();
        CommandResults commandresults;
        if (flag) {
            BlockPos blockpos = parseBlockPos(source, args, 1, false);
            BlockEntity blockentity = world.getBlockEntity(blockpos);
            if (blockentity == null) {
                throw new CommandException("commands.stats.noCompatibleBlock", blockpos.getX(), blockpos.getY(), blockpos.getZ());
            }

            if (blockentity instanceof CommandBlockBlockEntity) {
                commandresults = ((CommandBlockBlockEntity)blockentity).getCommandResults();
            } else {
                if (!(blockentity instanceof SignBlockEntity)) {
                    throw new CommandException("commands.stats.noCompatibleBlock", blockpos.getX(), blockpos.getY(), blockpos.getZ());
                }

                commandresults = ((SignBlockEntity)blockentity).getCommandResults();
            }
        } else {
            Entity entity = parseEntity(source, args[1]);
            commandresults = entity.getCommandResults();
        }

        if ("set".equals(s)) {
            String s1 = args[i++];
            String s2 = args[i];
            if (s1.length() == 0 || s2.length() == 0) {
                throw new CommandException("commands.stats.failed");
            }

            CommandResults.updateSourceAndObjective(commandresults, commandresults$type, s1, s2);
            sendSuccess(source, this, "commands.stats.success", commandresults$type.getName(), s2, s1);
        } else if ("clear".equals(s)) {
            CommandResults.updateSourceAndObjective(commandresults, commandresults$type, null, null);
            sendSuccess(source, this, "commands.stats.cleared", commandresults$type.getName());
        }

        if (flag) {
            BlockPos blockpos1 = parseBlockPos(source, args, 1, false);
            BlockEntity blockentity1 = world.getBlockEntity(blockpos1);
            blockentity1.markDirty();
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, "entity", "block");
        }

        if (args.length == 2 && args[0].equals("entity")) {
            return suggestMatching(args, this.getPlayerNames());
        }

        if (args.length >= 2 && args.length <= 4 && args[0].equals("block")) {
            return suggestCoordinate(args, 1, pos);
        }

        if ((args.length != 3 || !args[0].equals("entity")) && (args.length != 5 || !args[0].equals("block"))) {
            if ((args.length != 4 || !args[0].equals("entity")) && (args.length != 6 || !args[0].equals("block"))) {
                return (args.length != 6 || !args[0].equals("entity")) && (args.length != 8 || !args[0].equals("block"))
                    ? null
                    : suggestMatching(args, this.getObjectives());
            } else {
                return suggestMatching(args, CommandResults.Type.getNames());
            }
        } else {
            return suggestMatching(args, "set", "clear");
        }
    }

    protected String[] getPlayerNames() {
        return MinecraftServer.getInstance().getPlayerNames();
    }

    protected List<String> getObjectives() {
        Collection<ScoreboardObjective> collection = MinecraftServer.getInstance().getWorld(0).getScoreboard().getObjectives();
        List<String> list = Lists.newArrayList();

        for (ScoreboardObjective scoreboardobjective : collection) {
            if (!scoreboardobjective.getCriterion().isReadOnly()) {
                list.add(scoreboardobjective.getName());
            }
        }

        return list;
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return args.length > 0 && args[0].equals("entity") && index == 1;
    }
}
