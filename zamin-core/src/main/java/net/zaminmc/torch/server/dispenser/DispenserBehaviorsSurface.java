package net.zaminmc.torch.server.dispenser;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.dispenser.DispenseBehaviors;
import net.zaminmc.torch.dispenser.DispenseBehavior;
import net.zaminmc.torch.dispenser.DispenseContext;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.World;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The public behavior registry (§27.3's dispenser family) over the engine's
 * per-item behavior table: the plugin-facing stand-in for the reference's
 * {@code Bootstrap.registerDispenseBehaviors}.
 *
 * <p><b>The ownership protocol (§27.6):</b> {@code register} and
 * {@code reset} admit onto the world's owner lane (the {@code execute}
 * admission), so a registration from any thread lands between ticks,
 * never inside a dispense walk. The map itself is a
 * {@linkplain ConcurrentHashMap concurrent} registry because the OWNER is
 * the registration lane's authority — the concurrent map is not a
 * substitute for it (rule 11) but the honest carrier for a table that
 * one writer lane fills while the confirmation read
 * ({@code behaviorOf}) stays lock-free from any thread. The custom
 * behaviors themselves run inside the dispense walk, on the owner
 * lane.</p>
 */
final class DispenserBehaviorsSurface implements DispenseBehaviors {

    private final EngineWorld world;

    /** The registered customs, by item identifier value. */
    private final Map<String, DispenseBehavior> customs = new ConcurrentHashMap<>();

    DispenserBehaviorsSurface(EngineWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public void register(ItemType item, DispenseBehavior behavior) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(behavior, "behavior");
        String key = item.identifier().value();
        world.execute(() -> customs.put(key, behavior));
    }

    @Override
    public void reset(ItemType item) {
        Objects.requireNonNull(item, "item");
        String key = item.identifier().value();
        world.execute(() -> customs.remove(key));
    }

    @Override
    public Optional<DispenseBehavior> behaviorOf(ItemType item) {
        Objects.requireNonNull(item, "item");
        return Optional.ofNullable(customs.get(item.identifier().value()));
    }

    /**
     * The engine's consult: the custom behavior the item answers with, or
     * null when the built-in switch (or the default drop) owns the item.
     * Called from the dispense walk — the owner lane.
     */
    DispenseBehavior customFor(String itemIdentifierValue) {
        return customs.get(itemIdentifierValue);
    }

    /** The live dispense context handed to a custom behavior. */
    static final class EngineDispenseContext implements DispenseContext {
        private final World world;
        private final BlockPosition origin;
        private final Direction facing;
        private final ItemStack stack;

        EngineDispenseContext(World world, BlockPosition origin, Direction facing, ItemStack stack) {
            this.world = world;
            this.origin = origin;
            this.facing = facing;
            this.stack = stack;
        }

        @Override public World world() {
            return world;
        }

        @Override public BlockPosition origin() {
            return origin;
        }

        @Override public Direction facing() {
            return facing;
        }

        @Override public ItemStack stack() {
            return stack;
        }
    }
}
