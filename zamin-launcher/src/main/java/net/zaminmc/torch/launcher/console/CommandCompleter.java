package net.zaminmc.torch.launcher.console;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * Tab completion for the console (architecture §30.3 "completion"): the
 * FIRST word only, matched case-insensitively by prefix against the
 * launcher's administrative built-ins plus the command-service registry's
 * safely published snapshot. The snapshot supplier returns an immutable
 * copy; completion never consults live world/player/entity state and never
 * crosses an ownership boundary — a slow simulation owner can never stall
 * the console thread (30.4.4).
 *
 * <p>Argument positions (wordIndex &gt; 0) answer nothing: per-command
 * suggestion surfaces arrive with their own slices, and an honest empty
 * candidate list today is better than a fabricated one.</p>
 */
public final class CommandCompleter implements Completer {

    /** The console's own administrative commands (the launcher switch's set). */
    public static final List<String> BUILTINS = List.of("save", "shutdown", "state", "stop");

    private final Supplier<List<String>> commandNames;

    public CommandCompleter(Supplier<List<String>> commandNames) {
        this.commandNames = commandNames;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (line == null || line.wordIndex() != 0) {
            return; // first word only; argument positions answer nothing
        }
        String word = line.word() == null ? "" : line.word();
        for (String name : BUILTINS) {
            addIfPrefix(word, name, candidates);
        }
        // The registry snapshot: one immutable copy, safe to read from the
        // console thread while the engine registers more commands.
        List<String> names = commandNames.get();
        if (names != null) {
            for (String name : names) {
                addIfPrefix(word, name, candidates);
            }
        }
    }

    private static void addIfPrefix(String word, String name, List<Candidate> candidates) {
        if (name != null
                && name.toLowerCase(Locale.ROOT)
                        .startsWith(word.toLowerCase(Locale.ROOT))) {
            candidates.add(new Candidate(name));
        }
    }
}
