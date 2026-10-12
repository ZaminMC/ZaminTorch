package net.minecraft.server.command.exception;

public class CommandException extends Exception {
    private final Object[] args;

    public CommandException(String reason, Object... args) {
        super(reason);
        this.args = args;
    }

    public Object[] getArgs() {
        return this.args;
    }
}
