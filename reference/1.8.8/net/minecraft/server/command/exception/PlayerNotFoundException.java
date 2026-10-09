package net.minecraft.server.command.exception;

public class PlayerNotFoundException extends CommandException {
    public PlayerNotFoundException() {
        this("commands.generic.player.notFound");
    }

    public PlayerNotFoundException(String string, Object... objects) {
        super(string, objects);
    }
}
