package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RandomTickSystem;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world-completion flora: the reed plants only on wet soil, grows to
 * three and fells from the base; the cactus stacks on sand, breaks beside
 * solids and pricks the bodies that touch it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CaneAndCactusAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "cane", "it", 20, 4, 20,
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
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8_000;
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

    private void setBlock(BlockPosition at, BlockType type) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "seeded " + at);
    }

    private void useOn(PlayerSession player, BlockPosition at, int face) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
        Thread.sleep(150);
    }

    private void probe(RandomTickSystem ticks, BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> ticks.randomTick(at));
        Thread.sleep(80);
    }

    @Test
    void canePlantsOnlyOnWetSoilAndGrowsToThree() throws Exception {
        server = boot();
        PlayerSession farmer = join("Farmer");

        // Dry dirt: the cane refuses (the historical reed gate).
        BlockPosition dry = new BlockPosition(0, 64, 0);
        setBlock(dry, BuiltinBlocks.DIRT);
        farmer.inventory().setSlot(0, ItemStack.of(item("minecraft:sugar_cane"), 4));
        farmer.inventory().selectHotbarSlot(0);
        useOn(farmer, dry, 1);
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(dry.offset(0, 1, 0)),
                "dry soil refuses the reed");
        assertEquals(4, farmer.inventory().held().count(), "the refused use spends nothing");

        // Wet soil: water beside the dirt — the cane takes, one per use.
        BlockPosition wet = new BlockPosition(4, 64, 0);
        setBlock(wet, BuiltinBlocks.DIRT);
        setBlock(wet.offset(1, 0, 0),
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER));
        useOn(farmer, wet, 1);
        assertEquals(BuiltinBlocks.SUGAR_CANE, server.world().getBlock(wet.offset(0, 1, 0)),
                "wet soil takes the reed");
        assertEquals(3, farmer.inventory().held().count(), "planting spent one cane");

        // The random-tick clock grows the reed to three, then stops. The
        // growth is a 1-in-3 roll per probe, so the loops poll until the
        // stalk appears (the cap, not the pace, is the contract).
        var ticks = server.randomTicks();
        BlockPosition cane = wet.offset(0, 1, 0);
        for (int i = 0; i < 30; i++) {
            probe(ticks, cane);
        }
        await(() -> server.world().getBlock(cane.offset(0, 1, 0)).equals(BuiltinBlocks.SUGAR_CANE),
                "the reed grew a second stalk");
        for (int i = 0; i < 30; i++) {
            probe(ticks, cane.offset(0, 1, 0));
        }
        await(() -> server.world().getBlock(cane.offset(0, 2, 0)).equals(BuiltinBlocks.SUGAR_CANE),
                "the reed grew its third stalk");
        for (int i = 0; i < 8; i++) {
            probe(ticks, cane.offset(0, 2, 0));
        }
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(cane.offset(0, 3, 0)),
                "the reed never exceeds three");
        server.shutdown(null);
    }

    @Test
    void breakingTheBaseFellsTheWholeReed() throws Exception {
        server = boot();
        join("Bystander");
        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.DIRT);
        setBlock(soil.offset(1, 0, 0),
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER));
        BlockPosition base = soil.offset(0, 1, 0);
        setBlock(base, BuiltinBlocks.SUGAR_CANE);
        setBlock(base.offset(0, 1, 0), BuiltinBlocks.SUGAR_CANE);

        // Break the base: the support chain pops the upper stalk one update
        // after another.
        server.ticker().submit(() -> server.world().setBlock(base, BuiltinBlocks.AIR));
        await(() -> server.world().getBlock(base.offset(0, 1, 0)).equals(BuiltinBlocks.AIR),
                "the orphaned stalk popped");
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(base),
                "the base is gone");
        server.shutdown(null);
    }

    @Test
    void cactusBreaksBesideSolidsAndPricksBodies() throws Exception {
        server = boot();
        PlayerSession toucher = join("Toucher");
        // Ground level: the flat world's surface sits at y=4, so the sand
        // stack stands on real ground (no gravity fall mid-test).
        BlockPosition sand = new BlockPosition(0, 5, 0);
        setBlock(sand, BuiltinBlocks.SAND);
        BlockPosition cactus = sand.offset(0, 1, 0);
        setBlock(cactus, BuiltinBlocks.CACTUS);

        // A solid block settles beside the cactus: it breaks with its drop.
        setBlock(cactus.offset(1, 0, 0), BuiltinBlocks.STONE);
        await(() -> server.world().getBlock(cactus).equals(BuiltinBlocks.AIR),
                "the cactus broke beside the solid");

        // Clear the stone again, replant, and stand in the cell: the prick
        // damage lands at the half-second cadence.
        setBlock(cactus.offset(1, 0, 0), BuiltinBlocks.AIR);
        setBlock(cactus, BuiltinBlocks.CACTUS);
        server.ticker().submit(() -> toucher.applyMovement(
                new Position(cactus.x() + 0.5, cactus.y(), cactus.z() + 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> toucher.health() < PlayerSession.MAX_HEALTH,
                "the cactus pricked the body");
        server.shutdown(null);
    }

    @Test
    void sugarAndPaperCraftFromTheCane() throws Exception {
        server = boot();
        join("Crafter");
        var crafting = server.crafting();

        // One cane -> one sugar (the 2x2 grid takes the 1x1 pattern).
        var grid = new net.zaminmc.torch.server.player.CraftingGrid();
        grid.setCell(0, ItemStack.of(item("minecraft:sugar_cane"), 1));
        var result = crafting.resultOf(grid.snapshotArray());
        assertEquals("minecraft:sugar",
                result.map(ItemStack::type).map(t -> t.identifier().toString()).orElse(""),
                "one cane crafts one sugar");

        // Three canes in a row -> three paper (the 3x3 table grid).
        var table = new net.zaminmc.torch.server.player.CraftingGrid(3, 3);
        table.setCell(0, ItemStack.of(item("minecraft:sugar_cane"), 1));
        table.setCell(1, ItemStack.of(item("minecraft:sugar_cane"), 1));
        table.setCell(2, ItemStack.of(item("minecraft:sugar_cane"), 1));
        var paper = crafting.resultOf3x3(table.snapshotArray());
        assertEquals("minecraft:paper",
                paper.map(ItemStack::type).map(t -> t.identifier().toString()).orElse(""),
                "a cane row crafts paper");
        assertEquals(3, paper.map(ItemStack::count).orElse(0), "the row yields three papers");
        server.shutdown(null);
    }

    @Test
    void goldOreSmeltsIntoTheIngot() {
        var result = net.zaminmc.torch.server.furnace.FurnaceRecipes.resultOf(
                net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:gold_ore")).orElseThrow());
        assertTrue(result.isPresent(), "gold ore is smeltable");
        assertEquals("minecraft:gold_ingot", result.get().output().toString());
    }
}
