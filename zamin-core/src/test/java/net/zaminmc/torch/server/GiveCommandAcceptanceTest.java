package net.zaminmc.torch.server;

import net.zaminmc.torch.server.command.EngineCommandResults;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The vanilla give form (ML-008 resolved — GiveCommand 1:1): the
 * {@code <player> <item> [count] [data] [dataTag]} target shape, the
 * 1..64 count band, the pop sound heard by everyone EXCEPT the target
 * (the playSound(Entity except, ...) overload), the full-fit arm's
 * one-tick ghost (makeFakeItem), the remainder arm's drop with
 * noPickUpDelay + the owner window, and the op-2 gate.
 */
class GiveCommandAcceptanceTest {

    @TempDir
    Path dataDir;

    EngineServer server;
    PlayerSession alice;
    PlayerSession bob;
    final CopyOnWriteArrayList<String> aliceLines = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<String> bobLines = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<net.zaminmc.torch.server.fx.FxManager.SoundExcept> pops =
            new CopyOnWriteArrayList<>();

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "givecmd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        alice = join("Alice");
        bob = join("Bob");
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                        && bob.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both playing");
        server.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "Alice opped");
        // The system-chat listener (the give reply's path — the same bus
        // BlockEntityTagPlacementTest records).
        server.addChatListener(new net.zaminmc.torch.server.chat.ChatListener() {
            @Override public void onChatMessage(PlayerSession sender, String content) { }
            @Override public void onSystemMessage(PlayerSession recipient, String content) {
                if (recipient == alice) {
                    aliceLines.add(content);
                } else if (recipient == bob) {
                    bobLines.add(content);
                }
            }
        });
        // The engine-side FX capture: the pop's except-law (the wire arm
        // mirrors the Sound translation with the exempt observer skipped).
        server.fx().addListener(event -> {
            if (event instanceof net.zaminmc.torch.server.fx.FxManager.SoundExcept except) {
                pops.add(except);
            }
        });
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void updateSpawnPosition(int x, int y, int z) {
        }

        @Override
        public void playSound(String name, double x, double y, double z, float volume, float pitch) {
        }

        @Override
        public void worldParticles(int particleId, boolean longDistance, double x, double y, double z,
                                   float offsetX, float offsetY, float offsetZ,
                                   float particleData, int count, int[] params) {
        }
    }

    private PlayerSession join(String name) {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new RecordingLink(), name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private void await(java.util.function.BooleanSupplier condition, String what)
            throws InterruptedException {
        net.zaminmc.torch.testing.EngineAwaits.awaitCondition(
                server.world(), condition, 400, what);
    }

    @Test
    void theVanillaFormTargetsTheNamedPlayer() throws Exception {
        // The console-shaped arm through Alice's own dispatch: she grants
        // BOB a diamond stack by name — the first argument is the target,
        // the second the item, exactly the reference's arg order.
        server.chatService().submitChat(alice, "/give Bob minecraft:diamond 3");
        await(() -> bob.countOf(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 3,
                "the diamonds landed in Bob's inventory");
        assertTrue(alice.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 0,
                "Alice granted, never received");
        await(() -> aliceLines.stream().anyMatch(l -> l.contains("Given")
                        && l.contains("Bob")),
                "the success line names the target");
    }

    @Test
    void thePopSoundIsHeardByEveryoneExceptTheTarget() throws Exception {
        server.chatService().submitChat(alice, "/give Bob diamond 1");
        await(() -> bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 1,
                "the grant landed");
        await(() -> !pops.isEmpty(), "the pop event fired");
        assertEquals("random.pop", pops.get(0).name());
        assertEquals(0.2F, pops.get(0).volume());
        assertEquals(bob, pops.get(0).exempt(), "the target is the exempt session");
    }

    @Test
    void theFullFitDropsTheOneTickGhost() throws Exception {
        server.chatService().submitChat(alice, "/give Bob diamond 2");
        await(() -> bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 2,
                "the grant landed");
        // The reference's full-fit quirk: a ghost item drops anyway with
        // makeFakeItem — never-pick-up (delay 32767) + age 5999, so it
        // despawns on the very next item tick.
        AtomicReference<ItemEntity> ghost = new AtomicReference<>();
        await(() -> {
            for (ItemEntity e : server.itemEntities().all()) {
                if (e.stack().type() == net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) {
                    ghost.set(e);
                    return true;
                }
            }
            return false;
        }, "the ghost spawned");
        assertTrue(ghost.get().age() >= 5999,
                "the ghost rides the fake age (5999 at spawn, 6000 one tick later)");
        assertTrue(ghost.get().pickupDelayTicks() > 32000,
                "the ghost is never pickable (delay 32767)");
        await(() -> server.itemEntities().all().isEmpty(), "the ghost despawned");
        // The whole grant stayed in the inventory — the ghost was a wire
        // ghost, nothing more.
        assertTrue(bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 2,
                "the grant is intact");
    }

    @Test
    void theRemainderDropsWithNoPickUpDelayAndTheOwnerWindow() throws Exception {
        // Fill Bob's inventory first: 36 storage slots of dirt (one from
        // the command, the rest through the API's own give).
        server.chatService().submitChat(alice, "/give Bob dirt 64");
        await(() -> bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIRT) == 64,
                "the dirt stack landed");
        for (int i = 0; i < 35; i++) {
            server.ticker().submit(() -> bob.inventory().pickUp(
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.DIRT, 64)));
        }
        await(() -> bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIRT) == 36 * 64,
                "the inventory is full");
        // Now a grant that cannot fit a single unit: everything drops.
        server.chatService().submitChat(alice, "/give Bob diamond 5");
        // THE DROP IS PICKED BY ITS OWN SHAPE, never by list index (run
        // 590's member, the SurvivalFlow family's own documented race):
        // the engine's live list can gain OTHER entities concurrently —
        // a natural drop or a leaf-decay pop between the await and the
        // read — and an index-based get(0) then reads the wrong body
        // (the observed failure: a natural drop's nonzero pickup delay
        // answering the noPickUpDelay pin).
        await(() -> server.itemEntities().all().stream().anyMatch(
                item -> item.stack().type()
                        .equals(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND)),
                "the remainder dropped");
        ItemEntity dropped = server.itemEntities().all().stream().filter(
                        item -> item.stack().type()
                                .equals(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND))
                .findFirst().orElseThrow();
        assertEquals(0, dropped.pickupDelayTicks(), "noPickUpDelay: instantly collectable");
        assertEquals("Bob", dropped.owner(), "the owner window stamps the target");
        assertEquals(5, dropped.stack().count(), "the whole stack dropped");
        assertTrue(bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 0,
                "nothing fit");
    }

    @Test
    void theCountBandAndTheUnknownRefusals() throws Exception {
        // The reference's parseInt(args[2], 1, 64) — the band is the
        // command's own, never the stack size.
        server.chatService().submitChat(alice, "/give Bob diamond 0");
        await(() -> aliceLines.stream().anyMatch(l -> l.contains("at least 1")),
                "the zero count refuses");
        server.chatService().submitChat(alice, "/give Bob diamond 65");
        await(() -> aliceLines.stream().anyMatch(l -> l.contains("at most 64")),
                "the over-band count refuses");
        server.chatService().submitChat(alice, "/give Bob not_an_item 1");
        await(() -> aliceLines.stream().anyMatch(l -> l.contains("Unknown item")),
                "the unknown item refuses");
        server.chatService().submitChat(alice, "/give Nobody diamond 1");
        await(() -> aliceLines.stream().anyMatch(l -> l.contains("could not be found")),
                "the unknown player refuses");
        assertTrue(bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 0,
                "nothing landed through the refusals");
    }

    @Test
    void theOpLevelGate() throws Exception {
        // Bob (level 0) is refused the op-2 verb — the same unknown-command
        // refusal every gated verb answers, never a reveal.
        server.chatService().submitChat(bob, "/give Bob diamond 1");
        await(() -> bobLines.stream().anyMatch(
                        l -> l.contains("Unknown command")),
                "the refusal line");
        assertTrue(bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 0,
                "nothing granted");
    }

    @Test
    void theConsoleGrantsBySelectorAndUuidLadder() throws Exception {
        // The bare-name arm with the default count (1) and the data arm —
        // the granted stack lands in the held slot (the empty inventory's
        // first), carrying the parsed data.
        server.chatService().submitChat(alice, "/give Bob cooked_fish 1 1");
        await(() -> bob.countOf(
                        net.zaminmc.torch.server.item.BuiltinItems.COOKED_FISH) == 1
                        && bob.inventory().held().damage() == 1,
                "the data arm landed on the held stack");
        // The selector arm: @p resolves through the sender's own view —
        // Alice selecting nearest is herself.
        server.chatService().submitChat(alice, "/give @p diamond 2");
        await(() -> alice.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 2,
                "the selector arm granted");
        // The uuid arm of the parsePlayer ladder.
        server.chatService().submitChat(alice,
                "/give " + bob.uuid() + " diamond 1");
        await(() -> bob.countOf(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND) == 1,
                "the uuid arm granted");
    }
}
