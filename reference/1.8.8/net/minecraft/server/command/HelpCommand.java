package net.minecraft.server.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.CommandNotFoundException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.InvalidNumberException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class HelpCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "help";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.help.usage";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("?");
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        List<Command> list = this.getAvailableCommands(source);
        int i = 7;
        int j = (list.size() - 1) / 7;
        int k = 0;

        try {
            k = args.length == 0 ? 0 : parseInt(args[0], 1, j + 1) - 1;
        } catch (InvalidNumberException invalidnumberexception) {
            Map<String, Command> map = this.getCommands();
            Command command = map.get(args[0]);
            if (command != null) {
                throw new IncorrectUsageException(command.getUsage(source));
            }

            if (MathHelper.parseInt(args[0], -1) != -1) {
                throw invalidnumberexception;
            }

            throw new CommandNotFoundException();
        }

        int l = Math.min((k + 1) * 7, list.size());
        TranslatableText translatabletext1 = new TranslatableText("commands.help.header", k + 1, j + 1);
        translatabletext1.getStyle().setColor(Formatting.DARK_GREEN);
        source.sendMessage(translatabletext1);

        for (int i1 = k * 7; i1 < l; i1++) {
            Command command1 = list.get(i1);
            TranslatableText translatabletext = new TranslatableText(command1.getUsage(source));
            translatabletext.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + command1.getName() + " "));
            source.sendMessage(translatabletext);
        }

        if (k == 0 && source instanceof PlayerEntity) {
            TranslatableText translatabletext2 = new TranslatableText("commands.help.footer");
            translatabletext2.getStyle().setColor(Formatting.GREEN);
            source.sendMessage(translatabletext2);
        }
    }

    protected List<Command> getAvailableCommands(CommandSource source) {
        List<Command> list = MinecraftServer.getInstance().getCommandHandler().getAvailableCommands(source);
        Collections.sort(list);
        return list;
    }

    protected Map<String, Command> getCommands() {
        return MinecraftServer.getInstance().getCommandHandler().getCommands();
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            Set<String> set = this.getCommands().keySet();
            return suggestMatching(args, set.toArray(new String[set.size()]));
        } else {
            return null;
        }
    }
}
