package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.item.ItemStack;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-026 PASS-2 SEAT — the TNT prime hiss's dimension stamp
 * (docs/vanilla-conformance-audit.md, the forty-first pass's second act):
 * the reference plays {@code game.tnt.primed} in the IGNITING world.
 *
 * <p>THE REFERENCE LAW (TntBlock.ignite lines 59-68, re-walked for this
 * seat): {@code world.playSound(primedtntentity, "game.tnt.primed", 1.0F,
 * 1.0F)} — the IGNITING world's own playSound, which iterates THAT
 * world's listener list alone (World.playSound lines 769-773 — every
 * listener is the per-world PlayerManager, refusing every observer
 * standing in another dimension). The Bootstrap dispenser arm carries the
 * same law (lines 329-338: {@code source.getWorld().playSound(...)}) —
 * the DISPENSING world's hiss. The blast-consumed prime plays NOTHING
 * (onExploded lines 47-54 — the silent spawn; the engine's
 * {@code primeFromBlast} matches).</p>
 *
 * <p>THE ENGINE'S ROW (TntSystem): the primed BODY carries the acting
 * world's dimension (Wave FL4-F7 — {@code new PrimedTnt(...,
 * actingWorld.dimension(), ...)}), but the HISS rides the no-dimension
 * fx overload — the unconditional OVERWORLD 0 stamp. A nether prime:
 * the hiss is REFUSED for the nether players standing beside the TNT
 * (the wire's fan gate, V18Connection.fxEligible — TF-12) and
 * PHANTOM-PLAYS for overworld players at the same coordinates. The
 * body crosses the boundary correctly; its own sound does not.</p>
 *
 * <p>THE SEAT: the full-boot construction (the FL4-F7 seat's own wire):
 * a nether flint-and-steel use primes the NETHER's TNT — the captured
 * FX stream must carry the hiss stamped with the NETHER's -1. RED on
 * develop (the observed stamp 0). The dispenser arm
 * ({@code spawnPrimedAt} — Bootstrap's law) rides the same pin.</p>
 */
class TntPrimeHissDimensionDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.Sound> captured = java.util.Collections.synchronizedList(
            new ArrayList<>());

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tnthiss" + Math.abs(new java.util.Random().nextInt(100000)),
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

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    @Test
    void theNetherPrimeHissCarriesTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        PlayerSession igniter = join("NetherIgniter");
        igniter.setDimension(-1);

        int fy = 100;
        BlockPosition tnt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 4; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(tnt, BuiltinBlocks.TNT);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.TNT.equals(nether.getBlock(tnt)),
                40, "the nether TNT landed");

        igniter.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the igniter settles");
        igniter.inventory().setSlot(0, new ItemStack(BuiltinItems.FLINT_AND_STEEL, 1));
        igniter.inventory().selectHotbarSlot(0);

        // The REAL use wire (the FL4-F7 seat's own): the flint clicked on
        // the TNT — the reference's ignite arm.
        server.useItemOnBlock(igniter, tnt, 1, java.util.Optional.empty(), windowId -> { });

        // THE CONTROL — the prime landed and the body carries the NETHER's
        // dimension (the FL4-F7 law, green on develop: the body's stamp is
        // the very contrast the hiss's stamp breaks).
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(tnt).equals(nether.airType())
                        && server.tnt().primedBodies().stream()
                                .anyMatch(body -> body.dimension() == -1),
                40, "the nether TNT primed with the nether body stamp");

        // THE PIN — the reference plays the hiss in the IGNITING world
        // (TntBlock.ignite line 65: the IGNITING world's playSound; World
        // 769-773: that world's listeners alone). The engine's hiss rides
        // the no-dimension overload — the overworld 0 stamp: the nether
        // players standing beside the primed TNT hear NOTHING, overworld
        // players at the same coordinates hear the phantom hiss.
        FxManager.Sound hiss = captured.stream()
                .filter(sound -> sound.name().equals("game.tnt.primed"))
                .findFirst().orElse(null);
        assertTrue(hiss != null, "the prime hiss fired on the fx bus (the volume/pitch "
                + "law is #124-unrelated and stands; only the stamp is at stake)");
        assertEquals(-1, hiss.dimension(),
                "VT-026: the nether prime's hiss must carry the NETHER's dimension "
                        + "(the reference's ignite plays the sound in the IGNITING "
                        + "world — TntBlock.ignite line 65 over World.playSound "
                        + "769-773, that world's listener list alone). Observed: the "
                        + "hiss stamped " + hiss.dimension() + " — the no-dimension "
                        + "overload's unconditional overworld 0 (the wire's fan then "
                        + "refuses every nether observer and phantom-plays the "
                        + "overworld's at the same coordinates)");
    }

    @Test
    void theNetherDispensedTntHissCarriesTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(70 + dx, fy, 70 + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(new BlockPosition(70, fy, 70))),
                40, "the nether platform landed");

        // The dispenser arm (Bootstrap lines 329-338): the dispensed TNT
        // primes at the facing cell — the hiss in the DISPENSING world
        // (source.getWorld().playSound, line 335). The body carries the
        // acting world's stamp (FL4-F7); the hiss must ride the same one.
        int before = captured.size();
        server.ticker().submit(() ->
                server.tnt().spawnPrimedAt(nether, 70.5, fy + 1, 70.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> captured.size() > before
                        && server.tnt().primedBodies().stream()
                                .anyMatch(body -> body.dimension() == -1),
                40, "the dispensed body primed in the nether");

        FxManager.Sound hiss = captured.stream()
                .filter(sound -> sound.name().equals("game.tnt.primed"))
                .findFirst().orElse(null);
        assertTrue(hiss != null, "the dispensed prime hissed on the fx bus");
        assertEquals(-1, hiss.dimension(),
                "VT-026's dispenser arm: the dispensed TNT's hiss carries the "
                        + "DISPENSING world's dimension (Bootstrap line 335 — "
                        + "source.getWorld().playSound). Observed stamp: "
                        + hiss.dimension());
    }
}
