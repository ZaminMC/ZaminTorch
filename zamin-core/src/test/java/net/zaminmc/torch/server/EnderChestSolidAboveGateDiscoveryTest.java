package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.chest.EnderChestBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S F-EC1 DISCOVERY SEAT (docs/vanilla-conformance-audit.md,
 * the twenty-fourth pass): the ender chest's solid-above gate reads the
 * MOVEMENT consult where the reference reads {@code Block.isSolid()} —
 * the SIGNAL consult.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8 EnderChestBlock.use lines 71-90):
 * the open's refusal gate is line 80 —
 * {@code world.getBlockState(pos.up()).getBlock().isSolid()} — and
 * {@code Block.isSolid()} (Block.java lines 274-276) is the three-conjunct
 * SIGNAL consult: {@code material.isSolidBlocking() && isCube() &&
 * !isSignalSource()}. The isCube-false families (the glass pair —
 * GlassBlock lines 25-27, the beacon, the piston bases, the slabs, the
 * stairs, the chests, the leaves...) and the signal sources (the redstone
 * block alone surviving the census — every other source dies at isCube or
 * the material conjunct) are NOT solid for this gate: an ender chest
 * under GLASS opens in vanilla; an ender chest under a REDSTONE BLOCK
 * opens; an ender chest under STONE refuses.</p>
 *
 * <p>THE ENGINE'S SHAPE ({@code EngineServer.openEnderChestOnTick} line
 * 17862): the gate reads {@code WorldSolidity.isSolid} — the MOVEMENT
 * consult (what blocks a body). Glass and the redstone block are
 * movement-solid, so the engine REFUSES the open under both where vanilla
 * opens. The site was outside F-TC1's signal-routing casualty census (the
 * six swapped sites were the signal re-radiation family — signal(),
 * comparatorInput, the wire's power bands, the wakes); this is a USE gate
 * reading the same wrong consult, the class of error F-TC1 repaired.</p>
 *
 * <p>THE SEAT: the ender chest with GLASS above (the reference's law: the
 * window OPENS — glass is isCube-false, not solid for this consult) and
 * with a REDSTONE BLOCK above (the reference's law: the window OPENS —
 * the signal-source conjunct), the STONE lid as the refusing control
 * (both laws refuse — the existing EnderChestAcceptanceTest's own seat).
 * The glass and redstone seats are EXPECTED RED on develop until F-EC1's
 * correction lands. Do not re-pin them to the engine's current answer;
 * the finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnderChestSolidAboveGateDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ecgate" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.CREATIVE, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private void settle() throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 6, "the settle");
    }

    /**
     * F-EC1: the ender chest's solid-above gate reads the reference's
     * {@code Block.isSolid()} — the isCube-false families and the signal
     * sources are not solid for this consult, so the chest OPENS under
     * glass and under a redstone block; the stone lid alone refuses.
     */
    @Test
    void theGateReadsTheSignalConsultNotTheMovementConsult() throws Exception {
        PlayerSession session = join("EnderGate");
        BlockPosition at = new BlockPosition(100, 60, 100);
        server.ticker().submit(() -> server.world().setBlock(at,
                EnderChestBlocks.enderChestOf(3)));
        await(() -> EnderChestBlocks.isFamily(server.world().getBlock(at)),
                "the ender chest block landed");
        server.ticker().submit(() -> session.applyMovement(
                new Position(99.5, 61.5, 100.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> session.position().x() == 99.5, "the player stood at the chest");

        // THE GLASS LID: the reference's Block.isSolid() answers FALSE for
        // the glass (GlassBlock lines 25-27's isCube-false) — the chest
        // OPENS. The engine's movement consult answers TRUE — the refusal.
        server.ticker().submit(() -> server.world().setBlock(
                at.offset(0, 1, 0), BuiltinBlocks.GLASS));
        settle();
        assertTrue(server.world().getBlock(at.offset(0, 1, 0))
                        .equals(BuiltinBlocks.GLASS),
                "the glass lid stands");
        AtomicInteger glassOpened = new AtomicInteger(-1);
        server.useItemOnBlock(session, at, 1, Optional.empty(), 0, glassOpened::set);
        await(() -> glassOpened.get() > 0,
                "F-EC1: the ender chest opens under the glass lid (the reference's "
                        + "isCube-false glass is not solid for Block.isSolid())");
        assertEquals(PlayerSession.ContainerKind.ENDER_CHEST, session.openContainerKind(),
                "F-EC1: the glass-lidded open assigned the ender chest kind");
        server.closeWindow(session, glassOpened.get());
        settle();

        // THE REDSTONE BLOCK LID: the reference's Block.isSolid() answers
        // FALSE for the redstone block (the signal-source conjunct — the
        // census's lone survivor) — the chest OPENS. The engine's movement
        // consult answers TRUE — the refusal.
        server.ticker().submit(() -> server.world().setBlock(
                at.offset(0, 1, 0), BuiltinBlocks.REDSTONE_BLOCK));
        settle();
        AtomicInteger redstoneOpened = new AtomicInteger(-1);
        server.useItemOnBlock(session, at, 1, Optional.empty(), 0, redstoneOpened::set);
        await(() -> redstoneOpened.get() > 0,
                "F-EC1: the ender chest opens under the redstone block lid (the "
                        + "reference's signal-source conjunct — not solid for "
                        + "Block.isSolid())");
        server.closeWindow(session, redstoneOpened.get());
        settle();

        // THE STONE CONTROL: both laws refuse — the existing acceptance
        // test's own seat, kept here so the discovery seat cannot pass by
        // refusing everything.
        server.ticker().submit(() -> server.world().setBlock(
                at.offset(0, 1, 0), BuiltinBlocks.STONE));
        settle();
        AtomicInteger refused = new AtomicInteger(-1);
        server.useItemOnBlock(session, at, 1, Optional.empty(), 0, refused::set);
        settle();
        assertEquals(-1, refused.get(),
                "the stone lid refused the open (both laws — the control)");
    }
}
