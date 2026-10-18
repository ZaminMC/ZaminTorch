package net.zaminmc.torch.launcher.console;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The terminal sink (architecture §30.3 "input and redraw" + "output
 * policy"): a JUL {@link Handler} that prints log records ABOVE the line the
 * operator is currently editing (JLine redraws the prompt and buffer), with
 * an independent terminal-only threshold and a bounded asynchronous handoff
 * so a slow terminal can never stall a logging thread.
 *
 * <p>Output policy, as specified: a terminal threshold may hide records from
 * the SCREEN but the file sink (the plain JUL FileHandler attached beside
 * this bridge) keeps every record — this handler filters, it never discards
 * from persistence. The handoff is one bounded queue drained by one writer
 * thread; when the queue is full the record is dropped from the screen, the
 * drop is counted, and a rate-limited loud warning is emitted so silent
 * starvation can never happen (30.4.7's backpressure test).</p>
 *
 * <p>Formatting: the Paper console look ({@code [HH:mm:ss LEVEL]: message})
 * with full throwable details; the level is colored when the terminal speaks
 * ANSI; legacy § codes follow {@link AnsiSanitizer}'s documented
 * translate-or-strip rule; raw ESC sequences never pass through.</p>
 */
public final class TerminalLogBridge extends Handler implements AutoCloseable {

    /** Where a formatted line lands (the interactive console or the plain fallback). */
    public interface Output {
        /** Prints one already-formatted line above the edited input line. */
        void printAbove(String line);
    }

    private static final String FORMAT = "[%1$tH:%1$tM:%1$tS %4$s]: %5$s%6$s%n";
    /** The loud-warning cadence when the bounded queue overflows. */
    private static final int DROP_WARN_EVERY = 100;

    private final Level threshold;
    private volatile boolean ansi;
    private final Output output;
    private final java.util.concurrent.BlockingQueue<String> queue;
    private final Thread writer;
    private final java.util.concurrent.atomic.AtomicLong dropped = new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean closed;

    /** One shared logger for the bridge's own diagnostics (never routed back). */
    private static final Logger BRIDGE_LOG = Logger.getLogger(TerminalLogBridge.class.getName());

    public TerminalLogBridge(ConsoleConfig config, Output output, boolean ansi) {
        this.threshold = config.terminalThreshold();
        this.ansi = ansi;
        this.output = output;
        this.queue = new java.util.concurrent.LinkedBlockingQueue<>(config.terminalQueueCapacity());
        this.writer = new Thread(this::writerLoop, "torch-console-writer");
        this.writer.setDaemon(true);
        setLevel(Level.ALL);
        setFilter(null);
    }

    /** Sets whether the terminal speaks ANSI (the console decides once, at terminal setup). */
    public void setAnsi(boolean ansi) {
        this.ansi = ansi;
    }

    private boolean ansi() {
        return ansi;
    }

    /** Starts the writer thread (the console calls this once the terminal is up). */
    public void start() {
        if (!writer.isAlive()) {
            writer.start();
        }
    }

    @Override
    public void publish(LogRecord record) {
        if (closed || record == null) {
            return;
        }
        if (record.getLevel().intValue() < threshold.intValue()) {
            return; // hidden from the screen only; the file sink keeps it
        }
        String line = format(record);
        if (!queue.offer(line)) {
            long count = dropped.incrementAndGet();
            if (count % DROP_WARN_EVERY == 1) {
                // Loud, rate-limited, and NOT routed through this bridge (no loop).
                BRIDGE_LOG.warning("[console] terminal queue full; " + count
                        + " screen lines dropped so far (the file log keeps every record)");
            }
        }
    }

    /** Formats one record: Paper console look, throwable details, color when ANSI. */
    String format(LogRecord record) {
        String message = AnsiSanitizer.stripEscapes(record.getMessage());
        String throwable = throwableText(record);
        StringBuilder sb = new StringBuilder(128);
        try (Formatter formatter = new Formatter(sb)) {
            String level = record.getLevel().getName();
            formatter.format(FORMAT, record.getMillis(), null, null, level,
                    message, throwable);
        }
        String line = sb.toString();
        // The message body may still carry legacy § codes: translate for a
        // color terminal, strip for a plain one (the documented rule).
        return ansi() ? AnsiSanitizer.translateLegacy(line)
                      : AnsiSanitizer.stripLegacy(line);
    }

    private String throwableText(LogRecord record) {
        Throwable thrown = record.getThrown();
        if (thrown == null) {
            return "";
        }
        StringWriter string = new StringWriter(256);
        try (PrintWriter print = new PrintWriter(string)) {
            print.println();
            thrown.printStackTrace(print);
        }
        return string.toString();
    }

    private void writerLoop() {
        try {
            while (!closed || !queue.isEmpty()) {
                String line;
                try {
                    line = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    continue; // drain what remains, then the closed flag ends it
                }
                if (line != null) {
                    output.printAbove(line);
                }
            }
        } catch (RuntimeException e) {
            // A terminal write failure must not propagate into logging
            // threads; the bridge dies quietly and the file sink continues.
            BRIDGE_LOG.warning("[console] terminal writer stopped: " + e);
        }
    }

    /** The screen-drop telemetry (30.4.7's observable backpressure). */
    public long droppedCount() {
        return dropped.get();
    }

    @Override
    public void flush() {
        // The writer thread owns the terminal; nothing to flush synchronously.
    }

    /** Stops accepting records, drains the queue, and joins the writer bounded. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
