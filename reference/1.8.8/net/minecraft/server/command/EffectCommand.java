package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.InvalidNumberException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;

public class EffectCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "effect";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.effect.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new IncorrectUsageException("commands.effect.usage");
        }

        LivingEntity livingentity = parseEntity(source, args[0], LivingEntity.class);
        if (args[1].equals("clear")) {
            if (livingentity.getStatusEffects().isEmpty()) {
                throw new CommandException("commands.effect.failure.notActive.all", livingentity.getName());
            }

            livingentity.clearStatusEffects();
            sendSuccess(source, this, "commands.effect.success.removed.all", livingentity.getName());
        } else {
            int i;
            try {
                i = parseInt(args[1], 1);
            } catch (InvalidNumberException invalidnumberexception) {
                StatusEffect statuseffect = StatusEffect.get(args[1]);
                if (statuseffect == null) {
                    throw invalidnumberexception;
                }

                i = statuseffect.id;
            }

            int j = 600;
            int l = 30;
            int k = 0;
            if (i >= 0 && i < StatusEffect.BY_ID.length && StatusEffect.BY_ID[i] != null) {
                StatusEffect statuseffect1 = StatusEffect.BY_ID[i];
                if (args.length >= 3) {
                    l = parseInt(args[2], 0, 1000000);
                    if (statuseffect1.isInstant()) {
                        j = l;
                    } else {
                        j = l * 20;
                    }
                } else if (statuseffect1.isInstant()) {
                    j = 1;
                }

                if (args.length >= 4) {
                    k = parseInt(args[3], 0, 255);
                }

                boolean flag = true;
                if (args.length >= 5 && "true".equalsIgnoreCase(args[4])) {
                    flag = false;
                }

                if (l > 0) {
                    StatusEffectInstance statuseffectinstance = new StatusEffectInstance(i, j, k, false, flag);
                    livingentity.addStatusEffect(statuseffectinstance);
                    sendSuccess(source, this, "commands.effect.success", new TranslatableText(statuseffectinstance.getName()), i, k, livingentity.getName(), l);
                } else if (livingentity.hasStatusEffect(i)) {
                    livingentity.removeStatusEffect(i);
                    sendSuccess(
                        source, this, "commands.effect.success.removed", new TranslatableText(statuseffect1.getTranslationKey()), livingentity.getName()
                    );
                } else {
                    throw new CommandException(
                        "commands.effect.failure.notActive", new TranslatableText(statuseffect1.getTranslationKey()), livingentity.getName()
                    );
                }
            } else {
                throw new InvalidNumberException("commands.effect.notFound", i);
            }
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, this.getPlayerNames());
        } else if (args.length == 2) {
            return suggestMatching(args, StatusEffect.getKeys());
        } else {
            return args.length == 5 ? suggestMatching(args, "true", "false") : null;
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
