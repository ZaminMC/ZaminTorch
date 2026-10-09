package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class GiveCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "give";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.give.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException("commands.give.usage");
        }

        PlayerEntity playerentity = parsePlayer(source, args[0]);
        Item item = parseItem(source, args[1]);
        int i = args.length >= 3 ? parseInt(args[2], 1, 64) : 1;
        int j = args.length >= 4 ? parseInt(args[3]) : 0;
        ItemStack itemstack = new ItemStack(item, i, j);
        if (args.length >= 5) {
            String s = parseText(source, args, 4).getString();

            try {
                itemstack.setNbt(SnbtParser.parse(s));
            } catch (NbtException nbtexception) {
                throw new CommandException("commands.give.tagError", nbtexception.getMessage());
            }
        }

        boolean flag = playerentity.inventory.addItem(itemstack);
        if (flag) {
            playerentity.world
                .playSound(
                    (Entity)playerentity,
                    "random.pop",
                    0.2F,
                    ((playerentity.getRandom().nextFloat() - playerentity.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                );
            playerentity.playerMenu.updateListeners();
        }

        if (flag && itemstack.size <= 0) {
            itemstack.size = 1;
            source.addResult(CommandResults.Type.AFFECTED_ITEMS, i);
            ItemEntity itementity1 = playerentity.dropItem(itemstack, false);
            if (itementity1 != null) {
                itementity1.makeFakeItem();
            }
        } else {
            source.addResult(CommandResults.Type.AFFECTED_ITEMS, i - itemstack.size);
            ItemEntity itementity = playerentity.dropItem(itemstack, false);
            if (itementity != null) {
                itementity.setNoPickUpDelay();
                itementity.setOwner(playerentity.getName());
            }
        }

        sendSuccess(source, this, "commands.give.success", itemstack.getDisplayName(), i, playerentity.getName());
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
