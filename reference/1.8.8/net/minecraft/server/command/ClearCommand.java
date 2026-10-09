package net.minecraft.server.command;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;

public class ClearCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.clear.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        ServerPlayerEntity serverplayerentity = args.length == 0 ? asPlayer(source) : parsePlayer(source, args[0]);
        Item item = args.length >= 2 ? parseItem(source, args[1]) : null;
        int i = args.length >= 3 ? parseInt(args[2], -1) : -1;
        int j = args.length >= 4 ? parseInt(args[3], -1) : -1;
        NbtCompound nbtcompound = null;
        if (args.length >= 5) {
            try {
                nbtcompound = SnbtParser.parse(parseString(args, 4));
            } catch (NbtException nbtexception) {
                throw new CommandException("commands.clear.tagError", nbtexception.getMessage());
            }
        }

        if (args.length >= 2 && item == null) {
            throw new CommandException("commands.clear.failure", serverplayerentity.getName());
        }

        int k = serverplayerentity.inventory.removeAll(item, i, j, nbtcompound);
        serverplayerentity.playerMenu.updateListeners();
        if (!serverplayerentity.abilities.creativeMode) {
            serverplayerentity.use();
        }

        source.addResult(CommandResults.Type.AFFECTED_ITEMS, k);
        if (k == 0) {
            throw new CommandException("commands.clear.failure", serverplayerentity.getName());
        }

        if (j == 0) {
            source.sendMessage(new TranslatableText("commands.clear.testing", serverplayerentity.getName(), k));
        } else {
            sendSuccess(source, this, "commands.clear.success", serverplayerentity.getName(), k);
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, this.getPlayerNames());
        } else {
            return args.length == 2 ? suggestMatching(args, Item.REGISTRY.keySet()) : null;
        }
    }

    protected String[] getPlayerNames() {
        return MinecraftServer.getInstance().getPlayerNames();
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }
}
