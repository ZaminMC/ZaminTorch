package net.minecraft.server.command;

import java.util.Arrays;
import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.PlayerNotFoundException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;

public class TellCommand extends AbstractCommand {
    @Override
    public List<String> getAliases() {
        return Arrays.asList("w", "msg");
    }

    @Override
    public String getName() {
        return "tell";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.message.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException("commands.message.usage");
        }

        PlayerEntity playerentity = parsePlayer(source, args[0]);
        if (playerentity == source) {
            throw new PlayerNotFoundException("commands.message.sameTarget");
        }

        Text text = parseText(source, args, 1, !(source instanceof PlayerEntity));
        TranslatableText translatabletext = new TranslatableText("commands.message.display.incoming", source.getDisplayName(), text.copy());
        TranslatableText translatabletext1 = new TranslatableText("commands.message.display.outgoing", playerentity.getDisplayName(), text.copy());
        translatabletext.getStyle().setColor(Formatting.GRAY).setItalic(true);
        translatabletext1.getStyle().setColor(Formatting.GRAY).setItalic(true);
        playerentity.sendMessage(translatabletext);
        source.sendMessage(translatabletext1);
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }
}
