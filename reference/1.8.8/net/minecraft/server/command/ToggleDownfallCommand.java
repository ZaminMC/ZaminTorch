package net.minecraft.server.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.world.WorldData;

public class ToggleDownfallCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "toggledownfall";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.downfall.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        this.toggleDownfall();
        sendSuccess(source, this, "commands.downfall.success");
    }

    protected void toggleDownfall() {
        WorldData worlddata = MinecraftServer.getInstance().worlds[0].getData();
        worlddata.setRaining(!worlddata.isRaining());
    }
}
