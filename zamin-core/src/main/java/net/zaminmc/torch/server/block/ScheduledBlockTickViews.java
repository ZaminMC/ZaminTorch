package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ScheduledBlockTicks;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.List;
import java.util.Optional;

/**
 * The public scheduled block-tick view (§27.3's family): the
 * {@link ScheduledTickSystem}'s own queue dressed as the
 * {@link ScheduledBlockTicks} family — the queries copy the
 * tick-confined structures (best-effort from any thread); the mutation
 * arms assert the world's authoritative context (§27.6 — call them
 * through {@code World.execute} off the lane).
 */
public final class ScheduledBlockTickViews implements ScheduledBlockTicks {

    private final ScheduledTickSystem system;
    private final EngineWorld world;

    public ScheduledBlockTickViews(ScheduledTickSystem system, EngineWorld world) {
        this.system = system;
        this.world = world;
    }

    @Override
    public List<PendingTick> pending() {
        return system.pending().stream()
                .map(entry -> new PendingTick(entry.pos(), entry.block(), entry.time(), entry.priority()))
                .toList();
    }

    @Override
    public Optional<PendingTick> pendingAt(BlockPosition position, Identifier blockFamily) {
        ScheduledTickEntry entry = system.pendingAt(position, blockFamily);
        return entry == null ? Optional.empty()
                : Optional.of(new PendingTick(entry.pos(), entry.block(), entry.time(), entry.priority()));
    }

    @Override
    public boolean cancelAt(BlockPosition position, Identifier blockFamily) {
        world.requireMutationContext("cancelScheduledBlockTick");
        return system.cancelAt(position, blockFamily);
    }

    @Override
    public void scheduleAt(BlockPosition position, Identifier blockFamily, int delay, int priority) {
        world.requireMutationContext("scheduleBlockTick");
        system.scheduleTick(position, blockFamily, delay, priority);
    }
}
