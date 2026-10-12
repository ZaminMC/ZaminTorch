package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;

public class MeCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "me";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.me.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length <= 0) {
            throw new IncorrectUsageException("commands.me.usage");
        }

        Text text = parseText(source, args, 0, !(source instanceof PlayerEntity));
        MinecraftServer.getInstance().getPlayerManager().sendSystemMessage(new TranslatableText("chat.type.emote", source.getDisplayName(), text));
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return suggestMatching(args, MinecraftServer.getInstance().getPlayerNames());
    }
}
