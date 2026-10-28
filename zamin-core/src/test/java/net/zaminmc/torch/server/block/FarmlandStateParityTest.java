package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The farmland state laws (the verifier's fourteenth pass, the farmland
 * pass — the findings doc's B-section, every law cited to its reference
 * anchor):
 *
 * <p>THE RAIN ARM (FarmlandBlock.tick line 47): {@code !world.isRaining
 * (pos.up())} — rain alone hydrates a field, no water needed; the consult
 * rides the boot-wired rain seam. THE DRY-TO-DIRT REVERSION (lines 50-51):
 * at moisture 0 with no protection the farmland turns to DIRT on the tick
 * — the protection consult is the reference's own class walk
 * ({@code isUnderCrop} lines 73-76: {@code instanceof WheatBlock ||
 * instanceof StemBlock}) and the WheatBlock CLASS covers the carrots and
 * the potatoes (both subclasses), so all three crops and both stems
 * protect their field while bare farmland or a flower reverts. THE SCAN
 * BOX (isNearWater lines 78-86): the farmland's own level and ONE ABOVE
 * alone — water below never hydrates — and the consult is
 * {@code Material.WATER} alone (LAVA never hydrates). THE SOLID-ABOVE
 * ARM (neighborChanged lines 88-94): a material-solid block on the
 * farmland turns it to DIRT immediately; a torch or a flower never
 * does.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FarmlandStateParityTest {

    private EngineWorld world;
    private RandomTickSystem ticks;

    @BeforeAll
    void setUp() {
        FrozenBlockRegistry registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("farmland-parity", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(0, 0));
        ticks = new RandomTickSystem(world, new Random(0xFA3A1L));
    }

    /** The rain arm: a dry field with rain falling on it turns wet. */
    @Test
    void theRainAloneHydratesTheField() {
        BlockPosition soil = farmlandAt(8, 6);
        ticks.setRainRead(position -> true); // rain falling at the cell above
        ticks.randomTick(soil);
        assertEquals(BuiltinBlocks.FARMLAND_WET, world.getBlock(soil),
                "rain alone hydrates (FarmlandBlock.tick line 47 — the"
                        + " pre-audit engine consulted water only)");
        ticks.setRainRead(position -> false);
        ticks.randomTick(soil);
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(soil),
                "the rain gone, the field dries (the binary flip)");
    }

    /**
     * The dry-to-dirt reversion (lines 50-51 with isUnderCrop 73-76): bare
     * farmland reverts; a wheat, a carrot, a potato and both stems protect
     * their field (the WheatBlock class covers its two subclasses — the
     * whole crop family); a flower never protects.
     */
    @Test
    void theDryFarmlandRevertsToDirtWithoutItsCrop() {
        // Bare: reverts.
        BlockPosition bare = farmlandAt(14, 6);
        ticks.randomTick(bare);
        assertEquals(BuiltinBlocks.DIRT, world.getBlock(bare),
                "a bare dry farmland turns to dirt (lines 50-51)");
        world.setBlock(bare, BuiltinBlocks.FARMLAND);

        // Each crop family member protects (the class walk covers the
        // carrots and the potatoes — WheatBlock's own subclasses).
        BlockPosition wheatSoil = farmlandAt(20, 6);
        world.setBlock(wheatSoil.offset(0, 1, 0), BuiltinBlocks.WHEAT_STAGE0);
        ticks.randomTick(wheatSoil);
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(wheatSoil),
                "the wheat protects its field");
        world.setBlock(wheatSoil.offset(0, 1, 0), BuiltinBlocks.CARROTS_STAGE0);
        ticks.randomTick(wheatSoil);
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(wheatSoil),
                "the carrot protects its field (a WheatBlock subclass —"
                        + " the reference's own instanceof walk)");
        world.setBlock(wheatSoil.offset(0, 1, 0), BuiltinBlocks.POTATOES_STAGE0);
        ticks.randomTick(wheatSoil);
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(wheatSoil),
                "the potato protects its field");
        world.setBlock(wheatSoil.offset(0, 1, 0),
                MelonBlocks.stemStage("pumpkin_stem", 0));
        ticks.randomTick(wheatSoil);
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(wheatSoil),
                "the pumpkin stem protects its field (the StemBlock half)");

        // A flower never protects: the reversion pops it through the
        // standard block-change path.
        BlockPosition flowerSoil = farmlandAt(26, 6);
        world.setBlock(flowerSoil.offset(0, 1, 0), BuiltinBlocks.POPPY);
        ticks.randomTick(flowerSoil);
        assertEquals(BuiltinBlocks.DIRT, world.getBlock(flowerSoil),
                "the poppy's field reverts (isUnderCrop's class walk"
                        + " knows flowers no more than the reference)");
    }

    /**
     * The scan box (isNearWater lines 78-86): water at the field's own
     * level or one above hydrates; water one BELOW never does — and the
     * consult is the WATER kind alone, LAVA never hydrating.
     */
    @Test
    void theScanBoxReadsTheFieldLevelAndTheWaterKindAlone() {
        // A wheat above the field for the whole seat: the crop protects
        // the field from the reversion arm so the HYDRATION consult is
        // the seat's isolated subject.
        BlockPosition soil = farmlandAt(32, 6);
        world.setBlock(soil.offset(0, 1, 0), BuiltinBlocks.WHEAT_STAGE0);

        // Water one below the field: never hydrates.
        world.setBlock(soil.offset(1, -1, 0),
                FluidBlocks.sourceOf(FluidBlocks.Kind.WATER));
        ticks.randomTick(soil);
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(soil),
                "water BELOW the field's level never hydrates (the scan"
                        + " box is the field's level and one above — the"
                        + " pre-audit engine scanned one level below too)");
        world.setBlock(soil.offset(1, -1, 0), world.airType());

        // Lava beside the field, at its own level: never hydrates.
        world.setBlock(soil.offset(1, 0, 0),
                FluidBlocks.sourceOf(FluidBlocks.Kind.LAVA));
        ticks.randomTick(soil);
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(soil),
                "LAVA never hydrates a field (Material.WATER alone, line 80"
                        + " — the pre-audit isFluid consult admitted lava)");
        world.setBlock(soil.offset(1, 0, 0), world.airType());

        // Water beside the field, at its own level: hydrates.
        world.setBlock(soil.offset(1, 0, 0),
                FluidBlocks.sourceOf(FluidBlocks.Kind.WATER));
        ticks.randomTick(soil);
        assertEquals(BuiltinBlocks.FARMLAND_WET, world.getBlock(soil),
                "water at the field's level hydrates");
    }

    /**
     * The solid-above arm (neighborChanged lines 88-94): a stone arriving
     * on the farmland turns it to DIRT; a torch never does.
     */
    @Test
    void theSolidAboveTurnsTheFarmlandToDirt() {
        net.zaminmc.torch.server.entity.ItemEntityManager items =
                new net.zaminmc.torch.server.entity.ItemEntityManager(
                        (x, y, z) -> false, new Random(7), 15000);
        net.zaminmc.torch.server.entity.FallingBlockEntityManager falling =
                new net.zaminmc.torch.server.entity.FallingBlockEntityManager(
                        (x, y, z) -> false, world,
                        (position, stack) -> { }, new Random(8), 16000);
        BlockUpdateSystem bus = new BlockUpdateSystem(world, items, falling);

        // The stone on the field: the arm fires through the bus.
        BlockPosition soil = farmlandAt(38, 6);
        world.setBlock(soil.offset(0, 1, 0), BuiltinBlocks.STONE);
        bus.onBlockChanged(world, soil.offset(0, 1, 0), BuiltinBlocks.STONE);
        bus.tick();
        assertEquals(BuiltinBlocks.DIRT, world.getBlock(soil),
                "a solid block on the farmland turns it to dirt"
                        + " (lines 88-94 — the pre-audit bus had no"
                        + " farmland arm)");

        // The torch on the field: never reverts (the model's own member).
        BlockPosition torchSoil = farmlandAt(44, 6);
        world.setBlock(torchSoil.offset(0, 1, 0), BuiltinBlocks.TORCH);
        bus.onBlockChanged(world, torchSoil.offset(0, 1, 0), BuiltinBlocks.TORCH);
        bus.tick();
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(torchSoil),
                "a torch never reverts the field (the non-solid member)");
    }

    /** A dry farmland cell at (x, 6, z) over solid ground, wet-free. */
    private BlockPosition farmlandAt(int x, int z) {
        BlockPosition soil = new BlockPosition(x, 6, z);
        world.setBlock(soil, BuiltinBlocks.FARMLAND);
        return soil;
    }
}
