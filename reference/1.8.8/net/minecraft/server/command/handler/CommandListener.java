package net.minecraft.server.command.handler;

import net.minecraft.server.command.Command;
import net.minecraft.server.command.source.CommandSource;

public interface CommandListener {
    void sendSuccess(CommandSource source, Command command, int flags, String message, Object... args);
}
