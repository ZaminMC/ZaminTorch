package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tamed wolf's collar-dye arm (WolfEntity.interactMob lines 313-324 —
 * the tameable family's last ledgered tail, verified against the live
 * engine): a different color takes the collar (one unit consumed in
 * survival, the reference's own {@code !creativeMode} guard), the same
 * color falls through to the owner's sit toggle, and any player may dye a
 * tamed wolf's collar (the arm carries no owner gate — the reference's
 * own ladder).
 */
class WolfCollarDyeAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private net.zaminmc.torch.server.player.PlayerSession player;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "wcd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        await(() -> server.state() == net.zaminmc.torch.ServerState.RUNNING,
                "the server up");
        return server;
    }

    private net.zaminmc.torch.server.player.PlayerSession join(String name)
            throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        net.zaminmc.torch.server.player.PlayerSession session =
                ((EngineBridge.Accepted) result).session();
        // The stub link never answers the handshake — the scripted client's
        // own promotion (the ChestMinecartTest form).
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "the joined player reached PLAYING");
        return session;
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(description);
    }

    /** A tamed, standing wolf beside the joined player. */
    private MobEntity tamedWolfBeside() {
        Position at = player.position();
        MobEntity wolf = server.mobs().spawnAt(MobType.WOLF,
                new Position(at.x() + 1.0, at.y(), at.z()));
        wolf.tameWolf(player.name(), player.uuid().toString());
        wolf.setWolfSitting(false);
        return wolf;
    }

    private void holdDye(int metadata, int count) {
        player.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.INK_SAC, count).withDamage(metadata));
        player.inventory().selectHotbarSlot(0);
    }

    @Test
    void theDifferentColorTakesTheCollarAndPaysOneUnit() throws Exception {
        boot();
        try {
            player = join("Dyer");
            MobEntity wolf = tamedWolfBeside();
            assertEquals(1, wolf.wolfCollarColor(), "the reference's own red default");

            // A yellow dye (metadata 4) on the red collar: the change.
            holdDye(4, 3);
            server.interactEntity(player, wolf.entityId());
            await(() -> wolf.wolfCollarColor() == 4, "the collar took the yellow");
            assertEquals(2, player.inventory().held().count(),
                    "one unit consumed in survival");

            // The SAME color: the fall-through to the owner's sit verb —
            // the collar stands, the stack stands, the wolf sits.
            wolf.setWolfSitting(false);
            holdDye(4, 2);
            server.interactEntity(player, wolf.entityId());
            await(() -> wolf.wolfSitting(), "the same color fell through to the sit toggle");
            assertEquals(4, wolf.wolfCollarColor(), "the collar unchanged");
            assertEquals(2, player.inventory().held().count(), "the stack unchanged");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theCreativeHandDyesWithoutPayingAndAnyPlayerMayDye() throws Exception {
        boot();
        try {
            player = join("Tailor");
            player.setGamemode(net.zaminmc.torch.GameMode.CREATIVE);
            MobEntity wolf = tamedWolfBeside();

            // The creative guard: the change lands, the stack never pays.
            holdDye(11, 1);
            server.interactEntity(player, wolf.entityId());
            await(() -> wolf.wolfCollarColor() == 11, "the creative dye landed");
            assertEquals(1, player.inventory().held().count(), "the creative stack stands");

            // No owner gate: a second, non-owner player dyes the same wolf
            // (the arm carries only the tamed gate — the reference's ladder).
            var other = join("Stranger");
            Position at = player.position();
            other.applyMovement(new Position(at.x() + 1.0, at.y(), at.z()),
                    new net.zaminmc.torch.util.Rotation(0f, 0f), true);
            other.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.INK_SAC, 2).withDamage(2));
            other.inventory().selectHotbarSlot(0);
            server.interactEntity(other, wolf.entityId());
            await(() -> wolf.wolfCollarColor() == 2, "the stranger dyed the wolf");
            assertFalse(wolf.isOwner(other), "the stranger is not the owner");
            assertTrue(wolf.isOwner(player), "the owner stands");
        } finally {
            server.shutdown(() -> { });
        }
    }
}
