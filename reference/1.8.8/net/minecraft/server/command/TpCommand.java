package net.minecraft.server.command;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class TpCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.tp.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new IncorrectUsageException("commands.tp.usage");
        }

        int i = 0;
        Entity entity;
        if (args.length != 2 && args.length != 4 && args.length != 6) {
            entity = asPlayer(source);
        } else {
            entity = parseEntity(source, args[0]);
            i = 1;
        }

        if (args.length == 1 || args.length == 2) {
            Entity entity1 = parseEntity(source, args[args.length - 1]);
            if (entity1.world != entity.world) {
                throw new CommandException("commands.tp.notSameDimension");
            }

            entity.startRiding(null);
            if (entity instanceof ServerPlayerEntity) {
                ((ServerPlayerEntity)entity).networkHandler.teleport(entity1.x, entity1.y, entity1.z, entity1.yaw, entity1.pitch);
            } else {
                entity.setPositionAndAngles(entity1.x, entity1.y, entity1.z, entity1.yaw, entity1.pitch);
            }

            sendSuccess(source, this, "commands.tp.success", entity.getName(), entity1.getName());
        } else {
            if (args.length < i + 3) {
                throw new IncorrectUsageException("commands.tp.usage");
            }

            if (entity.world != null) {
                int j = i;
                AbstractCommand.Coordinate abstractcommand$coordinate = parseTeleportCoordinate(entity.x, args[j++], true);
                AbstractCommand.Coordinate abstractcommand$coordinate1 = parseTeleportCoordinate(entity.y, args[j++], 0, 0, false);
                AbstractCommand.Coordinate abstractcommand$coordinate2 = parseTeleportCoordinate(entity.z, args[j++], true);
                AbstractCommand.Coordinate abstractcommand$coordinate3 = parseTeleportCoordinate(entity.yaw, args.length > j ? args[j++] : "~", false);
                AbstractCommand.Coordinate abstractcommand$coordinate4 = parseTeleportCoordinate(entity.pitch, args.length > j ? args[j] : "~", false);
                if (entity instanceof ServerPlayerEntity) {
                    Set<PlayerMoveS2CPacket.Argument> set = EnumSet.noneOf(PlayerMoveS2CPacket.Argument.class);
                    if (abstractcommand$coordinate.isRelative()) {
                        set.add(PlayerMoveS2CPacket.Argument.X);
                    }

                    if (abstractcommand$coordinate1.isRelative()) {
                        set.add(PlayerMoveS2CPacket.Argument.Y);
                    }

                    if (abstractcommand$coordinate2.isRelative()) {
                        set.add(PlayerMoveS2CPacket.Argument.Z);
                    }

                    if (abstractcommand$coordinate4.isRelative()) {
                        set.add(PlayerMoveS2CPacket.Argument.PITCH);
                    }

                    if (abstractcommand$coordinate3.isRelative()) {
                        set.add(PlayerMoveS2CPacket.Argument.YAW);
                    }

                    float f = (float)abstractcommand$coordinate3.getInput();
                    if (!abstractcommand$coordinate3.isRelative()) {
                        f = MathHelper.wrapDegrees(f);
                    }

                    float f1 = (float)abstractcommand$coordinate4.getInput();
                    if (!abstractcommand$coordinate4.isRelative()) {
                        f1 = MathHelper.wrapDegrees(f1);
                    }

                    if (f1 > 90.0F || f1 < -90.0F) {
                        f1 = MathHelper.wrapDegrees(180.0F - f1);
                        f = MathHelper.wrapDegrees(f + 180.0F);
                    }

                    entity.startRiding(null);
                    ((ServerPlayerEntity)entity)
                        .networkHandler
                        .teleport(
                            abstractcommand$coordinate.getInput(), abstractcommand$coordinate1.getInput(), abstractcommand$coordinate2.getInput(), f, f1, set
                        );
                    entity.setHeadYaw(f);
                } else {
                    float f2 = (float)MathHelper.wrapDegrees(abstractcommand$coordinate3.getCoordinate());
                    float f3 = (float)MathHelper.wrapDegrees(abstractcommand$coordinate4.getCoordinate());
                    if (f3 > 90.0F || f3 < -90.0F) {
                        f3 = MathHelper.wrapDegrees(180.0F - f3);
                        f2 = MathHelper.wrapDegrees(f2 + 180.0F);
                    }

                    entity.setPositionAndAngles(
                        abstractcommand$coordinate.getCoordinate(),
                        abstractcommand$coordinate1.getCoordinate(),
                        abstractcommand$coordinate2.getCoordinate(),
                        f2,
                        f3
                    );
                    entity.setHeadYaw(f2);
                }

                sendSuccess(
                    source,
                    this,
                    "commands.tp.success.coordinates",
                    entity.getName(),
                    abstractcommand$coordinate.getCoordinate(),
                    abstractcommand$coordinate1.getCoordinate(),
                    abstractcommand$coordinate2.getCoordinate()
                );
            }
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length != 1 && args.length != 2 ? null : suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }
}
