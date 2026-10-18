package net.zaminmc.torch.launcher.console;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * The FILE sink's formatter (architecture §30.3 "formatting" +
 * "output policy"): the console look plus the logger/source name, with the
 * full throwable — and guaranteed free of ANSI escape sequences (raw ESC
 * bytes inside a logged message are stripped by {@link AnsiSanitizer}).
 *
 * <p>Legacy § codes stay verbatim: they are literal message content (chat
 * text), not terminal control bytes, and the file is the diagnostic record
 * the engine writes them exactly as the message carried them. The TERMINAL
 * sink alone follows the translate-or-strip rule.</p>
 */
public final class LogFileFormatter extends Formatter {

    /** The file look: [time LEVEL] logger: message + throwable. */
    private static final String FORMAT = "[%1$tH:%1$tM:%1$tS %4$s] %3$s: %5$s%6$s%n";

    @Override
    public String format(LogRecord record) {
        String message = AnsiSanitizer.stripEscapes(record.getMessage());
        String throwable = "";
        if (record.getThrown() != null) {
            java.io.StringWriter string = new java.io.StringWriter(256);
            record.getThrown().printStackTrace(new java.io.PrintWriter(string));
            throwable = "\n" + string;
        }
        StringBuilder out = new StringBuilder(160);
        try (java.util.Formatter formatter = new java.util.Formatter(out)) {
            formatter.format(FORMAT, record.getMillis(), null,
                    record.getLoggerName() == null ? "torch" : record.getLoggerName(),
                    record.getLevel().getName(), message, throwable);
        }
        return out.toString();
    }
}
