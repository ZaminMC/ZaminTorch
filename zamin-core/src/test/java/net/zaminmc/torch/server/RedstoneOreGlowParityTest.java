package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The redstone ore's glow family — the walk-on lighting and the interact
 * spark band (the fifteenth pass's §B1/§B2, the fall-path family's
 * demand-named remainder):
 *
 * <p>THE STEPS BAND (Entity.move lines 594-604): the
 * {@code onSteppedOn} consult fires for EVERY body's per-tick move while
 * onGround — before the distance-walked band that gates only the step
 * sound, so standing still lights the ore on the first tick
 * (RedstoneOreBlock.onSteppedOn lines 40-43 over interact lines 51-56).
 * The {@code !flag} gate (line 412: {@code onGround && isSneaking() &&
 * instanceof PlayerEntity}) skips the ground-sneaking PLAYER alone — a
 * mob never sneaks; the item and orb families ride the reference's
 * default {@code makesSteps()} TRUE.</p>
 *
 * <p>THE SPARK BAND (addParticles lines 96-132): SIX passes, one per
 * face, each relocating to the {@code ±0.0625} band ONLY when that
 * face's neighbor is NOT solid-render, spawning ONLY outside the cell's
 * own box (line 128's clamp) — a server broadcast the pre-fix engine
 * never emitted (the use arm's "the particles ride the client's display
 * band" comment covered the LIT form's ambient display alone).</p>
 */
class RedstoneOreGlowParityTest {

    @TempDir
    Path dataDir;

    /** The captured interact sparks (the FX listener's own record). */
    private final ConcurrentLinkedQueue<FxManager.RedstoneOreSpark> sparks =
            new ConcurrentLinkedQueue<>();

    @Test
    void thePlayerStandingLightsTheOreAndSparks() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(0, 20, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE),
                    "the ore seeded");

            PlayerSession walker = join(server, "Walker");
            server.teleportPlayer(walker, new Position(0.5, 30.0, 0.5));
            await(() -> walker.position().y() == 30.0, "the teleport landed");
            server.movementProposal(walker, new Position(0.5, 26.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(walker, new Position(0.5, 23.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(walker, new Position(0.5, 21.0, 0.5), Rotation.ZERO, true);
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.LIT_REDSTONE_ORE),
                    "standing on the ore lights it (the consult before the distance band)");

            // The spark band: six passes, every one spawning OUTSIDE the
            // cell's own box (line 128's clamp), the up-face band's
            // particle at y + 0.0625 + 1.0 (the all-air isolation).
            await(() -> sparks.size() >= 6, "the interact's sparks arrived");
            for (FxManager.RedstoneOreSpark spark : sparks) {
                assertTrue(
                        spark.position().x() < 0.0 || spark.position().x() > 1.0
                                || spark.position().y() < 20.0 || spark.position().y() > 21.0
                                || spark.position().z() < 0.0 || spark.position().z() > 1.0,
                        "every spark spawns outside the cell's own box: " + spark.position());
            }
            assertTrue(sparks.stream().anyMatch(s -> s.position().y() > 21.0),
                    "the up-face band's particle lands at y + 0.0625 + 1.0 (the air above)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theGroundSneakingPlayerNeverLightsTheOre() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(0, 20, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE),
                    "the ore seeded");

            PlayerSession creeper = join(server, "Creeper");
            server.ticker().submit(() -> creeper.setSneaking(true));
            await(() -> creeper.sneaking(), "the sneak held");
            server.teleportPlayer(creeper, new Position(0.5, 30.0, 0.5));
            await(() -> creeper.position().y() == 30.0, "the teleport landed");
            server.movementProposal(creeper, new Position(0.5, 26.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(creeper, new Position(0.5, 23.0, 0.5), Rotation.ZERO, false);
            server.movementProposal(creeper, new Position(0.5, 21.0, 0.5), Rotation.ZERO, true);
            Thread.sleep(500);
            assertEquals(net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE,
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0)),
                    "the ground-sneaking player skips the steps band (the !flag gate)");
            assertTrue(sparks.isEmpty(),
                    "no interact fires — the band never consults for the sneaking player");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMobCrossingLightsTheOre() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(0, 20, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE),
                    "the ore seeded");

            // The mob's own steps band (the stepSoundWalk consult staged
            // as the pending intent — the manager's drain, the engine's
            // listener, the whole interact).
            var spawned = server.mobs().spawnAt(MobType.ZOMBIE, new Position(0.5, 23.0, 0.5));
            assertTrue(spawned != null, "the zombie spawned");
            await(() -> spawned.onGround(), "the zombie landed on the ore");
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.LIT_REDSTONE_ORE),
                    "the grounded mob's consult lights the ore (a mob never sneaks)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theRestingItemLightsTheOreAndTheSolidFaceConsultSuppressesItsBand() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            // Phase one: the ISOLATED cell — the floating ore, air on all
            // six faces. The item's own steps band (the reference's
            // default makesSteps() TRUE) consults its -0.2 feet cell and
            // the interact fires the whole band.
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(0, 20, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE),
                    "the ore seeded");
            var stack = net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.lookup(Identifier.parse("minecraft:stone")).orElseThrow(), 1);
            var item = server.itemEntities().spawnRestored(
                    new Position(0.5, 22.5, 0.5), stack);
            assertTrue(item != null, "the item spawned");
            await(() -> item.onGround(), "the item came to rest on the ore");
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.LIT_REDSTONE_ORE),
                    "the resting item's consult lights the ore");
            await(() -> sparks.size() >= 6, "the isolated cell's whole band arrives");
            assertTrue(sparks.stream().anyMatch(s -> s.position().y() > 21.0),
                    "the up-face band emits at y + 0.0625 + 1.0");
            assertTrue(sparks.stream().anyMatch(s -> s.position().x() > 1.0),
                    "the east-face band emits at x + 0.0625 + 1.0");
            // THE REFERENCE'S OWN QUIRK (addParticles line 128, verbatim):
            // the spawn clamp reads {@code d2 < 0.0} — the WORLD's floor,
            // never the cell's own base — so the down-face band's relocated
            // particle (y - 0.0625) spawns ONLY at y = 0; every cell above
            // the floor fails the clamp and the down face never sparks.
            assertFalse(sparks.stream().anyMatch(s -> s.position().y() < 20.0),
                    "the down-face band never sparks above the world's floor"
                            + " (line 128's d2 < 0.0 quirk, ported verbatim)");
            sparks.clear();

            // Phase two: the SOLID-RENDER east neighbor — the stone beside
            // the ore suppresses the east pass alone (addParticles lines
            // 120-122: the band relocates ONLY when the face's neighbor is
            // NOT solid-render; the pass keeps its inside-cell roll and
            // line 128's clamp refuses it). The item still rests on the
            // ore's top — the side neighbor never blocks the rest.
            // The resting item's per-tick consult keeps interacting — the
            // LIT form's interact is the SAME band (the spark alone), so
            // the phase runs on the lit ore exactly as it would on the
            // unlit one.
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(1, 20, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(1, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.STONE),
                    "the east stone placed");
            await(() -> sparks.size() >= 5,
                    "the other five faces still emit over the solid east face");
            Thread.sleep(300);
            assertFalse(sparks.stream().anyMatch(s -> s.position().x() > 1.0),
                    "the solid-render east face suppresses its band alone (lines 120-122)");
            assertTrue(sparks.stream().anyMatch(s -> s.position().y() > 21.0),
                    "the open up face still emits");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theRestingOrbLightsTheOre() throws Exception {
        EngineServer server = boot();
        try {
            EngineWorld world = server.world();
            server.ticker().submit(() -> world.setBlock(
                    new net.zaminmc.torch.block.BlockPosition(0, 20, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE));
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE),
                    "the ore seeded");
            var orb = server.experienceOrbs().spawnRestored(new Position(0.5, 22.5, 0.5), 3, 0);
            assertTrue(orb != null, "the orb spawned");
            await(() -> orb.onGround(), "the orb came to rest on the ore");
            await(() -> world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 20, 0))
                    .equals(net.zaminmc.torch.server.block.BuiltinBlocks.LIT_REDSTONE_ORE),
                    "the resting orb's consult lights the ore (the default makesSteps)");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "oreglow", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.fx().addListener(new FxManager.Listener() {
            @Override
            public void onFx(FxManager.FxEvent event) {
                if (event instanceof FxManager.RedstoneOreSpark spark) {
                    sparks.add(spark);
                }
            }
        });
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
