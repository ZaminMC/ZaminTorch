package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.World;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mob system: spawn bookkeeping, the death flow (20-tick animation, then
 * loot + removal), dawn/despawn policies and the population maintainer.
 * Deterministic world, recorded listener, seeded rolls.
 */
class MobManagerTest {

    private static MobEntity.WorldQuery worldAt(Position nearestPlayer) {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                if (nearestPlayer == null) {
                    return null;
                }
                double dx = nearestPlayer.x() - x;
                double dy = nearestPlayer.y() - y;
                double dz = nearestPlayer.z() - z;
                return dx * dx + dy * dy + dz * dz <= range * range ? nearestPlayer : null;
            }
        };
    }

    /** Surface at y=4 everywhere (population rolls land on it). */
    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 4;

    private static final class Events implements MobManager.Listener {
        int spawned, moved, hurt, died, sounds;
        final List<String> removals = new ArrayList<>();
        final List<Float> playerHits = new ArrayList<>();

        @Override public void onMobSpawned(MobEntity mob) { spawned++; }
        @Override public void onMobMoved(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession rider) { moved++; }
        @Override public void onMobHurt(MobEntity mob) { hurt++; }
        @Override public void onMobDied(MobEntity mob) { died++; }
        @Override public void onMobRemoved(MobEntity mob, String reason) { removals.add(reason); }
        @Override public void onMobAttackedPlayer(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession target,
                                                  float damage) {
            playerHits.add(damage);
        }
        @Override public void onMobSound(MobEntity mob, String soundName) { sounds++; }
        @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
        @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
        @Override public void onMobSheared(MobEntity mob, int woolCount) { wool += woolCount; }
        @Override public void onMobCoatRegrown(MobEntity mob) { regrown++; }
        @Override public void onMobExploded(MobEntity mob) { explosions++; }

        int wool, regrown, explosions;
    }

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new ArrayList<>();

        @Override public void spawnLootDrop(Position position, ItemStack stack) {
            drops.add(stack);
        }
    }

    private record Rig(MobManager manager, Events events, Loot loot) {
    }

    private Rig rig(long seed) {
        Events events = new Events();
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(null), new Random(seed), loot,
                1_100_000, SURFACE);
        manager.addListener(events);
        return new Rig(manager, events, loot);
    }

    @Test
    void spawnBooksTheEntityAndTheListener() {
        Rig rig = rig(1);
        MobEntity mob = rig.manager().spawnAt(MobType.PIG, new Position(0.5, 4.0, 0.5));
        assertEquals(1_100_000, mob.entityId(), "mob ids live in their own band");
        assertEquals(1, rig.manager().size());
        assertEquals(mob, rig.manager().byId(1_100_000));
        assertEquals(1, rig.events().spawned);
    }

    @Test
    void deathFlowsHurtDiedAnimationLootRemoval() {
        Rig rig = rig(2);
        MobEntity pig = rig.manager().spawnAt(MobType.PIG, new Position(0.5, 4.0, 0.5));
        rig.manager().hurt(pig, 999.0f, 0.0);
        assertEquals(1, rig.events().hurt);
        assertEquals(1, rig.events().died);
        assertTrue(rig.loot().drops.isEmpty(), "loot waits for the death animation");
        for (int i = 0; i < MobEntity.DEATH_TICKS + 1; i++) {
            rig.manager().tick(List.of(), MobManager.NIGHT_START);
        }
        assertEquals(1, rig.loot().drops.size(), "the pig's loot rolled once");
        ItemStack drop = rig.loot().drops.get(0);
        assertEquals(BuiltinItems.PORKCHOP, drop.type(), "pigs drop porkchops");
        assertTrue(drop.count() >= 1 && drop.count() <= 3, "porkchop count is 1-3");
        assertEquals(0, rig.manager().size(), "the body left the world");
        assertEquals(List.of("died"), rig.events().removals);
        assertNull(rig.manager().byId(pig.entityId()), "removed ids are not found");
    }

    @Test
    void cowLootRollsStayInTheirHistoricalBounds() {
        Rig rig = rig(5);
        for (int i = 0; i < 30; i++) {
            MobEntity cow = rig.manager().spawnAt(MobType.COW, new Position(0.5, 4.0, 0.5));
            rig.manager().hurt(cow, 999.0f, 0.0);
            for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
                rig.manager().tick(List.of(), MobManager.NIGHT_START);
            }
        }
        int beefStacks = 0;
        int beef = 0;
        int leather = 0;
        for (ItemStack drop : rig.loot().drops) {
            if (drop.type().equals(BuiltinItems.BEEF)) {
                beefStacks++;
                beef += drop.count();
            } else if (drop.type().equals(BuiltinItems.LEATHER)) {
                leather += drop.count();
            } else {
                throw new AssertionError("unexpected loot " + drop.type());
            }
        }
        assertEquals(30, beefStacks, "every cow drops its beef roll (1-3 each)");
        assertTrue(beef >= 30 && beef <= 90, "beef 1-3 per cow, got " + beef);
        assertTrue(leather <= 60, "leather 0-2 per cow, got " + leather);
    }

    @Test
    void dawnIgnitesTheUndeadAndNightKeepsThem() {
        Rig rig = rig(7);
        MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        rig.manager().tick(List.of(), 1_000); // broad daylight
        assertTrue(rig.events().removals.isEmpty(),
                "the dawn no longer vaporizes: the undead ignite");
        assertTrue(zombie.burning(), "the daylight set the zombie on fire");
        assertTrue(rig.manager().byId(zombie.entityId()) != null,
                "the burning body stays until the fire consumes it");

        // The sun keeps burning: enough daylight ticks kill the body by fire.
        for (int i = 0; i < 500 && rig.manager().byId(zombie.entityId()) != null; i++) {
            rig.manager().tick(List.of(), 1_000);
        }
        assertTrue(rig.manager().byId(zombie.entityId()) == null,
                "the burn consumed the zombie");
        assertEquals(List.of("died"), rig.events().removals,
                "the fire death plays the ordinary death flow");

        Rig nightRig = rig(8);
        nightRig.manager().spawnAt(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        nightRig.manager().tick(List.of(), MobManager.NIGHT_START + 5);
        assertEquals(1, nightRig.manager().size(), "night keeps its zombies");
        assertTrue(nightRig.events().removals.isEmpty());
    }

    @Test
    void creepersAreSunProofButSpidersGoNeutralAtDawn() {
        Rig rig = rig(10);
        MobEntity creeper = rig.manager().spawnAt(MobType.CREEPER, new Position(0.5, 4.0, 0.5));
        rig.manager().tick(List.of(), 1_000);
        assertTrue(rig.manager().byId(creeper.entityId()) != null,
                "the creeper survives the dawn (the historical rule)");
        assertFalse(creeper.burning(), "creepers never catch fire from the sun");
    }

    @Test
    void distantMobsDespawnButMobsIdleWhenNobodyPlays() {
        Rig rig = rig(9);
        MobEntity far = rig.manager().spawnAt(MobType.PIG, new Position(200.0, 4.0, 200.0));

        // Nobody playing: nothing despawns (the population waits for visitors).
        rig.manager().tick(List.of(), 1_000);
        assertTrue(rig.events().removals.isEmpty(), "no players: no despawn");
        assertNotNull(rig.manager().byId(far.entityId()));
        rig.manager().remove(far, "test");
    }

    @Test
    void populationMaintainerTopsUpPassivesAndSpawnsNightZombies() {
        Rig rig = rig(11);
        var player = playingPlayer(new Position(0.5, 4.0, 0.5));
        // Two hundred maintainer cycles: passives top up toward the cap, and
        // the night rolls certainly produced zombies in that many chances.
        for (int i = 0; i < MobManager.MAINTAIN_PERIOD * 200; i++) {
            rig.manager().tick(List.of(player), MobManager.NIGHT_START + 100);
        }
        int passive = 0;
        int hostile = 0;
        for (MobEntity mob : rig.manager().all()) {
            if (mob.type().hostile) {
                hostile++;
            } else {
                passive++;
            }
        }
        assertTrue(passive >= MobManager.PASSIVE_CAP - 4,
                "passives topped up near the cap, got " + passive);
        assertTrue(passive <= MobManager.PASSIVE_CAP,
                "the cap holds, got " + passive);
        assertTrue(hostile > 0, "night spawning produced zombies");
        assertTrue(hostile <= MobManager.HOSTILE_CAP, "the hostile cap holds");
        assertTrue(rig.events().spawned > 0);
    }

    @Test
    void zombieMeleeReachesThePlayerThroughTheListener() {
        Loot loot = new Loot();
        var player = playingPlayer(new Position(1.0, 4.0, 0.5));
        var world = worldAt(player.position());
        MobManager manager = new MobManager(world, new Random(13), loot,
                1_100_000, SURFACE);
        Events events = new Events();
        manager.addListener(events);
        manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        boolean hit = false;
        for (int i = 0; i < 100 && !hit; i++) {
            manager.tick(List.of(player), MobManager.NIGHT_START);
            hit = !events.playerHits.isEmpty();
        }
        assertTrue(hit, "the zombie landed a hit on the adjacent player");
        assertEquals(MobEntity.MELEE_ATTACK_DAMAGE, events.playerHits.get(0),
                "easy-difficulty zombie damage");
    }

    private static net.zaminmc.torch.server.player.PlayerSession playingPlayer(Position at) {
        net.zaminmc.torch.server.net.ClientLink link = new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var session = new net.zaminmc.torch.server.player.PlayerSession(
                java.util.UUID.nameUUIDFromBytes("Watcher".getBytes()), "Watcher", link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    private static net.zaminmc.torch.World stubWorld() {
        return new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(net.zaminmc.torch.block.BlockPosition p, net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(net.zaminmc.torch.block.ChunkPosition p) { return true; }
        };
    }
}
