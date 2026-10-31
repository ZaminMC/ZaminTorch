package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.world.ExplosionEvent;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blast's entity-gather coverage (the nineteenth pass's F-EX1 — the
 * reference's damageEntities walk, Explosion.java lines 115-144): the
 * reference gathers EVERY entity class in the {@code [floor(c-f3-1),
 * floor(c+f3+1)]} box through {@code world.getEntities(source, box)} —
 * filtered ONLY by {@code !isImmuneToExplosions()} (the census: exactly ONE
 * override, ArmorStandEntity line 593, and it answers isInvisible() — a
 * VISIBLE stand takes the blast). Every gathered body inside the
 * {@code d12 <= 1} band takes the wound through its own takeDamage law and
 * the unconditional scatter {@code velocityX/Y/Z += dir * d11} (lines
 * 133-137 — the add rides OUTSIDE the takeDamage verdict).
 *
 * <p>The engine's walk carried FOUR arms alone (players, mobs, vehicles,
 * decorations) — the items, the orbs, the projectiles, the primed TNT, the
 * end crystals and the armor stands never heard a blast. These seats pin
 * the missing families' own laws:</p>
 *
 * <ul>
 *   <li><b>Items</b> (ItemEntity.takeDamage lines 202-218): health 5, the
 *       blast damage {@code (int)((d10*d10 + d10)/2*8*f3 + 1)} decrements,
 *       {@code health <= 0} removes — a close stack is DESTROYED, not
 *       dropped; the nether star refuses the explosive source whole (lines
 *       207-209); the survivors scatter.</li>
 *   <li><b>Experience orbs</b> (ExperienceOrbEntity.takeDamage lines
 *       141-151): the same health-5 shape.</li>
 *   <li><b>Projectiles</b> (the Entity default takeDamage — the wound
 *       no-ops, the scatter still lands): a flying arrow takes the impulse
 *       and diverges from its un-blasted twin.</li>
 *   <li><b>Primed TNT</b> (no override, no immunity in 1.8.8): the fuse
 *       body takes the impulse — the TNT-cannon's own law.</li>
 *   <li><b>End crystals</b> (EnderCrystalEntity.takeDamage lines 67-84):
 *       any damage removes and detonates the 6.0F blast — the End chain.</li>
 *   <li><b>Armor stands</b> (ArmorStandEntity.takeDamage lines 382-386 +
 *       isImmuneToExplosions line 593): a visible stand breaks — onBroken
 *       drops the EQUIPMENT, the stand item itself does NOT drop (the
 *       explosion arm's own quirk, onBroken lines 480-490 vs
 *       onBrokenByPlayer lines 475-478).</li>
 *   <li><b>Fireworks</b> (the Entity default): the rocket takes the impulse.</li>
 * </ul>
 *
 * <p>Harness note: the public §27.7 ExplosionEvent fires at the blast's
 * GATE (before any destruction), so the seats treat it only as the
 * detonation's proof and await the OBSERVABLE outcomes patiently on the
 * tick thread — the permanent divergences (a removed body, a scattered
 * body's drift, the twins' separation), never a same-tick snapshot.</p>
 */
class ExplosionEntityGatherParityTest {

    @TempDir
    Path dataDir;

