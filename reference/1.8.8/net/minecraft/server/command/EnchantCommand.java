package net.minecraft.server.command;

import java.util.List;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.InvalidNumberException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;

public class EnchantCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "enchant";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.enchant.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException("commands.enchant.usage");
        }

        PlayerEntity playerentity = parsePlayer(source, args[0]);
        source.addResult(CommandResults.Type.AFFECTED_ITEMS, 0);

        int i;
        try {
            i = parseInt(args[1], 0);
        } catch (InvalidNumberException invalidnumberexception) {
            Enchantment enchantment = Enchantment.byKey(args[1]);
            if (enchantment == null) {
                throw invalidnumberexception;
            }

            i = enchantment.id;
        }

        int j = 1;
        ItemStack itemstack = playerentity.getItemInHand();
        if (itemstack == null) {
            throw new CommandException("commands.enchant.noItem");
        }

        Enchantment enchantment1 = Enchantment.byId(i);
        if (enchantment1 == null) {
            throw new InvalidNumberException("commands.enchant.notFound", i);
        }

        if (!enchantment1.canEnchant(itemstack)) {
            throw new CommandException("commands.enchant.cantEnchant");
        }

        if (args.length >= 3) {
            j = parseInt(args[2], enchantment1.getMinLevel(), enchantment1.getMaxLevel());
        }

        if (itemstack.hasNbt()) {
            NbtList nbtlist = itemstack.getEnchantments();
            if (nbtlist != null) {
                for (int k = 0; k < nbtlist.size(); k++) {
                    int l = nbtlist.getCompound(k).getShort("id");
                    if (Enchantment.byId(l) != null) {
                        Enchantment enchantment2 = Enchantment.byId(l);
                        if (!enchantment2.isCompatible(enchantment1)) {
                            throw new CommandException(
                                "commands.enchant.cantCombine", enchantment1.getName(j), enchantment2.getName(nbtlist.getCompound(k).getShort("lvl"))
                            );
                        }
                    }
                }
            }
        }

        itemstack.addEnchantment(enchantment1, j);
        sendSuccess(source, this, "commands.enchant.success");
        source.addResult(CommandResults.Type.AFFECTED_ITEMS, 1);
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, this.getPlayerNames());
        } else {
            return args.length == 2 ? suggestMatching(args, Enchantment.getKeys()) : null;
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
