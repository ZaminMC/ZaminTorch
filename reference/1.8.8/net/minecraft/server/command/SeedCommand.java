package net.minecraft.server.command;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.world.World;

public class SeedCommand extends AbstractCommand {
    @Override
    public boolean canUse(CommandSource source) {
        return MinecraftServer.getInstance().isSingleplayer() || super.canUse(source);
    }

    @Override
    public String getName() {
        return "seed";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.seed.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        World world = source instanceof PlayerEntity ? ((PlayerEntity)source).world : MinecraftServer.getInstance().getWorld(0);
        source.sendMessage(new TranslatableText("commands.seed.success", world.getSeed()));
    }
}