    /**
     * SEAT 1 — the dropped items: the near stack is DESTROYED by the wound
     * (vanilla removes it, no drop), the far stack survives with damage and
     * drifts away on the scatter, and the nether star refuses the explosive
     * wound whole (its own row) while still taking the scatter.
     */
    @Test
    void theBlastDestroysAndScattersDroppedItems() throws Exception {
        EngineServer server = boot();
        try {
            // The natural flat ground (grass top at y=4, bodies at y=5).
            // The creeper at (12.5, 5, 10.5); the blast center is its feet.
            int near = spawnItemAt(server, new Position(12.5, 5.125, 13.0),
                    BuiltinItems.STONE);   // 2.5 blocks: damage 23 over health 5
            int far = spawnItemAt(server, new Position(12.5, 5.125, 16.0),
                    BuiltinItems.DIAMOND); // 5.5 blocks: damage 3, survives
            int star = spawnItemAt(server, new Position(10.0, 5.125, 10.5),
                    BuiltinItems.NETHER_STAR); // 2.5 blocks: the refusal row
            detonateCreeperAt(server, new Position(12.5, 5, 10.5));

            // The near stack: the wound (damage 23 over health 5) removes it.
            await(server, () -> itemById(server, near) == null,
                    "the near stack destroyed by the blast wound");
            // The far stack: damage 3 over health 5 survives — and scatters
            // (the horizontal drift away from its rest spot; a crater-fall
            // moves y alone, never the horizontal).
            await(server, () -> horizontalDrift(server, far, 12.5, 16.0) > 0.03,
                    "the far stack took the scatter impulse");
            // The nether star: immune to the explosive wound, scattered anyway.
            await(server, () -> horizontalDrift(server, star, 10.0, 10.5) > 0.03,
                    "the star still takes the scatter (the add rides outside "
                            + "the takeDamage verdict)");
            assertNotNull(itemById(server, star), "the star refused the wound whole");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 2 — the experience orbs: the near orb is destroyed by the wound,
     * the far orb survives and scatters.
     */
    @Test
    void theBlastDestroysAndScattersExperienceOrbs() throws Exception {
        EngineServer server = boot();
        try {
            int near = spawnOrbAt(server, new Position(12.5, 5.125, 13.0));
            int far = spawnOrbAt(server, new Position(12.5, 5.125, 16.0));
            detonateCreeperAt(server, new Position(12.5, 5, 10.5));

            await(server, () -> orbById(server, near) == null,
                    "the near orb destroyed by the blast wound");
            await(server, () -> orbDrift(server, far, 12.5, 16.0) > 0.03,
                    "the far orb took the scatter impulse");
            assertNotNull(orbById(server, far), "the far orb survives the wound");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 3 — the flying projectiles: two identical arrows (the same
     * deterministic launch, one beside the blast, one 23 blocks away as the
     * control). The blast's impulse must separate the pair — pre-fix the
     * twins fly identically forever.
     */
    @Test
    void theBlastScattersFlyingProjectiles() throws Exception {
        EngineServer server = boot();
        try {
            AtomicReference<ProjectileEntity> near = new AtomicReference<>();
            AtomicReference<ProjectileEntity> control = new AtomicReference<>();
            server.ticker().submit(() -> {
                near.set(server.projectiles().spawnRestored(
                        ProjectileEntity.Kind.ARROW,
                        new Position(15.5, 6.0, 10.5), 0.0, 0.42, 0.15));
                control.set(server.projectiles().spawnRestored(
                        ProjectileEntity.Kind.ARROW,
                        new Position(38.5, 6.0, 10.5), 0.0, 0.42, 0.15));
            });
            await(server, () -> near.get() != null && control.get() != null,
                    "the arrow pair launched");
            detonateCreeperAt(server, new Position(12.5, 5, 10.5));

            await(server, () -> Math.abs(near.get().velocityX() - control.get().velocityX())
                    > 0.02, "the near arrow diverged from its un-blasted twin");
            // The arrow family takes no removal wound: both bodies live.
            await(server, () -> server.projectiles().all().stream()
                            .anyMatch(p -> p.entityId() == near.get().entityId()),
                    "the blasted arrow survives (the Entity-default wound)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 3b — the re-aim census over the real blast wire (F-EX5's
     * resolution): the creeper-sourced blast re-aims the GHAST's fireball
     * (FireballEntity inherits ProjectileEntity.takeDamage — the blast's
     * source becomes the new shooter) but NEVER the blaze's small fireball
     * (SmallFireballEntity.takeDamage lines 62-64 answer false): the charge
     * keeps its own shooter and its own flight, taking the SCATTER alone.
     */
    @Test
    void theBlastReAimsTheGhastFireballButNeverTheBlazeCharge() throws Exception {
        EngineServer server = boot();
        try {
            AtomicReference<ProjectileEntity> near = new AtomicReference<>();
            AtomicReference<ProjectileEntity> control = new AtomicReference<>();
            AtomicReference<ProjectileEntity> ghastShot = new AtomicReference<>();
            server.ticker().submit(() -> {
                near.set(server.projectiles().spawnRestored(
                        ProjectileEntity.Kind.SMALL_FIREBALL,
                        new Position(15.5, 8.0, 10.5), 0.0, 0.0, 0.3));
                control.set(server.projectiles().spawnRestored(
                        ProjectileEntity.Kind.SMALL_FIREBALL,
                        new Position(38.5, 8.0, 10.5), 0.0, 0.0, 0.3));
                ghastShot.set(server.projectiles().launchGhastFireball(1602,
                        new Position(15.5, 9.5, 10.5), 0.0, 0.0, 1.0, 0));
            });
            await(server, () -> near.get() != null && control.get() != null
                    && ghastShot.get() != null, "the fireball trio launched");
            var creeper = server.mobs().spawnAt(MobType.CREEPER,
                    new Position(12.5, 5, 10.5));
            assertNotNull(creeper, "the creeper spawned");
            int creeperId = creeper.entityId();
            server.ticker().submit(() -> {
                creeper.setCreeperIgnited(true);
                creeper.setCreeperFuseTime(5);
            });

            // The ghast's shot: the re-aim lands — the blast's own source
            // becomes the new shooter (the inherited takeDamage arm).
            await(server, () -> ghastShot.get().throwerId() == creeperId,
                    "the creeper's blast re-aimed the ghast's fireball");
            // The blaze's charge: the SCATTER lands (the near body diverges
            // from its un-blasted twin — the add rides outside the verdict)
            // but the OVERRIDE answers false — the shooter never changes.
            await(server, () -> Math.abs(near.get().velocityX()
                    - control.get().velocityX()) > 0.02,
                    "the near charge took the scatter's impulse");
            await(server, () -> near.get().throwerId() == -1,
                    "the charge keeps no shooter (the override's refusal)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 4 — the primed TNT: the fuse body takes the impulse (vanilla's
     * PrimedTnt carries NO immunity and NO takeDamage override — the wound
     * no-ops, the scatter lands: the TNT cannon's own law).
     */
    @Test
    void theBlastScattersPrimedTnt() throws Exception {
        EngineServer server = boot();
        try {
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(15, 5, 10), BuiltinBlocks.TNT));
            await(server, () -> server.world().getBlock(new BlockPosition(15, 5, 10))
                    .identifier().value().equals("tnt"), "the TNT placed");
            server.ticker().submit(() ->
                    server.tnt().useIgnite(server.world(), new BlockPosition(15, 5, 10)));
            await(server, () -> server.world().getBlock(new BlockPosition(15, 5, 10))
                    .identifier().value().equals("air"), "the TNT primed");
            // Let the priming hop settle (the 0.2 rise decays to rest).
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(
                    server.world(), 12, "the primed body settles");
            detonateCreeperAt(server, new Position(12.5, 5, 10.5));

            await(server, () -> {
                List<net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt> bodies =
                        server.tnt().allPrimed();
                if (bodies.isEmpty()) {
                    return false;
                }
                var body = bodies.get(0);
                return Math.abs(body.velocityX()) + Math.abs(body.velocityZ()) > 0.05;
            }, "the primed TNT took the scatter impulse");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 5 — the end crystal chain: any damage to a crystal removes it
     * and detonates its own 6.0F blast (EnderCrystalEntity.takeDamage lines
     * 67-84) — so the FIRST crystal's blast must wound the second through
     * the same gather, chaining the detonation vanilla-famous through the
     * End pillars.
     */
    @Test
    void theBlastChainsEndCrystals() throws Exception {
        EngineServer server = boot();
        try {
            AtomicReference<Boolean> a = new AtomicReference<>();
            AtomicReference<Boolean> b = new AtomicReference<>();
            server.ticker().submit(() -> {
                a.set(server.enderCrystals().spawn(10.5, 45, 10.5, 0.0f));
                b.set(server.enderCrystals().spawn(13.5, 45, 10.5, 0.0f));
            });
            await(server, () -> a.get() != null && b.get() != null,
                    "the crystal pair spawned");
            List<ExplosionEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
            var sub = ((net.zaminmc.torch.Server) server).events()
                    .on(ExplosionEvent.class, events::add);

            // The first crystal's own death blast (the arrow's pop path —
            // any damage detonates): the chain rides the blast's gather.
            server.ticker().submit(() -> {
                var first = server.enderCrystals().at(10.5, 45, 10.5);
                server.enderCrystals().damage(first);
            });
            await(server, () -> events.size() >= 2, "both blasts detonated");
            sub.unsubscribe();

            await(server, () -> server.enderCrystals().at(10.5, 45, 10.5) == null,
                    "the first crystal is gone (its own death)");
            await(server, () -> server.enderCrystals().at(13.5, 45, 10.5) == null,
                    "the second crystal chained — the blast's gather wounded it");
            assertEquals(2, events.size(), "exactly the two chained blasts");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 6 — the armor stands: a visible stand in the band breaks — the
     * equipment drops through onBroken, the stand item does NOT (the
     * explosion arm's own quirk); a stand outside the band never hears the
     * blast.
     */
    @Test
    void theBlastBreaksArmorStands() throws Exception {
        EngineServer server = boot();
        try {
            AtomicReference<Boolean> near = new AtomicReference<>();
            AtomicReference<Boolean> far = new AtomicReference<>();
            server.ticker().submit(() -> {
                near.set(server.armorStands().place(
                        new BlockPosition(15, 4, 10), 0, 1, 0.0f, new java.util.Random(10)));
                far.set(server.armorStands().place(
                        new BlockPosition(35, 4, 10), 0, 1, 0.0f, new java.util.Random(10)));
            });
            await(server, () -> near.get() != null && far.get() != null,
                    "the stand pair placed");
            assertTrue(near.get(), "the near stand placed");
            assertTrue(far.get(), "the far stand placed");
            detonateCreeperAt(server, new Position(12.5, 5, 10.5));
            await(server, () -> server.armorStands().allStands().stream()
                    .noneMatch(s -> (int) Math.floor(s.x()) == 15), "the near stand swept");

            assertTrue(server.armorStands().allStands().stream()
                            .noneMatch(s -> (int) Math.floor(s.x()) == 15),
                    "the near stand broke in the blast");
            assertTrue(server.armorStands().allStands().stream()
                            .anyMatch(s -> (int) Math.floor(s.x()) == 35),
                    "the far stand never heard the blast");
            // The explosion arm's own quirk: NO armor stand item drops
            // (onBroken drops the equipment alone — a bare stand drops
            // nothing at all).
            AtomicReference<Boolean> standItem = new AtomicReference<>();
            server.ticker().submit(() -> {
                for (ItemEntity item : server.itemEntities().all()) {
                    if (item.stack().type().identifier().value().equals("armor_stand")) {
                        standItem.set(true);
                        return;
                    }
                }
                standItem.set(false);
            });
            await(server, () -> standItem.get() != null, "the item census answered");
            assertFalse(standItem.get(),
                    "the explosion-broken stand drops NO stand item (onBroken's own quirk)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 7 — the firework rocket: the Entity-default wound no-ops, the
     * scatter still lands (a zero-horizontal restored rocket takes the
     * blast's horizontal impulse whole).
     */
    @Test
    void theBlastScattersFireworks() throws Exception {
        EngineServer server = boot();
        try {
            AtomicReference<net.zaminmc.torch.server.fireworks.FireworksEntity> rocket =
                    new AtomicReference<>();
            server.ticker().submit(() -> rocket.set(server.fireworks().launchRestored(
                    new Position(15.5, 6.0, 10.5), 0.0, 0.05, 0.0, 0, 400,
                    ItemStack.of(BuiltinItems.FIREWORKS, 1))));
            await(server, () -> rocket.get() != null, "the rocket launched");
            detonateCreeperAt(server, new Position(12.5, 5, 10.5));

            await(server, () -> Math.abs(rocket.get().velocityX())
                    + Math.abs(rocket.get().velocityZ()) > 0.02,
                    "the rocket took the scatter impulse");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * SEAT 8 — the crystal arm's dimension gate (the review rider over the
     * merge): the crystal registry is the END's own family (Slice 62 — the
     * system's surface IS the End world), and the reference's
     * {@code world.getEntities} never crosses worlds. An OVERWORLD blast
     * at exactly coinciding coordinates can never touch an End crystal:
     * no pop, no chained 6.0F detonation in the End.
     */
    @Test
    void theOverworldBlastNeverTouchesTheEndCrystals() throws Exception {
        EngineServer server = boot();
        try {
            // The End crystal at the very coordinates the overworld creeper
            // will occupy — the leak's own worst case.
            AtomicReference<Boolean> spawned = new AtomicReference<>();
            server.ticker().submit(() -> spawned.set(
                    server.enderCrystals().spawn(12.5, 5, 10.5, 0.0f)));
            await(server, () -> spawned.get() != null, "the End crystal spawned");
            assertTrue(spawned.get(), "the crystal stands in the End registry");
            List<ExplosionEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
            var sub = ((net.zaminmc.torch.Server) server).events()
                    .on(ExplosionEvent.class, events::add);

            detonateCreeperAt(server, new Position(12.5, 5, 10.5));
            await(server, () -> events.size() >= 1, "the creeper blast detonated");
            // Let the wound phase (and any would-be chain) settle: five
            // ticks of overworld time.
            long atBlast = server.world().timeOfDay();
            await(server, () -> server.world().timeOfDay() >= atBlast + 5,
                    "the blast settled");
            sub.unsubscribe();

            assertNotNull(server.enderCrystals().at(12.5, 5, 10.5),
                    "the End crystal never heard the overworld blast");
            assertEquals(1, events.size(),
                    "exactly the creeper's own blast — no chained End detonation");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private static ItemEntity itemById(EngineServer server, int entityId) {
        return server.itemEntities().all().stream()
                .filter(i -> i.entityId() == entityId).findFirst().orElse(null);
    }

    private static net.zaminmc.torch.server.experience.ExperienceOrbEntity orbById(
            EngineServer server, int entityId) {
        return server.experienceOrbs().all().stream()
                .filter(o -> o.entityId() == entityId).findFirst().orElse(null);
    }

    /** The scattered body's horizontal drift from its rest spot. */
    private static double horizontalDrift(EngineServer server, int entityId,
                                          double restX, double restZ) {
        ItemEntity item = itemById(server, entityId);
        if (item == null) {
            return -1.0;
        }
        Position p = item.position();
        return Math.abs(p.x() - restX) + Math.abs(p.z() - restZ);
    }

    private static double orbDrift(EngineServer server, int entityId,
                                   double restX, double restZ) {
        var orb = orbById(server, entityId);
        if (orb == null) {
            return -1.0;
        }
        Position p = orb.position();
        return Math.abs(p.x() - restX) + Math.abs(p.z() - restZ);
    }

    private static int spawnItemAt(EngineServer server, Position at,
                                   net.zaminmc.torch.item.ItemType type)
            throws InterruptedException {
        AtomicReference<Integer> id = new AtomicReference<>();
        server.ticker().submit(() -> {
            ItemEntity spawned = server.itemEntities().spawnRestored(
                    at, ItemStack.of(type, 1));
            id.set(spawned == null ? -1 : spawned.entityId());
        });
        await(server, () -> id.get() != null, "the item spawned at " + at);
        assertTrue(id.get() > 0, "the item spawned (the §27.7 gate open)");
        return id.get();
    }

    private static int spawnOrbAt(EngineServer server, Position at)
            throws InterruptedException {
        AtomicReference<Integer> id = new AtomicReference<>();
        server.ticker().submit(() -> {
            var orb = server.experienceOrbs().spawnRestored(at, 3, 0);
            id.set(orb == null ? -1 : orb.entityId());
        });
        await(server, () -> id.get() != null, "the orb spawned at " + at);
        assertTrue(id.get() > 0, "the orb spawned");
        return id.get();
    }

    /**
     * The creeper's own blast (CreeperEntity.explode lines 206-213): the
     * body's feet the center, power 3 (f3 = 6), mobGriefing's destructive
     * default on. The 5-tick fuse forces the detonation without a player.
     */
    private static void detonateCreeperAt(EngineServer server, Position at)
            throws InterruptedException {
        var creeper = server.mobs().spawnAt(MobType.CREEPER, at);
        assertNotNull(creeper, "the creeper spawned");
        server.ticker().submit(() -> {
            creeper.setCreeperIgnited(true);
            creeper.setCreeperFuseTime(5);
        });
    }

    private static void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "exgt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
