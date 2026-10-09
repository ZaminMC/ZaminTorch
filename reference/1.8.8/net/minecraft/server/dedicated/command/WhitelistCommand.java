package net.minecraft.server.dedicated.command;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;

public class WhitelistCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "whitelist";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.whitelist.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new IncorrectUsageException("commands.whitelist.usage");
        }

        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        if (args[0].equals("on")) {
            minecraftserver.getPlayerManager().setEnforceWhitelist(true);
            sendSuccess(source, this, "commands.whitelist.enabled");
        } else if (args[0].equals("off")) {
            minecraftserver.getPlayerManager().setEnforceWhitelist(false);
            sendSuccess(source, this, "commands.whitelist.disabled");
        } else if (args[0].equals("list")) {
            source.sendMessage(
                new TranslatableText(
                    "commands.whitelist.list",
                    minecraftserver.getPlayerManager().getWhitelistNames().length,
                    minecraftserver.getPlayerManager().getSavedIds().length
                )
            );
            String[] astring = minecraftserver.getPlayerManager().getWhitelistNames();
            source.sendMessage(new LiteralText(listArgs(astring)));
        } else if (args[0].equals("add")) {
            if (args.length < 2) {
                throw new IncorrectUsageException("commands.whitelist.add.usage");
            }

            GameProfile gameprofile = minecraftserver.getGameProfileCache().get(args[1]);
            if (gameprofile == null) {
                throw new CommandException("commands.whitelist.add.failed", args[1]);
            }

            minecraftserver.getPlayerManager().addToWhitelist(gameprofile);
            sendSuccess(source, this, "commands.whitelist.add.success", args[1]);
        } else if (args[0].equals("remove")) {
            if (args.length < 2) {
                throw new IncorrectUsageException("commands.whitelist.remove.usage");
            }

            GameProfile gameprofile1 = minecraftserver.getPlayerManager().getWhitelist().getProfile(args[1]);
            if (gameprofile1 == null) {
                throw new CommandException("commands.whitelist.remove.failed", args[1]);
            }

            minecraftserver.getPlayerManager().removeFromWhitelist(gameprofile1);
            sendSuccess(source, this, "commands.whitelist.remove.success", args[1]);
        } else if (args[0].equals("reload")) {
            minecraftserver.getPlayerManager().reloadWhitelist();
            sendSuccess(source, this, "commands.whitelist.reloaded");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, "on", "off", "list", "add", "remove", "reload");
        }

        if (args.length == 2) {
            if (args[0].equals("remove")) {
                return suggestMatching(args, MinecraftServer.getInstance().getPlayerManager().getWhitelistNames());
            }

            if (args[0].equals("add")) {
                return suggestMatching(args, MinecraftServer.getInstance().getGameProfileCache().getNames());
            }
        }

        return null;
    }
}
