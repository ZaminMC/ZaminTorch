package net.minecraft.server.command;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public class SpawnPointCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "spawnpoint";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.spawnpoint.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length > 1 && args.length < 4) {
            throw new IncorrectUsageException("commands.spawnpoint.usage");
        }

        ServerPlayerEntity serverplayerentity = args.length > 0 ? parsePlayer(source, args[0]) : asPlayer(source);
        BlockPos blockpos = args.length > 3 ? parseBlockPos(source, args, 1, true) : serverplayerentity.getCommandSourceBlockPos();
        if (serverplayerentity.world != null) {
            serverplayerentity.setSpawnPoint(blockpos, true);
            sendSuccess(source, this, "commands.spawnpoint.success", serverplayerentity.getName(), blockpos.getX(), blockpos.getY(), blockpos.getZ());
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
        } else {
            return args.length > 1 && args.length <= 4 ? suggestCoordinate(args, 1, pos) : null;
        }
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }
}
