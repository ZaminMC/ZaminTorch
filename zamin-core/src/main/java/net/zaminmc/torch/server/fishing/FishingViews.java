package net.zaminmc.torch.server.fishing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The public fishing view (§27.3's mechanics family — the Slice 67/69
 * exposure over the {@link FishingSystem}): the engine's own bobber
 * census, catch-context rows, and §27.7 interception family dressed as
 * the {@code net.zaminmc.torch.fishing.Fishing} plugin surface —
 * {@code Server.fishing()}'s answer.
 *
 * <p><b>The §27.5 handle law:</b> every census read resolves to the
 * LIVE system — {@link FishingSystem#all()}/{@link
 * FishingSystem#bobberOf(int)} under the engine's own monitor (the
 * §27.6 snapshot protocol the engine's synchronized arms carry), each
 * row an immutable copy of the reference's own bobber state model (the
 * position/velocity walk, the grounded/hooked/biting flags). A read
 * from a foreign thread reflects the census at some instant between
 * ticks, never a half-written walk.</p>
 *
 * <p><b>The registration verbs:</b> the api interceptors and the loot
 * override adapt onto the engine's own seams (the engine's nested
 * contracts run the walk) — the wrapper map below carries the api
 * instance to its engine wrapper so the removal answers symmetrically.
 * One instance registers once (a second add is a no-op — the removal's
 * symmetric answer).</p>
 *
 * <p>Deliberately NOT surfaced: the engine's own wiring — {@code
 * WorldQuery}/{@code HookResolver}/{@code CatchSink} (the world's
 * raytrace/water/sky reads, the entity clip, the drop/XP/sound sinks)
 * and the wire {@code Listener} (the protocol adapter's own) are
 * engine-internal, not plugin surface.</p>
 */
public final class FishingViews implements net.zaminmc.torch.fishing.Fishing {

    private final FishingSystem system;

    /** The api interceptor to its engine wrapper (the removal's lookup). */
    private final Map<net.zaminmc.torch.fishing.Fishing.CatchInterceptor,
            FishingSystem.CatchInterceptor> interceptorWrappers =
            new ConcurrentHashMap<>();

    public FishingViews(FishingSystem system) {
        this.system = Objects.requireNonNull(system, "system");
    }

    @Override
    public List<net.zaminmc.torch.fishing.Fishing.Bobber> bobbers() {
        // §27.6: the snapshot copies under the system's monitor (the
        // same monitor the census-mutating arms hold) — the copy is
        // tear-free against the tick lane, any calling thread.
        synchronized (system) {
            return system.all().stream().map(FishingViews::row).toList();
        }
    }

    @Override
    public Optional<net.zaminmc.torch.fishing.Fishing.Bobber> bobberOf(int anglerId) {
        synchronized (system) {
            FishingSystem.Bobber bobber = system.bobberOf(anglerId);
            return bobber == null ? Optional.empty() : Optional.of(row(bobber));
        }
    }

    @Override
    public void addCatchInterceptor(
            net.zaminmc.torch.fishing.Fishing.CatchInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor");
        FishingSystem.CatchInterceptor wrapped = context -> interceptor.onCatch(
                new net.zaminmc.torch.fishing.Fishing.CatchContext(context.anglerId(),
                        context.bobberAt(), apiCategory(context.category()),
                        context.luckLevel(), context.lureLevel(), context.caught()));
        if (interceptorWrappers.putIfAbsent(interceptor, wrapped) == null) {
            // The first registration of this instance (a second add is
            // the no-op — the removal stays symmetric).
            system.addCatchInterceptor(wrapped);
        }
    }

    @Override
    public boolean removeCatchInterceptor(
            net.zaminmc.torch.fishing.Fishing.CatchInterceptor interceptor) {
        FishingSystem.CatchInterceptor wrapped = interceptorWrappers.remove(interceptor);
        return wrapped != null && system.removeCatchInterceptor(wrapped);
    }

    @Override
    public void setLootSource(net.zaminmc.torch.fishing.Fishing.LootSource source) {
        if (source == null) {
            system.setLootSource(null); // the restore
            return;
        }
        system.setLootSource((category, luck, lure, random) ->
                source.roll(apiCategory(category), luck, lure, random));
    }

    /** One bobber's snapshot row (the reference's own state reads). */
    private static net.zaminmc.torch.fishing.Fishing.Bobber row(
            FishingSystem.Bobber bobber) {
        return new net.zaminmc.torch.fishing.Fishing.Bobber(bobber.entityId(),
                bobber.throwerId(), bobber.position(), bobber.velocityX(),
                bobber.velocityY(), bobber.velocityZ(), bobber.grounded(),
                bobber.biting(), bobber.hooked() != null);
    }

    /** The engine band to the api band (the same three constants). */
    private static net.zaminmc.torch.fishing.Fishing.LootCategory apiCategory(
            FishingSystem.LootCategory category) {
        return switch (category) {
            case JUNK -> net.zaminmc.torch.fishing.Fishing.LootCategory.JUNK;
            case TREASURE -> net.zaminmc.torch.fishing.Fishing.LootCategory.TREASURE;
            case FISH -> net.zaminmc.torch.fishing.Fishing.LootCategory.FISH;
        };
    }
}
