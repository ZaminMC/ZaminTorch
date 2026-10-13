package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.cauldron.CauldronBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cauldron family (Slice 23i — the reference port of
 * reference/1.8.8 block/CauldronBlock.java): the four level states
 * (legacy 118, the LEVEL property on the nibble), the block/item
 * registration with the 2.0 hardness and the item drop, the bucket
 * fill (level 3, the bucket empties), the bottle drain (the water
 * potion + one level), the rain fill (the precipitation walk's third
 * arm — the 1-in-20 roll under the rainy-biome gate), and the
 * burning-body douse that spends one level.
 */
class CauldronAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "cau" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    @Test
    void theRegistryCarriesTheFamily() {
        // The four states resolve through the block registry, the level
        // round-trips, and the item exists beside the block.
        var zero = CauldronBlocks.of(0);
        var three = CauldronBlocks.of(3);
        assertEquals("minecraft:cauldron", zero.identifier().toString());
        assertEquals("minecraft:cauldron_level3", three.identifier().toString());
        assertEquals(3, CauldronBlocks.levelOf(three));
        assertEquals(0, CauldronBlocks.levelOf(zero));
        assertTrue(CauldronBlocks.isCauldron(zero));
        assertTrue(CauldronBlocks.isCauldron(three));
        // The clamped band: the of() clamps, levelOf never leaves 0..3.
        assertEquals("minecraft:cauldron_level3", CauldronBlocks.of(7).identifier().toString());
        // The item registry answers the cauldron item (the /give path).
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:cauldron")).isPresent(),
                "the cauldron item registers");
    }

    @Test
    void theBucketFillsAndTheBottleDrains() throws Exception {
        BlockPosition at = new BlockPosition(20, 40, 20);
        server.ticker().submit(() ->
                server.world().setBlock(at, CauldronBlocks.of(0)));
        await(() -> CauldronBlocks.isCauldron(server.world().getBlock(at)),
                "the empty cauldron stands");

        PlayerSession farmer = join("Farmer", new Position(20.5, 40.0, 21.5));

        // The water bucket fills to 3; survival swaps for the empty bucket.
        server.ticker().submit(() -> {
            farmer.inventory().setSlot(0, ItemStack.of(BuiltinItems.WATER_BUCKET, 1));
            farmer.inventory().selectHotbarSlot(0);
        });
        await(() -> !farmer.inventory().held().isEmpty(), "the bucket held");
        server.useItemOnBlock(farmer, at, 1, Optional.empty(), windowId -> { });
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == 3,
                "the bucket filled the cauldron to 3");
        await(() -> farmer.inventory().held().type().equals(BuiltinItems.BUCKET),
                "the bucket emptied in the hand");

        // A second bucket at level 3: consumed, nothing changes.
        server.ticker().submit(() ->
                farmer.inventory().setSlot(0, ItemStack.of(BuiltinItems.WATER_BUCKET, 1)));
        await(() -> farmer.inventory().held().type().equals(BuiltinItems.WATER_BUCKET),
                "the second bucket held");
        server.useItemOnBlock(farmer, at, 1, Optional.empty(), windowId -> { });
        Thread.sleep(300);
        assertEquals(3, CauldronBlocks.levelOf(server.world().getBlock(at)),
                "the full cauldron stays 3");
        assertEquals(BuiltinItems.WATER_BUCKET, farmer.inventory().held().type(),
                "the full cauldron never takes the bucket");

        // The glass bottle drains one level into the water potion (metadata
        // 0); the bottle pays one unit.
        server.ticker().submit(() ->
                farmer.inventory().setSlot(1, ItemStack.of(BuiltinItems.GLASS_BOTTLE, 2)));
        await(() -> !farmer.inventory().held().isEmpty(), "the bottle armed");
        server.ticker().submit(() -> farmer.inventory().selectHotbarSlot(1));
        await(() -> farmer.inventory().held().type().equals(BuiltinItems.GLASS_BOTTLE),
                "the bottle held");
        server.useItemOnBlock(farmer, at, 1, Optional.empty(), windowId -> { });
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == 2,
                "the bottle drained one level");
        boolean hasPotion = false;
        for (int slot = 0; slot < 36; slot++) {
            var stack = farmer.inventory().slot(slot);
            if (!stack.isEmpty() && stack.type().equals(BuiltinItems.POTION)
                    && stack.damage() == 0) {
                hasPotion = true;
            }
        }
        assertTrue(hasPotion, "the water potion (metadata 0) landed in the inventory");
    }

    @Test
    void theRainFillsTheOpenCauldronUnderTheRainyBiome() throws Exception {
        // A 16x16 cauldron carpet at the spawn chunk's surface band: every
        // precipitation pick in this chunk lands on a cauldron whatever
        // the in-chunk draw.
        int baseX = 0;
        int baseZ = 0;
        int y = 40;
        server.ticker().submit(() -> {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    server.world().setBlock(new BlockPosition(baseX + dx, y, baseZ + dz),
                            CauldronBlocks.of(0));
                }
            }
        });
        await(() -> CauldronBlocks.isCauldron(
                        server.world().getBlock(new BlockPosition(baseX, y, baseZ))),
                "the cauldron carpet stands");

        // Force the rain (the console's /weather rain 60 — the
        // WeatherSystemTest's own pattern).
        var console = new net.zaminmc.torch.server.chat.CommandSender() {
            @Override public String name() { return "Console"; }
            @Override public void sendMessage(String message) { }
            @Override public int opLevel() { return 4; }
            @Override public PlayerSession player() { return null; };
        };
        server.chatService().dispatchCommand(console, "/weather rain 60");
        await(() -> server.raining(), "the rain flag rose");

        // The fill: any cauldron in the carpet climbs above level 0 (the
        // 1-in-16 precipitation roll x the 1-in-20 fill roll — a generous
        // window: 256 candidate columns, ~20 tps, the expectation reaches
        // several fills inside a minute).
        await(() -> {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    if (CauldronBlocks.levelOf(server.world().getBlock(
                            new BlockPosition(baseX + dx, y, baseZ + dz))) > 0) {
                        return true;
                    }
                }
            }
            return false;
        }, "the rain filled at least one cauldron");

        // The cap: no cauldron ever exceeds level 3.
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int level = CauldronBlocks.levelOf(server.world().getBlock(
                        new BlockPosition(baseX + dx, y, baseZ + dz)));
                assertTrue(level <= 3, "the level stays inside the reference band");
            }
        }
    }

    @Test
    void theBurningBodyDousesAndSpendsTheLevel() throws Exception {
        BlockPosition at = new BlockPosition(30, 40, 30);
        server.ticker().submit(() ->
                server.world().setBlock(at, CauldronBlocks.of(2)));
        await(() -> CauldronBlocks.isCauldron(server.world().getBlock(at)),
                "the half-full cauldron stands");

        PlayerSession walker = join("Walker", new Position(30.5, 40.0, 30.5));
        // Ignite the body, then place it inside the cauldron cell.
        server.ticker().submit(() -> walker.ignite(200));
        await(() -> walker.burning(), "the walker burns");
        server.ticker().submit(() -> walker.applyMovement(
                new Position(30.5, 40.3, 30.5), net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> !walker.burning(), "the cauldron doused the burning body");
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == 1,
                "the douse spent one level");
    }
}
