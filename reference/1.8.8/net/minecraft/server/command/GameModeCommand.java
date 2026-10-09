package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.InvalidNumberException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldSettings;

public class GameModeCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "gamemode";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.gamemode.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length <= 0) {
            throw new IncorrectUsageException("commands.gamemode.usage");
        }

        WorldSettings.GameMode worldsettings$gamemode = this.parseGameMode(source, args[0]);
        PlayerEntity playerentity = args.length >= 2 ? parsePlayer(source, args[1]) : asPlayer(source);
        playerentity.setGameMode(worldsettings$gamemode);
        playerentity.fallDistance = 0.0F;
        if (source.getCommandSourceWorld().getGameRules().getBoolean("sendCommandFeedback")) {
            playerentity.sendMessage(new TranslatableText("gameMode.changed"));
        }

        Text text = new TranslatableText("gameMode." + worldsettings$gamemode.getKey());
        if (playerentity != source) {
            sendSuccess(source, this, 1, "commands.gamemode.success.other", playerentity.getName(), text);
        } else {
            sendSuccess(source, this, 1, "commands.gamemode.success.self", text);
        }
    }

    protected WorldSettings.GameMode parseGameMode(CommandSource source, String s) throws InvalidNumberException {
        if (s.equalsIgnoreCase(WorldSettings.GameMode.SURVIVAL.getKey()) || s.equalsIgnoreCase("s")) {
            return WorldSettings.GameMode.SURVIVAL;
        } else if (s.equalsIgnoreCase(WorldSettings.GameMode.CREATIVE.getKey()) || s.equalsIgnoreCase("c")) {
            return WorldSettings.GameMode.CREATIVE;
        } else if (s.equalsIgnoreCase(WorldSettings.GameMode.ADVENTURE.getKey()) || s.equalsIgnoreCase("a")) {
            return WorldSettings.GameMode.ADVENTURE;
        } else {
            return !s.equalsIgnoreCase(WorldSettings.GameMode.SPECTATOR.getKey()) && !s.equalsIgnoreCase("sp")
                ? WorldSettings.getGameModeById(parseInt(s, 0, WorldSettings.GameMode.values().length - 2))
                : WorldSettings.GameMode.SPECTATOR;
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, "survival", "creative", "adventure", "spectator");
        } else {
            return args.length == 2 ? suggestMatching(args, this.getPlayerNames()) : null;
        }
    }

    protected String[] getPlayerNames() {
        return MinecraftServer.getInstance().getPlayerNames();
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 1;
    }
}
