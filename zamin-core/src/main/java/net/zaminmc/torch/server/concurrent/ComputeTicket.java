package net.zaminmc.torch.server.concurrent;

/**
 * The versioned compute-result envelope (the design's §6-A "parallel
 * calculation, serialized authoritative application"): the worker computes
 * over an immutable snapshot, the result ships with the ownership generation
 * it was computed against, and the owning domain validates the ticket before
 * applying anything. A result computed against a superseded generation is
 * rejected — it is never blindly applied after the world moved (the
 * stale-path-result rule and the Folia EntityScheduler evidence,
 * {@code docs/FOLIA_FORENSIC_AUDIT.md} §4-B and §5 case 4).
 *
 * <p>Instances are immutable and safe to cross threads.</p>
 *
 * @param <T> the computed result type (must be treated as immutable by both sides)
 */
public final class ComputeTicket<T> {

    private final T result;
    private final long generation;
    private final String domainName;

    private ComputeTicket(T result, long generation, String domainName) {
        this.result = result;
        this.generation = generation;
        this.domainName = domainName;
    }

    /** Captures a result against the domain's current generation. */
    public static <T> ComputeTicket<T> of(OwnershipDomain domain, T result) {
        return new ComputeTicket<>(result, domain.generation(), domain.name());
    }

    /** @return the computed payload (never null; compute failures do not produce tickets). */
    public T result() {
        return result;
    }

    /** The generation the computation ran against. */
    public long generation() {
        return generation;
    }

    /** @return whether this ticket is still applicable to the domain (same generation, same domain). */
    public boolean isApplicableTo(OwnershipDomain domain) {
        return generation == domain.generation() && domainName.equals(domain.name());
    }

    /**
     * Validates and returns the payload for application by the domain's
     * authority.
     *
     * @throws StaleResultException when the domain moved past the ticket's
     *         generation (transfer, migration) or the ticket belongs to
     *         another domain — the caller discards and recomputes
     */
    public T resultOrThrow(OwnershipDomain domain) {
        if (!isApplicableTo(domain)) {
            throw new StaleResultException(domainName, generation,
                    domain.name(), domain.generation());
        }
        return result;
    }
}
