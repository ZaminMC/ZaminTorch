package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The silverfish + the infested blocks (Slice 18c — SilverfishEntity +
 * InfestedBlock): the kind's identity, the egg family's mapping, the
 * hand/silk drop arms, the wake-up clock, the infest roll, and the egg's
 * burst through the live survival break on a real booted server.
 */
class SilverfishEntityTest {

    @TempDir
    Path dataDir;

    /** A flat-world stub with a scriptable adjacent block. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        Identifier adjacentBlock;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            return adjacentBlock;
        }
    }

    private static MobEntity silverfishIn(ScriptedWorld world, long seed) {
        return new MobEntity(960, MobType.SILVERFISH, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    @Test
    void theSilverfishCarriesTheIdentity() {
        // Entities.java: the Silverfish 60. The body (SilverfishEntity's
        // ctor + initAttributes lines 49-54): 0.4 x 0.3, 8 health, the 1.0
        // ATTACK_DAMAGE, the 0.25 movement.
        assertEquals(60, MobType.SILVERFISH.legacyTypeId);
        assertEquals(0.4, MobType.SILVERFISH.width, 1e-9);
        assertEquals(0.3, MobType.SILVERFISH.height, 1e-9);
        assertEquals(8.0f, MobType.SILVERFISH.maxHealth, 1e-6f);
        assertEquals(1.0f, MobType.SILVERFISH.attackDamage, 1e-6f,
                "initAttributes line 53: the 1.0 melee");
        assertTrue(MobType.SILVERFISH.hostile);
        assertEquals("mob.silverfish.say", MobType.SILVERFISH.idleSound);
        assertEquals("mob.silverfish.hit", MobType.SILVERFISH.hurtSound);
        assertEquals("mob.silverfish.kill", MobType.SILVERFISH.deathSound);
        // MobType.getMobType line 127: the ARTHROPOD category.
        assertEquals(2, MobType.SILVERFISH.damageCategory());

        // getEyeHeight line 45: the flat 0.1F.
        ScriptedWorld world = new ScriptedWorld();
        MobEntity body = silverfishIn(world, 95L);
        assertEquals(0.1, body.eyePosition().y() - body.position().y(), 1e-9);
    }

    @Test
    void theEggFamilyMapsTheHosts() {
        // InfestedBlock.canBeInfested (lines 56-60): the three plain host
        // forms alone.
        assertTrue(net.zaminmc.torch.server.block.InfestedBlocks.canBeInfested(
                Identifier.parse("minecraft:stone")));
        assertTrue(net.zaminmc.torch.server.block.InfestedBlocks.canBeInfested(
                Identifier.parse("minecraft:cobblestone")));
        assertTrue(net.zaminmc.torch.server.block.InfestedBlocks.canBeInfested(
                Identifier.parse("minecraft:stone_bricks")));
        assertFalse(net.zaminmc.torch.server.block.InfestedBlocks.canBeInfested(
                Identifier.parse("minecraft:mossy_stone_bricks")),
                "the mossy brick never takes the egg");
        assertFalse(net.zaminmc.torch.server.block.InfestedBlocks.canBeInfested(
                Identifier.parse("minecraft:dirt")));

        // The egg/host round trips (Variant.byHostState + getHostState).
        assertEquals(Identifier.parse("minecraft:infested_stone"),
                net.zaminmc.torch.server.block.InfestedBlocks.eggFor(
                        Identifier.parse("minecraft:stone")));
        assertEquals(Identifier.parse("minecraft:stone"),
                net.zaminmc.torch.server.block.InfestedBlocks.hostOf(
                        Identifier.parse("minecraft:infested_stone")));
        assertEquals(Identifier.parse("minecraft:stone_bricks"),
                net.zaminmc.torch.server.block.InfestedBlocks.hostOf(
                        Identifier.parse("minecraft:infested_stone_bricks")));

        // getSilkTouchDrop's damage field (lines 59-76): the mossy,
        // cracked and chiseled eggs carry 1/2/3.
        assertEquals(0, net.zaminmc.torch.server.block.InfestedBlocks.silkDropDamage(
                Identifier.parse("minecraft:infested_stone")));
        assertEquals(1, net.zaminmc.torch.server.block.InfestedBlocks.silkDropDamage(
                Identifier.parse("minecraft:infested_mossy_stone_bricks")));
        assertEquals(2, net.zaminmc.torch.server.block.InfestedBlocks.silkDropDamage(
                Identifier.parse("minecraft:infested_cracked_stone_bricks")));
        assertEquals(3, net.zaminmc.torch.server.block.InfestedBlocks.silkDropDamage(
                Identifier.parse("minecraft:infested_chiseled_stone_bricks")));
    }

    @Test
    void theEggNeverDropsByHandAndSilkDropsTheHost() {
        DropService drops = new DropService(new Random(97L));
        // The hand break: nothing (getBaseDropCount 0 — the silverfish
        // burst is the break path's own walk, never an item).
        assertTrue(drops.dropsFor(BuiltinBlocks.INFESTED_STONE, null).isEmpty());
        assertTrue(drops.dropsFor(BuiltinBlocks.INFESTED_STONE_BRICKS, null).isEmpty());
        // The silk arm (getSilkTouchDrop): the HOST's item — the plain
        // egg the stone, the brick egg the brick, the mossy egg the brick
        // with its own damage.
        var byHand = drops.dropsFor(BuiltinBlocks.INFESTED_STONE, null, true, 0);
        assertEquals(1, byHand.size());
        assertEquals(Identifier.parse("minecraft:stone"), byHand.get(0).type().identifier());
        assertEquals(0, byHand.get(0).damage());
        var silkBrick = drops.dropsFor(BuiltinBlocks.INFESTED_STONE_BRICKS, null, true, 0);
        assertEquals(Identifier.parse("minecraft:stone_bricks"),
                silkBrick.get(0).type().identifier());
        var silkMossy = drops.dropsFor(BuiltinBlocks.INFESTED_MOSSY_STONE_BRICKS, null, true, 0);
        assertEquals(Identifier.parse("minecraft:stone_bricks"),
                silkMossy.get(0).type().identifier(),
                "the reference's ItemStack(Blocks.STONE_BRICKS, 1, MOSSY)");
        assertEquals(1, silkMossy.get(0).damage());
    }

    @Test
    void theWakeupClockArmsOnHurtAndFiresTheScan() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity body = silverfishIn(world, 98L);

        // No wake without the hurt.
        for (int i = 0; i < 40; i++) {
            body.tick(true);
            assertFalse(body.consumeSilverfishWake(), "nothing arms the clock");
        }

        // WakeUpFriendsGoal.onDamaged (lines 198-201): the hurt arms the
        // 20-tick cooldown; the scan verdict fires when it expires.
        body.armSilverfishWake();
        boolean woke = false;
        for (int i = 0; i < 20 && !woke; i++) {
            body.tick(true);
            woke = body.consumeSilverfishWake();
        }
        assertTrue(woke, "the cooldown expires into the scan verdict");

        // The already-expired clock stays quiet until the next hurt.
        for (int i = 0; i < 10; i++) {
            body.tick(true);
            assertFalse(body.consumeSilverfishWake());
        }
    }

    @Test
    void theInfestRollTakesTheAdjacentHost() {
        ScriptedWorld world = new ScriptedWorld();
        // InfestStoneGoal.canStart: the 1-in-10 roll over the adjacent
        // stone cell — the stub answers stone for every read.
        world.adjacentBlock = Identifier.parse("minecraft:stone");
        MobEntity body = silverfishIn(world, 99L);

        boolean infested = false;
        for (int i = 0; i < 200 && !infested; i++) {
            body.tick(true);
            if (body.consumeSilverfishInfest()) {
                infested = true;
                // The target cell: one of the six neighbors of the body's
                // own cell (0, 8, 0).
                int dx = body.silverfishInfestX();
                int dy = body.silverfishInfestY();
                int dz = body.silverfishInfestZ();
                assertTrue(Math.abs(dx) + Math.abs(dy - 8) + Math.abs(dz) == 1,
                        "the infest target is the adjacent cell");
            }
        }
        assertTrue(infested, "the 1-in-10 roll fires within 200 ticks");

        // A non-infestable world never takes the egg.
        ScriptedWorld dirt = new ScriptedWorld();
        dirt.adjacentBlock = Identifier.parse("minecraft:dirt");
        MobEntity wanderer = silverfishIn(dirt, 100L);
        for (int i = 0; i < 200; i++) {
            wanderer.tick(true);
            assertFalse(wanderer.consumeSilverfishInfest(),
                    "the dirt never hosts the egg");
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "silverfish", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private net.zaminmc.torch.server.player.PlayerSession join(EngineServer server, String name)
            throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.nameUUIDFromBytes(name.getBytes()));
        net.zaminmc.torch.server.player.PlayerSession session =
                ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theEggBurstSpawnsTheSilverfishOnTheSurvivalBreak() throws Exception {
        EngineServer server = boot();
        try {
            var player = join(server, "Miner");
            // Place the egg one block ahead of the spawn.
            BlockPosition at = new BlockPosition(
                    (int) Math.floor(player.position().x()),
                    (int) Math.floor(player.position().y()),
                    (int) Math.floor(player.position().z()) + 3);
            server.world().execute(() -> server.world().setBlock(at,
                    BuiltinBlocks.INFESTED_STONE_BRICKS));
            await(() -> server.world().getBlock(at)
                    .equals(BuiltinBlocks.INFESTED_STONE_BRICKS), "the egg landed");

            // The survival dig: the hardness-0 egg mines instantly on the
            // START (the reference's own f >= 1.0 rule) — the burst rides
            // the tryMineBlock walk.
            server.blockInteraction().submitMiningStart(player, at);
            await(() -> server.world().getBlock(at).equals(server.world().airType()),
                    "the egg broke");
            await(() -> server.mobs().all().stream()
                            .anyMatch(m -> m.type() == MobType.SILVERFISH),
                    "the burst spawned the silverfish");
            MobEntity silverfish = server.mobs().all().stream()
                    .filter(m -> m.type() == MobType.SILVERFISH)
                    .findFirst().orElseThrow();
            // InfestedBlock.dropItems: the body lands at the block's own
            // center (pos + 0.5 on x/z) — the mind may walk it a step
            // before the read, so the band is one block wide.
            assertTrue(Math.abs(silverfish.position().x() - (at.x() + 0.5)) < 1.0,
                    "the burst's x lands at the block center (was "
                            + silverfish.position().x() + ")");
            assertTrue(Math.abs(silverfish.position().z() - (at.z() + 0.5)) < 1.0,
                    "the burst's z lands at the block center (was "
                            + silverfish.position().z() + ")");

            // The creative break never bursts (afterMinedByPlayer is the
            // survival-only arm): place a second egg and break it creative.
            server.chatService().dispatchCommand(new net.zaminmc.torch.server.chat.CommandSender() {
                @Override public String name() {
                    return "Server";
                }

                @Override public void sendMessage(String message) {
                }

                @Override public int opLevel() {
                    return 4;
                }

                @Override public net.zaminmc.torch.server.player.PlayerSession player() {
                    return null;
                }
            }, "/gamemode creative " + player.name());
            await(() -> player.gamemode() == net.zaminmc.torch.GameMode.CREATIVE,
                    "the creative mode landed");
            int before = (int) server.mobs().all().stream()
                    .filter(m -> m.type() == MobType.SILVERFISH).count();
            server.world().execute(() -> server.world().setBlock(at,
                    BuiltinBlocks.INFESTED_STONE_BRICKS));
            await(() -> server.world().getBlock(at)
                    .equals(BuiltinBlocks.INFESTED_STONE_BRICKS), "the second egg landed");
            server.blockInteraction().submitCreativeBreak(player, at);
            await(() -> server.world().getBlock(at).equals(server.world().airType()),
                    "the second egg broke");
            int after = (int) server.mobs().all().stream()
                    .filter(m -> m.type() == MobType.SILVERFISH).count();
            assertEquals(before, after, "the creative break never bursts");
        } finally {
            server.shutdown(null);
        }
    }
}
