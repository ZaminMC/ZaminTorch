package net.minecraft.server.command.exception;

public class CommandSyntaxException extends CommandException {
    public CommandSyntaxException() {
        this("commands.generic.snytax");
    }

    public CommandSyntaxException(String string, Object... objects) {
        super(string, objects);
    }
}
