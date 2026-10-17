package net.zaminmc.torch.server.command;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.text.Json;
import net.zaminmc.torch.server.text.Text;
import net.zaminmc.torch.server.text.TextJson;
import net.zaminmc.torch.server.text.TextUtils;

/**
 * The /title command (TitleCommand.java 1:1, op-2): the
 * title/subtitle/times/clear/reset arms over the 0x45 wire verbs — the
 * args gate order, the case-insensitive type names with the TITLE default,
 * the joined-string JSON parse with the tellraw jsonException refusal, and
 * updateForEntity resolving the component against the target player before
 * the wire.
 *
 * <p>Known engine adaptations (ledgered): the feedback is literal English
 * mirroring the en_US locale (the engine carries no i18n layer — every
 * builtin command does the same); the selector and name parses ride
 * CommandParse over the engine's registry.</p>
 */
public final class TitleCommand {

    private final PlayerRegistry players;

    public TitleCommand(PlayerRegistry players) {
        this.players = players;
    }

    /** @return the feedback line, or null when already answered. */
    public String run(CommandSender source, String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: /title <player> <title|subtitle|times|clear|reset> ...");
        }

        if (args.length < 3) {
            if ("title".equals(args[1]) || "subtitle".equals(args[1])) {
                throw new IllegalArgumentException("Usage: /title <player> title <string>");
            }

            if ("times".equals(args[1])) {
                throw new IllegalArgumentException(
                        "Usage: /title <player> times <fadeIn> <stay> <fadeOut>");
            }
        }

        PlayerSession player = CommandParse.parsePlayer(players, source, args[0]);
        Type type = Type.byName(args[1]);
        if (type != Type.CLEAR && type != Type.RESET) {
            if (type == Type.TIMES) {
                if (args.length != 5) {
                    throw new IllegalArgumentException(
                            "Usage: /title <player> <title|subtitle|times|clear|reset> ...");
                }

                int i = CommandParse.parseInt(args[2]);
                int j = CommandParse.parseInt(args[3]);
                int k = CommandParse.parseInt(args[4]);
                player.link().titleTimes(i, j, k);
                return "Title command successfully executed";
            } else {
                if (args.length < 3) {
                    throw new IllegalArgumentException(
                            "Usage: /title <player> <title|subtitle|times|clear|reset> ...");
                }

                String s = CommandParse.parseString(args, 2);

                Text text;
                try {
                    text = TextJson.fromJson(s);
                } catch (Json.JsonFormatException jsonparseexception) {
                    // The reference's root-cause walk: the tellraw
                    // jsonException with the underlying message.
                    throw new IllegalArgumentException(
                            "Invalid json: " + (jsonparseexception.getMessage() == null
                                    ? "" : jsonparseexception.getMessage()));
                }

                Text resolved = TextUtils.updateForEntity(
                        TargetSelector.sourceViewOf(source), text, player);
                String json = TextJson.toJson(resolved);
                if (type == Type.SUBTITLE) {
                    player.link().titleSubtitle(json);
                } else {
                    player.link().titleTitle(json);
                }
                return "Title command successfully executed";
            }
        } else {
            if (args.length != 2) {
                throw new IllegalArgumentException(
                        "Usage: /title <player> <title|subtitle|times|clear|reset> ...");
            }

            if (type == Type.CLEAR) {
                player.link().titleClear();
            } else {
                player.link().titleReset();
            }
            return "Title command successfully executed";
        }
    }

    /** The reference's TitlesS2CPacket.Type: byName's case-insensitive default-TITLE walk. */
    public enum Type {
        TITLE,
        SUBTITLE,
        TIMES,
        CLEAR,
        RESET;

        public static Type byName(String name) {
            for (Type type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return TITLE;
        }

        public static String[] getNames() {
            String[] names = new String[values().length];
            int i = 0;
            for (Type type : values()) {
                names[i++] = type.name().toLowerCase();
            }
            return names;
        }
    }
}
