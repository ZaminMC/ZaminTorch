package net.minecraft.server.command.source;

import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public interface CommandSource {
    String getName();

    Text getDisplayName();

    void sendMessage(Text message);

    boolean canUseCommand(int permissionLevel, String command);

    BlockPos getCommandSourceBlockPos();

    Vec3d getCommandSourcePos();

    World getCommandSourceWorld();

    Entity asEntity();

    boolean sendCommandSuccessToOps();

    void addResult(CommandResults.Type type, int result);
}
