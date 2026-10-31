package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-005 DISCOVERY SEAT (docs/vanilla-conformance-audit.md,
 * the twenty-fourth pass): the portal stand clock's riding gate.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the gate rides BOTH arms.
 * {@code PortalBlock.onEntityCollision} (block/PortalBlock.java lines
 * 158-162) calls {@code entity.onPortalCollision(pos)} only when
 * {@code entity.vehicle == null && entity.rider == null} — a riding (or
 * ridden) body never even arms the portal collision. And the crossing arm
 * itself re-gates: {@code Entity.baseTick} (entity/Entity.java line 286)
 * runs {@code this.vehicle == null && this.portalTime++ >= i}. A player
 * sitting on a pig (or a horse, a boat, a minecart) inside a nether
 * portal therefore stands there indefinitely in vanilla — no stand clock
 * accumulation, no crossing, until they dismount. The vanilla-reachable
 * demonstration: saddle a pig, ride it into a portal frame's interior —
 * the portal does not fire while mounted.</p>
 *
 * <p>THE ENGINE'S SHAPE ({@code EngineServer.tickPortalStand} lines
 * 6787-6835): the nether arm computes {@code inPortal} from the feet cell
 * alone — no riding consult anywhere between the cell read and
 * {@code advancePortalClock}. (The END arm directly above it DOES consult
 * {@code session.ridingVehicleId() < 0 && session.ridingMobId() < 0}, so
 * the engine has the state and the idiom; the nether arm simply omits
 * the gate. {@code changeDimensionOnTick} re-gates nothing either, and
 * never dismounts the rider.)</p>
 *
 * <p>THE SEAT: the pig stands INSIDE the portal frame's interior (its
 * feet on the bottom obsidian row, the reference-reachable ridden
 * scenario); the rider's seat anchor (mob seat height 0.55 + the
 * player's -0.35 ride height, {@code tickMobRiders}'s own law) lands the
 * rider's feet INSIDE the portal cells, so the engine's feet-cell read
 * names a portal cell and the ONLY thing that can hold the crossing is
 * the riding gate. The dimension is sampled every 2 ticks for a 120-tick
 * window (past the survival stand threshold 80, PlayerEntity line 300).
 * The reference law: the dimension NEVER leaves 0 while the body stays
 * mounted. This seat is EXPECTED RED on develop until VT-005's
 * correction lands. Do not re-pin it to the engine's current answer; the
 * finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalRidingGateDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ridegate" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var accepted = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /** The frame alone (the interior unlit): VT-006's entity-level crossing
     * arms an UNMOUNTED mob standing in portal cells on the FIRST armed tick
     * (the base entity's threshold 0), so a pig spawned inside a lit frame is
     * in the nether before any rider could mount — the seats predate the
     * crossing. The frame mounts the pair in the SAME tick (no wander window),
     * and the interior lights only after: both bodies are then riding-gated
     * and the crossing stays held — the seats' own law. */
    private static void buildFrameOnly(EngineWorld world, int x, int y, int z) {
        for (int w = 0; w < 4; w++) {
            for (int h = -1; h < 4; h++) {
                boolean frameCell = w == 0 || w == 3 || h == -1 || h == 3;
                if (frameCell) {
                    world.setBlock(new BlockPosition(x + w, y + h, z), BuiltinBlocks.OBSIDIAN);
                }
            }
        }
    }

    /** Lights the 2x3 interior column (the frame's own cells stay). */
    private static void lightInterior(EngineWorld world, int x, int y, int z) {
        for (int w = 1; w <= 2; w++) {
            for (int h = 0; h < 3; h++) {
                world.setBlock(new BlockPosition(x + w, y + h, z), BuiltinBlocks.NETHER_PORTAL);
            }
        }
    }

    /**
     * VT-005: a mounted body standing in the portal cells never crosses —
     * the reference's riding gate (PortalBlock lines 158-162 +
     * Entity.baseTick line 286) holds the crossing until the body
     * dismounts, whatever the stand window.
     */
    @Test
    void theMountedBodyStandingInThePortalCellsNeverCrosses() throws Exception {
        EngineWorld overworld = server.world();
        PlayerSession rider = join("PigRider");

        // The overworld portal (the crossing's source): a 4x5 X-plane
        // frame at (40, 20, 40) — the DimensionWalk shape, the interior
        // UNLIT until the mount holds (the VT-006 note on buildFrameOnly).
        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildFrameOnly(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX, overY - 1, overZ))
                .equals(BuiltinBlocks.OBSIDIAN), "the overworld frame built");

        // The pig stands INSIDE the frame's interior: its feet on the
        // bottom obsidian row (cell overY-1's top surface), the body
        // occupying the interior column above. The rider's seat anchor
        // lands at pig.y + 0.55 - 0.35 = pig.y + 0.2 — the rider's feet
        // cell IS the interior bottom row, a portal cell. The spawn and
        // the mount land in the SAME tick (no wander window before the
        // riding gate holds).
        Position pigAt = new Position(overX + 1.5, overY, overZ + 0.5);
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity pig = server.mobs().spawnAt(MobType.PIG, pigAt);
            server.mobs().mountMob(pig, rider.engineEntityId(),
                    rider.name(), rider.uuid().toString());
        });
        await(() -> rider.ridingMobId() >= 0, "the rider took the pig's seat");

        // The interior lights with the mount held: both bodies are
        // riding-gated now, so the crossing stays held for the window.
        server.ticker().submit(() -> lightInterior(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the interior lit");

        // The seat's precondition: the rider's feet cell is a portal cell
        // (tickMobRiders landed the seat anchor inside the interior) —
        // the engine's own feet-cell read names the portal, so the riding
        // gate is the ONLY thing that can hold the crossing.
        await(() -> {
            BlockPosition feet = new BlockPosition(
                    (int) Math.floor(rider.position().x()),
                    (int) Math.floor(rider.position().y()),
                    (int) Math.floor(rider.position().z()));
            return BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(feet))
                    || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(feet));
        }, "the rider's feet cell is a portal cell (the seat landed)");
        BlockPosition riderFeet = new BlockPosition(
                (int) Math.floor(rider.position().x()),
                (int) Math.floor(rider.position().y()),
                (int) Math.floor(rider.position().z()));
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(riderFeet))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(riderFeet)),
                "the seat's precondition: the mounted rider's feet stand inside a portal cell ("
                        + riderFeet + ", got " + overworld.getBlock(riderFeet).identifier()
                        + ", position " + rider.position() + ")");
        assertEquals(0, rider.dimension(), "the body is in the overworld at the mount");

        // THE STAND: 120 ticks sampled at 2-tick granularity — past the
        // survival stand threshold (the reference's PlayerEntity line 300:
        // 80 for the non-invulnerable body) with margin. THE REFERENCE
        // LAW: the riding gate holds — the collision never arms and the
        // crossing never fires while the body stays mounted, so the
        // dimension holds 0 for the whole window. THE ENGINE'S SHAPE: the
        // nether arm consults no riding state, so the stand clock
        // accumulates and the crossing fires at the threshold.
        StringBuilder timeline = new StringBuilder();
        for (int sample = 0; sample < 60; sample++) {
            EngineAwaits.awaitTicks(server.world(), 2, "the stand window advances");
            timeline.append("t").append((sample + 1) * 2)
                    .append(":dim=").append(rider.dimension())
                    .append(",clock=").append(rider.portalTime())
                    .append(",cool=").append(rider.portalCooldown())
                    .append(",riding=").append(rider.ridingMobId())
                    .append("; ");
            assertEquals(0, rider.dimension(),
                    "VT-005: the mounted body never crosses (the reference's "
                            + "vehicle == null && rider == null gate) — timeline: " + timeline);
        }
        // The reference's own end state: the mounted body never armed the
        // collision, so the clock never accumulated (the 4-per-tick decay
        // held it at 0 through the whole window).
        assertEquals(0, rider.portalTime(),
                "VT-005: the reference's end state — the stand clock never "
                        + "accumulated while mounted (clock " + rider.portalTime() + ")");
    }

    /**
     * VT-005's dismount control (the independent pass's isolation
     * experiment): the same seat, but the rider steps off the pig
     * mid-window. THE REFERENCE LAW: the mounted window accumulated
     * NOTHING (the gated collision never armed, the 4-per-tick decay held
     * the clock at 0), so the dismounted body — the dismount exit lands
     * at mount.x + 1.1, the frame's OTHER interior column, still a
     * portal cell — stands its FRESH 80 and crosses no earlier than 80
     * ticks after the dismount. THE ENGINE'S SHAPE: the mounted window
     * accumulated the whole stand (the ungated clock), so the crossing
     * rides the carried clock and fires at dismount + the REMAINDER of
     * the threshold. The control isolates the gate as the only variable:
     * the seat's geometry is crossing-capable for this body either way.
     */
    @Test
    void theDismountedBodyCrossesOnlyAfterItsFreshStand() throws Exception {
        EngineWorld overworld = server.world();
        PlayerSession rider = join("PigRiderControl");

        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildFrameOnly(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX, overY - 1, overZ))
                .equals(BuiltinBlocks.OBSIDIAN), "the overworld frame built");

        Position pigAt = new Position(overX + 1.5, overY, overZ + 0.5);
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity pig = server.mobs().spawnAt(MobType.PIG, pigAt);
            server.mobs().mountMob(pig, rider.engineEntityId(),
                    rider.name(), rider.uuid().toString());
        });
        await(() -> rider.ridingMobId() >= 0, "the rider took the pig's seat");

        // The interior lights with the mount held (the VT-006 note on
        // buildFrameOnly: the crossing stays gated while mounted).
        server.ticker().submit(() -> lightInterior(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the interior lit");

        // The mounted half-window: 40 ticks (half the threshold — enough
        // for the engine's ungated clock to carry 40, nowhere near enough
        // for a crossing on any law).
        EngineAwaits.awaitTicks(server.world(), 40, "the mounted half-window");
        assertTrue(rider.ridingMobId() >= 0, "the body is still mounted at the half-window");
        assertEquals(0, rider.dimension(), "no crossing during the mounted half-window");

        // The dismount: the exit lands at mount.x + 1.1 — the frame's
        // other interior column (cell overX + 2), still a portal cell.
        // The ridden body itself is addressed by the session's own id
        // (the population's list order is not the seat's to lean on).
        net.zaminmc.torch.server.entity.MobEntity mount =
                server.mobs().byId(rider.ridingMobId());
        server.ticker().submit(() -> server.mobs().dismountMob(mount, false));
        await(() -> rider.ridingMobId() < 0, "the rider stepped off the pig");
        BlockPosition exitFeet = new BlockPosition(
                (int) Math.floor(rider.position().x()),
                (int) Math.floor(rider.position().y()),
                (int) Math.floor(rider.position().z()));
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(exitFeet))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(exitFeet)),
                "the control's precondition: the dismounted body still stands in a "
                        + "portal cell (" + exitFeet + ", got "
                        + overworld.getBlock(exitFeet).identifier()
                        + ", position " + rider.position() + ")");

        // THE FRESH STAND: the crossing must not fire before 80 ticks
        // past the dismount (the reference's fresh clock). The engine's
        // carried clock fires at the remainder instead.
        StringBuilder timeline = new StringBuilder();
        for (int sample = 0; sample < 39; sample++) {
            EngineAwaits.awaitTicks(server.world(), 2, "the fresh stand window advances");
            timeline.append("t").append((sample + 1) * 2)
                    .append(":dim=").append(rider.dimension())
                    .append(",clock=").append(rider.portalTime())
                    .append(",cool=").append(rider.portalCooldown())
                    .append("; ");
            assertEquals(0, rider.dimension(),
                    "VT-005 (the control): no crossing before the fresh 80-tick "
                            + "stand past the dismount — timeline: " + timeline);
        }
        // The crossing itself fires within the following window (the
        // reference's own threshold law for the standing body).
        await(() -> rider.dimension() == -1, "the dismounted body crosses on its fresh stand");
    }
}
