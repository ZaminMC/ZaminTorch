package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.armorstand.ArmorStand;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.weather.LightningSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-4 — the lightning family's Phase E boundary arms: the bolt's
 * two consults each carry the reference's ONE box law (Box.intersects
 * 164-171: each body's OWN box against the query box, STRICT on all six
 * faces, EXACTLY TOUCHING never intersecting).
 *
 * <p>THE STRIKE WALK (LightningBoltEntity.tick lines 84-90:
 * {@code world.getEntities(this, Box(x-3, y-3, z-3, x+3, y+6+3, z+3))}
 * — NO class filter, NO sky gate): the pre-fix engine shared the
 * TARGET-SELECTION walk's consult (ServerWorld.findLightningTarget
 * 370-379's {@code getEntitiesOfType(LivingEntity.class, box, isAlive
 * && hasSkyAccess)}) — its sky gate withheld strikes from every covered
 * body, its center test missed the edge-stand, and its living census
 * never walked the stand, the item or the orb (a bolt NEVER destroyed a
 * dropped item, vanilla's own no-refusal census — ItemEntity.takeDamage
 * 202-218 / ExperienceOrbEntity.takeDamage 141-151; the stand's wound
 * REFUSED but its fire stamp rides the base body whole —
 * ArmorStandEntity.takeDamage 395-400 + Entity.struckByLightning
 * 1546-1552).</p>
 *
 * <p>THE TARGET WALK's own census is proven over the REAL strike roll
 * (the 1-in-100000 gate itself, driven in the batch form): a stand
 * cluster on its platform — LivingEntity bodies in the reference's own
 * census — produces the CHOSEN-TARGET route, the bolt landing at a
 * STAND'S OWN cell.</p>
 */
class LightningBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bolt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE DISCRIMINATOR: the covered body takes the bolt
    // (the strike walk consults NO sky gate)
    // ------------------------------------------------------------------

    /**
     * A player under a stone roof — the sky gate's own stand-in reads
     * the column's heightmap, the roof sits the surface above their
     * head — takes the bolt's wound when it strikes three cells east
     * (outside the roof, the body's box well inside the strike box
     * [boltX-3, boltX+3]). The reference's strike walk carries NO sky
     * gate: the cave body under the overhang is struck exactly like
     * the open-sky one. Pre-fix the strike walk shared the
     * target-selection consult — its sky gate withheld the strike
     * whole: RED pre-fix.
     */
    @Test
    void theCoveredBodyTakesTheBolt() throws Exception {
        int x = 10, z = 10, fy = 40;
        carveOpenRig(x, fy, z);
        // The roof over the body's cell alone (the pocket: the body's
        // 1.8 box under the fy+3 slab, the bolt outside it).
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(x, fy + 3, z), BuiltinBlocks.STONE));
        EngineAwaits.awaitCondition(server.world(), () -> BuiltinBlocks.STONE.equals(
                        server.world().getBlock(new BlockPosition(x, fy + 3, z))),
                40, "the pocket's roof placed");

        PlayerSession body = joined("Covered");
        server.teleportPlayer(body, new Position(x + 0.5, fy + 1, z + 0.5));
        // The bolt three cells east — its strike box [x, x+6] holding
        // the body's own box [x+0.2, x+0.8] x [fy+1, fy+2.8] whole.
        server.ticker().submit(() -> server.lightningSurface()
                .strike(x + 3, fy + 1, z, false));
        await(() -> body.health() < 20.0f,
                "the covered body takes the bolt (the strike walk consults no sky gate)");
        // The two-tick 5+5 damage window (LightningBoltEntity's own
        // lifeDelay 1 and 0), the fire's own 1/sec behind it — the
        // wound observed inside the window, never past the floor.
        assertTrue(body.health() >= 8.0f && body.health() <= 15.0f,
                "the two-tick 5+5 window with the fire's own 1/sec behind it: "
                        + body.health());
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE STRICT BOX: the exact-touch straddle at the bolt
    // box's own face
    // ------------------------------------------------------------------

    /**
     * Box.intersects refuses the exact touch on every face: a player
     * whose box's WEST edge sits a HAIR (1e-9) past the bolt box's
     * EAST face (the center at (boltX+3)+0.3+1e-9) is never struck
     * over a 2s never-window; the same player a hair INSIDE a second
     * bolt's box is. The boundary is pinned to a nanometer each side.
     * Pre-fix the center consult placed the center test at the band's
     * own edge — the inside arm never struck either: RED pre-fix.
     */
    @Test
    void theExactTouchAtTheBoltBoxEdge() throws Exception {
        int outsideX = 30, insideX = 40, z = 10, fy = 40;
        carveOpenRig(outsideX, fy, z);
        carveOpenRig(insideX, fy, z);
        PlayerSession body = joined("Straddler");
        // The negative arm: a hair past the box's east face
        // (boltX + 3).
        server.teleportPlayer(body,
                new Position(outsideX + 3.3 + 1e-9, fy + 1, z + 0.5));
        server.ticker().submit(() -> server.lightningSurface()
                .strike(outsideX, fy + 1, z, false));
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            assertEquals(20.0f, body.health(),
                    "the box edge a hair past the bolt box's face never "
                            + "strikes (the strict box law)");
            Thread.sleep(50);
        }
        // The positive arm: the same body a hair inside the second
        // bolt's box (the west edge (boltX+3)-1e-9 past the face
        // inward).
        server.teleportPlayer(body,
                new Position(insideX + 3.3 - 1e-9, fy + 1, z + 0.5));
        server.ticker().submit(() -> server.lightningSurface()
                .strike(insideX, fy + 1, z, false));
        await(() -> body.health() < 20.0f,
                "the same body a hair inside the bolt box's face is struck");
        assertTrue(body.health() >= 8.0f && body.health() <= 15.0f,
                "the two-tick 5+5 window with the fire's own 1/sec behind it: "
                        + body.health());
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE STAND'S OWN LAW: the wound refused, the fire stamp
    // whole (the strike walk's LivingEntity census)
    // ------------------------------------------------------------------

    /**
     * The armor stand at the strike cell — a LivingEntity in the
     * reference's own census — takes the bolt's
     * {@code struckByLightning} whole: the wound REFUSED
     * (ArmorStandEntity.takeDamage 395-400: neither arrow nor player)
     * but the fire arm rides the base body (Entity 1548-1551:
     * {@code onFireTimer++ + setOnFireFor(8)} — the 160-tick stamp).
     * The engine's own stand-burn clock then walks the stamp down (the
     * landed burn model: one tick per tick, the 4.0 on-fire toll per
     * burning tick — the stand's own break law, another slice's), so
     * the seat observes the stamp FRESH (the first poll lands well
     * inside the burn's own window; the strike arm itself never
     * touches the stand's health — the wound-refusal is the arm's own
     * shape, the burn toll alone walks it). Pre-fix the stand family
     * was absent from the walk whole: the stamp never landed: RED
     * pre-fix.
     */
    @Test
    void theStandTakesTheFireStampButNoWound() throws Exception {
        int x = 60, z = 10, fy = 40;
        carveOpenRig(x, fy, z);
        AtomicReference<ArmorStand> stand = new AtomicReference<>();
        server.ticker().submit(() -> stand.set(server.armorStands().summonAt(
                x + 0.5, fy + 1, z + 0.5, 0, null)));
        await(() -> stand.get() != null, "the stand summoned at the strike cell");
        server.ticker().submit(() -> server.lightningSurface()
                .strike(x, fy + 1, z, false));
        // The stamp observed fresh — the stand still standing at the
        // observation, or the burn clock already walking it (the
        // pre-fix engine never stamped at all: zero forever).
        await(() -> stand.get().fireTicks() > 0
                        && (server.armorStands().allStands().contains(stand.get())
                                || stand.get().fireTicks() < 160),
                "the stand's struck-by-lightning fire stamp (8 seconds, the "
                        + "base body's own arm) while the stand stands");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE COVERAGE: the item under the bolt is destroyed, the
    // item outside the box survives
    // ------------------------------------------------------------------

    /**
     * The reference's strike walk carries NO class filter and
     * ItemEntity.takeDamage 202-218 carries NO source refusal (the
     * star's gate is explosive alone): the 5.0 wound over the item's
     * health 5 DESTROYS it whole — vanilla lightning destroys a
     * dropped item. The same item ten cells east — outside the box —
     * never hears the bolt. Pre-fix the item family was absent whole:
     * RED pre-fix.
     */
    @Test
    void theItemUnderTheBoltIsDestroyedAndTheOutsideSurvives() throws Exception {
        int x = 80, z = 10, fy = 40;
        carveOpenRig(x, fy, z);
        // The control's own floor pillar (ten cells east, far outside
        // the [boltX-3, boltX+3] box).
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(x + 10, fy, z), BuiltinBlocks.STONE));
        EngineAwaits.awaitCondition(server.world(), () -> BuiltinBlocks.STONE.equals(
                        server.world().getBlock(new BlockPosition(x + 10, fy, z))),
                40, "the control's pillar placed");
        AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> under =
                new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> beside =
                new AtomicReference<>();
        server.ticker().submit(() -> {
            under.set(server.itemEntities().spawnRestored(
                    new Position(x + 0.5, fy + 1 + 0.125, z + 0.5),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.STONE, 1), 0));
            beside.set(server.itemEntities().spawnRestored(
                    new Position(x + 10.5, fy + 1 + 0.125, z + 0.5),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.STONE, 1), 0));
        });
        await(() -> under.get() != null && beside.get() != null,
                "both items spawned at rest");
        server.ticker().submit(() -> server.lightningSurface()
                .strike(x, fy + 1, z, false));
        await(() -> under.get().health() <= 0
                        && !server.itemEntities().all().contains(under.get()),
                "the item under the bolt destroyed whole (the 5.0 wound over "
                        + "the health 5)");
        assertTrue(server.itemEntities().all().contains(beside.get()),
                "the item outside the bolt box survives");
        assertEquals(5, beside.get().health(),
                "the outside item's health untouched");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE COVERAGE: the orb under the bolt is destroyed
    // ------------------------------------------------------------------

    /**
     * The orb family the same coverage (ExperienceOrbEntity.takeDamage
     * 141-151: the plain health-5 walk, no gate at all): the bolt's
     * 5.0 destroys the orb whole, no payout. RED pre-fix (the family
     * absent from the walk).
     */
    @Test
    void theOrbUnderTheBoltIsDestroyed() throws Exception {
        int x = 100, z = 10, fy = 40;
        carveOpenRig(x, fy, z);
        AtomicReference<net.zaminmc.torch.server.experience.ExperienceOrbEntity> orb =
                new AtomicReference<>();
        server.ticker().submit(() -> orb.set(server.experienceOrbs().spawnRestored(
                new Position(x + 0.5, fy + 1 + 0.125, z + 0.5), 3, 0, 0)));
        await(() -> orb.get() != null, "the orb spawned at rest");
        server.ticker().submit(() -> server.lightningSurface()
                .strike(x, fy + 1, z, false));
        await(() -> orb.get().health() <= 0
                        && !server.experienceOrbs().all().contains(orb.get()),
                "the orb under the bolt destroyed whole (the 5.0 wound over "
                        + "the health 5)");
    }

    // ------------------------------------------------------------------
    // Seat 6 — THE TARGET WALK'S CENSUS: the stand becomes the strike
    // target (the real roll, driven in the batch form)
    // ------------------------------------------------------------------

    /**
     * THE TARGET-SELECTION walk (ServerWorld.findLightningTarget
     * 370-379: {@code getEntitiesOfType(LivingEntity.class, box,
     * isAlive && hasSkyAccess)}) counts the armor stand — a
     * LivingEntity in the reference's own census: a stand cluster on
     * its floating platform (cells (8..9, 8..9) of chunk (0, 0), the
     * stands' feet at fy+1, far above the natural surface's y-5
     * bodies) produces the CHOSEN-TARGET route — the bolt landing at a
     * STAND'S OWN cell, the y at the platform's own fy+1 (a
     * natural-surface strike — a mob's chosen cell or a precipitation
     * cell — rides y 5, never fy+1). The seat drives the reference's
     * REAL 1-in-100000 roll ({@code tryStrikeAtChunk}) in the batch
     * form — the storm's own dice, never forced: each batch runs the
     * roll four million times (an expected forty strikes; the cell
     * lands within the cluster's grown box in roughly one strike in
     * five, and the cluster — the only bodies on the platform — is
     * then the pick). The listener's flag demands a LIVE stand at the
     * trip: with the cluster alive a platform-cell strike's box always
     * holds the stands as candidates, so the precipitation route never
     * lands there — the flag is the CHOSEN route alone (the batch
     * blocks the engine's own tick, so the stands' burn-break — the
     * engine's landed stand-burn clock, another slice's law — can
     * never open the gap mid-batch; the next batch re-summons the
     * cluster when the burn retired it). Pre-fix the stand family was
     * absent from the census AND the walk: the stamp never landed at
     * all — the flag's fallback (a precipitation bolt at a platform
     * cell over the never-stamped stands) fails the stamp
     * confirmation: RED pre-fix.
     */
    @Test
    void theStandBecomesTheStrikeTarget() throws Exception {
        int fx = 8, fz = 8, fy = 40;
        // The cluster's own 2x2 platform — one floating stone slab per
        // cell, the stands' feet at fy+1 (the heightmap's surface at
        // each column exactly its floor — the sky gate's own
        // stand-in).
        server.ticker().submit(() -> {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    server.world().setBlock(new BlockPosition(fx + dx, fy, fz + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(), () -> BuiltinBlocks.STONE.equals(
                        server.world().getBlock(new BlockPosition(fx, fy, fz))),
                40, "the cluster's platform placed");

        List<ArmorStand> tracked = new CopyOnWriteArrayList<>();
        AtomicBoolean atAStandCell = new AtomicBoolean();
        server.ticker().submit(() -> {
            summonCluster(tracked, fx, fy, fz);
            server.lightningSurface().addListener(new LightningSystem.Listener() {
                @Override
                public void onBoltStruck(int entityId, double bx,
                                         double by, double bz) {
                    // THE CHOSEN-ROUTE FLAG: a bolt at a stand's own
                    // cell — the platform's x/z band, the y at the
                    // platform's own fy+1 — while a stand still
                    // stands.
                    if (((int) bx == fx || (int) bx == fx + 1)
                            && ((int) bz == fz || (int) bz == fz + 1)
                            && (int) by == fy + 1
                            && anyStandLive(tracked)) {
                        atAStandCell.set(true);
                    }
                }
            });
        });
        await(() -> !tracked.isEmpty(), "the first cluster summoned");

        // The batch loop — the real roll, up to eight batches of four
        // million (the storm's own 1-in-100000 dice; the expected
        // platform-cell landing arrives within the first batch or two
        // — the retries cover the pick's own odds among the natural
        // bodies).
        for (int batch = 0; batch < 8 && !atAStandCell.get(); batch++) {
            server.ticker().submit(() -> {
                if (atAStandCell.get()) {
                    return;
                }
                // The burn's own break may have retired the last
                // cluster between batches — the next generation.
                if (!anyStandLive(tracked)) {
                    summonCluster(tracked, fx, fy, fz);
                }
                for (int i = 0; i < 4_000_000 && !atAStandCell.get(); i++) {
                    server.lightningSurface().tryStrikeAtChunk(0, 0, true, true);
                }
            });
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline
                    && !atAStandCell.get()) {
                Thread.sleep(25);
            }
        }
        assertTrue(atAStandCell.get(),
                "a bolt landed at a stand's own cell (the target walk's "
                        + "LivingEntity census — the stand IS a candidate, "
                        + "the chosen route)");
        // The confirmation the pre-fix engine could never forge: the
        // chosen bolt's own strike walk stamps the cluster (the
        // bolt's damage box always holds the chosen stand).
        await(() -> {
            for (ArmorStand stand : tracked) {
                if (stand.fireTicks() > 0) {
                    return true;
                }
            }
            return false;
        }, "the chosen-target bolt's strike walk stamped the cluster");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Whether any tracked stand still stands in the live map. */
    private boolean anyStandLive(List<ArmorStand> tracked) {
        for (ArmorStand stand : tracked) {
            if (server.armorStands().allStands().contains(stand)) {
                return true;
            }
        }
        return false;
    }

    /** One generation: the 2x2 cluster summoned onto the platform. */
    private void summonCluster(List<ArmorStand> tracked, int fx, int fy, int fz) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                tracked.add(server.armorStands().summonAt(
                        fx + dx + 0.5, fy + 1, fz + dz + 0.5, 0, null));
            }
        }
    }

    /**
     * The stone floor at fy with clean air above through fy+6 — the
     * open-sky rig (no roof: the lightning seats carry no undead burn
     * confound — the bodies are players, stands, items and orbs).
     */
    private void carveOpenRig(int x, int fy, int z) throws InterruptedException {
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 5; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlock(new BlockPosition(x + dx, fy, z + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 6; y++) {
                        world.setBlock(new BlockPosition(x + dx, y, z + dz),
                                world.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(world,
                () -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(x, fy, z))),
                40, "the open rig carved");
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
