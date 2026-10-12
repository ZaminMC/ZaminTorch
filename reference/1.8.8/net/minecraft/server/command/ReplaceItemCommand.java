package net.minecraft.server.command;

import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.InvalidNumberException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ReplaceItemCommand extends AbstractCommand {
    private static final Map<String, Integer> SLOTS_BY_ID = Maps.newHashMap();

    @Override
    public String getName() {
        return "replaceitem";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.replaceitem.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new IncorrectUsageException("commands.replaceitem.usage");
        }

        boolean flag;
        if (args[0].equals("entity")) {
            flag = false;
        } else {
            if (!args[0].equals("block")) {
                throw new IncorrectUsageException("commands.replaceitem.usage");
            }

            flag = true;
        }

        int i;
        if (flag) {
            if (args.length < 6) {
                throw new IncorrectUsageException("commands.replaceitem.block.usage");
            }

            i = 4;
        } else {
            if (args.length < 4) {
                throw new IncorrectUsageException("commands.replaceitem.entity.usage");
            }

            i = 2;
        }

        int j = this.getSlot(args[i++]);

        Item item;
        try {
            item = parseItem(source, args[i]);
        } catch (InvalidNumberException invalidnumberexception) {
            if (Block.byKey(args[i]) != Blocks.AIR) {
                throw invalidnumberexception;
            }

            item = null;
        }

        i++;
        int k = args.length > i ? parseInt(args[i++], 1, 64) : 1;
        int l = args.length > i ? parseInt(args[i++]) : 0;
        ItemStack itemstack = new ItemStack(item, k, l);
        if (args.length > i) {
            String s = parseText(source, args, i).getString();

            try {
                itemstack.setNbt(SnbtParser.parse(s));
            } catch (NbtException nbtexception) {
                throw new CommandException("commands.replaceitem.tagError", nbtexception.getMessage());
            }
        }

        if (itemstack.getItem() == null) {
            itemstack = null;
        }

        if (flag) {
            source.addResult(CommandResults.Type.AFFECTED_ITEMS, 0);
            BlockPos blockpos = parseBlockPos(source, args, 1, false);
            World world = source.getCommandSourceWorld();
            BlockEntity blockentity = world.getBlockEntity(blockpos);
            if (blockentity == null || !(blockentity instanceof Inventory)) {
                throw new CommandException("commands.replaceitem.noContainer", blockpos.getX(), blockpos.getY(), blockpos.getZ());
            }

            Inventory inventory = (Inventory)blockentity;
            if (j >= 0 && j < inventory.getSize()) {
                inventory.setItem(j, itemstack);
            }
        } else {
            Entity entity = parseEntity(source, args[1]);
            source.addResult(CommandResults.Type.AFFECTED_ITEMS, 0);
            if (entity instanceof PlayerEntity) {
                ((PlayerEntity)entity).playerMenu.updateListeners();
            }

            if (!entity.replaceItem(j, itemstack)) {
                throw new CommandException("commands.replaceitem.failed", j, k, itemstack == null ? "Air" : itemstack.getDisplayName());
            }

            if (entity instanceof PlayerEntity) {
                ((PlayerEntity)entity).playerMenu.updateListeners();
            }
        }

        source.addResult(CommandResults.Type.AFFECTED_ITEMS, k);
        sendSuccess(source, this, "commands.replaceitem.success", j, k, itemstack == null ? "Air" : itemstack.getDisplayName());
    }

    private int getSlot(String slotId) throws CommandException {
        if (!SLOTS_BY_ID.containsKey(slotId)) {
            throw new CommandException("commands.generic.parameter.invalid", slotId);
        } else {
            return SLOTS_BY_ID.get(slotId);
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, "entity", "block");
        } else if (args.length == 2 && args[0].equals("entity")) {
            return suggestMatching(args, this.getPlayerNames());
        } else if (args.length >= 2 && args.length <= 4 && args[0].equals("block")) {
            return suggestCoordinate(args, 1, pos);
        } else if ((args.length != 3 || !args[0].equals("entity")) && (args.length != 5 || !args[0].equals("block"))) {
            return (args.length != 4 || !args[0].equals("entity")) && (args.length != 6 || !args[0].equals("block"))
                ? null
                : suggestMatching(args, Item.REGISTRY.keySet());
        } else {
            return suggestMatching(args, SLOTS_BY_ID.keySet());
        }
    }

    protected String[] getPlayerNames() {
        return MinecraftServer.getInstance().getPlayerNames();
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return args.length > 0 && args[0].equals("entity") && index == 1;
    }

    static {
        for (int i = 0; i < 54; i++) {
            SLOTS_BY_ID.put("slot.container." + i, i);
        }

        for (int j = 0; j < 9; j++) {
            SLOTS_BY_ID.put("slot.hotbar." + j, j);
        }

        for (int k = 0; k < 27; k++) {
            SLOTS_BY_ID.put("slot.inventory." + k, 9 + k);
        }

        for (int l = 0; l < 27; l++) {
            SLOTS_BY_ID.put("slot.enderchest." + l, 200 + l);
        }

        for (int i1 = 0; i1 < 8; i1++) {
            SLOTS_BY_ID.put("slot.villager." + i1, 300 + i1);
        }

        for (int j1 = 0; j1 < 15; j1++) {
            SLOTS_BY_ID.put("slot.horse." + j1, 500 + j1);
        }

        SLOTS_BY_ID.put("slot.weapon", 99);
        SLOTS_BY_ID.put("slot.armor.head", 103);
        SLOTS_BY_ID.put("slot.armor.chest", 102);
        SLOTS_BY_ID.put("slot.armor.legs", 101);
        SLOTS_BY_ID.put("slot.armor.feet", 100);
        SLOTS_BY_ID.put("slot.horse.saddle", 400);
        SLOTS_BY_ID.put("slot.horse.armor", 401);
        SLOTS_BY_ID.put("slot.horse.chest", 499);
    }
}
