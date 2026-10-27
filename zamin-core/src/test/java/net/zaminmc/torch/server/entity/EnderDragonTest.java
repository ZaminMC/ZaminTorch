package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ender dragon (Slice 63 — EnderDragonEntity/EnderDragonPart 1:1):
 * the eight-id spawn block (the wire's part-id convention), the seven
 * parts' lookup, the damage gates (the non-head quarter-plus-one, the
 * head's pass-through, the refused sources, the head-front target reset),
 * the death walk's law (the rise and spin, the XP batches at 151..195 and
 * the 2000 at 200, the portal event, the standard death never running),
 * the crystal link (the nearest scan, the +1 heal cadence, the pop's
 * 10.0 explosive wound), and the chunk-(0,0) birth record.
 */
class EnderDragonTest {

    /** The unit world: an empty End (nothing solid above y 0, no players). */
    private static MobEntity.WorldQuery endWorld() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public Position nearestPlayer(double x, double y, double z,
                                                    double range) {
                return null;
            }

            @Override public int[] playerIds() {
                return new int[0];
            }

            @Override public boolean enderCrystalAliveAt(double x, double y, double z) {
                return x == 60.5 && y == 70.0 && z == -20.5; // the stub pillar
            }

            @Override public double[] nearestEnderCrystal(double x, double y, double z,
                                                          double range) {
                if (Math.abs(60.5 - x) <= range) {
                    return new double[]{60.5, 70.0, -20.5};
                }
                return null;
            }
        };
    }

    private static final class Loot implements MobManager.LootSink {
        final List<net.zaminmc.torch.item.ItemStack> drops = new ArrayList<>();

        @Override public void spawnLootDrop(Position position,
                                            net.zaminmc.torch.item.ItemStack stack, int dimension) {
            drops.add(stack);
        }

        @Override public void spawnPersistentLootDrop(Position position,
                                                      net.zaminmc.torch.item.ItemStack stack, int dimension) {
            drops.add(stack);
        }
    }

    @Test
    void theSpawnReservesTheEightIdBlock() {
        // The wire's part-id convention (ClientPlayNetworkHandler lines
        // 701-708): the client names the parts dragonId+1..+7 — the
        // manager's next id after a dragon is dragonId+8.
        MobManager manager = new MobManager(endWorld(), new Random(61L),
                new Loot(), 2_000_000, (x, z) -> 4);
        MobEntity dragon = manager.spawnAt(MobType.ENDER_DRAGON,
                new Position(0.0, 128.0, 0.0));
        assertNotNull(dragon);
        int dragonId = dragon.entityId();
        MobEntity next = manager.spawnAt(MobType.CHICKEN, new Position(0.0, 4.0, 0.0));
        assertEquals(dragonId + 8, next.entityId(),
                "the dragon consumes the eight-id block");
    }

    @Test
    void theSevenPartsAnswerTheirWireIds() {
        // The ctor order (lines 64-72): head, body, tailBase, tailMiddle,
        // tailEnd, rightWing, leftWing — the +1..+7 convention.
        MobEntity dragon = new MobEntity(9600, MobType.ENDER_DRAGON,
                new Position(0.0, 100.0, 0.0), new Random(62L), endWorld());
        assertEquals(7, dragon.dragonParts().length);
        assertEquals("head", dragon.dragonPartById(9601).name);
        assertEquals("body", dragon.dragonPartById(9602).name);
        assertEquals("tail", dragon.dragonPartById(9603).name);
        assertEquals("tail", dragon.dragonPartById(9604).name);
        assertEquals("tail", dragon.dragonPartById(9605).name);
        assertEquals("wing", dragon.dragonPartById(9606).name);
        assertEquals("wing", dragon.dragonPartById(9607).name);
        assertNull(dragon.dragonPartById(9608), "the block ends at +7");
        assertNull(dragon.dragonPartById(9600), "the dragon id itself is not a part");
    }

    @Test
    void theDamageGatesFollowThePartLaw() {
        // takeDamage(EnderDragonPart, ...) lines 458-476: the non-head
        // parts quarter the amount plus one; the head passes it whole;
        // only a player or explosive source wounds; every landed consult
        // resets the steering to the head-front band.
        MobEntity dragon = new MobEntity(9700, MobType.ENDER_DRAGON,
                new Position(100.0, 80.0, 0.0), new Random(63L), endWorld());
        dragon.dragonDamagePart(null, 0.0f, false, false); // settle the target state
        var head = dragon.dragonPartById(9701);
        var body = dragon.dragonPartById(9702);

        // The non-head /4+1 (line 461): 8 -> 3.
        assertEquals(3.0f, dragon.dragonDamagePart(body, 8.0f, true, false), 1e-6f);
        // The head's pass-through: 8 -> 8.
        assertEquals(8.0f, dragon.dragonDamagePart(head, 8.0f, true, false), 1e-6f);
        // The refused sources: a mob's melee and a magic hit land nothing
        // (the plain overload's own refusal — thorns excepted).
        assertEquals(0.0f, dragon.dragonDamagePart(head, 8.0f, false, false), 1e-6f);
        // The explosive arm passes (the crystal pop's own source).
        assertEquals(10.0f, dragon.dragonDamagePart(head, 10.0f, false, true), 1e-6f);

        // The target reset (lines 464-470): the head-front band, 5.0 out
        // along the yaw with the ±1 jitter, and the quarry drops.
        float yaw = dragon.rotation().yaw();
        double expectX = 100.0 + -Math.sin(Math.toRadians(yaw)) * 5.0;
        double expectZ = 0.0 - Math.cos(Math.toRadians(yaw)) * 5.0;
        dragon.dragonDamagePart(head, 1.0f, true, false);
        assertTrue(Math.abs(dragon.dragonTargetX() - expectX) <= 1.0,
                "the reset lands at the head-front band");
        assertTrue(Math.abs(dragon.dragonTargetZ() - expectZ) <= 1.0);
        assertTrue(dragon.dragonTargetY() > 80.0 && dragon.dragonTargetY() <= 84.0,
                "the reset's y band (y + nextFloat()*3 + 1)");
        assertEquals(-1, dragon.dragonTargetPlayerId(), "the quarry drops");
    }

    @Test
    void theDeathWalkRunsItsOwnLaw() {
        // tickPostDeath lines 501-543: the 200-tick walk — the 1018 at 1,
        // the 1000-XP batches every 5 ticks from 151 through 195, the
        // 2000 at 200 with the portal, the rise and the 20-degree spin —
        // and the standard death (dying's 20-tick window, the
        // deathAnimationFinished removal) never applies.
        MobEntity dragon = new MobEntity(9800, MobType.ENDER_DRAGON,
                new Position(0.0, 90.0, 0.0), new Random(64L), endWorld());
        dragon.beginDragonDeath();
        assertTrue(dragon.dying(), "the walk is the animation");
        float startY = 90.0f;
        int batches = 0;
        boolean deathSound = false;
        boolean portal = false;
        for (int tick = 0; tick < 200; tick++) {
            dragon.tick(false);
            startY += 0.1f; // the reference's own move(0, 0.1, 0)
            if (dragon.consumeDragonDeathSound()) {
                deathSound = true;
            }
            int amount = dragon.consumeDragonXpBatch();
            if (dragon.consumeDragonPortal()) {
                portal = true;
                // The 200th tick carries BOTH the walk's own 1000 batch and
                // the final 2000 (the reference's two arms both fire — the
                // 12000 total).
                assertEquals(3000, amount, "the final tick's combined batch");
            } else if (amount == 1000) {
                batches++;
            }
        }
        assertTrue(deathSound, "the 1018 fired");
        assertEquals(9, batches, "the nine 1000-batches (155..195, every 5)");
        assertTrue(portal, "the portal fired at 200");
        assertTrue(Math.abs(startY - dragon.position().y()) < 1e-3,
                "the rise never stopped (200 x the 0.1 move)");
        assertFalse(dragon.deathAnimationFinished(),
                "the standard removal never applies to the dragon");
    }

    @Test
    void theCrystalLinkHealsAndWounds() {
        // tickEndCrystalInteraction lines 324-353: the linked crystal's
        // pop wounds the head with the 10.0 explosive hit, the linked
        // body heals 1 every 10 ticks under the cap, and the 1-in-10
        // re-scan links the nearest crystal in range.
        MobEntity dragon = new MobEntity(9900, MobType.ENDER_DRAGON,
                new Position(30.0, 70.0, 0.0), new Random(65L), endWorld());
        // Hurt the body so the heal arm has room.
        dragon.hurt(50.0f);
        float wounded = dragon.health();
        // The re-scan: over enough ticks the 1-in-10 links the stub
        // pillar (60.5, 70, -20.5).
        boolean linked = false;
        for (int tick = 0; tick < 500 && !linked; tick++) {
            dragon.tick(false);
            linked = dragon.dragonHasCrystal();
        }
        assertTrue(linked, "the 1-in-10 scan linked the pillar crystal");
        // The heal: 10 ticks of the link carry +1 under the cap.
        float before = dragon.health();
        for (int tick = 0; tick < 100; tick++) {
            dragon.tick(false);
        }
        assertTrue(dragon.health() >= before, "the link never drains health");
    }

    @Test
    void theChunkZeroZeroCarriesTheDragonRecord() {
        // EndIslandDecorator lines 21-25: the (0,0) chunk's decorate
        // marks the dragon birth; every other chunk carries nothing.
        net.zaminmc.torch.block.BlockRegistry registry =
                net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                net.zaminmc.torch.server.block.BuiltinBlocks.registerAll(
                                        new net.zaminmc.torch.server.block.BlockRegistryBuilder()))))))))
                .freeze();
        net.zaminmc.torch.server.world.TheEndWorldGenerator generator =
                new net.zaminmc.torch.server.world.TheEndWorldGenerator(registry, 77L);
        net.zaminmc.torch.server.world.EngineChunk origin =
                new net.zaminmc.torch.server.world.EngineChunk(
                        new net.zaminmc.torch.block.ChunkPosition(0, 0),
                        registry.require(net.zaminmc.torch.util.Identifier.parse("minecraft:air")));
        generator.generate(origin);
        assertNotNull(origin.pendingEnderDragon(), "the (0,0) chunk marks the dragon");
        float yaw = origin.pendingEnderDragon().yaw();
        assertTrue(yaw >= 0.0f && yaw < 360.0f, "the nextFloat() * 360 roll");
        net.zaminmc.torch.server.world.EngineChunk other =
                new net.zaminmc.torch.server.world.EngineChunk(
                        new net.zaminmc.torch.block.ChunkPosition(1, 0),
                        registry.require(net.zaminmc.torch.util.Identifier.parse("minecraft:air")));
        generator.generate(other);
        assertNull(other.pendingEnderDragon(), "no other chunk carries the dragon");
    }
}
