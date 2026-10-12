package net.zaminmc.torch.event;

/**
 * Handler ordering across the {@link EventBus}.
 *
 * <p>Handlers receive events in ascending priority order within one
 * dispatch: {@code FIRST} before {@code EARLY} and so on. Ties inside one
 * priority run in registration order (stable). Use {@link #MONITOR} for
 * pure observation of the final outcome — after every earlier handler has
 * had its say — and never cancel or mutate from it.</p>
 */
public enum EventPriority {

    FIRST,
    EARLY,
    NORMAL,
    LATE,
    LAST,
    /** Observe-only: the last word on the event before the engine acts. */
    MONITOR;

    /** The default registration priority when none is given. */
    public static final EventPriority DEFAULT = NORMAL;
}
