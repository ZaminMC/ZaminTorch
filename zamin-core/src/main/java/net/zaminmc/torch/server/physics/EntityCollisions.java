package net.zaminmc.torch.server.physics;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.CollisionShapes;
import net.zaminmc.torch.server.block.CollisionShapes.Box;
import net.zaminmc.torch.server.block.CollisionShapes.NeighborProbe;

import java.util.ArrayList;
import java.util.List;

/**
 * The block-side collision gather (the physics-solver slice): the 1:1
 * port of the reference's {@code World.getCollisions(Entity, Box)}
 * lines 897-940 — the box list a body's swept shape clips against in
 * {@code Entity.move}.
 *
 * <p>The walk, exactly the reference's own:</p>
 * <ol>
 * <li>the cell band: {@code floor(minX) .. floor(maxX + 1)} on X,
 * {@code floor(minY) .. floor(maxY + 1)} on Y <b>starting one cell
 * below</b> ({@code k - 1}: the 1.5-tall families poke through their
 * ceiling, so a cell below the region can still reach into it), and
 * the Z band like X;</li>
 * <li>the chunk gate: an unloaded column contributes nothing — a body
 * never collides with unloaded terrain;</li>
 * <li>the border consult: a cell outside the world border reads as
 * STONE for a body inside the border (the border is a wall) and reads
 * real for a body already outside (the reference's own
 * flag/flag1 law);</li>
 * <li>per cell, the block's own {@code addCollisions}: the shape
 * table's boxes, offset into world space, added <b>only when the box
 * intersects the swept region</b> — the reference's own
 * {@code shape.intersects(box)} gate.</li>
 * </ol>
 *
 * <p>The connection families (the fences, the walls, the panes, the
 * closed gates, the stairs' own corner resolution) resolve their arms
 * against the cell's neighbours through the same
 * {@link CollisionShapes#cellBoxes} surface the point queries use, so
 * the swept-box model and the point model read one law.</p>
 */
public final class EntityCollisions {

    private EntityCollisions() {
    }

    /**
     * Gathers every block collision box intersecting the region, in
     * world coordinates, in the reference's own walk order (the X
     * columns outer, the Z rows inner, the Y column innermost).
     *
     * @param world  the block-read surface (dimension context owned by
     *               the caller)
     * @param region the swept shape (a body's own box expanded over its
     *               motion)
     * @param moverInsideBorder whether the MOVING body is inside the
     *               world border (the flag1 consult: cells outside read
     *               as STONE only for a body inside)
     * @return the intersecting boxes, world-space, in walk order
     */
    public static List<Aabb> gatherBlockBoxes(EntityCollisionWorld world, Aabb region,
            boolean moverInsideBorder) {
        List<Aabb> out = new ArrayList<>();
        appendBlockBoxes(world, region, moverInsideBorder, out);
        return out;
    }

    /**
     * The appending form (the reference's own list-passing shape): the
     * solver reuses one list across its step retries.
     */
    public static void appendBlockBoxes(EntityCollisionWorld world, Aabb region,
            boolean moverInsideBorder, List<Aabb> out) {
        int minX = (int) Math.floor(region.minX);
        int maxX = (int) Math.floor(region.maxX + 1.0);
        int minY = (int) Math.floor(region.minY);
        int maxY = (int) Math.floor(region.maxY + 1.0);
        int minZ = (int) Math.floor(region.minZ);
        int maxZ = (int) Math.floor(region.maxZ + 1.0);
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                if (!world.chunkLoaded(x, z)) {
                    continue;
                }
                // The reference's own k - 1 start: the tall families
                // reach through their cell's ceiling, so the cell below
                // the region still contributes.
                for (int y = minY - 1; y < maxY; y++) {
                    if (y < BlockPosition.MIN_Y || y > BlockPosition.MAX_Y) {
                        continue;
                    }
                    // The border law: the real block when the cell lies
                    // inside the border OR the body itself is outside;
                    // STONE (the wall — the reference's own STONE
                    // default state stand-in) otherwise.
                    BlockType type;
                    if (world.borderContains(x, z) || !moverInsideBorder) {
                        type = world.blockAt(x, y, z);
                    } else {
                        type = net.zaminmc.torch.server.block.BuiltinBlocks.STONE;
                    }
                    appendCellBoxes(world, type, x, y, z, region, out);
                }
            }
        }
    }

    /**
     * One cell's own {@code addCollisions} (the reference's
     * {@code Block.addCollisions} lines 409-414): each of the type's
     * shape-table boxes, offset into world space, added only when it
     * intersects the region.
     */
    private static void appendCellBoxes(EntityCollisionWorld world, BlockType type,
            int x, int y, int z, Aabb region, List<Aabb> out) {
        if (type == null) {
            return;
        }
        NeighborProbe probe = cellProbe(world, x, y, z);
        for (Box box : CollisionShapes.cellBoxes(type, probe)) {
            Aabb worldBox = new Aabb(
                    x + box.minX(), y + box.minY(), z + box.minZ(),
                    x + box.maxX(), y + box.maxY(), z + box.maxZ());
            if (region.intersects(worldBox)) {
                out.add(worldBox);
            }
        }
    }

    /**
     * The cell's neighbour probe — the same dimension-aware read the
     * point queries resolve through, bound to this cell.
     */
    private static NeighborProbe cellProbe(EntityCollisionWorld world, int x, int y, int z) {
        return (dx, dy, dz) -> {
            int ny = y + dy;
            if (ny < BlockPosition.MIN_Y || ny > BlockPosition.MAX_Y) {
                return null;
            }
            int nx = x + dx;
            int nz = z + dz;
            if (!world.chunkLoaded(nx, nz)) {
                return null;
            }
            return world.blockAt(nx, ny, nz);
        };
    }
}
