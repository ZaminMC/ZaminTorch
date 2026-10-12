package net.zaminmc.torch.server.event;

import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.event.EventHandler;
import net.zaminmc.torch.event.EventPriority;
import net.zaminmc.torch.event.EventSubscription;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public event bus mechanics (§27.7): ordering, isolation, unsubscription,
 * exception continuation, and the registration-from-any-thread contract.
 */
class EventBusUnitTest {

    /** A minimal concrete event for the mechanics tests. */
    static class Ping implements Event {
        final int seq;

        Ping(int seq) {
            this.seq = seq;
        }
    }

    /** A cancellable event. */
    static final class Boom implements Event, net.zaminmc.torch.event.Cancellable {
        boolean cancelled;

        @Override
        public boolean cancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    /** A subtype: handlers for the base must receive it. */
    static final class SubPing extends Ping {
        SubPing(int seq) {
            super(seq);
        }
    }

    @Test
    void dispatchRunsInPriorityThenRegistrationOrder() {
        EngineEventBus bus = new EngineEventBus();
        List<String> order = new ArrayList<>();
        bus.on(Ping.class, EventPriority.MONITOR, e -> order.add("monitor"));
        bus.on(Ping.class, EventPriority.FIRST, e -> order.add("first"));
        bus.on(Ping.class, EventPriority.NORMAL, e -> order.add("normal-1"));
        bus.on(Ping.class, EventPriority.NORMAL, e -> order.add("normal-2"));
        bus.on(Ping.class, EventPriority.LAST, e -> order.add("last"));

        bus.fire(new Ping(1));

        assertEquals(List.of("first", "normal-1", "normal-2", "last", "monitor"), order);
    }

    @Test
    void cancellationIsVisibleToTheFiringCaller() {
        EngineEventBus bus = new EngineEventBus();
        bus.on(Boom.class, EventPriority.EARLY, boom -> boom.cancel());

        Boom fired = bus.fire(new Boom());

        assertTrue(fired.cancelled());
    }

    @Test
    void anEscapingExceptionDoesNotStopTheDispatch() {
        List<String> saw = new ArrayList<>();
        AtomicInteger reported = new AtomicInteger();
        EngineEventBus bus = new EngineEventBus(t -> reported.incrementAndGet());
        bus.on(Boom.class, EventPriority.FIRST, boom -> {
            throw new IllegalStateException("handler one fails");
        });
        bus.on(Boom.class, EventPriority.LATE, boom -> saw.add("late"));

        Boom fired = bus.fire(new Boom());

        assertFalse(fired.cancelled()); // the failing handler cancelled nothing
        assertEquals(List.of("late"), saw);
        assertEquals(1, reported.get());
    }

    @Test
    void unsubscribeIsIdempotentAndStopsDelivery() {
        EngineEventBus bus = new EngineEventBus();
        AtomicInteger hits = new AtomicInteger();
        EventSubscription sub = bus.on(Ping.class, e -> hits.incrementAndGet());

        bus.fire(new Ping(1));
        assertEquals(1, hits.get());

        sub.unsubscribe();
        sub.unsubscribe(); // idempotent
        assertFalse(sub.active());

        bus.fire(new Ping(2));
        assertEquals(1, hits.get()); // no second delivery
    }

    @Test
    void aHandlerMayUnsubscribeItselfMidDispatch() {
        EngineEventBus bus = new EngineEventBus();
        AtomicInteger hits = new AtomicInteger();
        EventSubscription[] self = new EventSubscription[1];
        self[0] = bus.on(Ping.class, e -> {
            hits.incrementAndGet();
            self[0].unsubscribe();
        });

        bus.fire(new Ping(1));
        bus.fire(new Ping(2));

        assertEquals(1, hits.get()); // the first fire delivered, the second did not
    }

    @Test
    void baseHandlersReceiveSubtypeEvents() {
        EngineEventBus bus = new EngineEventBus();
        List<String> order = new ArrayList<>();
        bus.on(Ping.class, e -> order.add("base"));
        bus.on(SubPing.class, e -> order.add("sub"));

        bus.fire(new SubPing(7));

        // The subtype's own handlers first, then the discovered base's.
        assertEquals(List.of("sub", "base"), order);
    }

    @Test
    void registrationIsSafeFromAnotherThread() throws InterruptedException {
        EngineEventBus bus = new EngineEventBus();
        CountDownLatch registered = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            bus.on(Ping.class, e -> { });
            registered.countDown();
        });
        other.start();
        assertTrue(registered.await(2, TimeUnit.SECONDS));

        assertEquals(1, bus.registeredHandlerCount());
        other.join();
    }

    @Test
    void handlersRegisteredForOneTypeDoNotReceiveAnother() {
        EngineEventBus bus = new EngineEventBus();
        List<String> saw = new ArrayList<>();
        bus.on(Ping.class, e -> saw.add("ping"));
        bus.on(Boom.class, e -> saw.add("boom"));

        bus.fire(new Boom());

        assertEquals(List.of("boom"), saw);
    }

    @Test
    void reentrantFireNestsLikeMethodCalls() {
        EngineEventBus bus = new EngineEventBus();
        List<String> order = new ArrayList<>();
        EventHandler<Ping> outer = new EventHandler<>() {
            @Override
            public void handle(Ping event) {
                if (event.seq == 1) {
                    order.add("outer-begin");
                    bus.fire(new Ping(2));
                    order.add("outer-end");
                } else {
                    order.add("inner");
                }
            }
        };
        bus.on(Ping.class, outer);

        bus.fire(new Ping(1));

        assertEquals(List.of("outer-begin", "inner", "outer-end"), order);
    }
}
