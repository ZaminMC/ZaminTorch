package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Ported from reference/1.8.8 net/minecraft/server/world/PortalForcer.java
 * (lines 1-351): the destination-side portal walk — the loaded-cell search
 * for an existing nether portal near the arrival point (the 257x257 column
 * scan descending every column to its lowest portal cell, the squared-
 * distance minimum, the per-position cache with its 300-second eviction),
 * the portal's own shape match through the frame builder, the placement
 * math that lands the body inside the found frame (the width/height
 * inverse-lerp offsets, the clockwise-y width shift, the facing-dependent
 * yaw rotation), and the generation arm — the two rotation scans over the
 * 16-radius neighborhood (the 4-rotation full scan, then the 2-rotation
 * height-only fallback), the y-clamped platform fallback, and the 4-wide
 * frame build (obsidian frame, portal interior, the axis carried from the
 * winning rotation).
 *
 * <p>Determinism / engine adaptations (documented):</p>
 * <ul>
 *   <li>The search reads only LOADED cells — the engine's unloaded chunks
 *   answer air (the caller loads the destination neighborhood first; the
 *   reference's getBlockState would load them synchronously).</li>
 *   <li>The reference's explicit {@code updateNeighbors} walk after the
 *   build is the engine's automatic change dispatch (the world's listener
 *   walk notifies neighbors on every commit) — no second walk.</li>
 *   <li>The cache key is the reference's {@code ChunkPos.toLong(floor(x),
 *   floor(z))} — the BLOCK coordinates packed verbatim (the reference
 *   passes block coords into the chunk packer; the cache is therefore
 *   block-granular, kept exactly).</li>
 *   <li>The velocity rotation carries the body's velocity in and out
 *   (the reference rotates velocityX/Z by the facing quarter-turns); the
 *   player walk passes zeros — the player's velocity is client-
 *   authoritative and the engine holds none.</li>
 * </ul>
 */
public final class PortalForcer {

    /** The cache entry: the portal cell remembered and its last use (world time). */
    private record CachedPortal(BlockPosition pos, long lastUseTime) {
    }

    /**
     * The body's portal memory (the reference's Entity
     * {@code lastPortalOffset} / {@code lastPortalFacing}, set by
     * onPortalCollision at the entry): the normalized position inside the
     * entered frame and the facing it was entered on.
     */
    public static final class Memory {
        /** The normalized position along the frame's width (the reference's lastPortalOffset.x). */
        public double offsetAlongWidth;
        /** The normalized position up the frame's height (the reference's lastPortalOffset.y). */
        public double offsetUp;
        /** The entered facing's horizontal id (the reference's getIdHorizontal; SOUTH=0 default). */
        public int lastFacingHorizontalId;
        /** Whether a portal entry set the memory (the first teleport rides the defaults). */
        public boolean known;
    }

    /** The arrival: the landing position and the rotated yaw (+ the rotated velocity). */
    public record Arrival(double x, double y, double z, float yaw,
                          double velocityX, double velocityZ) {
    }

    private final EngineWorld world;
    private final Random random;
    private final int dimensionHeight;
    private final Map<Long, CachedPortal> portalCache = new HashMap<>();
    private final List<Long> portalCacheKeys = new ArrayList<>();

    public PortalForcer(EngineWorld world, long seed, int dimensionHeight) {
        this.world = world;
        this.random = new Random(seed);
        this.dimensionHeight = dimensionHeight;
    }

    /**
     * The reference's {@code onDimensionChanged} (the non-End arm): find the
     * destination portal, generate one when the search misses, find again
     * after the build. @return the arrival.
     */
    public Arrival onDimensionChanged(double x, double y, double z, float yaw,
                                      double velocityX, double velocityZ,
                                      Memory memory) {
        Optional<Arrival> found = findNetherPortal(x, y, z, yaw, velocityX, velocityZ, memory);
        if (found.isEmpty()) {
            generateNetherPortal(x, y, z);
            found = findNetherPortal(x, y, z, yaw, velocityX, velocityZ, memory);
        }
        // The search over a freshly built frame always lands (the build
        // writes the portal cells the scan reads).
        return found.orElse(new Arrival(x, y, z, yaw, velocityX, velocityZ));
    }

