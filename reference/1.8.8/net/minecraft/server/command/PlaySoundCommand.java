package net.minecraft.server.command;

import java.util.List;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PlaySoundCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "playsound";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.playsound.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException(this.getUsage(source));
        }

        int i = 0;
        String s = args[i++];
        ServerPlayerEntity serverplayerentity = parsePlayer(source, args[i++]);
        Vec3d vec3d = source.getCommandSourcePos();
        double d0 = vec3d.x;
        if (args.length > i) {
            d0 = parseCoordinate(d0, args[i++], true);
        }

        double d1 = vec3d.y;
        if (args.length > i) {
            d1 = parseCoordinate(d1, args[i++], 0, 0, false);
        }

        double d2 = vec3d.z;
        if (args.length > i) {
            d2 = parseCoordinate(d2, args[i++], true);
        }

        double d3 = 1.0;
        if (args.length > i) {
            d3 = parseDouble(args[i++], 0.0, Float.MAX_VALUE);
        }

        double d4 = 1.0;
        if (args.length > i) {
            d4 = parseDouble(args[i++], 0.0, 2.0);
        }

        double d5 = 0.0;
        if (args.length > i) {
            d5 = parseDouble(args[i], 0.0, 1.0);
        }

        double d6 = d3 > 1.0 ? d3 * 16.0 : 16.0;
        double d7 = serverplayerentity.distanceTo(d0, d1, d2);
        if (d7 > d6) {
            if (d5 <= 0.0) {
                throw new CommandException("commands.playsound.playerTooFar", serverplayerentity.getName());
            }

            double d8 = d0 - serverplayerentity.x;
            double d9 = d1 - serverplayerentity.y;
            double d10 = d2 - serverplayerentity.z;
            double d11 = Math.sqrt(d8 * d8 + d9 * d9 + d10 * d10);
            if (d11 > 0.0) {
                d0 = serverplayerentity.x + d8 / d11 * 2.0;
                d1 = serverplayerentity.y + d9 / d11 * 2.0;
                d2 = serverplayerentity.z + d10 / d11 * 2.0;
            }

            d3 = d5;
        }

        serverplayerentity.networkHandler.sendPacket(new SoundEventS2CPacket(s, d0, d1, d2, (float)d3, (float)d4));
        sendSuccess(source, this, "commands.playsound.success", s, serverplayerentity.getName());
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 2) {
            return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
        } else {
            return args.length > 2 && args.length <= 5 ? suggestCoordinate(args, 2, pos) : null;
        }
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 1;
    }
}
