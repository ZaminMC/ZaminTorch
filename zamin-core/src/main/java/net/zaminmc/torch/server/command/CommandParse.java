package net.zaminmc.torch.server.command;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.UUID;

/**
 * The 1.8.8 command parse helpers (AbstractCommand's own walks 1:1):
 * parsePlayer (selector, then uuid, then name, then the not-found refusal),
 * parseString (the space-joined tail), and the strict parseInt (the
 * "not a valid number" refusal — MathHelper.parseInt's throwing form).
 */
public final class CommandParse {

    private CommandParse() {
    }

    /** The reference's parsePlayer(source, s) 1:1. */
    public static PlayerSession parsePlayer(PlayerRegistry players, CommandSender source, String s) {
        PlayerSession serverplayerentity = TargetSelector.selectFirstPlayer(
                TargetSelector.sourceViewOf(source), s);
        if (serverplayerentity == null) {
            try {
                serverplayerentity = players.byUuid(UUID.fromString(s)).orElse(null);
            } catch (IllegalArgumentException illegalargumentexception) {
                // swallowed, the reference's own catch
            }
        }

        if (serverplayerentity == null) {
            serverplayerentity = players.byName(s).orElse(null);
        }

        if (serverplayerentity == null) {
            throw new IllegalArgumentException("That player could not be found");
        }
        return serverplayerentity;
    }

    /** The reference's parseString(args, startIndex): the space-joined tail. */
    public static String parseString(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /** The reference's parseInt's strict form: the "not a valid number" refusal. */
    public static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a valid number");
        }
    }
}