    /**
     * The reference's {@code findNetherPortal}: the cache first, then the
     * 257x257 column scan (every column descends from the dimension top to
     * its lowest portal cell; the squared-distance minimum wins), then the
     * placement math over the found frame's shape match.
     */
    public Optional<Arrival> findNetherPortal(double x, double y, double z,
                                              float yaw, double velocityX, double velocityZ,
                                              Memory memory) {
        double best = -1.0;
        int originX = floor(x);
        int originZ = floor(z);
        int originY = floor(y);
        boolean fresh = true;
        BlockPosition found = null;
        // The reference's ChunkPos.toLong(j, k) with the BLOCK coordinates —
        // block-granular cache, kept verbatim.
        long cacheKey = ((long) (originX & 0xFFFFFFFFL) << 32) | (originZ & 0xFFFFFFFFL);
        CachedPortal cached = portalCache.get(cacheKey);
        if (cached != null) {
            best = 0.0;
            found = cached.pos();
            portalCache.put(cacheKey, new CachedPortal(cached.pos(), world.totalTicks()));
            fresh = false;
        } else {
            for (int dx = -128; dx <= 128; dx++) {
                for (int dz = -128; dz <= 128; dz++) {
                    // The reference's blockpos3.add(i1, height-1-y, j1): the
                    // column cursor starts at the dimension top. The walk
                    // stops at y=0 — the engine's BlockPosition rejects the
                    // void below, and the reference's own reads answer the
                    // same non-air void there (a portal never sits in it).
                    BlockPosition cursor = new BlockPosition(
                            originX + dx, dimensionHeight - 1, originZ + dz);
                    while (cursor.y() >= 0) {
                        if (WorldSolidity.isNetherPortal(world.getBlock(cursor).identifier())) {
                            while (cursor.y() > 0 && WorldSolidity.isNetherPortal(
                                    world.getBlock(cursor.offset(0, -1, 0)).identifier())) {
                                cursor = cursor.offset(0, -1, 0);
                            }
                            double distance = squaredDistance(cursor, originX, originY, originZ);
                            if (best < 0.0 || distance < best) {
                                best = distance;
                                found = cursor;
                            }
                        }
                        if (cursor.y() == 0) {
                            break;
                        }
                        cursor = cursor.offset(0, -1, 0);
                    }
                }
            }
        }

        if (best >= 0.0 && found != null) {
            if (fresh) {
                portalCache.put(cacheKey, new CachedPortal(found, world.totalTicks()));
                portalCacheKeys.add(cacheKey);
            }
            PortalFrameBuilder.PortalShapeMatch match = PortalFrameBuilder.matchAt(world, found);

            // The placement math (the reference's d5/d6/d7 walk over the
            // match's geometry and the body's portal memory).
            double destX = match.topLeftFront().x() + 0.5;
            double destY = match.topLeftFront().y() + 0.5;
            double destZ = match.topLeftFront().z() + 0.5;
            // The width-axis origin coordinate (the topLeft's own width-axis
            // coordinate), the up offset riding the memory's height share.
            double widthOrigin = match.forwardAxis() == PortalFrameBuilder.Axis.X
                    ? match.topLeftFront().z()
                    : match.topLeftFront().x();
            destY = match.topLeftFront().y() + 1 - memory.offsetUp * match.height();
            if (!match.forwardPositive()) {
                widthOrigin++;
            }
            // The width shift: the body's share of the frame's width, riding
            // the clockwise-y axis direction's sign.
            double widthShift = (1.0 - memory.offsetAlongWidth) * match.width()
                    * clockwiseYOffsetSign(match.forwardAxis(), match.forwardPositive());
            if (match.forwardAxis() == PortalFrameBuilder.Axis.X) {
                destZ = widthOrigin + widthShift;
            } else {
                destX = widthOrigin + widthShift;
            }
            // The yaw: the entered facing's quarter-turns traded for the
            // found frame's.
            float destYaw = yaw - memory.lastFacingHorizontalId * 90.0f
                    + match.forwardHorizontalId() * 90.0f;
            // The velocity quarter-turn (the reference's f/f1/f2/f3 matrix:
            // identity when forward.opposite == the entered facing; both
            // negated when forward == it; (-vz, vx) when forward.opposite
            // == the facing's clockwise-y; (vz, -vx) otherwise).
            int lastId = memory.lastFacingHorizontalId & 3;
            int newId = match.forwardHorizontalId() & 3;
            double outVelocityX = velocityX;
            double outVelocityZ = velocityZ;
            if (((newId + 2) & 3) == lastId) {
                // forward.opposite == lastPortalFacing: identity.
            } else if (newId == lastId) {
                outVelocityX = -velocityX;
                outVelocityZ = -velocityZ;
            } else if (((newId + 2) & 3) == ((lastId + 1) & 3)) {
                outVelocityX = -velocityZ;
                outVelocityZ = velocityX;
            } else {
                outVelocityX = velocityZ;
                outVelocityZ = -velocityX;
            }
            return Optional.of(new Arrival(destX, destY, destZ, destYaw, outVelocityX, outVelocityZ));
        }
        return Optional.empty();
    }

