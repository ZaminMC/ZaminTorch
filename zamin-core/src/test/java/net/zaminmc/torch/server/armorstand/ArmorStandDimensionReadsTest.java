package net.zaminmc.torch.server.armorstand;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave AS — the armor-stand family's dimension reads (the reference's
 * Entity.world over the LAST standing overworld-0 family, the TF-9
 * shape): the placement stamps the body's dimension from the PLACER's
 * world (the reference's ArmorStandItem.useOn consults the caller's own
 * world parameter, lines 27-43, and births the stand in it, line 51),
 * every world-touching site reads it back — the walk's floor/wall
 * consults, the break's particles + drops, the double-hit clock's own
 * world.getTime() — and the ZAD v4 file carries the row (the ZID v8
 * convention: the v1-v3 files default the overworld 0, the migration
 * law).
 */
class ArmorStandDimensionReadsTest {

    /** The recording surface: every consult's dimension row, by arm. */
    private static final class RecordingSurface implements ArmorStandSystem.Surface {
        final List<Integer> replaceableDimensions = new ArrayList<>();
        final List<Integer> airDimensions = new ArrayList<>();
        final List<Integer> solidDimensions = new ArrayList<>();
        final List<Integer> slipperinessDimensions = new ArrayList<>();
        final List<Integer> dropDimensions = new ArrayList<>();
        final List<Integer> occupiesDimensions = new ArrayList<>();
        final List<Integer> nowDimensions = new ArrayList<>();
        final List<Integer> burstDimensions = new ArrayList<>();
        final List<Integer> cartDimensions = new ArrayList<>();

        @Override
        public boolean replaceableAt(BlockPosition pos, int dimension) {
            replaceableDimensions.add(dimension);
            return false;
        }

        @Override
        public boolean airAt(BlockPosition pos, int dimension) {
            airDimensions.add(dimension);
            return true;
        }

        @Override
        public boolean solidAt(BlockPosition pos, int dimension) {
            solidDimensions.add(dimension);
            return false;
        }

        @Override
        public float blockSlipperinessAt(BlockPosition pos, int dimension) {
            slipperinessDimensions.add(dimension);
            return 0.6f;
        }

        @Override
        public void dropItem(Position at, int dimension, ItemStack stack) {
            dropDimensions.add(dimension);
        }

        @Override
        public boolean entityOccupies(double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ, int dimension) {
            occupiesDimensions.add(dimension);
            return false;
        }

        @Override
        public long now(int dimension) {
            nowDimensions.add(dimension);
            return 100L;
        }

        @Override
        public void standBreakingBurst(Position at, int dimension) {
            burstDimensions.add(dimension);
        }

        @Override
        public List<ArmorStandSystem.CartView> rideableCarts(int dimension) {
            cartDimensions.add(dimension);
            return List.of();
        }
    }

    @Test
    void theNetherPlacementStampsAndGatesInItsOwnWorld() {
        RecordingSurface surface = new RecordingSurface();
        ArmorStandSystem system = new ArmorStandSystem(surface, 30_000);
        // A NETHER player places (the reference's useOn consults the
        // caller's world): every placement gate carries dimension -1.
        assertTrue(system.place(new BlockPosition(10, 40, 10), -1, 1, 0.0f,
                new Random(1)), "the stand landed");
        ArmorStand stand = system.allStands().get(0);
        assertEquals(-1, stand.dimension(), "the body's own world row");
        assertTrue(surface.replaceableDimensions.stream().allMatch(d -> d == -1),
                "the replaceable consult reads the placer's world alone");
        assertTrue(surface.airDimensions.stream().allMatch(d -> d == -1),
                "the air consult reads the placer's world alone");
        assertTrue(surface.occupiesDimensions.stream().allMatch(d -> d == -1),
                "the box-occupancy consult reads the placer's world alone");
    }

    @Test
    void eachDimensionAnswersItsOwn() {
        RecordingSurface surface = new RecordingSurface();
        ArmorStandSystem system = new ArmorStandSystem(surface, 30_500);
        // The End player (1), the nether player (-1), the overworld (0) —
        // each stand carries its own placer's world, never a shared row.
        system.place(new BlockPosition(0, 64, 0), 1, 1, 0.0f, new Random(2));
        system.place(new BlockPosition(0, 64, 0), -1, 1, 0.0f, new Random(3));
        system.place(new BlockPosition(0, 64, 0), 0, 1, 0.0f, new Random(4));
        List<ArmorStand> stands = system.allStands();
        assertEquals(3, stands.size());
        assertEquals(1, stands.get(0).dimension());
        assertEquals(-1, stands.get(1).dimension());
        assertEquals(0, stands.get(2).dimension());
    }

    @Test
    void theBreaksDropsBurstAndClockReadTheStandsOwnWorld() {
        RecordingSurface surface = new RecordingSurface();
        ArmorStandSystem system = new ArmorStandSystem(surface, 31_000);
        assertTrue(system.place(new BlockPosition(10, 40, 10), -1, 1, 0.0f,
                new Random(5)));
        ArmorStand stand = system.allStands().get(0);
        // The first hit stamps the clock (the wobble) — the world.getTime()
        // consult carries the stand's own dimension.
        assertTrue(system.attack(stand, false), "the first hit consumed");
        assertEquals(List.of(-1), surface.nowDimensions,
                "the double-hit clock reads the stand's own world "
                        + "(the reference's this.world.getTime())");
        // The second hit inside the five-tick window breaks the stand: the
        // burst and both drop arms (the stand item + the equipment)
        // carry the same row (onBrokenByPlayer lines 475-490).
        surface.nowDimensions.clear();
        surface.solidDimensions.clear();
        surface.slipperinessDimensions.clear();
        surface.cartDimensions.clear();
        assertTrue(system.attack(stand, false), "the second hit broke the stand");
        assertTrue(system.allStands().isEmpty(), "the broken stand left the registry");
        assertEquals(List.of(-1), surface.burstDimensions,
                "the breaking particles fire in the stand's own world");
        assertEquals(List.of(-1), surface.dropDimensions,
                "the stand item's drop spawns in the stand's own world "
                        + "(the reference's Block.dropItem(this.world, ...))");
    }

