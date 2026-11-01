package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-018 SEAT — the stick's {@code random.bowhit} sound
 * (docs/vanilla-conformance-audit.md, the pass-35 lane's discovery;
 * this lane's pass 2 — the FX-assert seat):
 *
 * <p><b>The reference's law</b> (1.8.8): the stick arm plays the bowhit
 * sound at EVERY block stick — ArrowEntity line 303, inside the
 * {@code else} that records the hit block (lines 288-309):
 * {@code this.playSound("random.bowhit", 1.0F, 1.2F / (this.random
 * .nextFloat() * 0.2F + 0.9F))} — at the backed-off rest position,
 * BEFORE {@code inGround = true} (line 305). The pitch band is
 * [1.2/1.1, 1.2/0.9] = [1.0909, 1.3333], one fresh random draw per
 * stick. The entity-hit arm (a mob/player hit) plays NOTHING — the
 * sound is the BLOCK stick's alone.</p>
 *
 * <p><b>The engine's shape</b> (develop {@code 777b7c86}): the stick
 * arm fires the {@code onProjectileLanded} listener fan alone
 * (ProjectileManager line ~1197) — the ONLY registered implementation
 * is the protocol adapter's explicit no-op (V18ProtocolServer line
 * 1759: "A landed arrow just stops receiving teleport syncs"), and
 * {@code random.bowhit} appears in the engine at the tripwire alone
 * (TripwireSystem line 313). NO listener plays any sound at the stick
 * — the clients never hear the impact.</p>
 *
 * <p>The seat records every FX event on the bus and asserts the
 * bowhit's presence at the stick; the control pulse first proves the
 * recorder hears this boot's sounds (a dead listener cannot make the
 * seat pass for the wrong reason). RED on develop as the evidence
 * record; the VT-018 fix branch carries the GREEN parity pin (the
 * sound present at the rest position, the pitch within the reference
 * band).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStickBowhitSoundDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    /** Every sound event heard on the bus, in order. */
    private final List<FxManager.Sound> heard = new CopyOnWriteArrayList<>();

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bowhit" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.Sound sound) {
                heard.add(sound);
            }
        });
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /**
     * VT-018's seat — the stick plays nothing in the engine. An arrow
     * dropped onto a 5x5 stone platform (the VT-014 seat's own vertical
     * construction: exact spawn, exact velocity) sticks in the center
     * block; the reference plays {@code random.bowhit} at the rest
     * position on that very tick. The recorder must hold a
     * {@code random.bowhit} event by then.
     */
    @Test
    void theStickPlaysTheBowhitSound() throws Exception {
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            for (int x = 48; x <= 52; x++) {
                for (int z = 48; z <= 52; z++) {
                    world.setBlock(new BlockPosition(x, 20, z), BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(50, 20, 50))),
                "the stone platform stands at y=20");

        // The control pulse: the recorder hears this boot's sounds (the
        // reference's own launch sound name, injected at the platform).
        server.ticker().submit(() -> server.fx().sound(
                new Position(50.5, 21.5, 50.5), "random.bow", 1.0f, 1.2f));
        await(() -> !heard.isEmpty(), "the control pulse is heard");
        assertTrue(heard.stream().anyMatch(s -> s.name().equals("random.bow")),
                "the control: the recorder hears the boot's sound bus");

        // The deterministic vertical drop (the VT-014 seat's own numbers):
        // the arrow crosses the platform's top face and sticks in the
        // center block within ~20 ticks.
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().spawnRestored(
                ProjectileEntity.Kind.ARROW,
                new Position(50.5, 44.5, 50.5), 0.0, -0.8, 0.0)));
        await(() -> launched.get() != null, "the arrow spawns above the platform");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks in the platform's center block");
        Position stuckAt = arrow.position();

        // Five ticks: the reference's sound lands on the stick tick itself;
        // the window only guards the poll.
        EngineAwaits.awaitTicks(world, 5, "the stick tick settles");

        assertTrue(heard.stream().anyMatch(s -> s.name().equals("random.bowhit")),
                "VT-018: the arrow's BLOCK stick must play random.bowhit — the "
                        + "reference's stick arm plays it at EVERY block stick "
                        + "(ArrowEntity line 303, inside the else that records the "
                        + "hit block: playSound(\"random.bowhit\", 1.0F, 1.2F / "
                        + "(random.nextFloat() * 0.2F + 0.9F)), at the rest position "
                        + "BEFORE inGround = true — the pitch band [1.0909, 1.3333], "
                        + "one fresh random draw per stick, the ENTITY-hit arm playing "
                        + "nothing). Observed: the recorder held " + heard.size()
                        + " sound events through the stick (" + heard + ") — the "
                        + "engine's stick arm fires the onProjectileLanded fan alone "
                        + "(ProjectileManager line ~1197), the ONLY registered "
                        + "implementation is the protocol adapter's explicit NO-OP "
                        + "(V18ProtocolServer line 1759), and random.bowhit exists in "
                        + "the engine at the tripwire alone (TripwireSystem line 313). "
                        + "The arrow stuck at " + stuckAt + " in complete silence");
        assertFalse(heard.stream().anyMatch(s -> s.name().equals("random.bowhit")
                        && (s.pitch() < 1.0909f || s.pitch() > 1.3333f)),
                "VT-018's parity band (the fix's pin): the bowhit's pitch must sit "
                        + "in the reference's band 1.2/(rand*0.2+0.9) — [1.0909, 1.3333]");
    }
}
