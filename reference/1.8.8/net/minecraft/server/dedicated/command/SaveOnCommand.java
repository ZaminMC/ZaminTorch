package net.minecraft.server.dedicated.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.world.ServerWorld;

public class SaveOnCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "save-on";
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.save-on.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        boolean flag = false;

        for (int i = 0; i < minecraftserver.worlds.length; i++) {
            if (minecraftserver.worlds[i] != null) {
                ServerWorld serverworld = minecraftserver.worlds[i];
                if (serverworld.savingDisabled) {
                    serverworld.savingDisabled = false;
                    flag = true;
                }
            }
        }

        if (flag) {
            sendSuccess(source, this, "commands.save.enabled");
        } else {
            throw new CommandException("commands.save-on.alreadyOn");
        }
    }
}
