package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-029 SEATS (pass 2 — the behavioral construction, the
 * corrected form) — the orb's BOX MODEL: the size and the anchoring.
 * Pass 1 (the forty-fourth pass's third act) established by the source
 * walk; this pass's first construction was CORRECTED at the Phase E
 * assessment (the record's own discipline — the error and its lesson
 * recorded in the audit report):
 *
 * <ul>
 * <li><b>The construction law</b>: the SIZE slice lives at the SPAWN
 * path ALONE — the reference's spawned orb is <b>0.5×0.5</b>
 * ({@code ExperienceOrbEntity}'s xp constructor, line 22 — every
 * gameplay spawn site: the ten direct sites plus
 * {@code Block.dropExperience} line 530), while the reference's LOADED
 * orb is 0.25×0.25 (the default constructor, line 35 — the
 * {@code Entities.register} reflective path, a vanilla quirk). The
 * engine's single 0.25 model MATCHES the load path's size; the
 * shortfall shows only for gameplay-spawned orbs. The size seats
 * therefore use the SPAWNED form (settled to rest before the read);
 * the anchoring seat uses the RESTORED form (both worlds 0.25 there —
 * the anchoring alone separates).</li>
 * <li><b>The SIZE consequence</b>: the pickup envelope's horizontal
 * reach — the engine's 0.3 + 0.125 + 1.0 = 1.425 against the spawned
 * reference's 0.3 + 0.25 + 1.0 = 1.55 — and the box's vertical extent
 * at rest (0.25 tall vs 0.5).</li>
 * <li><b>The ANCHORING consequence</b>: the engine seats the box
 * CENTER at the given y (the engine's convention) where the reference
 * seats the box BOTTOM at the given y ({@code setPosition}'s feet
 * law) — a 0.125 offset at the load path, and at the spawn path a
 * DOUBLE lift (the engine's +0.2 center lift on top of the callers'
 * own +0.5, the pass-3 census).</li>
 * <li><b>The envelope FORM is correct</b> — the player's 0.3
 * half-width IS carried; the pass-38 record's suspicion was CORRECTED
 * by pass 1: the form is right, the SIZE and the ANCHORING are the
 * divergences.</li>
 * </ul>
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the pickup rides the
 * player's grown-box walk — {@code PlayerEntity.playerTick} 484-505:
 * the player's own box {@code setSize(0.6F, 1.8F)} grown
 * {@code (1.0, 0.5, 1.0)}, {@code world.getEntities} over the STRICT
 * box intersection ({@code Box.intersects} 164-171, all six faces
 * strict), then {@code onPlayerCollision} (line 170: the delay gate,
 * the cooldown, the {@code random.orb} sound, the collect).</p>
 *
 * <p>THE CONSTRUCTION DISCIPLINE: the spawned seats settle the orb to
 * rest with NO players in the world (the scatter decays under the
 * ground friction, the pickup delay expires, the retarget windows
 * find no one), then read the rest position and place the walker
 * RELATIVE to it (the construction is seed-stable but never
 * hand-computed); the attraction never engages inside the read window
 * (the retarget window re-arms ~20 ticks after the join; every seat
 * reads within five). The seats are EXPECTED RED on develop until
 * VT-029's correction lands; the finding record owns the mismatch.</p>
 */
class OrbBoxModelDiscoveryTest {

    /** The flat floor at y=64 (the family's own ground stub). */
    private static final class FlatGround implements ExperienceOrbEntity.Ground {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < 64.0;
        }
    }

    private static PlayerSession player(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        return session;
    }

    /** Minimal world stub: sessions need a World reference only. */
    private static net.zaminmc.torch.World stubWorld() {
        return new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public net.zaminmc.torch.util.Position spawnPosition() {
                return net.zaminmc.torch.util.Position.ZERO;
            }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        };
    }

    /**
     * THE HORIZONTAL SHORTFALL (the spawned form) — a gameplay-spawned
     * orb resting 1.45 blocks beside the walker collects in vanilla and
     * stands in the engine. The reference's spawned orb box spans
     * [ox−0.25, ox+0.25]: its west face at dx=1.45 (ox−px−0.25 = 1.2)
     * sits strictly inside the grown box's east edge (px+1.3). The
     * engine's 0.25-wide orb caps the reach at 1.425 — the 0.125
     * half-size shortfall leaves the orb standing.
     */
    @Test
    void theWalkerCollectsTheSpawnedOrbOnePointFourFiveBesideThem() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnOrb(new Position(2.0, 64.4, 0.5), 5);
        assertNotNull(orb, "the orb entered the world");
        // The settle: no players, the scatter decays, the orb rests — 75
        // ticks leave the read window inside the attraction's retarget
        // gap (the windows run every ~20 ticks: 21/41/61/81 for a
        // first-entity-id of 1; the reads at 76-80 fall before tick 81's
        // window, so the PULL never engages and the envelope alone is
        // under test — the reference's own retarget cadence, the same
        // gap).
        for (int i = 0; i < 75; i++) {
            manager.tick(List.of());
        }
        assertTrue(orb.onGround(), "the construction settles: the spawned orb rests on the floor");
        double restX = orb.position().x();
        double restZ = orb.position().z();
        PlayerSession walker = player("Walker");
        walker.applyMovement(new Position(restX - 1.45, 64.0, restZ),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }
        assertEquals(0, manager.size(),
                "the reference's SPAWNED orb (0.5 wide, reach 1.55) collects at dx=1.45 — "
                        + "the orb's west face (dx-0.25 = 1.2) strictly inside the grown box's "
                        + "east edge (1.3); the engine's 0.25-wide orb caps the reach at 1.425 "
                        + "and the orb stands uncollected");
        assertEquals(5, walker.totalXp(), "the points rode into the total");
    }

    /**
     * THE VERTICAL WINDOW AT REST (the spawned form) — a player whose
     * feet are 0.8 above the orb's floor collects the spawned orb in
     * vanilla and misses it in the engine. The reference's spawned box
     * at rest spans [floor, floor+0.5]: its top (64.5) clears the grown
     * box's bottom (py−0.5 = 64.3). The engine's 0.25-tall box tops out
     * at floor+0.25 (64.25) — 0.05 short of the same edge.
     */
    @Test
    void thePlayerZeroPointEightAboveTheSpawnedOrbsFloorCollectsIt() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnOrb(new Position(2.0, 64.4, 0.5), 5);
        assertNotNull(orb, "the orb entered the world");
        for (int i = 0; i < 75; i++) { // the pull-free settle (see the first seat's note)
            manager.tick(List.of());
        }
        assertTrue(orb.onGround(), "the construction settles: the spawned orb rests on the floor");
        double restX = orb.position().x();
        double restZ = orb.position().z();
        PlayerSession above = player("Above");
        above.applyMovement(new Position(restX, 64.8, restZ),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(above));
        }
        assertEquals(0, manager.size(),
                "the reference's spawned orb box spans [floor, floor+0.5] — a player 0.8 "
                        + "above the floor clears the box top by 0.2 (64.5 > 64.3); the "
                        + "engine's 0.25-tall box tops out at floor+0.25 and the same player "
                        + "misses by 0.05");
        assertEquals(5, above.totalXp(), "the points rode into the total");
    }

    /**
     * THE RESTORED ANCHORING (the load path's box seat — the restored
     * form, both worlds 0.25 wide): a restored orb's first-read box
     * seats its BOTTOM at the saved y in vanilla (setPosition's feet
     * law over the 0.25 load-path constructor) and its CENTER at the
     * saved y in the engine. The discriminator: the player 0.65 above
     * the saved y — the grown box's bottom (firstRead+0.15) falls
     * inside the reference's box (top firstRead+0.25) but above the
     * engine's (top firstRead+0.125).
     *
     * <p>The first read is one physics tick after the restore: the
     * fall is identical in both worlds (gravity 0.03 pre-move, no
     * clip at this height), so the saved-y anchoring alone separates
     * the boxes.</p>
     */
    @Test
    void theRestoredOrbsBoxSeatsItsBottomAtTheSavedY() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnRestored(
                new Position(0.5, 70.0, 0.5), 5, 0);
        assertNotNull(orb, "the orb landed");
        PlayerSession above = player("AboveTheFall");
        above.applyMovement(new Position(0.5, 70.65, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        // One tick: the orb falls to 69.97 (gravity 0.03, no clip), the
        // pickup reads the box at that instant.
        manager.tick(List.of(above));
        assertEquals(0, manager.size(),
                "the reference's LOADED orb seats its box bottom at the saved y (the "
                        + "feet law): the first-read box [69.97, 70.22] clears the grown "
                        + "box's bottom edge (70.15) by 0.07 and collects; the engine's "
                        + "center-anchored box [69.845, 70.095] misses the same edge by "
                        + "0.055");
        assertEquals(5, above.totalXp(), "the points rode into the total");
    }

    /**
     * THE VOID-KILL PLANE (the fix-time re-verification the finding's
     * record named — the reference's own kill law, Entity.baseTick line
     * 342 {@code this.y < -64.0} over the FEET y): a spawned orb whose
     * box BOTTOM sits below -64 dies in vanilla whatever its center
     * reads. The discriminator band: a 0.5 box with bottom at -64.15
     * (center -63.9) — the engine's pre-fix center-plane law kept it
     * (the center -63.9 not below -64); the feet law kills it.
     */
    @Test
    void theVoidKillPlaneReadsTheBoxBottomNotTheCenter() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        // The spawn path seats the box bottom at the passed y: bottom
        // -64.15, center -63.9 — inside the band where the two laws
        // disagree (the pre-fix center law keeps it, the reference's
        // feet law kills it).
        manager.spawnOrb(new Position(0.5, -64.15, 0.5), 5);
        assertEquals(1, manager.size(), "the orb entered the world below the floor");
        manager.tick(List.of());
        assertEquals(0, manager.size(),
                "the reference's kill plane reads the box BOTTOM (Entity.baseTick line 342, "
                        + "this.y < -64.0 over the feet): an orb whose bottom sits at -64.15 "
                        + "dies whatever its center reads — the pre-fix center-plane law kept "
                        + "this orb alive");
    }

    /**
     * THE CONTROL (the spawned form) — a gameplay-spawned orb resting
     * 1.3 blocks beside the walker collects in BOTH worlds (inside the
     * engine's 1.425 cap and the spawned reference's 1.55 reach alike):
     * the rig's sanity, isolating the size as the sole divergence at
     * 1.45. GREEN on develop.
     */
    @Test
    void theSpawnedOrbOnePointThreeBesideTheWalkerCollectsInBothWorlds() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnOrb(new Position(2.0, 64.4, 0.5), 5);
        assertNotNull(orb, "the control orb entered the world");
        for (int i = 0; i < 75; i++) { // the pull-free settle (see the first seat's note)
            manager.tick(List.of());
        }
        assertTrue(orb.onGround(), "the construction settles: the control orb rests");
        double restX = orb.position().x();
        double restZ = orb.position().z();
        PlayerSession walker = player("ControlWalker");
        walker.applyMovement(new Position(restX - 1.3, 64.0, restZ),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }
        assertEquals(0, manager.size(),
                "dx=1.3 sits inside both caps (the engine's 1.425, the spawned reference's "
                        + "1.55) — the rig collects when it should");
        assertEquals(5, walker.totalXp(), "the points rode into the total");
    }
}
