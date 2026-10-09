package net.minecraft.server.integrated.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.world.WorldSettings;

public class PublishCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "publish";
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.publish.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        String s = MinecraftServer.getInstance().publish(WorldSettings.GameMode.SURVIVAL, false);
        if (s != null) {
            sendSuccess(source, this, "commands.publish.started", s);
        } else {
            sendSuccess(source, this, "commands.publish.failed");
        }
    }
}
