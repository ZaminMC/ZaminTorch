package net.minecraft.server.dedicated.command;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class OpCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "op";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.op.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length == 1 && args[0].length() > 0) {
            MinecraftServer minecraftserver = MinecraftServer.getInstance();
            GameProfile gameprofile = minecraftserver.getGameProfileCache().get(args[0]);
            if (gameprofile == null) {
                throw new CommandException("commands.op.failed", args[0]);
            }

            minecraftserver.getPlayerManager().addOp(gameprofile);
            sendSuccess(source, this, "commands.op.success", args[0]);
        } else {
            throw new IncorrectUsageException("commands.op.usage");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            String s = args[args.length - 1];
            List<String> list = Lists.newArrayList();

            for (GameProfile gameprofile : MinecraftServer.getInstance().getGameProfiles()) {
                if (!MinecraftServer.getInstance().getPlayerManager().isOp(gameprofile) && doesStringStartWith(s, gameprofile.getName())) {
                    list.add(gameprofile.getName());
                }
            }

            return list;
        } else {
            return null;
        }
    }
}
