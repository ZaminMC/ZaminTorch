package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.VineBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vine family's placement DISPATCH (Wave VN-B — the end-to-end seat
 * over the real use wire, the seat shape adopted from the seventeenth
 * pass's VN-B1 review; the consult-level seats live in the block
 * package's VineGrowthDecayParityTest): the held vine item through
 * {@code useItemOnBlock} → {@code useItemArmOnTick}'s vine arm →
 * {@code placeVineOnTick} — the reference's BlockItem.useOn lines 20-65
 * over VineBlock.canBePlaced lines 131-143 + getPlacementState lines
 * 305-308.
 *
 * <p>THE SIX ARMS: the side placement's single OPPOSITE-face bit with
 * the survival consume (getPlacementState line 299); the four refusals
 * that consume nothing — the DOWN face (canBePlaced's default arm),
 * the open-sky UP click (the ceiling consult reads pos.up(), never the
 * floor below), the torch wall (canGrowOn: isCube && blocksMovement —
 * a torch is neither), and the GLASS wall (GlassBlock.isCube lines
 * 25-27 overrides false — a vine never grows on glass, the render-cube
 * consult the collision-shape consult alone would pass); the UP
 * placement under a lintel (the faceless vine_none, metadata 0); the
 * replaceable TARGET (a vine cell in the offset path takes the fresh
 * state, World.canPlace's replaceable half); and the replaceable
 * CLICKED arm (useOn lines 24-28 — clicking a vine cell directly
 * plants IN PLACE, the offset walk never runs).</p>
 */
class VinePlacementDispatchParityTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "vinedispatch", "it", 20, 4, 20,
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
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError(description);
    }

    private void hold(PlayerSession player, ItemStack stack) {
        player.inventory().setSlot(0, stack);
        player.inventory().selectHotbarSlot(0);
    }

    private void useOn(PlayerSession player, BlockPosition at, int face) {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
    }

    @Test
    void theDispatchCarriesTheGateTheStateAndBothReplaceArms() throws Exception {
        server = boot();
        PlayerSession placer = join("Placer");
        var world = server.world();
        int gx = (int) Math.floor(placer.position().x());
        int gy = (int) Math.floor(placer.position().y()) - 1;
        int gz = (int) Math.floor(placer.position().z());
        // A sky platform eight above the spawn ground: every cell the
        // seats assert AIR against is worldgen sky, never terrain.
        int py = gy + 8;
        BlockPosition floor = new BlockPosition(gx, py, gz);
        BlockPosition wall = new BlockPosition(gx, py + 1, gz + 3);
        BlockPosition torchWall = new BlockPosition(gx, py + 1, gz - 3);
        BlockPosition torchCell = new BlockPosition(gx, py + 2, gz - 3);
        BlockPosition glassWall = new BlockPosition(gx, py + 1, gz + 5);
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    world.setBlock(new BlockPosition(gx + dx, py, gz + dz), BuiltinBlocks.STONE);
                }
            }
            world.setBlock(wall, BuiltinBlocks.STONE);
            world.setBlock(torchWall, BuiltinBlocks.STONE);
            world.setBlock(torchCell, BuiltinBlocks.TORCH);
            world.setBlock(glassWall, BuiltinBlocks.GLASS);
        });
        await(() -> world.getBlock(wall).equals(BuiltinBlocks.STONE)
                && world.getBlock(torchCell).equals(BuiltinBlocks.TORCH)
                && world.getBlock(glassWall).equals(BuiltinBlocks.GLASS), "the scene seeded");

        // ------------------------------------------------------------------
        // ARM 1 — the side placement carries the single opposite-face bit
        // ------------------------------------------------------------------
        // Clicking the wall's NORTH face (face 2, the wall at +Z of the
        // vine cell) plants the vine's SOUTH bit (getPlacementState line
        // 299: the face TOWARD the wall) — one bit, never the bare
        // all-faces form (metadata 15) the pre-fix generic placement
        // carried. One vine consumed in survival.
        placer.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, py + 1,
                gz + 1.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "the placer settles");
        hold(placer, new ItemStack(BuiltinItems.VINE, 2, 0));
        useOn(placer, wall, 2);
        BlockPosition sideCell = wall.offset(0, 0, -1);
        await(() -> world.getBlock(sideCell).equals(VineBlocks.stateFor(VineBlocks.BIT_SOUTH)),
                "the side placement lands the vine's SOUTH bit (the face toward the wall)");
        await(() -> placer.inventory().held().count() == 1,
                "the placement consumed one vine");

        // ------------------------------------------------------------------
        // ARM 2 — the gate's refusals (the ceiling, the open sky, the
        // torch, the glass); every refusal consumes nothing
        // ------------------------------------------------------------------
        // The DOWN face never places (canBePlaced's default arm). The UP
        // face with open sky above the target cell refuses (the gate
        // reads pos.up()). A horizontal face whose CLICKED wall is a
        // torch refuses (not a cube). A horizontal face whose clicked
        // wall is GLASS refuses too — GlassBlock.isCube overrides false,
        // so canGrowOn (isCube && blocksMovement) never passes glass:
        // the render-cube consult, the one law a collision-shape consult
        // alone cannot see (glass carries a FULL collision box).
        hold(placer, new ItemStack(BuiltinItems.VINE, 3, 0));
        useOn(placer, floor, 0); // DOWN — the ceiling rule
        useOn(placer, floor, 1); // UP — open sky above the target cell
        useOn(placer, torchCell, 2); // the torch's north face — not a cube wall
        useOn(placer, glassWall, 2); // the glass wall's north face — never a vine surface
        EngineAwaits.awaitTicks(world, 2, "the refusals settle");
        assertTrue(world.getBlock(floor.offset(0, -1, 0)).equals(world.airType()),
                "the DOWN face never plants a vine");
        assertTrue(world.getBlock(floor.offset(0, 1, 0)).equals(world.airType()),
                "the open-sky UP placement never lands");
        assertTrue(world.getBlock(torchCell.offset(0, 0, -1)).equals(world.airType()),
                "a torch never carries a vine");
        assertTrue(world.getBlock(glassWall.offset(0, 0, -1)).equals(world.airType()),
                "glass never carries a vine (the render-cube consult)");
        assertEquals(3, placer.inventory().held().count(),
                "every refused placement consumed nothing");

        // ------------------------------------------------------------------
        // ARM 3 — the UP placement hangs from the solid above
        // ------------------------------------------------------------------
        // A lintel seeded two above the platform (the target cell's own
        // up-neighbor) gates the UP placement in: clicking the floor's
        // top face plants the all-clear form one above the floor
        // (getPlacementState's vertical arm — metadata 0, the dormant
        // vine_none, the transient form the decay pops at the first
        // neighbour change).
        BlockPosition lintel = floor.offset(0, 2, 0);
        server.ticker().submit(() -> world.setBlock(lintel, BuiltinBlocks.STONE));
        await(() -> world.getBlock(lintel).equals(BuiltinBlocks.STONE), "the lintel seeded");
        placer.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, py + 1,
                gz - 1.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "the placer moves off the hang cell");
        hold(placer, new ItemStack(BuiltinItems.VINE, 2, 0));
        useOn(placer, floor, 1);
        BlockPosition hangCell = floor.offset(0, 1, 0);
        await(() -> world.getBlock(hangCell).equals(VineBlocks.stateFor(0)),
                "the UP placement lands the all-clear form under the solid above");
        await(() -> placer.inventory().held().count() == 1,
                "the hanging placement consumed one vine");

        // ------------------------------------------------------------------
        // ARM 4 — the replaceable pair (the target AND the clicked cell)
        // ------------------------------------------------------------------
        // 4a — the replaceable TARGET: the side cell carries a DIFFERENT
        // vine state (the NORTH bit); clicking the wall's NORTH face
        // again targets that vine cell — World.canPlace's replaceable
        // half lets the placement REPLACE it, the fresh SOUTH-bit state
        // landing in place of the seeded NORTH-bit form.
        server.ticker().submit(() -> world.setBlock(sideCell,
                VineBlocks.stateFor(VineBlocks.BIT_NORTH)));
        await(() -> world.getBlock(sideCell).equals(VineBlocks.stateFor(VineBlocks.BIT_NORTH)),
                "the seeded NORTH-bit vine waits");
        placer.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, py + 1,
                gz + 1.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "the placer returns to the wall");
        hold(placer, new ItemStack(BuiltinItems.VINE, 2, 0));
        useOn(placer, wall, 2);
        await(() -> world.getBlock(sideCell).equals(VineBlocks.stateFor(VineBlocks.BIT_SOUTH)),
                "the vine cell accepted the replace placement (the SOUTH bit over the seeded NORTH)");
        await(() -> placer.inventory().held().count() == 1,
                "the replace placement consumed one vine");

        // 4b — the replaceable CLICKED arm (useOn lines 24-28): a
        // vine_east seeded between two stones — the EAST stone keeps its
        // own bit alive through the stones' deferred wakes (a bitless
        // seed would pop at the first wake: hasConnections answers
        // false on every bit gone), the SOUTH stone passes the click's
        // own face gate. Clicking THE VINE ITSELF (its north face)
        // plants IN PLACE — the offset walk never runs for a replaceable
        // clicked cell — and the state carries the SOUTH bit pointing at
        // the gate's stone (a distinguishable replace: vine_east in,
        // vine_south out).
        BlockPosition arm4bSouthStone = new BlockPosition(gx, py + 1, gz - 1);
        BlockPosition arm4bEastStone = new BlockPosition(gx + 1, py + 1, gz - 2);
        BlockPosition clickedVine = new BlockPosition(gx, py + 1, gz - 2);
        server.ticker().submit(() -> {
            world.setBlock(arm4bSouthStone, BuiltinBlocks.STONE);
            world.setBlock(arm4bEastStone, BuiltinBlocks.STONE);
            world.setBlock(clickedVine, VineBlocks.stateFor(VineBlocks.BIT_EAST));
        });
        await(() -> world.getBlock(arm4bSouthStone).equals(BuiltinBlocks.STONE)
                && world.getBlock(arm4bEastStone).equals(BuiltinBlocks.STONE)
                && world.getBlock(clickedVine).equals(VineBlocks.stateFor(VineBlocks.BIT_EAST)),
                "the stone pair and the stable vine_east seeded");
        hold(placer, new ItemStack(BuiltinItems.VINE, 1, 0));
        useOn(placer, clickedVine, 2);
        await(() -> world.getBlock(clickedVine).equals(VineBlocks.stateFor(VineBlocks.BIT_SOUTH)),
                "the clicked vine cell took the placement IN PLACE (the replaceable clicked arm)");
        await(() -> placer.inventory().held().count() == 0,
                "the in-place placement consumed the last vine");
        server.shutdown(null);
    }
}
