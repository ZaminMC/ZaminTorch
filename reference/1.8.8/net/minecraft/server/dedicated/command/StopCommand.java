package net.minecraft.server.dedicated.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;

public class StopCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.stop.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (MinecraftServer.getInstance().worlds != null) {
            sendSuccess(source, this, "commands.stop.start");
        }

        MinecraftServer.getInstance().stop();
    }
}
