package net.zaminmc.torch.server.concurrent;

/**
 * The stale-compute-result failure (the design's §6-A: "a stale result is
 * discarded and recomputed or handled by a documented fallback; it is never
 * blindly applied"). Carries the ticket's origin (domain, generation) and
 * the domain's current state so diagnostics can show exactly how far the
 * world moved under the computation.
 */
public class StaleResultException extends IllegalStateException {

    private final String ticketDomain;
    private final long ticketGeneration;
    private final String currentDomain;
    private final long currentGeneration;

    public StaleResultException(String ticketDomain, long ticketGeneration,
                                String currentDomain, long currentGeneration) {
        super("Stale compute result: ticket from domain '" + ticketDomain
                + "' at generation " + ticketGeneration
                + " cannot apply to domain '" + currentDomain
                + "' now at generation " + currentGeneration);
        this.ticketDomain = ticketDomain;
        this.ticketGeneration = ticketGeneration;
        this.currentDomain = currentDomain;
        this.currentGeneration = currentGeneration;
    }

    public String ticketDomain() {
        return ticketDomain;
    }

    public long ticketGeneration() {
        return ticketGeneration;
    }

    public String currentDomain() {
        return currentDomain;
    }

    public long currentGeneration() {
        return currentGeneration;
    }
}
