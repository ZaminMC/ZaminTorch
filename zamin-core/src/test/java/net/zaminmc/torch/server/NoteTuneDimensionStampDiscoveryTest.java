package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-035 MEMBER-1 SEAT (docs/vanilla-conformance-audit.md,
 * the forty-sixth pass) — the note family's TUNE carries the ACTING
 * world's dimension.
 *
 * <p><b>The reference's law</b> (1.8.8): the note's sound plays through
 * the ACTING world's listener chain — ServerWorld.doBlockEvents line 847
 * runs NoteBlock.doEvent (lines 95-101) inside the acting world's event
 * drain, {@code world.playSound(x+.5, y+.5, z+.5, "note."+instrument,
 * 3.0F, pitch)} (World 781-784) iterates the acting world's listeners,
 * and ServerWorldEventListener.playSound (lines 40-44) sends the sound
 * packet with {@code this.world.dimension.getId()} — the acting
 * dimension's observers alone. A nether note block's tune reaches the
 * NETHER's players; an overworld observer at the same coordinates hears
 * nothing.</p>
 *
 * <p><b>The engine's shape</b> (develop {@code efcd0c4f}): the Slice C
 * landing made the note family per-dimension ({@code noteBlocksFor},
 * EngineServer 5947 — the nether/End families with their OWN worlds),
 * but {@code NoteBlockSystem.playNote} line 403 still reads the
 * NO-DIMENSION overload — {@code fx.sound(pos, "note." + instrument,
 * 3.0f, pitch)} — which stamps OVERWORLD 0 (FxManager 195-197, the
 * OVERWORLD constant 173). A nether note block's tune is delivered to
 * dimension-0 observers ONLY: the nether players hear silence, the
 * overworld players at the same coordinates hear a phantom note.</p>
 *
 * <p>The seat drives the REAL use wire ({@code useItemOnBlock} from a
 * session placed in the nether — the Slice C acceptance seat's own
 * construction) with the FxManager bus captured, and asserts the
 * VANILLA law: the nether play's tune event carries dimension -1. The
 * overworld control pins the 0 stamp's correctness for the overworld
 * family so the fix cannot silence the channel. EXPECTED RED on develop
 * at the dimension assert; the VT-035 fix branch (the audio half riding PR #142, the relay half this finding) lifts it green. Do not
 * re-pin this seat to the engine's current answer.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NoteTuneDimensionStampDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    /** Every sound event heard on the bus (the discovery recorder). */
    private final List<FxManager.Sound> heard = new CopyOnWriteArrayList<>();

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "notestamp" + Math.abs(new java.util.Random().nextInt(100000)),
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

    private PlayerSession join(String name, int dimension) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        return session;
    }

    /** A stone-floored platform with air above — the note block's own needs. */
    private void platform(EngineWorld world, int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.setBlock(new BlockPosition(x + dx, y - 1, z + dz), BuiltinBlocks.STONE);
                    for (int dy = 0; dy <= 3; dy++) {
                        world.setBlock(new BlockPosition(x + dx, y + dy, z + dz), world.airType());
                    }
                }
            }
            world.setBlock(new BlockPosition(x, y, z), BuiltinBlocks.NOTE_BLOCK);
        });
        await(() -> BuiltinBlocks.NOTE_BLOCK.equals(world.getBlock(new BlockPosition(x, y, z))),
                "the note block placed in " + world.dimension());
    }

    /**
     * THE NETHER SEAT: a note block played by a player STANDING IN THE
     * NETHER emits its tune stamped with the NETHER's dimension (-1) —
     * the reference's acting-world listener law. On develop the event
     * carries 0 (the no-dimension overload's overworld stamp): the
     * nether players would hear silence while the overworld players at
     * the same coordinates hear the phantom.
     */
    @Test
    void theNetherNoteTuneCarriesTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int x = 300, y = 40, z = 300;
        platform(nether, x, y, z);
        BlockPosition at = new BlockPosition(x, y, z);

        PlayerSession netherPlayer = join("NetherTuner", -1);
        netherPlayer.applyMovement(new Position(x + 0.5, y, z + 1.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the nether player settles");

        int before = heard.size();
        server.useItemOnBlock(netherPlayer, at, 1, Optional.empty(), id -> { });
        await(() -> server.noteBlocksFor(-1).tileAt(at) != null
                        && server.noteBlocksFor(-1).tileAt(at).note() == 1,
                "the nether note tuned once (the play ran)");
        EngineAwaits.awaitTicks(server.world(), 2, "the tune's emit window");

        List<FxManager.Sound> tunes = heard.stream()
                .filter(sound -> sound.name().startsWith("note."))
                .toList();
        assertTrue(tunes.size() >= 1,
                "the nether note play emitted a tune event (observed: " + heard + ")");
        FxManager.Sound tune = tunes.get(tunes.size() - 1);
        assertEquals(-1, tune.dimension(),
                "VT-035 member 1: the nether note's tune carries the NETHER dimension"
                        + " (the reference's ServerWorldEventListener.playSound stamps"
                        + " this.world.dimension.getId(); observed stamp "
                        + tune.dimension() + " for the name " + tune.name() + ")");
        assertEquals(new Position(x + 0.5, y + 0.5, z + 0.5), tune.position(),
                "the tune plays at the note block's center (doEvent lines 95-101)");
        assertEquals(3.0f, tune.volume(), 1.0e-6f, "the reference's volume 3.0F");
    }

    /**
     * THE OVERWORLD CONTROL: the overworld family's tune keeps the 0
     * stamp — the correct answer for the acting dimension 0. This pins
     * the working half so the fix routes by dimension rather than
     * silencing the channel.
     */
    @Test
    void theOverworldNoteTuneKeepsTheOverworldDimension() throws Exception {
        int x = 310, y = 40, z = 310;
        platform(server.world(), x, y, z);
        BlockPosition at = new BlockPosition(x, y, z);

        PlayerSession overworldPlayer = join("OverworldTuner", 0);
        overworldPlayer.applyMovement(new Position(x + 0.5, y, z + 1.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the overworld player settles");

        server.useItemOnBlock(overworldPlayer, at, 1, Optional.empty(), id -> { });
        await(() -> server.noteBlocks().tileAt(at) != null
                        && server.noteBlocks().tileAt(at).note() == 1,
                "the overworld note tuned once (the play ran)");
        EngineAwaits.awaitTicks(server.world(), 2, "the tune's emit window");

        List<FxManager.Sound> tunes = heard.stream()
                .filter(sound -> sound.name().startsWith("note."))
                .toList();
        assertTrue(tunes.size() >= 1,
                "the overworld note play emitted a tune event (observed: " + heard + ")");
        FxManager.Sound tune = tunes.get(tunes.size() - 1);
        assertEquals(0, tune.dimension(),
                "the overworld note's tune keeps the OVERWORLD stamp (the acting"
                        + " dimension law's correct half)");
    }
}