    /**
     * The reference's {@code generateNetherPortal}: the 16-radius two-scan
     * placement hunt (the 4-rotation full scan first, the 2-rotation
     * height-only fallback), the y-clamped platform fallback, and the
     * 4-wide frame build riding the winning rotation.
     */
    public boolean generateNetherPortal(double x, double y, double z) {
        int radius = 16;
        double best = -1.0;
        int originX = floor(x);
        int originY = floor(y);
        int originZ = floor(z);
        int winX = originX;
        int winY = originY;
        int winZ = originZ;
        int winRotation = 0;
        int firstRotation = this.random.nextInt(4);

        // The full scan: 4 rotations (the reference's k3 loop over i2..i2+4).
        // The reference's label296 sits ON the scanY loop — a failed rotation
        // moves to the column's NEXT y, never skipping the rest of the row.
        for (int scanX = originX - radius; scanX <= originX + radius; scanX++) {
            double dx = scanX + 0.5 - x;
            for (int scanZ = originZ - radius; scanZ <= originZ + radius; scanZ++) {
                double dz = scanZ + 0.5 - z;
                columnFull:
                for (int scanY = dimensionHeight - 1; scanY >= 0; scanY--) {
                    if (isAirAt(scanX, scanY, scanZ)) {
                        while (scanY > 0 && isAirAt(scanX, scanY - 1, scanZ)) {
                            scanY--;
                        }
                        for (int rotation = firstRotation; rotation < firstRotation + 4; rotation++) {
                            int dxRot = rotation % 2;
                            int dzRot = 1 - dxRot;
                            if (rotation % 4 >= 2) {
                                dxRot = -dxRot;
                                dzRot = -dzRot;
                            }
                            for (int w = 0; w < 3; w++) {
                                for (int frame = 0; frame < 4; frame++) {
                                    for (int h = -1; h < 4; h++) {
                                        int cellX = scanX + (frame - 1) * dxRot + w * dzRot;
                                        int cellY = scanY + h;
                                        int cellZ = scanZ + (frame - 1) * dzRot - w * dxRot;
                                        if (h < 0 && !isSolidAt(cellX, cellY, cellZ)
                                                || h >= 0 && !isAirAt(cellX, cellY, cellZ)) {
                                            continue columnFull;
                                        }
                                    }
                                }
                            }
                            double dy = scanY + 0.5 - y;
                            double distance = dx * dx + dy * dy + dz * dz;
                            if (best < 0.0 || distance < best) {
                                best = distance;
                                winX = scanX;
                                winY = scanY;
                                winZ = scanZ;
                                winRotation = rotation % 4;
                            }
                        }
                    }
                }
            }
        }

        // The fallback scan: 2 rotations (height-only variants). The same
        // column-level label — a failed rotation tries the column's next y.
        if (best < 0.0) {
            for (int scanX = originX - radius; scanX <= originX + radius; scanX++) {
                double dx = scanX + 0.5 - x;
                for (int scanZ = originZ - radius; scanZ <= originZ + radius; scanZ++) {
                    double dz = scanZ + 0.5 - z;
                    columnFallback:
                    for (int scanY = dimensionHeight - 1; scanY >= 0; scanY--) {
                        if (isAirAt(scanX, scanY, scanZ)) {
                            while (scanY > 0 && isAirAt(scanX, scanY - 1, scanZ)) {
                                scanY--;
                            }
                            for (int rotation = firstRotation; rotation < firstRotation + 2; rotation++) {
                                int dxRot = rotation % 2;
                                int dzRot = 1 - dxRot;
                                for (int w = 0; w < 4; w++) {
                                    for (int h = -1; h < 4; h++) {
                                        int cellX = scanX + (w - 1) * dxRot;
                                        int cellY = scanY + h;
                                        int cellZ = scanZ + (w - 1) * dzRot;
                                        if (h < 0 && !isSolidAt(cellX, cellY, cellZ)
                                                || h >= 0 && !isAirAt(cellX, cellY, cellZ)) {
                                            continue columnFallback;
                                        }
                                    }
                                }
                                double dy = scanY + 0.5 - y;
                                double distance = dx * dx + dy * dy + dz * dz;
                                if (best < 0.0 || distance < best) {
                                    best = distance;
                                    winX = scanX;
                                    winY = scanY;
                                    winZ = scanZ;
                                    winRotation = rotation % 2;
                                }
                            }
                        }
                    }
                }
            }
        }

        // The rotation's unit vectors (the reference's i7/i3 pair).
        int dxRot = winRotation % 2;
        int dzRot = 1 - dxRot;
        if (winRotation % 4 >= 2) {
            dxRot = -dxRot;
            dzRot = -dzRot;
        }

        // The platform fallback: nothing fit anywhere — the y clamps into
        // the 70..height-10 band and a flat obsidian floor builds.
        if (best < 0.0) {
            winY = clamp(winY, 70, dimensionHeight - 10);
            for (int k = -1; k <= 1; k++) {
                for (int w = 1; w < 3; w++) {
                    for (int h = -1; h < 3; h++) {
                        int cellX = winX + (w - 1) * dxRot + k * dzRot;
                        int cellY = winY + h;
                        int cellZ = winZ + (w - 1) * dzRot - k * dxRot;
                        world.setBlock(new BlockPosition(cellX, cellY, cellZ),
                                h < 0 ? BuiltinBlocks.OBSIDIAN : world.airType());
                    }
                }
            }
        }

        // The frame build (the reference's j8/i9/i10 walk): 4 wide × 5 tall,
        // ONE cell deep — obsidian at the width edges (w==0/3) and the
        // height edges (h==-1/3), the 2x3 interior fills with the rotation's
        // portal cells; the reference's outer 4-loop rewrites the same
        // rectangle four times (idempotent — kept verbatim, the engine's
        // no-change writes dispatch nothing).
        BlockType portalCell = dxRot != 0
                ? BuiltinBlocks.NETHER_PORTAL
                : BuiltinBlocks.NETHER_PORTAL_Z;
        for (int repeat = 0; repeat < 4; repeat++) {
            for (int w = 0; w < 4; w++) {
                for (int h = -1; h < 4; h++) {
                    int cellX = winX + (w - 1) * dxRot;
                    int cellY = winY + h;
                    int cellZ = winZ + (w - 1) * dzRot;
                    boolean frame = w == 0 || w == 3 || h == -1 || h == 3;
                    world.setBlock(new BlockPosition(cellX, cellY, cellZ),
                            frame ? BuiltinBlocks.OBSIDIAN : portalCell);
                }
            }
            // The neighbor walk: the reference's updateNeighbors pass — the
            // engine's change dispatch already notified every neighbor on
            // each setBlock commit, so no second walk runs.
        }
        return true;
    }

