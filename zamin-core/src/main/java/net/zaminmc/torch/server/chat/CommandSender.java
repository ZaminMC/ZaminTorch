package net.zaminmc.torch.server.chat;

import net.zaminmc.torch.server.command.TargetSelector;
import net.zaminmc.torch.server.player.PlayerSession;

/**
 * The origin of a command: an online player or the console (the historical
 * ICommandSender). Commands receive this, never raw wires; feedback flows back
 * through {@link #sendMessage}.
 */
public interface CommandSender {

    /** The display name of the sender ("Server" for the console). */
    String name();

    /** Delivers a command feedback line to this sender. */
    void sendMessage(String message);

    /** The permission level (0 = player, 4 = console-equivalent owner). */
    int opLevel();

    /** The sending player, or null when the command came from the console. */
    PlayerSession player();

    /**
     * The command-source position — the ~ origin, 1:1 the reference's
     * getCommandSourcePos: a player's body, the console's world origin,
     * and /execute's parsed coordinates for its proxied source. Commands
     * parsing ~-relative coordinates read this, never the body directly,
     * so the execute proxy re-anchors them.
     */
    default double[] sourcePos() {
        PlayerSession p = player();
        return p != null
                ? new double[] {p.position().x(), p.position().y(), p.position().z()}
                : new double[] {0.0, 0.0, 0.0};
    }

    /**
     * The command-source dimension — 1:1 the reference's
     * getCommandSourceWorld's id: a player's dimension, the console's 0
     * (the overworld), and /execute's entity dimension for its proxied
     * source.
     */
    default int sourceDimension() {
        PlayerSession p = player();
        return p != null ? p.dimension() : 0;
    }

    /**
     * The entity the source acts as — 1:1 the reference's asEntity: the
     * sending player, /execute's entity (a mob included), or null for the
     * console. Selector parsing (the c==1 self-bias) consults this.
     */
    default TargetSelector.Candidate asSourceEntity() {
        PlayerSession p = player();
        return p != null ? TargetSelector.Candidate.candidateOf(p) : null;
    }

    /**
     * The command's result reporting — 1:1 the reference's
     * CommandSource.addResult. The console's arm is empty (the
     * MinecraftServer's own override); the entity senders route to their
     * stat holder, the dispatch layer reports the registry-level counts.
     */
    default void addResult(net.zaminmc.torch.server.command.EngineCommandResults.Type type,
                           int result) {
    }
}
