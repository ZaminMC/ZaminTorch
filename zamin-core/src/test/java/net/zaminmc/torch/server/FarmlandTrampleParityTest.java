package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The farmland trample (the fall-path family's first slice —
 * FarmlandBlock.onFallenOn lines 57-71, the B4 row the fourteenth pass's
 * farmland part left open for this pass):
 *
 * <p>THE LANDING CONSULT (Entity.move lines 569-581 + checkFallDamage
 * 719-733): the landed cell at {@code floor(y - 0.2)} with the air-cell
 * fence fallback — the cell BELOW the body's feet, never the body's own
 * cell. THE ROLL (line 60): {@code world.random.nextFloat() <
 * fallDistance - 0.5F} — a descent of half a block or less NEVER tramples
 * ({@code nextFloat()} is never negative), a high landing ALWAYS does
 * (the bound exceeds 1). THE GRIEFING GATE (lines 63-66): a NON-PLAYER
 * body in a no-grief world tramples nothing AND SKIPS THE FALL DAMAGE
 * (the early return before super — the mob lands soft when its grief is
 * refused); a PLAYER always tramples, the gate never applies. THE WRITE
 * (line 68): both farmland forms turn to DIRT, and the neighbor bus
 * carries the crop's own pop off the reversion.</p>
 */
class FarmlandTrampleParityTest {

    @TempDir
    Path dataDir;

    @Test
    void thePlayerTramplesAHighLandingBothForms() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            // Two fields: the dry form and the wet form, side by side.
            server.ticker().submit(() -> {
                world.setBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0),
                        net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND);
                world.setBlock(new net.zaminmc.torch.block.BlockPosition(4, 4, 0),
                        net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND_WET);
            });
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND)
                    && world.getBlock(new net.zaminmc.torch.block.BlockPosition(4, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND_WET),
                    "the two fields seeded");

            PlayerSession faller = join(server, "Faller");
            // The dry form: teleport high, descend in per-tick steps (the
            // client's own packets), land on the field. A 12-block fall's
            // roll bound is 11.5 — ALWAYS passes.
            server.teleportPlayer(faller, new Position(0.5, 17.0, 0.5));
            await(() -> faller.position().y() == 17.0, "the teleport landed");
            server.movementProposal(faller, new Position(0.5, 13.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(faller, new Position(0.5, 9.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(faller, new Position(0.5, 5.0, 0.5), Rotation.ZERO, true);
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.DIRT),
                    "the dry field trampled to dirt (the roll bound 11.5 always passes)");

            // The wet form: the same descent.
            server.teleportPlayer(faller, new Position(4.5, 17.0, 0.5));
            await(() -> faller.position().y() == 17.0, "the second teleport landed");
            server.movementProposal(faller, new Position(4.5, 13.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(faller, new Position(4.5, 9.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(faller, new Position(4.5, 5.0, 0.5), Rotation.ZERO, true);
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(4, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.DIRT),
                    "the wet field tramples too (both forms, line 68's plain DIRT)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theShortDescentNeverTramples() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(0, 4, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND),
                    "the field seeded");

            PlayerSession hopper = join(server, "Hopper");
            // A 0.4-block descent: the roll bound is -0.1 — NEVER passes
            // (nextFloat() is never negative), the field survives.
            server.teleportPlayer(hopper, new Position(0.5, 5.4, 0.5));
            await(() -> hopper.position().y() == 5.4, "the hop start");
            server.movementProposal(hopper, new Position(0.5, 5.0, 0.5), Rotation.ZERO, true);
            await(() -> hopper.position().y() == 5.0, "the hop landed");
            Thread.sleep(150);
            assertEquals(net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND,
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0)),
                    "a half-block-or-less descent never rolls (line 60's bound is negative)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theCropPopsOffTheRevertedField() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> {
                world.setBlock(new net.zaminmc.torch.block.BlockPosition(8, 4, 0),
                        net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND);
                world.setBlock(new net.zaminmc.torch.block.BlockPosition(8, 5, 0),
                        net.zaminmc.torch.server.block.BuiltinBlocks.WHEAT_STAGE0);
            });
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(8, 5, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.WHEAT_STAGE0),
                    "the crop seeded on its field");

            PlayerSession reaper = join(server, "Reaper");
            server.teleportPlayer(reaper, new Position(8.5, 15.0, 0.5));
            await(() -> reaper.position().y() == 15.0, "the teleport landed");
            server.movementProposal(reaper, new Position(8.5, 11.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(reaper, new Position(8.5, 8.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(reaper, new Position(8.5, 5.0, 0.5), Rotation.ZERO, true);
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(8, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.DIRT),
                    "the field trampled");
            // The crop's own support consult (WheatBlock.canBePlacedOn —
            // FARMLAND alone): the bus pops it off the reverted field, the
            // seed item dropping at the cell.
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(8, 5, 0))
                    .equals(world.airType()),
                    "the wheat pops off the dirt (the crop's support arm)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMobTramplesThroughThePendingIntent() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(0, 4, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND),
                    "the field seeded");

            // The zombie falls from 7 blocks onto the field (the mob
            // gravity's own walk): the roll bound 6+ always passes, the
            // mobGriefing default is true, and the pending intent's DIRT
            // write lands through the manager's listener.
            var zombie = server.mobs().spawnAt(MobType.ZOMBIE, new Position(0.5, 12.0, 0.5));
            assertTrue(zombie != null, "the zombie spawned");
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.DIRT),
                    "the mob's trample wrote the dirt through the pending intent");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theGrieflessMobLandsSoftAndUntrampled() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            // The gamerule write rides the tick lane (the ownership law).
            server.ticker().submit(() -> server.gameRules().set("mobGriefing", "false"));
            await(() -> "false".equals(server.gameRules().get("mobGriefing")),
                    "the no-grief rule set");
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(0, 4, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND),
                    "the field seeded");

            // The same high fall (the roll ALWAYS passes at this height) —
            // but the no-grief world refuses the mob: no trample AND NO
            // FALL DAMAGE (lines 63-66's early return before super).
            var zombie = server.mobs().spawnAt(MobType.ZOMBIE, new Position(0.5, 12.0, 0.5));
            assertTrue(zombie != null, "the zombie spawned");
            var mob = zombie;
            await(() -> mob.onGround(), "the zombie landed");
            assertEquals(net.zaminmc.torch.server.block.BuiltinBlocks.FARMLAND,
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 4, 0)),
                    "the griefless mob trampled nothing");
            assertEquals(20.0f, mob.health(), 0.0001f,
                    "the refused grief lands SOFT — the early return skips the"
                            + " fall damage too (lines 63-66)");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "trample", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        server.ticker().submit(() -> session.setOpLevel(2));
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timeout: " + description);
            }
            Thread.sleep(25);
        }
    }
}
