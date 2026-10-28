package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.stat.EngineStats;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stats family's parity seats (the verifier's eleventh pass — the
 * findings doc docs/STATS_FAMILY_AUDIT_FINDINGS.md's ten restorations,
 * every law cited to its reference anchor):
 *
 * <ul>
 * <li>B1 — the mined row + the 0.025 ride the HARVEST gate
 *     (tryMineBlock lines 211-225: bare-hand stone pays neither);</li>
 * <li>B2 — the play clock runs on the DEAD body (PlayerEntity.tick
 *     lines 283-289: only the since-death row carries the isAlive gate);</li>
 * <li>A1 — the player-kill row at the score walk (takeKillScore lines
 *     558-560);</li>
 * <li>A2 — the victim's killed-by row on a mob death (ServerPlayerEntity
 *     .die lines 391-396, the light tracker with the 300-tick window);</li>
 * <li>A3/A4 — the movement rows walk EVERY gamemode
 *     (tickNonRidingMovementRelatedStats lines 1310-1350: the walk, the
 *     sprint/crouch twins, the dive's 3-D norm, the climb, the flown's
 *     strict &gt; 25, the jump row, the fallen row of takeFallDamage lines
 *     1376-1384);</li>
 * <li>A5 — the riding rows (tickRidingRelatedStats lines 1352-1373: the
 *     boat sails, the pig rides, the 3-D norm);</li>
 * <li>A6 — the fishing trio at the roll (FishingBobberEntity.getResult
 *     lines 499-510);</li>
 * <li>A7 — the villager pair (interactMob line 405 + TradeResultSlot line
 *     58);</li>
 * <li>B3/B4/B5 — the item-stat rows: the broken row rides the wear walk
 *     (takeDamageAndBreak line 229 — ONE site, every path), the use row
 *     of the dig (ItemStack.mineBlock lines 252-257) and of the swing
 *     (ItemStack.attack lines 245-250);</li>
 * <li>B6 — the outside-click drop counts (InventoryMenu lines 182/187's
 *     thrownByPlayer=TRUE pair).</li>
 * </ul>
 */
class StatsParityTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------ B1: the harvest gate

    @Test
    void theMinedRowRidesTheHarvestGate() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession miner = join(server, "Miner");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            // Stone by BARE HAND: canMineBlock false (the rock family
            // requires the pickaxe — PlayerInventory lines 458-465), so
            // tryMineBlock's flag1 && flag gate (line 223) never calls
            // afterMinedByPlayer — neither the mined row nor the 0.025.
            BlockPosition stone = new BlockPosition(2, 5, 2);
            seedBlock(server, miner, stone, BuiltinBlocks.STONE);
            server.blockInteraction().submitMiningStart(miner, stone);
            EngineAwaits.awaitTicks(server.world(), 155, "the bare-hand stone dig (the slow divisor)");
            server.blockInteraction().submitMiningFinished(miner, stone);
            await(() -> server.world().getBlock(stone)
                    .identifier().value().equals("air"), "the stone dig committed");
            assertEquals(0, miner.stats().get(EngineStats.mineBlockOf(
                            net.zaminmc.torch.util.Identifier.of("minecraft", "stone"))),
                    "bare-hand stone never pays the mined row (the harvest gate)");
            assertEquals(0.0f, miner.exhaustion(), 1.0E-4f,
                    "bare-hand stone never pays the 0.025 either");
            // Dirt by bare hand: isToolNotRequired — the row and the 0.025
            // both land (afterMinedByPlayer lines 774-776).
            BlockPosition dirt = new BlockPosition(3, 5, 2);
            seedBlock(server, miner, dirt, BuiltinBlocks.DIRT);
            server.blockInteraction().submitMiningStart(miner, dirt);
            EngineAwaits.awaitTicks(server.world(), 18, "the bare-hand dirt dig");
            server.blockInteraction().submitMiningFinished(miner, dirt);
            await(() -> server.world().getBlock(dirt)
                    .identifier().value().equals("air"), "the dirt dig committed");
            assertEquals(1, miner.stats().get(EngineStats.mineBlockOf(
                            net.zaminmc.torch.util.Identifier.of("minecraft", "dirt"))),
                    "the harvest-passing break pays the mined row");
            assertEquals(0.025f, miner.exhaustion(), 1.0E-4f,
                    "the harvest-passing break pays the quarter-dime");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ B2: the dead play clock

    @Test
    void thePlayClockRunsOnTheDeadBody() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Casualty");
            body.setBody(0.5f, 20, 5.0f);
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            int before = body.stats().get(EngineStats.MINUTES_PLAYED);
            server.damage(body, 5.0f);
            await(() -> body.dead(), "the body died");
            assertEquals(0, body.stats().get(EngineStats.TIME_SINCE_DEATH),
                    "the since-death clock resets at the death");
            // The respawn screen's window: the play clock keeps counting
            // (PlayerEntity.tick line 285 rides UNGATED; only line 287's
            // since-death row carries the isAlive gate).
            EngineAwaits.awaitTicks(server.world(), 10, "the respawn screen window");
            assertTrue(body.stats().get(EngineStats.MINUTES_PLAYED) >= before + 10,
                    "the dead body's play clock runs on");
            assertEquals(0, body.stats().get(EngineStats.TIME_SINCE_DEATH),
                    "the since-death clock never advances on the dead body");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ A1: the player-kill row

    @Test
    void thePlayerKillRowLandsAtTheScoreWalk() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession killer = join(server, "Slayer");
            PlayerSession victim = join(server, "Doomed");
            victim.setBody(0.5f, 20, 5.0f);
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            // The victim beside the killer (the reach gate).
            Position at = killer.position();
            server.teleportPlayer(victim, new Position(at.x() + 1.0, at.y(), at.z()));
            EngineAwaits.awaitTicks(server.world(), 2, "the victim lands");
            server.attackPlayer(killer, victim);
            await(() -> victim.dead(), "the punch killed the weakened victim");
            assertEquals(1, killer.stats().get(EngineStats.PLAYERS_KILLED),
                    "the PvP kill pays the player-kill row (takeKillScore "
                            + "lines 558-560)");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ A2: the killed-by row

    @Test
    void theVictimsKilledByRowLandsOnAMobDeath() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Bitten");
            body.setBody(1.0f, 20, 5.0f);
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            // A zombie beside the body: the melee goal arms within its
            // cadence and the wound's record rides the death walk.
            server.chatService().submitChat(body, "/spawnmob zombie 1");
            MobEntity zombie = awaitMob(server, MobType.ZOMBIE);
            Position at = body.position();
            server.ticker().submit(() -> zombie.teleport(
                    new Position(at.x() + 0.8, at.y(), at.z())));
            await(() -> body.dead(), "the zombie's melee killed the weakened body");
            assertEquals(1, body.stats().get(
                            EngineStats.killedByEntityOf("Zombie")),
                    "the mob death pays the victim's killed-by row "
                            + "(ServerPlayerEntity.die lines 391-396)");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ A3/A4: the movement rows

    @Test
    void theGroundRowsWalkSprintAndCrouchBeside() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Walker");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // Two plain meters: walked alone.
            server.movementProposal(body, new Position(x, y, z + 2.0), Rotation.ZERO, true);
            await(() -> body.stats().get(EngineStats.CM_WALKED) >= 200,
                    "the walk row paid");
            assertEquals(0, body.stats().get(EngineStats.SPRINT_ONE_CM),
                    "the plain walk never pays the sprint row");
            // Two sprinting meters: BOTH rows pay the same meter count
            // (lines 1331-1334 — sprint adds beside walked, never replaces).
            server.ticker().submit(() -> body.setSprinting(true));
            EngineAwaits.awaitTicks(server.world(), 2, "the flag lands");
            int walkedBefore = body.stats().get(EngineStats.CM_WALKED);
            server.movementProposal(body, new Position(x, y, z + 4.0), Rotation.ZERO, true);
            await(() -> body.stats().get(EngineStats.SPRINT_ONE_CM) >= 200,
                    "the sprint row paid");
            assertEquals(walkedBefore + 200, body.stats().get(EngineStats.CM_WALKED),
                    "the sprint pays walked beside sprint (the same k)");
            // Two sneaking meters (the sprint flag off): the crouch row
            // rides the non-sprint else (lines 1336-1338).
            server.ticker().submit(() -> {
                body.setSprinting(false);
                body.setSneaking(true);
            });
            EngineAwaits.awaitTicks(server.world(), 2, "the flags land");
            server.movementProposal(body, new Position(x, y, z + 6.0), Rotation.ZERO, true);
            await(() -> body.stats().get(EngineStats.CROUCH_ONE_CM) >= 200,
                    "the crouch row paid");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMovementRowsWalkEveryGamemode() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession creative = join(server, "Architect");
            server.ticker().submit(() -> creative.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            EngineAwaits.awaitTicks(server.world(), 3, "the mode lands");
            double x = creative.position().x();
            double y = creative.position().y();
            double z = creative.position().z();
            // The walk row is UNGATED (the reference's walk carries no
            // creative gate — the vanilla statistics screen counts a
            // creative player's walked meters).
            server.movementProposal(creative, new Position(x, y, z + 2.0), Rotation.ZERO, true);
            await(() -> creative.stats().get(EngineStats.CM_WALKED) >= 200,
                    "the creative walk row paid");
            // The exhaustion stays refused (addFatigue's own gate).
            assertEquals(0.0f, creative.exhaustion(), 1.0E-6f,
                    "the creative body never pays the fatigue half");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theDiveSwimAndFlownRowsPayTheirOwnBands() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Swimmer");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A water column for the dive, plus the ground-level pond cell
            // beside it for the surface stroke (pass 10's own geometry: the
            // walk consults the post-move position, so the destination cell
            // carries water at the FEET level alone — the eye cell above
            // stays dry and the stroke is the SURFACE arm, never the dive).
            final int cx = (int) Math.floor(x);
            final int cz = (int) Math.floor(z);
            final int baseY = (int) y;
            for (int i = 0; i < 4; i++) {
                final int dy = i;
                server.ticker().submit(() -> server.world().setBlock(
                        new BlockPosition(cx, baseY + dy, cz),
                        net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                                net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER)));
            }
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(cx, baseY, cz + 1),
                    net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                            net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER)));
            EngineAwaits.awaitTicks(server.world(), 3, "the pond settles");
            // The surface stroke first: one meter along the pond — the swum
            // row pays the horizontal band alone (lines 1319-1325).
            server.movementProposal(body, new Position(x, y, z + 1.0), Rotation.ZERO, false);
            await(() -> body.stats().get(EngineStats.CM_SWUM) >= 100,
                    "the swum row paid the surface stroke");
            assertEquals(0, body.stats().get(EngineStats.CM_DIVEN),
                    "the surface stroke never pays the dive row");
            // The dive: park the body up inside the column and descend a
            // meter — the dive row pays the 3-D norm (the descent itself
            // is swimming, lines 1310-1318).
            server.teleportPlayer(body, new Position(x, y + 2.0, z));
            EngineAwaits.awaitTicks(server.world(), 3, "the dive seeds");
            server.movementProposal(body, new Position(x, y + 1.0, z), Rotation.ZERO, false);
            await(() -> body.stats().get(EngineStats.CM_DIVEN) >= 100,
                    "the dived row paid over the 3-D norm");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theFlownRowPaysOnlyPastTwentyFiveCentimeters() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Flier");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A 0.2-meter horizontal drift while airborne: the reference's
            // STRICT l > 25 refuses (20 cm never pays).
            server.movementProposal(body, new Position(x, y + 1.0, z + 0.2),
                    Rotation.ZERO, false);
            EngineAwaits.awaitTicks(server.world(), 4, "the drift window");
            assertEquals(0, body.stats().get(EngineStats.CM_FLOWN),
                    "a 20-centimeter drift never pays the flown row (the strict gate)");
            // A full meter airborne: the row pays.
            server.movementProposal(body, new Position(x, y + 1.0, z + 1.2),
                    Rotation.ZERO, false);
            await(() -> body.stats().get(EngineStats.CM_FLOWN) >= 100,
                    "a meter of flight pays the flown row");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theJumpRowCountsEveryJump() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Hopper");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // The ground left under rising motion (PlayerEntity.jump line
            // 1278 — the JUMPS row beside the pass-10 fatigue).
            server.movementProposal(body, new Position(x, y + 0.5, z), Rotation.ZERO, false);
            await(() -> body.stats().get(EngineStats.JUMPS) >= 1,
                    "the jump row paid");
            assertEquals(0.2f, body.exhaustion(), 1.0E-4f,
                    "the plain jump's fatigue rides beside the row");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theFallenRowCountsTwoMeterLandings() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Parachutist");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A five-meter fall onto the ground: the fallen row pays
            // round(5 * 100) = 500 (takeFallDamage lines 1376-1384).
            server.movementProposal(body, new Position(x, y + 5.0, z), Rotation.ZERO, false);
            EngineAwaits.awaitTicks(server.world(), 4, "the fall window");
            server.movementProposal(body, new Position(x, y, z), Rotation.ZERO, true);
            await(() -> body.stats().get(EngineStats.CM_FALLEN) >= 500,
                    "the five-meter landing paid the fallen row");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ A5: the riding rows

    @Test
    void theBoatRideRowSails() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession sailor = join(server, "Sailor");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            Position at = sailor.position();
            VehicleEntity boat = server.vehicles().spawnBoat(
                    new Position(at.x() + 2.0, at.y(), at.z()), 0);
            server.ticker().submit(() ->
                    server.vehicles().mount(boat, sailor.engineEntityId()));
            await(() -> sailor.ridingAny(), "the seat opened");
            EngineAwaits.awaitTicks(server.world(), 3, "the seat anchors");
            // The hull a meter east: the rider's seat walks with it, the
            // sailed row paying the 3-D displacement (tickRidingRelatedStats
            // lines 1352-1373 — the pre-audit walk skipped boats entirely).
            server.ticker().submit(() -> boat.setPosition(
                    new Position(at.x() + 3.0, at.y(), at.z())));
            await(() -> sailor.stats().get(EngineStats.CM_SAILED) >= 100,
                    "the boat's move paid the sailed row");
            assertEquals(0, sailor.stats().get(EngineStats.CM_MINECART),
                    "the boat never pays the cart row");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePigRideRowPaysItsOwnBand() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession rider = join(server, "Jockey");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            server.chatService().submitChat(rider, "/spawnmob pig 1");
            MobEntity pig = awaitMob(server, MobType.PIG);
            Position at = rider.position();
            server.ticker().submit(() -> {
                pig.teleport(new Position(at.x() + 1.0, at.y(), at.z()));
                pig.setRider(rider.engineEntityId());
                rider.setRidingMobId(pig.entityId());
            });
            await(() -> rider.ridingAny(), "the pig seat opened");
            EngineAwaits.awaitTicks(server.world(), 3, "the seat anchors");
            // The pig a meter east: the seat walk pays the pig row
            // (tickRidingRelatedStats lines 1366-1367 — the pre-audit
            // mob-seat walk paid no stat at all).
            server.ticker().submit(() -> pig.teleport(
                    new Position(at.x() + 2.0, at.y(), at.z())));
            await(() -> rider.stats().get(EngineStats.CM_PIG) >= 100,
                    "the pig's move paid the pig row");
            assertEquals(0, rider.stats().get(EngineStats.CM_HORSE),
                    "the pig never pays the horse row");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ B6: the outside-click drop

    @Test
    void theOutsideClickDropCounts() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Sorter");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            server.ticker().submit(() -> body.inventory().cursorBox().set(
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIRT, 3)));
            // The slot -999 left click: the whole cursor throws — a
            // thrownByPlayer drop (InventoryMenu lines 182/187), the DROPS
            // row paying exactly like the Q-drop.
            server.windowClick(body, 0, -999, 0, 0, ok -> {
            });
            await(() -> body.stats().get(EngineStats.DROPS) == 1,
                    "the outside click's throw paid the drop row");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ B4 + B5: the use rows

    @Test
    void theUseRowRidesTheDigAndTheSwing() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession miner = join(server, "Digger");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            // A wooden shovel in the hand: the dig's use row pays per
            // broken block (ItemStack.mineBlock lines 252-257 — the flag
            // rides OUTSIDE the hardness gate, every tool family).
            server.ticker().submit(() -> miner.inventory().setSlot(0,
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.WOODEN_SHOVEL, 1)));
            EngineAwaits.awaitTicks(server.world(), 2, "the tool lands");
            BlockPosition dirt = new BlockPosition(2, 5, 2);
            seedBlock(server, miner, dirt, BuiltinBlocks.DIRT);
            server.blockInteraction().submitMiningStart(miner, dirt);
            EngineAwaits.awaitTicks(server.world(), 6, "the shovel dig");
            server.blockInteraction().submitMiningFinished(miner, dirt);
            await(() -> server.world().getBlock(dirt)
                    .identifier().value().equals("air"), "the dig committed");
            assertEquals(1, miner.stats().get(EngineStats.useItemOf(
                            BuiltinItems.WOODEN_SHOVEL.identifier())),
                    "the tool's dig paid its use row");
            // The swing's use row: a sword's landed hit on a mob pays once
            // (ItemStack.attack lines 245-250 — the flag inside the landed
            // verdict of PlayerEntity.attack lines 1029-1034).
            server.ticker().submit(() -> miner.inventory().setSlot(0,
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.WOODEN_SWORD, 1)));
            EngineAwaits.awaitTicks(server.world(), 2, "the sword lands");
            server.chatService().submitChat(miner, "/spawnmob sheep 1");
            MobEntity sheep = awaitMob(server, MobType.SHEEP);
            Position at = miner.position();
            server.ticker().submit(() -> sheep.teleport(
                    new Position(at.x() + 1.0, at.y(), at.z())));
            EngineAwaits.awaitTicks(server.world(), 2, "the sheep lands");
            server.attackEntity(miner, sheep.entityId());
            await(() -> sheep.health() < sheep.type().maxHealth,
                    "the swing landed");
            assertEquals(1, miner.stats().get(EngineStats.useItemOf(
                            BuiltinItems.WOODEN_SWORD.identifier())),
                    "the sword's landed swing paid its use row");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ B3: the break row

    @Test
    void theBreakRowRidesTheWearWalk() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession miner = join(server, "Wrecker");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            // A wooden shovel at its LAST surviving point (the pass-4
            // boundary law: the stack SURVIVES at exactly the maximum and
            // breaks only when the wear EXCEEDS it): the dig's one wear
            // passes the max and the break row lands through the damage
            // walk's own site (takeDamageAndBreak line 229 — every wear
            // path's single row).
            net.zaminmc.torch.item.ItemStack nearlyDone =
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.WOODEN_SHOVEL, 1)
                            .withDamage(BuiltinItems.WOODEN_SHOVEL.maxDurability());
            server.ticker().submit(() -> miner.inventory().setSlot(0, nearlyDone));
            EngineAwaits.awaitTicks(server.world(), 2, "the tool lands");
            BlockPosition dirt = new BlockPosition(2, 5, 2);
            seedBlock(server, miner, dirt, BuiltinBlocks.DIRT);
            server.blockInteraction().submitMiningStart(miner, dirt);
            EngineAwaits.awaitTicks(server.world(), 6, "the shovel dig");
            server.blockInteraction().submitMiningFinished(miner, dirt);
            await(() -> miner.inventory().held().isEmpty(), "the tool broke");
            assertEquals(1, miner.stats().get(EngineStats.breakItemOf(
                            BuiltinItems.WOODEN_SHOVEL.identifier())),
                    "the dig's fatal wear paid the break row");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theWaterLeapPaysNoJumpRow() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession body = join(server, "Puddled");
            EngineAwaits.awaitTicks(server.world(), 3, "the scratch seeds");
            double x = body.position().x();
            double y = body.position().y();
            double z = body.position().z();
            // A one-deep pond at the standing cell: the feet sit in water
            // (isInWater at the origin), the eye stays above (not
            // submerged). The leap then originates IN the water — the
            // reference's jump input walk (LivingEntity lines 1446-1456)
            // sends isInWater() to jumpInWater() BEFORE the ground arm, so
            // jump() — the row AND the fatigue — never fires: a
            // shallow-water leap pays nothing at the jump site.
            int cellX = (int) Math.floor(x);
            int cellZ = (int) Math.floor(z);
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(cellX, (int) Math.floor(y), cellZ),
                    net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                            net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER)));
            EngineAwaits.awaitTicks(server.world(), 3, "the pond settles");
            assertEquals(0, body.stats().get(EngineStats.JUMPS),
                    "the pond stand paid no jump");
            assertEquals(0.0f, body.exhaustion(), 1.0E-6f,
                    "the pond stand charged nothing");
            // The leap: up and one cell over, onto the dry neighbor,
            // mid-air at the walk's consult — the unfixed arm reads it as
            // a ground-leaving jump (dy > 0 off a standing scratch).
            server.movementProposal(body, new Position(x + 1.0, y + 0.42, z),
                    Rotation.ZERO, false);
            EngineAwaits.awaitTicks(server.world(), 4, "the leap window");
            assertEquals(0, body.stats().get(EngineStats.JUMPS),
                    "the water-origin leap pays no JUMPS row (the input "
                            + "walk's water arm consumed it — jumpInWater, "
                            + "not jump)");
            assertEquals(0.0f, body.exhaustion(), 1.0E-6f,
                    "the water-origin leap pays no jump fatigue");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------ the harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "stats", "it", 20, 2, 20,
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

    /** Places a block of the given type at the position (the place face 1 = up). */
    private void seedBlock(EngineServer server, PlayerSession player, BlockPosition at,
                           net.zaminmc.torch.block.BlockType type) {
        server.blockInteraction().submitPlace(player, at.offset(0, -1, 0), 1, type);
    }

    private MobEntity awaitMob(EngineServer server, MobType type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            for (MobEntity mob : server.mobs().all()) {
                if (mob.type() == type && !mob.dead()) {
                    return mob;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The " + type + " never spawned");
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
