package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class ExperienceCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "xp";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.xp.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length <= 0) {
            throw new IncorrectUsageException("commands.xp.usage");
        }

        String s = args[0];
        boolean flag = s.endsWith("l") || s.endsWith("L");
        if (flag && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }

        int i = parseInt(s);
        boolean flag1 = i < 0;
        if (flag1) {
            i *= -1;
        }

        PlayerEntity playerentity = args.length > 1 ? parsePlayer(source, args[1]) : asPlayer(source);
        if (flag) {
            source.addResult(CommandResults.Type.QUERY_RESULT, playerentity.xpLevel);
            if (flag1) {
                playerentity.addXp(-i);
                sendSuccess(source, this, "commands.xp.success.negative.levels", i, playerentity.getName());
            } else {
                playerentity.addXp(i);
                sendSuccess(source, this, "commands.xp.success.levels", i, playerentity.getName());
            }
        } else {
            source.addResult(CommandResults.Type.QUERY_RESULT, playerentity.xp);
            if (flag1) {
                throw new CommandException("commands.xp.failure.widthdrawXp");
            }

            playerentity.increaseXp(i);
            sendSuccess(source, this, "commands.xp.success", i, playerentity.getName());
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 2 ? suggestMatching(args, this.getPlayerNames()) : null;
    }

    protected String[] getPlayerNames() {
        return MinecraftServer.getInstance().getPlayerNames();
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 1;
    }
}
