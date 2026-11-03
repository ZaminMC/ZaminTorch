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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-027 PASS-2 SEAT — the dig/place commit feedback's
 * dimension stamp (docs/vanilla-conformance-audit.md, the forty-first
 * pass's second act): the reference plays the break and place feedback
 * in the ACTING world.
 *
 * <p>THE REFERENCE LAW (the interaction family's break/place feedback):
 * the block-break effect (Effect 2001) and the place sound
 * (BlockItem.place lines 53-60) both ride the ACTING world's own
 * dispatch — ServerPlayerInteractionManager's {@code world} (the
 * player's own), whose Effect/Sound packets reach that world's players
 * alone (the per-world PlayerManager listener list, World.playSound
 * 769-773). A nether dig's break sound plays IN THE NETHER.</p>
 *
 * <p>THE ENGINE'S ROW (BlockInteractionService + the boot's commit
 * feedback closure): the dig/place WRITES route the acting world (Wave
 * FL4-F5 — the nether dig breaks the nether's cells, the drops land in
 * the nether), but the FEEDBACK closure — the shatter burst and the
 * dig/placing named sounds — rides the NO-DIMENSION fx overloads: the
 * unconditional OVERWORLD 0 stamp. The {@code Commit} record carries
 * no dimension at all. A nether dig: the block vanishes (the write is
 * routed), the drop lands in the nether (TF-9) — and the digger breaks
 * the block IN COMPLETE SILENCE (the sound stamped 0, the wire's fan
 * refusing every nether observer), while overworld players at the same
 * coordinates hear the phantom dig. The place arm carries the same
 * wrong stamp (the family's placing sound, the same closure).</p>
 *
 * <p>THE SEAT: the full-boot construction (the FL4-F5 seat's own wire):
 * a nether player digs a stone block through the REAL dig wire — the
 * captured FX stream must carry the dig sound stamped with the
 * NETHER's -1. RED on develop (the observed stamp 0). The place arm
 * rides the same pin (a nether stone place — the family's placing
 * name, the same closure).</p>
 */
class NetherDigSoundDimensionDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.Sound> captured = java.util.Collections.synchronizedList(
            new ArrayList<>());

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "digsnd" + Math.abs(new java.util.Random().nextInt(100000)),
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
    void theNetherDigsBreakSoundCarriesTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        PlayerSession digger = join("NetherDigger");
        digger.setDimension(-1);

        int fy = 100;
        BlockPosition target = new BlockPosition(64, fy + 1, 64);
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
            nether.setBlock(target, BuiltinBlocks.STONE);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(target)),
                40, "the nether stone landed");

        digger.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the digger settles");
        digger.inventory().setSlot(0, new ItemStack(BuiltinItems.DIAMOND_PICKAXE, 1));
        digger.inventory().selectHotbarSlot(0);

        // The REAL dig wire (the FL4-F5 seat's own): START, the dig clock,
        // FINISH — the break lands in the nether, the drop lands in the
        // nether, and the feedback closure fires.
        server.blockInteraction().submitMiningStart(digger, target);
        EngineAwaits.awaitTicks(server.world(), 12, "the dig accumulates past the accept band");
        server.blockInteraction().submitMiningFinished(digger, target);
        // Await the FEEDBACK itself (the block's opening precedes the sound
        // by a hair on the same tick — the sound's landing is the pin's own
        // precondition).
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(target).equals(nether.airType())
                        && captured.stream()
                                .anyMatch(sound -> sound.name().startsWith("dig.")),
                40, "the nether stone broke and its dig sound fired");

        // THE PIN — the reference plays the break feedback in the ACTING
        // world (the dig wire's own world — ServerPlayerInteractionManager's
        // field; the per-world listener list, World.playSound 769-773). The
        // engine's feedback closure rides the no-dimension overloads — the
        // overworld 0 stamp: the nether digger breaks the block in COMPLETE
        // SILENCE (the wire's fan refuses every nether observer) while
        // overworld players at the same coordinates hear the phantom dig.
        FxManager.Sound digSound = captured.stream()
                .filter(sound -> sound.name().startsWith("dig."))
                .findFirst().orElse(null);
        assertNotNull(digSound, "the break's dig sound fired on the fx bus (the named-sound "
                + "half of the engine's established break-feedback decomposition)");
        assertEquals(-1, digSound.dimension(),
                "VT-027: the nether dig's break sound must carry the NETHER's dimension "
                        + "(the reference's break feedback rides the dig wire's own world "
                        + "— the per-world listener list alone). Observed: the sound "
                        + "stamped " + digSound.dimension() + " — the commit feedback "
                        + "closure's no-dimension overload (the unconditional overworld "
                        + "0), the nether digger refused by the wire's fan while the "
                        + "overworld hears the phantom");
    }

    @Test
    void theNetherPlacesSoundCarriesTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        PlayerSession placer = join("NetherPlacer");
        placer.setDimension(-1);

        int fy = 100;
        BlockPosition spot = new BlockPosition(64, fy + 1, 64);
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
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(spot).equals(nether.airType()),
                40, "the nether platform landed");

        placer.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the placer settles");
        placer.inventory().setSlot(0, new ItemStack(BuiltinItems.STONE, 64));
        placer.inventory().selectHotbarSlot(0);

        // The REAL place wire: the stone placed against the platform's top
        // face — the commit feedback's place arm (the family's placing
        // sound, the same closure).
        server.useItemOnBlock(placer, new BlockPosition(64, fy, 64), 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(spot))
                        && captured.stream()
                                .anyMatch(sound -> sound.name().equals("dig.stone")),
                40, "the stone placed in the nether and its placing sound fired");

        // THE PIN — the place arm's the same closure, the same law: the
        // placing sound in the ACTING world (BlockItem.place's own world,
        // lines 53-60 — the per-world listener list). The stone family's
        // placing name is its breaking name (BlockSoundMap's own law —
        // getPlacing's base is getBreaking): "dig.stone".
        FxManager.Sound placeSound = captured.stream()
                .filter(sound -> sound.name().equals("dig.stone"))
                .findFirst().orElse(null);
        assertNotNull(placeSound, "the place's family sound fired on the fx bus (the "
                + "stone family's placing name is its breaking name, dig.stone — "
                + "BlockSoundMap.getPlacing's own law); observed: " + captured);
        assertEquals(-1, placeSound.dimension(),
                "VT-027's place arm: the nether place's sound must carry the NETHER's "
                        + "dimension (BlockItem.place lines 53-60 — the placing sound "
                        + "in the ACTING world). Observed stamp: " + placeSound.dimension());
    }
}
