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
     *
     * <p>WAVE KO-F — THE CHUNK-BANDED WALK (Paper 0060 part 2's engine
     * mapping): the cell band is walked CHUNK BY CHUNK (the chunkX band
     * outer, the chunkZ band inner, each chunk's own x/z sub-band
     * inside), the chunk's {@link EntityCollisionWorld#chunkReader(
     * int, int) reader} fetched ONCE per chunk — the per-cell world
     * lookup ({@code new BlockPosition} + {@code chunkPosition()} + the
     * concurrent-map {@code get}) retired for every reader-carrying
     * surface. THE CELL ORDER IS IDENTICAL to the flat walk by
     * construction (the chunk bands are monotonic in x and z, the
     * sub-bands clamped to the region's own bounds), so the box list —
     * and every clip the solver runs over it — answers EXACTLY as
     * before (the equivalence seat pins this over randomized scenes
     * against a verbatim copy of the pre-KO-F flat walk).</p>
     *
     * <p>The fallback arm: a null reader means the chunk is not loaded
     * OR the surface carries no fast face — the per-column
     * {@link EntityCollisionWorld#chunkLoaded(int, int)} gate consults
     * once per chunk (all sixteen of its columns share the one chunk),
     * and a loaded-but-readerless chunk reads its cells through the
     * per-cell {@code blockAt} path unchanged (the stub worlds' own
     * shape, byte-identical to the pre-KO-F walk).</p>
     */
    public static void appendBlockBoxes(EntityCollisionWorld world, Aabb region,
            boolean moverInsideBorder, List<Aabb> out) {
        int minX = (int) Math.floor(region.minX);
        int maxX = (int) Math.floor(region.maxX + 1.0);
        int minY = (int) Math.floor(region.minY);
        int maxY = (int) Math.floor(region.maxY + 1.0);
        int minZ = (int) Math.floor(region.minZ);
        int maxZ = (int) Math.floor(region.maxZ + 1.0);
        // WAVE KO-F: the chunk bands of the region's own cell band
        // (maxX/maxZ are exclusive bounds — an empty band skips whole).
        if (minX >= maxX || minZ >= maxZ) {
            return;
        }
        int minChunkX = minX >> 4;
        int maxChunkX = (maxX - 1) >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = (maxZ - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            // The chunk's own x sub-band, clamped to the region's band.
            int xStart = Math.max(minX, chunkX << 4);
            int xEnd = Math.min(maxX, (chunkX << 4) + 16);
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int zStart = Math.max(minZ, chunkZ << 4);
                int zEnd = Math.min(maxZ, (chunkZ << 4) + 16);
                // WAVE KO-F: ONE fetch per chunk — the reader IS the
                // loaded gate (a present reader is a loaded chunk; null
                // is an unloaded chunk or a readerless surface, the
                // fallback arm below).
                EntityCollisionWorld.ChunkReader reader =
                        world.chunkReader(chunkX, chunkZ);
                boolean loadedFallback = reader == null
                        && world.chunkLoaded(xStart, zStart);
                if (reader == null && !loadedFallback) {
                    continue; // the unloaded chunk contributes nothing
                }
                for (int x = xStart; x < xEnd; x++) {
                    for (int z = zStart; z < zEnd; z++) {
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
                                type = reader != null
                                        ? reader.blockAt(x, y, z)
                                        : world.blockAt(x, y, z);
                            } else {
                                type = net.zaminmc.torch.server.block.BuiltinBlocks.STONE;
                            }
                            appendCellBoxes(world, type, x, y, z, region, out);
                        }
                    }
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
