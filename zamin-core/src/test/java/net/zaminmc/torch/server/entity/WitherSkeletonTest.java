package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wither skeleton (Slice 18d — SkeletonEntity.setType(1) over the
 * archer's own kind): the variant's identity (the taller 0.72x2.535 hull,
 * the fire immunity, the 4.0 stone-sword melee, the generic eye share of
 * the taller body), the nether initialize's 4-in-5 conversion with the
 * stone-sword swap, the reference's own drop law (the coal-with-the-
 * negative arm, the bones beside, the bow never), and the wither curse
 * through the live melee pipeline (tryDamage lines 96-106: the flat
 * 200-tick WITHER, no difficulty gate).
 */
class WitherSkeletonTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The unit world (the flat plane)
    // ------------------------------------------------------------------

    private static MobEntity.WorldQuery flatWorld() {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    @Test
    void theVariantCarriesTheTallerHullAndTheMelee() {
        MobEntity wither = new MobEntity(951, MobType.SKELETON, new Position(0.5, 8.0, 0.5),
                new Random(95L), flatWorld());
        wither.setSkeletonWither(true);
        // setType line 281: setSize(0.72F, 2.535F).
        assertEquals(0.72, wither.bodyWidth(), 1e-9, "the wither width");
        assertEquals(2.535, wither.bodyHeight(), 1e-9, "the wither height");
        // initialize line 221: ATTACK_DAMAGE base 4.0 (the stone sword).
        assertEquals(4.0f, wither.attackDamage(), 1e-6f, "the wither melee");
        // setType line 291: immuneToFire = type == 1.
        assertTrue(wither.fireImmune(), "the wither never ignites");
        // tryDamage line 99: the flat 200-tick curse.
        assertEquals(200, wither.skeletonWitherEffectTicks(), "the WITHER duration");
        assertEquals(51, MobType.SKELETON.legacyTypeId, "one wire kind for both bodies");
    }

    @Test
    void theArcherKeepsItsOwnBodyAndTheBow() {
        // The MobManager arms every spawn's bow (addRandomEquipment line
        // 211) — the rig below pins it; the plain body keeps the flat hull.
        MobEntity archer = new MobEntity(952, MobType.SKELETON, new Position(0.5, 8.0, 0.5),
                new Random(96L), flatWorld());
        assertFalse(archer.skeletonWither(), "the default body is the archer");
        assertFalse(archer.fireImmune(), "the archer burns");
        assertEquals(0.6, archer.bodyWidth(), 1e-9);
        assertEquals(1.8, archer.bodyHeight(), 1e-9);
        // getEyeHeight line 314: the archer's own flat 1.74.
        assertEquals(1.74, archer.eyePosition().y() - archer.position().y(), 1e-9);
        // getEyeHeight's wither arm: super.getEyeHeight reads the LIVE size
        // (2.535 * 0.85), not the kind's own 1.95.
        archer.setSkeletonWither(true);
        assertEquals(2.535 * 0.85, archer.eyePosition().y() - archer.position().y(), 1e-9,
                "the wither eye rides the taller hull");
    }

    // ------------------------------------------------------------------
    // The manager rig (the seeded rolls)
    // ------------------------------------------------------------------

    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 8;

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new ArrayList<>();

        @Override
        public void spawnLootDrop(Position position, ItemStack stack, int dimension) {
            drops.add(stack);
        }
    }

    private record Rig(MobManager manager, Loot loot) {
    }

    private Rig rig(long seed) {
        Loot loot = new Loot();
        return new Rig(new MobManager(flatWorld(), new Random(seed), loot, 1_200_000, SURFACE),
                loot);
    }

    @Test
    void theNetherRollConvertsFourOfFiveWithTheSword() {
        Rig rig = rig(201L);
        int wither = 0;
        for (int i = 0; i < 100; i++) {
            MobEntity mob = rig.manager().spawnNaturalNether(MobType.SKELETON,
                    new Position(0.5, 8.0, 0.5), false, 0.0f);
            assertNotNull(mob);
            if (mob.skeletonWither()) {
                wither++;
                // initialize line 220: the stone sword replaces the bow.
                assertEquals(BuiltinItems.STONE_SWORD, mob.heldEquipment().type(),
                        "the wither carries the sword");
            } else {
                // addRandomEquipment line 211: the archer keeps the bow.
                assertEquals(BuiltinItems.BOW, mob.heldEquipment().type(),
                        "the un-converted keeps the bow");
            }
        }
        // initialize line 217: random.nextInt(5) > 0 — the 4-in-5 law.
        assertTrue(wither >= 70 && wither <= 90,
                "the 4-in-5 conversion (was " + wither + " of 100)");
    }

    @Test
    void theOverworldNeverConverts() {
        Rig rig = rig(202L);
        for (int i = 0; i < 20; i++) {
            MobEntity mob = rig.manager().spawnNatural(MobType.SKELETON,
                    new Position(0.5, 8.0, 0.5), false, 0.0f);
            assertNotNull(mob);
            assertFalse(mob.skeletonWither(), "the overworld keeps the archer");
            assertEquals(BuiltinItems.BOW, mob.heldEquipment().type(),
                    "every spawn carries the bow (addRandomEquipment)");
        }
    }

    @Test
    void theDeathWalkFollowsTheReferenceLaw() {
        // The always-rare rig: every nextFloat answers 0.0 — the held
        // equipment always spills (0.085), the rare head always rolls
        // (0.025) — so the law pins deterministically.
        Loot loot = new Loot();
        MobManager alwaysRare = new MobManager(flatWorld(),
                new Random(203L) {
                    @Override
                    public float nextFloat() {
                        return 0.0f;
                    }
                }, loot, 1_200_000, SURFACE);
        MobEntity wither = alwaysRare.spawnAt(MobType.SKELETON, new Position(0.5, 8.0, 0.5));
        wither.setSkeletonWither(true);
        wither.setHeldEquipment(ItemStack.of(BuiltinItems.STONE_SWORD, 1));
        wither.markPlayerHit(); // LivingEntity.die line 766's gate
        alwaysRare.hurt(wither, 999.0f, 0.0f);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            alwaysRare.tick(List.of(), MobManager.NIGHT_START);
        }
        boolean sawCoal = false;
        boolean sawBone = false;
        boolean sawSword = false;
        boolean sawSkull = false;
        for (ItemStack drop : loot.drops) {
            if (drop.type().equals(BuiltinItems.COAL)) {
                sawCoal = true; // dropItems line 181-185: the coal arm
            } else if (drop.type().equals(BuiltinItems.BONE)) {
                sawBone = true; // dropItems lines 194-198: both variants
            } else if (drop.type().equals(BuiltinItems.STONE_SWORD)) {
                sawSword = true; // dropEquipment's 0.085 arm (forced)
            } else if (drop.type().equals(BuiltinItems.SKULL)) {
                sawSkull = true; // dropRareItem's wither head (forced)
                assertEquals(1, drop.damage(), "the wither variant's damage field");
            } else {
                throw new AssertionError("unexpected wither loot " + drop.type());
            }
            assertEquals(1, drop.count(), "per-unit single-item stacks");
        }
        assertTrue(sawBone || sawCoal, "the reference's own members");
        assertTrue(sawSword, "the held sword spills at the forced 0.085 roll");
        assertTrue(sawSkull, "the rare wither head at the forced 0.025 roll");

        // The archer's own walk never carries coal or the head.
        Loot archerLoot = new Loot();
        MobManager archerManager = new MobManager(flatWorld(),
                new Random(204L) {
                    @Override
                    public float nextFloat() {
                        return 0.0f;
                    }
                }, archerLoot, 1_200_500, SURFACE);
        MobEntity archer = archerManager.spawnAt(MobType.SKELETON, new Position(0.5, 8.0, 0.5));
        archer.markPlayerHit();
        archerManager.hurt(archer, 999.0f, 0.0f);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            archerManager.tick(List.of(), MobManager.NIGHT_START);
        }
        for (ItemStack drop : archerLoot.drops) {
            assertFalse(drop.type().equals(BuiltinItems.COAL),
                    "the archer never drops coal");
            assertFalse(drop.type().equals(BuiltinItems.SKULL),
                    "the archer never drops the wither head");
        }
        assertTrue(archerLoot.drops.stream().anyMatch(d -> d.type().equals(BuiltinItems.BONE)),
                "the archer's bones ride both arms");
        assertTrue(archerLoot.drops.stream().anyMatch(d -> d.type().equals(BuiltinItems.BOW)),
                "the held bow spills at the forced roll");
    }

    // ------------------------------------------------------------------
    // The live pipeline (a booted server)
    // ------------------------------------------------------------------

    private net.zaminmc.torch.server.EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "witherskeleton", "it", 20, 2, 20,
                dataDir.toString());
        net.zaminmc.torch.server.EngineServer server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        return server;
    }

    private net.zaminmc.torch.server.player.PlayerSession join(
            net.zaminmc.torch.server.EngineServer server, String name) throws InterruptedException {
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

    /** The console-shaped sender (level 4 — the /difficulty gate). */
    private static net.zaminmc.torch.server.chat.CommandSender console() {
        return new net.zaminmc.torch.server.chat.CommandSender() {
            @Override
            public String name() {
                return "Server";
            }

            @Override
            public void sendMessage(String message) {
            }

            @Override
            public int opLevel() {
                return 4;
            }

            @Override
            public net.zaminmc.torch.server.player.PlayerSession player() {
                return null;
            }
        };
    }

    @Test
    void theWitherCurseRidesTheMeleePipeline() throws Exception {
        net.zaminmc.torch.server.EngineServer server = boot();
        try {
            server.world().execute(() -> server.world().setTimeOfDay(14_000));
            await(() -> server.world().timeOfDay() >= 14_000 && server.world().timeOfDay() < 15_000,
                    "the night hours landed");
            var player = join(server, "Cursed");
            server.chatService().submitChat(player, "/spawnmob skeleton 1");
            await(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == MobType.SKELETON), "skeleton present");
            MobEntity skeleton = server.mobs().all().stream()
                    .filter(m -> m.type() == MobType.SKELETON)
                    .findFirst().orElseThrow();

            // The nether's own conversion, applied on the owner lane (the
            // initialize roll's flag + the stone-sword swap).
            server.world().execute(() -> {
                skeleton.setSkeletonWither(true);
                skeleton.setHeldEquipment(ItemStack.of(BuiltinItems.STONE_SWORD, 1));
            });

            // tryDamage lines 96-106: every landed melee hit applies the
            // flat 200-tick WITHER (tier 0) — no difficulty gate.
            await(() -> server.potions().hasEffect(player,
                            net.zaminmc.torch.server.potion.StatusEffects.WITHER),
                    "the wither curse lands");
            var curse = server.potions().get(player,
                    net.zaminmc.torch.server.potion.StatusEffects.WITHER);
            assertNotNull(curse, "the wither store carries the bite");
            assertTrue(curse.duration() <= 200 && curse.duration() > 150,
                    "the flat 10-second curse (was " + curse.duration() + ")");
            assertEquals(0, curse.amplifier(), "the tier-0 wither");

            // The archer's bite never curses (the flag's own gate).
            server.potions().clear(player);
            server.world().execute(() -> skeleton.setSkeletonWither(false));
            await(() -> player.health() < net.zaminmc.torch.server.player.PlayerSession.MAX_HEALTH,
                    "the archer's melee lands");
            assertNull(server.potions().get(player,
                    net.zaminmc.torch.server.potion.StatusEffects.WITHER),
                    "the archer never curses");
        } finally {
            server.shutdown(null);
        }
    }
}
