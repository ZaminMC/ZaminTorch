package net.minecraft.server.dedicated.command;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.PlayerNotFoundException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public class KickCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.kick.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length > 0 && args[0].length() > 1) {
            ServerPlayerEntity serverplayerentity = MinecraftServer.getInstance().getPlayerManager().get(args[0]);
            String s = "Kicked by an operator.";
            boolean flag = false;
            if (serverplayerentity == null) {
                throw new PlayerNotFoundException();
            }

            if (args.length >= 2) {
                s = parseText(source, args, 1).getString();
                flag = true;
            }

            serverplayerentity.networkHandler.disconnect(s);
            if (flag) {
                sendSuccess(source, this, "commands.kick.success.reason", serverplayerentity.getName(), s);
            } else {
                sendSuccess(source, this, "commands.kick.success", serverplayerentity.getName());
            }
        } else {
            throw new IncorrectUsageException("commands.kick.usage");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length >= 1 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerNames()) : null;
    }
}
