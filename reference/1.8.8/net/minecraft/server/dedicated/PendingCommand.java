package net.minecraft.server.dedicated;

import net.minecraft.server.command.source.CommandSource;

public class PendingCommand {
    public final String command;
    public final CommandSource source;

    public PendingCommand(String command, CommandSource source) {
        this.command = command;
        this.source = source;
    }
}
