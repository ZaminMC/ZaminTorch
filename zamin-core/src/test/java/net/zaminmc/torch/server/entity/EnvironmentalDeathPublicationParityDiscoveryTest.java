package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave EK — THE ENVIRONMENTAL KILLS' DEATH PUBLICATION (the find the PP
 * wave's scoping surfaced): the body-level {@code hurtThroughFrame} arms
 * — the fire clock, the drown, the lava, the void, the wall, the starve,
 * the snow golem's melt, the anvil crush — walk the reference's own
 * takeDamage whole in the engine (the frame consult, the excess arm's
 * flag law), but their KILLS never reached the publication walk the
 * melee and magic families ride: no death voice, no status 3, no
 * MobDeathEvent, no XP burst — the body died through the
 * death-animation loop's silent remove. The reference runs die() inside
 * the damage walk itself (LivingEntity.takeDamage lines 709-723 + die
 * line 760): the death VOICE rides the wound's flag gate alone (a kill
 * through the excess arm dies SILENT — the PP wave's own law), the
 * status 3 rides die() unconditionally, and the whole publication now
 * lands through the listener the melee path fires — the drain at the
 * body's own tick (the same-tick shape) and at the loop's head (the
 * cross-phase kills, the anvil's crush landing in the block phase).
 */
class EnvironmentalDeathPublicationParityDiscoveryTest {

    /** The flat stub world with the submersion flag the drowning seat arms. */
    private static final class DrownWorld implements MobEntity.WorldQuery {
        boolean submerged;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return false;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return submerged;
        }
    }

    private static MobManager managerIn(MobEntity.WorldQuery world, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        return new MobManager(world, new Random(seed), new NoLoot(),
                997_000, (x, z) -> 8);
    }

    /** The death-publication recorder (the listener the adapter rides). */
    private static final class DeathLog implements MobManager.Listener {
        final List<Boolean> voicelessVerdicts = new ArrayList<>();

        @Override public void onMobSpawned(MobEntity mob) { }
        @Override public void onMobMoved(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession rider) { }
        @Override public void onMobHurt(MobEntity mob) { }
        @Override public void onMobDied(MobEntity mob) {
            voicelessVerdicts.add(false); // the legacy voiceful form
        }
        @Override public void onMobDied(MobEntity mob, boolean voiceless) {
            voicelessVerdicts.add(voiceless);
        }
        @Override public void onMobRemoved(MobEntity mob, String reason) { }
        @Override public void onMobAttackedPlayer(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession target,
                float damage) { }
        @Override public void onMobSound(MobEntity mob, String soundName) { }
        @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
        @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
        @Override public void onMobSheared(MobEntity mob, int woolCount) { }
        @Override public void onMobCoatRegrown(MobEntity mob) { }
        @Override public void onMobExploded(MobEntity mob) { }
    }

    // ------------------------------------------------------------------
    // Seat 1 — the fresh environmental kill publishes VOICEFUL
    // ------------------------------------------------------------------

    /**
     * A body-level arm's fresh killing wound (the fire clock's own call
     * shape, driven directly): the publication lands on the SAME TICK
     * through the drain after the body's own tick — the death voice, the
     * status 3, the MobDeathEvent, the XP burst — exactly once, in the
     * VOICEFUL form. RED on the pre-fix engine (zero publications — the
     * body died through the silent remove).
     */
    @Test
    void theFreshEnvironmentalKillPublishesVoiceful() {
        MobManager manager = managerIn(new DrownWorld(), 95L);
        DeathLog deaths = new DeathLog();
        manager.addListener(deaths);
        MobEntity sheep = manager.spawnAt(MobType.SHEEP,
                new Position(0.5, 8.0, 0.5));
        assertNotNull(sheep, "the spawn never cancels here");
        sheep.setNoAi(true); // the wounds continue — the pin gates the mind alone
        sheep.hurt(6.0f, true); // 8 -> 2.0 health

        sheep.hurtThroughFrame(2.0f, true); // the fresh killing wound
        assertTrue(sheep.dead(), "the wound killed the body");

        manager.tick(List.of(), 6000L);
        assertEquals(List.of(false), deaths.voicelessVerdicts,
                "the fresh environmental kill publishes VOICEFUL — exactly"
                        + " once, the same tick (the pre-fix engine never"
                        + " published it at all)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the excess arm's environmental kill dies SILENT
    // ------------------------------------------------------------------

    /**
     * The flag gate's own law over the environmental family (the PP
     * wave's find generalized): a killing wound through the frame's
     * EXCESS arm — a stronger wound inside the window's first half pays
     * its difference alone — dies SILENT: the status 3 alone, no death
     * voice.
     */
    @Test
    void theExcessEnvironmentalKillDiesSilent() {
        MobManager manager = managerIn(new DrownWorld(), 96L);
        DeathLog deaths = new DeathLog();
        manager.addListener(deaths);
        MobEntity sheep = manager.spawnAt(MobType.SHEEP,
                new Position(0.5, 8.0, 0.5));
        assertNotNull(sheep, "the spawn never cancels here");
        sheep.setNoAi(true);
        sheep.hurt(5.5f, true); // 8 -> 2.5 health

        sheep.hurtThroughFrame(1.0f, true); // the frame stands at 1.0
        assertEquals(1.5f, sheep.health(), 1e-9, "the first wound landed");
        sheep.hurtThroughFrame(4.0f, true); // the excess 3.0 kills the 1.5 body
        assertTrue(sheep.dead(), "the 3.0 excess kills the 1.5-health body");

        manager.tick(List.of(), 6000L);
        assertEquals(List.of(true), deaths.voicelessVerdicts,
                "the excess arm's environmental kill dies SILENT — the"
                        + " death voice rides the flag gate alone");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the drowning sheep publishes its death end-to-end
    // ------------------------------------------------------------------

    /**
     * The real arm: a submerged sheep's breath runs out (300 ticks of
     * air, then the 2.0 DROWN every 20) and the drowning kill publishes
     * — the fresh form (the wounds 20 ticks apart, the frame long
     * expired between them). RED on the pre-fix engine: the body drowned
     * through the silent remove.
     */
    @Test
    void theDrowningSheepPublishesItsDeath() {
        DrownWorld world = new DrownWorld();
        world.submerged = true;
        MobManager manager = managerIn(world, 97L);
        DeathLog deaths = new DeathLog();
        manager.addListener(deaths);
        MobEntity sheep = manager.spawnAt(MobType.SHEEP,
                new Position(0.5, 8.0, 0.5));
        assertNotNull(sheep, "the spawn never cancels here");
        sheep.setNoAi(true);
        sheep.hurt(6.0f, true); // 8 -> 2.0 health: the first drown wound kills

        for (int tick = 0; tick < 340 && !sheep.dead(); tick++) {
            manager.tick(List.of(), 6000L);
        }
        assertTrue(sheep.dead(), "the breath ran out and the drown killed");
        assertEquals(List.of(false), deaths.voicelessVerdicts,
                "the drowning kill publishes VOICEFUL (the wound's own"
                        + " tick — the same-tick drain)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the cross-phase kill (the anvil's crush) publishes
    // ------------------------------------------------------------------

    /**
     * The head drain's own service: a wound that lands OUTSIDE the mob
     * tick's own phase — the falling anvil's crush (the block phase,
     * before the mobs' loop) — publishes on the loop's very next
     * iteration, whatever phase killed the body.
     */
    @Test
    void theCrossPhaseCrushPublishesOnTheNextLoopHead() {
        MobManager manager = managerIn(new DrownWorld(), 98L);
        DeathLog deaths = new DeathLog();
        manager.addListener(deaths);
        MobEntity sheep = manager.spawnAt(MobType.SHEEP,
                new Position(0.5, 8.0, 0.5));
        assertNotNull(sheep, "the spawn never cancels here");
        sheep.setNoAi(true);

        // The anvil's crush shape (EngineServer's own call: the wound
        // rides the shared hurt frame, bypassing nothing).
        sheep.hurtThroughFrame(40.0f, false);
        assertTrue(sheep.dead(), "the crush killed the body");

        manager.tick(List.of(), 6000L);
        assertEquals(List.of(false), deaths.voicelessVerdicts,
                "the cross-phase kill publishes at the loop's head — the"
                        + " same listener, the same voiceful form");
    }
}
