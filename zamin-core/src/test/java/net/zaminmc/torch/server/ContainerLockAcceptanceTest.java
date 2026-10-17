package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 91 Wave C p3 — the containers' Lock row over the live engine
 * (ServerPlayerEntity.openChestMenu lines 573-582 +
 * PlayerEntity.canUnlockInventory lines 1688-1696): a locked container
 * without the named key answers the action-bar chat + the door-close
 * sound and never opens; the held item's own custom hover name is the
 * key; spectators pass every lock.
 */
class ContainerLockAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "clk" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** A link recording every chat component (the lock-out's action bar). */
    private static final class RecordingLink implements ClientLink {
        final List<String> chat = new CopyOnWriteArrayList<>();

        @Override public boolean isActive() { return true; }
        @Override public void kick(String reason) { }
        @Override public void sendChatComponent(String json, int position) {
            chat.add(position + ":" + json);
        }
    }

    private PlayerSession join(String name, Position at, ClientLink link) {
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    private BlockPosition placeChest() throws InterruptedException {
        BlockPosition at = new BlockPosition(2, 4, 2);
        server.ticker().submit(() ->
                server.world().setBlock(at, BuiltinBlocks.CHEST));
        await(() -> server.world().getBlock(at).equals(BuiltinBlocks.CHEST), "the chest placed");
        return at;
    }

    @Test
    void theLockRefusesTheOpenAndTheNamedKeyOpens() throws Exception {
        RecordingLink link = new RecordingLink();
        PlayerSession player = join("Keyholder", new Position(0.5, 5, 0.5), link);
        await(() -> player.state() == PlayerState.PLAYING, "session reaches PLAYING");
        BlockPosition chestAt = placeChest();

        // The lock set on the block entity (the /blockdata arm's own row).
        server.ticker().submit(() ->
                server.chests().getOrCreate(chestAt).setLockKey("Secret"));

        // A bare hand on the locked chest: NO window, the action-bar chat.
        AtomicInteger opened = new AtomicInteger();
        server.useItemOnBlock(player, chestAt, 1, Optional.empty(), id -> opened.incrementAndGet());
        await(() -> !link.chat.isEmpty(), "the lock-out chat arrived");
        assertTrue(link.chat.get(0).startsWith("2:"),
                "the lock-out rides the action bar (position 2): " + link.chat.get(0));
        assertTrue(link.chat.get(0).contains("container.isLocked"),
                "the reference's own translatable key: " + link.chat.get(0));
        assertEquals(0, opened.get(), "the locked chest never opened a window");
        assertEquals(net.zaminmc.torch.server.player.PlayerSession.ContainerKind.NONE,
                player.openContainerKind(), "no window state carried");

        // The named key in the hand: the SAME chest opens.
        server.ticker().submit(() -> player.inventory().setSlot(
                player.inventory().heldSlot(),
                new ItemStack(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:tripwire_hook"))
                        .orElseThrow(), 1, 0, "Secret")));
        int chatBefore = link.chat.size();
        server.useItemOnBlock(player, chestAt, 1, Optional.empty(), id -> opened.incrementAndGet());
        await(() -> opened.get() > 0, "the named key opened the window");
        assertEquals(net.zaminmc.torch.server.player.PlayerSession.ContainerKind.CHEST,
                player.openContainerKind(), "the chest window state landed");
        assertEquals(chatBefore, link.chat.size(), "the keyed open answers no lock-out chat");
    }

    @Test
    void theWrongNameStaysLockedAndTheEmptyLockOpensFreely() throws Exception {
        RecordingLink link = new RecordingLink();
        PlayerSession player = join("Wronghand", new Position(0.5, 5, 0.5), link);
        await(() -> player.state() == PlayerState.PLAYING, "session reaches PLAYING");
        BlockPosition chestAt = placeChest();

        // A named item that is NOT the key: still locked.
        server.ticker().submit(() -> {
            server.chests().getOrCreate(chestAt).setLockKey("Secret");
            player.inventory().setSlot(player.inventory().heldSlot(),
                    new ItemStack(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                            net.zaminmc.torch.util.Identifier.parse("minecraft:tripwire_hook"))
                            .orElseThrow(), 1, 0, "Not The Key"));
        });
        AtomicInteger opened = new AtomicInteger();
        server.useItemOnBlock(player, chestAt, 1, Optional.empty(), id -> opened.incrementAndGet());
        await(() -> !link.chat.isEmpty(), "the wrong name still lock-outs");
        assertEquals(0, opened.get(), "no window under the wrong name");

        // The empty lock (InventoryLock.NONE's own shape): every hand opens.
        server.ticker().submit(() ->
                server.chests().getOrCreate(chestAt).setLockKey(""));
        server.useItemOnBlock(player, chestAt, 1, Optional.empty(), id -> opened.incrementAndGet());
        await(() -> opened.get() > 0, "the empty lock opens freely");
        assertEquals(net.zaminmc.torch.server.player.PlayerSession.ContainerKind.CHEST,
                player.openContainerKind());
    }
}
