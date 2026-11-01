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
 * THE VERIFIER'S VT-029 SEATS (pass 2 — the behavioral construction) —
 * the orb's BOX MODEL: the size and the anchoring. Pass 1 (the
 * forty-fourth pass's third act) established by the source walk:
 *
 * <ul>
 * <li><b>The SIZE diverges</b> — the engine's orb is a 0.25×0.25 box
 * ({@code HALF_WIDTH/HALF_HEIGHT = 0.125}) where the reference's
 * SPAWNED orb is <b>0.5×0.5</b> ({@code ExperienceOrbEntity}'s xp
 * constructor, line 22 — {@code setSize(0.5F, 0.5F)}; every gameplay
 * spawn site goes through it: the ore/break xp, the mob deaths, the
 * breeding, the trading, the dragon's death rain). The 0.25 default
 * constructor (line 35) is the entity-LOAD path alone, a vanilla
 * quirk. The world-space consequences seated below: the pickup
 * envelope's horizontal reach 1.425 vs the reference's 1.55, and the
 * box's vertical extent 0.25 vs 0.5.</li>
 * <li><b>The ANCHORING diverges</b> — the engine's restored orb
 * centers the box at the saved y (the engine's center convention,
 * box [y−0.125, y+0.125]) where the reference's LOAD path seats the
 * box BOTTOM at the saved y ({@code setPosition}'s feet law over the
 * 0.25 load-path constructor, box [y, y+0.25]).</li>
 * <li><b>The envelope FORM is correct</b> — the player's 0.3
 * half-width IS carried (the 1.425 = 0.3 + 0.125 + 1.0 answers the
 * reference's grown-box intersection {@code |ox−px| < 1.3 + half});
 * the vertical center-convention box is the true form under the
 * engine's convention. The pass-38 record's suspicion ("the family
 * shares the shortfall") was CORRECTED by pass 1: the form is right,
 * the SIZE and the ANCHORING are the divergences.</li>
 * </ul>
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the pickup rides the
 * player's grown-box walk — {@code PlayerEntity.playerTick} 484-505:
 * the player's own box {@code setSize(0.6F, 1.8F)} grown
 * {@code (1.0, 0.5, 1.0)}, {@code world.getEntities} over the STRICT
 * box intersection ({@code Box.intersects} 164-171, all six faces
 * strict), then {@code onPlayerCollision} (line 170: the delay gate,
 * the cooldown, the {@code random.orb} sound, the collect). The
 * spawned orb's box: [x−0.25, x+0.25] × [y, y+0.5] (the constructor's
 * feet-law y).</p>
 *
 * <p>THE CONSTRUCTION DISCIPLINE: the seats use the RESTORED form
 * ({@code spawnRestored} — no scatter velocity, no pickup delay, the
 * reference's own load shape) placed EXACTLY at the rest height, so
 * the orb neither drifts nor falls before the read; the attraction
 * never engages (the retarget window opens at tick ~21 for a
 * first-entity-id of 1 — every seat reads within five ticks). The
 * seats are EXPECTED RED on develop until VT-029's correction lands;
 * the finding record owns the mismatch.</p>
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
     * THE HORIZONTAL SHORTFALL — an orb resting 1.45 blocks beside the
     * walker collects in vanilla and stands in the engine. The
     * reference's reach: the grown box [px−1.3, px+1.3] intersecting
     * the orb's [ox−0.25, ox+0.25] — 1.45 &lt; 1.55 strictly, the
     * orb's west face (1.7) inside the grown east edge (1.8). The
     * engine's cap: 0.3 + 0.125 + 1.0 = 1.425 — the 0.125 half-size
     * shortfall leaves the orb standing.
     */
    @Test
    void theWalkerCollectsTheOrbOnePointFourFiveBesideThem() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnRestored(
                new Position(1.95, 64.0 + ExperienceOrbEntity.HALF_HEIGHT, 0.5), 5, 0);
        assertNotNull(orb, "the orb landed");
        PlayerSession walker = player("Walker");
        walker.applyMovement(new Position(0.5, 64.0, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }
        assertEquals(0, manager.size(),
                "the reference's orb (0.5 wide, reach 1.55) collects at dx=1.45 — the "
                        + "orb's west face 1.7 strictly inside the grown box's east edge "
                        + "1.8; the engine's 0.25-wide orb caps the reach at 1.425 and the "
                        + "orb stands uncollected");
        assertEquals(5, walker.totalXp(), "the points rode into the total");
    }

    /**
     * THE VERTICAL WINDOW AT REST — a player whose feet are 0.8 above
     * the orb's floor collects in vanilla and misses in the engine.
     * The reference's orb box at rest spans [floor, floor+0.5]: its
     * top (64.5) clears the grown box's bottom (py−0.5 = 64.3). The
     * engine's 0.25-tall box tops out at floor+0.25 (64.25) — 0.05
     * short of the same edge.
     */
    @Test
    void thePlayerZeroPointEightAboveTheOrbsFloorCollectsIt() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnRestored(
                new Position(0.5, 64.0 + ExperienceOrbEntity.HALF_HEIGHT, 0.5), 5, 0);
        assertNotNull(orb, "the orb landed");
        PlayerSession above = player("Above");
        above.applyMovement(new Position(0.5, 64.8, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(above));
        }
        assertEquals(0, manager.size(),
                "the reference's orb box spans [floor, floor+0.5] — a player 0.8 above "
                        + "the floor clears the box top by 0.2 (64.5 > 64.3); the engine's "
                        + "0.25-tall box tops out at floor+0.25 and the same player misses "
                        + "by 0.05");
        assertEquals(5, above.totalXp(), "the points rode into the total");
    }

    /**
     * THE RESTORED ANCHORING (the load path's box seat) — a restored
     * orb's first-read box seats its BOTTOM at the saved y in vanilla
     * (setPosition's feet law over the 0.25 load-path constructor) and
     * its CENTER at the saved y in the engine. The discriminator: the
     * player 0.65 above the saved y — the grown box's bottom
     * (firstRead+0.15) falls inside the reference's box (top
     * firstRead+0.25) but above the engine's (top firstRead+0.125).
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
     * THE CONTROL — an orb resting 1.3 blocks beside the walker
     * collects in BOTH worlds (inside the engine's 1.425 cap and the
     * reference's 1.55 reach alike): the rig's sanity, isolating the
     * size as the sole divergence at 1.45. GREEN on develop.
     */
    @Test
    void theOrbOnePointThreeBesideTheWalkerCollectsInBothWorlds() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnRestored(
                new Position(1.8, 64.0 + ExperienceOrbEntity.HALF_HEIGHT, 0.5), 5, 0);
        assertNotNull(orb, "the control orb landed");
        PlayerSession walker = player("ControlWalker");
        walker.applyMovement(new Position(0.5, 64.0, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }
        assertEquals(0, manager.size(),
                "dx=1.3 sits inside both caps (the engine's 1.425, the reference's "
                        + "1.55) — the rig collects when it should");
        assertEquals(5, walker.totalXp(), "the points rode into the total");
    }
}
