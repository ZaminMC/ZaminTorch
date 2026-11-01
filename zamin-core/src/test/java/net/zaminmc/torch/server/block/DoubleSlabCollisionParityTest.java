package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-DS1's behavioral seat — the double slab is the reference's FULL cube
 * (SlabBlock lines 39-52: {@code isDouble() -> setShape(0,0,0,1,1,1)} with
 * {@code addCollisions} riding {@code updateShape}; the three concrete
 * double classes each answer {@code isDouble() -> true}).
 *
 * <p>The ten registered double names that end in {@code _slab} — the
 * four 43-family variants, the six 125-family woods — must answer the
 * full-cube row at EVERY consult the table feeds: the rest surface at
 * the cell's CEILING (64 -> 65, never the 64.5 half), the upper half
 * solid to the point test, and the {@code isFullCube} family (the
 * player/mob suffocation walks over {@code isViewBlocking}, the
 * chest-open lock over {@code isSolid}, the vine grow support) reading
 * the cube. The three fall-through names ({@code double_stone_slab2},
 * the two seamless forms) already answered the full cube by accident —
 * the family now whole under one row. The SINGLES beside them keep
 * their own half laws untouched.</p>
 */
class DoubleSlabCollisionParityTest {

    /** The cell every consult-level seat reads (the family convention). */
    private static final double CELL = 64.0;

    private static final net.zaminmc.torch.block.BlockType[] STONE_FAMILY_DOUBLES = {
            BuiltinBlocks.DOUBLE_STONE_SLAB,
            BuiltinBlocks.DOUBLE_SANDSTONE_SLAB,
            BuiltinBlocks.DOUBLE_COBBLESTONE_SLAB,
            BuiltinBlocks.DOUBLE_STONE_BRICK_SLAB,
            BuiltinBlocks.DOUBLE_STONE_SLAB2,
            BuiltinBlocks.DOUBLE_STONE_SLAB_SEAMLESS,
            BuiltinBlocks.DOUBLE_STONE_SLAB2_SEAMLESS};

    private static final net.zaminmc.torch.block.BlockType[] WOODEN_FAMILY_DOUBLES = {
            BuiltinBlocks.DOUBLE_WOODEN_SLAB,
            BuiltinBlocks.DOUBLE_SPRUCE_SLAB,
            BuiltinBlocks.DOUBLE_BIRCH_SLAB,
            BuiltinBlocks.DOUBLE_JUNGLE_SLAB,
            BuiltinBlocks.DOUBLE_ACACIA_SLAB,
            BuiltinBlocks.DOUBLE_DARK_OAK_SLAB};

    private static void assertFullCubeRow(net.zaminmc.torch.block.BlockType type) {
        assertEquals(CELL + 1.0, WorldSolidity.supportY(type, CELL + 0.25), 1e-9,
                type.identifier() + ": the rest surface is the cell's ceiling");
        assertEquals(CELL + 1.0, WorldSolidity.supportY(type, CELL + 0.75), 1e-9,
                type.identifier() + ": the ceiling answers from the upper half too");
        assertTrue(WorldSolidity.isSolidAt(type, CELL + 0.25),
                type.identifier() + ": the lower half is solid");
        assertTrue(WorldSolidity.isSolidAt(type, CELL + 0.75),
                type.identifier() + ": the upper half is solid (the full cube)");
        assertTrue(WorldSolidity.isSolidAt(type, CELL + 0.99),
                type.identifier() + ": the cell answers solid to its ceiling");
        assertTrue(CollisionShapes.isFullCube(type),
                type.identifier() + ": the isFullCube family reads the cube");
    }

    @Test
    void theDoubleStoneFamilyCarriesTheFullCube() {
        for (net.zaminmc.torch.block.BlockType type : STONE_FAMILY_DOUBLES) {
            assertFullCubeRow(type);
        }
    }

    @Test
    void theDoubleWoodenFamilyCarriesTheFullCube() {
        for (net.zaminmc.torch.block.BlockType type : WOODEN_FAMILY_DOUBLES) {
            assertFullCubeRow(type);
        }
    }

