package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fluids' flow — the reference's liquid family 1:1
 * (reference/1.8.8 FlowingLiquidBlock.java + LiquidSourceBlock.java +
 * LiquidBlock.java, the Wave VI migration): the whole flow tick over the
 * direct-driven unwired window (the same delays and the same dispatch the
 * seam carries in the engine — the GravityFalls pattern).
 *
 * <p>The demand-named pieces each carry their test: the LEVEL 9-15
 * falling-with-level band, convertToSource's infinite-water (the two-hop
 * birth), the nether's j=1 and the lava 10-tick cadence (the dimension
 * profile), getSpread/getDistanceToGap's direction preference, the
 * source's neighborChanged→convertToFlowing (and its inverse — a placed
 * source stays inert until a neighbor changes), isLiquidBlocking (the
 * door's open-but-blocking quirk), spreadTo's wash-out drops, the contact
 * bands (obsidian/cobblestone/stone), the seven-block reach with the
 * still settle, and the drying cascade.</p>
 */
class FluidFlowTest {

    /** The debug harness's instrumentation gate. */
    static final boolean DEBUG = Boolean.getBoolean("fluid.debug");

    /** The stub world: a stone floor at y<=5, air above, a flat 17x17 window. */
    private final class SceneWorld implements FluidSystem.World {
        final Map<Long, BlockType> blocks = new HashMap<>();
        final boolean nether;
        long ticks;

        SceneWorld(boolean nether) {
            this.nether = nether;
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    for (int y = 0; y <= 5; y++) {
                        blocks.put(key(x, y, z), BuiltinBlocks.STONE);
                    }
                }
            }
        }

        @Override
        public BlockType getBlock(BlockPosition position) {
            return blocks.getOrDefault(key(position.x(), position.y(), position.z()),
                    BuiltinBlocks.AIR);
        }

        @Override
        public void setBlock(BlockPosition position, BlockType type) {
            blocks.put(key(position.x(), position.y(), position.z()), type);
            // The engine's bus shape: every committed write fires the
            // change listener (EngineWorld.setBlock's own fan) — the
            // fluid's own spread writes schedule their cells through it.
            if (fluids != null) {
                fluids.onBlockChanged(null, position, type);
            }
        }

        @Override
        public BlockType airType() {
            return BuiltinBlocks.AIR;
        }

        @Override
        public long totalTicks() {
            return ticks;
        }

        @Override
        public String name() {
            return "fluid-scene";
        }

        @Override
        public boolean yeetsWater() {
            return nether; // the nether's doesWaterVaporize
        }

        @Override
        public boolean hasNoSky() {
            return nether; // the nether's own no-sky profile
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private SceneWorld scene;
    private FluidSystem fluids;
    private final List<String> washes = new ArrayList<>();
    private final List<String> sounds = new ArrayList<>();

    private void scene() {
        scene(false);
    }

    private void scene(boolean nether) {
        scene = new SceneWorld(nether);
        washes.clear();
        sounds.clear();
        fluids = new FluidSystem(scene, new FluidSystem.Sink() {
            @Override
            public void wash(net.zaminmc.torch.util.Position at, BlockType washed) {
                washes.add(washed.identifier().value());
            }

            @Override
            public void sound(net.zaminmc.torch.util.Position at, String name,
                              float volume, float pitch) {
                sounds.add(name);
            }
        }, new Random(3));
    }

    /** The committed write: the stub's setBlock fires the listener (the engine's bus). */
    private void commit(BlockPosition at, BlockType type) {
        scene.setBlock(at, type);
    }

    private void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            scene.ticks++;
            fluids.tick();
        }
    }

    private String idAt(int x, int y, int z) {
        return scene.getBlock(new BlockPosition(x, y, z)).identifier().value();
    }

    // ------------------------------------------------------------------
    // The pour, the settle, the reach
    // ------------------------------------------------------------------

    @Test
    void thePourSettlesIntoTheSourceAndPoursDown() {
        scene();
        // The bucket's own pour (BucketItem.place): the FLOWING form at
        // LEVEL 0, five cells above the floor.
        BlockPosition pour = new BlockPosition(0, 10, 0);
        commit(pour, FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(5);
        // The reference's own oscillation: the pour settles into the
        // source on its tick, and the down-pour write's neighbor update
        // converts it right back to the flowing form — an actively
        // pouring source flickers between the two forms every cadence
        // (the 1.8 shape; the client renders them identically).
        String pourForm = idAt(0, 10, 0);
        assertTrue(pourForm.equals("water") || pourForm.equals("flowing_water"),
                "the pour cell rides the water/flowing-water oscillation (was " + pourForm + ")");
        assertEquals("falling_water", idAt(0, 9, 0),
                "the source's first spread is the down-pour at LEVEL 8");
        // The column's own ticks: each falling cell settles into the
        // still-8 form and feeds the cell below.
        advance(30);
        assertEquals("still_water_8", idAt(0, 9, 0),
                "the falling column settles into the still-8 form (the equilibrium)");
        // The landing on the floor spreads FRESH (the landing column's k1=1).
        assertEquals("still_water_8", idAt(0, 6, 0), "the column stands on the floor");
        assertTrue(FluidBlocks.isFluid(Identifier.parse("minecraft:" + idAt(1, 6, 0))),
                "the landed column spreads fresh sideways (k1 = 1)");
    }

    @Test
    void theSevenBlockReachSettlesIntoTheStillForms() {
        scene();
        BlockPosition source = new BlockPosition(0, 6, 0);
        commit(source, FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(80);
        assertEquals("water", idAt(0, 6, 0), "the source");
        assertEquals("still_water_1", idAt(1, 6, 0), "the first ring settles still at level 1");
        assertEquals("still_water_3", idAt(3, 6, 0), "the third ring at level 3");
        assertEquals("still_water_7", idAt(7, 6, 0), "the seventh ring at level 7");
        assertEquals("air", idAt(8, 6, 0),
                "the reach ends at seven (k1 >= 8 returns, the reference's own band)");
    }

    @Test
    void theLavasOverworldDecayIsTwoAndTheNethersIsOne() {
        // The overworld: lava decays j=2 — the ring runs 2, 4, 6 (three
        // cells of reach).
        scene(false);
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.LAVA, FluidBlocks.Form.FLOWING, 0));
        advance(200);
        assertEquals("lava", idAt(0, 6, 0), "the lava source");
        assertEquals("still_lava_2", idAt(1, 6, 0), "the first ring at level 2 (j = 2)");
        assertEquals("still_lava_6", idAt(3, 6, 0), "the third ring at level 6");
        assertEquals("air", idAt(4, 6, 0), "the overworld lava's reach is three cells");
        // The nether: j=1 — the full seven-block reach.
        scene(true);
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.LAVA, FluidBlocks.Form.FLOWING, 0));
        advance(200);
        assertEquals("still_lava_1", idAt(1, 6, 0), "the nether's first ring at level 1 (j = 1)");
        assertEquals("still_lava_7", idAt(7, 6, 0), "the nether's seventh ring — the full reach");
        assertEquals("air", idAt(8, 6, 0), "the reach still ends at seven");
    }

    @Test
    void theCadenceBandIsFiveThirtyAndTen() {
        // Water: the first ring appears after five ticks.
        scene();
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(5);
        assertTrue(FluidBlocks.isFluid(Identifier.parse("minecraft:" + idAt(1, 6, 0))),
                "the water's ring-1 landed on the 5-tick cadence");
        // The overworld lava: nothing before tick 30.
        scene(false);
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.LAVA, FluidBlocks.Form.FLOWING, 0));
        advance(29);
        assertEquals("air", idAt(1, 6, 0), "the overworld lava's 30-tick cadence has not fired");
        advance(1);
        assertTrue(FluidBlocks.isFluid(Identifier.parse("minecraft:" + idAt(1, 6, 0))),
                "the overworld lava's ring-1 landed on the 30-tick cadence");
        // The nether lava: the 10-tick cadence.
        scene(true);
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.LAVA, FluidBlocks.Form.FLOWING, 0));
        advance(10);
        assertTrue(FluidBlocks.isFluid(Identifier.parse("minecraft:" + idAt(1, 6, 0))),
                "the nether's lava moves on the 10-tick cadence");
    }

    // ------------------------------------------------------------------
    // The direction preference (getSpread/getDistanceToGap)
    // ------------------------------------------------------------------

    @Test
    void theSpreadFlowsTowardTheNearestGap() {
        scene();
        // A hole in the floor two cells east: the stream must run east
        // alone — the gap-seeking preference.
        scene.blocks.put(key(3, 5, 0), BuiltinBlocks.AIR);
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(5);
        assertTrue(FluidBlocks.isFluid(Identifier.parse("minecraft:" + idAt(1, 6, 0))),
                "the east cell took the flow");
        assertEquals("air", idAt(-1, 6, 0), "the west cell never took the flow");
        assertEquals("air", idAt(0, 6, 1), "the south cell never took the flow");
        assertEquals("air", idAt(0, 6, -1), "the north cell never took the flow");
    }

    @Test
    void theFallingBandCarriesTheAboveDepth() {
        scene();
        // The ledge: the floor stands only under the column; the ring
        // cell beside the landing pours over its own edge — the falling
        // column carries the above-depth (LEVEL 9-15, the demand-named
        // band).
        for (int x = 1; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                scene.blocks.put(key(x, 5, z), BuiltinBlocks.AIR);
            }
        }
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(12);
        // The landing spread's ring-1 at (1,6,0) pours into the open cell
        // below it at LEVEL 1+8 = 9 — falling_water_1, the above-depth 1.
        assertEquals("falling_water_1", idAt(1, 5, 0),
                "the falling band carries the above-depth (LEVEL 9)");
        advance(10);
        assertEquals("still_water_9", idAt(1, 5, 0),
                "the falling-with-depth column settles into still-9 (the reference's own form)");
    }

    // ------------------------------------------------------------------
    // The infinite water (convertToSource's two-hop birth)
    // ------------------------------------------------------------------

    @Test
    void twoAdjacentSourcesBirthASourceBetweenThem() {
        scene();
        commit(new BlockPosition(-1, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(5);
        commit(new BlockPosition(1, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(60);
        assertEquals("water", idAt(-1, 6, 0), "the west source");
        assertEquals("water", idAt(1, 6, 0), "the east source");
        // The gap cell: two adjacent sources over solid ground — the
        // computed level 0 (flowing-0 for one cadence), then the true
        // source on its own tick (the reference's two-hop birth).
        assertEquals("water", idAt(0, 6, 0),
                "the gap birthed a true source (the infinite-water pool)");
        assertTrue(FluidBlocks.isSource(Identifier.parse("minecraft:" + idAt(0, 6, 0))),
                "the born cell is a true source (LEVEL 0 on the source form)");
    }

    // ------------------------------------------------------------------
    // The still/flowing lifecycle
    // ------------------------------------------------------------------

    @Test
    void aPlacedSourceStaysInertUntilANeighborChanges() {
        scene();
        // The /setblock shape: a directly-placed SOURCE never spreads —
        // LiquidSourceBlock's onAdded carries no schedule; only a
        // neighbor change converts it to the flowing form.
        commit(new BlockPosition(0, 6, 0), FluidBlocks.sourceOf(FluidBlocks.Kind.WATER));
        advance(100);
        assertEquals("air", idAt(1, 6, 0), "the inert source never spread east");
        assertEquals("air", idAt(0, 6, -1), "the inert source never spread north");
        // A stone placed beside it: the wake converts the source to the
        // flowing form and the spread begins.
        commit(new BlockPosition(-1, 6, 0), BuiltinBlocks.STONE);
        assertEquals("flowing_water", idAt(0, 6, 0),
                "the neighbor change converted the source to the flowing form");
        advance(10);
        assertTrue(FluidBlocks.isFluid(Identifier.parse("minecraft:" + idAt(1, 6, 0))),
                "the woken source spread");
    }

    @Test
    void theStillPoolWakesThroughTheFlowingFormOnNeighborChanges() {
        scene();
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(80);
        assertEquals("still_water_1", idAt(1, 6, 0), "the settled ring-1");
        // The wake: a stone beside the settled cell converts it to the
        // flowing form at its own level; its tick re-settles it.
        commit(new BlockPosition(2, 6, 0), BuiltinBlocks.STONE);
        assertEquals("flowing_water_1", idAt(1, 6, 0),
                "the wake converted the settled cell to the flowing form at its level");
        advance(10);
        assertEquals("still_water_1", idAt(1, 6, 0), "the flowing form re-settled (equilibrium)");
    }

    @Test
    void theDryingCascadeDrainsTheStreamWhenTheSourceDeparts() {
        scene();
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(80);
        assertEquals("still_water_3", idAt(3, 6, 0), "the settled stream");
        // The source removed: the wake converts the ring; each cell's walk
        // reads a dry horizon (l < 0) and dies to air, the write waking
        // the next ring out — the cascade.
        commit(new BlockPosition(0, 6, 0), BuiltinBlocks.AIR);
        advance(100);
        assertEquals("air", idAt(1, 6, 0), "the first ring dried");
        assertEquals("air", idAt(3, 6, 0), "the third ring dried");
        assertEquals("air", idAt(7, 6, 0), "the whole stream drained");
    }

    // ------------------------------------------------------------------
    // isLiquidBlocking + the wash-out
    // ------------------------------------------------------------------

    @Test
    void theOpenDoorBlocksTheFlowDespiteBeingWalkThrough() {
        scene();
        // The open lower door: walk-through (WorldSolidity's open-half
        // carve) but liquid-blocking (the reference's DoorBlock
        // instanceof — the state never matters, the BLOCK does).
        commit(new BlockPosition(1, 6, 0),
                Identifier.parse("minecraft:wooden_door_open") == null ? BuiltinBlocks.STONE
                        : lookupDoorOpen());
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(20);
        assertEquals("wooden_door_open", idAt(1, 6, 0), "the door cell never took the flow");
    }

    @Test
    void theLadderAndTheReedsBlockWhileTheTorchWashesOut() {
        scene();
        // The ladder: climbable, never solid — but liquid-blocking
        // (the reference's LADDER row).
        BlockType ladder = BuiltinBlocks.LADDER_EAST;
        commit(new BlockPosition(1, 6, 0), ladder);
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(20);
        assertEquals("ladder_east", idAt(1, 6, 0), "the ladder cell never took the flow");
        // The torch: NOT blocking — the stream washes it out and pops its
        // item (spreadTo's dropItems arm).
        scene();
        commit(new BlockPosition(1, 6, 0), BuiltinBlocks.TORCH);
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0));
        advance(20);
        assertTrue(FluidBlocks.isFluid(Identifier.parse("minecraft:" + idAt(1, 6, 0))),
                "the stream took the torch's cell");
        assertTrue(washes.contains("torch"), "the wash-out popped the torch's item");
    }

    // ------------------------------------------------------------------
    // The contact bands (checkSpreadCollisions + the down-pour's stone)
    // ------------------------------------------------------------------

    @Test
    void theLavaContactBandsAreObsidianCobblestoneAndNothing() {
        scene();
        // A lava source with water placed beside it: the lava converts to
        // OBSIDIAN (LEVEL 0) with the fizz.
        commit(new BlockPosition(0, 6, 0), FluidBlocks.sourceOf(FluidBlocks.Kind.LAVA));
        commit(new BlockPosition(1, 6, 0), FluidBlocks.sourceOf(FluidBlocks.Kind.WATER));
        assertEquals("obsidian", idAt(0, 6, 0), "the lava source hardened into obsidian");
        assertTrue(sounds.contains("random.fizz"), "the conversion fizzed");

        // A shallow stream (LEVEL 2, within the <= 4 band): COBBLESTONE.
        scene();
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.LAVA, FluidBlocks.Form.FLOWING, 2));
        commit(new BlockPosition(1, 6, 0), FluidBlocks.sourceOf(FluidBlocks.Kind.WATER));
        assertEquals("cobblestone", idAt(0, 6, 0),
                "the shallow stream hardened into cobblestone");

        // A deep stream (LEVEL 6, past the band): the lava STAYS (the
        // reference's own refusal).
        scene();
        commit(new BlockPosition(0, 6, 0),
                FluidBlocks.typeOf(FluidBlocks.Kind.LAVA, FluidBlocks.Form.FLOWING, 6));
        commit(new BlockPosition(1, 6, 0), FluidBlocks.sourceOf(FluidBlocks.Kind.WATER));
        assertEquals("flowing_lava_6", idAt(0, 6, 0),
                "the deep stream refuses the conversion (the reference's band)");
    }

    @Test
    void theLavaPouringDownIntoWaterMakesStone() {
        scene();
        // The lava above, the water below: the down-pour's own arm — the
        // water cell becomes STONE (never obsidian/cobblestone — the
        // tick's lines 89-93, a different band than the contact's).
        commit(new BlockPosition(0, 7, 0), FluidBlocks.sourceOf(FluidBlocks.Kind.LAVA));
        commit(new BlockPosition(0, 6, 0), FluidBlocks.sourceOf(FluidBlocks.Kind.WATER));
        advance(40);
        assertEquals("stone", idAt(0, 6, 0),
                "the lava pouring down into water made stone");
        assertTrue(sounds.contains("random.fizz"), "the stone arm fizzed");
    }

    // ------------------------------------------------------------------
    // The lava source's fire-start (the random-tick body — the doFireTick
    // gate reads the wired rule; the default arm answers true)
    // ------------------------------------------------------------------

    @Test
    void theLavaSourceFireStartLightsFlammableNeighbors() {
        scene();
        // The else arm's ground tries (LiquidSourceBlock.tick lines 52-59):
        // the flammable ground BESIDE the lava (the same y band) with air
        // above — the fire lands on top of it. (Netherrack is NOT
        // flammable in 1.8.8 — it burns forever ON it; the planks are the
        // reference's own flammable family.)
        commit(new BlockPosition(1, 6, 0), BuiltinBlocks.OAK_PLANKS);
        BlockPosition lava = new BlockPosition(0, 6, 0);
        commit(lava, FluidBlocks.sourceOf(FluidBlocks.Kind.LAVA));
        // Drive the fire-start directly (the random tick's own dispatch —
        // the rolls are the reseeded random's).
        boolean lit = false;
        for (int attempt = 0; attempt < 300 && !lit; attempt++) {
            fluids.dispatchTick(null, lava, FluidBlocks.sourceOf(FluidBlocks.Kind.LAVA),
                    new Random(attempt));
            lit = "fire".equals(idAt(1, 7, 0));
        }
        assertTrue(lit, "the lava source's fire-start lit the air above the planks");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private BlockType lookupDoorOpen() {
        return new EngineBlockType(Identifier.parse("minecraft:wooden_door_open"),
                "Oak Door", 4);
    }
}
