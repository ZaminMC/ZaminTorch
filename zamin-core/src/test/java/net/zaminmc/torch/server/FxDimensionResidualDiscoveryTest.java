package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.TrapdoorBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
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
 * THE VERIFIER'S VT-029 PASS-2 SEAT — the no-dimension FX residual
 * (docs/vanilla-conformance-audit.md, the forty-seventh pass): the
 * per-dimension-context sites that stamp the overworld 0.
 *
 * <p>THE REFERENCE LAW (the dispatch chain, 1:1): every one of these
 * sounds/events rides the ACTING world's own listener list —
 * {@code World.doEvent(type, pos, data)} (World.java 2609) → the block's
 * handler → {@code world.playSound(x, y, z, name, volume, pitch)}
 * (World.java 781) → {@code this.eventListeners} — the WORLD'S OWN list —
 * → PlayerManager.sendPacket (PlayerManager.java 626-640):
 * {@code serverplayerentity.dimension == dimension} refuses every observer
 * standing in another dimension. The sound never crosses a world
 * boundary.</p>
 *
 * <p>THE ENGINE'S ROW: the FxManager's no-dimension overloads stamp
 * {@code OVERWORLD} unconditionally (FxManager.java 195-197 — the
 * documented migration form). This seat drives FOUR of the sixteen
 * censused members through their REAL wires — the note play (the
 * per-dimension NoteBlockSystem instances, the cleanest member), the
 * trapdoor use (the block-use dispatch's door/gate family), the bone
 * meal burst (the worldEvent family), and the flint ignite (the
 * use-item sound family) — each through a NETHER actor. Every observed
 * stamp must be the NETHER's -1.</p>
 *
 * <p>RED on develop: every observed stamp is the overworld 0 — the
 * nether actor's sound plays in the overworld (the wire's fan refusing
 * every nether observer, the overworld hearing the phantom).</p>
 */
class FxDimensionResidualDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.Sound> capturedSounds =
            java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<FxManager.WorldEvent> capturedEvents =
            java.util.Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "fxres" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.Sound sound) {
                capturedSounds.add(sound);
            } else if (event instanceof FxManager.WorldEvent worldEvent) {
                capturedEvents.add(worldEvent);
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

    /** The nether platform: a 5x5 stone floor at fy with four open air
     * layers above it (the #135 seat's own shape). */
    private void netherPlatform(EngineWorld nether, int fy) throws InterruptedException {
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
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(
                        new BlockPosition(64, fy, 64))),
                40, "the nether platform landed");
    }

    // ------------------------------------------------------------------
    // Member 1 — the note play (the per-dimension NoteBlockSystem)
    // ------------------------------------------------------------------

    @Test
    void theNetherNotePlaysInTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        int fy = 100;
        netherPlatform(nether, fy);

        // The note block stands on the platform (stone below = the bass
        // drum), open air above (the play's own air-above gate).
        BlockPosition noteAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            nether.setBlock(noteAt, BuiltinBlocks.NOTE_BLOCK);
            nether.setBlock(noteAt.offset(0, 1, 0), nether.airType());
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.NOTE_BLOCK.equals(nether.getBlock(noteAt)),
                40, "the nether note block landed");

        // The NETHER family's own use wire (the tile rides the nether
        // family — netherWorld's own listener, Slice C).
        server.ticker().submit(() -> server.noteBlocksFor(-1).useFromTick(noteAt));
        EngineAwaits.awaitCondition(server.world(),
                () -> capturedSounds.stream().anyMatch(sound -> sound.name().startsWith("note.")),
                40, "the nether note played");

        FxManager.Sound note = capturedSounds.stream()
                .filter(sound -> sound.name().startsWith("note."))
                .findFirst().orElse(null);
        assertNotNull(note, "the note sound fired on the fx bus");
        // THE PIN — the reference's play rides the note block's OWN world
        // (NoteBlock.doEvent lines 83-85: world.playSound over the
        // per-world listener list). The engine's NoteBlockSystem is
        // constructed PER-DIMENSION (three instances over their own
        // worlds) yet playNote calls the NO-DIMENSION overload — the
        // unconditional overworld 0.
        assertEquals(-1, note.dimension(),
                "VT-029: the nether note block's play must carry the NETHER's dimension "
                        + "(NoteBlockBlockEntity.playNote lines 32-54 → NoteBlock lines 83-85: "
                        + "world.playSound over the ACTING world's own listener list — "
                        + "PlayerManager.sendPacket's dimension gate, lines 626-640). The "
                        + "NoteBlockSystem is constructed per-dimension; observed stamp: "
                        + note.dimension());
    }

    // ------------------------------------------------------------------
    // Member 2 — the trapdoor use (the block-use dispatch's door/gate family)
    // ------------------------------------------------------------------

    @Test
    void theNetherTrapdoorUseSoundsInTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        PlayerSession user = join("NetherTrapper");
        user.setDimension(-1);
        int fy = 100;
        netherPlatform(nether, fy);
        user.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the user settles");

        // A closed wooden trapdoor on the platform: the use flips it open
        // (a write into the NETHER world) and plays random.door_open.
        BlockPosition trapdoorAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() ->
                nether.setBlock(trapdoorAt, TrapdoorBlocks.state("trapdoor", 0, false, false)));
        EngineAwaits.awaitCondition(server.world(),
                () -> TrapdoorBlocks.isTrapdoor(nether.getBlock(trapdoorAt)),
                40, "the nether trapdoor landed");

        // The REAL use wire: an empty hand (the block stage runs for
        // everyone) — the trapdoor arm consumes the click.
        server.useItemOnBlock(user, trapdoorAt, 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> TrapdoorBlocks.isOpen(nether.getBlock(trapdoorAt))
                        && capturedSounds.stream()
                                .anyMatch(sound -> sound.name().equals("random.door_open")),
                40, "the trapdoor flipped open and its sound fired");

        // THE CONTROL — the flip itself routed the ACTING world (the
        // nether cell carries the open state; the write is correct).
        assertTrue(TrapdoorBlocks.isOpen(nether.getBlock(trapdoorAt)),
                "the trapdoor flipped in the NETHER (the write routed correctly — the "
                        + "sound alone is misstamped)");

        FxManager.Sound door = capturedSounds.stream()
                .filter(sound -> sound.name().equals("random.door_open"))
                .findFirst().orElse(null);
        assertNotNull(door, "the trapdoor's open sound fired on the fx bus");
        // THE PIN — the reference's use arm: TrapdoorBlock.use lines
        // 107-116 → world.doEvent(player, 1003, pos, 0) — the ACTING
        // world's own event (the client plays random.door_open from that
        // packet). The engine's arm writes the nether cell but stamps the
        // sound 0.
        assertEquals(-1, door.dimension(),
                "VT-029: the nether trapdoor's open sound must carry the NETHER's dimension "
                        + "(TrapdoorBlock.use lines 107-116: world.doEvent over the ACTING "
                        + "world's listener list — PlayerManager 626-640's dimension gate). "
                        + "The flip wrote the NETHER cell; observed sound stamp: "
                        + door.dimension());
    }

    // ------------------------------------------------------------------
    // Members 10-14 — the bone meal burst (the worldEvent family)
    // ------------------------------------------------------------------

    @Test
    void theNetherBoneMealBurstCarriesTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        PlayerSession farmer = join("NetherFarmer");
        farmer.setDimension(-1);
        int fy = 100;
        netherPlatform(nether, fy);
        farmer.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the farmer settles");

        // A stage-0 wheat on farmland (the crops arm's own target).
        BlockPosition cropAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            nether.setBlock(cropAt.offset(0, -1, 0), BuiltinBlocks.FARMLAND);
            nether.setBlock(cropAt, BuiltinBlocks.WHEAT_STAGE0);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.WHEAT_STAGE0.equals(nether.getBlock(cropAt)),
                40, "the nether wheat landed");

        farmer.inventory().setSlot(0, new ItemStack(BuiltinItems.BONE_MEAL, 5));
        farmer.inventory().selectHotbarSlot(0);

        // The REAL use wire: the bone meal used on the crop — the 2005
        // world event at the fertilized cell.
        server.useItemOnBlock(farmer, cropAt, 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> !capturedEvents.isEmpty(),
                40, "the bone meal's 2005 burst fired");

        FxManager.WorldEvent burst = capturedEvents.stream()
                .filter(event -> event.eventId() == 2005)
                .findFirst().orElse(null);
        assertNotNull(burst, "the 2005 world event fired on the fx bus; observed: "
                + capturedEvents);
        // THE PIN — the reference: DyeItem.useOn line 45 —
        // world.doEvent(2005, pos, 0) over the ACTING world's own
        // listener list. The engine's five bone-meal arms write the
        // nether cell (the crop grows) but stamp the event 0.
        assertEquals(-1, burst.dimension(),
                "VT-029: the nether bone meal's 2005 burst must carry the NETHER's "
                        + "dimension (DyeItem.useOn line 45: world.doEvent(2005, pos, 0) — "
                        + "the ACTING world's listener list, PlayerManager 626-640's "
                        + "dimension gate). Observed stamp: " + burst.dimension());
    }

    // ------------------------------------------------------------------
    // Member 6 — the flint ignite (the use-item sound family)
    // ------------------------------------------------------------------

    @Test
    void theNetherFlintIgniteSoundsInTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        PlayerSession lighter = join("NetherLighter");
        lighter.setDimension(-1);
        int fy = 100;
        netherPlatform(nether, fy);
        lighter.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the lighter settles");

        lighter.inventory().setSlot(0, new ItemStack(BuiltinItems.FLINT_AND_STEEL, 1));
        lighter.inventory().selectHotbarSlot(0);

        // The REAL use wire: the steel against the platform's top face —
        // the fire lands in the open cell above (the nether write) and
        // fire.ignite plays.
        server.useItemOnBlock(lighter, new BlockPosition(64, fy, 64), 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.FIRE.equals(nether.getBlock(
                        new BlockPosition(64, fy + 1, 64)))
                        && capturedSounds.stream()
                                .anyMatch(sound -> sound.name().equals("fire.ignite")),
                40, "the nether fire landed and its ignite sound fired");

        // THE CONTROL — the fire itself routed the ACTING world (the
        // write is correct; the sound alone is misstamped).
        assertTrue(BuiltinBlocks.FIRE.equals(nether.getBlock(
                        new BlockPosition(64, fy + 1, 64))),
                "the fire landed in the NETHER (the write routed correctly)");

        FxManager.Sound ignite = capturedSounds.stream()
                .filter(sound -> sound.name().equals("fire.ignite"))
                .findFirst().orElse(null);
        assertNotNull(ignite, "the ignite sound fired on the fx bus");
        // THE PIN — the reference: FlintAndSteelItem line 25 —
        // world.playSound(pos + 0.5, "fire.ignite", 1.0F, ...) over the
        // ACTING world's own listener list. The engine's arm writes the
        // nether fire but stamps the sound 0.
        assertEquals(-1, ignite.dimension(),
                "VT-029: the nether flint ignite must carry the NETHER's dimension "
                        + "(FlintAndSteelItem line 25: world.playSound over the ACTING "
                        + "world's listener list — PlayerManager 626-640's dimension gate). "
                        + "The fire landed in the NETHER; observed sound stamp: "
                        + ignite.dimension());
    }
}
