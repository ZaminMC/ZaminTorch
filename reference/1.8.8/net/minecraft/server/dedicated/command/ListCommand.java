package net.minecraft.server.dedicated.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

public class ListCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "list";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.players.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        int i = MinecraftServer.getInstance().getPlayerCount();
        source.sendMessage(new TranslatableText("commands.players.list", i, MinecraftServer.getInstance().getMaxPlayerCount()));
        source.sendMessage(new LiteralText(MinecraftServer.getInstance().getPlayerManager().listNames(args.length > 0 && "uuids".equalsIgnoreCase(args[0]))));
        source.addResult(CommandResults.Type.QUERY_RESULT, i);
    }
}
