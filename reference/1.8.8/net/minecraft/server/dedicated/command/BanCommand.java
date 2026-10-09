package net.minecraft.server.dedicated.command;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerBanEntry;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public class BanCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.ban.usage";
    }

    @Override
    public boolean canUse(CommandSource source) {
        return MinecraftServer.getInstance().getPlayerManager().getBans().isEnabled() && super.canUse(source);
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length >= 1 && args[0].length() > 0) {
            MinecraftServer minecraftserver = MinecraftServer.getInstance();
            GameProfile gameprofile = minecraftserver.getGameProfileCache().get(args[0]);
            if (gameprofile == null) {
                throw new CommandException("commands.ban.failed", args[0]);
            }

            String s = null;
            if (args.length >= 2) {
                s = parseText(source, args, 1).getString();
            }

            PlayerBanEntry playerbanentry = new PlayerBanEntry(gameprofile, null, source.getName(), null, s);
            minecraftserver.getPlayerManager().getBans().add(playerbanentry);
            ServerPlayerEntity serverplayerentity = minecraftserver.getPlayerManager().get(args[0]);
            if (serverplayerentity != null) {
                serverplayerentity.networkHandler.disconnect("You are banned from this server.");
            }

            sendSuccess(source, this, "commands.ban.success", args[0]);
        } else {
            throw new IncorrectUsageException("commands.ban.usage");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length >= 1 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerNames()) : null;
    }
}