    @Test
    void theWalkAndCartConsultsReadTheStandsOwnWorld() {
        RecordingSurface surface = new RecordingSurface();
        ArmorStandSystem system = new ArmorStandSystem(surface, 31_500);
        // A nether stand and an overworld stand: the tick's gravity walk
        // consults each body's own world (the floor consult + the friction
        // consult + the cart push consult).
        system.place(new BlockPosition(10, 40, 10), -1, 1, 0.0f, new Random(6));
        system.place(new BlockPosition(10, 40, 12), 0, 1, 0.0f, new Random(7));
        system.tick();
        assertTrue(surface.solidDimensions.contains(-1),
                "the nether stand's floor consult reads its own world");
        assertTrue(surface.solidDimensions.contains(0),
                "the overworld stand's floor consult reads its own world");
        assertTrue(surface.cartDimensions.contains(-1),
                "the nether stand's cart consult reads its own world");
        assertTrue(surface.cartDimensions.contains(0),
                "the overworld stand's cart consult reads its own world");
    }

    @Test
    void theZadRoundTripCarriesTheDimensionAndTheV3FileDefaultsTheOverworld(
            @TempDir Path dir) throws Exception {
        // A nether stand + an End stand: the v4 file carries both rows and
        // the restore stamps them back (the ZID v8 convention).
        ArmorStandDataStore store = new ArmorStandDataStore(
                dir.resolve("armor_stands.zad"));
        List<ArmorStandSystem.StandRecord> saved = List.of(
                new ArmorStandSystem.StandRecord(1.5, 64.0, 2.5, 0.0f,
                        emptyEquipment(), (byte) 0,
                        ArmorStand.DEFAULT_HEAD, ArmorStand.DEFAULT_BODY,
                        ArmorStand.DEFAULT_LEFT_ARM, ArmorStand.DEFAULT_RIGHT_ARM,
                        ArmorStand.DEFAULT_LEFT_LEG, ArmorStand.DEFAULT_RIGHT_LEG,
                        0, false, -1),
                new ArmorStandSystem.StandRecord(3.5, 64.0, 4.5, 0.0f,
                        emptyEquipment(), (byte) 0,
                        ArmorStand.DEFAULT_HEAD, ArmorStand.DEFAULT_BODY,
                        ArmorStand.DEFAULT_LEFT_ARM, ArmorStand.DEFAULT_RIGHT_ARM,
                        ArmorStand.DEFAULT_LEFT_LEG, ArmorStand.DEFAULT_RIGHT_LEG,
                        0, false, 1));
        store.save(saved);
        List<ArmorStandSystem.StandRecord> loaded = store.load();
        assertEquals(2, loaded.size());
        assertEquals(-1, loaded.get(0).dimension(), "the nether row survives the file");
        assertEquals(1, loaded.get(1).dimension(), "the End row survives the file");

        // The restore walk stamps the rows back.
        RecordingSurface surface = new RecordingSurface();
        ArmorStandSystem system = new ArmorStandSystem(surface, 32_000);
        system.restore(loaded);
        assertEquals(-1, system.allStands().get(0).dimension());
        assertEquals(1, system.allStands().get(1).dimension());

        // A hand-built v3 file (the pre-wave format: every row without the
        // dimension byte) loads every stand at the overworld 0 — the
        // migration law (every historical stand is an overworld stand).
        byte[] v3 = buildV3File();
        Path v3File = dir.resolve("armor_stands_v3.zad");
        java.nio.file.Files.write(v3File, v3);
        List<ArmorStandSystem.StandRecord> migrated =
                new ArmorStandDataStore(v3File).load();
        assertEquals(1, migrated.size());
        assertEquals(0, migrated.get(0).dimension(),
                "the v3 file's stand lands in the overworld (the migration law)");
        assertFalse(migrated.get(0).invisible() && migrated.get(0).invisible());
    }

    /** The five empty equipment slots every real record carries (the
     *  store's own layout reads them unconditionally). */
    private static List<ItemStack> emptyEquipment() {
        return List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY);
    }

    /**
     * One v3-format stand row, hand-built: the magic + version 3, the
     * count 1, then the pre-wave layout (x/y/z, yaw, status, five empty
     * equipment slots, the six pose triples, the DisabledSlots int, the
     * Invisible boolean — NO dimension byte).
     */
    private static byte[] buildV3File() {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(sink)) {
            out.writeByte('Z');
            out.writeByte('A');
            out.writeByte('D');
            out.writeByte(3);
            writeVarInt(out, 1);
            out.writeDouble(1.5);
            out.writeDouble(64.0);
            out.writeDouble(2.5);
            out.writeFloat(0.0f);
            out.writeByte(0);
            for (int slot = 0; slot < 5; slot++) {
                out.writeByte(0); // an empty equipment slot
            }
            for (int part = 0; part < 6; part++) {
                out.writeFloat(0.0f);
                out.writeFloat(0.0f);
                out.writeFloat(0.0f);
            }
            out.writeInt(0); // DisabledSlots
            out.writeBoolean(false); // Invisible
            // No dimension byte — the v3 layout's own end.
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return sink.toByteArray();
    }

    private static void writeVarInt(java.io.DataOutputStream out, int value)
            throws java.io.IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