    /**
     * The reference's {@code tick}: every 100 time units the cache evicts
     * the entries unused for 300.
     */
    public void tick(long time) {
        if (time % 100L == 0L) {
            long stale = time - 300L;
            portalCacheKeys.removeIf(key -> {
                CachedPortal entry = portalCache.get(key);
                if (entry == null || entry.lastUseTime() < stale) {
                    portalCache.remove(key);
                    return true;
                }
                return false;
            });
        }
    }

    /** The clockwise-y axis-direction's sign (the reference's clockwiseY().getAxisDirection().getOffset()). */
    private static int clockwiseYOffsetSign(PortalFrameBuilder.Axis forwardAxis, boolean forwardPositive) {
        // clockwiseY: NORTH->EAST->SOUTH->WEST->NORTH.
        if (forwardAxis == PortalFrameBuilder.Axis.X) {
            // EAST (+X) -> SOUTH (+Z); WEST (-X) -> NORTH (-Z).
            return forwardPositive ? 1 : -1;
        }
        // SOUTH (+Z) -> WEST (-X); NORTH (-Z) -> EAST (+X).
        return forwardPositive ? -1 : 1;
    }

    /**
     * The void-clamped read (the engine's BlockPosition rejects the y the
     * reference's world walk answers itself): out-of-height cells read as
     * the void — not air, not solid — which is exactly how the reference's
     * own out-of-height getBlockState behaves.
     */
    private BlockType blockOrVoid(int x, int y, int z) {
        if (y < 0 || y >= dimensionHeight) {
            return null;
        }
        return world.getBlock(new BlockPosition(x, y, z));
    }

    private boolean isAirAt(int x, int y, int z) {
        BlockType block = blockOrVoid(x, y, z);
        return block != null && block.equals(world.airType());
    }

    private boolean isSolidAt(int x, int y, int z) {
        BlockType block = blockOrVoid(x, y, z);
        return block != null && WorldSolidity.isSolid(block);
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    private static double squaredDistance(BlockPosition position, int x, int y, int z) {
        double dx = position.x() - x;
        double dy = position.y() - y;
        double dz = position.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
