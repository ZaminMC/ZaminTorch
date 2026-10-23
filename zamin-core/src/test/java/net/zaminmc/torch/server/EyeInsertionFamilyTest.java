package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * THE EYE-INSERTION FAMILY'S OWN COMPLETION (the import-families slice's
 * demand-named rows, retired against the reference 1:1): the frame's eye
 * insertion carries the reference's own comparator rows — the
 * {@code updateNeighborComparators(pos, END_PORTAL_FRAME)} wake
 * (EnderEyeItem.useOn line 33 → World lines 2701-2716: every comparator
 * reading the frame re-arms, directly or through one solid block) and the
 * frame's own analog band (EndPortalFrameBlock lines 61-68: every frame
 * state is an analog source, {@code eye ? 15 : 0}) — and the firework
 * rocket's payload is THE HELD STACK ITSELF (the reference's ctor line
 * 63), the Fireworks compound riding it whole: the Flight byte names the
 * fuse band ({@code i = 1 + Flight}, ctor line 39), the Explosions list
 * rides the synced slot-8 item.
 */
class EyeInsertionFamilyTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession player;

    @AfterEach
    void stop() {
        if (server != null) {
            server.shutdown(null);
        }
    }

    private void boot(GameMode mode) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "eyefam" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                mode, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    private PlayerSession join(String name) {
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
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
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

    private void standAt(Position at) throws InterruptedException {
        server.ticker().submit(() ->
                player.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> Math.abs(player.position().x() - at.x()) < 0.01
                        && Math.abs(player.position().z() - at.z()) < 0.01,
                "the player stood at " + at);
        EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
    }

    private void hold(ItemStack stack) throws InterruptedException {
        server.ticker().submit(() -> {
            player.inventory().setSlot(0, stack);
            player.inventory().selectHotbarSlot(0);
        });
        await(() -> !player.inventory().held().isEmpty()
                        && player.inventory().held().type().equals(stack.type()),
                "the hand holds " + stack.type().identifier());
    }

    private void use(BlockPosition at, int face) {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(), windowId -> { });
    }

    private static int wirePower(EngineServer engine, int x, int y, int z) {
        AtomicReference<net.zaminmc.torch.block.BlockType> result = new AtomicReference<>();
        engine.ticker().submit(() -> result.set(
                engine.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        var type = result.get();
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

    /**
     * THE COMPARATOR WAKE + THE FRAME'S OWN ANALOG BAND (EnderEyeItem.useOn
     * line 33 + EndPortalFrameBlock lines 61-68, 1:1): a comparator facing
     * an EMPTY frame reads 0 (the frame IS an analog source — never the
     * diode read-through), and the eye's insertion wakes it: the frame
     * flips to the eyed pair (analog 15) and the wire at the comparator's
     * output climbs to 15 through the wake's own 2-tick reaction.
     */
    @Test
    void theFrameInsertionWakesTheComparatorAndFlipsTheSignal() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Angler");
        int x = 10, z = 10, y = 5;
        // The empty frame, the comparator facing it (input side west =
        // the frame), the wire at the output, and the beds under both.
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.END_PORTAL_FRAME_E);
            server.world().setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x + 1, y, z),
                    RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
            server.world().setBlock(new BlockPosition(x + 2, y, z), RedstoneBlocks.wireOfPower(0));
        });
        await(() -> RedstoneBlocks.isComparator(server.world()
                .getBlock(new BlockPosition(x + 1, y, z))), "the comparator placed");
        // The EMPTY frame's own band: the analog read answers 0 (the
        // frame is an analog source — the comparator stays silent, never
        // the diode read-through behind it).
        await(() -> wirePower(server, x + 2, y, z) == 0,
                "the empty frame keeps the comparator silent (analog 0)");
        // The insertion: stand close, hold the eye, use it on the frame.
        standAt(new Position(x + 0.5, y, z - 2.5));
        hold(ItemStack.of(BuiltinItems.ENDER_EYE, 2));
        use(new BlockPosition(x, y, z), 1);
        // The flip: the frame takes the eye (the eyed pair), the wake
        // re-arms the comparator, the analog 15 lands on the wire.
        await(() -> server.world().getBlock(new BlockPosition(x, y, z))
                        .equals(BuiltinBlocks.END_PORTAL_FRAME_EYE_E),
                "the frame took the eye");
        await(() -> wirePower(server, x + 2, y, z) == 15,
                "the eyed frame's analog 15 woke the comparator (the wire reads 15)");
        await(() -> player.inventory().held().count() == 1,
                "the insertion paid one unit in survival");
    }

    /**
     * THE HELD STACK IS THE PAYLOAD (the reference's ctor line 63 —
     * {@code syncedData.update(8, item)}): the Fireworks compound rides
     * the rocket whole — the Flight byte names the fuse band
     * ({@code 10 * (1 + Flight) + rand(6) + rand(7)}, ctor lines 33-46),
     * the Explosions list rides the synced slot-8 item to the client's
     * own burst render.
     */
    @Test
    void theHeldFireworksCompoundReachesTheRocketAndNamesTheFuse() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Pyro");
        // A Fireworks compound: Flight 3 (the fuse band 40..51) with one
        // explosion row the client's burst reads.
        ItemStack rocket = ItemStack.of(BuiltinItems.FIREWORKS, 2)
                .withFireworks(Map.of(
                        "Flight", (byte) 3,
                        "Explosions", List.of(Map.of(
                                "Type", (byte) 0,
                                "Colors", new int[] {11743532}))));
        standAt(new Position(2.5, 5.0, 2.5));
        hold(rocket);
        use(new BlockPosition(8, 5, 2), 1);
        await(() -> server.fireworks() != null && !server.fireworks().all().isEmpty(),
                "the rocket launched");
        var launched = server.fireworks().all().get(0);
        assertNotNull(launched.payload().fireworks(),
                "the payload carries the held stack's own Fireworks compound");
        assertEquals((byte) 3, launched.payload().fireworks().get("Flight"),
                "the Flight byte rides the payload whole");
        assertTrue(launched.maxAge() >= 40 && launched.maxAge() <= 51,
                "the Flight 3 names the fuse band 10*4 + rand(6) + rand(7) = 40..51: "
                        + launched.maxAge());
        // The unit payment in survival.
        await(() -> player.inventory().held().count() == 1,
                "the launch paid one unit in survival");
    }
}
