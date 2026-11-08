package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.armorstand.ArmorStand;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-5 — the dragon's contact hulls' box law (the Phase E
 * boundary arms over the dragon family): the reference flings through
 * {@code world.getEntities(this, rightWing.getShape().grown(4.0, 2.0,
 * 4.0).moved(0.0, -2.0, 0.0))} (EnderDragonEntity lines 269-272, the
 * fling arm's own {@code instanceof LivingEntity} filter at lines
 * 355-366) — each gathered body's OWN box against the part's grown
 * hull, STRICT both sides (exactly touching never intersecting).
 *
 * <p>The pre-fix engine tested the body's POSITION POINT inside the
 * hull with inclusive edges — a body EDGE-STANDING beside the wing
 * (its box grazing the grown hull while its center sits outside) was
 * never flung — and the ARMOR STAND (a LivingEntity in the reference's
 * own fling filter) was absent from the walk whole: a stand at the
 * wing never moved. Both RED pre-fix.</p>
 */
class DragonContactBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dragon" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE DISCRIMINATOR: the player's edge-stand at the wing
    // hull's east face is flung
    // ------------------------------------------------------------------

    /**
     * The right wing's grown hull — the wing's own 4x3 box grown 4/2/4
     * and moved -2, the east face at wx + 6 — flings every living body
     * whose OWN box overlaps it. A player standing with their center a
     * hair (0.1) PAST the east face — the 0.6x1.8 box still 0.2 inside
     * the hull — is flung in vanilla (the radial push away from the
     * body center — the EASTWARD +x impulse). The engine's own player
     * knockback is CLIENT-DRIVEN by design (publishKnockback's wire
     * event, the victim's client simulating the impulse — the seat's
     * stub link never moves), so the OBSERVABLE is the knockback wire
     * event itself: the eastward vx the fling's own signature. The
     * pre-fix point consult read the CENTER against the hull: a center
     * outside never flung, whatever the box held. RED pre-fix. The
     * wing drifts a few hundredths per tick with the dragon's flight —
     * the seat re-reads the wing and re-places the body each round
     * (the 0.2 overlap margin riding the 12-wide hull), the fling walk
     * running every tick.
     *
     * <p>THE SEAT'S OWN FORM (run #678's first-occurrence read — the engine
     * 1:1, the bench's vanilla-validity the defect, the wall-clip seat's
     * own pattern): (1) the player joins CREATIVE — the reference flings
     * every LivingEntity with no gamemode gate (creative players ARE
     * flung by the wings, flingMobs' own filter), while the head hull's
     * 10.0 wound never lands on a creative body (the invulnerable
     * family) — a survival body standing in a flying dragon's path dies
     * to the HEAD arm mid-seat, and a dead body's every teleport is
     * silently dropped (teleportOnTick's own dead gate) with the fling
     * walk skipping it whole: all 30 rounds run against a body frozen at
     * its death spot, exactly run #678's shape; (2) each round's window
     * counts COMPLETED ENGINE TICKS (the ML-002 law — a wall-clock 700ms
     * observes load-dependent FEWER fling walks than the 14 the window
     * means), polled in 2-tick steps so the round exits the moment the
     * fling lands.</p>
     */
    @Test
    void theEdgeStandPlayerAtTheWingHullIsFlung() throws Exception {
        spawnDragonInTheEnd();
        PlayerSession body = joinedInTheEnd("EdgeStand");
        // Creative: flung by the wings (the reference's own LivingEntity
        // filter — no gamemode gate), never wounded by the head (the
        // invulnerable family) — the death that froze run #678's body off
        // the table whole.
        server.ticker().submit(() -> body.setGamemode(GameMode.CREATIVE));
        EngineAwaits.awaitTicks(server.worldFor(1), 1, "the creative mode landed");
        AtomicBoolean flung = new AtomicBoolean();
        server.addSurvivalListener(new EngineServer.SurvivalListener() {
            @Override
            public void onBodyChanged(PlayerSession player) { }

            @Override
            public void onDied(PlayerSession player) { }

            @Override
            public void onRespawned(PlayerSession player, Position spawn) { }

            @Override
            public void onPlayerHurt(PlayerSession player) { }

            @Override
            public void onKnockback(PlayerSession player, double vx, double vy, double vz) {
                if (player == body && vx > 0.1) {
                    flung.set(true); // the eastward impulse — the radial push
                }
            }
        });
        for (int round = 0; round < 30 && !flung.get(); round++) {
            double[] wing = readRightWing();
            if (wing == null) {
                EngineAwaits.awaitTicks(server.worldFor(1), 5, "the dragon's parts");
                continue;
            }
            double face = wing[0] + 6.0; // the wing's own half (2) + the grow (4)
            double placedX = face + 0.1;
            server.teleportPlayer(body, new Position(placedX, wing[1] - 2.0,
                    wing[2]));
            // The round's window: 14 COMPLETED ticks (the census's ML-002
            // law — under load a wall-clock 700ms observes too few of the
            // every-tick fling walks for the 0.2 overlap to hold against
            // the wing's drift), in 2-tick steps for the early exit.
            for (int t = 0; t < 7 && !flung.get(); t++) {
                EngineAwaits.awaitTicks(server.worldFor(1), 2,
                        "the wing's fling walk");
            }
        }
        assertTrue(flung.get(),
                "the edge-standing player at the wing hull's east face is "
                        + "flung (the own box 0.2 inside the grown hull, the "
                        + "center 0.1 outside — the pre-fix point consult "
                        + "never flung it)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE COVERAGE: the stand at the same wing hull is flung
    // (the fling arm's LivingEntity census)
    // ------------------------------------------------------------------

    /**
     * The armor stand — a LivingEntity in the reference's own fling
     * filter (flingMobs lines 360-365) — at the same edge-stand spot:
     * its own 0.5x1.975 hull 0.15 inside the wing's grown hull, the
     * center past the east face — is flung whole (the eastward shift
     * over its own gravity walk). Pre-fix the stand family was absent
     * from the walk: never moved at all. RED pre-fix. Each round
     * summons a fresh stand at the CURRENT wing edge (the stand's own
     * placement surface carries no teleport arm — the fresh summon the
     * seat's own form).
     */
    @Test
    void theStandAtTheWingHullIsFlung() throws Exception {
        spawnDragonInTheEnd();
        AtomicBoolean flung = new AtomicBoolean();
        for (int round = 0; round < 30 && !flung.get(); round++) {
            double[] wing = readRightWing();
            if (wing == null) {
                EngineAwaits.awaitTicks(server.worldFor(1), 5, "the dragon's parts");
                continue;
            }
            double face = wing[0] + 6.0;
            double placedX = face + 0.1;
            AtomicReference<ArmorStand> stand = new AtomicReference<>();
            server.ticker().submit(() -> stand.set(server.armorStands().summonAt(
                    placedX, wing[1] - 2.0, wing[2], 1, null)));
            await(() -> stand.get() != null, "the stand summoned at the wing edge");
            // The round's window: 14 COMPLETED ticks (the ML-002 law — the
            // wall-clock 700ms the loaded runner starved), in 2-tick steps.
            for (int t = 0; t < 7 && !flung.get(); t++) {
                EngineAwaits.awaitTicks(server.worldFor(1), 2,
                        "the wing's fling walk");
                if (stand.get() != null && stand.get().x() > placedX + 0.15) {
                    flung.set(true);
                }
            }
        }
        assertTrue(flung.get(),
                "the stand at the wing hull is flung (the fling arm's own "
                        + "LivingEntity census — the pre-fix walk never "
                        + "moved it)");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Spawns the dragon into the End and awaits its parts' walk. */
    private void spawnDragonInTheEnd() throws InterruptedException {
        server.ticker().submit(() -> server.mobs().spawnAt(
                MobType.ENDER_DRAGON, new Position(30.0, 70.0, 0.0), 1));
        await(() -> {
            for (MobEntity mob : server.mobs().all()) {
                if (mob.type() == MobType.ENDER_DRAGON && mob.dimension() == 1
                        && !mob.dead() && mob.dragonHead() != null) {
                    return true;
                }
            }
            return false;
        }, "the dragon spawned in the End with its parts");
    }

    /**
     * The right wing's live placement {x, y, z} (the dragon's own
     * segment walk — parts[5]).
     */
    private double[] readRightWing() {
        AtomicReference<double[]> wing = new AtomicReference<>();
        server.ticker().submit(() -> {
            for (MobEntity mob : server.mobs().all()) {
                if (mob.type() == MobType.ENDER_DRAGON && mob.dimension() == 1
                        && !mob.dead() && mob.dragonHead() != null) {
                    var part = mob.dragonParts()[5];
                    wing.set(new double[] {part.x(), part.y(), part.z()});
                    return;
                }
            }
        });
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && wing.get() == null) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return wing.get();
    }

    /** Joins a player and moves them to the End (dimension 1). */
    private PlayerSession joinedInTheEnd(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        session.setDimension(1);
        // The End's own ground: a small platform so the body settles
        // before the hull placement (the free-fall window's own start).
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    server.worldFor(1).setBlock(
                            new BlockPosition(dx, 60, dz), BuiltinBlocks.STONE);
                }
            }
        });
        session.applyMovement(new Position(0.5, 61, 0.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.worldFor(1), 3, "the body settles in the End");
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
