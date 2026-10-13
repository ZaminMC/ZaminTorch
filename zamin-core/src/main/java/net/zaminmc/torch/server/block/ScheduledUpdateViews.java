package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ScheduledUpdates;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.List;
import java.util.Optional;

/**
 * The public scheduled-update views (§27.3's scheduled-block-tick
 * family): the block-update system's own queue and fuse list dressed as
 * the {@link ScheduledUpdates} family — the queries copy the
 * tick-confined structures (best-effort from any thread); the mutation
 * arms assert the world's authoritative context (§27.6 — call them
 * through {@code World.execute} off the lane).
 */
public final class ScheduledUpdateViews implements ScheduledUpdates {

    private final BlockUpdateSystem system;
    private final EngineWorld world;

    public ScheduledUpdateViews(BlockUpdateSystem system, EngineWorld world) {
        this.system = system;
        this.world = world;
    }

    @Override
    public List<PendingUpdate> pending() {
        return system.pendingUpdates().stream()
                .map(view -> new PendingUpdate(view.position(), view.dueTick()))
                .toList();
    }

    @Override
    public List<PendingUpdate> pendingFallChecks() {
        return system.pendingFallChecksView().stream()
                .map(view -> new PendingUpdate(view.position(), view.dueTick()))
                .toList();
    }

    @Override
    public Optional<PendingUpdate> pendingAt(BlockPosition position) {
        BlockUpdateSystem.ScheduledTickView view = system.pendingUpdateAt(position);
        return view == null ? Optional.empty()
                : Optional.of(new PendingUpdate(view.position(), view.dueTick()));
    }

    @Override
    public boolean cancelAt(BlockPosition position) {
        world.requireMutationContext("cancelScheduledUpdate");
        return system.cancelUpdate(position);
    }

    @Override
    public boolean cancelFallCheckAt(BlockPosition position) {
        world.requireMutationContext("cancelFallCheck");
        return system.cancelFallCheck(position);
    }

    @Override
    public void requestAt(BlockPosition position) {
        world.requireMutationContext("requestScheduledUpdate");
        system.requestUpdate(position);
    }
}
