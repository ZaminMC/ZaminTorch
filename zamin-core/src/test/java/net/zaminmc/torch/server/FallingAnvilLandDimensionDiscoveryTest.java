package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * THE VERIFIER'S VT-028 PASS-2 SEAT — the falling anvil's land sound's
 * dimension stamp (docs/vanilla-conformance-audit.md, the forty-first
 * pass's third act — the no-dimension census tail's first head): the
 * reference fires the anvil's land event in the FALLING BODY'S OWN
 * WORLD.
 *
 * <p>THE REFERENCE LAW (FallingBlockEntity.tick lines 90-104 +
 * AnvilBlock.onTickFallingBlockEntity lines 89-91, re-walked for this
 * seat): the landing places the block via
 * {@code world.setBlockState(blockpos1, this.state, 3)} and calls the
 * block's OWN hook — the BASE FallingBlock's hook is EMPTY (the
 * ordinary sand/gravel landing is SILENT, no event at all), and the
 * ANVIL's override alone fires {@code world.doEvent(1022, pos, 0)} —
 * on {@code world}, the falling entity's OWN world, whose event
 * listeners (the per-world PlayerManager) refuse every observer
 * standing in another dimension. The client answers the 1022 with the
 * {@code random.anvil_land} name. A nether anvil's land sound plays IN
 * THE NETHER.</p>
 *
 * <p>THE ENGINE'S ROW (EngineServer's onFallingEnded): the falling
 * BODY carries its own dimension (Wave FL4-F9 — the physics and the
 * landing consult the body's world), but the anvil's land sound rides
 * the NO-DIMENSION fx overload — the unconditional OVERWORLD 0 stamp.
 * A nether anvil landing: the sound is REFUSED for the nether players
 * at the landing and PHANTOM-PLAYS for overworld players at the same
 * coordinates.</p>
 *
 * <p>THE SEAT: the full-boot construction — an anvil in the nether
 * falling through the REAL gravity chain (GravityFalls.tryFall → the
 * manager's physics → the landing → the event dispatch). The captured
 * FX stream must carry the land sound stamped with the NETHER's -1.
 * RED on develop (the observed stamp 0). THE SCOPE GUARD: an ordinary
 * SAND landing plays NOTHING (the base hook's empty law — the fix must
 * never widen the sound to the sand family).</p>
 */
class FallingAnvilLandDimensionDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.Sound> captured = java.util.Collections.synchronizedList(
            new ArrayList<>());

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "anvilland" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.Sound sound) {
                captured.add(sound);
            }
        });
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    @Test
    void theNetherAnvilsLandSoundCarriesTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");

        // The nether platform at y=100 with three cells of air above; the
        // anvil at y=103 falls to y=101 on the real gravity chain.
        int fy = 100;
        BlockPosition anvilAt = new BlockPosition(64, fy + 3, 64);
        BlockPosition landingAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 5; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(anvilAt, BuiltinBlocks.ANVIL);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ANVIL.equals(nether.getBlock(anvilAt)),
                40, "the nether anvil landed in its cell");

        // The REAL gravity chain: tryFall converts the block (the engine's
        // own verified Slice-470 shape over FallingBlock lines 37-59), the
        // manager's physics walk the fall, the landing consults the body's
        // OWN world (FL4-F9) and the event dispatch fires the sound.
        server.ticker().submit(() -> net.zaminmc.torch.server.block.GravityFalls
                .tryFall(nether, anvilAt, server.fallingEntities()));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ANVIL.equals(nether.getBlock(landingAt))
                        && nether.getBlock(anvilAt).equals(nether.airType())
                        && captured.stream()
                                .anyMatch(sound -> sound.name().equals("random.anvil_land")),
                60, "the anvil fell, landed in the nether and its land sound fired");

        // THE PIN — the reference's 1022 rides the falling body's OWN
        // world (AnvilBlock.onTickFallingBlockEntity line 90 over the
        // entity's world doEvent — the per-world listener list alone). The
        // engine's named-sound stand-in rode the no-dimension overload —
        // the overworld 0 stamp: the nether players at the landing hear
        // NOTHING, the overworld hears the phantom.
        FxManager.Sound land = captured.stream()
                .filter(sound -> sound.name().equals("random.anvil_land"))
                .findFirst().orElseThrow();
        assertEquals(-1, land.dimension(),
                "VT-028: the nether anvil's land sound must carry the NETHER's dimension "
                        + "(the reference's 1022 event rides the falling body's own world — "
                        + "AnvilBlock.onTickFallingBlockEntity line 90, the per-world "
                        + "listener list alone). Observed: the sound stamped "
                        + land.dimension() + " — the no-dimension overload's unconditional "
                        + "overworld 0 (the wire's fan then refuses every nether observer "
                        + "and phantom-plays the overworld's at the same coordinates)");
    }

    @Test
    void theOrdinarySandLandingPlaysNoSound() throws Exception {
        EngineWorld nether = server.worldFor(-1);

        // THE SCOPE GUARD — the base FallingBlock's landing hook is EMPTY
        // in the reference (FallingBlock lines 76-77: the ordinary
        // sand/gravel landing fires NO event, plays NO sound); only the
        // anvil's override speaks. The engine's sound must stay
        // anvil-only whatever the fix does.
        int fy = 100;
        BlockPosition sandAt = new BlockPosition(64, fy + 3, 64);
        BlockPosition landingAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 5; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(sandAt, BuiltinBlocks.SAND);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.SAND.equals(nether.getBlock(sandAt)),
                40, "the nether sand landed in its cell");

        server.ticker().submit(() -> net.zaminmc.torch.server.block.GravityFalls
                .tryFall(nether, sandAt, server.fallingEntities()));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.SAND.equals(nether.getBlock(landingAt)),
                60, "the sand fell and landed in the nether");

        assertFalse(captured.stream()
                        .anyMatch(sound -> sound.name().equals("random.anvil_land")),
                "THE SCOPE GUARD: the ordinary sand landing is SILENT (the base "
                        + "FallingBlock's hook is empty, lines 76-77 — the anvil's "
                        + "override alone fires the event); observed: " + captured);
        assertNotNull(captured, "the capture ran");
    }
}
