package net.minecraft.server.dedicated.command;

import java.util.List;
import java.util.regex.Matcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.CommandSyntaxException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class PardonIpCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "pardon-ip";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public boolean canUse(CommandSource source) {
        return MinecraftServer.getInstance().getPlayerManager().getIpBans().isEnabled() && super.canUse(source);
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.unbanip.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length == 1 && args[0].length() > 1) {
            Matcher matcher = BanIpCommand.REGEX_PATTERN.matcher(args[0]);
            if (matcher.matches()) {
                MinecraftServer.getInstance().getPlayerManager().getIpBans().remove(args[0]);
                sendSuccess(source, this, "commands.unbanip.success", args[0]);
            } else {
                throw new CommandSyntaxException("commands.unbanip.invalid");
            }
        } else {
            throw new IncorrectUsageException("commands.unbanip.usage");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerManager().getIpBans().getNames()) : null;
    }
}
