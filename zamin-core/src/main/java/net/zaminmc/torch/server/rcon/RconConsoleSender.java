package net.zaminmc.torch.server.rcon;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.player.PlayerSession;

/**
 * The remote console's command source — the engine shape of the reference's
 * remote-console sink (reference/1.8.8/net/minecraft/server/Console.java):
 * full operator rights ({@code canUseCommand} always true, reference line
 * 22), the name {@code "Rcon"} (reference line 12), and every feedback line
 * appended to one capture buffer with NO separator (reference line 25 —
 * the vanilla concatenation quirk commands are written around).
 *
 * <p>The engine-side difference, named: the reference's sink is a static
 * singleton ({@code Console.getInstance()}) whose {@code destroy()} reset
 * runs before each command — safe only because vanilla serializes remote
 * commands. The engine hands every {@code runRconCommand} call a fresh
 * sender instead: two concurrent RCON connections must never interleave
 * into one shared buffer (§6.4's no-shared-mutable-state law). The reset and
 * read verbs stay 1:1 ({@code resetLog}/{@code chatBuffer} = the
 * reference's {@code destroy()}/{@code getTextAsString()} callers).</p>
 */
public final class RconConsoleSender implements CommandSender {

    private final StringBuffer text = new StringBuffer();

    @Override
    public String name() {
        return "Rcon";
    }

    /**
     * The reference line 24-26: append, never newline-separated — and the
     * capture reads the UNFORMATTED face ({@code message.getString()},
     * BaseText lines 60-68: the concatenated {@code getContent()} alone,
     * the style's color NEVER in the text). The engine's legacy-string
     * model embeds the {@code §} codes the reference carries in the
     * {@link net.zaminmc.torch.server.text.Style} instead, so the sink
     * strips them here — the remote console's buffer answers the same
     * plain text the reference's {@code getString()} would (the player
     * wire keeps the red: that surface rides the formatted face).
     */
    @Override
    public void sendMessage(String message) {
        this.text.append(stripLegacyFormatting(message));
    }

    /** The {@code getString()} law over the engine's legacy strings. */
    private static String stripLegacyFormatting(String message) {
        if (message.indexOf('§') < 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '§' && i + 1 < message.length()) {
                i++; // skip the code char (the 0-9a-fk-or band)
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** The reference line 22: the remote console answers every permission gate. */
    @Override
    public int opLevel() {
        return 4;
    }

    /** The console-shaped source: no player body (reference {@code asEntity} null). */
    @Override
    public PlayerSession player() {
        return null;
    }

    /**
     * The result-reporting arm (the reference's empty override, line 36) —
     * the engine's default no-op already carries it; the override exists to
     * pin the 1:1 citation.
     */
    @Override
    public void addResult(net.zaminmc.torch.server.command.EngineCommandResults.Type type,
                          int result) {
        // empty, the reference's own arm
    }

    /** The reference's {@code destroy()} caller shape: the buffer reset. */
    public void resetLog() {
        this.text.setLength(0);
    }

    /** The reference's {@code getTextAsString()} shape: the captured command output. */
    public String chatBuffer() {
        return this.text.toString();
    }
}
