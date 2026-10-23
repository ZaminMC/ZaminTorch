package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * THE SPAWN-PROTECTION GATE (DedicatedServer.isSpawnProtected lines
 * 387-409, 1:1 — Slice N4 arms the consult now that the ops model
 * exists): an op-less server protects NOTHING (the reference's own
 * {@code getOps().isEmpty() -> false} — a fresh server's uses are free
 * until the first /op arms the band); once an operator exists, a
 * non-operator inside the chebyshev radius around spawn (the
 * {@code spawn-protection} property, default 16) is refused while the
 * operator passes freely; radius 0 disables the band.
 */
class SpawnProtectionAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession owner;
    private PlayerSession guest;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "prot" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        owner = join("Owner");
        guest = join("Guest");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session =
                ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(new Position(0.5, 5.0, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    private void placeChest() throws InterruptedException {
        // The chest at (2, 5, 0) — spawn (0, 4, 0)'s chebyshev band: 2.
        BlockPosition at = new BlockPosition(2, 5, 0);
        server.ticker().submit(() -> server.world().setBlock(at,
                BuiltinBlocks.CHEST));
        await(() -> server.world().getBlock(at).equals(BuiltinBlocks.CHEST),
                "the chest landed");
    }

    private void use(PlayerSession player) {
        server.useItemOnBlock(player, new BlockPosition(2, 5, 0), 1,
                java.util.Optional.empty(), windowId -> { });
    }

    /**
     * THE UNARMED BAND: with nobody opped the protection is OFF — the
     * guest's use works exactly like the owner's (the reference's own
     * {@code getOps().isEmpty() -> false}; a fresh vanilla server's
     * spawn area is fully usable).
     */
    @Test
    void theOpLessServerProtectsNothing() throws Exception {
        placeChest();
        use(guest);
        await(() -> guest.openContainerKind() == PlayerSession.ContainerKind.CHEST,
                "the guest opened the chest (nobody opped: no protection)");
    }

    /**
     * THE ARMED BAND: once an operator exists, the non-operator's use
     * inside the radius refuses (the chest stays shut — no block arm, no
     * item arm) while the operator's own use passes freely.
     */
    @Test
    void theArmedBandRefusesTheNonOperator() throws Exception {
        placeChest();
        // /op the owner — the store persists and arms the band.
        server.ticker().submit(() -> {
            server.opStore().op(owner.uuid(), owner.name(), 4);
            owner.setOpLevel(4);
        });
        await(() -> server.opStore().isOp(owner.uuid()), "the owner opped");

        // The guest's use inside the band: refused.
        use(guest);
        try {
            EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(PlayerSession.ContainerKind.NONE, guest.openContainerKind(),
                "the non-operator's use inside the band refused");

        // The operator's use: free.
        use(owner);
        await(() -> owner.openContainerKind() == PlayerSession.ContainerKind.CHEST,
                "the operator's use passed freely");
    }

    /**
     * THE CHEBYSHEV BAND'S EDGE: a use OUTSIDE the radius passes even
     * for the non-operator — the band is the spawn-centred square, not
     * the whole world.
     */
    @Test
    void theOutsideBandUsePasses() throws Exception {
        // /op the owner to arm the band.
        server.ticker().submit(() -> server.opStore().op(owner.uuid(), owner.name(), 4));
        await(() -> server.opStore().isOp(owner.uuid()), "the owner opped");

        // A chest well outside the default radius 16: at (40, 5, 40).
        BlockPosition far = new BlockPosition(40, 5, 40);
        server.ticker().submit(() -> server.world().setBlock(far,
                BuiltinBlocks.CHEST));
        await(() -> server.world().getBlock(far).equals(BuiltinBlocks.CHEST),
                "the far chest landed");
        // The guest walks near the far chest first (the 8-block gate).
        server.ticker().submit(() -> guest.applyMovement(
                new Position(40.5, 6.5, 42.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> guest.position().x() == 40.5, "the guest walked to the far chest");

        server.useItemOnBlock(guest, far, 1, java.util.Optional.empty(),
                windowId -> { });
        await(() -> guest.openContainerKind() == PlayerSession.ContainerKind.CHEST,
                "the outside-band use passed for the non-operator");
    }
}
