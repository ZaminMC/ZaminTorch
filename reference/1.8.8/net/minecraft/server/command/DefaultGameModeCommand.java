package net.minecraft.server.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;
import net.minecraft.world.WorldSettings;

public class DefaultGameModeCommand extends GameModeCommand {
    @Override
    public String getName() {
        return "defaultgamemode";
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.defaultgamemode.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length <= 0) {
            throw new IncorrectUsageException("commands.defaultgamemode.usage");
        }

        WorldSettings.GameMode worldsettings$gamemode = this.parseGameMode(source, args[0]);
        this.setDefault(worldsettings$gamemode);
        sendSuccess(source, this, "commands.defaultgamemode.success", new TranslatableText("gameMode." + worldsettings$gamemode.getKey()));
    }

    protected void setDefault(WorldSettings.GameMode gameMode) {
        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        minecraftserver.setDefaultGameMode(gameMode);
        if (minecraftserver.shouldForceGameMode()) {
            for (ServerPlayerEntity serverplayerentity : MinecraftServer.getInstance().getPlayerManager().getAll()) {
                serverplayerentity.setGameMode(gameMode);
                serverplayerentity.fallDistance = 0.0F;
            }
        }
    }
}
