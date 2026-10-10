package net.zaminmc.torch.server.concurrent;

/**
 * The diagnosable ownership failure (the design's §15 enforcement: "the
 * default should be an explicit, diagnosable failure … not silent
 * corruption"). Carries the domain, the refused operation, and the expected
 * versus actual executor threads. Extends {@link IllegalStateException} so
 * the historical wrong-thread world guard's contract (an unchecked failure
 * on out-of-context mutation) is preserved for existing callers.
 */
public class OwnershipViolationException extends IllegalStateException {

    private final String domain;
    private final String operation;

    public OwnershipViolationException(String domain, String operation,
                                       String expectedExecutor, String actualExecutor) {
        super("Ownership violation in domain '" + domain + "': operation '" + operation
                + "' requires the domain's bound executor"
                + (expectedExecutor == null ? " (none bound)" : " ('" + expectedExecutor + "'")
                + ") but ran on '" + actualExecutor + "'");
        this.domain = domain;
        this.operation = operation;
    }

    /** The domain whose context was violated. */
    public String domain() {
        return domain;
    }

    /** The refused operation ("setBlock", "enter", …). */
    public String operation() {
        return operation;
    }
}
