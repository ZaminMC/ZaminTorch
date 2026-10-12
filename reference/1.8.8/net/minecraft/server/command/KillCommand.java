package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class KillCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "kill";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.kill.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length == 0) {
            PlayerEntity playerentity = asPlayer(source);
            playerentity.discard();
            sendSuccess(source, this, "commands.kill.successful", playerentity.getDisplayName());
        } else {
            Entity entity = parseEntity(source, args[0]);
            entity.discard();
            sendSuccess(source, this, "commands.kill.successful", entity.getDisplayName());
        }
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerNames()) : null;
    }
}
