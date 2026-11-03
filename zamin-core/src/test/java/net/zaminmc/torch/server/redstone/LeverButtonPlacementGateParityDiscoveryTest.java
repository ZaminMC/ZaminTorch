package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-038's behavioral seats — the lever/button ITEM-PLACEMENT GATES read
 * the fallback arms, not the reference's clicked-face gate (reference/
 * 1.8.8, the four-level chain read whole on the pass-1 source walk):
 *
 * <ul>
 * <li>{@code BlockItem.useOn} lines 28-67 — the gate at line 43:
 *     {@code world.canPlace(this.block, pos, false, face, null, stack)}
 *     runs BEFORE {@code getPlacementState} (line 45); a failed gate
 *     returns false with NO placement, NO sound and NO item consumption
 *     (the {@code stack.size--} at line 61 rides the placement arm
 *     alone).</li>
 * <li>{@code World.canPlace} lines 2274-2282 —
 *     {@code blockx.getMaterial().isReplaceable() && block.canBePlaced(
 *     this, pos, face, item)} — the CLICKED face's consult.</li>
 * <li>{@code Block.canBePlaced} lines 656-661 — the 4-arg form
 *     delegating to the 3-arg; {@code LeverBlock.canBePlaced} lines
 *     41-43 and {@code ButtonBlock.canBePlaced} lines 52-54 both answer
 *     {@code canSurvive(world, pos, face.getOpposite())} — the clicked
 *     face's MOUNT ALONE, never a floor fallback.</li>
 * <li>{@code ButtonBlock.canSurvive} lines 70-73 (LeverBlock
 *     delegating lines 59-61): {@code facing == DOWN ?
 *     World.hasSolidTop(mount) : mount.getBlock().isSolid()} — the
 *     F-SP1-corrected side law; {@code Block.isSolid} lines 274-276 =
 *     {@code material.isSolidBlocking() && isCube() && !isSignalSource()}
 *     — every signal source (the torch among them) FAILS the wall
 *     mount, every translucent (the glass family) fails the floor
 *     mount.</li>
 * </ul>
 *
 * <p>THE DEAD CODE: {@code LeverBlock.getPlacementState} lines 69-76 (the
 * {@code Plane.HORIZONTAL} walk, the floor form at line 75, the doomed
 * default) and {@code ButtonBlock.getPlacementState} lines 85-88 (the
 * DOWN form) are UNREACHABLE through the item path — the gate at
 * useOn line 43 passes only when the SAME consult the first arm reads
 * already passed, so the fallback arms never run for an item placement.
 * The engine's {@code EngineServer.placeLeverOnTick} (the floor-only
 * fallback arm: {@code hasSolidTop(below target)} placing
 * {@code leverOf(5, false)}) and {@code EngineServer.placeButtonOnTick}
 * (the mount-failure DOWN placement: {@code facing = 0} placed
 * unconditionally) place the phantom forms vanilla refuses outright.</p>
 *
 * <p>THE CONSTRUCTION: every seat drives the REAL item-use path (the
 * lever/button item held by a survival player, the click on the seeded
 * mount), then asserts the VANILLA verdict. The three divergence seats
 * are RED BY CONTRACT on unfixed develop (the phantom floor lever, the
 * phantom DOWN buttons — the engine placing what vanilla refuses, and
 * consuming the item vanilla keeps); the refusal-agreement seat and the
 * four placement controls are GREEN on develop (the placement path
 * itself live — the fix's regression pins).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeverButtonPlacementGateParityDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "levergate", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }

    private void hold(PlayerSession player, ItemType held) {
        player.inventory().setSlot(0, ItemStack.of(held, 1));
        player.inventory().selectHotbarSlot(0);
    }

    /** A free floor cell two blocks east of the player, one below the feet. */
    private BlockPosition anchorNear(PlayerSession player) {
        return new BlockPosition(
                (int) Math.floor(player.position().x()) + 2,
                (int) Math.floor(player.position().y()) - 1,
                (int) Math.floor(player.position().z()));
    }

    private void seed(BlockPosition at, BlockType type)
            throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "the cell seeded " + type);
    }

    private void clear(BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, server.world().airType()));
        await(() -> server.world().getBlock(at).equals(server.world().airType()),
                "the cell cleared");
    }

    private BlockType typeAt(BlockPosition at) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(server.world().getBlock(at)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return result.get();
    }

    // ------------------------------------------------------------------
    // The divergence seats — RED BY CONTRACT on unfixed develop
    // ------------------------------------------------------------------

    /**
     * THE LEVER ROW: a redstone torch standing on stone; the torch's EAST
     * side clicked with a lever held (the target beside the torch, above
     * stone). Vanilla refuses outright — the gate consults the clicked
     * face's mount ALONE (canBePlaced(pos, EAST) → canSurvive(pos, WEST)
     * → the torch's isSolid(): the signal-source arm of Block.isSolid
     * lines 274-276 — a torch is no wall mount), and the floor fallback
     * the engine reads is DEAD CODE in the reference's item path. The
     * engine places the phantom floor lever (leverOf(5) — the up_z form,
     * the axis hardcoded off the player's look) and consumes the item.
     */
    @Test
    void theLeverRefusesTheSignalSourceSideClickVanillaRefuses() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("LeverBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition floorWest = anchorNear(builder);
            BlockPosition floorEast = floorWest.offset(1, 0, 0);
            BlockPosition torchCell = floorWest.offset(0, 1, 0);
            BlockPosition target = floorEast.offset(0, 1, 0);
            seed(floorWest, BuiltinBlocks.STONE);
            seed(floorEast, BuiltinBlocks.STONE);
            clear(torchCell);
            clear(target);
            seed(torchCell, RedstoneBlocks.torchStandingLit());
            hold(builder, item("minecraft:lever"));
            server.useItemOnBlock(builder, torchCell, 5, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600); // the settle window past, the vanilla verdict stands
            assertFalse(RedstoneBlocks.isLever(typeAt(target)),
                    "VT-038: no lever beside the torch — the clicked face's mount (the "
                            + "torch, a signal source) fails Block.isSolid and the "
                            + "reference's item gate refuses BEFORE any fallback arm "
                            + "runs (BlockItem.useOn line 43 over LeverBlock."
                            + "canBePlaced lines 41-43). Observed: "
                            + typeAt(target).identifier() + " at the target — the "
                            + "engine's phantom floor lever (placeLeverOnTick's "
                            + "fallback arm).");
            assertEquals(1, builder.inventory().held().count(),
                    "VT-038: the lever item unspent — the refused use never consumes "
                            + "(the reference's stack.size-- rides the placement arm "
                            + "alone, BlockItem.useOn line 61)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE BUTTON ROW (translucent top): a glass block standing on stone;
     * the glass TOP clicked with a stone button held. Vanilla refuses —
     * canBePlaced(pos, UP) → canSurvive(pos, DOWN) → hasSolidTop(glass) =
     * false (Material.GLASS is translucent, World.hasSolidTop arm 1). The
     * engine places the phantom DOWN-facing button above the glass and
     * consumes the item; the button floats (no ceiling) until the first
     * neighbor change pops it, dropping a button item.
     */
    @Test
    void theButtonRefusesTheTranslucentTopClickVanillaRefuses() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("ButtonBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition floor = anchorNear(builder);
            BlockPosition glassCell = floor.offset(0, 1, 0);
            BlockPosition target = glassCell.offset(0, 1, 0);
            seed(floor, BuiltinBlocks.STONE);
            clear(glassCell);
            clear(target);
            seed(glassCell, BuiltinBlocks.GLASS);
            hold(builder, item("minecraft:stone_button"));
            server.useItemOnBlock(builder, glassCell, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertFalse(RedstoneBlocks.isButton(typeAt(target)),
                    "VT-038: no button above the glass — the clicked face's mount "
                            + "(GLASS, translucent) fails World.hasSolidTop and the "
                            + "reference's item gate refuses BEFORE the DOWN fallback "
                            + "runs (BlockItem.useOn line 43 over ButtonBlock."
                            + "canBePlaced lines 52-54 → canSurvive lines 70-73). "
                            + "Observed: " + typeAt(target).identifier() + " at the "
                            + "target — the engine's phantom DOWN button "
                            + "(placeButtonOnTick's unconditional fallback).");
            assertEquals(1, builder.inventory().held().count(),
                    "VT-038: the button item unspent — the refused use never consumes");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE BUTTON ROW (signal-source side): the torch's EAST side clicked
     * with a wooden button held — the family's other member, the same
     * gate. Vanilla refuses (the torch fails Block.isSolid's
     * signal-source arm); the engine places the phantom DOWN button
     * floating in the air beside the torch and consumes the item.
     */
    @Test
    void theWoodenButtonRefusesTheSignalSourceSideClickVanillaRefuses() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("WoodBtnBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition floorWest = anchorNear(builder);
            BlockPosition floorEast = floorWest.offset(1, 0, 0);
            BlockPosition torchCell = floorWest.offset(0, 1, 0);
            BlockPosition target = floorEast.offset(0, 1, 0);
            seed(floorWest, BuiltinBlocks.STONE);
            seed(floorEast, BuiltinBlocks.STONE);
            clear(torchCell);
            clear(target);
            seed(torchCell, RedstoneBlocks.torchStandingLit());
            hold(builder, item("minecraft:wooden_button"));
            server.useItemOnBlock(builder, torchCell, 5, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertFalse(RedstoneBlocks.isButton(typeAt(target)),
                    "VT-038: no wooden button beside the torch — the clicked face's "
                            + "mount (the torch, a signal source) fails Block.isSolid "
                            + "and the reference's item gate refuses BEFORE the DOWN "
                            + "fallback runs. Observed: " + typeAt(target).identifier()
                            + " at the target — the engine's phantom DOWN button.");
            assertEquals(1, builder.inventory().held().count(),
                    "VT-038: the button item unspent — the refused use never consumes");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The agreement seats — GREEN on unfixed develop (the fix's pins)
    // ------------------------------------------------------------------

    /**
     * THE REFUSAL AGREEMENT: the same torch-side lever click, but the
     * floor below the target is GLASS — the engine's own fallback arm
     * (hasSolidTop) fails too, so BOTH trees refuse. The attribution row:
     * the engine's placement machinery refuses correctly when its consult
     * answers false; the divergence above is the fallback ARM alone, not
     * a broken occupancy gate or a dead use path.
     */
    @Test
    void theLeverRefusalAgreesWhenTheFloorTooFailsHasSolidTop() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("LeverRefuser");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition floorWest = anchorNear(builder);
            BlockPosition floorEast = floorWest.offset(1, 0, 0);
            BlockPosition torchCell = floorWest.offset(0, 1, 0);
            BlockPosition target = floorEast.offset(0, 1, 0);
            seed(floorWest, BuiltinBlocks.STONE);
            seed(floorEast, BuiltinBlocks.GLASS);
            clear(torchCell);
            clear(target);
            seed(torchCell, RedstoneBlocks.torchStandingLit());
            hold(builder, item("minecraft:lever"));
            server.useItemOnBlock(builder, torchCell, 5, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertFalse(RedstoneBlocks.isLever(typeAt(target)),
                    "the agreement refusal: no lever beside the torch when the floor "
                            + "below the target fails hasSolidTop too (GLASS) — both "
                            + "trees refuse");
            assertEquals(1, builder.inventory().held().count(),
                    "the agreement refusal: the item unspent");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE WALL LEVER CONTROL: a stone pillar's EAST side clicked with a
     * lever held — the mount (stone) passes Block.isSolid, so BOTH trees
     * place the wall lever (the engine's first arm, the reference's
     * first getPlacementState arm — the same consult, the same verdict).
     */
    @Test
    void theWallLeverControlPlacesOnTheSolidMount() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("WallLeverBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition floor = anchorNear(builder);
            BlockPosition pillar = floor.offset(0, 1, 0);
            BlockPosition target = pillar.offset(1, 0, 0);
            seed(floor, BuiltinBlocks.STONE);
            clear(pillar);
            clear(target);
            seed(pillar, BuiltinBlocks.STONE);
            hold(builder, item("minecraft:lever"));
            server.useItemOnBlock(builder, pillar, 5, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(RedstoneBlocks.isLever(typeAt(target)),
                    "the wall lever control: the lever places against the stone's "
                            + "east face (the mount passes Block.isSolid — both trees "
                            + "agree). Observed: " + typeAt(target).identifier());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE FLOOR LEVER CONTROL: the stone's TOP clicked with a lever held
     * — the mount (stone) passes hasSolidTop, so BOTH trees place the
     * floor lever through the FIRST arm (the fallback never needed).
     */
    @Test
    void theFloorLeverControlPlacesOnTheSolidTop() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("FloorLevBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition floor = anchorNear(builder);
            BlockPosition target = floor.offset(0, 1, 0);
            seed(floor, BuiltinBlocks.STONE);
            clear(target);
            hold(builder, item("minecraft:lever"));
            server.useItemOnBlock(builder, floor, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(RedstoneBlocks.isLever(typeAt(target)),
                    "the floor lever control: the lever places on the stone's top "
                            + "(the mount passes World.hasSolidTop — both trees "
                            + "agree). Observed: " + typeAt(target).identifier());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE UP BUTTON CONTROL: the stone's TOP clicked with a stone button
     * held — the mount passes hasSolidTop, so BOTH trees place the
     * UP-facing button (the engine's facing = face = 1; the reference's
     * first getPlacementState arm setting FACING = the clicked face).
     */
    @Test
    void theUpButtonControlPlacesOnTheSolidTop() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("UpButtonBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition floor = anchorNear(builder);
            BlockPosition target = floor.offset(0, 1, 0);
            seed(floor, BuiltinBlocks.STONE);
            clear(target);
            hold(builder, item("minecraft:stone_button"));
            server.useItemOnBlock(builder, floor, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(RedstoneBlocks.isButton(typeAt(target)),
                    "the up button control: the stone button places on the stone's "
                            + "top (both trees agree). Observed: "
                            + typeAt(target).identifier());
            assertEquals(1, RedstoneBlocks.buttonFacing(typeAt(target)),
                    "the up button control: the button faces UP (the clicked face — "
                            + "ButtonBlock.getPlacementState's first arm). Observed: "
                            + typeAt(target).identifier());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE WOODEN UP BUTTON CONTROL: the same stone-top click with the
     * wooden button — the family's other member through the same gate.
     */
    @Test
    void theWoodenUpButtonControlPlacesOnTheSolidTop() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("WoodUpBtnBuildr");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition floor = anchorNear(builder);
            BlockPosition target = floor.offset(0, 1, 0);
            seed(floor, BuiltinBlocks.STONE);
            clear(target);
            hold(builder, item("minecraft:wooden_button"));
            server.useItemOnBlock(builder, floor, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(RedstoneBlocks.isButton(typeAt(target)),
                    "the wooden up button control: the wooden button places on the "
                            + "stone's top (both trees agree). Observed: "
                            + typeAt(target).identifier());
            assertTrue(RedstoneBlocks.buttonWooden(typeAt(target)),
                    "the wooden pair member placed");
            assertEquals(1, RedstoneBlocks.buttonFacing(typeAt(target)),
                    "the wooden up button control: the button faces UP");
        } finally {
            server.shutdown(() -> { });
        }
    }
}
