package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE DIG HANDLER'S OWN GATES (ServerPlayNetworkHandler.handlePlayerDig
 * lines 424-456, 1:1 — Slice N6): the dig's own six-block band from the
 * feet raised by the reference's {@code + 1.5} (never the 1.62 standing
 * eye, never the use walk's 64.0), the START consults' refusal echo, the
 * extinguish arm, and the finish/abort tail echo.
 *
 * <p>THE REAL BUG THIS SLICE REPAIRS: the old survival band was the
 * 4.5-block reach from the 1.62 eye — a dig at five blocks that vanilla
 * opens, the engine refused; and the early finish never answered the
 * standing block (the "prediction stays" comment missed the reference's
 * handler-level BlockUpdate tail).</p>
 */
class DigHandlerGatesAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession player;
    /** The recording link: the dig's echoes — the refusal echo and the tail echo. */
    private final List<String> resyncs = new ArrayList<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.shutdown(null);
        }
    }

    private void boot(GameMode mode) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dig" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                mode, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
            @Override public void blockResync(net.zaminmc.torch.block.BlockPosition position,
                    net.zaminmc.torch.block.BlockType type) {
                resyncs.add(position + "=" + type.identifier());
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /**
     * Places the dig target the way the engine's own placement commits it,
     * then walks the player to the given standing position (the test's own
     * authoritative teleport — the same verb the N2 geometry repairs used).
     */
    private void seedAndStand(BlockPosition target, net.zaminmc.torch.block.BlockType type,
                              Position standAt) throws InterruptedException {
        server.blockInteraction().submitPlace(player, target.offset(0, -1, 0), 1, type);
        await(() -> server.world().getBlock(target).equals(type),
                "the target block landed at " + target);
        player.applyMovement(standAt, net.zaminmc.torch.util.Rotation.ZERO, true);
        Thread.sleep(200); // the movement lands before the dig submissions
    }

    /** The inside-edge stand: 5.5 blocks out — the OLD 4.5 band refused, the dig band opens. */
    private static final Position INSIDE_EDGE = new Position(8.0, 5.0, 2.5);
    /** The outside stand: 6.5 blocks out — beyond the dig band, nothing answers. */
    private static final Position OUTSIDE = new Position(9.0, 5.0, 2.5);

    /**
     * THE BAND WIDENING'S OWN EVIDENCE: a survival dig at 5.5 blocks
     * (dx 5.5, dy the reference's +1.5 over the same-level centre, d²
     * 31.25) — the OLD band (4.5 from the 1.62 eye, d² 31.5 against
     * 20.25) refused the START outright; the reference's dig band opens
     * it and the full cycle breaks the block.
     */
    @Test
    void theSixBlockDigBandOpensWhatTheOldSurvivalBandRefused() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Digger");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedAndStand(target, BuiltinBlocks.DIRT, INSIDE_EDGE);

        server.blockInteraction().submitMiningStart(player, target, 1);
        Thread.sleep(900); // dirt by hand: 15 ticks nominal; the 0.7 accept floor passes
        server.blockInteraction().submitMiningFinished(player, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the inside-edge dig broke the block (the old 4.5 band refused this START)");
        assertTrue(resyncs.isEmpty(),
                "the accepted finish answers no echo (the cell is air): " + resyncs);
    }

    /**
     * THE BEYOND-BAND REFUSAL (the reference's {@code d3 > 36.0 -> return}):
     * at 6.5 blocks (d² 43.25) the START answers NOTHING — no session, no
     * echo — and the FINISH from the same position answers nothing too
     * (the block never breaks).
     */
    @Test
    void aDigBeyondTheBandAnswersNothing() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("FarArms");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedAndStand(target, BuiltinBlocks.DIRT, OUTSIDE);

        server.blockInteraction().submitMiningStart(player, target, 1);
        Thread.sleep(900);
        server.blockInteraction().submitMiningFinished(player, target);
        Thread.sleep(400);
        assertFalse(server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "a finish beyond the band never breaks the block");
        assertTrue(resyncs.isEmpty(),
                "the beyond-band refusal answers NOTHING — no echo either: " + resyncs);
    }

    /**
     * THE START CONSULTS' REFUSAL ECHO (handlePlayerDig lines 437-445):
     * with an operator armed, a non-operator inside the chebyshev band
     * (the {@code spawn-protection} default 16) gets NO dig — the refusal
     * answers the BlockUpdate echo (the reference's {@code sendPacket(new
     * BlockUpdateS2CPacket(...))}) and the block stands; the operator's
     * own dig passes freely.
     */
    @Test
    void theStartConsultsRefusalAnswersTheEcho() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Guest"); // the non-operator
        PlayerSession owner = join("Owner");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedAndStand(target, BuiltinBlocks.DIRT, new Position(0.5, 5.0, 0.5));

        server.ticker().submit(() -> server.opStore().op(owner.uuid(), owner.name(), 4));
        await(() -> server.opStore().isOp(owner.uuid()), "the owner opped");

        // The guest's START: the consult refuses, the echo answers.
        server.blockInteraction().submitMiningStart(player, target, 1);
        Thread.sleep(400);
        await(() -> resyncs.stream().anyMatch(r -> r.startsWith("BlockPosition[x=2, y=5, z=2]=minecraft:")),
                "the refused START answered the BlockUpdate echo");
        Thread.sleep(900);
        assertFalse(server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the refused consult never opened the dig");

        // The owner's own dig: the operator passes freely — the owner
        // stands the inside edge for the START.
        resyncs.clear();
        owner.applyMovement(INSIDE_EDGE, net.zaminmc.torch.util.Rotation.ZERO, true);
        Thread.sleep(200);
        server.blockInteraction().submitMiningStart(owner, target, 1);
        Thread.sleep(900);
        server.blockInteraction().submitMiningFinished(owner, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the operator's dig passed the consult freely");
    }

    /**
     * THE EXTINGUISH ARM (startMiningBlock line 128 over World
     * .extinguishFire lines 1639-1647): a FIRE block at the START's
     * face-offset cell dies (the fizz beside it) and the dig still opens
     * — the reference discards the extinguish's return on the survival
     * path.
     */
    @Test
    void theSurvivalStartExtinguishesFireAtTheFaceOffset() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Fireman");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedAndStand(target, BuiltinBlocks.DIRT, INSIDE_EDGE);
        BlockPosition fireAt = target.offset(0, 1, 0); // the UP face-offset cell
        server.ticker().submit(() -> server.world().setBlock(fireAt, BuiltinBlocks.FIRE));
        await(() -> server.world().getBlock(fireAt).equals(BuiltinBlocks.FIRE),
                "the fire landed at " + fireAt);

        // START with the UP face: the fire at the offset dies, the dig opens.
        server.blockInteraction().submitMiningStart(player, target, 1);
        await(() -> server.world().getBlock(fireAt).equals(BuiltinBlocks.AIR),
                "the dig start extinguished the fire at the face-offset cell");
        Thread.sleep(900);
        server.blockInteraction().submitMiningFinished(player, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the extinguish never stopped the dig from opening");
    }

    /**
     * THE CREATIVE EXTINGUISH-FIRST LAW (startMiningBlock lines 105-110):
     * {@code if (!extinguishFire(null, pos, face)) tryMineBlock(pos)} — a
     * fire at the face-offset cell consumes the click itself: the fire
     * dies, the block behind it SURVIVES; the same break with no fire to
     * consume breaks the block.
     */
    @Test
    void theCreativeExtinguishFirstConsumesTheClick() throws Exception {
        boot(GameMode.CREATIVE);
        player = join("Pyro");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedAndStand(target, BuiltinBlocks.STONE, INSIDE_EDGE);
        BlockPosition fireAt = target.offset(0, 1, 0);
        server.ticker().submit(() -> server.world().setBlock(fireAt, BuiltinBlocks.FIRE));
        await(() -> server.world().getBlock(fireAt).equals(BuiltinBlocks.FIRE),
                "the fire landed at " + fireAt);

        // The break with fire at the face-offset: the fire dies, the stone stands.
        server.blockInteraction().submitCreativeBreak(player, target, 1);
        await(() -> server.world().getBlock(fireAt).equals(BuiltinBlocks.AIR),
                "the creative break extinguished the fire");
        Thread.sleep(400);
        assertEquals(BuiltinBlocks.STONE, server.world().getBlock(target),
                "the extinguish consumed the click — the block behind the fire SURVIVES");

        // The same break with nothing to extinguish: the stone dies.
        server.blockInteraction().submitCreativeBreak(player, target, 1);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the unencumbered break broke the block");
    }

    /**
     * THE TAIL ECHO ON THE EARLY PARK (handlePlayerDig lines 449-452):
     * an early finish parks the dig (progress below the 0.7 accept) and
     * the still-standing block answers the digger's link — the old code's
     * "prediction stays" missed the reference's handler-level BlockUpdate;
     * the parked dig still self-completes on the vanilla clock.
     */
    @Test
    void theEarlyFinishTailEchoAnswersTheStandingBlock() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Hasty");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedAndStand(target, BuiltinBlocks.DIRT, INSIDE_EDGE);

        server.blockInteraction().submitMiningStart(player, target, 1);
        Thread.sleep(100); // far below the 0.7 floor: the park
        server.blockInteraction().submitMiningFinished(player, target);
        await(() -> resyncs.stream().anyMatch(r -> r.startsWith("BlockPosition[x=2, y=5, z=2]=minecraft:")),
                "the early-parked finish answered the standing block (the tail echo)");
        assertFalse(server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the park itself never mines on the spot");
        // The wasMining accumulator self-completes at progress 1.0.
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the parked dig self-completed on the vanilla clock");
    }

    /**
     * THE IGNORED ABORT (the reference's {@code return} beyond the band):
     * an abort from beyond six blocks is IGNORED — the session stays live
     * — and the back-in-band finish afterwards is accepted with the
     * session still open (a killed session would have refused it).
     */
    @Test
    void theAbortBeyondTheBandLeavesTheSessionLive() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Wanderer");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedAndStand(target, BuiltinBlocks.DIRT, INSIDE_EDGE);

        server.blockInteraction().submitMiningStart(player, target, 1);
        Thread.sleep(100);
        player.applyMovement(OUTSIDE, net.zaminmc.torch.util.Rotation.ZERO, true);
        Thread.sleep(200);
        server.blockInteraction().submitMiningAborted(player, target); // beyond the band
        Thread.sleep(300);
        assertTrue(resyncs.isEmpty(),
                "the ignored abort answers nothing — no echo: " + resyncs);

        // Back inside the band: the finish runs WITH the session the abort never killed.
        player.applyMovement(INSIDE_EDGE, net.zaminmc.torch.util.Rotation.ZERO, true);
        Thread.sleep(700); // past the 0.7 floor from the ORIGINAL start
        server.blockInteraction().submitMiningFinished(player, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the ignored abort left the session live — the finish broke the block");
    }

    /**
     * THE IN-BAND ABORT'S OWN TAIL (the reference's shared else-band tail):
     * an abort inside the band clears the live dig and answers the standing
     * block; the dig never completes afterwards (the cleared session's
     * finish is refused — the tail echo answers again, the block stands).
     */
    @Test
    void theInBandAbortClearsTheDigAndEchoesTheStandingBlock() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Quitter");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedAndStand(target, BuiltinBlocks.DIRT, INSIDE_EDGE);

        server.blockInteraction().submitMiningStart(player, target, 1);
        Thread.sleep(100);
        server.blockInteraction().submitMiningAborted(player, target); // inside the band
        await(() -> resyncs.stream().anyMatch(r -> r.startsWith("BlockPosition[x=2, y=5, z=2]=minecraft:")),
                "the abort's tail answered the standing block");
        Thread.sleep(1_200); // past the full nominal duration
        server.blockInteraction().submitMiningFinished(player, target);
        Thread.sleep(400);
        assertFalse(server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the cleared dig never completes — the block stands");
        long echoes = resyncs.stream()
                .filter(r -> r.startsWith("BlockPosition[x=2, y=5, z=2]=minecraft:")).count();
        assertTrue(echoes >= 2,
                "the abort's echo AND the refused finish's echo both answered: " + resyncs);
    }
}
