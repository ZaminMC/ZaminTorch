package net.minecraft.server.dedicated.command;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;

public class BanListCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "banlist";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public boolean canUse(CommandSource source) {
        return (
                MinecraftServer.getInstance().getPlayerManager().getIpBans().isEnabled()
                    || MinecraftServer.getInstance().getPlayerManager().getBans().isEnabled()
            )
            && super.canUse(source);
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.banlist.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length >= 1 && args[0].equalsIgnoreCase("ips")) {
            source.sendMessage(new TranslatableText("commands.banlist.ips", MinecraftServer.getInstance().getPlayerManager().getIpBans().getNames().length));
            source.sendMessage(new LiteralText(listArgs(MinecraftServer.getInstance().getPlayerManager().getIpBans().getNames())));
        } else {
            source.sendMessage(new TranslatableText("commands.banlist.players", MinecraftServer.getInstance().getPlayerManager().getBans().getNames().length));
            source.sendMessage(new LiteralText(listArgs(MinecraftServer.getInstance().getPlayerManager().getBans().getNames())));
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, "players", "ips") : null;
    }
}
