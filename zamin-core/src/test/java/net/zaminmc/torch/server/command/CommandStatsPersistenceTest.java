package net.zaminmc.torch.server.command;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command-stat bindings' persistence (Slice 90 p3 — the reference's
 * Entity.writeNbt line 1128 + CommandBlockBlockEntity's executor rows):
 * the /stats-set bindings survive the restart through all four envelopes —
 * the ZCB tail (the command-block tile), the ZMD v5 tail (the mob), the
 * ZPD v10 tail (the player), and the ZSD v2 tail (the sign — Slice 91
 * Wave B, the engine's own convention: the reference's SignBlockEntity
 * carries none).
 */
class CommandStatsPersistenceTest {

    @TempDir
    static Path dataDir;

    /** The one world name both boots share (the persistence round-trip's key). */
    private static final String WORLD_NAME =
            "stats" + Math.abs(UUID.randomUUID().hashCode() % 100000);

    private static net.zaminmc.torch.server.EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, WORLD_NAME,
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        net.zaminmc.torch.server.EngineServer server =
                new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        return server;
    }

    private static PlayerSession join(net.zaminmc.torch.server.EngineServer server, String name)
            throws InterruptedException {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new SilentLink(), name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        return session;
    }

    private static final class SilentLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, String description)
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

    @Test
    void theBindingsSurviveTheRestart() throws Exception {
        // --- the first life: bind on the player, a mob, and a command block.
        net.zaminmc.torch.server.EngineServer first = boot();
        PlayerSession alice = join(first, "Alice");
        first.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "opped");
        // The player's own binding.
        first.chatService().submitChat(alice,
                "/stats entity Alice set SuccessCount Alice counter");
        await(() -> alice.commandResults().sourceOf(
                        EngineCommandResults.Type.SUCCESS_COUNT) != null,
                "the player's binding landed");
        // A named mob's binding.
        first.chatService().submitChat(alice, "/summon Zombie ~ ~ ~ {CustomName:\"Guard\"}");
        await(() -> first.mobs().all().stream()
                .anyMatch(m -> "Guard".equals(m.customName())), "the named zombie");
        first.chatService().submitChat(alice,
                "/stats entity @e[name=Guard] set AffectedBlocks Alice blocks");
        await(() -> first.mobs().all().stream()
                .filter(m -> "Guard".equals(m.customName()))
                .anyMatch(m -> m.commandResults().sourceOf(
                        net.zaminmc.torch.server.command.EngineCommandResults.Type.AFFECTED_BLOCKS) != null),
                "the mob's binding landed");
        // A command block's binding.
        first.chatService().submitChat(alice, "/setblock ~3 ~ ~ command_block");
        int bx = (int) Math.floor(alice.position().x()) + 3;
        int by = (int) Math.floor(alice.position().y());
        int bz = (int) Math.floor(alice.position().z());
        // The tile's placement first (the binding command needs it live).
        await(() -> first.commandBlocks().at(new BlockPosition(bx, by, bz)) != null,
                "the command block tile");
        first.chatService().submitChat(alice,
                "/stats block " + bx + " " + by + " " + bz + " set QueryResult Alice query");
        await(() -> first.commandBlocks().at(new BlockPosition(bx, by, bz)).commandResults()
                        .sourceOf(net.zaminmc.torch.server.command.EngineCommandResults.Type.QUERY_RESULT) != null,
                "the tile's binding landed");
        // A sign's binding (the ZSD v2 tail): the block through the command
        // walk, the text through the manager's own set (the wire edit path's
        // effect), the binding through /stats block — the sign tile family.
        first.chatService().submitChat(alice, "/setblock ~5 ~ ~ sign");
        int sx = (int) Math.floor(alice.position().x()) + 5;
        int sz = (int) Math.floor(alice.position().z());
        BlockPosition signPos = new BlockPosition(sx, by, sz);
        await(() -> "sign".equals(first.world().getBlock(signPos).identifier().value()),
                "the sign block landed");
        first.ticker().submit(() -> first.signs().set(signPos,
                new String[]{"Stats", "live here", "", ""}));
        await(() -> first.signs().peek(signPos) != null, "the sign text landed");
        first.chatService().submitChat(alice,
                "/stats block " + sx + " " + by + " " + sz + " set SuccessCount Alice signs");
        await(() -> first.signs().statsAt(signPos).sourceOf(
                        EngineCommandResults.Type.SUCCESS_COUNT) != null,
                "the sign's binding landed");
        // The scoreboard objectives the bindings feed.
        first.chatService().submitChat(alice, "/scoreboard objectives add counter dummy");
        first.chatService().submitChat(alice, "/scoreboard objectives add blocks dummy");
        first.chatService().submitChat(alice, "/scoreboard objectives add query dummy");
        await(() -> first.scoreboard().getObjectives().stream()
                .anyMatch(o -> "counter".equals(o.name())), "the objectives");
        first.chatService().submitChat(alice, "/save");
        await(() -> first.world().totalTicks() >= 2, "the save ran");
        first.shutdown(null);
        // --- the second life: the same data dir, the bindings back.
        net.zaminmc.torch.server.EngineServer second = boot();
        PlayerSession again = join(second, "Alice");
        second.ticker().submit(() -> again.setOpLevel(2));
        await(() -> again.opLevel() >= 2, "opped again");
        try {
            // The player's binding (the ZPD v10 tail).
            assertEquals("Alice", again.commandResults()
                    .sourceOf(EngineCommandResults.Type.SUCCESS_COUNT), "the player's source");
            assertEquals("counter", again.commandResults()
                    .objectiveOf(EngineCommandResults.Type.SUCCESS_COUNT), "the player's objective");
            // The mob's binding (the ZMD v5 tail).
            net.zaminmc.torch.server.entity.MobEntity guard = second.mobs().all().stream()
                    .filter(m -> "Guard".equals(m.customName()))
                    .findFirst().orElse(null);
            assertNotNull(guard, "the named zombie returned");
            assertEquals("Alice", guard.commandResults()
                    .sourceOf(EngineCommandResults.Type.AFFECTED_BLOCKS), "the mob's source");
            assertEquals("blocks", guard.commandResults()
                    .objectiveOf(EngineCommandResults.Type.AFFECTED_BLOCKS), "the mob's objective");
            // The command block's binding (the ZCB v2 tail).
            var tile = second.commandBlocks().at(new BlockPosition(bx, by, bz));
            assertNotNull(tile, "the command block returned");
            assertEquals("Alice", tile.commandResults()
                    .sourceOf(EngineCommandResults.Type.QUERY_RESULT), "the tile's source");
            assertEquals("query", tile.commandResults()
                    .objectiveOf(EngineCommandResults.Type.QUERY_RESULT), "the tile's objective");
            // The sign's binding (the ZSD v2 tail) — the text AND the stats
            // rode the same file.
            assertNotNull(second.signs().peek(signPos), "the sign text returned");
            assertEquals("Stats", second.signs().peek(signPos)[0], "the sign's first line");
            assertEquals("Alice", second.signs().statsAt(signPos)
                    .sourceOf(EngineCommandResults.Type.SUCCESS_COUNT), "the sign's source");
            assertEquals("signs", second.signs().statsAt(signPos)
                    .objectiveOf(EngineCommandResults.Type.SUCCESS_COUNT), "the sign's objective");
            // The bound feed fires: a command through the player feeds the
            // objective's score (the /stats contract).
            second.chatService().submitChat(again, "/time set day");
            await(() -> second.scoreboard().hasScore("Alice",
                            second.scoreboard().getObjectives().stream()
                                    .filter(o -> "counter".equals(o.name()))
                                    .findFirst().orElse(null))
                    || second.world().totalTicks() >= 3, "the feed ran");
            assertTrue(second.scoreboard().getObjectives().stream()
                    .anyMatch(o -> "counter".equals(o.name())), "the objective returned too");
        } finally {
            second.shutdown(null);
        }
    }

    @Test
    void aVersionOneSignFileLoadsBindingsEmpty() throws Exception {
        // The ZSD v1 shape (pre-Wave-B): magic + version 1 + the entries
        // with NO CommandStats tail. The version-aware load accepts it —
        // the texts read back, the bindings load empty (the v2 tail's own
        // default, the ZCB v1 precedent).
        java.nio.file.Path file = dataDir.resolve(
                "v1-signs-" + Math.abs(UUID.randomUUID().hashCode() % 100000) + ".bin");
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(file)))) {
            out.writeByte('Z');
            out.writeByte('S');
            out.writeByte('D');
            out.writeByte(1);
            out.writeByte(1); // one sign
            out.writeInt(1);
            out.writeInt(2);
            out.writeInt(3);
            String[] lines = {"one", "two", "three", "four"};
            for (String line : lines) {
                byte[] bytes = line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeByte(bytes.length); // single-byte varint (short lines)
                out.write(bytes);
            }
        }
        java.util.List<net.zaminmc.torch.server.sign.SignDataStore.Loaded> loadedRows =
                new net.zaminmc.torch.server.sign.SignDataStore(file).load();
        assertEquals(1, loadedRows.size(), "one v1 row read back");
        net.zaminmc.torch.server.sign.SignDataStore.Loaded loaded = loadedRows.get(0);
        assertEquals(1, loaded.signs().size(), "the v1 texts read back");
        assertEquals("one", loaded.signs().get(new BlockPosition(1, 2, 3))[0]);
        assertEquals("four", loaded.signs().get(new BlockPosition(1, 2, 3))[3]);
        assertEquals(0, loaded.dimension(), "a v1 file's row loads as an overworld row");
        assertTrue(loaded.stats().isEmpty(), "a v1 file carries no tails — the bindings load empty");
    }
}
