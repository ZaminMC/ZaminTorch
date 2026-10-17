package net.zaminmc.torch.server.command;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.text.Json;
import net.zaminmc.torch.server.text.Text;
import net.zaminmc.torch.server.text.TextJson;
import net.zaminmc.torch.server.text.TextUtils;

/**
 * The /tellraw command (TellRawCommand.java 1:1, op-2): the joined-string
 * JSON parse, updateForEntity resolving the component against the receiving
 * player (the reference's own entity argument), and the delivery as the
 * player's own system chat line (the serialized component at the SYSTEM
 * position — the reference's sendMessage(Component)).
 *
 * <p>Known engine adaptations (ledgered): the literal-English feedback
 * convention; the delivery rides the raw-JSON chat verb instead of the
 * plain-string line.</p>
 */
public final class TellRawCommand {

    private final PlayerRegistry players;

    public TellRawCommand(PlayerRegistry players) {
        this.players = players;
    }

    /** @return the feedback line, or null when already answered. */
    public String run(CommandSender source, String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: /tellraw <player> <raw json message>");
        }

        PlayerSession player = CommandParse.parsePlayer(players, source, args[0]);
        String s = CommandParse.parseString(args, 1);

        try {
            Text text = TextJson.fromJson(s);
            Text resolved = TextUtils.updateForEntity(
                    TargetSelector.sourceViewOf(source), text, player);
            player.link().sendChatComponent(TextJson.toJson(resolved), 1);
        } catch (Json.JsonFormatException jsonparseexception) {
            throw new IllegalArgumentException(
                    "Invalid json: " + (jsonparseexception.getMessage() == null
                            ? "" : jsonparseexception.getMessage()));
        }
        return null; // the reference answers nothing on success
    }
}
