package net.minecraft.server.command;

import java.util.List;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public interface Command extends Comparable<Command> {
    String getName();

    String getUsage(CommandSource source);

    List<String> getAliases();

    void run(CommandSource source, String[] args) throws CommandException;

    boolean canUse(CommandSource source);

    List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos);

    boolean hasTargetSelectorAt(String[] args, int index);
}
