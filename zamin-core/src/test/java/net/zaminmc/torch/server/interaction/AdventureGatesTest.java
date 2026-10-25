package net.zaminmc.torch.server.interaction;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * Wave I — the adventure gates over the live engine (PlayerEntity's
 * canUseItemOn 1:1 + ServerPlayerInteractionManager's two CanDestroy
 * consults): a restricted placer's held stack must carry the CLICKED
 * block in its CanPlaceOn list to place, the MINED block in its
 * CanDestroy list to dig; a passing gate consumes and breaks exactly
 * like the survival path, a refusal drops the prediction and never
 * consumes.
 */
class AdventureGatesTest {

    @TempDir
    Path dataDir;

    EngineServer server;
    PlayerSession adventurer;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ag" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        adventurer = join("Dora");
        await(() -> adventurer.state() == PlayerState.PLAYING, "playing");
        // Flip into adventure (the engine's own setter — the /gamemode path).
        tick(() -> adventurer.setGamemode(GameMode.ADVENTURE));
        await(() -> adventurer.gamemode() == GameMode.ADVENTURE, "adventure set");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new RecordingLink(), name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        tick(() -> {
            session.markPlaying();
            session.setOpLevel(2);
            session.applyMovement(new Position(0.5, 5, 0.5), Rotation.ZERO, true);
        });
        return session;
    }

    private void tick(Runnable body) {
        AtomicBoolean done = new AtomicBoolean();
        server.ticker().submit(() -> {
            try {
                body.run();
            } finally {
                done.set(true);
            }
        });
        try {
            await(done::get, "the tick task ran");
        } catch (InterruptedException e) {
            throw new AssertionError("interrupted awaiting the tick task", e);
        }
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override public boolean isActive() { return true; }
        @Override public void kick(String reason) { }
        @Override public void updateSpawnPosition(int x, int y, int z) { }
        @Override public void playSound(String name, double x, double y, double z,
                                        float volume, float pitch) { }
        @Override public void worldParticles(int particleId, boolean longDistance, double x, double y, double z,
                                             float offsetX, float offsetY, float offsetZ,
                                             float particleData, int count, int[] params) { }
        @Override public void sendChatComponent(String json, int position) { }
    }

    // ------------------------------------------------------------------
    // The placement gate
    // ------------------------------------------------------------------

    @Test
    void theAdventurePlacementRefusesWithoutTheListAndPassesNamingTheClickedBlock()
            throws Exception {
        // The clicked stone the CanPlaceOn consult names.
        BlockPosition ground = new BlockPosition(2, 4, 2);
        BlockPosition target = new BlockPosition(2, 5, 2);
        tick(() -> server.world().setBlock(ground, BuiltinBlocks.STONE));
        await(() -> server.world().getBlock(ground).equals(BuiltinBlocks.STONE), "the stone laid");

        var chest = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:chest")).orElseThrow();

        // 1. No list: the prediction drops, nothing places, nothing consumes.
        tick(() -> adventurer.inventory().setSlot(0, ItemStack.of(chest, 1)));
        server.useItemOnBlock(adventurer, ground, 1, Optional.empty(), id -> { });
        EngineAwaits.awaitTicks(server.world(), 8, "the resync arrives; the block never lands");
        assertTrue(server.world().getBlock(target).equals(server.world().airType()),
                "the bare adventure stack never places");
        assertTrue(adventurer.inventory().held().count() == 1,
                "the refused placement never consumes");

        // 2. The list naming a DIFFERENT block: still refused.
        tick(() -> adventurer.inventory().setSlot(0,
                ItemStack.of(chest, 1).withCanPlaceOn(List.of("minecraft:dirt"))));
        server.useItemOnBlock(adventurer, ground, 1, Optional.empty(), id -> { });
        EngineAwaits.awaitTicks(server.world(), 8, "engine progress (a 400 wall settle)");
        assertTrue(server.world().getBlock(target).equals(server.world().airType()),
                "the wrong-name list never places");

        // 3. The list naming the CLICKED block (the reference's
        // pos.offset(face.opposite) — the stone, never the chest family):
        // the placement lands and consumes like the survival path. The L2
        // chest slice places the LOOK-FACING state (chest_north/... — never
        // the bare 54 constant), so the assertion reads the family.
        tick(() -> adventurer.inventory().setSlot(0,
                ItemStack.of(chest, 1).withCanPlaceOn(List.of("minecraft:stone"))));
        server.useItemOnBlock(adventurer, ground, 1, Optional.empty(), id -> { });
        await(() -> net.zaminmc.torch.server.chest.ChestFacingBlocks
                .isPlainFamily(server.world().getBlock(target)),
                "the named list places");
        await(() -> adventurer.inventory().held().isEmpty(), "the passing gate consumes");
    }

    @Test
    void theBareNameNormalizationFeedsThroughGive() throws Exception {
        // /give's dataTag carries the list; the sanitize normalizes bare
        // names ("stone" -> "minecraft:stone", the engine's id convention).
        server.chatService().submitChat(adventurer, "/give " + adventurer.name() + " chest 1 0 {CanPlaceOn:[stone]}");
        await(() -> {
            ItemStack held = adventurer.inventory().held();
            return !held.isEmpty()
                    && held.canPlaceOn() != null
                    && held.canPlaceOn().size() == 1
                    && held.canPlaceOn().get(0).equals("minecraft:stone");
        }, "the granted stack carries the normalized list");
    }

    // ------------------------------------------------------------------
    // The mine gate
    // ------------------------------------------------------------------

    @Test
    void theAdventureDigRefusesWithoutTheListAndBreaksWithIt() throws Exception {
        BlockPosition target = new BlockPosition(2, 5, 2);
        tick(() -> server.world().setBlock(target, BuiltinBlocks.DIRT));
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.DIRT), "the dirt seeded");

        // 1. No list: the dig never opens, the finish refuses, the block stays.
        server.blockInteraction().submitMiningStart(adventurer, target);
        EngineAwaits.awaitTicks(server.world(), 12, "engine progress (a 600 wall settle)");
        server.blockInteraction().submitMiningFinished(adventurer, target);
        EngineAwaits.awaitTicks(server.world(), 8, "engine progress (a 400 wall settle)");
        assertTrue(server.world().getBlock(target).equals(BuiltinBlocks.DIRT),
                "the bare adventure dig never breaks");

        // 2. The list naming the block (bare "dirt" normalized): the dig
        // opens, the finish commits, the block breaks.
        var stick = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:stick")).orElseThrow();
        tick(() -> adventurer.inventory().setSlot(0,
                ItemStack.of(stick, 1).withCanDestroy(List.of("dirt"))));
        server.blockInteraction().submitMiningStart(adventurer, target);
        EngineAwaits.awaitTicks(server.world(), 12, "the dig's own elapsed floor (the finish's walk)");
        server.blockInteraction().submitMiningFinished(adventurer, target);
        await(() -> server.world().getBlock(target).equals(server.world().airType()),
                "the named list breaks");
    }

    @Test
    void theSurvivalModePassesUntouched() throws Exception {
        // Back in survival the gates pass without any list (mayBuild true).
        tick(() -> adventurer.setGamemode(GameMode.SURVIVAL));
        await(() -> adventurer.gamemode() == GameMode.SURVIVAL, "survival set");
        BlockPosition ground = new BlockPosition(2, 4, 2);
        BlockPosition target = new BlockPosition(2, 5, 2);
        tick(() -> server.world().setBlock(ground, BuiltinBlocks.STONE));
        await(() -> server.world().getBlock(ground).equals(BuiltinBlocks.STONE), "the stone laid");
        var chest = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:chest")).orElseThrow();
        tick(() -> adventurer.inventory().setSlot(0, ItemStack.of(chest, 1)));
        server.useItemOnBlock(adventurer, ground, 1, Optional.empty(), id -> { });
        await(() -> net.zaminmc.torch.server.chest.ChestFacingBlocks
                .isPlainFamily(server.world().getBlock(target)),
                "the survival placement passes without the list");
    }
}
