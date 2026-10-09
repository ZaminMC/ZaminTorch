package net.minecraft.server.command;

import java.util.List;
import net.minecraft.network.packet.s2c.play.SpawnPointS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class SetWorldSpawnCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "setworldspawn";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.setworldspawn.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        BlockPos blockpos;
        if (args.length == 0) {
            blockpos = asPlayer(source).getCommandSourceBlockPos();
        } else {
            if (args.length != 3 || source.getCommandSourceWorld() == null) {
                throw new IncorrectUsageException("commands.setworldspawn.usage");
            }

            blockpos = parseBlockPos(source, args, 0, true);
        }

        source.getCommandSourceWorld().setSpawnPoint(blockpos);
        MinecraftServer.getInstance().getPlayerManager().sendPacket(new SpawnPointS2CPacket(blockpos));
        sendSuccess(source, this, "commands.setworldspawn.success", blockpos.getX(), blockpos.getY(), blockpos.getZ());
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length > 0 && args.length <= 3 ? suggestCoordinate(args, 0, pos) : null;
    }
}
