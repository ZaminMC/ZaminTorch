package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The experience economy, end to end: a survival coal-ore break releases
 * orbs at the block, the orbs pay out into the body that touches them,
 * creative breaks release nothing, and a mob's death scatters its kill band.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MiningXpAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "xp", "it", 20, 4, 20,
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
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        return session;
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

    private ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }

    private void setBlock(BlockPosition at, BlockType type) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "seeded " + at);
    }

    /** Stands the player 3.5 blocks off a block (in reach, outside pickup range). */
    private void standBeside(PlayerSession player, BlockPosition at) throws InterruptedException {
        Position beside = new Position(at.x() + 3.5, at.y(), at.z() + 0.5);
        server.ticker().submit(() -> player.applyMovement(beside,
                net.zaminmc.torch.util.Rotation.ZERO, true));
        Thread.sleep(80);
    }

    @Test
    void survivalDiamondBreakReleasesOrbsThatPayOut() throws Exception {
        server = boot();
        PlayerSession miner = join("Miner");
        BlockPosition ore = new BlockPosition(0, 64, 0);
        setBlock(ore, BuiltinBlocks.DIAMOND_ORE);
        standBeside(miner, ore);

        miner.inventory().setSlot(0, ItemStack.of(item("minecraft:iron_pickaxe"), 1));
        miner.inventory().selectHotbarSlot(0);
        // The honest dig rhythm: start, wait out the hardness (diamond ore
        // with an iron pick runs ~950ms nominal; the lenient floor is ~70%),
        // finish. The diamond roll (3-7) always pays something.
        server.blockInteraction().submitMiningStart(miner, ore);
        Thread.sleep(1100);
        server.blockInteraction().submitMiningFinished(miner, ore);

        await(() -> server.world().getBlock(ore).equals(BuiltinBlocks.AIR),
                "the diamond ore broke");
        await(() -> server.experienceOrbs().size() > 0,
                "the break released XP orbs");
        assertTrue(miner.totalXp() == 0, "nothing collected yet from a distance");

        // Walk onto the orb cluster: the pickup box absorbs the points.
        Position orbSpot = server.experienceOrbs().all().get(0).position();
        server.ticker().submit(() -> miner.applyMovement(orbSpot,
                net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> miner.totalXp() > 0, "the orbs paid out on touch");
        await(() -> server.experienceOrbs().size() == 0, "every orb was collected");
        server.shutdown(null);
    }

    @Test
    void creativeBreaksNeverReleaseXp() throws Exception {
        server = boot();
        PlayerSession builder = join("Creator");
        server.ticker().submit(() -> builder.setGamemode(net.zaminmc.torch.GameMode.CREATIVE));
        Thread.sleep(60);
        BlockPosition ore = new BlockPosition(0, 64, 0);
        setBlock(ore, BuiltinBlocks.COAL_ORE);
        standBeside(builder, ore);

        server.blockInteraction().submitCreativeBreak(builder, ore);
        await(() -> server.world().getBlock(ore).equals(BuiltinBlocks.AIR),
                "the creative break landed");
        Thread.sleep(200);
        assertEquals(0, server.experienceOrbs().size(),
                "creative mining pays nothing (the historical rule)");
        assertEquals(0, builder.totalXp(), "no points entered the body");
        server.shutdown(null);
    }

    @Test
    void mobDeathsScatterTheirKillBand() throws Exception {
        server = boot();
        PlayerSession watcher = join("Watcher");
        BlockPosition arena = new BlockPosition(0, 64, 0);
        standBeside(watcher, arena);

        var mob = server.mobs().spawnAt(
                net.zaminmc.torch.server.entity.MobType.ZOMBIE,
                new Position(arena.x() + 1.5, arena.y(), arena.z() + 0.5));
        assertTrue(server.mobs().byId(mob.entityId()) != null, "the zombie spawned");
        server.ticker().submit(() -> server.mobs().hurt(mob, 100.0f, 0.0f));
        // The 20-tick death animation runs before the body leaves and the
        // reward lands.
        await(() -> server.experienceOrbs().size() > 0, "the kill reward landed as orbs");
        int total = server.experienceOrbs().all().stream()
                .mapToInt(net.zaminmc.torch.server.experience.ExperienceOrbEntity::amount)
                .sum();
        assertEquals(5, total, "a hostile body releases the historical 5 XP");
        server.shutdown(null);
    }
}
