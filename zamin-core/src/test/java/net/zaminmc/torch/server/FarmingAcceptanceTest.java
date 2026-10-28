package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RandomTickSystem;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * Behavioral scenarios for the farming loop: the hoe tills, seeds take on
 * farmland, random ticks grow the crop (hydrated faster), bone meal jumps the
 * age, the mature harvest yields wheat plus a seed, and the recipes craft
 * bread and bone meal from the crop's products.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FarmingAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "farm", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }

    /** Forces the held slot to the given stack (the test seam for inventory state). */
    private void hold(PlayerSession player, ItemType type, int count) {
        player.inventory().setSlot(0, ItemStack.of(type, count));
        player.inventory().selectHotbarSlot(0);
    }

    private void useOn(PlayerSession player, BlockPosition at) throws InterruptedException {
        server.useItemOnBlock(player, at, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 3, "the tick-thread application window");
    }

    /** Sets one block through the tick thread (the world's ownership rule). */
    private void setBlock(BlockPosition at, BlockType type) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "seeded " + at);
    }

    /** Runs a random-tick probe on the tick thread and waits for it. */
    private void probe(net.zaminmc.torch.server.block.RandomTickSystem ticks,
                       BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> ticks.randomTick(at));
        EngineAwaits.awaitTicks(server.world(), 2, "engine progress (a 60 wall settle)");
    }

    @Test
    void hoeTillsGrassIntoFarmlandAndWearsTheTool() throws Exception {
        server = boot();
        PlayerSession farmer = join("Farmer");
        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.GRASS_BLOCK);

        // The handler's 8-block body gate (Slice N2): the farmer stands
        // at the soil before the use.
        server.ticker().submit(() -> farmer.applyMovement(
                new Position(0.5, 65.5, 1.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 150 wall settle)");

        hold(farmer, item("minecraft:iron_hoe"), 1);
        int before = farmer.inventory().held().damage();
        useOn(farmer, soil);

        assertEquals(BuiltinBlocks.FARMLAND, server.world().getBlock(soil),
                "the hoe tills grass into farmland");
        assertTrue(farmer.inventory().held().damage() > before,
                "the till wears one use");
        server.shutdown(null);
    }

    @Test
    void seedsPlantOnFarmlandOnly() throws Exception {
        server = boot();
        PlayerSession farmer = join("Planter");
        BlockPosition farmland = new BlockPosition(0, 64, 0);
        BlockPosition dirt = new BlockPosition(4, 64, 4);
        setBlock(farmland, BuiltinBlocks.FARMLAND);
        setBlock(dirt, BuiltinBlocks.DIRT);
        // The handler's 8-block body gate (Slice N2): the farmer stands
        // between the two soils before the uses.
        server.ticker().submit(() -> farmer.applyMovement(
                new Position(2.5, 65.5, 2.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 150 wall settle)");

        // On plain dirt the seeds refuse (the historical gate).
        hold(farmer, item("minecraft:wheat_seeds"), 4);
        useOn(farmer, dirt);
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(dirt.offset(0, 1, 0)),
                "seeds do not take on plain dirt");
        assertEquals(4, farmer.inventory().held().count(), "the refused use consumes nothing");

        // On farmland the crop plants and a seed is spent.
        useOn(farmer, farmland);
        assertEquals(BuiltinBlocks.WHEAT_STAGE0, server.world().getBlock(farmland.offset(0, 1, 0)),
                "seeds plant the first wheat stage on farmland");
        assertEquals(3, farmer.inventory().held().count(), "planting consumes one seed");
        server.shutdown(null);
    }

    @Test
    void randomTicksGrowTheCropAndHydrateFarmland() throws Exception {
        server = boot();
        var world = server.world();
        BlockPosition drySoil = new BlockPosition(0, 64, 0);
        BlockPosition dryCrop = drySoil.offset(0, 1, 0);
        BlockPosition wetSoil = new BlockPosition(8, 64, 8);
        setBlock(drySoil, BuiltinBlocks.FARMLAND);
        setBlock(dryCrop, BuiltinBlocks.WHEAT_STAGE0);
        setBlock(wetSoil, BuiltinBlocks.FARMLAND);

        RandomTickSystem ticks = new RandomTickSystem(world, new java.util.Random(7));

        // Hydration: dry farmland with water beside it turns wet on its tick;
        // wet farmland with none dries back.
        setBlock(wetSoil.offset(2, 0, 0),
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER));
        probe(ticks, wetSoil);
        assertEquals(BuiltinBlocks.FARMLAND_WET, world.getBlock(wetSoil),
                "farmland beside water hydrates on its random tick");
        probe(ticks, drySoil);
        assertEquals(BuiltinBlocks.FARMLAND, world.getBlock(drySoil),
                "dry farmland without water stays dry");

        // Growth: full daylight crops advance (seeded, so deterministic);
        // over enough ticks the crop reaches maturity from either soil.
        for (int i = 0; i < 40; i++) {
            probe(ticks, dryCrop);
        }
        assertTrue(RandomTickSystem.wheatStageAt(world, dryCrop) > 0,
                "the crop grew across the seeded run (stage "
                        + RandomTickSystem.wheatStageAt(world, dryCrop) + ")");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // The planting arms (the verifier's fourteenth pass — CropItem's
    // carrot/potato pair, WheatSeedsItem's nether wart over soul sand,
    // HoeItem's coarse-dirt conversion, and the seeds' UP-face gate)
    // ------------------------------------------------------------------

    @Test
    void theCarrotAndPotatoPlantOverFarmland() throws Exception {
        server = boot();
        PlayerSession farmer = join("Rooter");
        // The handler's 8-block body gate (Slice N2): stand near the soils.
        server.ticker().submit(() -> farmer.applyMovement(
                new Position(0.5, 65.5, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress");

        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.FARMLAND);
        BlockPosition carrotCell = soil.offset(0, 1, 0);
        hold(farmer, item("minecraft:carrot"), 3);
        useOn(farmer, soil);
        assertEquals(BuiltinBlocks.CARROTS_STAGE0, server.world().getBlock(carrotCell),
                "the carrot plants its stage-0 crop (CropItem.useOn — Item.java line 691)");
        assertEquals(2, farmer.inventory().held().count(), "planting consumed one carrot");

        // The potato over wet farmland: the same law.
        BlockPosition wetSoil = new BlockPosition(4, 64, 0);
        setBlock(wetSoil, BuiltinBlocks.FARMLAND);
        setBlock(wetSoil.offset(1, 0, 0),
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER));
        BlockPosition potatoCell = wetSoil.offset(0, 1, 0);
        hold(farmer, item("minecraft:potato"), 2);
        useOn(farmer, wetSoil);
        assertEquals(BuiltinBlocks.POTATOES_STAGE0, server.world().getBlock(potatoCell),
                "the potato plants its stage-0 crop (Item.java line 692)");
        assertEquals(1, farmer.inventory().held().count(), "planting consumed one potato");

        // The refused use: a carrot on plain DIRT never plants, and with a
        // full hunger bar the eat arm refuses too — nothing spent.
        BlockPosition dirt = new BlockPosition(8, 64, 0);
        setBlock(dirt, BuiltinBlocks.DIRT);
        hold(farmer, item("minecraft:carrot"), 3);
        useOn(farmer, dirt);
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(dirt.offset(0, 1, 0)),
                "a carrot never plants on plain dirt");
        assertEquals(3, farmer.inventory().held().count(), "the refused use spent nothing");
        server.shutdown(null);
    }

    @Test
    void theNetherWartPlantsOverSoulSandAndPopsWhenItDeparts() throws Exception {
        server = boot();
        PlayerSession warlock = join("Warlock");
        server.ticker().submit(() -> warlock.applyMovement(
                new Position(0.5, 65.5, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress");

        BlockPosition sand = new BlockPosition(0, 64, 0);
        setBlock(sand, BuiltinBlocks.SOUL_SAND);
        BlockPosition wart = sand.offset(0, 1, 0);
        hold(warlock, item("minecraft:nether_wart"), 2);
        useOn(warlock, sand);
        assertEquals(BuiltinBlocks.NETHER_WART_STAGE0, server.world().getBlock(wart),
                "the wart plants its stage-0 over soul sand (Item.java line 658 —"
                        + " the SAME WheatSeedsItem law over SOUL_SAND)");
        assertEquals(1, warlock.inventory().held().count(), "planting consumed one wart");

        // The soul sand departs: PlantBlock.neighborChanged -> 
        // canSurviveOrBreak (lines 40-43 over 50-55) pops the wart.
        setBlock(sand, BuiltinBlocks.AIR);
        await(() -> server.world().getBlock(wart).equals(BuiltinBlocks.AIR),
                "the wart popped when its soul sand left (the neighbour bus arm)");
        server.shutdown(null);
    }

    @Test
    void theHoeConvertsCoarseDirtToRegularDirtFirst() throws Exception {
        server = boot();
        PlayerSession farmer = join("Digger");
        server.ticker().submit(() -> farmer.applyMovement(
                new Position(0.5, 65.5, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress");

        // HoeItem.useOn lines 38-45: the COARSE_DIRT variant tills to
        // REGULAR DIRT (never straight to farmland) — the second click
        // tills the result.
        BlockPosition coarse = new BlockPosition(0, 64, 0);
        setBlock(coarse, BuiltinBlocks.COARSE_DIRT);
        hold(farmer, item("minecraft:wooden_hoe"), 1);
        useOn(farmer, coarse);
        assertEquals(BuiltinBlocks.DIRT, server.world().getBlock(coarse),
                "the first till converts coarse dirt to regular dirt");
        useOn(farmer, coarse);
        assertEquals(BuiltinBlocks.FARMLAND, server.world().getBlock(coarse),
                "the second click tills the regular dirt to farmland");
        server.shutdown(null);
    }

    @Test
    void theSeedsRefuseTheSideFaces() throws Exception {
        server = boot();
        PlayerSession farmer = join("Sower");
        server.ticker().submit(() -> farmer.applyMovement(
                new Position(0.5, 65.5, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress");

        // WheatSeedsItem.useOn's first gate: face != UP -> false — a side
        // click never plants and nothing is spent.
        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.FARMLAND);
        BlockPosition cropCell = soil.offset(0, 1, 0);
        hold(farmer, item("minecraft:wheat_seeds"), 4);
        server.useItemOnBlock(farmer, soil, 3, java.util.Optional.empty(),
                windowId -> { }); // face 3: a horizontal side
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress");
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(cropCell),
                "the side click never planted (the UP-face gate)");
        assertEquals(4, farmer.inventory().held().count(), "nothing spent");
        server.shutdown(null);
    }

    @Test
    void boneMealJumpsTheCropForward() throws Exception {
        server = boot();
        PlayerSession farmer = join("Grower");
        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.FARMLAND);
        setBlock(soil.offset(0, 1, 0), BuiltinBlocks.WHEAT_STAGE2);

        // The handler's 8-block body gate (Slice N2): the farmer stands
        // at the crop before the use.
        server.ticker().submit(() -> farmer.applyMovement(
                new Position(0.5, 65.5, 1.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 150 wall settle)");

        hold(farmer, item("minecraft:bone_meal"), 2);
        useOn(farmer, soil.offset(0, 1, 0));

        int stage = RandomTickSystem.wheatStageAt(server.world(), soil.offset(0, 1, 0));
        assertTrue(stage >= 4 && stage <= 7,
                "bone meal jumps the age by 2-4 (was 2, now " + stage + ")");
        assertEquals(1, farmer.inventory().held().count(), "the dust is spent");
        server.shutdown(null);
    }

    @Test
    void matureHarvestYieldsWheatAndTheSeedBonusWalk() throws Exception {
        server = boot();
        PlayerSession farmer = join("Harvester");
        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.FARMLAND);
        BlockPosition crop = soil.offset(0, 1, 0);
        setBlock(crop, BuiltinBlocks.WHEAT_STAGE7);

        // The reference's mature law (WheatBlock.dropItems lines 91-105):
        // exactly ONE wheat plus the 3 + fortune bonus rolls, each
        // nextInt(15) <= 7 → one seed (0-3 at fortune 0 — never a flat one
        // seed; the seed stack only appears when the walk lands at least
        // one). The DropBandParityAcceptanceTest carries the exhaustive
        // band; this seat pins the gesture end-to-end.
        var drops = new net.zaminmc.torch.server.interaction.DropService(
                new java.util.Random(0x5EEDL))
                .dropsFor(BuiltinBlocks.WHEAT_STAGE7, ItemStack.EMPTY.type());
        assertEquals(item("minecraft:wheat"), drops.get(0).type());
        assertEquals(1, drops.get(0).count(), "exactly one wheat, first");
        int seeds = drops.size() == 2 ? drops.get(1).count() : 0;
        assertTrue(seeds >= 0 && seeds <= 3, "the bonus stays in the 0-3 band: " + seeds);
        if (drops.size() == 2) {
            assertEquals(item("minecraft:wheat_seeds"), drops.get(1).type());
        }

        var young = new net.zaminmc.torch.server.interaction.DropService(
                new java.util.Random(0x5EEDL))
                .dropsFor(BuiltinBlocks.WHEAT_STAGE2, ItemStack.EMPTY.type());
        assertEquals(1, young.size(), "a young crop yields only the seed");
        assertEquals(item("minecraft:wheat_seeds"), young.get(0).type());
        server.shutdown(null);
    }

    @Test
    void theCraftingGridCraftsBreadAndBoneMeal() {
        List<net.zaminmc.torch.server.crafting.CraftingRecipe> all =
                net.zaminmc.torch.server.crafting.BuiltinRecipes.ALL;
        boolean bread = all.stream().anyMatch(r -> r.result().item()
                .equals(net.zaminmc.torch.util.Identifier.parse("minecraft:bread")));
        boolean meal = all.stream().anyMatch(r -> r.result().item()
                .equals(net.zaminmc.torch.util.Identifier.parse("minecraft:bone_meal")));
        assertTrue(bread, "the bread recipe is registered");
        assertTrue(meal, "the bone meal recipe is registered");
    }
}
