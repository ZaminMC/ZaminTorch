package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * The ground successor generator — the exact vanilla 1.8.8 WalkNodeEvaluator
 * (reference/1.8.8 net/minecraft/entity/ai/pathing/WalkNodeEvaluator.java):
 * cardinal-only successors (vanilla 1.8 paths never step diagonally in the
 * graph — the diagonal look is the body following waypoints), the blocking
 * scan over the entity's floor(width+1) × floor(height+1) box, the step-up
 * rule (the lateral cell blocked AND the head cell free → climb one), the
 * drop rule (walk the free column down against the safe-fall budget) and
 * the blocking-type vocabulary:
 *
 * <ul>
 *   <li>{@code 1} — the box is free (walkable)</li>
 *   <li>{@code 2} — free but touching water (the swimmable flag)</li>
 *   <li>{@code 0} — blocked (a collidable, non-walk-through block in the box)</li>
 *   <li>{@code -1} — water and the mob swims</li>
 *   <li>{@code -2} — lava and the mob is not itself in lava</li>
 *   <li>{@code -3} — the fence family and the off-rail rail rule</li>
 *   <li>{@code -4} — trapdoors (no trapdoor blocks exist in this engine's
 *       registry, so the arm is dormant until one is registered)</li>
 * </ul>
 *
 * <p>The vanilla wooden-door arms ({@code DoorBlock} → 0, pass-through and
 * open-door passes) are documented but unreachable for the same reason — no
 * door blocks are registered; the flags ({@code canPassThroughDoors},
 * {@code canOpenDoors}) are kept so the arms wire up when doors arrive.</p>
 */
public class WalkNodeEvaluator extends NodeEvaluator {
    private boolean canPassThroughDoors;
    private boolean canOpenDoors;
    private boolean canSwim;
    private boolean canFloat;
    private boolean savedCanSwim;

    /** The vanilla Entity.getSafeFallDistance: a hard 3. */
    public static final int SAFE_FALL_DISTANCE = 3;

    @Override
    public void prepare(PathWorld world, MobView entity) {
        super.prepare(world, entity);
        this.savedCanSwim = this.canSwim;
    }

    @Override
    public void done() {
        super.done();
        this.canSwim = this.savedCanSwim;
    }

    @Override
    public PathNode getStart(MobView entity) {
        int i;
        if (this.canFloat && entity.inWater()) {
            i = (int) entity.y();
            int floorX = MathHelperFloor(entity.x());
            int floorZ = MathHelperFloor(entity.z());

            while (PathWorld.materialOrAir(this.world, floorX, i, floorZ) == CellMaterial.WATER) {
                i++;
            }

            this.canSwim = false;
        } else {
            i = MathHelperFloor(entity.y() + 0.5);
        }

        return this.getNode(MathHelperFloor(entity.x() - entity.width() / 2.0F), i,
                MathHelperFloor(entity.z() - entity.width() / 2.0F));
    }

    @Override
    public PathNode getTarget(MobView entity, double x, double y, double z) {
        return this.getNode(MathHelperFloor(x - entity.width() / 2.0F), MathHelperFloor(y),
                MathHelperFloor(z - entity.width() / 2.0F));
    }

    @Override
    public int getNeighbors(PathNode[] path, MobView entity, PathNode node,
                            PathNode target, float range) {
        int i = 0;
        int j = 0;
        if (this.getBlockingType(entity, node.x, node.y + 1, node.z) == 1) {
            j = 1;
        }

        PathNode pathnode = this.getValidNode(entity, node.x, node.y, node.z + 1, j);
        PathNode pathnode1 = this.getValidNode(entity, node.x - 1, node.y, node.z, j);
        PathNode pathnode2 = this.getValidNode(entity, node.x + 1, node.y, node.z, j);
        PathNode pathnode3 = this.getValidNode(entity, node.x, node.y, node.z - 1, j);
        if (pathnode != null && !pathnode.visited && pathnode.distanceTo(target) < range) {
            path[i++] = pathnode;
        }

        if (pathnode1 != null && !pathnode1.visited && pathnode1.distanceTo(target) < range) {
            path[i++] = pathnode1;
        }

        if (pathnode2 != null && !pathnode2.visited && pathnode2.distanceTo(target) < range) {
            path[i++] = pathnode2;
        }

        if (pathnode3 != null && !pathnode3.visited && pathnode3.distanceTo(target) < range) {
            path[i++] = pathnode3;
        }

        return i;
    }

