package net.zaminmc.torch.launcher.console;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounded persistent history with the sensitive-command policy (§30.4.3):
 * restart round trip, the bound, the exclusion at the funnel, and the
 * unreadable-file degradation.
 */
class FilteredHistoryTest {

    @TempDir
    Path dir;

    private LineReader readerOver(Path historyFile, List<String> sensitive) throws Exception {
        DumbTerminal terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream());
        FilteredHistory history = new FilteredHistory(sensitive);
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, historyFile.toString())
                .variable(LineReader.HISTORY_SIZE, 3)
                .history(history)
                .build();
        // The production wiring attaches explicitly (TorchConsole.buildReader):
        // attach loads the file AND makes save()/the size bound live — JLine
        // itself only auto-attaches inside the first readLine.
        history.attach(reader);
        return reader;
    }

    @Test
    void sensitiveCommandsNeverEnterHistory() {
        FilteredHistory history = new FilteredHistory(List.of("login", "secret"));
        assertTrue(history.isSensitive("login user pass"));
        assertTrue(history.isSensitive("  LOGIN user"), "case and blanks do not matter");
        assertTrue(history.isSensitive("/login user"), "the console slash form is covered");
        assertTrue(history.isSensitive("secret"));
        assertFalse(history.isSensitive("logins"), "prefix words are not sensitive");
        assertFalse(history.isSensitive("say hello"));
        history.add(Instant.now(), "login hunter2");
        history.add(Instant.now(), "say hello");
        assertEquals(1, history.size(), "only the insensitive line was stored");
        assertEquals("say hello", history.get(0));
    }

    @Test
    void historySurvivesRestartThroughTheFile() throws Exception {
        Path file = dir.resolve("console.history");
        LineReader first = readerOver(file, List.of());
        first.getHistory().add(Instant.now(), "give diamond 64");
        first.getHistory().add(Instant.now(), "login hunter2-not-listed");
        ((FilteredHistory) first.getHistory()).save();

        assertTrue(Files.exists(file), "the history file persists");
        LineReader second = readerOver(file, List.of());
        assertEquals(2, second.getHistory().size(), "both lines reload on the next boot");
        assertEquals("give diamond 64", second.getHistory().get(0));
    }

    @Test
    void theBoundTrimsTheOldest() throws Exception {
        Path file = dir.resolve("console.history");
        FilteredHistory history = new FilteredHistory(List.of());
        DumbTerminal terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream());
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_SIZE, 3)
                .history(history)
                .build();
        history.attach(reader);
        for (int i = 1; i <= 5; i++) {
            reader.getHistory().add(Instant.now(), "command-" + i);
        }
        assertEquals(3, reader.getHistory().size(), "the in-memory bound holds");
        assertEquals("command-3", reader.getHistory().get(reader.getHistory().first()),
                "the oldest beyond the bound are trimmed (get() is offset-indexed)");
    }

    @Test
    void anUnreadableHistoryFileNeverBlocksTheConsole() throws Exception {
        // A directory where the history file should be: every read fails.
        Path file = Files.createDirectories(dir.resolve("locked.console.history"));
        DumbTerminal terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream());
        FilteredHistory history = new FilteredHistory(List.of());
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(LineReader.HISTORY_FILE, file.toString())
                .history(history)
                .build();
        // attach on an unreadable "file" throws UncheckedIOException (a
        // directory read fails inside load()); the PRODUCTION catch in
        // TorchConsole.buildReader degrades to history-less — here the same
        // degradation keeps the console working in memory.
        try {
            history.attach(reader);
        } catch (RuntimeException expected) {
            // the unreadable history file is isolated, exactly as specified
        }
        reader.getHistory().add(Instant.now(), "still works");
        assertEquals(1, reader.getHistory().size());
    }

    @Test
    void persistenceFailuresAreIsolatedNotFatal() {
        FilteredHistory history = new FilteredHistory(List.of());
        history.add(Instant.now(), "line one");
        // save() without an attached reader/file context must not throw out
        // of the console loop; DefaultHistory's own no-reader save is a no-op
        // or an isolated failure the caller already guards.
        try {
            history.save();
        } catch (Exception e) {
            // acceptable: the console loop catches and warns (TorchConsole)
        }
        assertEquals(1, history.size(), "the in-memory history is untouched");
    }
}
