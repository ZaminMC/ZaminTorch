package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * Ground navigation — the vanilla 1.8.8 GroundPathNavigation + PathNavigation
 * (reference/1.8.8 net/minecraft/entity/ai/pathing/) adapted to the engine's
 * body: the exact waypoint advancement (the width²·offset squared band over
 * the same-level prefix, then the direct-walk shortcut), the exact stuck
 * check (100 ticks without covering 1.5 blocks stops the route), the
 * airborne y-skip, and the ray-walk shortcut ({@code canMoveDirectly}) that
 * lets the mob skip waypoints when a straight line is walkable.
 *
 * <p>Adaptations, documented: the vanilla movement target's y comes from a
 * collision-box scan ({@code world.getCollisions} + {@code intersectY})
 * feeding the MovementControl jump rule — this engine's body steps up
 * through the shared ground model, so the raw waypoint is the movement
 * target and its y is informational. The vanilla chunk-region view
 * ({@code WorldRegion} at followRange + 16) is unnecessary here — the
 * engine's {@link PathWorld} answers bounds-safe for the whole world. The
 * zombie-on-chicken {@code canUpdatePath} arm and the sun-avoidance
 * {@code trimPath} belong to goals the engine has not ported yet.</p>
 */
public class MobNavigation {
    protected final MobView mob;
    protected final PathWorld world;
    protected Path path;
    protected double speed;
    private final float followRange;
    private int ticks;
    private int lastStuckCheckTime;
    private double lastStuckCheckX;
    private double lastStuckCheckY;
    private double lastStuckCheckZ;
    private float offset = 1.0F;
    private final PathFinder pathFinder;
    private final WalkNodeEvaluator nodeEvaluator;

    public MobNavigation(MobView mob, PathWorld world, float followRange) {
        this.mob = mob;
        this.world = world;
        this.followRange = followRange;
        this.nodeEvaluator = this.createNodeEvaluator();
        this.pathFinder = new PathFinder(this.nodeEvaluator);
    }

    /** The vanilla GroundPathNavigation factory: a walk evaluator. */
    protected WalkNodeEvaluator createNodeEvaluator() {
        WalkNodeEvaluator evaluator = new WalkNodeEvaluator();
        evaluator.setCanPassThroughDoors(true);
        return evaluator;
    }

    public Path findPath(double x, double y, double z) {
        return this.pathFinder.findPath(this.world, this.mob, x, y, z, this.followRange);
    }

    public Path findPath(MobView target) {
        return this.pathFinder.findPath(this.world, this.mob, target, this.followRange);
    }

    /**
     * The vanilla BlockPos overload: the goal cell is floored, then centered
     * (+0.5 on every axis) — the search targets the cell's center, exactly
     * {@code findPath(new BlockPos(floor x, (int) y, floor z))}.
     */
    public boolean moveTo(double x, double y, double z, double speed) {
        Path path = this.findPath(Math.floor(x) + 0.5, (int) y + 0.5, Math.floor(z) + 0.5);
        return this.moveAlong(path, speed);
    }

    public boolean moveAlong(Path path, double speed) {
        if (path == null) {
            this.path = null;
            return false;
        }

        // The vanilla reset rule: a route with the SAME node sequence keeps
        // its walking index — a repath does not rewind the mob.
        if (!path.equals(this.path)) {
            this.path = path;
        }

        if (this.path.length() == 0) {
            return false;
        }

        this.speed = speed;
        double[] vec3d = this.getTempPos();
        this.lastStuckCheckTime = this.ticks;
        this.lastStuckCheckX = vec3d[0];
        this.lastStuckCheckY = vec3d[1];
        this.lastStuckCheckZ = vec3d[2];
        return true;
    }

    public Path getPath() {
        return this.path;
    }

    public double getSpeed() {
        return this.speed;
    }

