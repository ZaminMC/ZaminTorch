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

    /**
     * The reference's parseEntity(source, s) 1:1: the any-kind ladder —
     * selector first, then the player registry by name, then by uuid.
     * Mob-kind matches only arrive through selectors (the reference's own
     * shape: the non-selector arms consult the player manager).
     */
    public static TargetSelector.Candidate parseEntity(
            PlayerRegistry players, CommandSender source, String s) {
        TargetSelector.Candidate entity = TargetSelector.selectFirst(
                TargetSelector.sourceViewOf(source), s);
        if (entity == null) {
            PlayerSession byName = players.byName(s).orElse(null);
            if (byName != null) {
                return TargetSelector.Candidate.candidateOf(byName);
            }
            try {
                PlayerSession byUuid = players.byUuid(UUID.fromString(s)).orElse(null);
                if (byUuid != null) {
                    return TargetSelector.Candidate.candidateOf(byUuid);
                }
            } catch (IllegalArgumentException illegalargumentexception) {
                // swallowed, the reference's own catch
            }
        }

        if (entity == null) {
            throw new IllegalArgumentException("That entity could not be found");
        }
        return entity;
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

    /**
     * The reference's parseInt(s, min, max): the strict parse plus the
     * at-least/at-most bounds refusals (AbstractCommand's own walk).
     */
    public static int parseInt(String s, int min, int max) {
        int i = parseInt(s);
        if (i < min) {
            throw new IllegalArgumentException("Value must be at least " + min);
        }
        if (i > max) {
            throw new IllegalArgumentException("Value must be at most " + max);
        }
        return i;
    }

    /** The reference's parseDouble(s): the "'%s' is not a valid number" refusal. */
    public static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a valid number");
        }
    }

    /** The reference's parseDouble(s, min): the min-bounded form. */
    public static double parseDouble(String s, double min) {
        return parseDouble(s, min, Double.MAX_VALUE);
    }

    /**
     * The reference's parseDouble(s, min, max): the strict parse plus the
     * at-least/at-most bounds refusals.
     */
    public static double parseDouble(String s, double min, double max) {
        double d = parseDouble(s);
        if (d < min) {
            throw new IllegalArgumentException("Value must be at least " + min);
        }
        if (d > max) {
            throw new IllegalArgumentException("Value must be at most " + max);
        }
        return d;
    }

    /**
     * The reference's parseBoolean(s): true/1 and false/0, anything else
     * the "'%s' is not a valid boolean value" refusal.
     */
    public static boolean parseBoolean(String s) {
        if (s.equals("true") || s.equals("1")) {
            return true;
        }
        if (s.equals("false") || s.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("'" + s + "' is not a valid boolean value");
    }

    /**
     * The reference's parseCoordinate(c, s, false): the ~-relative form
     * with the ±30000000 world bounds (execute's own parse walk — a bare
     * {@code ~} answers the source coordinate, {@code ~n} offsets it, a
     * plain number stands alone).
     */
    public static double parseCoordinate(double c, String s) {
        return parseCoordinate(c, s, false);
    }

    /**
     * The reference's parseCoordinate(c, s, center): the ~-relative form
     * with the ±30000000 world bounds; a plain integer coordinate gains
     * the +0.5 block center when {@code center} (parseBlockPos's own
     * walk).
     */
    public static double parseCoordinate(double c, String s, boolean center) {
        boolean tilde = s.startsWith("~");
        double d = tilde ? c : 0.0;
        if (!tilde || s.length() > 1) {
            String t = tilde ? s.substring(1) : s;
            boolean decimal = t.contains(".");
            d += parseDouble(t);
            if (!decimal && !tilde && center) {
                d += 0.5;
            }
        }
        if (d < -30000000.0) {
            throw new IllegalArgumentException("Value must be at least -30000000");
        }
        if (d > 30000000.0) {
            throw new IllegalArgumentException("Value must be at most 30000000");
        }
        return d;
    }
}
