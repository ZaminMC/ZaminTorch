package net.minecraft.server.command;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.InvalidNumberException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;

public class DifficultyCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "difficulty";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.difficulty.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length <= 0) {
            throw new IncorrectUsageException("commands.difficulty.usage");
        }

        Difficulty difficulty = this.parseDifficulty(args[0]);
        MinecraftServer.getInstance().setDifficulty(difficulty);
        sendSuccess(source, this, "commands.difficulty.success", new TranslatableText(difficulty.getKey()));
    }

    protected Difficulty parseDifficulty(String s) throws InvalidNumberException {
        if (s.equalsIgnoreCase("peaceful") || s.equalsIgnoreCase("p")) {
            return Difficulty.PEACEFUL;
        } else if (s.equalsIgnoreCase("easy") || s.equalsIgnoreCase("e")) {
            return Difficulty.EASY;
        } else if (s.equalsIgnoreCase("normal") || s.equalsIgnoreCase("n")) {
            return Difficulty.NORMAL;
        } else {
            return !s.equalsIgnoreCase("hard") && !s.equalsIgnoreCase("h") ? Difficulty.byId(parseInt(s, 0, 3)) : Difficulty.HARD;
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, "peaceful", "easy", "normal", "hard") : null;
    }
}
