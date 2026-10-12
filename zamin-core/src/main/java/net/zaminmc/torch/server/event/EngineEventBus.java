package net.zaminmc.torch.server.event;

import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.event.EventBus;
import net.zaminmc.torch.event.EventHandler;
import net.zaminmc.torch.event.EventPriority;
import net.zaminmc.torch.event.EventSubscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The engine's public event bus (architecture §27.7).
 *
 * <p>Threading model: subscriptions live in concurrent structures, so
 * registration and unsubscription are safe from any thread. Dispatch runs
 * on the firing engine context; the per-type handler snapshot is taken
 * once per dispatch, so a handler mutating the registry mid-dispatch
 * affects the next dispatch, not the in-flight one — the documented
 * contract.</p>
 *
 * <p>Ordering: ascending {@link EventPriority}, then registration order
 * within a priority. Handler exceptions are caught, reported, and the
 * dispatch continues — an exception never aborts the engine action unless
 * a handler cancelled first. MONITOR is an observe-only convention: the
 * bus cannot intercept a POJO's setter, so engines document the contract
 * and tests pin the ordering instead.</p>
 */
public final class EngineEventBus implements EventBus {

    private static final Logger LOGGER = Logger.getLogger(EngineEventBus.class.getName());

    /** One handler under its priority slot. */
    private record Entry(EventHandler<Event> handler) {
    }

    private static final class TypeSlots {
        final List<Entry>[] byPriority = makeSlots();

        @SuppressWarnings("unchecked")
        private static List<Entry>[] makeSlots() {
            List<Entry>[] slots = new List[EventPriority.values().length];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = new CopyOnWriteArrayList<>();
            }
            return slots;
        }

        /** The flat snapshot in priority-then-registration order. */
        List<Entry> snapshot() {
            List<Entry> flat = new ArrayList<>();
            for (List<Entry> slot : byPriority) {
                flat.addAll(slot);
            }
            return flat;
        }
    }

    private final Map<Class<? extends Event>, TypeSlots> types = new ConcurrentHashMap<>();
    private final Consumer<Throwable> exceptionSink;

    public EngineEventBus() {
        this(null);
    }

    /**
     * @param exceptionSink optional test hook receiving handler failures;
     *                       production leaves it null and the bus reports.
     */
    public EngineEventBus(Consumer<Throwable> exceptionSink) {
        this.exceptionSink = exceptionSink;
    }

    @Override
    public <T extends Event> EventSubscription on(Class<T> type, EventPriority priority,
                                                   EventHandler<? super T> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(handler, "handler");
        TypeSlots slots = types.computeIfAbsent(type, k -> new TypeSlots());
        @SuppressWarnings("unchecked")
        EventHandler<Event> erased = (EventHandler<Event>) handler;
        Entry entry = new Entry(erased);
        slots.byPriority[priority.ordinal()].add(entry);
        AtomicBoolean active = new AtomicBoolean(true);
        return new EventSubscription() {
            @Override
            public void unsubscribe() {
                if (active.compareAndSet(true, false)) {
                    slots.byPriority[priority.ordinal()].remove(entry);
                }
            }

            @Override
            public boolean active() {
                return active.get();
            }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> T fire(T event) {
        Objects.requireNonNull(event, "event");
        // Walk the class hierarchy so a handler registered for a base event
        // class also receives its subtypes. Terminal classes dispatch once.
        for (Class<?> c = event.getClass(); c != null && Event.class.isAssignableFrom(c);
                c = c.getSuperclass()) {
            fireOne((Class<? extends Event>) c, event);
        }
        return event;
    }

    private void fireOne(Class<? extends Event> type, Event event) {
        TypeSlots slots = types.get(type);
        if (slots == null) {
            return;
        }
        for (Entry entry : slots.snapshot()) {
            try {
                entry.handler().handle(event);
            } catch (Throwable t) {
                if (exceptionSink != null) {
                    exceptionSink.accept(t);
                } else {
                    LOGGER.warning(() -> "Event handler failed for " + event.eventName()
                            + ": " + t);
                }
                // The contract: report and continue; the next handler runs.
            }
        }
    }

    /** Test/ops read: how many handlers are registered (all types). */
    public int registeredHandlerCount() {
        int count = 0;
        for (TypeSlots slots : types.values()) {
            for (List<Entry> slot : slots.byPriority) {
                count += slot.size();
            }
        }
        return count;
    }

    /** Test read: the discovered dispatch order (priority then registration). */
    List<EventPriority> prioritiesFor(Class<? extends Event> type) {
        TypeSlots slots = types.get(type);
        if (slots == null) {
            return Collections.emptyList();
        }
        List<EventPriority> out = new ArrayList<>();
        EventPriority[] values = EventPriority.values();
        for (int i = 0; i < slots.byPriority.length; i++) {
            out.addAll(Collections.nCopies(slots.byPriority[i].size(), values[i]));
        }
        return out;
    }
}
