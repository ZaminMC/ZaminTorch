package net.minecraft.server.command.exception;

public class InvalidNumberException extends CommandException {
    public InvalidNumberException() {
        this("commands.generic.num.invalid");
    }

    public InvalidNumberException(String string, Object... objects) {
        super(string, objects);
    }
}
