package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class TestForCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "testfor";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.testfor.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new IncorrectUsageException("commands.testfor.usage");
        }

        Entity entity = parseEntity(source, args[0]);
        NbtCompound nbtcompound = null;
        if (args.length >= 2) {
            try {
                nbtcompound = SnbtParser.parse(parseString(args, 1));
            } catch (NbtException nbtexception) {
                throw new CommandException("commands.testfor.tagError", nbtexception.getMessage());
            }
        }

        if (nbtcompound != null) {
            NbtCompound nbtcompound1 = new NbtCompound();
            entity.writeNbtWithoutId(nbtcompound1);
            if (!NbtUtils.matches(nbtcompound, nbtcompound1, true)) {
                throw new CommandException("commands.testfor.failure", entity.getName());
            }
        }

        sendSuccess(source, this, "commands.testfor.success", entity.getName());
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length == 1 ? suggestMatching(args, MinecraftServer.getInstance().getPlayerNames()) : null;
    }
}
