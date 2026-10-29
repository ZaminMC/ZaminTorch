package net.zaminmc.torch.server;

import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item's and the orb's ground-friction consult (the F-FRICTION
 * family's item/orb slice — the §22 queue's loudest candidate):
 *
 * <p>The reference reads the block's OWN slipperiness from the cell
 * below the feet — ItemEntity.tick lines 93-104 and
 * ExperienceOrbEntity.tick lines 116-124:
 * {@code f = slipperiness(under) * 0.98} on ground, 0.98 in air. The
 * ICE family's 0.98 makes the ground band 0.9604 (the slide), the
 * slime's 0.8 makes it 0.784, the common block's 0.6 makes it 0.588.
 * The engine's flat {@code 0.6} was wrong on EVERY surface — the ice
 * the loudest half: the pre-fix item on ice kept ~0.6% of its speed
 * after ten ticks where the reference keeps 67%, and a dropped item
 * on ice stopped within three-quarters of a block where vanilla
 * slides it several blocks.</p>
 */
class ItemSlipperinessParityTest {

    @TempDir
    Path dataDir;

    @Test
    void theDroppedItemSlidesOnIceAndRestsOnStone() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            // Two lanes: the ice floor at x<0, the stone control at x>16.
            server.ticker().submit(() -> {
                for (int x = -24; x < 0; x++) {
                    for (int z = -8; z <= 8; z++) {
                        world.setBlock(new BlockPosition(x, 4, z),
                                BuiltinBlocks.ICE);
                    }
                }
                for (int x = 16; x < 48; x++) {
                    for (int z = -8; z <= 8; z++) {
                        world.setBlock(new BlockPosition(x, 4, z),
                                BuiltinBlocks.STONE);
                    }
                }
            });
            await(() -> world.getBlock(new BlockPosition(-1, 4, 0))
                            .equals(BuiltinBlocks.ICE)
                    && world.getBlock(new BlockPosition(16, 4, 0))
                            .equals(BuiltinBlocks.STONE),
                    "the ice and stone lanes seeded");

            // THE ICE SLIDE: the item dropped on ice keeps 96.04% of
            // its horizontal speed per tick — after two seconds it is
            // still blocks away from its drop point (the reference's
            // own asymptote is ~7.7 blocks from the 0.3 toss, thrown
            // down the lane's long end so the slide stays on the
            // ice). The pre-fix flat 0.6 killed the toss within ~0.75
            // blocks total — this very assertion failed over the
            // pre-fix tree (the stash record).
            AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> iceItem =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                var item = server.itemEntities().spawnThrown(
                        new Position(-4.5, 6.0, 0.5),
                        ItemStack.of(BuiltinItems.EMERALD, 1));
                if (item != null) {
                    item.setVelocity(-0.3, 0.0, 0.0);
                    iceItem.set(item);
                }
            });
            await(() -> iceItem.get() != null, "the ice item dropped");
            Position iceStart = iceItem.get().position();
            Thread.sleep(2000);
            double iceTravel = Math.abs(iceItem.get().position().x()
                    - iceStart.x());
            assertTrue(iceTravel > 4.0,
                    "the ice slide carried the item beyond 4 blocks"
                            + " (travelled " + iceTravel + ") — the pre-fix"
                            + " flat-0.6 tree caps the same toss at ~2.1"
                            + " (the airborne band included)");

            // THE STONE CONTROL: the same toss on stone comes to rest
            // around two blocks — five airborne ticks at the 0.98 air
            // band (~1.44) then the 0.588 ground band (~0.7, the
            // reference's own total ~2.1; the pre-fix flat 0.6 sits
            // inside the same window — the control bounds the sanity,
            // the ICE lane is the distinguishing observable).
            AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> stoneItem =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                var item = server.itemEntities().spawnThrown(
                        new Position(20.5, 6.0, 0.5),
                        ItemStack.of(BuiltinItems.EMERALD, 1));
                if (item != null) {
                    item.setVelocity(-0.3, 0.0, 0.0);
                    stoneItem.set(item);
                }
            });
            await(() -> stoneItem.get() != null, "the stone item dropped");
            Position stoneStart = stoneItem.get().position();
            Thread.sleep(2000);
            double stoneTravel = Math.abs(stoneItem.get().position().x()
                    - stoneStart.x());
            assertTrue(stoneTravel < 2.4,
                    "the stone control rested inside the window"
                            + " (travelled " + stoneTravel + ")");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theOrbSlidesOnIce() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> {
                for (int x = -24; x < 0; x++) {
                    for (int z = -8; z <= 8; z++) {
                        world.setBlock(new BlockPosition(x, 4, z),
                                BuiltinBlocks.ICE);
                    }
                }
            });
            await(() -> world.getBlock(new BlockPosition(-1, 4, 0))
                            .equals(BuiltinBlocks.ICE),
                    "the ice lane seeded");

            // The orb rides the same consult (ExperienceOrbEntity.tick
            // lines 116-130): the 0.9604 ground band keeps the drift
            // sliding for seconds where the pre-fix flat 0.6 stopped it
            // within ~0.5 blocks. Thrown down the lane's long end.
            AtomicReference<net.zaminmc.torch.server.experience.ExperienceOrbEntity> orbRef =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                var orb = server.experienceOrbs().spawnOrb(
                        new Position(-4.5, 6.0, 0.5), 3);
                if (orb != null) {
                    orb.setVelocity(-0.25, 0.0, 0.0);
                    orbRef.set(orb);
                }
            });
            await(() -> orbRef.get() != null, "the orb dropped");
            assertNotNull(orbRef.get(), "the orb spawned");
            Position orbStart = orbRef.get().position();
            Thread.sleep(2000);
            double travel = Math.abs(orbRef.get().position().x()
                    - orbStart.x());
            assertTrue(travel > 3.5,
                    "the ice slide carried the orb beyond 3.5 blocks"
                            + " (travelled " + travel + ") — the pre-fix"
                            + " flat-0.6 tree caps the same toss at ~1.8");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "slippery",
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timeout: " + description);
            }
            Thread.sleep(25);
        }
    }
}
