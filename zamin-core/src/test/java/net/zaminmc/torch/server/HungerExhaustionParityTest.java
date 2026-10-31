package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hunger exhaustion economy's parity seats (the verifier's tenth pass —
 * reference/1.8.8 HungerManager.tick lines 29-60, PlayerEntity lines
 * 1276-1351 + 856 + 1474-1481, DamageSource lines 32/121-136): the fold's
 * strictly-greater-than-4.0 boundary and its saturation-first payment, the
 * peaceful arm that never drops food, the movement arms (the walk/sprint
 * meter rates, the plain/sprint jump, the surface swim's horizontal band,
 * the dived arm's 3-D norm), the unmounted gate, the teleport that never
 * charges, and the damage-taken charges (the arrow, the blast, and the anvil
 * pay their source's 0.3; the in-wall suffocation pays its bypass family's
 * 0.0).
 */
class HungerExhaustionParityTest {

    @TempDir
    Path dataDir;

    @Test
    void theFoldBoundaryIsStrictlyGreaterThanFour() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Boundary");
            body.setBody(20.0f, 20, 5.0f);
            // Exactly 4.0: the reference's `if (this.exhaustion > 4.0F)`
            // (HungerManager.tick line 32) is strict — the accumulator may
            // sit at the boundary forever without paying.
            server.ticker().submit(() -> body.setExhaustion(4.0f));
            EngineAwaits.awaitTicks(server.world(), 12, "the boundary window");
            assertEquals(4.0f, body.exhaustion(), 1.0E-6f,
                    "exactly 4.0 never folds (the strict inequality)");
            assertEquals(5.0f, body.saturation(), 1.0E-6f, "saturation untouched");
            assertEquals(20, body.food(), "food untouched");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theFoldPaysSaturationFirstAndFloorsBeforeFoodReaches() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Folder");
            body.setBody(20.0f, 20, 5.0f);
            // Fold one: saturation absorbs (5 -> 4), food untouched.
            server.ticker().submit(() -> body.setExhaustion(4.5f));
            await(() -> body.saturation() == 4.0f, "the first fold paid saturation");
            assertEquals(20, body.food(), "the saturation arm spares food");
            // Fold two at 0.5 saturation: the arm still fires (the > 0
            // consult happens BEFORE the subtraction) and floors at zero.
            server.ticker().submit(() -> {
                body.setExhaustion(4.1f);
                body.setBody(20.0f, 20, 0.5f);
            });
            await(() -> body.saturation() == 0.0f, "the half point floors at zero");
            assertEquals(20, body.food(), "the flooring fold still spares food");
            // Fold three with saturation empty: food finally pays.
            server.ticker().submit(() -> body.setExhaustion(4.1f));
            await(() -> body.food() == 19, "the empty-saturation fold paid food");
            assertEquals(0.0f, body.saturation(), 1.0E-6f, "saturation stays floored");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePeacefulFoldArmNeverDropsFood() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Pacifist");
            server.consoleCommand("difficulty peaceful");
            await(() -> server.difficulty() == 0, "the peaceful tier landed");
            body.setBody(20.0f, 20, 0.0f);
            server.ticker().submit(() -> body.setExhaustion(4.1f));
            await(() -> body.exhaustion() < 0.2f, "the fold fired on peaceful");
            assertEquals(20, body.food(),
                    "the reference's `else if (difficulty != PEACEFUL)` arm (line 36) "
                            + "spares the food on peaceful");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theWalkAndSprintMeterRates() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Walker");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // One walked meter: 0.01 (reference line 1340's plain arm).
            server.movementProposal(body, new Position(x, y, z + 1.0), Rotation.ZERO, true);
            await(() -> body.exhaustion() >= 0.01f, "the walk charge landed");
            assertEquals(0.01f, body.exhaustion(), 1.0E-4f,
                    "the plain walk rate 0.01F * cm * 0.01F");
            // One sprinted meter: ~0.1 (reference line 1334's sprint arm).
            // The flag write rides the tick thread — await it before the
            // proposal (the charge consults the flag at the tick, the
            // proposal applies immediately).
            server.ticker().submit(() -> body.setSprinting(true));
            await(() -> body.sprinting(), "the sprint flag landed");
            server.movementProposal(body, new Position(x, y, z + 2.0), Rotation.ZERO, true);
            await(() -> body.exhaustion() >= 0.1099f, "the sprint charge landed");
            assertEquals(0.11f, body.exhaustion(), 1.0E-3f,
                    "the sprint rate 0.099999994F * cm * 0.01F on top of the walk");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theJumpArmPaysPlainAndSprint() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Jumper");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // The plain jump: the ground left under rising motion, 0.2
            // (reference jump() line 1282).
            server.movementProposal(body, new Position(x, y + 0.42, z), Rotation.ZERO, false);
            await(() -> body.exhaustion() >= 0.2f, "the plain jump charge landed");
            assertEquals(0.2f, body.exhaustion(), 1.0E-4f, "the plain jump's 0.2");
            // Land again (the jump arm needs the scratch's prevOnGround).
            server.movementProposal(body, new Position(x, y, z), Rotation.ZERO, true);
            EngineAwaits.awaitTicks(server.world(), 3, "the landing captures");
            // The sprint jump: 0.8 (reference jump() line 1280).
            server.ticker().submit(() -> body.setSprinting(true));
            await(() -> body.sprinting(), "the sprint flag landed");
            server.movementProposal(body, new Position(x, y + 0.42, z), Rotation.ZERO, false);
            await(() -> body.exhaustion() >= 1.0f, "the sprint jump charge landed");
            assertEquals(1.0f, body.exhaustion(), 1.0E-3f, "0.2 + the sprint jump's 0.8");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theSurfaceSwimChargesTheHorizontalBandAlone() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Swimmer");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A two-cell pond under the swim path: the exhaustion walk
            // consults the body's position AFTER the move, so the water
            // must cover the destination cell too (a real pond's shape —
            // the reference's isInWater consult at the stats walk).
            int cellX = (int) Math.floor(x);
            int cellZ = (int) Math.floor(z);
            placeWater(server, new BlockPosition(cellX, (int) y, cellZ));
            placeWater(server, new BlockPosition(cellX, (int) y, cellZ + 1));
            EngineAwaits.awaitTicks(server.world(), 3, "the pond settles");
            // One swum meter: 0.015 (reference lines 1319-1325).
            server.movementProposal(body, new Position(x, y, z + 1.0), Rotation.ZERO, false);
            await(() -> body.exhaustion() >= 0.015f, "the swim charge landed");
            assertEquals(0.015f, body.exhaustion(), 1.0E-4f,
                    "the surface swim's horizontal rate");
            // The surface bob — vertical only — pays nothing (the arm's
            // band is horizontal alone).
            float afterSwim = body.exhaustion();
            server.movementProposal(body, new Position(x, y + 0.5, z + 1.0),
                    Rotation.ZERO, false);
            EngineAwaits.awaitTicks(server.world(), 4, "the bob window");
            assertEquals(afterSwim, body.exhaustion(), 1.0E-4f,
                    "the vertical bob never charges the swim arm");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theDivedArmChargesTheDescentItself() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Diver");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            int cx = (int) Math.floor(x);
            int cz = (int) Math.floor(z);
            // A four-deep column above the pond floor: the eye submerges
            // (isSubmergedIn(Material.WATER) — the 3-D arm's gate).
            for (int i = 0; i < 4; i++) {
                placeWater(server, new BlockPosition(cx, (int) y + i, cz));
            }
            EngineAwaits.awaitTicks(server.world(), 3, "the column settles");
            // Park the body one cell up inside the column; the teleport
            // itself never charges (its own seat's law).
            server.teleportPlayer(body, new Position(x, y + 2.0, z));
            EngineAwaits.awaitTicks(server.world(), 3, "the dive seeds");
            assertEquals(0.0f, body.exhaustion(), 1.0E-4f,
                    "neither the water nor the teleport charged yet");
            // A one-meter descent: the 3-D norm pays 0.015 (reference
            // lines 1310-1318 — sqrt(x^2+y^2+z^2), the vertical included).
            server.movementProposal(body, new Position(x, y + 1.0, z), Rotation.ZERO, false);
            await(() -> body.exhaustion() >= 0.015f, "the dive charge landed");
            assertEquals(0.015f, body.exhaustion(), 1.0E-4f,
                    "the dived arm's 3-D rate on the pure descent");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMountedBodyPaysNoMovementCharges() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Passenger");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // Mount (the engine's vehicle carry owns the coordinates — the
            // reference's tickNonRidingMovementRelatedStats line 1305 gate:
            // the whole walk skips while vehicle != null).
            server.ticker().submit(() -> body.setRidingVehicleId(7));
            EngineAwaits.awaitTicks(server.world(), 2, "the mount lands");
            server.ticker().submit(() -> body.applyMovement(
                    new Position(x, y, z + 2.0), Rotation.ZERO, true));
            EngineAwaits.awaitTicks(server.world(), 4, "the mounted displacement window");
            assertEquals(0.0f, body.exhaustion(), 1.0E-6f,
                    "the vehicle's motion never charges the rider");
            // Dismount: honest walking resumes.
            server.ticker().submit(() -> body.setRidingVehicleId(-1));
            server.ticker().submit(() -> body.applyMovement(
                    new Position(x, y, z + 4.0), Rotation.ZERO, true));
            await(() -> body.exhaustion() >= 0.02f, "the dismounted walk charged");
            assertEquals(0.02f, body.exhaustion(), 1.0E-3f,
                    "two walked meters at the plain rate after the dismount");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theTeleportItselfNeverCharges() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Traveler");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A rising teleport while sprinting: the phantom-jump shape the
            // displacement inference charged before the fix (dy > 0 off a
            // valid scratch). The reference's stats walk measures the
            // movement walk's own displacement alone — setPosition jumps
            // never reach it.
            server.ticker().submit(() -> body.setSprinting(true));
            server.teleportPlayer(body, new Position(x, y + 10.0, z));
            EngineAwaits.awaitTicks(server.world(), 6, "the teleport window");
            assertEquals(0.0f, body.exhaustion(), 1.0E-6f,
                    "the rising teleport under sprint pays no phantom jump");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void suffocationInTheWallPaysNoExhaustion() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Walled");
            // Food below the regen gate keeps the window clean (the fold's
            // other arms stay out of the seat).
            body.setBody(20.0f, 10, 0.0f);
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A full cube at the eye band: the suffocation arm fires.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition((int) Math.floor(x), (int) (y + 1.62),
                            (int) Math.floor(z)),
                    BuiltinBlocks.STONE));
            await(() -> body.health() < 20.0f, "the suffocation wound landed");
            assertEquals(0.0f, body.exhaustion(), 1.0E-6f,
                    "the reference's IN_WALL rides setBypassesArmor() "
                            + "(DamageSource lines 18 + 121-127): exhaustion 0.0");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theLandedArrowPaysItsSourceExhaustion() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "PinCushion");
            body.setBody(20.0f, 10, 0.0f);
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double z = body.position().z();
            // A slow arrow aimed at the body's box: some tick's point lands
            // inside (0.3 m steps cannot jump the 0.6-deep box).
            server.projectiles().launch(ProjectileEntity.Kind.ARROW, -1,
                    new Position(x, body.position().y() + 1.0, z - 0.5),
                    0.0f, 0.0f, 0.3f, 0);
            await(() -> body.health() < 20.0f, "the arrow wound landed");
            assertEquals(0.3f, body.exhaustion(), 1.0E-4f,
                    "the arrow's indirect entity source pays the default 0.3 "
                            + "(PlayerEntity line 856 — no bypass setter in the ctor)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theLandedBlastPaysItsSourceExhaustion() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Blasted");
            body.setBody(20.0f, 10, 0.0f);
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A primed TNT two cells off: the easy-scaled blast wounds but
            // never kills (damage/2 + 1 at full hearts). The ignite rides
            // the tick thread (the region's ownership domain).
            server.ticker().submit(() -> server.tnt().ignite(server.world(), new BlockPosition(
                    (int) Math.floor(x) + 2, (int) y, (int) Math.floor(z)), 2));
            await(() -> body.health() < 20.0f, "the blast wound landed");
            assertEquals(0.3f, body.exhaustion(), 1.0E-4f,
                    "the explosion source (setScaledWithDifficulty().setExplosive(), "
                            + "neither zeroing) pays the default 0.3");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theAnvilCrushPaysItsSourceExhaustion() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Crushed");
            body.setBody(20.0f, 10, 0.0f);
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // An anvil four cells above the body's cell: the gravity fuse
            // (2 ticks) converts it, the fall crosses the body's cell, the
            // landing crush applies ceil(fall - 1) * 2 and the charge.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition((int) Math.floor(x), (int) y + 4,
                            (int) Math.floor(z)),
                    BuiltinBlocks.ANVIL));
            await(() -> body.health() < 20.0f, "the crush wound landed");
            assertEquals(0.3f, body.exhaustion(), 1.0E-4f,
                    "the anvil source (DamageSource line 27 — no bypass setter) "
                            + "pays the default 0.3");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theHungerEffectPaysPerAmplifier() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Peckish");
            body.setBody(20.0f, 20, 0.0f);
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            // Hunger II (amplifier 1): 0.025 * (amp + 1) = 0.05 per
            // application (reference StatusEffect.apply line 152).
            server.potions().add(body, new StatusEffectInstance(
                    StatusEffects.HUNGER, 400, 1));
            await(() -> body.exhaustion() >= 0.05f, "the hunger effect paid");
            assertTrue(body.exhaustion() < 0.05f * 6f,
                    "the accumulation steps by the per-application 0.05 alone");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMinedBlockPaysItsQuarterDime() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession miner = join(server, "Miner");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            BlockPosition target = new BlockPosition(2, 5, 2);
            seedBlock(server, miner, target, BuiltinBlocks.DIRT);
            server.blockInteraction().submitMiningStart(miner, target);
            // Dirt by hand: 15 ticks nominal; wait past it.
            EngineAwaits.awaitTicks(server.world(), 18, "the dig progresses");
            server.blockInteraction().submitMiningFinished(miner, target);
            await(() -> server.world().getBlock(target)
                    .identifier().value().equals("air"), "the dig committed");
            assertEquals(0.025f, miner.exhaustion(), 1.0E-4f,
                    "afterMinedByPlayer's flat quarter-dime (reference "
                            + "Block.java line 776)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePearlTeleportPaysNoPhantomJump() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Pearlist");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A 3x3 cap three cells above the thrower's cell: a pearl
            // launched straight up impacts the cap's underside, and the
            // teleport lands the thrower UP at the last crossed air cell —
            // the phantom-jump shape (dy > 0 off a standing scratch). The
            // 3x3 spread swallows the launch divergence (sigma 0.0075).
            int cellX = (int) Math.floor(x);
            int cellZ = (int) Math.floor(z);
            int capY = (int) Math.floor(y) + 3;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int fxDx = dx;
                    int fxDz = dz;
                    server.ticker().submit(() -> server.world().setBlock(
                            new BlockPosition(cellX + fxDx, capY, cellZ + fxDz),
                            BuiltinBlocks.STONE));
                }
            }
            EngineAwaits.awaitTicks(server.world(), 3, "the cap settles");
            // The pearl: straight up (pitch -90) from the eye — the
            // thrower's own engine id rides the shot (the sink resolves
            // the teleport target through it).
            server.projectiles().launch(ProjectileEntity.Kind.ENDER_PEARL,
                    body.engineEntityId(),
                    new Position(x, y + 1.62, z), 0.0f, -90.0f, 1.5f, 0);
            // The 5.0 fall toll proves the teleport fired (the FALL family
            // carries exhaustion 0.0 — the health drop is the only trace).
            await(() -> body.position().y() > y + 1.0, "the pearl carried the body up");
            EngineAwaits.awaitTicks(server.world(), 4, "the phantom window");
            assertTrue(body.exhaustion() < 0.005f,
                    "the upward pearl's position carry never charges the jump arm "
                            + "(the reference's setPositionAndUpdate never reaches "
                            + "the stats walk — PlayerEntity lines 1287-1303)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theDimensionArrivalWriteNeverCharges() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Crosser");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // The portal class's own write: applyDimensionArrival is the
            // nether walk's and the End entry's shared landing (the 8:1
            // coordinate carry + the forced spawn) — an upward arrival off
            // a standing scratch is exactly the phantom-jump shape.
            server.ticker().submit(() -> body.applyDimensionArrival(
                    0, new Position(x, y + 6.0, z), 0.0f, 0.0f));
            EngineAwaits.awaitTicks(server.world(), 6, "the crossing window");
            assertEquals(0.0f, body.exhaustion(), 1.0E-6f,
                    "the 8:1 nether walk and the End's forced spawn never "
                            + "charge the walk (setPositionAndAngles never "
                            + "reaches the stats walk)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theEndCreditsRespawnPaysNoPhantomJump() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Returner");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // The credits' landing: a high bed spawn makes the End-to-
            // overworld jump an upward one (the phantom-jump shape). The
            // arm keeps the whole body by design — only the position
            // carries.
            server.ticker().submit(() -> body.setBedSpawn(
                    new Position(x, y + 6.0, z)));
            await(() -> body.bedSpawn() != null, "the bed spawn landed");
            server.ticker().submit(body::markLeavingTheEnd);
            await(body::leavingTheEnd, "the credits armed");
            server.performRespawn(body);
            await(() -> !body.leavingTheEnd(), "the credits answered");
            EngineAwaits.awaitTicks(server.world(), 4, "the phantom window");
            assertEquals(0.0f, body.exhaustion(), 1.0E-6f,
                    "the End-credits respawn's landing never charges the "
                            + "jump arm (the reference's alive respawn is a "
                            + "setPosition — PlayerManager.respawn's "
                            + "movePlayer never reaches the stats walk)");
        } finally {
            server.shutdown(null);
        }
    }

    // --- the harness (the house pattern) -----------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "hunger", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void placeWater(EngineServer server, BlockPosition at) {
        server.ticker().submit(() -> server.world().setBlock(at,
                FluidBlocks.sourceOf(FluidBlocks.Kind.WATER)));
    }

    /** Places a block of the given type at the position (the place face 1 = up). */
    private void seedBlock(EngineServer server, PlayerSession player, BlockPosition at,
                           net.zaminmc.torch.block.BlockType type) {
        server.blockInteraction().submitPlace(player, at.offset(0, -1, 0), 1, type);
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
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
