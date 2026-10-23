package net.zaminmc.torch.server.sign;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE SIGN EDITOR'S OPENING TRIGGER (the audit's ML-003 row): the
 * reference's own law — ServerPlayerEntity.openSignEditor lines 547-550,
 * called from SignItem.useOn lines 51-54 the moment the placed sign block
 * lands — hands the editing screen to the PLACING player alone. The
 * engine's surface is the SignEditorListener (the protocol adapter's
 * 0x36 fan keys off it); these scenarios pin the engine side of that law
 * through the real use dispatch: the standing and the wall placement both
 * open the editor at the placed cell for the placer and for no other
 * session, the survival placement still consumes its item (the reference
 * order — place, consume, open), the client-claimed creative arm opens it
 * too, and a non-sign placement never does.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignEditorOpenAcceptanceTest {

    private EngineServer server;

    @TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "signedit", "it", 20, 4, 20,
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

    private void hold(PlayerSession player, ItemType type, int count) {
        player.inventory().setSlot(0, ItemStack.of(type, count));
        player.inventory().selectHotbarSlot(0);
    }

    /** Runs one right-click use on the tick thread (the empty claim form). */
    private void useOn(PlayerSession player, BlockPosition at, int face) {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
    }

    @Test
    void standingSignPlacementOpensTheEditorForThePlacerAlone() throws Exception {
        server = boot();
        List<Object[]> editorOpens = new CopyOnWriteArrayList<>();
        server.addSignEditorListener((editor, position) ->
                editorOpens.add(new Object[]{editor, position}));
        PlayerSession placer = join("Placer");
        PlayerSession onlooker = join("Onlooker");
        var world = server.world();
        BlockPosition floor = new BlockPosition(
                (int) Math.floor(placer.position().x()) + 2,
                (int) Math.floor(placer.position().y()) - 1,
                (int) Math.floor(placer.position().z()));
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");
        BlockPosition signCell = floor.offset(0, 1, 0);

        hold(placer, net.zaminmc.torch.server.item.BuiltinItems.SIGN, 1);
        useOn(placer, floor, 1);

        await(() -> SignManager.isSignType(world.getBlock(signCell)), "the sign block commits");
        await(() -> editorOpens.size() == 1, "the editor-open fan fired");
        assertEquals(placer, editorOpens.get(0)[0],
                "the editing screen belongs to the placing session");
        assertEquals(signCell, editorOpens.get(0)[1],
                "the editor names the placed sign's own cell");
        assertEquals(0, placer.inventory().held().count(),
                "the survival placement still consumed its item (the reference order)");
        assertEquals(1, editorOpens.size(),
                "no other session ever saw an editor open");
        server.shutdown(null);
    }

    @Test
    void wallSignPlacementOpensTheEditorAtTheHungCell() throws Exception {
        server = boot();
        List<Object[]> editorOpens = new CopyOnWriteArrayList<>();
        server.addSignEditorListener((editor, position) ->
                editorOpens.add(new Object[]{editor, position}));
        PlayerSession placer = join("Waller");
        var world = server.world();
        int gx = (int) Math.floor(placer.position().x()) + 4;
        int gz = (int) Math.floor(placer.position().z());
        int gy = (int) Math.floor(placer.position().y()) - 1;
        BlockPosition post = new BlockPosition(gx, gy + 1, gz);
        server.ticker().submit(() -> {
            world.setBlock(new BlockPosition(gx, gy, gz), BuiltinBlocks.STONE);
            world.setBlock(post, BuiltinBlocks.STONE);
        });
        await(() -> world.getBlock(post).equals(BuiltinBlocks.STONE), "post seeded");
        BlockPosition hungCell = new BlockPosition(gx, gy + 1, gz - 1);

        hold(placer, net.zaminmc.torch.server.item.BuiltinItems.SIGN, 1);
        useOn(placer, post, 2); // the north face: the wall sign hangs there

        await(() -> SignManager.isSignType(world.getBlock(hungCell)), "the wall sign commits");
        await(() -> editorOpens.size() == 1, "the editor-open fan fired");
        assertEquals(hungCell, editorOpens.get(0)[1],
                "the editor names the hung cell, not the clicked post");
        assertEquals(placer, editorOpens.get(0)[0], "the placer owns the editor");
        server.shutdown(null);
    }

    @Test
    void nonSignPlacementsNeverOpenTheEditor() throws Exception {
        server = boot();
        List<Object[]> editorOpens = new CopyOnWriteArrayList<>();
        server.addSignEditorListener((editor, position) ->
                editorOpens.add(new Object[]{editor, position}));
        PlayerSession placer = join("Mason");
        var world = server.world();
        BlockPosition floor = new BlockPosition(
                (int) Math.floor(placer.position().x()) + 2,
                (int) Math.floor(placer.position().y()) - 1,
                (int) Math.floor(placer.position().z()));
        BlockPosition top = floor.offset(0, 1, 0);

        // A plain stone placement commits the block and opens nothing.
        hold(placer, item("minecraft:stone"), 1);
        useOn(placer, floor, 1);
        await(() -> world.getBlock(top).equals(BuiltinBlocks.STONE), "the stone commits");
        // The tick-lane marker: queued behind the placement, so its release
        // proves the placement's own tick fully ran with no editor fan.
        CountDownLatch placementSettled = new CountDownLatch(1);
        server.ticker().submit(placementSettled::countDown);
        assertTrue(placementSettled.await(5, TimeUnit.SECONDS), "the tick lane drained");
        assertEquals(0, editorOpens.size(), "a stone placement never opens a sign editor");
        server.shutdown(null);
    }

    @Test
    void theClientClaimedPlacementOpensTheEditorToo() throws Exception {
        server = boot();
        List<Object[]> editorOpens = new CopyOnWriteArrayList<>();
        server.addSignEditorListener((editor, position) ->
                editorOpens.add(new Object[]{editor, position}));
        PlayerSession placer = join("Claimer");
        var world = server.world();
        BlockPosition floor = new BlockPosition(
                (int) Math.floor(placer.position().x()) + 2,
                (int) Math.floor(placer.position().y()) - 1,
                (int) Math.floor(placer.position().z()));
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");
        BlockPosition signCell = floor.offset(0, 1, 0);

        // The creative wire's client-claimed form: the claimed block rides
        // the use instead of the held stack (the reference's creative
        // SignItem.useOn opens the editor all the same).
        hold(placer, net.zaminmc.torch.server.item.BuiltinItems.SIGN, 1);
        server.useItemOnBlock(placer, floor, 1,
                java.util.Optional.of(BuiltinBlocks.SIGN_NORTH),
                windowId -> { });

        await(() -> SignManager.isSignType(world.getBlock(signCell)), "the claimed sign commits");
        await(() -> editorOpens.size() == 1, "the editor-open fan fired");
        assertEquals(placer, editorOpens.get(0)[0], "the claimer owns the editor");
        assertEquals(signCell, editorOpens.get(0)[1], "the editor names the claimed cell");
        assertEquals(1, placer.inventory().held().count(),
                "a client-claimed placement consumes nothing");
        server.shutdown(null);
    }

    private ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }
}
