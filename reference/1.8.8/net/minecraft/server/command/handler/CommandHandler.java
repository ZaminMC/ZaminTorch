package net.minecraft.server.command.handler;

import java.util.List;
import java.util.Map;
import net.minecraft.server.command.Command;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public interface CommandHandler {
    int run(CommandSource source, String command);

    List<String> getSuggestions(CommandSource source, String command, BlockPos pos);

    List<Command> getAvailableCommands(CommandSource source);

    Map<String, Command> getCommands();
}
