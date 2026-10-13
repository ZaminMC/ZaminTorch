package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zombie pigman (Slice 16d — ZombiePigmanEntity's anger model): the
 * neutral gate (the hunt only runs while angry), the group anger on a
 * player's hit with the 400+nextInt(400) window and the delayed angry
 * sound, and the kind's own identity.
 */
class ZombiePigmanTest {

    /** A flat-world stub: solid below y=8, air above, no player. */
    private static MobEntity.WorldQuery worldAt(Position nearest) {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                if (nearest == null) {
                    return null;
                }
                double dx = nearest.x() - x;
                double dy = nearest.y() - y;
                double dz = nearest.z() - z;
                return dx * dx + dy * dy + dz * dz <= range * range ? nearest : null;
            }
        };
    }

    private static MobEntity pigman(long seed) {
        return new MobEntity(950, MobType.ZOMBIE_PIGMAN, new Position(0.5, 8.0, 0.5),
                new Random(seed), worldAt(null));
    }

    @Test
    void theKindCarriesTheReferenceIdentity() {
        assertEquals(57, MobType.ZOMBIE_PIGMAN.legacyTypeId);
        assertEquals(0.6, MobType.ZOMBIE_PIGMAN.width, 1e-9);
        assertEquals(1.8, MobType.ZOMBIE_PIGMAN.height, 1e-9);
        assertEquals(20.0f, MobType.ZOMBIE_PIGMAN.maxHealth, 1e-6f);
        assertEquals(5.0f, MobType.ZOMBIE_PIGMAN.attackDamage, 1e-6f); // initAttributes
        assertTrue(MobType.ZOMBIE_PIGMAN.fireImmune());
        assertFalse(MobType.ZOMBIE_PIGMAN.slimeFamily());
        assertEquals("mob.zombiepig.zpig", MobType.ZOMBIE_PIGMAN.idleSound);
        assertEquals("mob.zombiepig.zpighurt", MobType.ZOMBIE_PIGMAN.hurtSound);
        assertEquals("mob.zombiepig.zpigdeath", MobType.ZOMBIE_PIGMAN.deathSound);
        assertEquals("minecraft:zombie_pigman", MobType.ZOMBIE_PIGMAN.identifier().toString());
    }

    @Test
    void theAngerWindowRollsAndDecays() {
        MobEntity pigman = pigman(81L);
        assertFalse(pigman.isAngry(), "a fresh pigman is neutral");

        // The anger: 400 + nextInt(400) ticks.
        pigman.becomeAngryTo("Attacker");
        assertTrue(pigman.isAngry());
        assertEquals("Attacker", pigman.angerTargetName());

        // The window decays one tick at a time (the tick's own walk).
        int ticks = 0;
        while (pigman.isAngry() && ticks < 1000) {
            pigman.tick(false);
            ticks++;
        }
        assertTrue(ticks >= 400 && ticks < 800,
                "the window is 400-799 ticks (took " + ticks + ")");
        assertFalse(pigman.isAngry());
    }

    @Test
    void theDelayedAngrySoundFiresWithinTheWindow() {
        MobEntity pigman = pigman(82L);
        pigman.becomeAngryTo("Attacker");
        boolean fired = false;
        for (int i = 0; i < 45 && !fired; i++) {
            pigman.tick(false);
            fired = pigman.consumePigmanAngrySound();
        }
        assertTrue(fired, "the angry sound fires within nextInt(40) + the tick walk");
        assertFalse(pigman.consumePigmanAngrySound(), "the sound fires once per anger");
    }

    @Test
    void theGroupAngerSpreadsThroughTheManager() {
        // The manager's propagation walk: the hurt body and every calm
        // pigman within 32 blocks anger together.
        java.util.function.BiFunction<Integer, Integer, Integer> surface = (x, z) -> 8;
        class CountingLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                                                net.zaminmc.torch.item.ItemStack stack) {
            }
        }
        MobManager manager = new MobManager(worldAt(null), new Random(83L),
                new CountingLoot(), 960_000, surface);
        MobEntity hurt = manager.spawnAt(MobType.ZOMBIE_PIGMAN, new Position(0.5, 8.0, 0.5));
        MobEntity near = manager.spawnAt(MobType.ZOMBIE_PIGMAN, new Position(6.5, 8.0, 0.5));
        MobEntity far = manager.spawnAt(MobType.ZOMBIE_PIGMAN, new Position(90.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(3.5, 8.0, 0.5));

        manager.pigmenBecomeAngryAt(hurt, "Attacker");
        assertTrue(hurt.isAngry(), "the hurt body angers");
        assertTrue(near.isAngry(), "the nearby pigman angers with it");
        assertFalse(far.isAngry(), "the far pigman stays calm (beyond 32)");
        assertFalse(zombie.isAngry(), "the zombie has no anger model");
        assertEquals("Attacker", near.angerTargetName());

        // The already-angry keep their own window: a second hit on the
        // group doesn't extend the neighbor's roll... but the hurt body
        // re-rolls its own (a fresh 400+nextInt(400)).
        int hurtWindow = 0;
        while (hurt.isAngry() && hurtWindow < 1000) {
            hurt.tick(false);
            hurtWindow++;
        }
        assertTrue(hurtWindow >= 400, "the re-rolled window holds the base");
    }

    /** A playing session named for the anger's target (the manager's
     * players walk resolves the name). */
    private static net.zaminmc.torch.server.player.PlayerSession playingPlayerNamed(
            String name, Position at) {
        net.zaminmc.torch.server.net.ClientLink link =
                new net.zaminmc.torch.server.net.ClientLink() {
                    @Override public boolean isActive() { return true; }
                    @Override public void kick(String reason) { }
                };
        var session = new net.zaminmc.torch.server.player.PlayerSession(
                java.util.UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() {
                return java.util.List.of();
            }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        }, Position.ZERO);
        session.markPlaying();
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    @Test
    void theRareGoldIngotRidesThePlayerKillWindow() {
        // ZombiePigmanEntity.dropRareItem line 189: the single gold ingot
        // on LivingEntity.die's 0.025 + looting * 0.01 roll — only inside
        // the player-kill window.
        class Loot implements MobManager.LootSink {
            final List<ItemStack> drops = new ArrayList<>();
            @Override public void spawnLootDrop(Position position, ItemStack stack) {
                drops.add(stack);
            }
        }
        Loot loot = new Loot();
        MobManager alwaysRare = new MobManager(worldAt(null),
                new Random(84L) {
                    @Override public float nextFloat() {
                        return 0.0f;
                    }
                }, loot, 961_000, (x, z) -> 8);
        MobEntity credited = alwaysRare.spawnAt(MobType.ZOMBIE_PIGMAN,
                new Position(0.5, 8.0, 0.5));
        credited.markPlayerHit();
        alwaysRare.hurt(credited, 999.0f, 0.0);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            alwaysRare.tick(List.of(), MobManager.NIGHT_START);
        }
        assertTrue(loot.drops.stream().anyMatch(d -> d.type() == BuiltinItems.GOLD_INGOT),
                "the player-kill window answers the rare ingot");
        // No player hit — the window never arms.
        Loot noHit = new Loot();
        MobManager neverHit = new MobManager(worldAt(null),
                new Random(85L) {
                    @Override public float nextFloat() {
                        return 0.0f;
                    }
                }, noHit, 962_000, (x, z) -> 8);
        MobEntity cold = neverHit.spawnAt(MobType.ZOMBIE_PIGMAN,
                new Position(0.5, 8.0, 0.5));
        neverHit.hurt(cold, 999.0f, 0.0);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            neverHit.tick(List.of(), MobManager.NIGHT_START);
        }
        assertTrue(noHit.drops.stream().noneMatch(d -> d.type() == BuiltinItems.GOLD_INGOT),
                "the rare ingot needs the player-kill window");
    }

    @Test
    void theAngerKeepsTheKillCreditAliveAcrossTheChase() {
        // ZombiePigmanEntity.mobAiTick lines 79-83: while the anger burns
        // and the named attacker is live, the credit window re-arms — the
        // pigman killed 150 ticks into the chase still credits the
        // attacker. The named player gone, the window lapses at 100.
        class Loot implements MobManager.LootSink {
            final List<ItemStack> drops = new ArrayList<>();
            @Override public void spawnLootDrop(Position position, ItemStack stack) {
                drops.add(stack);
            }
        }
        var attacker = playingPlayerNamed("Chaser", new Position(5.5, 8.0, 0.5));
        Loot loot = new Loot();
        MobManager manager = new MobManager(worldAt(null),
                new Random(86L) {
                    @Override public float nextFloat() {
                        return 0.0f;
                    }
                }, loot, 963_000, (x, z) -> 8);
        MobEntity chased = manager.spawnAt(MobType.ZOMBIE_PIGMAN,
                new Position(0.5, 8.0, 0.5));
        chased.becomeAngryTo("Chaser");
        chased.markPlayerHit(); // the wound that started the anger
        // 150 ticks — past the 100-tick window, the anger still burning.
        for (int t = 0; t < 150; t++) {
            manager.tick(List.of(attacker), MobManager.NIGHT_START);
        }
        assertTrue(chased.isAngry(), "the anger still burns at 150");
        assertTrue(chased.playerHitRecently(),
                "the re-acquisition keeps the credit window armed");
        manager.hurt(chased, 999.0f, 0.0);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            manager.tick(List.of(attacker), MobManager.NIGHT_START);
        }
        assertTrue(loot.drops.stream().anyMatch(d -> d.type() == BuiltinItems.GOLD_INGOT),
                "the chase kill still credits the attacker");
        // The named player gone: the window lapses and never re-arms.
        Loot lone = new Loot();
        MobManager empty = new MobManager(worldAt(null),
                new Random(87L) {
                    @Override public float nextFloat() {
                        return 0.0f;
                    }
                }, lone, 964_000, (x, z) -> 8);
        MobEntity abandoned = empty.spawnAt(MobType.ZOMBIE_PIGMAN,
                new Position(0.5, 8.0, 0.5));
        abandoned.becomeAngryTo("Ghost");
        abandoned.markPlayerHit();
        for (int t = 0; t < 150; t++) {
            empty.tick(List.of(), MobManager.NIGHT_START);
        }
        assertFalse(abandoned.playerHitRecently(),
                "no live attacker, no re-arm — the window lapses at 100");
    }
}
