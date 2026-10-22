package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.world.LocalDifficulty;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The local-difficulty seam (VIII14 — the completion's wiring evidence):
 * the spawn dice inside {@link MobManager#spawnAt} consult the wired
 * LocalDifficulty read at the SPAWN POSITION and the SPAWN DIMENSION —
 * the reference's {@code world.getLocalDifficulty(pos)} over the body's
 * own world. The natural overworld cycle consults dimension 0; the
 * nether's own cycle arms -1 around its walk; the reinforcement callee
 * arms the CALLER's dimension (the reference spawns the callee in the
 * caller's world — the callee body itself carries that dimension now).
 * The multiplier math is {@code LocalDifficultyTest}'s own domain (the
 * reference-shadow sweep); this file pins the WIRING.
 */
class LocalDifficultySeamTest {

    /** The flat stub world (the ZombieAttributesTest DoorWorld's own shape). */
    private static final class StubWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < 4.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    private static final MobManager.LootSink loot = new MobManager.LootSink() {
        @Override
        public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack) {
        }
    };

    /**
     * The recording consult: every (position, dimension) pair the dice
     * read, answering the fixed multiplier 0.5 (base 3 + the saturated
     * time term + no inhabited + no moon: regional 3.0 exactly — the
     * (3 - 2) / 2 band).
     */
    private static final class RecordingConsult {
        final List<Position> positions = new ArrayList<>();
        final List<Integer> dimensions = new ArrayList<>();

        java.util.function.BiFunction<Position, Integer, LocalDifficulty> seam() {
            return (position, dimension) -> {
                positions.add(position);
                dimensions.add(dimension);
                return new LocalDifficulty(3, 1_512_000L, 0L, 0.0F);
            };
        }
    }

    private static MobManager manager(RecordingConsult consult) {
        MobManager manager = new MobManager(new StubWorld(), new Random(7L), loot,
                990_000, (x, z) -> 4);
        manager.setDifficultyRead(() -> 3);
        manager.setLocalDifficultyRead(consult.seam());
        return manager;
    }

    @Test
    void theSpawnDiceConsultThePositionAndTheOverworldDimension() {
        RecordingConsult consult = new RecordingConsult();
        MobManager manager = manager(consult);
        Position at = new Position(12.5, 5.0, -7.5);
        manager.spawnAt(MobType.ZOMBIE, at);
        assertEquals(4, consult.positions.size(),
                "the four dice sites each consulted (pickup, door-break, equipment, leader)");
        for (Position read : consult.positions) {
            assertEquals(at.x(), read.x(), 0.0);
            assertEquals(at.z(), read.z(), 0.0);
        }
        for (int dimension : consult.dimensions) {
            assertEquals(0, dimension, "the plain spawn walk's default dimension is the overworld");
        }
    }

    @Test
    void theNetherCycleArmsItsOwnDimension() {
        RecordingConsult consult = new RecordingConsult();
        MobManager manager = manager(consult);
        manager.spawnNaturalNether(MobType.ZOMBIE_PIGMAN, new Position(3.5, 5.0, 3.5),
                false, 90.0F);
        assertTrue(consult.positions.size() >= 1, "the pigman's pickup dice consulted");
        for (int dimension : consult.dimensions) {
            assertEquals(-1, dimension, "the nether cycle's dice read the nether's own world");
        }
        // The arm RESETS after the walk (a following overworld spawn reads 0).
        consult.positions.clear();
        consult.dimensions.clear();
        manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 5.0, 0.5));
        for (int dimension : consult.dimensions) {
            assertEquals(0, dimension, "the arm restored the overworld default");
        }
    }

    @Test
    void theUnwiredManagerKeepsTheHistoricalStandIn() {
        // The bare test boot (no seam wired): the zeroed stand-in answers —
        // the pre-VIII14 shape, the fresh-chunk new-moon read.
        MobManager manager = new MobManager(new StubWorld(), new Random(7L), loot,
                990_000, (x, z) -> 4);
        manager.setDifficultyRead(() -> 3);
        // The stand-in at difficulty 3, the fresh wrapped clock: 3 * 0.75
        // = 2.25 -> the multiplier 0.125 — the dice still roll (the
        // historical band).
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 5.0, 0.5));
        assertEquals(0, zombie.dimension(), "the plain spawn's body is an overworld body");
        // The nether walk still stamps the body's own dimension.
        MobEntity pigman = manager.spawnNaturalNether(MobType.ZOMBIE_PIGMAN,
                new Position(3.5, 5.0, 3.5), false, 0.0F);
        assertEquals(-1, pigman.dimension());
    }
}