    private PathNode getValidNode(MobView entity, int x, int y, int z, int neighborBlockingType) {
        PathNode pathnode = null;
        int i = this.getBlockingType(entity, x, y, z);
        if (i == 2) {
            return this.getNode(x, y, z);
        }

        if (i == 1) {
            pathnode = this.getNode(x, y, z);
        }

        if (pathnode == null && neighborBlockingType > 0 && i != -3 && i != -4
                && this.getBlockingType(entity, x, y + neighborBlockingType, z) == 1) {
            pathnode = this.getNode(x, y + neighborBlockingType, z);
            y += neighborBlockingType;
        }

        if (pathnode != null) {
            int j = 0;
            int k = 0;

            while (y > 0) {
                k = this.getBlockingType(entity, x, y - 1, z);
                if (this.canSwim && k == -1) {
                    return null;
                }

                if (k != 1) {
                    break;
                }

                if (j++ >= entitySafeFallDistance()) {
                    return null;
                }

                if (--y <= 0) {
                    return null;
                }

                pathnode = this.getNode(x, y, z);
            }

            if (k == -2) {
                return null;
            }
        }

        return pathnode;
    }

    /** The vanilla read: {@code Entity.getSafeFallDistance()} is a hard 3. */
    private static int entitySafeFallDistance() {
        return SAFE_FALL_DISTANCE;
    }

    private int getBlockingType(MobView entity, int x, int y, int z) {
        return getBlockingType(this.world, entity, x, y, z, this.entityWidth,
                this.entityHeight, this.entityDepth, this.canSwim, this.canOpenDoors,
                this.canPassThroughDoors);
    }

    /**
     * The vanilla blocking scan over the entity-sized box at (x, y, z),
     * translated to the engine's {@link CellMaterial} categories. The door
     * and trapdoor arms keep their vanilla comments; no registered block
     * reaches them yet.
     */
    public static int getBlockingType(
        PathWorld world,
        MobView entity,
        int x,
        int y,
        int z,
        int width,
        int height,
        int depth,
        boolean canSwim,
        boolean canOpenDoors,
        boolean canPassThroughDoors
    ) {
        boolean flag = false;
        // The entity's own cell (the vanilla rail anchor check).
        int entityX = MathHelperFloor(entity.x());
        int entityY = MathHelperFloor(entity.y());
        int entityZ = MathHelperFloor(entity.z());
        boolean entityOnRails =
                PathWorld.materialOrAir(world, entityX, entityY, entityZ) == CellMaterial.RAIL
                || PathWorld.materialOrAir(world, entityX, entityY - 1, entityZ) == CellMaterial.RAIL;

        for (int i = x; i < x + width; i++) {
            for (int j = y; j < y + height; j++) {
                for (int k = z; k < z + depth; k++) {
                    CellMaterial material = PathWorld.materialOrAir(world, i, j, k);
                    if (material != CellMaterial.AIR) {
                        if (material == CellMaterial.WATER) {
                            if (canSwim) {
                                return -1;
                            }

                            flag = true;
                        } else if (material == CellMaterial.LAVA) {
                            if (!entity.inLava()) {
                                return -2;
                            }
                        } else if (material == CellMaterial.FENCE) {
                            return -3;
                        } else if (material == CellMaterial.RAIL) {
                            if (!entityOnRails) {
                                return -3;
                            }
                        } else if (material == CellMaterial.SOLID) {
                            // Vanilla: a wooden door blocks unless it can be
                            // passed/opened; no door blocks are registered, so
                            // every remaining collidable blocks outright.
                            return 0;
                        }
                        // WALK_THROUGH: the vanilla canWalkThrough set — the
                        // scan walks past it.
                    }
                }
            }
        }

        return flag ? 2 : 1;
    }

    public void setCanPassThroughDoors(boolean canPassThroughDoors) {
        this.canPassThroughDoors = canPassThroughDoors;
    }

    public void setCanOpenDoors(boolean canOpenDoors) {
        this.canOpenDoors = canOpenDoors;
    }

    public void setCanSwim(boolean canSwim) {
        this.canSwim = canSwim;
    }

    public void setCanFloat(boolean canFloat) {
        this.canFloat = canFloat;
    }

    public boolean canPassThroughDoors() {
        return this.canPassThroughDoors;
    }

    public boolean canFloat() {
        return this.canFloat;
    }

    public boolean canSwim() {
        return this.canSwim;
    }

    /** The decompile's MathHelper.floor. */
    private static int MathHelperFloor(double value) {
        return (int) Math.floor(value);
    }
}
