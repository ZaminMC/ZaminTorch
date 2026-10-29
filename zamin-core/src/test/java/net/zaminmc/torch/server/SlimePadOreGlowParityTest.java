package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slime pad trio + the redstone ore's step glow (the fall-path
 * family's honest remainder, the Wave TR row's demand-named tail):
 *
 * <p>THE PAD (SlimeBlock lines 27-51 over Entity.move's three dispatch
 * sites — onFallenOn 878-880, beforeCollision 588-590 over Block
 * 882-885, onSteppedOn 607-609): the landing pays the ZERO multiplier
 * ({@code takeFallDamage(fallDistance, 0.0F)} — a sneaking body alone
 * keeps the super's 1.0), the vertical clip FLIPS a falling body's
 * negative velocityY back positive (the bounce — the velocityY >= 0
 * NO-OP quirk kept 1:1), and the step arm DAMPS the horizontal pair at
 * {@code 0.4 + |velocityY| * 0.2} while the body stands (the sticky
 * floor — the |velocityY| &lt; 0.1 gate reading the bounce train's
 * standing oscillation). THE GLOW (RedstoneOreBlock.onSteppedOn lines
 * 39-43 over interact 52-57): a body stepping on the PLAIN ore lights
 * it — the mob path through the pending intent (the trample's own
 * shape), the player path through the per-tick standing consult (the
 * reference's own arm rides the verification move,
 * ServerPlayNetworkHandler line 316; the engine's proposal-driven walk
 * has no verification move — the idempotent swap's observable outcome
 * is the same), the sneaking player skipped (the step arm's
 * {@code !flag} gate). THE SILK REPAIR (RedstoneOreBlock
 * .getSilkTouchDrop lines 95-97): the explicit override returns the
 * ORE ITSELF for BOTH forms — the dig-start swap means the LIT form is
 * the one a silk dig actually breaks, and the pre-fix arm's empty
 * stack starved every silk harvest.</p>
 */
class SlimePadOreGlowParityTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The mob path (the solver-backed stub — the StepSoundFamilyTest shape)
    // ------------------------------------------------------------------

    @Test
    void thePadSwallowsTheMobFallAndBouncesIt() {
        PadStubWorld world = new PadStubWorld();
        world.slimeFloor();
        // A cow falling from six blocks onto the pad: the landing arm
        // pays the ZERO multiplier (no wound) and the vertical clip
        // FLIPS the impact velocity back positive (the bounce).
        MobEntity cow = new MobEntity(7, MobType.COW,
                new Position(5.5, 70.0, 5.5), new Random(11), world);
        cow.setVelocity(0.0, -1.0, 0.0);
        boolean bounced = false;
        for (int i = 0; i < 400; i++) {
            cow.tick(false);
            if (cow.onGround() && cow.velocityY() > 0.0) {
                bounced = true;
                break;
            }
        }
        assertTrue(bounced, "the landing tick flipped the impact velocity (the bounce)");
        assertTrue(cow.velocityY() > 0.3,
                "the flip is the FULL impact speed, not a remainder: "
                        + cow.velocityY());
        assertEquals(10.0f, cow.health(), 0.0001f,
                "the pad's zero multiplier swallowed the six-block fall"
                        + " (the cow's own ten-heart hull unchanged —"
                        + " takeFallDamage's ceil((distance - 3) * 0.0) never wounds)");
    }

    @Test
    void theStoneLandingStillWoundsAndZeroes() {
        PadStubWorld world = new PadStubWorld();
        world.stoneFloor();
        // The control: the same fall onto stone wounds the body and the
        // default beforeCollision zeroes the vertical.
        MobEntity cow = new MobEntity(7, MobType.COW,
                new Position(5.5, 70.0, 5.5), new Random(11), world);
        cow.setVelocity(0.0, -1.0, 0.0);
        boolean landed = false;
        for (int i = 0; i < 400; i++) {
            cow.tick(false);
            if (cow.onGround()) {
                landed = true;
                break;
            }
        }
        assertTrue(landed, "the cow landed on stone");
        assertEquals(0.0, cow.velocityY(), 1e-9,
                "the default beforeCollision zeroes the vertical");
        assertTrue(cow.health() < 10.0f,
                "the stone landing wounds the ten-heart hull (the 1.0"
                        + " multiplier): " + cow.health());
    }

    @Test
    void theStickyFloorDampsTheWalker() {
        // The boost (SlimeBlock.onSteppedOn lines 43-51): a standing
        // body's horizontal pair scaled by 0.4 + |velocityY| * 0.2 —
        // over the engine's own 0.6 ground damping (the tick's move
        // carries the pre-damping snapshot, the StepSoundFamilyTest's
        // own pinned cadence), one tick from velocityX 2.0 lands in
        // (1.2 * 0.4, 1.2 * 0.42) = (0.48, 0.504).
        PadStubWorld slime = new PadStubWorld();
        slime.slimeFloor();
        MobEntity onSlime = new MobEntity(7, MobType.COW,
                new Position(5.5, 64.0, 5.5), new Random(11), slime);
        onSlime.setOnGround(true);
        onSlime.setVelocity(2.0, 0.0, 0.0);
        onSlime.tick(false);
        double damped = onSlime.velocityX();
        assertTrue(damped > 0.47 && damped < 0.51,
                "the sticky floor's own 0.4-band damping on top of the"
                        + " ground friction: " + damped);

        // The control: the stone floor's tick answers the damping alone
        // (2.0 * 0.6 = 1.2 — no step arm touches it).
        PadStubWorld stone = new PadStubWorld();
        stone.stoneFloor();
        MobEntity onStone = new MobEntity(7, MobType.COW,
                new Position(5.5, 64.0, 5.5), new Random(11), stone);
        onStone.setOnGround(true);
        onStone.setVelocity(2.0, 0.0, 0.0);
        onStone.tick(false);
        assertEquals(1.2, onStone.velocityX(), 1e-9,
                "the stone control keeps the plain ground damping alone");
    }

    // ------------------------------------------------------------------
    // The redstone step glow (the full server — the pending intent)
    // ------------------------------------------------------------------

    @Test
    void theMobStepLightsTheOre() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> {
                world.setBlock(new BlockPosition(0, 4, 0),
                        BuiltinBlocks.REDSTONE_ORE);
            });
            await(() -> world.getBlock(new BlockPosition(0, 4, 0))
                    .equals(BuiltinBlocks.REDSTONE_ORE), "the ore seeded");

            // A zombie falls onto the plain ore: the landing tick's step
            // walk reads the -0.2 feet cell (the ore), the pending
            // intent carries the swap, the manager's listener writes
            // the LIT form.
            var zombie = server.mobs().spawnAt(MobType.ZOMBIE,
                    new Position(0.5, 12.0, 0.5));
            assertNotNull(zombie, "the zombie spawned");
            await(() -> world.getBlock(new BlockPosition(0, 4, 0))
                            .equals(BuiltinBlocks.LIT_REDSTONE_ORE),
                    "the mob's step lit the ore through the pending intent");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePlayerStepLightsTheOreAndTheSneakSkips() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> {
                world.setBlock(new BlockPosition(4, 4, 0),
                        BuiltinBlocks.REDSTONE_ORE);
                world.setBlock(new BlockPosition(8, 4, 0),
                        BuiltinBlocks.REDSTONE_ORE);
            });
            await(() -> world.getBlock(new BlockPosition(4, 4, 0))
                            .equals(BuiltinBlocks.REDSTONE_ORE)
                    && world.getBlock(new BlockPosition(8, 4, 0))
                    .equals(BuiltinBlocks.REDSTONE_ORE),
                    "the two ores seeded");

            // The standing consult: a non-sneaking on-ground body lights
            // the ore underfoot (the reference's own arm rides the
            // verification move; the engine's per-tick consult is the
            // idempotent observable twin).
            PlayerSession walker = join(server, "Walker");
            server.teleportPlayer(walker, new Position(4.5, 5.0, 0.5));
            await(() -> walker.position().x() == 4.5
                    && walker.position().y() == 5.0, "the walker stood");
            server.movementProposal(walker, new Position(4.5, 5.0, 0.5),
                    Rotation.ZERO, true);
            await(() -> world.getBlock(new BlockPosition(4, 4, 0))
                            .equals(BuiltinBlocks.LIT_REDSTONE_ORE),
                    "the player's step lit the ore");

            // The sneak gate (Entity.move's !flag skip — the step arm
            // never runs for a sneaking body): the second ore stays
            // plain under a sneaking walker.
            PlayerSession sneaker = join(server, "Sneaker");
            server.teleportPlayer(sneaker, new Position(8.5, 5.0, 0.5));
            await(() -> sneaker.position().x() == 8.5
                    && sneaker.position().y() == 5.0, "the sneaker stood");
            server.ticker().submit(() -> sneaker.setSneaking(true));
            await(() -> sneaker.sneaking(), "the sneak flag set");
            server.movementProposal(sneaker, new Position(8.5, 5.0, 0.5),
                    Rotation.ZERO, true);
            Thread.sleep(400);
            assertEquals(BuiltinBlocks.REDSTONE_ORE,
                    world.getBlock(new BlockPosition(8, 4, 0)),
                    "the sneaking body never lights the ore (the step arm's !flag skip)");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------
    // The player's pad landing (the full server — the tickLanding arm)
    // ------------------------------------------------------------------

    @Test
    void thePlayerLandsSoftOnThePadUnlessSneaking() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> {
                world.setBlock(new BlockPosition(0, 4, 0),
                        BuiltinBlocks.SLIME_BLOCK);
                world.setBlock(new BlockPosition(4, 4, 0),
                        BuiltinBlocks.SLIME_BLOCK);
            });
            await(() -> world.getBlock(new BlockPosition(0, 4, 0))
                            .equals(BuiltinBlocks.SLIME_BLOCK)
                    && world.getBlock(new BlockPosition(4, 4, 0))
                    .equals(BuiltinBlocks.SLIME_BLOCK),
                    "the two pads seeded");

            // A twelve-block fall onto the pad: the ZERO multiplier —
            // the wound's ceil((12 - 3) * 0.0) never passes, the body
            // keeps all twenty hearts (the stone control's identical
            // fall would take nine).
            PlayerSession faller = join(server, "Faller");
            server.teleportPlayer(faller, new Position(0.5, 17.0, 0.5));
            await(() -> faller.position().y() == 17.0, "the teleport landed");
            server.movementProposal(faller, new Position(0.5, 13.0, 0.5),
                    Rotation.ZERO, false);
            server.movementProposal(faller, new Position(0.5, 9.0, 0.5),
                    Rotation.ZERO, false);
            server.movementProposal(faller, new Position(0.5, 5.0, 0.5),
                    Rotation.ZERO, true);
            await(() -> faller.position().y() == 5.0, "the fall landed");
            Thread.sleep(200);
            assertEquals(20.0f, faller.health(), 0.0001f,
                    "the pad's zero multiplier swallowed the twelve-block fall");

            // The sneak arm (SlimeBlock.onFallenOn lines 28-30): a
            // SNEAKING body takes the super's full walk — the same
            // fall pays ceil(12 - 3) = 9 of the twenty hearts.
            PlayerSession sneaker = join(server, "SoftSneaker");
            server.ticker().submit(() -> sneaker.setSneaking(true));
            await(() -> sneaker.sneaking(), "the sneak flag set");
            server.teleportPlayer(sneaker, new Position(4.5, 17.0, 0.5));
            await(() -> sneaker.position().y() == 17.0, "the sneaker's teleport landed");
            server.movementProposal(sneaker, new Position(4.5, 13.0, 0.5),
                    Rotation.ZERO, false);
            server.movementProposal(sneaker, new Position(4.5, 9.0, 0.5),
                    Rotation.ZERO, false);
            server.movementProposal(sneaker, new Position(4.5, 5.0, 0.5),
                    Rotation.ZERO, true);
            await(() -> sneaker.position().y() == 5.0, "the sneaker's fall landed");
            Thread.sleep(200);
            assertEquals(11.0f, sneaker.health(), 0.0001f,
                    "the sneaking body takes the super's FULL walk"
                            + " (ceil(12 - 3) = 9 of the twenty hearts)");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------
    // The silk repair (the drop walk — the LIT form's own override)
    // ------------------------------------------------------------------

    @Test
    void theSilkDigOnTheLitOreDropsTheOreItself() {
        DropService drops = new DropService(new Random());
        BlockType lit = block("minecraft:lit_redstone_ore");
        // RedstoneOreBlock.getSilkTouchDrop lines 95-97: the EXPLICIT
        // override returns the ore itself for BOTH forms — the dig-start
        // swap means the LIT form is the one a silk dig actually breaks.
        List<net.zaminmc.torch.item.ItemStack> silk =
                drops.dropsFor(lit, BuiltinItems.IRON_PICKAXE, true, 0);
        assertEquals(1, silk.size(), "the silk harvest answers exactly one stack");
        assertEquals(item("minecraft:redstone_ore"), silk.get(0).type(),
                "the LIT form's silk drop is the ORE itself (the explicit"
                        + " override), never an empty stack");
        assertEquals(1, silk.get(0).count());

        // The bare-hand walk unchanged: the dust at 4 + nextInt(2) +
        // nextInt(fortune + 1), never the block item.
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 500; i++) {
            List<net.zaminmc.torch.item.ItemStack> yield =
                    drops.dropsFor(lit, BuiltinItems.IRON_PICKAXE);
            assertEquals(1, yield.size());
            assertEquals(item("minecraft:redstone"), yield.get(0).type());
            min = Math.min(min, yield.get(0).count());
            max = Math.max(max, yield.get(0).count());
        }
        assertEquals(4, min, "the dust base starts at 4");
        assertEquals(5, max, "the dust base tops at 4 + 1");
    }

    // ------------------------------------------------------------------
    // The vehicle bounce (the full server — the cart's own Entity.move)
    // ------------------------------------------------------------------

    @Test
    void theCartBouncesOnThePad() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> world.setBlock(
                    new BlockPosition(0, 40, 0), BuiltinBlocks.SLIME_BLOCK));
            await(() -> world.getBlock(new BlockPosition(0, 40, 0))
                    .equals(BuiltinBlocks.SLIME_BLOCK), "the pad seeded");

            // A cart dropped from six blocks onto the pad: the vertical
            // clip flips the impact velocity and the cart rises again
            // (the bounce — the vehicles run the same Entity.move). The
            // landing first (the rest position 41.35 — the cart's own
            // half-height over the pad's top), THEN the rise above it:
            // the pre-fix body rests at 41.35 with a zeroed vertical
            // forever, the flip carries it back up past 41.8.
            AtomicReference<VehicleEntity> cart = new AtomicReference<>();
            server.ticker().submit(() -> cart.set(
                    server.vehicles().spawnMinecart(
                            new Position(0.5, 46.0, 0.5), 0)));
            await(() -> cart.get() != null, "the cart spawned");
            await(() -> cart.get().onGround() || cart.get().position().y() < 41.4,
                    "the cart reached the pad");
            await(() -> cart.get().position().y() > 41.8,
                    "the cart bounced off the pad (the flipped velocity"
                            + " carried it above the rest position)");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------ harness

    /**
     * The solver-backed stub world (the StepSoundFamilyTest shape): the
     * pad trio's own reads — the block identity at a cell, the solid
     * consult, and the collision surface.
     */
    private static class PadStubWorld implements MobEntity.WorldQuery {
        final Map<Long, BlockType> cells = new HashMap<>();

        void put(int x, int y, int z, BlockType type) {
            cells.put(key(x, y, z), type);
        }

        static long key(int x, int y, int z) {
            return ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL);
        }

        BlockType read(int x, int y, int z) {
            if (y == 0) {
                return BuiltinBlocks.BEDROCK;
            }
            BlockType cell = cells.get(key(x, y, z));
            return cell != null ? cell : BuiltinBlocks.AIR;
        }

        /** The slime floor: cells at y=63 — the top at 64.0. */
        void slimeFloor() {
            for (int x = -100; x <= 100; x++) {
                for (int z = -100; z <= 100; z++) {
                    put(x, 63, z, BuiltinBlocks.SLIME_BLOCK);
                }
            }
        }

        /** The stone control floor. */
        void stoneFloor() {
            for (int x = -100; x <= 100; x++) {
                for (int z = -100; z <= 100; z++) {
                    put(x, 63, z, BuiltinBlocks.STONE);
                }
            }
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(
                    (int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.floor(z)));
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            return read(x, y, z).identifier();
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return false;
        }

        private final net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                new net.zaminmc.torch.server.physics.EntityCollisionWorld() {
                    @Override
                    public BlockType blockAt(int x, int y, int z) {
                        return read(x, y, z);
                    }

                    @Override
                    public boolean chunkLoaded(int x, int z) {
                        return true;
                    }
                };

        @Override
        public net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    private static BlockType block(String id) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze()
                .lookup(Identifier.parse(id)).orElseThrow();
    }

    private static ItemType item(String id) {
        return BuiltinItems.lookup(Identifier.parse(id)).orElseThrow();
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "slimeglow",
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        server.ticker().submit(() -> session.setOpLevel(2));
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timeout: " + description);
            }
            Thread.sleep(25);
        }
    }
}
