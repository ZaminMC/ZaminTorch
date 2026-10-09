package net.minecraft.server.command.handler;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.Command;
import net.minecraft.server.command.TargetSelector;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.Formatting;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandRegistry implements CommandHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Map<String, Command> commandsByName = Maps.newHashMap();
    private final Set<Command> commands = Sets.newHashSet();

    @Override
    public int run(CommandSource source, String command) {
        command = command.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        String[] astring = command.split(" ");
        String s = astring[0];
        astring = getCommandArgs(astring);
        Command commandx = this.commandsByName.get(s);
        int i = this.getIndexOfTargetSelector(commandx, astring);
        int j = 0;
        if (commandx == null) {
            TranslatableText translatabletext = new TranslatableText("commands.generic.notFound");
            translatabletext.getStyle().setColor(Formatting.RED);
            source.sendMessage(translatabletext);
        } else if (commandx.canUse(source)) {
            if (i > -1) {
                List<Entity> list = TargetSelector.select(source, astring[i], Entity.class);
                String s1 = astring[i];
                source.addResult(CommandResults.Type.AFFECTED_ENTITIES, list.size());

                for (Entity entity : list) {
                    astring[i] = entity.getUuid().toString();
                    if (this.run(source, astring, commandx, command)) {
                        j++;
                    }
                }

                astring[i] = s1;
            } else {
                source.addResult(CommandResults.Type.AFFECTED_ENTITIES, 1);
                if (this.run(source, astring, commandx, command)) {
                    j++;
                }
            }
        } else {
            TranslatableText translatabletext1 = new TranslatableText("commands.generic.permission");
            translatabletext1.getStyle().setColor(Formatting.RED);
            source.sendMessage(translatabletext1);
        }

        source.addResult(CommandResults.Type.SUCCESS_COUNT, j);
        return j;
    }

    protected boolean run(CommandSource source, String[] args, Command command, String rawCommand) {
        try {
            command.run(source, args);
            return true;
        } catch (IncorrectUsageException incorrectusageexception) {
            TranslatableText translatabletext2 = new TranslatableText(
                "commands.generic.usage", new TranslatableText(incorrectusageexception.getMessage(), incorrectusageexception.getArgs())
            );
            translatabletext2.getStyle().setColor(Formatting.RED);
            source.sendMessage(translatabletext2);
        } catch (CommandException commandexception) {
            TranslatableText translatabletext1 = new TranslatableText(commandexception.getMessage(), commandexception.getArgs());
            translatabletext1.getStyle().setColor(Formatting.RED);
            source.sendMessage(translatabletext1);
        } catch (Throwable throwable) {
            TranslatableText translatabletext = new TranslatableText("commands.generic.exception");
            translatabletext.getStyle().setColor(Formatting.RED);
            source.sendMessage(translatabletext);
            LOGGER.warn("Couldn't process command: '" + rawCommand + "'");
        }

        return false;
    }

    public Command register(Command command) {
        this.commandsByName.put(command.getName(), command);
        this.commands.add(command);

        for (String s : command.getAliases()) {
            Command commandx = this.commandsByName.get(s);
            if (commandx == null || !commandx.getName().equals(s)) {
                this.commandsByName.put(s, command);
            }
        }

        return command;
    }

    /**
     * Remove the command name from the arguments array
     */
    private static String[] getCommandArgs(String[] args) {
        String[] astring = new String[args.length - 1];
        System.arraycopy(args, 1, astring, 0, args.length - 1);
        return astring;
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String command, BlockPos pos) {
        String[] astring = command.split(" ", -1);
        String s = astring[0];
        if (astring.length == 1) {
            List<String> list = Lists.newArrayList();

            for (Entry<String, Command> entry : this.commandsByName.entrySet()) {
                if (AbstractCommand.doesStringStartWith(s, entry.getKey()) && entry.getValue().canUse(source)) {
                    list.add(entry.getKey());
                }
            }

            return list;
        } else {
            if (astring.length > 1) {
                Command commandx = this.commandsByName.get(s);
                if (commandx != null && commandx.canUse(source)) {
                    return commandx.getSuggestions(source, getCommandArgs(astring), pos);
                }
            }

            return null;
        }
    }

    @Override
    public List<Command> getAvailableCommands(CommandSource source) {
        List<Command> list = Lists.newArrayList();

        for (Command command : this.commands) {
            if (command.canUse(source)) {
                list.add(command);
            }
        }

        return list;
    }

    @Override
    public Map<String, Command> getCommands() {
        return this.commandsByName;
    }

    private int getIndexOfTargetSelector(Command command, String[] args) {
        if (command == null) {
            return -1;
        }

        for (int i = 0; i < args.length; i++) {
            if (command.hasTargetSelectorAt(args, i) && TargetSelector.matchesMultiple(args[i])) {
                return i;
            }
        }

        return -1;
    }
}
