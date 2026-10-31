package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * F-EX2's behavioral probe (V2): the sleeping player's eye-height frame in
 * the blast's knockback vector. The reference's PlayerEntity.getEyeHeight
 * (lines 1648-1659) carries THREE states — 1.62 standing, 0.2 LYING (the
 * sleeping frame), the sneak's -0.08 on top of either. The engine's
 * woundPlayersFromBlast reads {@code sneaking ? 1.54 : 1.62} — the lying
 * frame missing: a sleeping player inside the blast's reach takes the
 * knockback vector aimed 1.62 high instead of 0.2.
 *
 * <p>The seat: a blast center BETWEEN the two eye heights flips the
 * knockback's vertical SIGN — vanilla aims the lying body DOWN toward the
 * bed, the engine's frame aims it UP.</p>
 */
class ExplosionSleepingEyeFrameParityTest {

    @TempDir
    Path dataDir;

    @Test
    void theSleepingBlastKnockbackAimsAtTheLyingEye() throws Exception {
        EngineServer server = boot();
        try {
            // TWO players: the Waker keeps the Sleeper's sleep pending (the
            // morning jump waits for every player sleeping).
            PlayerSession sleeper = join(server, "Sleeper");
            PlayerSession waker = join(server, "Waker");

            // Night falls.
            server.ticker().submit(() -> server.world().setTimeOfDay(
                    net.zaminmc.torch.server.entity.MobManager.NIGHT_START + 100));
            await(() -> server.world().timeOfDay()
                    >= net.zaminmc.torch.server.entity.MobManager.NIGHT_START, "night falls");

            // The sleeper stands on the ground near the bed's cell and
            // clicks it: the body stays where the click found it.
            Position at = sleeper.position();
            int bx = (int) Math.floor(at.x());
            int by = (int) Math.floor(at.y());
            int bz = (int) Math.floor(at.z());
            BlockPosition bedFoot = new BlockPosition(bx + 2, by, bz);
            server.ticker().submit(() -> {
                server.world().setBlock(bedFoot, BuiltinBlocks.STONE);
                server.world().setBlock(bedFoot.offset(0, 0, 1), BuiltinBlocks.STONE);
            });
            await(() -> server.world().getBlock(bedFoot).equals(BuiltinBlocks.STONE),
                    "the bed pad seeded");
            // The bed item in hand.
            sleeper.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.lookup(
                            net.zaminmc.torch.util.Identifier.parse("minecraft:bed"))
                            .orElseThrow(), 1));
            sleeper.inventory().selectHotbarSlot(0);
            server.useItemOnBlock(sleeper, bedFoot, 1, java.util.Optional.empty(),
                    windowId -> { });
            BlockPosition bedCell = bedFoot.offset(0, 1, 0);
            await(() -> EngineServer.isBedHalf(server.world().getBlock(bedCell)),
                    "the bed placed on the pad");
            // The second use (the bed's own right-click): the body enters
            // the bed — the sleeping state the blast reads.
            server.useItemOnBlock(sleeper, bedCell, 1, java.util.Optional.empty(),
                    windowId -> { });
            await(() -> sleeper.sleeping(), "the sleeper is lying in the bed");
            Position feet = sleeper.position();

            // The blast: a TNT charge whose center lands BETWEEN the two
            // eye heights above the sleeper's feet (0.2 .. 1.62).
            int tntX = (int) Math.floor(feet.x()) - 4;
            int tntY = (int) Math.floor(feet.y()) + 1;
            int tntZ = (int) Math.floor(feet.z());
            BlockPosition tnt = new BlockPosition(tntX, tntY, tntZ);
            server.ticker().submit(() ->
                    server.world().setBlock(tnt, BuiltinBlocks.TNT));
            await(() -> server.world().getBlock(tnt).equals(BuiltinBlocks.TNT),
                    "the charge placed");

            // The motion capture (the wire-side explosion observer).
            AtomicReference<EngineServer.ExplosionEvent> captured = new AtomicReference<>();
            server.addExplosionListener(captured::set);

            server.ticker().submit(() -> server.tnt().ignite(server.world(), tnt, 2));
            await(() -> captured.get() != null, "the blast fired");

            EngineServer.ExplosionEvent event = captured.get();
            double[] knockback = event.playerMotion().get(sleeper.uuid());

            // The geometry must DISCRIMINATE: the center between the two
            // eye heights (the sign-flip band).
            double vanillaDy = (feet.y() + 0.2) - event.y();
            double engineDy = (feet.y() + 1.62) - event.y();
            org.junit.jupiter.api.Assertions.assertTrue(
                    Math.signum(vanillaDy) != Math.signum(engineDy) && vanillaDy != 0,
                    "the seat's geometry discriminates the two frames (center y="
                            + event.y() + ", feet y=" + feet.y() + ")");
            org.junit.jupiter.api.Assertions.assertNotNull(knockback,
                    "the sleeping player took the blast's knockback carry");
            // THE ASSERTION: the knockback's vertical component follows the
            // LYING frame (0.2) — aimed down toward the bed.
            org.junit.jupiter.api.Assertions.assertTrue(
                    Math.signum(knockback[1]) == Math.signum(vanillaDy),
                    "the sleeping player's knockback aims at the LYING eye (0.2), not the"
                            + " standing eye (1.62): motion y=" + knockback[1]
                            + " (vanilla frame dy=" + vanillaDy + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 — the standing control (the frame the old consult kept)
    // ------------------------------------------------------------------

    /**
     * The STANDING control: an awake player at the same geometry keeps the
     * 1.62 frame — the knockback aims UP at the standing eye exactly as
     * the pre-fix engine did (green on both trees, the guard).
     */
    @Test
    void theStandingBlastKnockbackAimsAtTheStandingEye() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession stander = join(server, "Stander");
            Position feet = stander.position();
            int tntX = (int) Math.floor(feet.x()) - 4;
            int tntY = (int) Math.floor(feet.y()) + 1;
            int tntZ = (int) Math.floor(feet.z());
            BlockPosition tnt = new BlockPosition(tntX, tntY, tntZ);
            server.ticker().submit(() ->
                    server.world().setBlock(tnt, BuiltinBlocks.TNT));
            await(() -> server.world().getBlock(tnt).equals(BuiltinBlocks.TNT),
                    "the charge placed");

            AtomicReference<EngineServer.ExplosionEvent> captured = new AtomicReference<>();
            server.addExplosionListener(captured::set);

            server.ticker().submit(() -> server.tnt().ignite(server.world(), tnt, 2));
            await(() -> captured.get() != null, "the blast fired");

            EngineServer.ExplosionEvent event = captured.get();
            double[] knockback = event.playerMotion().get(stander.uuid());
            double standingDy = (feet.y() + 1.62) - event.y();
            org.junit.jupiter.api.Assertions.assertNotNull(knockback,
                    "the standing player took the blast's knockback carry");
            org.junit.jupiter.api.Assertions.assertTrue(
                    Math.signum(knockback[1]) == Math.signum(standingDy),
                    "the standing player's knockback aims at the 1.62 eye: motion y="
                            + knockback[1] + " (standing frame dy=" + standingDy + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " reached PLAYING");
        return session;
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

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "exf" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
