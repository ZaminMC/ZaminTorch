package net.minecraft.server.command;

import com.google.gson.JsonParseException;
import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.CommandSyntaxException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextUtils;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class TellRawCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "tellraw";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.tellraw.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException("commands.tellraw.usage");
        }

        PlayerEntity playerentity = parsePlayer(source, args[0]);
        String s = parseString(args, 1);

        try {
            Text text = Text.Serializer.fromJson(s);
            playerentity.sendMessage(TextUtils.updateForEntity(source, text, playerentity));
        } catch (JsonParseException jsonparseexception) {
            Throwable throwable = ExceptionUtils.getRootCause(jsonparseexception);
            throw new CommandSyntaxException("commands.tellraw.jsonException", throwable == null ? "" : throwable.getMessage());
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerNames()) : null;
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }
}
