package net.minecraft.server.command;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;

public class EntityDataCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "entitydata";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.entitydata.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException("commands.entitydata.usage");
        }

        Entity entity = parseEntity(source, args[0]);
        if (entity instanceof PlayerEntity) {
            throw new CommandException("commands.entitydata.noPlayers", entity.getDisplayName());
        }

        NbtCompound nbtcompound = new NbtCompound();
        entity.writeNbtWithoutId(nbtcompound);
        NbtCompound nbtcompound1 = (NbtCompound)nbtcompound.copy();

        NbtCompound nbtcompound2;
        try {
            nbtcompound2 = SnbtParser.parse(parseText(source, args, 1).getString());
        } catch (NbtException nbtexception) {
            throw new CommandException("commands.entitydata.tagError", nbtexception.getMessage());
        }

        nbtcompound2.remove("UUIDMost");
        nbtcompound2.remove("UUIDLeast");
        nbtcompound.merge(nbtcompound2);
        if (nbtcompound.equals(nbtcompound1)) {
            throw new CommandException("commands.entitydata.failed", nbtcompound.toString());
        }

        entity.readNbt(nbtcompound);
        sendSuccess(source, this, "commands.entitydata.success", nbtcompound.toString());
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return index == 0;
    }
}
