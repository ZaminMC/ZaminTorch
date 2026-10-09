package net.minecraft.server.dedicated.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;

public class SetIdleTimeoutCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "setidletimeout";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.setidletimeout.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length != 1) {
            throw new IncorrectUsageException("commands.setidletimeout.usage");
        }

        int i = parseInt(args[0], 0);
        MinecraftServer.getInstance().setPlayerIdleTimeout(i);
        sendSuccess(source, this, "commands.setidletimeout.success", i);
    }
}
