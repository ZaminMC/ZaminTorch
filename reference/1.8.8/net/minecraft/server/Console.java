package net.minecraft.server;

import net.minecraft.entity.Entity;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class Console implements CommandSource {
    private static final Console INSTANCE = new Console();
    private StringBuffer text = new StringBuffer();

    @Override
    public String getName() {
        return "Rcon";
    }

    @Override
    public Text getDisplayName() {
        return new LiteralText(this.getName());
    }

    @Override
    public void sendMessage(Text message) {
        this.text.append(message.getString());
    }

    @Override
    public boolean canUseCommand(int permissionLevel, String command) {
        return true;
    }

    @Override
    public BlockPos getCommandSourceBlockPos() {
        return new BlockPos(0, 0, 0);
    }

    @Override
    public Vec3d getCommandSourcePos() {
        return new Vec3d(0.0, 0.0, 0.0);
    }

    @Override
    public World getCommandSourceWorld() {
        return MinecraftServer.getInstance().getCommandSourceWorld();
    }

    @Override
    public Entity asEntity() {
        return null;
    }

    @Override
    public boolean sendCommandSuccessToOps() {
        return true;
    }

    @Override
    public void addResult(CommandResults.Type type, int result) {
    }
}
