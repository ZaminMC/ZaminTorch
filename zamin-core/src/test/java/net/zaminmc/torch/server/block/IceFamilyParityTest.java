package net.zaminmc.torch.server.block;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ice family's melt and break seats (the verifier's twenty-first pass,
 * VT-003 — the reference's two arms the engine never carried):
 *
 * <p><b>THE MELT</b> (IceBlock.tick lines 66-77 + SnowLayerBlock.tick lines
 * 115-119 over the reference's randomTick → tick delegation): the
 * BLOCK-light consult alone ({@code world.getLight(LightType.BLOCK, pos) >
 * 11 - getOpacity()} — the translucent pair's zero opacity makes the gate
 * {@code > 11}; the SKY never melts a surface ice at noon), the nether's
 * vaporizing profile ({@code world.dimension.yeetsWater()} — true in the
 * nether alone) removing where the overworld and the End take the melt
 * water (the ICE MELT writes the SOURCE form, {@code Blocks.WATER}), and
 * the snow layer never yielding water at all.</p>
 *
 * <p><b>THE BREAK</b> (IceBlock.afterMinedByPlayer lines 42-55, the else
 * arm): a non-silk survival break of ice over a movement-blocking or
 * liquid floor turns the cell to FLOWING_WATER (the FLOWING form — the
 * melt's source form is the random-tick law alone); silk takes the cube
 * (the drop walk's own row) and the water never forms; ice over air
 * breaks to plain air.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IceFamilyParityTest {

    // ---- the direct-driven melt harness (the growth tests' own shape) ---

    private static EngineWorld overworld;
    private static EngineWorld nether;
    private static EngineWorld end;
    private static RandomTickSystem overworldTicks;
    private static RandomTickSystem netherTicks;
    private static RandomTickSystem endTicks;

    @BeforeAll
    static void setUpWorlds() {
        FrozenBlockRegistry registry = BuiltinBlocks.registerAll(
                new BlockRegistryBuilder()).freeze();
        overworld = worldOf(registry, 0);
        nether = worldOf(registry, -1);
        end = worldOf(registry, 1);
        overworldTicks = new RandomTickSystem(overworld, new Random(0x1CE1L));
        netherTicks = new RandomTickSystem(nether, new Random(0x1CE2L));
        endTicks = new RandomTickSystem(end, new Random(0x1CE3L));
    }

    private static EngineWorld worldOf(FrozenBlockRegistry registry, int dimension) {
        EngineWorld world = new EngineWorld("ice-parity-" + dimension, registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread(), dimension);
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.getOrGenerate(new ChunkPosition(0, 0));
        return world;
    }

    /** Places ice + the torch beside it (block light 13 at the ice cell). */
    private static void stageIceAndTorch(EngineWorld world, BlockPosition ice) {
        world.setBlock(ice.offset(0, -1, 0), BuiltinBlocks.STONE);
        world.setBlock(ice, BuiltinBlocks.ICE);
        world.setBlock(ice.offset(1, 0, 0), BuiltinBlocks.TORCH);
    }

    @Test
    void theTorchMeltTurnsOverworldIceToWaterSource() {
        BlockPosition ice = new BlockPosition(8, 5, 8);
        stageIceAndTorch(overworld, ice);
        // The light AT the ice cell is the torch's 14 attenuated by the
        // ICE'S OWN opacity-3 row (Block.java line 1176): 11 — and the
        // melt gate reads {@code 11 - 3 = 8}, so the adjacent torch still
        // melts (an unattenuated reading would be 13).
        assertEquals(11, overworld.blockLightAt(ice),
                "the ice-attenuated block light (the opacity-3 row)");
        overworldTicks.randomTick(ice);
        assertEquals(FluidBlocks.sourceOf(FluidBlocks.Kind.WATER),
                overworld.getBlock(ice),
                "the melt writes the WATER source form (IceBlock.tick line 76)");
        overworld.setBlock(ice.offset(1, 0, 0), overworld.airType());
    }

    @Test
    void theNetherProfileMeltsIceToNothing() {
        BlockPosition ice = new BlockPosition(8, 5, 8);
        stageIceAndTorch(nether, ice);
        netherTicks.randomTick(ice);
        assertEquals(nether.airType(), nether.getBlock(ice),
                "the nether's vaporizing profile removes the melting ice (line 72)");
        nether.setBlock(ice.offset(1, 0, 0), nether.airType());
    }

    @Test
    void theEndTakesTheMeltWater() {
        BlockPosition ice = new BlockPosition(8, 5, 8);
        stageIceAndTorch(end, ice);
        endTicks.randomTick(ice);
        assertEquals(FluidBlocks.sourceOf(FluidBlocks.Kind.WATER),
                end.getBlock(ice),
                "the End's no-sky profile keeps water — the melt writes the source form");
        end.setBlock(ice.offset(1, 0, 0), end.airType());
    }

    @Test
    void theDarkIceNeverMelts() {
        BlockPosition ice = new BlockPosition(12, 5, 12);
        overworld.setBlock(ice.offset(0, -1, 0), BuiltinBlocks.STONE);
        overworld.setBlock(ice, BuiltinBlocks.ICE);
        overworldTicks.randomTick(ice);
        assertEquals(BuiltinBlocks.ICE, overworld.getBlock(ice),
                "block light 0 never passes the > 11 gate");
    }

    @Test
    void theSkyNeverMeltsTheSurfaceIce() {
        BlockPosition ice = new BlockPosition(16, 5, 16);
        overworld.setBlock(ice.offset(0, -1, 0), BuiltinBlocks.STONE);
        overworld.setBlock(ice, BuiltinBlocks.ICE);
        // The sky light at the ice cell is the open sky's 15 attenuated by
        // the ice's own opacity-3 row: 12. The melt consult reads BLOCK
        // light alone (LightType.BLOCK) — zero here — so the noon surface
        // ice never melts.
        assertEquals(12, overworld.skyLightAt(ice),
                "the ice-attenuated sky light (the opacity-3 row)");
        assertEquals(0, overworld.blockLightAt(ice), "no block light staged");
        overworldTicks.randomTick(ice);
        assertEquals(BuiltinBlocks.ICE, overworld.getBlock(ice),
                "the melt consult reads BLOCK light alone — the sky-lit surface "
                        + "ice at noon never melts");
    }

    @Test
    void theTorchMeltsTheSnowLayerToNothing() {
        BlockPosition floor = new BlockPosition(20, 4, 20);
        BlockPosition snow = floor.offset(0, 1, 0);
        overworld.setBlock(floor, BuiltinBlocks.STONE);
        overworld.setBlock(snow, BuiltinBlocks.SNOW_LAYER);
        overworld.setBlock(snow.offset(1, 0, 0), BuiltinBlocks.TORCH);
        overworldTicks.randomTick(snow);
        assertEquals(overworld.airType(), overworld.getBlock(snow),
                "the snow layer melts away (SnowLayerBlock.tick lines 115-119)");
        overworld.setBlock(snow.offset(1, 0, 0), overworld.airType());
    }

    @Test
    void theSnowLayerUnderOpenSkyStays() {
        BlockPosition floor = new BlockPosition(24, 4, 24);
        BlockPosition snow = floor.offset(0, 1, 0);
        overworld.setBlock(floor, BuiltinBlocks.STONE);
        overworld.setBlock(snow, BuiltinBlocks.SNOW_LAYER);
        overworldTicks.randomTick(snow);
        assertEquals(BuiltinBlocks.SNOW_LAYER, overworld.getBlock(snow),
                "the sky-lit snow layer never melts (the BLOCK-light consult)");
    }

    // ---- the break harness (the real dig path on the live engine) -------

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "icebreak" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var accepted = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /** Stages the ice cell and stands the digger beside it, on the ground. */
    private PlayerSession stageIceAndDigger(String name, BlockType below, BlockPosition target)
            throws InterruptedException {
        PlayerSession digger = join(name);
        EngineWorld over = server.world();
        server.ticker().submit(() -> {
            over.setBlock(target.offset(0, -1, 0), below);
            over.setBlock(target, BuiltinBlocks.ICE);
        });
        await(() -> over.getBlock(target).equals(BuiltinBlocks.ICE), "the ice cell landed");
        Position stand = new Position(target.x() + 4.5, target.y(), target.z() + 0.5);
        server.ticker().submit(() -> digger.applyMovement(stand,
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(over, 6, "the stand lands");
        return digger;
    }

    private void dig(PlayerSession digger, BlockPosition target) throws InterruptedException {
        server.blockInteraction().submitMiningStart(digger, target, 1);
        EngineAwaits.awaitTicks(server.world(), 80,
                "ice by hand: the dig progresses past the finish floor");
        server.blockInteraction().submitMiningFinished(digger, target);
        EngineAwaits.awaitTicks(server.world(), 2, "the finish walk lands");
    }

    /** The settle await: the flowing form's own tick (FlowingLiquidBlock.tick
     * lines 91-94, the {@code i == 0} else arm) converts the level-0 falling
     * form to the SOURCE — the water's 5-tick cadence plus margin. */
    private void awaitSettle() throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 14, "the flowing form's settle tick");
    }

    @Test
    void theIceBreakOverStoneBecomesFlowingWater() throws Exception {
        BlockPosition target = new BlockPosition(2, 5, 2);
        PlayerSession digger = stageIceAndDigger("IceBreaker", BuiltinBlocks.STONE, target);
        dig(digger, target);
        // The reference's literal write: the FLOWING form at LEVEL 0 (the
        // falling form — afterMinedByPlayer line 55's FLOWING_WATER
        // defaultState), read before the fluid family's own tick runs.
        assertEquals(FluidBlocks.typeOf(FluidBlocks.Kind.WATER,
                        FluidBlocks.Form.FLOWING, 0),
                server.world().getBlock(target),
                "the non-silk break over a movement-blocking floor writes FLOWING_WATER "
                        + "(IceBlock.afterMinedByPlayer lines 49-55)");
        // The settle: the level-0 falling form converts to the SOURCE on its
        // own flow tick (FlowingLiquidBlock.tick lines 91-94) — the classic
        // full-water block the broken ice leaves.
        awaitSettle();
        assertEquals(FluidBlocks.sourceOf(FluidBlocks.Kind.WATER),
                server.world().getBlock(target),
                "the level-0 flowing form settles into the source (the reference's "
                        + "convertToSource arm)");
    }

    @Test
    void theIceBreakOverWaterBecomesFlowingWater() throws Exception {
        BlockPosition target = new BlockPosition(8, 6, 8);
        PlayerSession digger = stageIceAndDigger("IceFloater",
                FluidBlocks.sourceOf(FluidBlocks.Kind.WATER), target);
        dig(digger, target);
        assertEquals(FluidBlocks.typeOf(FluidBlocks.Kind.WATER,
                        FluidBlocks.Form.FLOWING, 0),
                server.world().getBlock(target),
                "the isLiquid floor arm forms the water too (line 53's || material.isLiquid())");
        awaitSettle();
        assertEquals(FluidBlocks.sourceOf(FluidBlocks.Kind.WATER),
                server.world().getBlock(target),
                "the floating break settles into the source beside its water floor");
    }

    @Test
    void theIceBreakOverAirLeavesPlainAir() throws Exception {
        BlockPosition target = new BlockPosition(14, 6, 14);
        // Air below: the floor consult fails — the reference's plain removal.
        PlayerSession digger = stageIceAndDigger("IceLedge", server.world().airType(), target);
        dig(digger, target);
        assertEquals(server.world().airType(), server.world().getBlock(target),
                "ice over air breaks to plain air (the below-gate negative)");
        awaitSettle();
        assertEquals(server.world().airType(), server.world().getBlock(target),
                "no water ever forms over the open ledge");
    }

    @Test
    void theSilkBreakTakesTheCubeAndFormsNoWater() throws Exception {
        BlockPosition target = new BlockPosition(20, 5, 20);
        PlayerSession digger = stageIceAndDigger("IceSilk", BuiltinBlocks.STONE, target);
        // The silk pickaxe (id 33, level 1): the reference's hasSilkTouchDrops
        // arm takes the cube and never reaches the water write.
        net.zaminmc.torch.item.ItemStack silkPick = new net.zaminmc.torch.item.ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_PICKAXE, 1, 0, null,
                Map.of(net.zaminmc.torch.server.enchantment.Enchantments.SILK_TOUCH.id, 1));
        server.ticker().submit(() -> digger.inventory()
                .setSlot(digger.inventory().heldSlot(), silkPick));
        await(() -> !digger.inventory().held().isEmpty()
                && digger.inventory().held().equals(silkPick), "the silk pick held");
        dig(digger, target);
        assertEquals(server.world().airType(), server.world().getBlock(target),
                "the silk break takes the cube — no water forms (lines 40-47)");
        awaitSettle();
        assertEquals(server.world().airType(), server.world().getBlock(target),
                "the settled cell stays air under silk");
    }
}