    public void tick() {
        this.ticks++;
        this.hasTarget = false;
        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.updatePath();
            } else if (this.path != null && this.path.getCurrentIndex() < this.path.length()) {
                double[] vec3d = this.getTempPos();
                double[] vec3d1 = this.path.getPos(this.mob.width(), this.path.getCurrentIndex());
                if (vec3d[1] > vec3d1[1]
                        && !this.mob.onGround()
                        && Math.floor(vec3d[0]) == Math.floor(vec3d1[0])
                        && Math.floor(vec3d[2]) == Math.floor(vec3d1[2])) {
                    this.path.setCurrentIndex(this.path.getCurrentIndex() + 1);
                }
            }

            if (!this.isDone()) {
                double[] current = this.path.getCurrentPos(this.mob.width());
                this.currentTargetX = current[0];
                this.currentTargetY = current[1];
                this.currentTargetZ = current[2];
                this.hasTarget = true;
            }
        }
    }

    /** The movement target the last {@code tick} produced (informational y). */
    public double currentTargetX;
    public double currentTargetY;
    public double currentTargetZ;
    public boolean hasTarget;

    protected void updatePath() {
        double[] vec3d = this.getTempPos();
        int i = this.path.length();

        for (int j = this.path.getCurrentIndex(); j < this.path.length(); j++) {
            if (this.path.getNode(j).y != (int) vec3d[1]) {
                i = j;
                break;
            }
        }

        float f = (float) (this.mob.width() * this.mob.width() * this.offset);

        for (int k = this.path.getCurrentIndex(); k < i; k++) {
            double[] vec3d1 = this.path.getPos(this.mob.width(), k);
            double dx = vec3d[0] - vec3d1[0];
            double dy = vec3d[1] - vec3d1[1];
            double dz = vec3d[2] - vec3d1[2];
            if (dx * dx + dy * dy + dz * dz < f) {
                this.path.setCurrentIndex(k + 1);
            }
        }

        int j1 = (int) Math.ceil(this.mob.width());
        int k1 = (int) this.mob.height() + 1;
        int l = j1;

        for (int i1 = i - 1; i1 >= this.path.getCurrentIndex(); i1--) {
            if (this.canMoveDirectly(vec3d[0], vec3d[1], vec3d[2],
                    this.path.getPos(this.mob.width(), i1), j1, k1, l)) {
                this.path.setCurrentIndex(i1);
                break;
            }
        }

        this.doStuckCheck(vec3d);
    }

    protected void doStuckCheck(double[] pos) {
        if (this.ticks - this.lastStuckCheckTime > 100) {
            double dx = pos[0] - this.lastStuckCheckX;
            double dy = pos[1] - this.lastStuckCheckY;
            double dz = pos[2] - this.lastStuckCheckZ;
            if (dx * dx + dy * dy + dz * dz < 2.25) {
                this.stop();
            }

            this.lastStuckCheckTime = this.ticks;
            this.lastStuckCheckX = pos[0];
            this.lastStuckCheckY = pos[1];
            this.lastStuckCheckZ = pos[2];
        }
    }

    public boolean isDone() {
        return this.path == null || this.path.isDone();
    }

    public void stop() {
        this.path = null;
        this.hasTarget = false;
    }

    /** The vanilla temp pos: the body's X/Z and the feet-cell surface. */
    protected double[] getTempPos() {
        return new double[]{this.mob.x(), this.getSurfaceY(), this.mob.z()};
    }

    private int getSurfaceY() {
        if (this.mob.inWater() && this.canFloat()) {
            int i = (int) this.mob.y();
            int j = 0;
            while (PathWorld.materialOrAir(this.world,
                    (int) Math.floor(this.mob.x()), i, (int) Math.floor(this.mob.z()))
                    == CellMaterial.WATER) {
                i++;
                if (++j > 16) {
                    return (int) this.mob.y();
                }
            }
            return i;
        } else {
            return (int) (this.mob.y() + 0.5);
        }
    }

    protected boolean canUpdatePath() {
        return this.mob.onGround()
                || this.canFloat() && (this.mob.inWater() || this.mob.inLava());
    }

    protected boolean canFloat() {
        return this.nodeEvaluator.canFloat();
    }

    /**
     * The vanilla ray-walk shortcut: whether the straight segment to the
     * point crosses only walkable ground at the from-level (the DDA over
     * the crossed cell columns, the behind-check included).
     */
    protected boolean canMoveDirectly(double fromX, double fromY, double fromZ,
                                      double[] to, int width, int height, int depth) {
        int i = (int) Math.floor(fromX);
        int j = (int) Math.floor(fromZ);
        double d0 = to[0] - fromX;
        double d1 = to[2] - fromZ;
        double d2 = d0 * d0 + d1 * d1;
        if (d2 < 1.0E-8) {
            return false;
        }

        double d3 = 1.0 / Math.sqrt(d2);
        d0 *= d3;
        d1 *= d3;
        width += 2;
        depth += 2;
        if (!this.canWalkOn(i, (int) fromY, j, width, height, depth, fromX, fromZ, d0, d1)) {
            return false;
        }

        width -= 2;
        depth -= 2;
        double d4 = 1.0 / Math.abs(d0);
        double d5 = 1.0 / Math.abs(d1);
        double d6 = i * 1 - fromX;
        double d7 = j * 1 - fromZ;
        if (d0 >= 0.0) {
            d6++;
        }

        if (d1 >= 0.0) {
            d7++;
        }

        d6 /= d0;
        d7 /= d1;
        int k = d0 < 0.0 ? -1 : 1;
        int l = d1 < 0.0 ? -1 : 1;
        int i1 = (int) Math.floor(to[0]);
        int j1 = (int) Math.floor(to[2]);
        int k1 = i1 - i;
        int l1 = j1 - j;

        while (k1 * k > 0 || l1 * l > 0) {
            if (d6 < d7) {
                d6 += d4;
                i += k;
                k1 = i1 - i;
            } else {
                d7 += d5;
                j += l;
                l1 = j1 - j;
            }

            if (!this.canWalkOn(i, (int) fromY, j, width, height, depth, fromX, fromZ, d0, d1)) {
                return false;
            }
        }

        return true;
    }

    private boolean canWalkOn(int x, int y, int z, int width, int height, int depth,
                              double posX, double posZ, double dirX, double dirZ) {
        int i = x - width / 2;
        int j = z - depth / 2;
        if (!this.canWalkAbove(i, y, j, width, height, depth, posX, posZ, dirX, dirZ)) {
            return false;
        }

        for (int k = i; k < i + width; k++) {
            for (int l = j; l < j + depth; l++) {
                double d0 = k + 0.5 - posX;
                double d1 = l + 0.5 - posZ;
                if (!(d0 * dirX + d1 * dirZ < 0.0)) {
                    CellMaterial material = PathWorld.materialOrAir(this.world, k, y - 1, l);
                    if (material == CellMaterial.AIR) {
                        return false;
                    }

                    if (material == CellMaterial.WATER && !this.mob.inWater()) {
                        return false;
                    }

                    if (material == CellMaterial.LAVA) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean canWalkAbove(int x, int y, int z, int width, int height, int depth,
                                 double posX, double posZ, double dirX, double dirZ) {
        for (int i = x; i < x + width; i++) {
            for (int j = y; j < y + height; j++) {
                for (int k = z; k < z + depth; k++) {
                    double d0 = i + 0.5 - posX;
                    double d1 = k + 0.5 - posZ;
                    if (!(d0 * dirX + d1 * dirZ < 0.0)) {
                        CellMaterial material = PathWorld.materialOrAir(this.world, i, j, k);
                        // The vanilla !canWalkThrough set: collidables and
                        // the fence family stop the straight walk.
                        if (material == CellMaterial.SOLID || material == CellMaterial.FENCE) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }
}
