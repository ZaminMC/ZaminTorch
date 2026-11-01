package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-038 SEATS (pass 2 — the behavioral construction) —
 * the ITEM family's box anchoring: the same center-vs-bottom question
 * VT-037 corrected at the orb, at the item's own 0.25 box.
 *
 * <p>THE CONSTRUCTION LAW (the pass-1 census): the reference's
 * {@code ItemEntity(World, x, y, z, ItemStack)} (lines 36-45) seats the
 * box BOTTOM at the passed y — {@code setPosition}'s feet law (Entity
 * line 240) — on EVERY path (the 4-arg constructor and the no-arg load
 * constructor are the SAME 0.25 size; the item family has no
 * two-constructor quirk). The engine's {@code ItemEntity} stores the
 * passed position as the box CENTER (the class's own law: "the engine's
 * bottom = position.y - HALF_HEIGHT") — every spawn arm passes the
 * reference-mimicking y forms the reference reads as the BOTTOM, so the
 * engine's box rides 0.125 LOW through the transient and the restore's
 * first read; the landing clip re-seats the bottom on the floor, so the
 * REST boxes converge.</p>
 *
 * <p>THE PICKUP LAW (the reference's PlayerEntity.playerTick 484-505):
 * the player's own box grown (1.0, 0.5, 1.0) over the strict
 * intersection — the same envelope the orb seats drove. The item's
 * envelope form is conformant (the pass-38/43 records); the ANCHORING
 * alone separates here.</p>
 *
 * <p>The seats are EXPECTED RED on develop until VT-038's correction
 * lands; the finding record owns the mismatch. The read is POST-tick
 * (the item falls one gravity step 0.04 before the envelope consults —
 * both worlds' boxes shift equally, the discriminator margins carry).</p>
 */
class ItemBoxModelDiscoveryTest {

    /** The free-fall rig: solid below y=60, air above — nothing blocks the drop cells. */
    private static final class FlatGround implements ItemEntity.Ground {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < 60.0;
        }
    }

    private static PlayerSession player(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), at);
        session.markPlaying();
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
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
     * THE RESTORED FIRST-READ BOX (the anchoring discriminator — the
     * orb's third seat's own shape at the item's 0.25): a restored
     * item's first-read box seats its BOTTOM at the saved y in vanilla
     * (setPosition's feet law over the no-arg load constructor) and its
     * CENTER at the saved y in the engine. The discriminator: the
     * player 0.65 above the saved y — the grown band's bottom edge
     * (70.15) falls inside the reference's post-tick box (top 70.21,
     * clearing by 0.06) but above the engine's (top 70.085, missing by
     * 0.065). One tick: gravity 0.04 shifts both boxes equally.
     */
    @Test
    void theRestoredItemsBoxSeatsItsBottomAtTheSavedY() {
        ItemEntityManager manager = new ItemEntityManager(
                new FlatGround(), new Random(42), 1);
        ItemEntity item = manager.spawnRestored(new Position(0.5, 70.0, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1));
        assertNotNull(item, "the item landed");
        PlayerSession above = player("AboveTheFall", new Position(0.5, 70.65, 0.5));
        // One tick: the item falls to 69.96 (gravity 0.04, no clip at this
        // height); the pickup reads the box at that instant.
        manager.tick(List.of(above));
        assertEquals(0, manager.size(),
                "the reference's LOADED item seats its box bottom at the saved y (the feet "
                        + "law): the first-read box [69.96, 70.21] clears the grown band's "
                        + "bottom edge (70.15) by 0.06 and collects; the engine's "
                        + "center-anchored box [69.835, 70.085] misses the same edge by "
                        + "0.065");
    }

    /**
     * THE SPAWN-PATH TRANSIENT (the drop-at-block's mid-flight offset): a
     * block-broken item spawns with its box BOTTOM at the drop point
     * (block +0.5, the reference's Block.java:519 form) in vanilla and
     * its CENTER there in the engine — a 0.125 offset that persists
     * through the toss rise and the fall until the landing clip re-seats
     * the bottom. The discriminator rides the FIRST tick's toss rise
     * (the spawn's 0.2 pop, gravity-diminished to 0.16): the post-tick
     * box tops at 62.91 in the reference against the engine's 62.785.
     * The player 1.35 above the block's base — the grown band's bottom
     * edge (62.85) falls between the two tops.
     */
    @Test
    void theDroppedItemsTransientBoxRidesALowEighthAgainstTheReference() {
        ItemEntityManager manager = new ItemEntityManager(
                new FlatGround(), new Random(42), 1);
        ItemEntity item = manager.spawnDropAtBlock(new Position(0, 62, 0),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        assertNotNull(item, "the drop entered the world");
        PlayerSession above = player("AboveTheDrop", new Position(0.5, 63.35, 0.5));
        // One tick: the toss pop 0.2 - gravity 0.04 = 0.16 rise from the
        // 62.5 drop point; the scatter's x/z drift stays far inside the
        // 1.425 horizontal cap; the read is post-tick.
        manager.tick(List.of(above));
        assertEquals(0, manager.size(),
                "the reference's dropped item seats its box bottom at the drop point "
                        + "(block +0.5): the post-tick box [62.66, 62.91] clears the grown "
                        + "band's bottom edge (62.85) by 0.06 and collects; the engine's "
                        + "center-anchored box tops at 62.785 — the 0.125 low ride misses "
                        + "the same edge by 0.065");
    }

    /**
     * THE CONTROL (the rest convergence): a settled item's box bottoms on
     * the floor in BOTH worlds (the engine's landing clip seats the
     * bottom at 60.0 exactly like the reference's move-clip) — a player
     * standing at the item's rest position collects it in both. GREEN on
     * develop: the rig's sanity, isolating the anchoring as the sole
     * divergence.
     */
    @Test
    void theSettledItemCollectsInBothWorldsAtItsRest() {
        ItemEntityManager manager = new ItemEntityManager(
                new FlatGround(), new Random(42), 1);
        ItemEntity item = manager.spawnDropAtBlock(new Position(0, 62, 0),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        assertNotNull(item, "the control drop entered the world");
        for (int i = 0; i < 100; i++) { // the settle: the scatter decays, the item rests
            manager.tick(List.of());
        }
        assertTrue(item.onGround(), "the construction settles: the item rests on the floor");
        double restX = item.position().x();
        double restZ = item.position().z();
        PlayerSession walker = player("ControlWalker", new Position(restX, 60.0, restZ));
        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }
        assertEquals(0, manager.size(),
                "the settled item's box bottoms on the floor in both worlds — the player "
                        + "standing at the rest position collects it when it should");
    }
}
