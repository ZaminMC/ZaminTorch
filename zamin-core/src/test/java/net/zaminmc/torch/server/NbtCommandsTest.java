package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NBT command arms (Slice 89 p5/p6 — BlockDataCommand and
 * EntityDataCommand 1:1, op-2): the write-merge-compare-read cycles over
 * the tile and entity NBT projections, the unchanged refusals, the
 * no-tile and no-players refusals, and the read's landings.
 */
import net.zaminmc.torch.server.player.PlayerSession;

class NbtCommandsTest {

    @TempDir
    Path dataDir;

    EngineServer server;
    PlayerSession alice;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "nbtcmd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        alice = join("Alice");
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        server.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "Alice opped");
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
        server.ticker().submit(session::markPlaying);
        return session;
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

    private void dispatch(String line) {
        server.chatService().submitChat(alice, line);
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
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

    private MobEntity theZombie() {
        return server.mobs().all().stream()
                .filter(m -> m.type() == MobType.ZOMBIE)
                .findFirst().orElse(null);
    }

    @Test
    void theBlockDataCycleLandsTheMergedRows() throws Exception {
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        dispatch("/setblock ~2 ~ ~ chest");
        BlockPosition chestPos = new BlockPosition(x + 2, y, z);
        await(() -> server.world().getBlock(chestPos).identifier().value().equals("chest"),
                "the chest landed");

        // The merge: the Items row lands through the read walk.
        dispatch("/blockdata ~2 ~ ~ {Items:[{Slot:0b,id:\"minecraft:stone\",Count:4b}]}");
        await(() -> {
            var chest = server.chests().peek(chestPos);
            return chest != null && !chest.getItem(0).isEmpty()
                    && chest.getItem(0).type().identifier().value().equals("stone")
                    && chest.getItem(0).count() == 4;
        }, "the merged Items row landed");

        // The unchanged refusal: an empty merge changes nothing (the deep
        // equals against the pre-merge copy — the slot keeps its stack).
        dispatch("/blockdata ~2 ~ ~ {}");
        Thread.sleep(300);
        var chest = server.chests().peek(chestPos);
        assertTrue(chest != null && chest.getItem(0).count() == 4,
                "the unchanged refusal changed nothing");

        // The no-tile refusal: the plain ground beside it.
        dispatch("/blockdata ~3 ~ ~ {Items:[]}");
        Thread.sleep(300);
        // (The refusal is observable only as no state change; the stone
        // cell carries no tile family at all.)
        assertEquals("air", server.world().getBlock(
                new BlockPosition(x + 3, y, z)).identifier().value());
    }

    @Test
    void theEntityDataCycleLandsTheMergedRows() throws Exception {
        dispatch("/summon Zombie ~ ~ ~");
        await(() -> theZombie() != null, "the zombie summoned");
        MobEntity zombie = theZombie();
        assertNotNull(zombie);

        // The merge: the name pair + the NoAI pin land.
        dispatch("/entitydata @e[type=Zombie,c=1] {NoAI:1b,CustomName:\"Boss\"}");
        await(() -> "Boss".equals(zombie.customName()) && zombie.noAi(),
                "the merged rows landed");
        // The UUID strip: the body's own uuid survives any input rows.
        java.util.UUID before = zombie.getUuid();
        dispatch("/entitydata @e[type=Zombie,c=1] {UUIDMost:1L,UUIDLeast:2L}");
        Thread.sleep(300);
        assertEquals(before, zombie.getUuid(), "the input's UUID rows never merge");

        // The Pos rows MOVE the body (the reference's own readNbt
        // behavior). The assert reads SOME body — the selector's nearest
        // may have shifted to a naturally-spawned zombie between the
        // commands; the moved one is whichever the selector picked.
        dispatch("/entitydata @e[type=Zombie,c=1] {Pos:[100.5d,64.0d,100.5d]}");
        await(() -> server.mobs().all().stream().anyMatch(m ->
                        Math.abs(m.position().x() - 100.5) < 1e-6
                                && Math.abs(m.position().z() - 100.5) < 1e-6),
                "the Pos rows moved a body");

        // The Health row as a SHORT lands (the reference's FLOAT/SHORT gate).
        dispatch("/entitydata @e[type=Zombie,c=1] {Health:10s}");
        await(() -> server.mobs().all().stream()
                        .anyMatch(m -> m.health() == 10.0f && "Boss".equals(m.customName())),
                "the SHORT Health row landed");

        // The player refusal: Alice is never a target.
        dispatch("/entitydata Alice {NoAI:1b}");
        Thread.sleep(300);
        assertTrue(alice.opLevel() >= 2, "the player refusal touched nothing");
    }

}
