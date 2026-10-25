package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fx.BlockSoundMap;
import net.zaminmc.torch.server.fx.BlockSoundMap.SoundFamily;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.physics.EntityCollisionWorld;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The step-sound family (the VT-7 tail's shared walk): the reference's
 * own {@code Block.Sounds} table — every family's breaking/placing/
 * stepping names and volume/pitch terms 1:1 (Block.java lines 40-88 +
 * 1508-1539, the Block.init() registration census) — and the
 * {@code Entity.move} walk that plays them (lines 594-624: the 0.6
 * accumulation, the blocksWalkedOn crossing, the swim arm, the
 * snow-layer override, the liquid refusal, the makesSteps=false
 * family). The placement sound's own law (BlockItem.place lines 53-60)
 * rides the same table — the family's placing name at
 * {@code (volume + 1) / 2} and {@code pitch * 0.8}.
 */
class StepSoundFamilyTest {

    // ------------------------------------------------------------------
    // The table: the twelve families and the DEFAULT_SOUNDS law
    // ------------------------------------------------------------------

    private static SoundFamily family(String id) {
        return BlockSoundMap.familyOf(Identifier.parse(id)).orElseThrow();
    }

    @Test
    void theTwelveFamiliesCarryTheReferenceNamesAndTerms() {
        // The plain families: step.<key> at volume 1.0 pitch 1.0.
        assertEquals("step.stone", family("minecraft:stone").stepping());
        assertEquals("step.wood", family("minecraft:oak_planks").stepping());
        assertEquals("step.gravel", family("minecraft:gravel").stepping());
        assertEquals("step.grass", family("minecraft:grass_block").stepping());
        assertEquals("step.cloth", family("minecraft:wool").stepping());
        assertEquals("step.sand", family("minecraft:sand").stepping());
        assertEquals("step.snow", family("minecraft:snow_layer").stepping());
        assertEquals("step.ladder", family("minecraft:ladder").stepping());
        assertEquals("step.anvil", family("minecraft:anvil").stepping());
        // The metal family: the stone KEY at pitch 1.5.
        assertEquals("step.stone", family("minecraft:iron_block").stepping());
        assertEquals(1.5f, family("minecraft:iron_block").pitch());
        // The glass family's placing override reads step.stone; the
        // breaking override reads dig.glass.
        assertEquals("dig.glass", family("minecraft:glass").breaking());
        assertEquals("step.stone", family("minecraft:glass").placing());
        assertEquals("step.stone", family("minecraft:glass").stepping());
        // The ladder breaks like wood.
        assertEquals("dig.wood", family("minecraft:ladder").breaking());
        // The anvil: volume 0.3, breaks stone, lands random.anvil_land.
        assertEquals(0.3f, family("minecraft:anvil").volume());
        assertEquals("dig.stone", family("minecraft:anvil").breaking());
        assertEquals("random.anvil_land", family("minecraft:anvil").placing());
        // The slime trio: the mob sounds, not dig/step names.
        assertEquals("mob.slime.big", family("minecraft:slime_block").breaking());
        assertEquals("mob.slime.big", family("minecraft:slime_block").placing());
        assertEquals("mob.slime.small", family("minecraft:slime_block").stepping());
    }

    @Test
    void theExtractionCorrectionsCarryTheReferenceRows() {
        // Dirt digs GRAVEL (register line 995's setSounds(GRAVEL_SOUNDS)),
        // wool CLOTH (dig.cloth — never the hand-written dig.wool), cactus
        // CLOTH, TNT GRASS, the snow pair SNOW.
        assertEquals("dig.gravel", family("minecraft:dirt").breaking());
        assertEquals("dig.cloth", family("minecraft:wool").breaking());
        assertEquals("dig.cloth", family("minecraft:cactus").breaking());
        assertEquals("dig.grass", family("minecraft:tnt").breaking());
        assertEquals("dig.sand", family("minecraft:soul_sand").breaking());
        assertEquals("dig.snow", family("minecraft:snow").breaking());
        assertEquals("dig.grass", family("minecraft:hay_block").breaking());
        // The DEFAULT_SOUNDS rows: the blocks whose registrations (and
        // constructors) set no family at all dig stone — the noteblock
        // (Material.WOOD but no setSounds), the cobweb, the infested
        // eggs, the redstone dust, the enchanting table.
        assertEquals(SoundFamily.STONE, family("minecraft:noteblock"));
        assertEquals(SoundFamily.STONE, family("minecraft:cobweb"));
        assertEquals(SoundFamily.STONE, family("minecraft:infested_stone"));
        assertEquals(SoundFamily.STONE, family("minecraft:redstone_wire"));
        assertEquals(SoundFamily.STONE, family("minecraft:enchanting_table"));
        assertEquals("dig.stone", family("minecraft:noteblock").breaking());
    }

    @Test
    void theFlattenedStateVariantsResolveToTheirBaseFamilies() {
        // The woods' stair halves dig wood; the masonry stairs carry
        // their base block's own family (StairsBlock's constructor copies
        // it — the stone brick pair digs stone).
        assertEquals("dig.wood", family("minecraft:birch_stairs_north_top").breaking());
        assertEquals("dig.wood", family("minecraft:dark_oak_stairs_east").breaking());
        assertEquals("dig.stone", family("minecraft:stone_brick_stairs_east_top").breaking());
        assertEquals("dig.stone", family("minecraft:cobblestone_stairs_west").breaking());
        // The facing furniture and the state flattening.
        assertEquals("dig.wood", family("minecraft:chest_east").breaking());
        assertEquals("dig.stone", family("minecraft:furnace_north").breaking());
        assertEquals("dig.wood", family("minecraft:oak_door_upper_open").breaking());
        assertEquals("dig.metal".replace("metal", "stone"),
                family("minecraft:iron_door_south").breaking(),
                "the iron door's METAL family breaks dig.stone at pitch 1.5");
        assertEquals(SoundFamily.METAL, family("minecraft:iron_door_south"));
        assertEquals(SoundFamily.WOOD, family("minecraft:spruce_fence_gate_open_east"));
        // The fluid levels and the fire ages.
        assertEquals(SoundFamily.STONE, family("minecraft:flowing_water_3"));
        assertEquals(SoundFamily.STONE, family("minecraft:still_lava_7"));
        assertEquals(SoundFamily.CLOTH, family("minecraft:fire_age5"));
        // The persistent leaves and the ready saplings.
        assertEquals("dig.grass", family("minecraft:acacia_leaves_persistent_check").breaking());
        assertEquals(SoundFamily.GRASS, family("minecraft:dark_oak_sapling_ready"));
        // The non-minecraft namespace answers empty (no historical family).
        assertTrue(BlockSoundMap.familyOf(Identifier.parse("test:red_block")).isEmpty());
    }

    // ------------------------------------------------------------------
    // The walk: the accumulation, the crossing, the swim arm, the gates
    // ------------------------------------------------------------------

    /**
     * The solver-backed stub world (the MobSolverWalkTest shape) with the
     * step walk's own reads: the block identity at a cell and the fluid
     * consults.
     */
    private static class StepStubWorld implements MobEntity.WorldQuery {
        final Map<Long, BlockTypeCell> cells = new HashMap<>();

        record BlockTypeCell(BlockType type, boolean water) {
        }

        void put(int x, int y, int z, BlockType type) {
            cells.put(key(x, y, z), new BlockTypeCell(type, false));
        }

        void putWater(int x, int y, int z) {
            cells.put(key(x, y, z), new BlockTypeCell(
                    FluidBlocks.sourceOf(FluidBlocks.Kind.WATER), true));
        }

        static long key(int x, int y, int z) {
            return ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL);
        }

        BlockTypeCell read(int x, int y, int z) {
            if (y == 0) {
                return new BlockTypeCell(BuiltinBlocks.BEDROCK, false);
            }
            BlockTypeCell cell = cells.get(key(x, y, z));
            return cell != null ? cell : new BlockTypeCell(BuiltinBlocks.AIR, false);
        }

        /** The stone floor: cells at y=63 — the top at 64.0. */
        void floor() {
            for (int x = -100; x <= 100; x++) {
                for (int z = -100; z <= 100; z++) {
                    put(x, 63, z, BuiltinBlocks.STONE);
                }
            }
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).type());
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            return read(x, y, z).type().identifier();
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return read((int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.floor(z)).water();
        }

        private final EntityCollisionWorld surface = new EntityCollisionWorld() {
            @Override
            public BlockType blockAt(int x, int y, int z) {
                return read(x, y, z).type();
            }

            @Override
            public boolean chunkLoaded(int x, int z) {
                return true;
            }
        };

        @Override
        public EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    /** A cow on the stub floor (the 0.9x1.4 walker, step height 0.5). */
    private static MobEntity cow(StepStubWorld world, double x, double y, double z) {
        MobEntity mob = new MobEntity(7, MobType.COW, new Position(x, y, z),
                new Random(11), world);
        mob.setOnGround(true);
        return mob;
    }

    @Test
    void theWalkingBodyStepsAtTheReferenceCadence() {
        StepStubWorld world = new StepStubWorld();
        world.floor();
        MobEntity mob = cow(world, 5.5, 64.0, 5.5);
        // Velocity 2.0 east: the first move carries 2.0 blocks —
        // distanceMoved = 2.0 * 0.6 = 1.2 crosses the init threshold 1.
        mob.setVelocity(2.0, 0.0, 0.0);
        mob.tick(false);
        MobEntity.StagedSound first = mob.consumeStepSound();
        assertNotNull(first, "the first crossing fired a footstep");
        assertEquals("step.stone", first.name());
        assertEquals(0.15f, first.volume(), 1e-6,
                "the family volume 1.0 scaled by the walk's 0.15");
        assertEquals(1.0f, first.pitch(), 1e-6);
        assertNull(mob.consumeSwimSound(), "no swim sound on dry land");
        // The second tick: velocityX damped to 1.2, the accumulated
        // 1.2 + 1.2*0.6 = 1.92 stays under the raised threshold 2 — no step.
        mob.tick(false);
        assertNull(mob.consumeStepSound(), "the accumulator has not crossed again yet");
        // The third tick: 1.92 + 0.72*0.6 = 2.352 crosses 2 — a step.
        mob.tick(false);
        MobEntity.StagedSound third = mob.consumeStepSound();
        assertNotNull(third, "the second crossing fires at the next accumulation");
        assertEquals("step.stone", third.name());
        // The steps stay quiet (the family volume law, not the hurt band).
        assertEquals(0.15f, third.volume(), 1e-6);
    }

    @Test
    void theSnowLayerAboveOverridesTheFootstepFamily() {
        StepStubWorld world = new StepStubWorld();
        world.floor();
        // The snow layers sit in the body's own cells along the walk
        // (the ground cell's up neighbor at the landing point) — the
        // reference's Blocks.SNOW_LAYER override reads the POST-MOVE
        // feet cell's up neighbor.
        for (int x = 5; x <= 9; x++) {
            world.put(x, 64, 5, BuiltinBlocks.SNOW_LAYER);
        }
        MobEntity mob = cow(world, 5.5, 64.0, 5.5);
        mob.setVelocity(2.0, 0.0, 0.0);
        mob.tick(false);
        MobEntity.StagedSound step = mob.consumeStepSound();
        assertNotNull(step, "the crossing fired over the snow layer");
        assertEquals("step.snow", step.name(),
                "the snow layer above overrides the stone floor's family");
    }

    @Test
    void theRiverbedSplashesAndStepsOnTheSameCrossing() {
        StepStubWorld world = new StepStubWorld();
        world.floor();
        // Water fills the body's cell over the stone floor: the riverbed.
        world.putWater(5, 64, 5);
        world.putWater(6, 64, 5);
        world.putWater(7, 64, 5);
        MobEntity mob = cow(world, 5.5, 64.0, 5.5);
        mob.setVelocity(2.0, 0.0, 0.0);
        mob.tick(false);
        // The reference plays BOTH on the crossing: the velocity-scaled
        // game.neutral.swim AND the stone floor's step.stone (the liquid
        // gate reads the FEET cell — the floor, not the water flag).
        MobEntity.StagedSound swim = mob.consumeSwimSound();
        assertNotNull(swim, "the wading body splashes at the crossing");
        assertEquals("game.neutral.swim", swim.name());
        assertTrue(swim.volume() > 0.0f && swim.volume() <= 1.0f,
                "the splash's velocity-scaled volume band: " + swim.volume());
        MobEntity.StagedSound step = mob.consumeStepSound();
        assertNotNull(step, "the riverbed's solid floor still steps");
        assertEquals("step.stone", step.name());
    }

    @Test
    void theOpenWaterRefusesTheFootstepButSplashes() {
        StepStubWorld world = new StepStubWorld();
        // Deep water: cells 63..66 water, the floor at 62 — the body
        // floats mid-column, its feet cell water.
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                world.put(x, 62, z, BuiltinBlocks.STONE);
                for (int y = 63; y <= 66; y++) {
                    world.putWater(x, y, z);
                }
            }
        }
        MobEntity mob = cow(world, 5.5, 65.0, 5.5);
        mob.setVelocity(2.0, 0.0, 0.0);
        mob.tick(false);
        // The crossing fires (the water cell is not air): the swim arm
        // splashes, playStepSound's liquid gate refuses the step.
        assertNotNull(mob.consumeSwimSound(), "the swimming body splashes");
        assertNull(mob.consumeStepSound(), "the liquid feet cell refuses the footstep");
    }

    @Test
    void theSilentFamilyNeverSteps() {
        // The reference's makesSteps overrides among the mob kinds: the
        // guardian, the endermite, and the silverfish walk silently.
        for (MobType kind : new MobType[] {
                MobType.SILVERFISH, MobType.ENDERMITE, MobType.GUARDIAN}) {
            StepStubWorld world = new StepStubWorld();
            world.floor();
            MobEntity mob = new MobEntity(7, kind, new Position(5.5, 64.0, 5.5),
                    new Random(11), world);
            mob.setOnGround(true);
            mob.setVelocity(2.0, 0.0, 0.0);
            mob.tick(false);
            assertNull(mob.consumeStepSound(), kind + " never steps (makesSteps false)");
            assertNull(mob.consumeSwimSound(), kind + " never splashes");
        }
        // The walking family keeps its steps (the control arm).
        StepStubWorld world = new StepStubWorld();
        world.floor();
        MobEntity walker = cow(world, 5.5, 64.0, 5.5);
        walker.setVelocity(2.0, 0.0, 0.0);
        walker.tick(false);
        assertNotNull(walker.consumeStepSound(), "the cow still steps");
    }

    @Test
    void theFenceBelowTheFeetCarriesTheFootstep() {
        StepStubWorld world = new StepStubWorld();
        world.floor();
        // A connected fence row at y=64 over the floor: the 1.5-tall
        // arms hold the body at 65.5, its feet cell (floor(65.5 - 0.2) =
        // 65) is AIR — the fence BELOW is the stepped-on block.
        for (int x = 3; x <= 9; x++) {
            world.put(x, 64, 5, BuiltinBlocks.FENCE);
        }
        MobEntity mob = cow(world, 4.5, 65.5, 5.5);
        mob.setOnGround(true);
        mob.setVelocity(2.0, 0.0, 0.0);
        mob.tick(false);
        MobEntity.StagedSound step = mob.consumeStepSound();
        assertNotNull(step, "the crossing fired over the fence row");
        assertEquals("step.wood", step.name(),
                "the fence-below consult carries the wood family");
    }

    // ------------------------------------------------------------------
    // The placement law (BlockItem.place lines 53-60) over the engine
    // ------------------------------------------------------------------

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.FxEvent> fxEvents = new ArrayList<>();

    @Test
    void thePlacementSoundCarriesTheReferenceLaw() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "steps" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.fx().addListener(fxEvents::add);
        server.start();
        PlayerSession player = join("Placer");

        // The clicked wall (the PlacementHandlerGates shape): seeded at
        // the tick lane before the use gesture.
        net.zaminmc.torch.block.BlockPosition wall =
                new net.zaminmc.torch.block.BlockPosition(8, 5, 2);
        server.ticker().submit(() ->
                server.world().setBlock(wall, BuiltinBlocks.STONE));
        await(() -> server.world().getBlock(wall).equals(BuiltinBlocks.STONE),
                "the wall seeded");

        // Stone: placing() = dig.stone, volume (1.0 + 1) / 2 = 1.0,
        // pitch 1.0 * 0.8 = 0.8.
        hold(player, ItemStack.of(BuiltinItems.STONE, 8));
        placeAt(player, new net.zaminmc.torch.block.BlockPosition(8, 5, 2),
                new net.zaminmc.torch.block.BlockPosition(7, 5, 2));
        FxManager.Sound stonePlacement = awaitSound("dig.stone");
        assertNotNull(stonePlacement, "the stone placement played its family placing name");
        assertEquals(1.0f, stonePlacement.volume(), 1e-6);
        assertEquals(0.8f, stonePlacement.pitch(), 1e-6);

        // Glass: the placing override reads step.stone (never dig.glass).
        fxEvents.clear();
        server.ticker().submit(() -> server.world().setBlock(
                new net.zaminmc.torch.block.BlockPosition(8, 5, 3), BuiltinBlocks.STONE));
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(8, 5, 3))
                        .equals(BuiltinBlocks.STONE), "the second wall seeded");
        hold(player, ItemStack.of(BuiltinItems.GLASS, 8));
        placeAt(player, new net.zaminmc.torch.block.BlockPosition(8, 5, 3),
                new net.zaminmc.torch.block.BlockPosition(7, 5, 3));
        FxManager.Sound glassPlacement = awaitSound("step.stone");
        assertNotNull(glassPlacement,
                "the glass placement carried the GLASS family's step.stone placing name");

        server.shutdown(null);
        server = null;
    }

    /** Waits for one named sound on the FX bus (null when absent). */
    private FxManager.Sound awaitSound(String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            for (FxManager.FxEvent event : fxEvents) {
                if (event instanceof FxManager.Sound sound && sound.name().equals(name)) {
                    return sound;
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    /** Holds a stack on the tick lane. */
    private void hold(PlayerSession player, ItemStack stack) throws InterruptedException {
        server.ticker().submit(() -> {
            player.inventory().setSlot(0, stack);
            player.inventory().selectHotbarSlot(0);
        });
        await(() -> !player.inventory().held().isEmpty()
                        && player.inventory().held().type().equals(stack.type()),
                "the hand holds " + stack.type().identifier());
    }

    /** The placement gesture: stand in place, use against the wall's
     * west face, await the landing. */
    private void placeAt(PlayerSession player,
                         net.zaminmc.torch.block.BlockPosition wall,
                         net.zaminmc.torch.block.BlockPosition target)
            throws InterruptedException {
        server.ticker().submit(() -> player.applyMovement(
                new Position(2.5, 5.0, 2.5), net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> Math.abs(player.position().x() - 2.5) < 0.01,
                "the player stood in place");
        server.useItemOnBlock(player, wall,
                4, java.util.Optional.empty(), windowId -> { });
        await(() -> !server.world().getBlock(target)
                        .equals(server.world().airType()),
                "the block placed at " + target);
    }

    private PlayerSession join(String name) throws InterruptedException {
        List<String> sink = new ArrayList<>();
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
            @Override public void blockResync(net.zaminmc.torch.block.BlockPosition position,
                    net.zaminmc.torch.block.BlockType type) {
                sink.add(position + "=" + type.identifier());
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session =
                ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @org.junit.jupiter.api.AfterEach
    void stop() {
        if (server != null) {
            server.shutdown(null);
        }
    }
}
