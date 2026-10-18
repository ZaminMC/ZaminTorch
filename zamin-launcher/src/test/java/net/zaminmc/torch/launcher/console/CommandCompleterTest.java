package net.zaminmc.torch.launcher.console;

import java.util.ArrayList;
import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completion (§30.4.4): built-ins and registered commands complete the
 * FIRST word by prefix, case-insensitively, at any cursor position inside
 * that word; argument positions answer nothing; the registry view is an
 * immutable snapshot.
 */
class CommandCompleterTest {

    private final CommandCompleter completer = new CommandCompleter(
            () -> List.of("gamemode", "give", "op", "say"));

    private List<String> complete(String line, int cursor) {
        try {
            List<Candidate> candidates = new ArrayList<>();
            var parsed = new DefaultParser().parse(line, cursor,
                    org.jline.reader.Parser.ParseContext.COMPLETE);
            completer.complete(new LineReaderImpl(newTerminal()), parsed, candidates);
            return candidates.stream().map(Candidate::value).sorted().toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static DumbTerminal newTerminal() {
        try {
            return new DumbTerminal(new java.io.ByteArrayInputStream(new byte[0]),
                    new java.io.ByteArrayOutputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void emptyFirstWordOffersEveryCommand() {
        List<String> got = complete("", 0);
        assertEquals(List.of("gamemode", "give", "op", "save", "say", "shutdown", "state",
                "stop"), got, "built-ins + registry, sorted");
    }

    @Test
    void prefixMatchesCaseInsensitively() {
        assertEquals(List.of("save", "say"), complete("SA", 2));
        assertEquals(List.of("state", "stop"), complete("st", 2));
        assertEquals(List.of("gamemode"), complete("GAME", 4));
    }

    @Test
    void unknownPrefixOffersNothing() {
        assertTrue(complete("zzz", 3).isEmpty());
    }

    @Test
    void midWordCursorFollowsJLinesWholeWordModel() {
        // JLine's completion unit is the WHOLE current word regardless of the
        // cursor's position inside it (DefaultParser.parse("save", 2).word()
        // == "save"): candidates match the whole word and accept replaces it.
        // The console keeps JLine's native convention.
        assertEquals(List.of("save"), complete("save", 2));
        assertEquals(List.of("say"), complete("say", 1));
    }

    @Test
    void argumentPositionsAnswerNothing() {
        assertTrue(complete("give dia", 8).isEmpty(), "second word: no fabricated args");
        assertTrue(complete("say hello wo", 11).isEmpty());
        assertTrue(complete("stop ", 5).isEmpty(), "even a known command's args stay empty");
    }
}
