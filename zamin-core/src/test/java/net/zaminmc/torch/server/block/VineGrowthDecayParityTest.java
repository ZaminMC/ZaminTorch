package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vine family's mechanics whole (Wave VN-B — the F-VINE pass's second
 * slice, reference/1.8.8 block/VineBlock.java):
 *
 * <p>THE PLACEMENT PAIR (canBePlaced lines 131-143 + getPlacementState
 * lines 305-308): a bottom click never places; an UP click reads the
 * CEILING above the placement cell (the reference's own law — a vine
 * never plants on open ground by a top click, only under an overhang
 * two cells up); a horizontal click reads the CLICKED block (the wall
 * the vine hangs on). The state: the OPPOSITE-face bit on a horizontal
 * click, the FACELESS default (metadata 0, {@code vine_none}) on a
 * vertical click — the transient form the decay pops at the first
 * neighbour change.</p>
 *
 * <p>THE GROWTH (tick lines 180-304): the 1-in-4 roll, the 9x9x3 census
 * (five family cells — the anchor counted — refuse the UP and SIDE
 * arms; the DOWN arm ignores it, the reference's own asymmetry), the
 * three arms over Direction.pick's uniform six, the coin loops in the
 * reference's own Plane.HORIZONTAL order (N, E, S, W), the DOWN extend
 * and merge, and the SIDE arm's five sub-arms — the clockwise and
 * counterclockwise wall births, the two corner wraps, the faceless top
 * cap, and the face gain against an OPAQUE cube alone (the translucent
 * family never takes the bit — {@code material.isSolidBlocking() &&
 * isCube()}, the glass exclusion).</p>
 *
 * <p>THE DECAY (neighborChanged lines 172-178 over hasConnections lines
 * 145-168): each dead face bit clears unless the vine ABOVE carries the
 * same bit (the hanging chain), the connectionless form pops with NO
 * drop, and the changed cell itself NEVER receives its own
 * neighbourChanged (the placed faceless vine lingers until a neighbour
 * changes — the rail's own dispatch law, World.updateNeighbors lines
 * 342-349).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VineGrowthDecayParityTest {

    private static FrozenBlockRegistry registry;

    private EngineWorld world;
    private BlockUpdateSystem updates;
    private net.zaminmc.torch.server.entity.ItemEntityManager items;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    // ------------------------------------------------------------------
    // The scene harness: a fresh flat world + the update bus wired as the
    // engine wires it (the world's own listener — every commit wakes the
    // neighbour walk, the seats drive the run edge with tick())
    // ------------------------------------------------------------------

    private void scene() {
        world = new EngineWorld("vine-scene", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        world.getOrGenerate(new ChunkPosition(0, 0));
        net.zaminmc.torch.server.entity.FallingBlockEntityManager falling =
                new net.zaminmc.torch.server.entity.FallingBlockEntityManager(
                        (x, y, z) -> false, world, (position, stack) -> { }, new Random(2), 2000);
        items = new net.zaminmc.torch.server.entity.ItemEntityManager(
                (x, y, z) -> false, new Random(1), 1000);
        updates = new BlockUpdateSystem(world, items, falling);
        world.addChangeListener(updates);
    }

    private static BlockPosition at(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }

    private String nameAt(int x, int y, int z) {
        return world.getBlock(at(x, y, z)).identifier().value();
    }

    private void set(int x, int y, int z, BlockType type) {
        world.setBlock(at(x, y, z), type);
    }

    /**
     * The scripted RNG (the ForcedRandom house shape): the vine's whole
     * walk consults {@code nextInt(4)} (the roll), {@code nextInt(6)}
     * (Direction.pick — DOWN, UP, NORTH, SOUTH, WEST, EAST, the
     * reference's own values order and the engine's face ids), and
     * {@code nextBoolean()} (the coin loops, Plane.HORIZONTAL's N, E,
     * S, W order).
     */
    private static final class ScriptedRandom extends Random {
        private final int[] ints;
        private final boolean[] bools;
        private int intAt;
        private int boolAt;

        ScriptedRandom(int[] ints, boolean[] bools) {
            this.ints = ints;
            this.bools = bools;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            return ints[intAt++ % ints.length] % bound;
        }

        @Override
        public boolean nextBoolean() {
            return bools[boolAt++ % bools.length];
        }
    }

    // ------------------------------------------------- the placement pair

    @Test
    void thePlacementFaceGateAndStateLaw() {
        scene();
        // A stone wall at (5,10,0); the target cell west of it.
        set(5, 10, 0, BuiltinBlocks.STONE);
        // The horizontal click (face 4 = west): the support read is the
        // CLICKED block — the wall itself (VineBlock.canBePlaced's
        // pos.offset(face.getOpposite())).
        assertTrue(VineBlocks.canPlaceAt(world, at(4, 10, 0), 4),
                "the west click reads the wall");
        // The same cell approached from the north (face 2): the support
        // read falls on air — the wall is not that way.
        assertFalse(VineBlocks.canPlaceAt(world, at(4, 10, 0), 2),
                "the north click reads the north cell, not the wall");
        // The UP click (face 1): the CEILING consult — the cell ABOVE the
        // placement, never the floor below (the reference's own law).
        assertFalse(VineBlocks.canPlaceAt(world, at(5, 11, 0), 1),
                "no vine on open ground by a top click");
        set(5, 12, 0, BuiltinBlocks.STONE); // the overhang two cells up
        assertTrue(VineBlocks.canPlaceAt(world, at(5, 11, 0), 1),
                "the 1-gap ceiling passes");
        // The DOWN click never places (the reference's default arm).
        assertFalse(VineBlocks.canPlaceAt(world, at(4, 10, 0), 0));
        // A non-growable support: a vine wall never carries another vine
        // (canGrowOn: isCube && blocksMovement — the vine is neither).
        set(5, 10, 0, BuiltinBlocks.VINE);
        assertFalse(VineBlocks.canPlaceAt(world, at(4, 10, 0), 4),
                "a vine is never the support");
        // THE STATE LAW (getPlacementState lines 305-308): the horizontal
        // click places the OPPOSITE-face bit — the vine hung on the
        // clicked wall points back at it.
        assertEquals(VineBlocks.stateFor(VineBlocks.BIT_SOUTH), VineBlocks.placementStateFor(2),
                "the north click hangs facing south");
        assertEquals(VineBlocks.stateFor(VineBlocks.BIT_NORTH), VineBlocks.placementStateFor(3));
        assertEquals(VineBlocks.stateFor(VineBlocks.BIT_EAST), VineBlocks.placementStateFor(4));
        assertEquals(VineBlocks.stateFor(VineBlocks.BIT_WEST), VineBlocks.placementStateFor(5));
        // The vertical click: the FACELESS default (metadata 0).
        assertEquals(VineBlocks.stateFor(0), VineBlocks.placementStateFor(1));
        assertEquals("vine_none", VineBlocks.placementStateFor(1).identifier().value());
        assertEquals("vine_none", VineBlocks.placementStateFor(0).identifier().value());
    }

    // ------------------------------------------------- the growth's UP arm

    @Test
    void theUpArmBirthRollsItsFacesAgainstTheWalls() {
        scene();
        // The anchor carries SOUTH|NORTH (bits 5); the above cell is air,
        // with the north and south walls beside it.
        set(4, 10, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH));
        set(4, 11, -1, BuiltinBlocks.STONE); // the north wall of the birth cell
        set(4, 11, 1, BuiltinBlocks.STONE);  // the south wall
        // The coins N,E,S,W = false,true,false,true: N and S consult their
        // walls (both stone — KEPT), E and W clear on the coin alone.
        VineBlocks.growthTick(world, at(4, 10, 0), world.getBlock(at(4, 10, 0)),
                new ScriptedRandom(new int[] {0, 1}, new boolean[] {false, true, false, true}));
        assertEquals("vine_south_north", nameAt(4, 11, 0),
                "the birth carries the anchor's walled faces");
        // The thinned roll: the S coin clears its bit, the N wall keeps it.
        set(4, 11, 0, world.airType());
        VineBlocks.growthTick(world, at(4, 10, 0), world.getBlock(at(4, 10, 0)),
                new ScriptedRandom(new int[] {0, 1}, new boolean[] {false, true, true, false}));
        assertEquals("vine_north", nameAt(4, 11, 0), "the coin thins the south bit away");
        // Every coin true: no face survives — the birth never happens (the
        // reference's any-bit-remains gate, line 219).
        set(4, 11, 0, world.airType());
        VineBlocks.growthTick(world, at(4, 10, 0), world.getBlock(at(4, 10, 0)),
                new ScriptedRandom(new int[] {0, 1}, new boolean[] {true, true, true, true}));
        assertTrue(world.getBlock(at(4, 11, 0)).equals(world.airType()),
                "a faceless birth never places");
        // The 25% roll's refusal: the nextInt(4) miss stops the whole walk.
        VineBlocks.growthTick(world, at(4, 10, 0), world.getBlock(at(4, 10, 0)),
                new ScriptedRandom(new int[] {1, 1}, new boolean[] {false, false, false, false}));
        assertTrue(world.getBlock(at(4, 11, 0)).equals(world.airType()),
                "the missed roll grows nothing");
        // The not-air gate: the above cell occupied — the UP arm skips
        // (and the fall-through DOWN arm finds stone below, no write).
        set(4, 11, 0, BuiltinBlocks.STONE);
        set(4, 9, 0, BuiltinBlocks.STONE);
        VineBlocks.growthTick(world, at(4, 10, 0), world.getBlock(at(4, 10, 0)),
                new ScriptedRandom(new int[] {0, 1}, new boolean[] {false, false, false, false}));
        assertEquals("stone", nameAt(4, 11, 0), "the occupied above cell takes no birth");
    }

    @Test
    void theCensusRefusesTheUpArmAtFiveFamilyCells() {
        scene();
        set(4, 10, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH));
        set(4, 11, -1, BuiltinBlocks.STONE);
        set(4, 11, 1, BuiltinBlocks.STONE);
        // Four more vines in the 9x9x3 box + the anchor = five: dense
        // (the anchor itself counts — k=l=i1=0 is in the box).
        set(2, 10, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH));
        set(6, 10, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH));
        set(4, 10, 2, VineBlocks.stateFor(VineBlocks.BIT_SOUTH));
        set(4, 10, -2, VineBlocks.stateFor(VineBlocks.BIT_SOUTH));
        VineBlocks.growthTick(world, at(4, 10, 0), world.getBlock(at(4, 10, 0)),
                new ScriptedRandom(new int[] {0, 1}, new boolean[] {false, true, false, true}));
        assertTrue(world.getBlock(at(4, 11, 0)).equals(world.airType()),
                "the dense band refuses the birth");
        // One vine fewer: the census passes and the birth lands.
        set(4, 10, -2, world.airType());
        VineBlocks.growthTick(world, at(4, 10, 0), world.getBlock(at(4, 10, 0)),
                new ScriptedRandom(new int[] {0, 1}, new boolean[] {false, true, false, true}));
        assertEquals("vine_south_north", nameAt(4, 11, 0),
                "four vines + the anchor still grows");
    }

    // ------------------------------------------------- the growth's DOWN arm

    @Test
    void theDownArmExtendsIntoAirWithCoinThinnedFaces() {
        scene();
        // The anchor at (4,11,0) carries SOUTH|NORTH|WEST (bits 7); the
        // cell below is air.
        set(4, 11, 0, VineBlocks.stateFor(
                VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH | VineBlocks.BIT_WEST));
        // ints {0, 0}: the roll passes, the pick is DOWN. The coins
        // N,E,S,W = false,true,false,true: N and S keep, W clears.
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 0}, new boolean[] {false, true, false, true}));
        assertEquals("vine_south_north", nameAt(4, 10, 0),
                "the extend copies the anchor's surviving faces");
        // Every coin true: nothing extends.
        set(4, 10, 0, world.airType());
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 0}, new boolean[] {true, true, true, true}));
        assertTrue(world.getBlock(at(4, 10, 0)).equals(world.airType()),
                "the all-true coins leave no face to carry");
    }

    @Test
    void theDownArmMergesIntoTheVineBelowAndIgnoresTheCensus() {
        scene();
        // The vine below carries EAST (8); the anchor carries
        // SOUTH|NORTH|EAST (13).
        set(4, 10, 0, VineBlocks.stateFor(VineBlocks.BIT_EAST));
        set(4, 11, 0, VineBlocks.stateFor(
                VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH | VineBlocks.BIT_EAST));
        // The coins N,E,S,W = true,false,true,false: N and S gain (the
        // coin AND the anchor's bit), E stays (no coin, already set), W
        // never — the merge is an OR alone.
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 0}, new boolean[] {true, false, true, false}));
        assertEquals("vine_south_north_east", nameAt(4, 10, 0),
                "the below vine gains the anchor's coined faces");
        // THE CENSUS ASYMMETRY: the DOWN arm runs dense — four more vines
        // in the box refuse nothing here (the reference's own law, lines
        // 224-260 carrying no flag consult).
        set(4, 10, 0, VineBlocks.stateFor(VineBlocks.BIT_EAST));
        set(2, 11, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH));
        set(6, 11, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH));
        set(4, 11, 2, VineBlocks.stateFor(VineBlocks.BIT_SOUTH));
        set(4, 11, -2, VineBlocks.stateFor(VineBlocks.BIT_SOUTH));
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 0}, new boolean[] {true, false, true, false}));
        assertEquals("vine_south_north_east", nameAt(4, 10, 0),
                "the DOWN arm grows through the dense band");
    }

    // ------------------------------------------------- the growth's SIDE arm

    @Test
    void theSideArmBirthsWrapsAndCaps() {
        scene();
        // The anchor carries SOUTH|NORTH (no EAST); the pick is EAST (5)
        // — a direction the anchor does NOT carry, the side arm's own
        // gate (line 235's else). The target is (5,11,0); the clockwise
        // of EAST is SOUTH, the counter is NORTH.
        set(4, 11, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH));

        // Sub-arm 1 (lines 277-279): the clockwise wall beside the target
        // births the target with the CLOCKWISE bit.
        set(5, 11, 1, BuiltinBlocks.STONE);
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
        assertEquals("vine_south", nameAt(5, 11, 0),
                "the clockwise wall births the south bit");
        set(5, 11, 0, world.airType());
        set(5, 11, 1, world.airType());

        // Sub-arm 2 (lines 279-280): no clockwise wall — the counter wall
        // births the target with the COUNTER bit.
        set(5, 11, -1, BuiltinBlocks.STONE);
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
        assertEquals("vine_north", nameAt(5, 11, 0),
                "the counterclockwise wall births the north bit");
        set(5, 11, 0, world.airType());
        set(5, 11, -1, world.airType());

        // Sub-arm 3 (lines 281-282): no walls beside the target — the
        // vine WRAPS the anchor's own south wall: the diagonal cell
        // (5,11,1) takes the bit OPPOSITE the growth direction (WEST).
        set(4, 11, 1, BuiltinBlocks.STONE);
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
        assertEquals("vine_west", nameAt(5, 11, 1),
                "the corner wrap lands on the diagonal with the opposite bit");
        set(5, 11, 1, world.airType());
        set(4, 11, 1, world.airType());

        // Sub-arm 4 (lines 283-284): the counterclockwise wrap's twin.
        set(4, 11, -1, BuiltinBlocks.STONE);
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
        assertEquals("vine_west", nameAt(5, 11, -1),
                "the counterclockwise wrap lands the same opposite bit");
        set(5, 11, -1, world.airType());
        set(4, 11, -1, world.airType());

        // Sub-arm 5 (lines 285-287): no wall anywhere — the FACELESS cap
        // under the target's own ceiling (metadata 0, the transient form).
        set(5, 12, 0, BuiltinBlocks.STONE);
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
        assertEquals("vine_none", nameAt(5, 11, 0),
                "the top cap is the faceless form under the ceiling");
    }

    @Test
    void theFaceGainWrapsOnlyTheOpaqueCubes() {
        scene();
        // The anchor S|N; the pick EAST; the target cell (5,11,0)
        // occupied — the FACE-GAIN arm (lines 288-291) reads the
        // occupying cube's material, not just its shape.
        set(4, 11, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH));

        // An OPAQUE cube: the anchor GAINS the east bit.
        set(5, 11, 0, BuiltinBlocks.STONE);
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
        assertEquals("vine_south_north_east", nameAt(4, 11, 0),
                "the opaque cube takes the east bit");

        // THE GLASS EXCLUSION (GlassBlock.isCube lines 33-36 — false over
        // a full collision box): the glass never takes the bit, and the
        // vine never grows on it either.
        set(4, 11, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH));
        set(5, 11, 0, BuiltinBlocks.GLASS);
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
        assertEquals("vine_south_north", nameAt(4, 11, 0),
                "the glass never takes the bit (the isCube override)");

        // THE TRANSLUCENT MATERIALS (Material.isSolidBlocking = !translucent
        // && blocksMovement, lines 100-101): the leaves, the glowstone and
        // the TNT are all GROW surfaces (blocksMovement true, isCube true)
        // but never TAKE the bit.
        for (BlockType translucent : new BlockType[] {
                BuiltinBlocks.OAK_LEAVES, BuiltinBlocks.GLOWSTONE, BuiltinBlocks.TNT}) {
            set(4, 11, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH));
            set(5, 11, 0, translucent);
            VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                    new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
            assertEquals("vine_south_north", nameAt(4, 11, 0),
                    "the translucent material never takes the bit: "
                            + translucent.identifier().value());
        }

        // PACKED ICE is NOT translucent (Material.java line 27 — the near
        // pair of the ice family): the bit lands.
        set(4, 11, 0, VineBlocks.stateFor(VineBlocks.BIT_SOUTH | VineBlocks.BIT_NORTH));
        set(5, 11, 0, BuiltinBlocks.PACKED_ICE);
        VineBlocks.growthTick(world, at(4, 11, 0), world.getBlock(at(4, 11, 0)),
                new ScriptedRandom(new int[] {0, 5}, new boolean[] {false}));
        assertEquals("vine_south_north_east", nameAt(4, 11, 0),
                "the packed ice takes the bit (not translucent)");
    }

    @Test
    void theGrowSurfaceVocabulary() {
        scene();
        // canGrowOn's own vocabulary (lines 146-148): the glass family
        // NEVER a support (the isCube override over the full box); the
        // glowstone, the ice, the TNT and the leaves all ARE (their
        // isCube stays the base true — the jungle canopy's own law).
        assertFalse(VineBlocks.isGrowableSupport(BuiltinBlocks.GLASS),
                "the glass never carries a vine");
        assertTrue(VineBlocks.isGrowableSupport(BuiltinBlocks.STONE));
        assertTrue(VineBlocks.isGrowableSupport(BuiltinBlocks.GLOWSTONE),
                "the glowstone carries (Material.GLASS, no isCube override)");
        assertTrue(VineBlocks.isGrowableSupport(BuiltinBlocks.ICE));
        assertTrue(VineBlocks.isGrowableSupport(BuiltinBlocks.TNT));
        assertTrue(VineBlocks.isGrowableSupport(BuiltinBlocks.OAK_LEAVES),
                "the leaves carry (the canopy's own law)");
        assertFalse(VineBlocks.isGrowableSupport(BuiltinBlocks.VINE),
                "a vine never carries another vine");
        // The placement's face gate consults the same law: no vine on a
        // glass wall, a vine on the glowstone.
        set(5, 10, 0, BuiltinBlocks.GLASS);
        assertFalse(VineBlocks.canPlaceAt(world, at(4, 10, 0), 4),
                "no vine on the glass wall");
        set(5, 10, 0, BuiltinBlocks.GLOWSTONE);
        assertTrue(VineBlocks.canPlaceAt(world, at(4, 10, 0), 4),
                "the glowstone wall carries");
    }

    // ------------------------------------------------- the decay

    @Test
    void theDecayPopsTheDeadBitSilently() {
        scene();
        set(5, 10, 0, BuiltinBlocks.STONE);
        set(4, 10, 0, VineBlocks.stateFor(VineBlocks.BIT_EAST));
        updates.tick();
        updates.tick(); // settled: the vine survives the neighbours' wakes
        assertEquals("vine_east", nameAt(4, 10, 0), "the settled control");
        // The wall breaks: the EAST bit dies, no chain above — the cell
        // pops with NO drop (getDropItem null + getBaseDropCount 0 over
        // dropItems' empty walk, neighborChanged lines 190-195).
        set(5, 10, 0, world.airType());
        updates.tick();
        updates.tick();
        assertEquals("air", nameAt(4, 10, 0), "the dead bit popped the cell");
        assertEquals(0, items.size(), "the decay drops nothing");
    }

    @Test
    void theChainConsultOutlivesTheWallAndCollapsesFromTheTop() {
        scene();
        // The hanging chain: two vines stacked, each carrying EAST, each
        // with its own wall. The LOWER vine's bit consult looks UP when
        // its wall dies — the vine above carrying the same bit HOLDS it
        // (hasConnections lines 152-157).
        set(5, 10, 0, BuiltinBlocks.STONE);
        set(5, 11, 0, BuiltinBlocks.STONE);
        set(4, 10, 0, VineBlocks.stateFor(VineBlocks.BIT_EAST));
        set(4, 11, 0, VineBlocks.stateFor(VineBlocks.BIT_EAST));
        updates.tick();
        updates.tick(); // settled
        assertEquals("vine_east", nameAt(4, 10, 0));
        assertEquals("vine_east", nameAt(4, 11, 0));
        // The LOWER wall breaks alone: the lower vine's EAST consult reads
        // the vine above — the bit HOLDS, nothing pops.
        set(5, 10, 0, world.airType());
        updates.tick();
        updates.tick();
        assertEquals("vine_east", nameAt(4, 10, 0), "the chain kept the bit");
        assertEquals("vine_east", nameAt(4, 11, 0), "the upper vine never woke");
        // The UPPER wall breaks: the upper vine pops — and its own pop
        // wakes the lower vine, whose above-consult now reads AIR — the
        // CHAIN COLLAPSES from the top.
        set(5, 11, 0, world.airType());
        updates.tick();
        updates.tick();
        updates.tick();
        assertEquals("air", nameAt(4, 11, 0), "the upper vine popped");
        assertEquals("air", nameAt(4, 10, 0), "the chain collapsed with it");
    }

    @Test
    void theFacelessFormLingersUntilANeighbourChanges() {
        scene();
        // The ceiling FIRST (drained while the vine cell is still air),
        // the faceless vine LAST: its own commit never wakes itself (the
        // changed cell is never its own neighbour — World.updateNeighbors
        // lines 342-349, the rail family's own exemption), so the placed
        // faceless form LINGERS exactly like vanilla's.
        set(4, 12, 0, BuiltinBlocks.STONE);
        updates.tick(); // the ceiling's wakes drain against air (no-ops)
        set(4, 11, 0, VineBlocks.stateFor(0));
        updates.tick();
        updates.tick();
        assertEquals("vine_none", nameAt(4, 11, 0),
                "the placed faceless vine survives its own commit");
        // A NEIGHBOUR's change wakes the vine: the faceless consult
        // answers zero connections — the cell pops.
        set(5, 11, 0, BuiltinBlocks.STONE);
        updates.tick();
        updates.tick();
        assertEquals("air", nameAt(4, 11, 0),
                "the first neighbour change popped the faceless form");
        assertEquals(0, items.size(), "the lingering pop drops nothing");
    }
}
