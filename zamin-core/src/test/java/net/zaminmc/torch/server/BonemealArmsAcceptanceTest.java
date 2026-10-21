package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.CocoaBlocks;
import net.zaminmc.torch.server.block.MelonBlocks;
import net.zaminmc.torch.server.block.MushroomBlocks;
import net.zaminmc.torch.server.block.WoodVariantBlocks;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bone-meal arms' use-path scenarios (the 2026-10-21 audit's VIII7 —
 * ItemDye.fertilize's dispatch over the reference's Fertilizable pairs):
 * the grass block's always-paid grow (GrassBlock lines 253-257 — canGrow
 * and canBeFertilized both always true), the mushroom's always-paid roll
 * (MushroomPlantBlock lines 93-97 — the 0.4 gate owns the giant form
 * alone), the cocoa pod's age-band advance (CocoaBlock lines 198-202 —
 * the AGE &lt; 2 band pays and steps, the mature pod's use never consumes),
 * and the pumpkin stem's 2-5 jump (StemBlock line 128 — the crops arm's
 * own law, the VIII7 stem sub-row's retirement evidence).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BonemealArmsAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "growth", "it", 20, 4, 20,
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
        Thread.sleep(150); // the tick-thread application window
    }

    /** Polls the held count to its expected value (the cold-boot flake
     *  guard: the use's tick-thread application can land past a fixed
     *  sleep under first-boot load — the world-state awaits' own pattern). */
    private void awaitHeld(PlayerSession player, int expected) throws InterruptedException {
        await(() -> player.inventory().held().count() == expected,
                "held count reaches " + expected);
    }

    /** Sets one block through the tick thread (the world's ownership rule). */
    private void setBlock(BlockPosition at, BlockType type) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "seeded " + at);
    }

    /** Moves the player beside the target (the use path's 8-block body gate). */
    private void standBeside(PlayerSession player) throws InterruptedException {
        server.ticker().submit(() -> player.applyMovement(
                new Position(0.5, 66.5, 1.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        Thread.sleep(150);
    }

    @Test
    void boneMealOnGrassAlwaysPaysAndPlantsTheFlora() throws Exception {
        server = boot();
        PlayerSession gardener = join("Gardener");
        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.GRASS_BLOCK);
        setBlock(soil.offset(0, 1, 0), server.world().airType());
        standBeside(gardener);

        hold(gardener, item("minecraft:bone_meal"), 2);
        useOn(gardener, soil);

        // GrassBlock's pair: canGrow always true (the stack ALWAYS pays),
        // canBeFertilized always true (the grow ALWAYS runs) — attempt 0 of
        // the 128-walk plants at the start cell whole.
        awaitHeld(gardener, 1);
        BlockType planted = server.world().getBlock(soil.offset(0, 1, 0));
        String name = planted.identifier().value();
        assertTrue(name.equals("tall_grass") || name.equals("dandelion")
                        || net.zaminmc.torch.server.block.FlowerBlocks
                                .isFlowerIdentifier(planted.identifier()),
                "the start cell carries the flora family, not " + name);
        server.shutdown(null);
    }

    @Test
    void boneMealOnTheMushroomAlwaysPays() throws Exception {
        server = boot();
        PlayerSession gardener = join("Mycologist");
        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.MYCELIUM);
        setBlock(soil.offset(0, 1, 0), BuiltinBlocks.BROWN_MUSHROOM);
        standBeside(gardener);

        hold(gardener, item("minecraft:bone_meal"), 2);
        useOn(gardener, soil.offset(0, 1, 0));

        // MushroomPlantBlock's pair: canGrow always true — the stack pays
        // whether the 0.4 roll grows the giant or the restore puts the
        // plant back (the fertilize's stack.size-- rides the canGrow arm).
        awaitHeld(gardener, 1);
        BlockType at = server.world().getBlock(soil.offset(0, 1, 0));
        assertTrue(at.equals(BuiltinBlocks.BROWN_MUSHROOM)
                        || MushroomBlocks.isMushroomBlock(at),
                "the cell carries either the restored plant or the giant (found "
                        + at.identifier() + ")");
        server.shutdown(null);
    }

    @Test
    void boneMealAdvancesTheCocoaPodOneAge() throws Exception {
        server = boot();
        PlayerSession gardener = join("Farmer");
        // The jungle log at (0, 65, 0), the pod on its east face at
        // (1, 65, 0) facing back west.
        setBlock(new BlockPosition(0, 65, 0), WoodVariantBlocks.logType(3));
        setBlock(new BlockPosition(1, 65, 0), CocoaBlocks.placementState(5));
        standBeside(gardener);

        hold(gardener, item("minecraft:bone_meal"), 2);
        useOn(gardener, new BlockPosition(1, 65, 0));

        // CocoaBlock's pair: canGrow (AGE < 2) pays the stack, grow is the
        // single AGE + 1 step (line 202) — never the crop family's 2-5.
        awaitHeld(gardener, 1);
        await(() -> CocoaBlocks.ageOf(server.world().getBlock(
                new BlockPosition(1, 65, 0))) == 1, "the pod advances to age 1");
        server.shutdown(null);
    }

    @Test
    void theMatureCocoaPodRefusesTheUse() throws Exception {
        server = boot();
        PlayerSession gardener = join("Farmer");
        setBlock(new BlockPosition(0, 65, 0), WoodVariantBlocks.logType(3));
        setBlock(new BlockPosition(1, 65, 0), CocoaBlocks.state(1, 2));
        standBeside(gardener);

        hold(gardener, item("minecraft:bone_meal"), 2);
        useOn(gardener, new BlockPosition(1, 65, 0));

        // The AGE 2 band: canGrow false — fertilize returns false whole and
        // the use never consumes the stack (the reference's own refusal).
        assertEquals(2, gardener.inventory().held().count(), "the mature pod never pays");
        assertEquals(2, CocoaBlocks.ageOf(server.world().getBlock(new BlockPosition(1, 65, 0))),
                "the mature pod stays at AGE 2");
        server.shutdown(null);
    }

    @Test
    void boneMealJumpsThePumpkinStem() throws Exception {
        server = boot();
        PlayerSession gardener = join("Farmer");
        setBlock(new BlockPosition(0, 64, 0), BuiltinBlocks.FARMLAND);
        setBlock(new BlockPosition(0, 65, 0), MelonBlocks.stemStage("pumpkin_stem", 2));
        standBeside(gardener);

        hold(gardener, item("minecraft:bone_meal"), 2);
        useOn(gardener, new BlockPosition(0, 65, 0));

        // StemBlock.grow line 128 (over the crops arm's own law): AGE +
        // MathHelper.nextInt(random, 2, 5), clamped at 7 — the stage-2 stem
        // lands in 4..7 (the VIII7 stem sub-row's retirement evidence: the
        // crops arm already carried the law whole).
        awaitHeld(gardener, 1);
        await(() -> net.zaminmc.torch.server.block.WorldSolidity.cropStageOf(
                server.world().getBlock(new BlockPosition(0, 65, 0)).identifier()) >= 4,
                "the stem jumps the 2-5 band");
        server.shutdown(null);
    }

    @Test
    void theMushroomPlacementRefusesTheLitGrass() throws Exception {
        server = boot();
        PlayerSession gardener = join("Mycologist");
        BlockPosition soil = new BlockPosition(0, 64, 0);
        setBlock(soil, BuiltinBlocks.GRASS_BLOCK);
        setBlock(soil.offset(0, 1, 0), server.world().airType());
        standBeside(gardener);

        // MushroomPlantBlock.canBePlaced lines 69-73 over canSurvive: the
        // placement-support gate consults the plant's own law — the lit
        // grass (skylight 15, no mycelium below) refuses the placement.
        hold(gardener, item("minecraft:brown_mushroom"), 4);
        useOn(gardener, soil); // the item's placement path (the held block)

        // The refused placement resyncs and never commits: the cell stays
        // air, the stack keeps its count (BlockItem's own refusal).
        assertFalse(server.world().getBlock(soil.offset(0, 1, 0))
                        .equals(BuiltinBlocks.BROWN_MUSHROOM),
                "the lit grass refuses the mushroom placement");
        // The mycelium arm passes: the same placement on mycelium commits.
        setBlock(soil, BuiltinBlocks.MYCELIUM);
        useOn(gardener, soil);
        assertTrue(server.world().getBlock(soil.offset(0, 1, 0))
                        .equals(BuiltinBlocks.BROWN_MUSHROOM),
                "the mycelium accepts the mushroom placement");
        server.shutdown(null);
    }
}
