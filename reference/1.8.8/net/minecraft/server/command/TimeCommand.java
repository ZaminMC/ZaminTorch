package net.minecraft.server.command;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class TimeCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "time";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.time.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length > 1) {
            if (args[0].equals("set")) {
                int l;
                if (args[1].equals("day")) {
                    l = 1000;
                } else if (args[1].equals("night")) {
                    l = 13000;
                } else {
                    l = parseInt(args[1], 0);
                }

                this.setTimeOfDay(source, l);
                sendSuccess(source, this, "commands.time.set", l);
                return;
            }

            if (args[0].equals("add")) {
                int k = parseInt(args[1], 0);
                this.addToTimeOfDay(source, k);
                sendSuccess(source, this, "commands.time.added", k);
                return;
            }

            if (args[0].equals("query")) {
                if (args[1].equals("daytime")) {
                    int j = (int)(source.getCommandSourceWorld().getTimeOfDay() % 2147483647L);
                    source.addResult(CommandResults.Type.QUERY_RESULT, j);
                    sendSuccess(source, this, "commands.time.query", j);
                    return;
                }

                if (args[1].equals("gametime")) {
                    int i = (int)(source.getCommandSourceWorld().getTime() % 2147483647L);
                    source.addResult(CommandResults.Type.QUERY_RESULT, i);
                    sendSuccess(source, this, "commands.time.query", i);
                    return;
                }
            }
        }

        throw new IncorrectUsageException("commands.time.usage");
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, "set", "add", "query");
        } else if (args.length == 2 && args[0].equals("set")) {
            return suggestMatching(args, "day", "night");
        } else {
            return args.length == 2 && args[0].equals("query") ? suggestMatching(args, "daytime", "gametime") : null;
        }
    }

    protected void setTimeOfDay(CommandSource source, int time) {
        for (int i = 0; i < MinecraftServer.getInstance().worlds.length; i++) {
            MinecraftServer.getInstance().worlds[i].setTimeOfDay(time);
        }
    }

    protected void addToTimeOfDay(CommandSource source, int time) {
        for (int i = 0; i < MinecraftServer.getInstance().worlds.length; i++) {
            ServerWorld serverworld = MinecraftServer.getInstance().worlds[i];
            serverworld.setTimeOfDay(serverworld.getTimeOfDay() + time);
        }
    }
}
