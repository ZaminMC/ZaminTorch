package net.minecraft.server.dedicated.command;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class DeOpCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "deop";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.deop.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length == 1 && args[0].length() > 0) {
            MinecraftServer minecraftserver = MinecraftServer.getInstance();
            GameProfile gameprofile = minecraftserver.getPlayerManager().getOps().getProfile(args[0]);
            if (gameprofile == null) {
                throw new CommandException("commands.deop.failed", args[0]);
            }

            minecraftserver.getPlayerManager().removeOp(gameprofile);
            sendSuccess(source, this, "commands.deop.success", args[0]);
        } else {
            throw new IncorrectUsageException("commands.deop.usage");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerManager().getOpNames()) : null;
    }
}
