package net.zaminmc.torch.launcher.console;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.jline.reader.impl.history.DefaultHistory;

/**
 * Bounded persistent history with the sensitive-command policy
 * (architecture §30.3 "history"): commands whose FIRST word matches the
 * configured sensitive list are never stored — not in memory, not in the
 * history file — while everything else persists across restarts through
 * JLine's {@link DefaultHistory} (file + in-memory size capped by the
 * {@code console.history.limit} variable).
 *
 * <p>The filter intercepts {@code add(Instant, String)} — the single funnel
 * every history insertion path (the reader's auto-add on accepted lines
 * included) funnels through, per the {@link org.jline.reader.History}
 * interface's default {@code add(String)} delegating here.</p>
 */
public final class FilteredHistory extends DefaultHistory {

    private final Predicate<String> sensitive;

    public FilteredHistory(List<String> sensitiveCommands) {
        // The first token decides: "login secret" is sensitive when "login"
        // is listed; arguments never change the verdict.
        this.sensitive = line -> {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            int space = trimmed.indexOf(' ');
            String head = (space < 0 ? trimmed : trimmed.substring(0, space))
                    .toLowerCase(Locale.ROOT);
            int slash = head.startsWith("/") ? 1 : 0;
            if (slash == 1) {
                head = head.substring(1);
            }
            return sensitiveCommands.contains(head);
        };
    }

    /** @return true when the line's first word is on the sensitive list. */
    public boolean isSensitive(String line) {
        return sensitive.test(line);
    }

    @Override
    public void add(Instant time, String line) {
        if (line == null || sensitive.test(line)) {
            return; // never stored: not in memory, never persisted
        }
        super.add(time, line);
    }
}
