package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grounded arrow's player pickup (the VIII14 backlog's arrow-pickup
 * row — reference/1.8.8 ArrowEntity lines 35/61/85 + onPlayerCollision
 * lines 427-440, Bootstrap line 70, BowItem lines 52-56): the pickup band
 * (0 the mob shooters' arrows, 1 the player bow's and the dispenser's, 2
 * the creative/Infinity override), the flag law with the survival
 * inventory consult, the creative-only band, and the walk-over collect.
 */
class ArrowPickupTest {

    private static final FxManager FX = new FxManager();

    // ------------------------------------------------------------------
    // The harness: a direct manager + a real session over a stub world
    // (the LeadSystemTest pattern — the inventory, the gamemode and the
    // position are the real session's own)
    // ------------------------------------------------------------------

    private ProjectileManager manager() {
        return new ProjectileManager(
                (x, y, z) -> false, new ProjectileManagerTest.RiggedHits(),
                new ProjectileManagerTest.RecordingCombat(), FX, new Random(21), 5_000);
    }

    private PlayerSession player(String name, GameMode mode) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.applyMovement(new Position(0.5, 4.0, 0.5), Rotation.ZERO, true);
        session.setGamemode(mode);
        return session;
    }

    private World stubWorld() {
        return new World() {
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
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity>
            entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) {
                return "plains";
            }
        };
    }

    /** A grounded arrow at the walker's feet: launched beside them, stuck
     *  by hand (the manager's own landed state), the pickup band the
     *  caller's. */
    private ProjectileEntity stuckArrow(ProjectileManager manager, int pickup) {
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0.6, 4.2, 0.5), 0, 0, 0.0);
        arrow.landInGround();
        arrow.setPickupStatus(pickup);
        return arrow;
    }

    /** @return the total arrow count in the session's inventory. */
    private static int arrowCount(PlayerSession session) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            var stack = session.inventory().slot(slot);
            if (!stack.isEmpty() && stack.type().identifier().value().equals("arrow")) {
                count += stack.count();
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // The law
    // ------------------------------------------------------------------

    @Test
    void thePlayerArrowCollectsOnWalkOver() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("Walker", GameMode.SURVIVAL);
        ProjectileEntity arrow = stuckArrow(manager, 1);
        int before = arrowCount(walker);
        manager.tick(List.of(walker));
        assertEquals(before + 1, arrowCount(walker),
                "the walk-over added one arrow to the inventory");
        assertFalse(manager.all().contains(arrow),
                "the collected arrow left the world");
    }

    @Test
    void theMobArrowStaysOnTheGround() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("Walker", GameMode.SURVIVAL);
        ProjectileEntity arrow = stuckArrow(manager, 0); // the skeleton's band
        int before = arrowCount(walker);
        manager.tick(List.of(walker));
        assertEquals(before, arrowCount(walker),
                "the mob shooter's arrow never collects");
        assertTrue(manager.all().contains(arrow),
                "the mob arrow stays in the world");
    }

    @Test
    void theFullInventoryLeavesTheArrow() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("Hoarder", GameMode.SURVIVAL);
        // Fill every slot with a non-arrow stack: the pickup-1 consult
        // (line 431) must actually FIT — a full inventory flips the flag
        // off and the arrow stays.
        for (int slot = 0; slot < 36; slot++) {
            walker.inventory().setSlot(slot, net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.STONE, 64));
        }
        ProjectileEntity arrow = stuckArrow(manager, 1);
        manager.tick(List.of(walker));
        assertTrue(manager.all().contains(arrow),
                "the full inventory leaves the arrow on the ground");
        assertEquals(0, arrowCount(walker), "nothing was added");
    }

    @Test
    void theCreativeOnlyBandCollectsWithoutTheInventory() {
        ProjectileManager manager = manager();
        PlayerSession creative = player("Architect", GameMode.CREATIVE);
        ProjectileEntity arrow = stuckArrow(manager, 2); // the Infinity band
        int before = arrowCount(creative);
        manager.tick(List.of(creative));
        assertFalse(manager.all().contains(arrow),
                "the creative walker collects the band-2 arrow");
        assertEquals(before, arrowCount(creative),
                "the band-2 collect never touches the inventory");
    }

    @Test
    void theCreativeOnlyBandRefusesTheSurvivalWalker() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("Survivor", GameMode.SURVIVAL);
        ProjectileEntity arrow = stuckArrow(manager, 2);
        int before = arrowCount(walker);
        manager.tick(List.of(walker));
        assertTrue(manager.all().contains(arrow),
                "the survival walker never scoops the Infinity bow's arrow");
        assertEquals(before, arrowCount(walker), "nothing was added");
    }

    @Test
    void theFlyingArrowNeverCollects() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("Walker", GameMode.SURVIVAL);
        // The in-flight arrow (the !inGround gate, line 428): the walk
        // never collects a moving body.
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0.6, 4.2, 0.5), 0, 0, 3.0);
        arrow.setPickupStatus(1);
        int before = arrowCount(walker);
        manager.tick(List.of(walker));
        assertEquals(before, arrowCount(walker),
                "the flying arrow never collects mid-flight");
        assertTrue(manager.all().contains(arrow),
                "the arrow is still flying — never collected");
    }

    @Test
    void theDistanceBandRefusesTheFarWalker() {
        ProjectileManager manager = manager();
        PlayerSession far = player("Faraway", GameMode.SURVIVAL);
        far.applyMovement(new Position(6.0, 4.0, 6.0), Rotation.ZERO, true);
        ProjectileEntity arrow = stuckArrow(manager, 1);
        int before = arrowCount(far);
        manager.tick(List.of(far));
        assertEquals(before, arrowCount(far),
                "the walker beyond the grown box never collects");
        assertTrue(manager.all().contains(arrow), "the arrow stays");
    }

    /**
     * The band's public read (§27.10 — the pickup slice's exposure): the
     * api {@code Projectile} handle answers the band through the
     * interface alone (no engine cast), the launch sites' own sets —
     * the mob shooter's 0, the player bow's 1, the Infinity creative
     * override's 2 — and the non-arrow families answer the 0 default.
     */
    @Test
    void thePickupBandReadsThroughThePublicHandle() {
        ProjectileManager manager = manager();
        net.zaminmc.torch.entity.Projectile mobArrow = stuckArrow(manager, 0);
        net.zaminmc.torch.entity.Projectile playerArrow = stuckArrow(manager, 1);
        net.zaminmc.torch.entity.Projectile infinityArrow = stuckArrow(manager, 2);
        net.zaminmc.torch.entity.Projectile shard = manager.launch(
                ProjectileEntity.Kind.SNOWBALL, -1, new Position(0.6, 4.2, 0.5), 0, 0, 0.0);
        assertEquals(0, mobArrow.pickupStatus(), "the mob shooter's band");
        assertEquals(1, playerArrow.pickupStatus(), "the player bow's band");
        assertEquals(2, infinityArrow.pickupStatus(), "the Infinity creative override");
        assertEquals(0, shard.pickupStatus(), "the non-arrow families answer 0");
    }
}
