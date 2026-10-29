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
 * The vine family's placement laws (VN-B1 — the reference's
 * VineBlock.canBePlaced lines 132-148 over World.canPlace lines
 * 2274-2282 + getPlacementState lines 296-300, each arm read against
 * the reference before its fix):
 *
 * <p>THE GATE: the clicked face decides — UP requires the solid cube
 * ABOVE the placement cell ({@code canGrowOn(pos.up())}, the hanging
 * form — a plain floor placement with open sky never lands), a
 * horizontal face requires the CLICKED WALL itself to be the growable
 * solid ({@code canGrowOn} = {@code isCube && blocksMovement} — a
 * torch wall never carries a vine), and DOWN never places (a vine
 * never attaches to a ceiling). A refused placement consumes
 * nothing.</p>
 *
 * <p>THE STATE: exactly ONE face bit, the face TOWARD the clicked
 * wall (the opposite of the clicked face — clicking the wall's NORTH
 * face carries the vine's SOUTH bit); a vertical (UP) placement keeps
 * the all-clear form (metadata 0). The pre-fix engine's generic
 * placement carried the BARE all-faces form (metadata 15) with no
 * gate — mid-air, on ceilings, against torches, every placement the
 * reference refuses.</p>
 *
 * <p>THE MATERIAL: every vine state is REPLACEABLE (the reference's
 * Material.REPLACEABLE_PLANT, canBeReplaced lines 60-62) — a vine
 * held against an existing vine cell replaces it (World.canPlace's
 * replaceable half). The pre-fix engine's replaceable set had no vine
 * row: the placement into a vine cell was refused.</p>
 */
class VinePlacementParityTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "vineplace", "it", 20, 4, 20,
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
    void thePlacementLawsOwnTheirFacesStatesAndGates() throws Exception {
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
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 3; dz++) {
                    world.setBlock(new BlockPosition(gx + dx, py, gz + dz), BuiltinBlocks.STONE);
                }
            }
            world.setBlock(wall, BuiltinBlocks.STONE);
            world.setBlock(torchWall, BuiltinBlocks.STONE);
            world.setBlock(torchCell, BuiltinBlocks.TORCH);
        });
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE)
                && world.getBlock(wall).equals(BuiltinBlocks.STONE)
                && world.getBlock(torchCell).equals(BuiltinBlocks.TORCH), "the scene seeded");

        // ------------------------------------------------------------------
        // ARM 1 — the side placement carries the single opposite-face bit
        // ------------------------------------------------------------------
        // Clicking the wall's NORTH face (face 2, the wall at +Z of the
        // vine cell) plants the vine's SOUTH bit (getPlacementState line
        // 299: the face TOWARD the wall) — one bit, never the bare
        // all-faces form the pre-fix generic placement carried.
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
        // ARM 2 — the gate's refusals (the ceiling, the open sky, the torch)
        // ------------------------------------------------------------------
        // The DOWN face never places (canBePlaced's default arm: false) —
        // nothing plants under the platform, nothing consumed. The UP face
        // with open sky above the target cell refuses
        // (canGrowOn(pos.up())). A horizontal face whose CLICKED wall is
        // a torch (not a cube) refuses.
        hold(placer, new ItemStack(BuiltinItems.VINE, 3, 0));
        useOn(placer, floor, 0); // DOWN — the ceiling rule
        useOn(placer, floor, 1); // UP — open sky above the target cell
        placer.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, py + 1,
                gz - 1.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "the placer moves to the torch wall");
        useOn(placer, torchCell, 2); // the torch's north face — not a cube wall
        EngineAwaits.awaitTicks(world, 2, "the refusals settle");
        assertTrue(world.getBlock(floor.offset(0, -1, 0)).equals(world.airType()),
                "the DOWN face never plants a vine");
        assertTrue(world.getBlock(floor.offset(0, 1, 0)).equals(world.airType()),
                "the open-sky UP placement never lands");
        assertTrue(world.getBlock(torchCell.offset(0, 0, -1)).equals(world.airType()),
                "a torch never carries a vine");
        assertEquals(3, placer.inventory().held().count(),
                "every refused placement consumed nothing");

        // ------------------------------------------------------------------
        // ARM 3 — the UP placement hangs from the solid above
        // ------------------------------------------------------------------
        // A lintel seeded two above the platform (the target cell's own
        // up-neighbor) gates the UP placement in: clicking the floor's
        // top face plants the all-clear form one above the floor
        // (getPlacementState's vertical arm — metadata 0, the dormant
        // vine_none). The pre-fix engine placed the bare all-faces form
        // wherever asked.
        BlockPosition lintel = floor.offset(0, 2, 0);
        server.ticker().submit(() -> world.setBlock(lintel, BuiltinBlocks.STONE));
        await(() -> world.getBlock(lintel).equals(BuiltinBlocks.STONE), "the lintel seeded");
        placer.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, py + 1,
                gz + 0.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "the placer returns to the floor");
        hold(placer, new ItemStack(BuiltinItems.VINE, 2, 0));
        useOn(placer, floor, 1);
        BlockPosition hangCell = floor.offset(0, 1, 0);
        await(() -> world.getBlock(hangCell).equals(VineBlocks.stateFor(0)),
                "the UP placement lands the all-clear form under the solid above");
        await(() -> placer.inventory().held().count() == 1,
                "the hanging placement consumed one vine");

        // ------------------------------------------------------------------
        // ARM 4 — the vine cell itself is a placement target
        // ------------------------------------------------------------------
        // The material's REPLACEABLE_PLANT law (canBeReplaced lines
        // 60-62): the side cell carries a DIFFERENT vine state (the NORTH
        // bit, the ticker's own seed); clicking the wall's NORTH face
        // again targets that vine cell — the reference's World.canPlace
        // replaceable half lets the placement REPLACE it, the fresh
        // SOUTH-bit state landing in place of the seeded NORTH-bit form.
        // The pre-fix engine's replaceable set had no vine row: the
        // placement was refused and the seeded state survived.
        server.ticker().submit(() -> world.setBlock(sideCell,
                VineBlocks.stateFor(VineBlocks.BIT_NORTH)));
        await(() -> world.getBlock(sideCell).equals(VineBlocks.stateFor(VineBlocks.BIT_NORTH)),
                "the seeded NORTH-bit vine waits");
        placer.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, py + 1,
                gz + 1.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "the placer returns to the wall");
        hold(placer, new ItemStack(BuiltinItems.VINE, 1, 0));
        useOn(placer, wall, 2);
        await(() -> world.getBlock(sideCell).equals(VineBlocks.stateFor(VineBlocks.BIT_SOUTH)),
                "the vine cell accepted the replace placement (the SOUTH bit over the seeded NORTH)");
        await(() -> placer.inventory().held().count() == 0,
                "the replace placement consumed the last vine");
        server.shutdown(null);
    }
}
