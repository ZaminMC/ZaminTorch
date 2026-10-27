package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave TF-9 — the item-entity family's dimension reads (the reference's
 * Entity.world, the mob query's own Slice-25d shape generalized): the
 * bodies carry their own dimension, every spawn walk stamps it, the shared
 * ground flips before each body's tick, the merge and the pickup walks
 * consult it, and the ZID v8 store round-trips it.
 */
class ItemDimensionReadsTest {

    /**
     * The recording ground: the solid read resolves through the ACTIVE
     * dimension the manager last flipped to (the plain overworld stub's
     * surface below y=4 exists in the OVERWORLD alone — the nether reads
     * empty space).
     */
    private static final class DimensionGround implements ItemEntity.Ground {
        int lastDimension = Integer.MIN_VALUE;
        int switches;

        @Override
        public void setActiveDimension(int dimension) {
            this.lastDimension = dimension;
            this.switches++;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            // The overworld's surface at y=4; the nether holds NOTHING
            // (the same-coordinate terrain exists in one world alone).
            return lastDimension == 0 && y < 4.0;
        }
    }

    private PlayerSession playingPlayer(String name, Position at, int dimension) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.setDimension(dimension);
        session.applyMovement(at, Rotation.ZERO, true);
        return session;
    }

    /** Minimal world stub: sessions need a World reference only. */
    private net.zaminmc.torch.World stubWorld() {
        return new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(net.zaminmc.torch.block.BlockPosition p, net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }

            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        };
    }

    @Test
    void theSpawnWalksStampTheDimension() {
        ItemEntityManager manager = new ItemEntityManager(new DimensionGround(),
                new Random(11), 1);
        ItemEntity drop = manager.spawnDropAtBlock(new Position(2, 6, 2),
                ItemStack.of(BuiltinItems.NETHERRACK, 1),
                ItemEntity.PICKUP_DELAY_DROP_TICKS, -1);
        ItemEntity thrown = manager.spawnThrown(new Position(3.5, 6.0, 3.5),
                ItemStack.of(BuiltinItems.GOLD_NUGGET, 1), -1);
        ItemEntity restored = manager.spawnRestored(new Position(4.5, 6.0, 4.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 1);
        ItemEntity plain = manager.spawnDropAtBlock(new Position(5, 6, 5),
                ItemStack.of(BuiltinItems.DIRT, 1), ItemEntity.PICKUP_DELAY_DROP_TICKS);
        assertEquals(-1, drop.dimension(), "the stamped drop lives in the nether");
        assertEquals(-1, thrown.dimension(), "the stamped throw lives in the nether");
        assertEquals(1, restored.dimension(), "the stamped restore lives in the end");
        assertEquals(0, plain.dimension(), "the three-arg form keeps the overworld 0");
    }

    @Test
    void theTickWalkFlipsTheGroundPerBody() {
        DimensionGround ground = new DimensionGround();
        ItemEntityManager manager = new ItemEntityManager(ground, new Random(12), 1);
        ItemEntity overworld = manager.spawnDropAtBlock(new Position(0, 6, 0),
                ItemStack.of(BuiltinItems.DIRT, 1), 0);
        ItemEntity nether = manager.spawnDropAtBlock(new Position(2, 6, 2),
                ItemStack.of(BuiltinItems.NETHERRACK, 1), 0, -1);

        ground.switches = 0;
        for (int i = 0; i < 40; i++) {
            manager.tick(List.of());
        }
        assertTrue(ground.switches >= 2,
                "both bodies' tick walks flipped the shared ground (got " + ground.switches + ")");
        assertEquals(-1, ground.lastDimension,
                "the walk ends on the last body's dimension (the list's own order)");

        // THE READ ITSELF: the overworld body settles on the surface at
        // y=4 (its center 4.125); the nether body NEVER lands — its world
        // holds nothing at these coordinates (the same-coordinate surface
        // exists in the overworld alone), so it keeps falling.
        assertTrue(overworld.onGround(), "the overworld body rests on the overworld's surface");
        assertFalse(nether.onGround(),
                "the nether body's read answers from the nether alone — no same-coordinate floor");
        assertTrue(nether.position().y() < overworld.position().y(),
                "the nether body fell past the overworld's floor level");
    }

    @Test
    void theMergeWalkGatesOnTheDimension() {
        // mergeWithNearbyItems walks world.getEntitiesOfType: only the
        // body's OWN-WORLD items combine. Same coordinates in two
        // dimensions never merge.
        ItemEntityManager manager = new ItemEntityManager(
                (x, y, z) -> false, new Random(13), 1);
        ItemEntity overworld = manager.spawnRestored(new Position(1.0, 5.0, 1.0),
                ItemStack.of(BuiltinItems.DIRT, 1));
        ItemEntity nether = manager.spawnRestored(new Position(1.0, 5.0, 1.0),
                ItemStack.of(BuiltinItems.DIRT, 1), -1);
        ItemEntity netherTwin = manager.spawnRestored(new Position(1.05, 5.0, 1.0),
                ItemStack.of(BuiltinItems.DIRT, 2), -1);

        manager.tick(List.of());
        assertEquals(2, manager.size(),
                "the cross-dimension pair never merged; the same-dimension pair did");
        assertEquals(3, nether.stack().count(),
                "the nether twin absorbed into the nether keeper");
        assertEquals(1, overworld.stack().count(),
                "the overworld body kept its own stack whole");
    }

    @Test
    void thePickupWalkGatesOnTheCollectorDimension() {
        // onPlayerCollision runs inside the item's own world: a player in
        // another dimension never collects a body at the same coordinates.
        ItemEntityManager manager = new ItemEntityManager(
                (x, y, z) -> false, new Random(14), 1);
        manager.spawnRestored(new Position(2.5, 4.0, 2.5),
                ItemStack.of(BuiltinItems.DIRT, 3));
        PlayerSession netherPlayer = playingPlayer("NetherCollector",
                new Position(2.5, 4.0, 2.5), -1);
        PlayerSession overworldPlayer = playingPlayer("Collector",
                new Position(2.5, 4.0, 2.5), 0);

        // The nether player stands ON the overworld item's coordinates and
        // never collects it; the overworld player at the same spot does.
        manager.tick(List.of(netherPlayer));
        assertEquals(1, manager.size(), "the cross-dimension collector never picks up");
        manager.tick(List.of(netherPlayer, overworldPlayer));
        assertEquals(0, manager.size(), "the own-dimension collector collects");
        assertEquals(3, overworldPlayer.inventory().held().count());
        assertTrue(netherPlayer.inventory().held().isEmpty(),
                "the nether player's inventory stayed empty");
    }

    @Test
    void theDimensionRoundTripsThroughTheZidStore(@TempDir Path tempDir) {
        ItemDataStore store = new ItemDataStore(tempDir.resolve("items.bin"));
        List<ItemEntityManager.ItemRecord> saved = List.of(
                new ItemEntityManager.ItemRecord(new Position(1.5, 8.0, 1.5),
                        0.0, 0.0, 0.0, ItemStack.of(BuiltinItems.NETHERRACK, 2),
                        100, 0, false, null, null, -1),
                new ItemEntityManager.ItemRecord(new Position(2.5, 8.0, 2.5),
                        0.0, 0.0, 0.0, ItemStack.of(BuiltinItems.DIRT, 1),
                        0, 10, false, "Thrower", null, 0),
                new ItemEntityManager.ItemRecord(new Position(3.5, 8.0, 3.5),
                        0.0, 0.0, 0.0, ItemStack.of(BuiltinItems.ENDER_EYE, 1),
                        0, 0, true, null, "Owner", 1));
        store.save(saved);
        List<ItemEntityManager.ItemRecord> loaded = store.load();
        assertEquals(-1, loaded.get(0).dimension(), "the nether byte round-trips (u8 255)");
        assertEquals(0, loaded.get(1).dimension(), "the overworld default stays implicit");
        assertEquals(1, loaded.get(2).dimension(), "the end byte round-trips");
        assertEquals("Thrower", loaded.get(1).thrower(), "the rows after the dimension stay whole");

        // The restore lands the bodies in their own worlds.
        ItemEntityManager manager = new ItemEntityManager(
                (x, y, z) -> false, new Random(15), 1);
        manager.restoreAll(loaded);
        assertEquals(-1, manager.all().get(0).dimension(), "the pigman-world body restores into the nether");
        assertEquals(0, manager.all().get(1).dimension());
        assertEquals(1, manager.all().get(2).dimension());
    }

    @Test
    void theSnapshotCarriesTheLiveDimension() {
        ItemEntityManager manager = new ItemEntityManager(
                (x, y, z) -> false, new Random(16), 1);
        manager.spawnRestored(new Position(1.0, 5.0, 1.0),
                ItemStack.of(BuiltinItems.DIRT, 1), -1);
        List<ItemEntityManager.ItemRecord> snapshot = manager.snapshot();
        assertEquals(-1, snapshot.get(0).dimension(),
                "the save walk carries the body's own dimension (the ZID v8 row)");
    }
}
