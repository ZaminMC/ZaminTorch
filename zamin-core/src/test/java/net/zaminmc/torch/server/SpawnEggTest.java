package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.SpawnEggs;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawn egg family's own law (Slice 20a — the reference's
 * Entities.registerWithSpawnEgg table + SpawnEggItem): the mapping (the
 * golems and the boss pair carry no eggs), the block use (the face-offset
 * spawn with the fence's half-block lift), the spawner re-type, the
 * creative/survival payment, and the unknown id's no-op.
 */
class SpawnEggTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private static net.zaminmc.torch.server.chat.CommandSender console() {
        return new net.zaminmc.torch.server.chat.CommandSender() {
            @Override public String name() {
                return "Console";
            }

            @Override public void sendMessage(String message) {
            }

            @Override public int opLevel() {
                return 4;
            }

            @Override public net.zaminmc.torch.server.player.PlayerSession player() {
                return null;
            }
        };
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theMappingMatchesTheReferenceEggTable() {
        // Entities.java lines 255-286: every registerWithSpawnEgg id.
        assertSame(MobType.CREEPER, SpawnEggs.typeOf(50));
        assertSame(MobType.SKELETON, SpawnEggs.typeOf(51));
        assertSame(MobType.SPIDER, SpawnEggs.typeOf(52));
        assertSame(MobType.ZOMBIE, SpawnEggs.typeOf(54));
        assertSame(MobType.SLIME, SpawnEggs.typeOf(55));
        assertSame(MobType.GHAST, SpawnEggs.typeOf(56));
        assertSame(MobType.ZOMBIE_PIGMAN, SpawnEggs.typeOf(57));
        assertSame(MobType.ENDERMAN, SpawnEggs.typeOf(58));
        assertSame(MobType.CAVE_SPIDER, SpawnEggs.typeOf(59));
        assertSame(MobType.SILVERFISH, SpawnEggs.typeOf(60));
        assertSame(MobType.BLAZE, SpawnEggs.typeOf(61));
        assertSame(MobType.MAGMA_CUBE, SpawnEggs.typeOf(62));
        assertSame(MobType.BAT, SpawnEggs.typeOf(65));
        assertSame(MobType.WITCH, SpawnEggs.typeOf(66));
        assertSame(MobType.ENDERMITE, SpawnEggs.typeOf(67));
        assertSame(MobType.GUARDIAN, SpawnEggs.typeOf(68));
        assertSame(MobType.PIG, SpawnEggs.typeOf(90));
        assertSame(MobType.SHEEP, SpawnEggs.typeOf(91));
        assertSame(MobType.COW, SpawnEggs.typeOf(92));
        assertSame(MobType.CHICKEN, SpawnEggs.typeOf(93));
        assertSame(MobType.SQUID, SpawnEggs.typeOf(94));
        assertSame(MobType.WOLF, SpawnEggs.typeOf(95));
        assertSame(MobType.MOOSHROOM, SpawnEggs.typeOf(96));
        assertSame(MobType.OCELOT, SpawnEggs.typeOf(98));
        assertSame(MobType.HORSE, SpawnEggs.typeOf(100));
        assertSame(MobType.RABBIT, SpawnEggs.typeOf(101));
        assertSame(MobType.VILLAGER, SpawnEggs.typeOf(120));
        // The reference's own gaps: the snow golem (97), the iron golem
        // (99), the giant (53) and the ender dragon (63) registered without
        // eggs — spawnEntity's containsKey gate refuses them.
        assertNull(SpawnEggs.typeOf(97));
        assertNull(SpawnEggs.typeOf(99));
        assertNull(SpawnEggs.typeOf(53));
        assertNull(SpawnEggs.typeOf(63));
        assertNull(SpawnEggs.typeOf(0));
    }

    @Test
    void theBlockUseSpawnsAtTheFaceOffsetAndPays() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession player = join(server, "Shepherd");
            BlockPosition stone = new BlockPosition(2, 4, 2);
            server.world().execute(() -> server.world().setBlock(stone, BuiltinBlocks.STONE));
            await(() -> server.world().getBlock(stone).equals(BuiltinBlocks.STONE),
                    "the stone landed");

            server.chatService().submitChat(player, "/give spawn_egg 1 90"); // the pig egg
            await(() -> !player.inventory().held().isEmpty(), "the egg granted");

            // SpawnEggItem.useOn line 82: the UP face's offset cell center —
            // (2.5, 5.0, 2.5), the plain 0.0 lift (not the fence's 0.5).
            server.useItemOnBlock(player, stone, 1, java.util.Optional.empty(), ignored -> { });
            // useOn line 82's cell — the body settles onto the stone's
            // support plane (the engine's snap) before the sample.
            await(() -> server.mobs().all().stream()
                            .anyMatch(m -> m.type() == MobType.PIG
                                    && Math.abs(m.position().y() - 5.0) < 0.01
                                    && Math.abs(m.position().x() - 2.5) < 0.01
                                    && Math.abs(m.position().z() - 2.5) < 0.01),
                    "the pig spawned at the offset cell's center");
            // useOn lines 85-88: the survival hand pays one unit.
            assertTrue(player.inventory().held().isEmpty(), "the egg was consumed");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theFenceUpFaceLiftsTheSpawn() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession player = join(server, "Farmer");
            BlockPosition fence = new BlockPosition(3, 4, 3);
            server.world().execute(() -> server.world().setBlock(fence, BuiltinBlocks.FENCE));
            await(() -> server.world().getBlock(fence).equals(BuiltinBlocks.FENCE),
                    "the fence landed");

            server.chatService().submitChat(player, "/give spawn_egg 1 91"); // the sheep egg
            await(() -> !player.inventory().held().isEmpty(), "the egg granted");

            // useOn lines 78-81: the FenceBlock's UP face adds its own
            // half-block hull — the body lands 1.5 above the fence's floor.
            server.useItemOnBlock(player, fence, 1, java.util.Optional.empty(), ignored -> { });
            // useOn lines 78-81: the FenceBlock's UP face adds its own
            // half-block hull — the body settles 1.5 above the fence's
            // floor (the engine's own 1.5 fence support).
            await(() -> server.mobs().all().stream()
                            .anyMatch(m -> m.type() == MobType.SHEEP
                                    && Math.abs(m.position().y() - 5.5) < 0.01
                                    && Math.abs(m.position().x() - 3.5) < 0.01
                                    && Math.abs(m.position().z() - 3.5) < 0.01),
                    "the sheep spawned on the fence's half-block hull");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theEggRetypesTheSpawnerAndTheUnknownIdClearsIt() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession player = join(server, "Warden");
            BlockPosition spawner = new BlockPosition(4, 4, 4);
            server.world().execute(() ->
                    server.world().setBlock(spawner, BuiltinBlocks.MOB_SPAWNER));
            await(() -> server.world().getBlock(spawner).equals(BuiltinBlocks.MOB_SPAWNER),
                    "the spawner landed");

            // useOn lines 68-82: the creeper egg re-types the block entity.
            server.chatService().submitChat(player, "/give spawn_egg 1 50");
            await(() -> !player.inventory().held().isEmpty(), "the creeper egg granted");
            server.useItemOnBlock(player, spawner, 1, java.util.Optional.empty(), ignored -> { });
            await(() -> server.spawners().peek(spawner) != null
                            && server.spawners().peek(spawner).type() == MobType.CREEPER,
                    "the spawner re-typed to the creeper");
            assertTrue(player.inventory().held().isEmpty(), "the egg paid for the re-type");

            // The unknown id (63 — the ender dragon registered without an
            // egg): the reference's null key CLEARS the type the same way.
            server.chatService().submitChat(player, "/give spawn_egg 1 63");
            await(() -> !player.inventory().held().isEmpty(), "the dragon egg granted");
            server.useItemOnBlock(player, spawner, 1, java.util.Optional.empty(), ignored -> { });
            await(() -> server.spawners().peek(spawner) == null
                            || server.spawners().peek(spawner).type() == null,
                    "the unknown id cleared the type");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theCreativeHandSpawnsWithoutPaying() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession player = join(server, "Architect");
            server.chatService().dispatchCommand(console(), "/gamemode creative Architect");
            await(() -> player.gamemode() == net.zaminmc.torch.GameMode.CREATIVE,
                    "the creative mode landed");

            BlockPosition dirt = new BlockPosition(5, 4, 5);
            server.world().execute(() -> server.world().setBlock(dirt, BuiltinBlocks.DIRT));
            await(() -> server.world().getBlock(dirt).equals(BuiltinBlocks.DIRT),
                    "the dirt landed");

            server.chatService().submitChat(player, "/give spawn_egg 2 54"); // the zombie pair
            await(() -> !player.inventory().held().isEmpty(), "the eggs granted");

            // useOn lines 85-88: the creative hand never pays.
            server.useItemOnBlock(player, dirt, 1, java.util.Optional.empty(), ignored -> { });
            await(() -> server.mobs().all().stream()
                            .anyMatch(m -> m.type() == MobType.ZOMBIE),
                    "the zombie spawned");
            assertEquals(2, player.inventory().held().count(), "the creative hand kept the stack");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theUnknownIdNeverSpawnsOrPays() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession player = join(server, "Skeptic");
            BlockPosition stone = new BlockPosition(6, 4, 6);
            server.world().execute(() -> server.world().setBlock(stone, BuiltinBlocks.STONE));
            await(() -> server.world().getBlock(stone).equals(BuiltinBlocks.STONE),
                    "the stone landed");

            server.chatService().submitChat(player, "/give spawn_egg 1 53"); // the giant: no egg
            await(() -> !player.inventory().held().isEmpty(), "the egg granted");
            // The no-body assert counts the mobs NEAR the use point alone
            // (inside the natural spawn system's own 24-block keep-out
            // ring around world spawn — NaturalSpawner line 90's law keeps
            // every natural pack outside it, so the ring's count can only
            // move when the EGG's own spawn lands one; the global count
            // races the natural cycle and flakes under load).
            long before = mobsNearUsePoint(server, stone);

            // spawnEntity's containsKey gate: no body, no payment.
            server.useItemOnBlock(player, stone, 1, java.util.Optional.empty(), ignored -> { });
            Thread.sleep(150); // a few ticks: nothing may arrive late
            assertEquals(before, mobsNearUsePoint(server, stone), "no body ever spawned");
            assertEquals(1, player.inventory().held().count(), "the unknown id never pays");
        } finally {
            server.shutdown(null);
        }
    }

    /**
     * The mobs within the 16-block ring around the use point (the egg's
     * own spawn would land at the clicked face's offset cell — well inside
     * it; the natural spawn system's 24-block keep-out law keeps every
     * natural pack outside the ring, so the count moves only for the egg).
     */
    private static long mobsNearUsePoint(EngineServer server, BlockPosition at) {
        return server.mobs().all().stream()
                .filter(mob -> {
                    var position = mob.position();
                    double dx = position.x() - at.x();
                    double dz = position.z() - at.z();
                    return dx * dx + dz * dz < 16.0 * 16.0;
                })
                .count();
    }
}
