package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-034 PASS-2 SEAT — the repeater's phantom click
 * (docs/vanilla-conformance-audit.md, the forty-ninth pass): the
 * reference's repeater delay cycle is SILENT.
 *
 * <p>THE REFERENCE LAW (RepeaterBlock.java, swept whole): NO playSound
 * and NO doEvent anywhere in RepeaterBlock or its DiodeBlock parent —
 * {@code use} (lines 38-45) is exactly the canModifyWorld gate, the
 * {@code world.setBlockState(pos, state.next(DELAY), 3)}, and the
 * return. The CLICK belongs to the COMPARATOR alone (ComparatorBlock.use
 * lines 148-152: {@code world.playSound(..., "random.click", 0.3F,
 * state.get(MODE) == SUBTRACT ? 0.55F : 0.5F)} — the pitch answering
 * the NEW mode). Right-clicking a repeater in vanilla makes no sound;
 * right-clicking a comparator clicks.</p>
 *
 * <p>THE ENGINE'S ROW (EngineServer.cycleRepeaterDelayOnTick, ~13118):
 * the delay step plays {@code random.click} at 0.3F/0.6F — the BUTTON's
 * law (the shape's own origin), a sound the reference never plays at
 * this site. The comparator's arm (12347-12353) is correct — the click
 * with session.dimension() and the mode-answer pitch.</p>
 *
 * <p>THE SEAT: a repeater use through the REAL wire must produce NO
 * random.click (RED on develop: the phantom fires); the COMPARATOR
 * control — the same wire, a comparator's mode cycle — must produce the
 * click (proving the seat's construction sound and the removal
 * surgical).</p>
 */
class RepeaterPhantomClickDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.Sound> capturedSounds =
            java.util.Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "repclk" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.Sound sound) {
                capturedSounds.add(sound);
            }
        });
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
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    @Test
    void theRepeaterDelayCycleIsSilent() throws Exception {
        EngineWorld world = server.world();
        PlayerSession user = join("RepeaterUser");
        int fy = 40;
        BlockPosition repeaterAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            world.setBlock(repeaterAt.offset(0, -1, 0), BuiltinBlocks.STONE);
            world.setBlock(repeaterAt,
                    RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_NORTH, 1, false));
        });
        EngineAwaits.awaitCondition(world,
                () -> RedstoneBlocks.isRepeater(world.getBlock(repeaterAt)),
                40, "the repeater landed");
        user.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "the user settles");

        // The REAL use wire: an empty hand right-clicks the repeater —
        // the delay cycles 1 -> 2 (the state's own observable).
        server.useItemOnBlock(user, repeaterAt, 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(world,
                () -> RedstoneBlocks.repeaterDelay(world.getBlock(repeaterAt)) == 2,
                40, "the delay cycled to 2");

        // THE PIN — the reference's repeater use is SILENT (RepeaterBlock
        // swept whole: no playSound, no doEvent — the CLICK belongs to the
        // comparator alone, ComparatorBlock.use lines 148-152). The
        // engine's delay step plays a phantom random.click.
        assertFalse(capturedSounds.stream()
                        .anyMatch(sound -> sound.name().equals("random.click")),
                "VT-034: the repeater's delay cycle must be SILENT (RepeaterBlock.use "
                        + "lines 38-45: the canModifyWorld gate, setBlockState(pos, "
                        + "state.next(DELAY), 3), the return — NO playSound anywhere in "
                        + "the repeater family; the click is the comparator's alone, "
                        + "ComparatorBlock.use lines 148-152). Observed: the phantom "
                        + "click fired — " + capturedSounds);
    }

    /** THE CONTROL — the comparator's mode cycle DOES click (the pitch
     * answering the new mode): the same wire, the sibling family. */
    @Test
    void theComparatorModeCycleClicks() throws Exception {
        EngineWorld world = server.world();
        PlayerSession user = join("ComparatorUser");
        int fy = 40;
        BlockPosition comparatorAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            world.setBlock(comparatorAt.offset(0, -1, 0), BuiltinBlocks.STONE);
            world.setBlock(comparatorAt,
                    RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_NORTH, false, false));
        });
        EngineAwaits.awaitCondition(world,
                () -> RedstoneBlocks.isComparator(world.getBlock(comparatorAt)),
                40, "the comparator landed");
        user.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "the user settles");

        // The same REAL use wire: the mode cycles COMPARE -> SUBTRACT.
        server.useItemOnBlock(user, comparatorAt, 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(world,
                () -> RedstoneBlocks.comparatorSubtract(world.getBlock(comparatorAt))
                        && capturedSounds.stream()
                                .anyMatch(sound -> sound.name().equals("random.click")),
                40, "the mode cycled and the click fired");

        // The reference's exact law: volume 0.3, the pitch answering the
        // NEW mode (SUBTRACT -> 0.55).
        FxManager.Sound click = capturedSounds.stream()
                .filter(sound -> sound.name().equals("random.click"))
                .findFirst().orElseThrow();
        assertEquals(0.3f, click.volume(), 0.0001f,
                "the comparator's click volume is the reference's 0.3F");
        assertEquals(0.55f, click.pitch(), 0.0001f,
                "the comparator's click pitch answers the NEW mode "
                        + "(SUBTRACT -> 0.55F, ComparatorBlock.use lines 148-152)");
        assertTrue(RedstoneBlocks.comparatorSubtract(world.getBlock(comparatorAt)),
                "the mode flipped to SUBTRACT (the write's own observable)");
    }
}