    @Test
    void theSinglesKeepTheirHalfLawsBesideTheDoubles() {
        // The single halves' own laws are UNTOUCHED by the double carve:
        // the bottom slab fills the lower half alone (SlabBlock.updateShape
        // lines 46-48), the top slab the upper (lines 44-46).
        assertEquals(CELL + 0.5, WorldSolidity.supportY(BuiltinBlocks.OAK_SLAB, CELL + 0.25), 1e-9,
                "the single bottom slab still supports at the mid-line");
        assertTrue(WorldSolidity.isSolidAt(BuiltinBlocks.OAK_SLAB, CELL + 0.25));
        assertFalse(WorldSolidity.isSolidAt(BuiltinBlocks.OAK_SLAB, CELL + 0.75),
                "the single bottom slab still opens its upper half");
        assertEquals(CELL + 1.0, WorldSolidity.supportY(BuiltinBlocks.STONE_SLAB_TOP, CELL + 0.9), 1e-9,
                "the single top slab still supports at the ceiling");
        assertFalse(WorldSolidity.isSolidAt(BuiltinBlocks.STONE_SLAB_TOP, CELL + 0.25),
                "the single top slab still opens its lower half");
        assertEquals(CELL + 0.5, WorldSolidity.supportY(BuiltinBlocks.STONE_SLAB2, CELL + 0.25), 1e-9,
                "the red sandstone single keeps its half");
        assertTrue(WorldSolidity.isSolidAt(BuiltinBlocks.STONE, CELL + 0.99));
        assertEquals(CELL + 1.0, WorldSolidity.supportY(BuiltinBlocks.STONE, CELL + 0.2), 1e-9);
    }

    /**
     * The body seat: an item entity dropped over a double slab comes to
     * rest on the CEILING (the vanilla EntityItem collides with the full
     * cube) — the world stub routes every consult through the REAL
     * WorldSolidity/BlockType answers, so the landing walks the very row
     * the table carries.
     */
    @Test
    void itemsRestOnTheDoubleSlabCeiling() {
        DoubleSlabWorld world = new DoubleSlabWorld();
        var stack = net.zaminmc.torch.item.ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIRT, 1);
        ItemEntity item = new ItemEntity(7, new Position(0.5, 8.0, 0.5), stack, 0);
        for (int i = 0; i < 300; i++) {
            item.tick(world);
        }
        assertTrue(item.onGround(), "the item settled on the double slab");
        assertEquals(5.0 + ItemEntity.HALF_HEIGHT, item.position().y(), 1e-9,
                "the item's bottom rests on the double's CEILING (vanilla: the full cube; "
                        + "the half-row bug rested it at the 4.5 mid-line)");
    }

    /**
     * The vine grow support (VineBlocks.isGrowableSupport -> isFullCube):
     * the reference's vines grow on the double slab's faces (the normal
     * cube), never on the glass family — the engine row now agrees.
     */
    @Test
    void theVineGrowSupportReadsTheDoublesCube() {
        assertTrue(VineBlocks.isGrowableSupport(BuiltinBlocks.DOUBLE_STONE_SLAB),
                "vines grow on the double slab (the normal cube face)");
        assertTrue(VineBlocks.isGrowableSupport(BuiltinBlocks.DOUBLE_WOODEN_SLAB),
                "vines grow on the double wooden slab");
        assertFalse(VineBlocks.isGrowableSupport(BuiltinBlocks.GLASS),
                "the glass family's exclusion stands");
    }

    /**
     * The single-cell world: a DOUBLE_STONE_SLAB at (0, 4, 0), stone below
     * y=4, air above — every consult delegating to the real WorldSolidity
     * answers on the real registered types (no stubbed shapes).
     */
    private static final class DoubleSlabWorld implements ItemEntity.Ground {

        private static net.zaminmc.torch.block.BlockType typeAt(double x, double y, double z) {
            if (y < 4.0) {
                return BuiltinBlocks.STONE;
            }
            return (int) Math.floor(x) == 0 && (int) Math.floor(y) == 4
                    && (int) Math.floor(z) == 0
                    ? BuiltinBlocks.DOUBLE_STONE_SLAB : BuiltinBlocks.AIR;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(typeAt(x, y, z));
        }

        @Override
        public boolean isSolidAt(double x, double y, double z) {
            return WorldSolidity.isSolidAt(typeAt(x, y, z), x, y, z,
                    CollisionShapes.ISOLATED);
        }

        @Override
        public double supportY(double x, double y, double z) {
            return WorldSolidity.supportY(typeAt(x, y, z), x, y, z,
                    CollisionShapes.ISOLATED);
        }
    }
}
