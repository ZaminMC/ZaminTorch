package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-3 — the anvil crush's own-box law (the Phase E boundary arms
 * over the falling family): the reference gathers through
 * {@code this.world.getEntities(this, this.getShape())}
 * (FallingBlockEntity.takeFallDamage lines 145-151) — the falling body's
 * OWN 0.98 box, every gathered body's OWN box against it, STRICT both
 * sides (exactly touching is never intersecting).
 *
 * <p>The pre-fix engine consulted the body's CENTER inside the
 * landing-cell band with inclusive edges, and walked the mobs and the
 * players alone: a body standing on the crush box's EDGE — its box
 * overlapping the falling body's box by a hair while its center sits
 * outside the cell — was never crushed, and the ITEM and ORB families'
 * health-5 bodies (ItemEntity.takeDamage 202-218 /
 * ExperienceOrbEntity.takeDamage 141-151 — a plain wound with no source
 * refusal: a falling anvil DESTROYS a dropped item, vanilla's own
 * census) were never consulted at all.</p>
 */
class AnvilCrushBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "crush" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE DISCRIMINATOR: the player's edge-stand is crushed
    // (the body's box over the crush box, the center outside the cell)
    // ------------------------------------------------------------------

    /**
     * The player's 0.6x1.8 box standing with their center at x+1.2 —
     * the box [x+0.9, x+1.5] overlapping the falling body's own box
     * [x+0.01, x+0.99] by 0.09, the center 0.21 OUTSIDE the cell — is
     * crushed by the 4-block fall (i = 3, the wound 6, health 20 -&gt; 14).
     * The pre-fix center consult never saw them: RED pre-fix. THE
     * SEAT'S OWN FLAKE REPAIR: the assertion rides the WOUND alone
     * (the crush walk precedes the callback's progression roll —
     * FallingBlockEntity lines 149-153 — so the wound lands even when
     * the 10%-band roll breaks the anvil; never awaiting the landing
     * block).
     */
    @Test
    void thePlayersEdgeStandIsCrushed() throws Exception {
        int x = 10, z = 10, fy = 40;
        carveCrushColumn(x, fy, z);
        PlayerSession body = joined("EdgeStand");
        server.teleportPlayer(body, new Position(x + 1.2, fy + 1, z + 0.5));
        dropAnvilFrom(x, fy + 5, z);
        // The fall 4 blocks: i = ceil(4 - 1) = 3, wound = min(3 * 2, 40) = 6.
        await(() -> body.health() == 14.0f,
                "the edge-standing player crushed whole (the box over the "
                        + "crush box, the center outside the cell)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the mob's edge-stand (the type's own box)
    // ------------------------------------------------------------------

    /**
     * The mob consult reads the TYPE'S own box ([pos ± width/2] x
     * [pos.y, pos.y + height]): a zombie (0.6x1.8) standing with its
     * center at x+1.2 — the box over the crush box by 0.09 — takes the
     * wound. RED pre-fix.
     */
    @Test
    void theMobsEdgeStandIsCrushed() throws Exception {
        int x = 30, z = 10, fy = 40;
        carveCrushColumn(x, fy, z);
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> mob =
                new AtomicReference<>();
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity spawned = server.mobs().spawnAt(
                    net.zaminmc.torch.server.entity.MobType.ZOMBIE,
                    new Position(x + 1.2, fy + 1, z + 0.5), 0);
            spawned.setNoAi(true); // the seat's own determinism pin — a
            // wandering mind would walk the box back into the band
            mob.set(spawned);
        });
        await(() -> mob.get() != null, "the zombie spawned at the edge");
        float before = mob.get().health();
        dropAnvilFrom(x, fy + 5, z);
        // The wound assertion alone — the crush precedes the roll (the
        // seat's own flake repair, the player seat's note).
        await(() -> mob.get().health() < before,
                "the edge-standing zombie crushed whole (the type's own box "
                        + "over the crush box)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the strictness straddle: a hair past the box's face
    // never crushes; a hair inside does
    // ------------------------------------------------------------------

    /**
     * Box.intersects refuses the exact touch on every face: a player
     * whose box's west edge sits a HAIR (1e-9) past the crush box's
     * east edge (the center at (x+0.99)+0.3+1e-9 over the engine's own
     * (x+1)-0.01 band arithmetic) is never crushed; the same player a
     * hair INSIDE a second column's band is. The boundary is pinned to
     * a nanometer on each side.
     */
    @Test
    void theExactTouchAtTheCrushEdgeNeverCrushes() throws Exception {
        int outsideX = 50, insideX = 60, z = 10, fy = 40;
        carveCrushColumn(outsideX, fy, z);
        carveCrushColumn(insideX, fy, z);
        PlayerSession body = joined("Straddler");
        // The negative arm: a hair past the east edge.
        double bandMax = (outsideX + 1) - 0.01;
        server.teleportPlayer(body, new Position(bandMax + 0.3 + 1e-9, fy + 1, z + 0.5));
        dropAnvilFrom(outsideX, fy + 5, z);
        // The 2s never-window: the fall completes inside it whatever the
        // progression roll does (the wound is the assertion, never the
        // landing block — the seat's own flake repair).
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            assertEquals(20.0f, body.health(),
                    "the box edge a hair past the crush edge is never crushed "
                            + "(the strict box law)");
            Thread.sleep(50);
        }
        // The positive arm: the same body a hair inside the second column.
        double bandMaxInside = (insideX + 1) - 0.01;
        server.teleportPlayer(body, new Position(bandMaxInside + 0.3 - 1e-9, fy + 1, z + 0.5));
        dropAnvilFrom(insideX, fy + 5, z);
        await(() -> body.health() < 20.0f,
                "the same body a hair inside the crush edge is crushed");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE COVERAGE: the item under the anvil is destroyed,
    // the item outside the box survives
    // ------------------------------------------------------------------

    /**
     * The reference's gather walks EVERY entity class — a dropped item
     * under the anvil takes the plain health-5 wound
     * (ItemEntity.takeDamage 202-218: no source refusal at all) and a
     * 5-block fall's wound 8 destroys it; the same item a full block
     * outside the crush box never hears the landing. Pre-fix the item
     * walks were absent whole: the under-anvil item SURVIVED — RED
     * pre-fix.
     */
    @Test
    void theItemUnderTheAnvilIsDestroyedAndTheOutsideSurvives() throws Exception {
        int x = 80, z = 10, fy = 40;
        carveCrushColumn(x, fy, z);
        AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> under =
                new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> beside =
                new AtomicReference<>();
        server.ticker().submit(() -> {
            under.set(server.itemEntities().spawnRestored(
                    new Position(x + 0.5, fy + 1 + 0.125, z + 0.5),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.STONE, 1), 0));
            beside.set(server.itemEntities().spawnRestored(
                    new Position(x + 1.5, fy + 1 + 0.125, z + 0.5),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.STONE, 1), 0));
        });
        await(() -> under.get() != null && beside.get() != null,
                "both items spawned at rest");
        dropAnvilFrom(x, fy + 6, z);
        // The destruction assertion alone — the crush precedes the roll.
        await(() -> under.get().health() <= 0
                        && !server.itemEntities().all().contains(under.get()),
                "the item under the anvil destroyed whole (the 5-block fall's "
                        + "wound 8 over the health 5)");
        // The isolation control: the item a full block east never heard
        // the landing.
        assertTrue(server.itemEntities().all().contains(beside.get()),
                "the item outside the crush box survives");
        assertTrue(beside.get().health() == 5,
                "the outside item's health untouched");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE COVERAGE: the orb under the anvil is destroyed
    // ------------------------------------------------------------------

    /**
     * The orb family the same coverage (ExperienceOrbEntity.takeDamage
     * 141-151: the plain health-5 walk, no gate at all) — a 5-block
     * fall's wound 8 destroys the orb under the anvil, no payout.
     * RED pre-fix (the walk absent whole).
     */
    @Test
    void theOrbUnderTheAnvilIsDestroyed() throws Exception {
        int x = 100, z = 10, fy = 40;
        carveCrushColumn(x, fy, z);
        AtomicReference<net.zaminmc.torch.server.experience.ExperienceOrbEntity> orb =
                new AtomicReference<>();
        server.ticker().submit(() -> orb.set(server.experienceOrbs().spawnRestored(
                new Position(x + 0.5, fy + 1 + 0.125, z + 0.5), 3, 0, 0)));
        await(() -> orb.get() != null, "the orb spawned at rest");
        dropAnvilFrom(x, fy + 6, z);
        // The destruction assertion alone — the crush precedes the roll.
        await(() -> orb.get().health() <= 0
                        && !server.experienceOrbs().all().contains(orb.get()),
                "the orb under the anvil destroyed whole (the wound 8 over "
                        + "the health 5)");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * The stone floor at fy with clean air above, the whole column —
     * and a ROOF at fy+7: the undead burn consult reads sky access
     * (DaylightBurnTest's own covered-cell law), and a burning zombie's
     * health walk would confound the crush seats' wound assertions —
     * the roof keeps the column's bodies unlit (the anvil's spawn at
     * fy+5/fy+6 stays below it).
     */
    private void carveCrushColumn(int x, int fy, int z) throws InterruptedException {
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 3; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlock(new BlockPosition(x + dx, fy, z + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 6; y++) {
                        world.setBlock(new BlockPosition(x + dx, y, z + dz),
                                world.airType());
                    }
                    world.setBlock(new BlockPosition(x + dx, fy + 7, z + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(world,
                () -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(x, fy, z))),
                40, "the crush column carved");
    }

    /** Places the anvil block and lets the REAL gravity chain fall it. */
    private void dropAnvilFrom(int x, int y, int z) {
        EngineWorld world = server.world();
        BlockPosition anvilAt = new BlockPosition(x, y, z);
        server.ticker().submit(() -> {
            world.setBlock(anvilAt, BuiltinBlocks.ANVIL);
            net.zaminmc.torch.server.block.GravityFalls.tryFall(world, anvilAt,
                    server.fallingEntities());
        });
    }

    private PlayerSession joined(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
