package net.zaminmc.torch.server.tnt;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.redstone.RedstoneSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The TNT subsystem (Slice 9f) — the reference port of the 1.8.8 TNT:
 * block/TntBlock.java (the redstone ignition through the neighbor signal,
 * the flint-and-steel use, the exploded-by-blast short fuse) and entity/
 * PrimedTntEntity.java (the 80-tick fuse, the small physics hop, the
 * 4.0F explosion).
 *
 * <h2>The ignition arms (TntBlock lines 36-56, 66-80, 96-113)</h2>
 *
 * <ul>
 *   <li><b>Redstone</b>: a TNT that arrives into — or hears — a neighbor
 *       signal ignites: the block leaves the world and the primed body
 *       takes over (the reference's onAdded + neighborChanged arms).</li>
 *   <li><b>Flint and steel / fire charge</b>: the use on the TNT block
 *       ignites it (the engine's flint use path carries this arm).</li>
 *   <li><b>Another blast</b>: an explosion that consumes the block primes
 *       a short-fuse body (next(fuse/4) + fuse/8 — the chain-reaction
 *       stagger).</li>
 * </ul>
 *
 * <h2>The primed body (PrimedTntEntity lines 29-73)</h2>
 *
 * The spawn: the center of the block, the small random horizontal hop
 * (0.02) and the 0.2 rise. The physics: gravity -0.04 per tick, the 0.98
 * drag, the ground friction (0.7 horizontal, the -0.5 bounce). The fuse:
 * 80 ticks, then the 4.0F explosion at the body's own position (the
 * reference's {@code y + height/16} = +0.06125).
 *
 * <h2>Engine adaptations, all ledgered</h2>
 *
 * <ul>
 *   <li>The explosion walk is the reference's own two-phase law
 *       (ExplosionService + the detonation seat): the boundary ray fan,
 *       the collect-then-destroy order, the d10 density attenuation, and
 *       the int-truncated quadratic wound form — the retired
 *       creeper-walk parameterization is gone.</li>
 *   <li>The burning-arrow collision arm (TntBlock.onEntityCollision) rides
 *       the projectile manager's stick-arm consult — the arrow STICKS into
 *       the TNT's cell first, the block primes, and the stuck arrow
 *       un-sticks a tick later when the consult's removal opens the cell
 *       (VT-030's order: ArrowEntity line 304 precedes the consult at
 *       308). The
 *       reference's igniter attribution (the arrow's shooter) is the
 *       engine's own standing simplification: the primed bodies carry no
 *       igniter.</li>
 *   <li>The smoke trail is NOT owed on the wire: the reference's
 *       addParticle rides the server listener's EMPTY override
 *       (ServerWorldEventListener lines 23-29) — the client renders its
 *       own trail from the Spawn Entity packet. No server packet exists.</li>
 * </ul>
 */
public final class TntSystem implements WorldChangeListener {

    /** The vanilla fuse (PrimedTntEntity line 36). */
    static final int FUSE_TICKS = 80;
    /** The vanilla explosion power (PrimedTntEntity.explode). */
    static final float POWER = 4.0f;

    /** One primed body in flight (the engine object IS the public handle, §27.5). */
    public static final class PrimedTnt
            implements net.zaminmc.torch.entity.PrimedTnt {
        private final int entityId;
        /**
         * The body's OWN dimension (Wave FL4-F7 — the reference's
         * {@code new PrimedTntEntity(world, ...)} riding the world it was
         * constructed in): every consult the body drives — the physics'
         * ground test, the detonation's blast walk, the wire's tracker
         * fan — ranges ITS OWN world's cells, never another dimension's
         * same-coordinate cells.
         */
        private final int dimension;
        private double x, y, z;
        private double velocityX, velocityY, velocityZ;
        private int fuse;
        private boolean onGround;

        PrimedTnt(int entityId, int dimension, double x, double y, double z, int fuse) {
            this.entityId = entityId;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fuse = fuse;
        }

        public int entityId() {
            return entityId;
        }

        /** The body's own dimension (the world it was primed in). */
        public int dimension() {
            return dimension;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public double velocityX() {
            return velocityX;
        }

        public double velocityY() {
            return velocityY;
        }

        public double velocityZ() {
            return velocityZ;
        }

        public int fuse() {
            return fuse;
        }

        // --- the public §27.3 handle surface (the TNT family) --------------

        @Override
        public net.zaminmc.torch.util.Position position() {
            return new net.zaminmc.torch.util.Position(x, y, z);
        }

        @Override
        public int fuseTicks() {
            return fuse;
        }

        @Override
        public boolean onGround() {
            return onGround;
        }

        @Override
        public net.zaminmc.torch.util.Identifier typeIdentifier() {
            return net.zaminmc.torch.util.Identifier.parse("minecraft:primed_tnt");
        }

        @Override
        public net.zaminmc.torch.entity.EntitySnapshot snapshot() {
            return new net.zaminmc.torch.entity.EntitySnapshot(
                    entityId, typeIdentifier(), position(), 0.0f,
                    velocityX, velocityY, velocityZ,
                    0.0f, 0.0f, onGround, false);
        }
    }

    /** Observers the protocol adapter translates into wire packets. */
    public interface Listener {
        /** A primed body entered the world (the Spawn Entity). */
        void onTntPrimed(PrimedTnt tnt);

        /** A primed body ticked its physics (the Entity Teleport). */
        void onTntMoved(PrimedTnt tnt);

        /** A primed body left the world (the fuse end or a shutdown). */
        void onTntRemoved(PrimedTnt tnt);
    }

    private static final int[][] OFFSETS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
    };

    /**
     * The per-dimension solidity probe (Wave FL4-F7 — the reference's
     * {@code this.worldObj} consult widened over the engine's shared body
     * list): the body's physics ground test ranges the body's OWN world's
     * cells, the dimension stamp riding the body.
     */
    public interface SolidityProbe {
        boolean solidIn(int dimension, double x, double y, double z);
    }

    /**
     * The per-dimension detonation sink (Wave FL4-F7 — the reference's
     * {@code this.worldObj.explode(...)}): the blast walk tears the body's
     * OWN world — the wounds and the wire already carry the blast's
     * dimension (the generalized {@code detonateAtIn}), the stamp riding
     * the body selects it.
     */
    public interface DetonationSink {
        void detonateIn(int dimension, double x, double y, double z, float power);
    }

    private final EngineWorld world;
    private final RedstoneSystem redstone;
    private final SolidityProbe solidAt;
    private final DetonationSink explode;
    private final FxManager fx;
    private final Random random;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<PrimedTnt> primed = new ArrayList<>();
    private int nextEntityId;

    /**
     * @param explode the detonation sink: (the body's dimension, the
     *                explosion center, the power) — the engine's own
     *                dimension-routed explosion walk (blocks, wounds, the
     *                Explosion packet).
     */
    public TntSystem(EngineWorld world, RedstoneSystem redstone,
                      SolidityProbe solidAt,
                      DetonationSink explode,
                      FxManager fx, Random random, int firstEntityId) {
        this.world = world;
        this.redstone = redstone;
        this.solidAt = solidAt;
        this.explode = explode;
        this.fx = fx;
        this.random = random;
        this.nextEntityId = firstEntityId;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** @return an unmodifiable view of the live primed bodies (the push-surface walk + tests). */
    public List<PrimedTnt> allPrimed() {
        return List.copyOf(primed);
    }

    // ------------------------------------------------------------------
    // The piston push (the VIII13 additive families — the reference's
    // MovingBlockEntity.moveEntities lines 80-115)
    // ------------------------------------------------------------------

    /** The primed body's box (PrimedTntEntity's own setSize(0.98F, 0.98F) family). */
    public static final double TNT_HALF_WIDTH = 0.49;
    public static final double TNT_HEIGHT = 0.98;

    /**
     * The piston push over one primed body (the reference's
     * {@code Entity.move(amount * facing)}): the IMMEDIATE shift riding
     * the family's OWN flight law — the horizontal axes integrate
     * unclipped (the tick's plain integration carries no wall clip, the
     * family's established shape, named), the descending axis clamped by
     * the tick's own feet-relative consult (the cell below the target
     * feet holds the body at its top), the ascending axis unclipped
     * (the family carries no ceiling arm). The pushed body rides the
     * tick's own move publication. Tick-thread context (the push
     * surface's walk runs inside the moving blocks' own tick).
     */
    public void pushTnt(PrimedTnt tnt, double dx, double dy, double dz) {
        double newX = tnt.x + dx;
        double newZ = tnt.z + dz;
        double newY = tnt.y + dy;
        if (dy < 0.0) {
            // The tick's own feet-relative ground clamp: the block just
            // below the target y holds the body at its top — the body's
            // OWN world's cell (the dimension stamp riding the body).
            if (solidAt.solidIn(tnt.dimension, newX, newY - 0.1, newZ)) {
                newY = (int) Math.floor(newY - 0.1) + 1;
                tnt.onGround = true;
            }
        }
        tnt.x = newX;
        tnt.y = newY;
        tnt.z = newZ;
        for (Listener listener : listeners) {
            listener.onTntMoved(tnt);
        }
    }

    /**
     * The slime-block fling over one primed body (MovingBlockEntity
     * lines 94-107): the velocity SET along the facing axis — the
     * family's own drag and ground friction carry the set components
     * out.
     */
    public void flingTnt(PrimedTnt tnt, double axisX, double axisY, double axisZ) {
        tnt.velocityX = axisX != 0.0 ? axisX : tnt.velocityX;
        tnt.velocityY = axisY != 0.0 ? axisY : tnt.velocityY;
        tnt.velocityZ = axisZ != 0.0 ? axisZ : tnt.velocityZ;
    }

    /**
     * The blast's scatter add over one primed body (the reference's
     * Explosion.damageEntities lines 135-137 — {@code velocityX/Y/Z +=
     * dir * d11}, the add riding outside the takeDamage verdict; the
     * PrimedTnt carries NO immunity and NO takeDamage override in 1.8.8,
     * so the wound no-ops and the impulse alone lands — the TNT cannon's
     * own law). The body's own tick carries the impulse out.
     */
    public void addVelocity(PrimedTnt tnt, double dx, double dy, double dz) {
        tnt.velocityX += dx;
        tnt.velocityY += dy;
        tnt.velocityZ += dz;
    }

    // ------------------------------------------------------------------
    // The change listener: the redstone ignition
    // ------------------------------------------------------------------

    /**
     * The arrival arm (TntBlock.onAdded lines 38-43): a TNT placed into
     * power primes instantly. The neighbor arm rides the redstone system's
     * cross-system bus (every world commit and every source's ring walks
     * fan through it — the engine's neighborChanged). The IGNITION routes
     * the CHANGED world's own cells (Wave FL4-F7 — the reference's
     * {@code onAdded(World world, ...)} receives the world the write
     * landed in).
     */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        if (isTnt(newType)) {
            checkIgnite(changedWorld, position);
        }
    }

    /**
     * The neighbor arm (TntBlock.neighborChanged lines 51-56): a position
     * heard a change — power at it primes the block. Registered on the
     * redstone system's cross-system neighbor bus. Tick-thread context.
     */
    public void neighborHeard(BlockPosition pos) {
        if (isTnt(world.getBlock(pos))) {
            checkIgnite(world, pos);
        }
    }

    /**
     * The signal check (TntBlock lines 36-43 + 51-56): power primes the
     * block. The WRITE routes the acting world's own cells; the SIGNAL
     * consult rides the redstone system's own binding (the system's
     * constructor world — the overworld's bus — the redstone family's own
     * per-dimension pass is the FL4 ledger's named tail), so a foreign
     * dimension's arrival never consults the overworld's signal at the
     * foreign coordinates.
     */
    private void checkIgnite(EngineWorld actingWorld, BlockPosition pos) {
        if (actingWorld == world && redstone.neighborSignal(pos) > 0) {
            ignite(actingWorld, pos, FUSE_TICKS);
        }
    }

    /** @return whether the type is the TNT block. */
    static boolean isTnt(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().equals("tnt");
    }

    /** The public read (the dispenser's flint-and-steel arm — Slice 11a). */
    public boolean isTntBlock(BlockType type) {
        return isTnt(type);
    }

    /**
     * The dispensed TNT (Bootstrap's TNT arm, lines 328-338): a primed body
     * at an arbitrary position — the facing cell's center, the cell's floor —
     * with the full 80-tick fuse and the priming hiss. No block leaves the
     * world (the TNT never was a block there — it rode the slot). The body
     * carries the dispensing world's own dimension stamp (Wave FL4-F7).
     * Tick-thread context.
     */
    public void spawnPrimedAt(EngineWorld actingWorld, double x, double y, double z) {
        PrimedTnt tnt = new PrimedTnt(nextEntityId++, actingWorld.dimension(),
                x, y, z, FUSE_TICKS);
        float angle = (float) (random.nextFloat() * Math.PI * 2.0);
        tnt.velocityX = -(float) Math.sin(angle) * 0.02f;
        tnt.velocityY = 0.2f;
        tnt.velocityZ = -(float) Math.cos(angle) * 0.02f;
        primed.add(tnt);
        for (Listener listener : listeners) {
            listener.onTntPrimed(tnt);
        }
        // The priming hiss at the body's own position (the reference's
        // playSoundAtEntity(primedtntentity, ...) — the entity's own x/y/z,
        // no rise).
        fx.sound(new Position(x, y, z), "game.tnt.primed", 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    // The ignition
    // ------------------------------------------------------------------

    /**
     * The prime (TntBlock.ignite lines 66-74 + the removal): the block
     * leaves the world, the primed body takes its place with the small
     * random hop, the "game.tnt.primed" sound. The block's departure and
     * the body's dimension BOTH route the ACTING world (Wave FL4-F7 — the
     * reference's {@code ignite(World world, ...)}). Tick-thread context.
     */
    public void ignite(EngineWorld actingWorld, BlockPosition pos, int fuse) {
        actingWorld.setBlock(pos, actingWorld.airType());
        PrimedTnt tnt = new PrimedTnt(nextEntityId++, actingWorld.dimension(),
                pos.x() + 0.5, pos.y(), pos.z() + 0.5, fuse);
        float angle = (float) (random.nextFloat() * Math.PI * 2.0);
        tnt.velocityX = -(float) Math.sin(angle) * 0.02f;
        tnt.velocityY = 0.2f;
        tnt.velocityZ = -(float) Math.cos(angle) * 0.02f;
        primed.add(tnt);
        for (Listener listener : listeners) {
            listener.onTntPrimed(tnt);
        }
        // The hiss at the primed body itself (playSoundAtEntity — the
        // body's own x/y/z).
        fx.sound(new Position(tnt.x(), tnt.y(), tnt.z()), "game.tnt.primed", 1.0f, 1.0f);
    }

    /**
     * The exploded-by-blast arm (TntBlock.onExploded lines 58-65): the
     * consumed block primes a short-fuse body
     * {@code next(fuse/4) + fuse/8} — the chain-reaction stagger. The
     * caller (the explosion walk) has already removed the block. The body
     * carries the BLAST world's own dimension (Wave FL4-F7 — the
     * reference's {@code onExploded(World world, ...)}).
     */
    public void primeFromBlast(EngineWorld blastWorld, BlockPosition pos) {
        PrimedTnt tnt = new PrimedTnt(nextEntityId++, blastWorld.dimension(),
                pos.x() + 0.5, pos.y(), pos.z() + 0.5, FUSE_TICKS);
        tnt.fuse = random.nextInt(FUSE_TICKS / 4) + FUSE_TICKS / 8;
        float angle = (float) (random.nextFloat() * Math.PI * 2.0);
        tnt.velocityX = -(float) Math.sin(angle) * 0.02f;
        tnt.velocityY = 0.2f;
        tnt.velocityZ = -(float) Math.cos(angle) * 0.02f;
        primed.add(tnt);
        for (Listener listener : listeners) {
            listener.onTntPrimed(tnt);
        }
    }

    /**
     * The flint-and-steel / fire-charge use arm (TntBlock.use lines 96-113):
     * the ignition. Returns whether the use consumed the click (the
     * durability/consumption stays with the caller — the engine's use path
     * owns the held-stack wear). Tick-thread context.
     */
    public boolean useIgnite(EngineWorld actingWorld, BlockPosition pos) {
        if (!isTnt(actingWorld.getBlock(pos))) {
            return false;
        }
        ignite(actingWorld, pos, FUSE_TICKS);
        return true;
    }

    /**
     * The burning-arrow arm (TntBlock.onEntityCollision lines 91-104): an
     * on-fire arrow STICKING into the TNT's cell primes the block — the full
     * 80-tick fuse, the block leaves the world, and the stuck arrow's
     * tracked-cell consult un-sticks it a tick later (VT-030's order: the
     * stick at ArrowEntity line 304 precedes the consult at 308 — the arrow
     * never carries its flight through the now-open cell). (The reference's
     * igniter is the arrow's shooter when living — the engine's primed
     * bodies carry no igniter attribution, the engine's own standing
     * simplification.)
     * Tick-thread context.
     *
     * @return whether the cell held TNT and the prime happened.
     */
    public boolean igniteByArrow(EngineWorld actingWorld, BlockPosition pos) {
        if (!isTnt(actingWorld.getBlock(pos))) {
            return false;
        }
        ignite(actingWorld, pos, FUSE_TICKS);
        return true;
    }

    /** @return the live primed bodies (diagnostics/tests). */
    public List<PrimedTnt> primedBodies() {
        return List.copyOf(primed);
    }

    // ------------------------------------------------------------------
    // The fuse + the physics (PrimedTntEntity.tick lines 52-73)
    // ------------------------------------------------------------------

    /**
     * The per-tick advance: the physics hop, the fuse, the detonation. The
     * walk is the reference's own INDEXED loop (World.tickEntities lines
     * 1211-1246 — {@code for (int i = 0; i < entities.size(); i++)} over
     * the LIVE list): a body added DURING the walk — the chain reaction's
     * {@code primeFromBlast} inside a detonation — extends the list and
     * rides a later pass of the same walk, exactly vanilla's shape. The
     * spent bodies leave through a DEFERRED removal (the reference's own
     * {@code entities.removeAll(entitiesToRemove)} sweep), never a
     * mid-walk list rebuild (the pre-fix survivors rebuild threw
     * ConcurrentModificationException the moment a detonation's chain
     * reaction landed).
     */
    public void tick() {
        if (primed.isEmpty()) {
            return;
        }
        List<PrimedTnt> spent = null;
        for (int i = 0; i < primed.size(); i++) {
            PrimedTnt tnt = primed.get(i);
            // The physics (lines 54-68): gravity, the move, the drag, the
            // ground friction with the bounce.
            tnt.velocityY -= 0.04;
            tnt.x += tnt.velocityX;
            tnt.y += tnt.velocityY;
            tnt.z += tnt.velocityZ;
            tnt.velocityX *= 0.98;
            tnt.velocityY *= 0.98;
            tnt.velocityZ *= 0.98;
            // The feet-relative ground test: the block just below the
            // body's own y (the reference's collision-driven onGround) —
            // the body's OWN world's cell (the dimension stamp riding
            // the body, Wave FL4-F7).
            tnt.onGround = solidAt.solidIn(tnt.dimension, tnt.x, tnt.y - 0.1, tnt.z);
            if (tnt.onGround) {
                // The collision clamp (the reference's move stops at the
                // surface — the entity never sinks into the ground): the
                // feet rest on the top of the block below, then the bounce.
                tnt.y = (int) Math.floor(tnt.y - 0.1) + 1;
                tnt.velocityX *= 0.7;
                tnt.velocityZ *= 0.7;
                tnt.velocityY = -tnt.velocityY * 0.5;
            }
            for (Listener listener : listeners) {
                listener.onTntMoved(tnt);
            }
            if (tnt.fuse-- <= 0) {
                // The detonation (lines 69-72 + explode): the 4.0F blast at
                // the body's own position — the reference's
                // {@code y + height / 16.0F} (0.98 / 16 = 0.06125).
                for (Listener listener : listeners) {
                    listener.onTntRemoved(tnt);
                }
                explode.detonateIn(tnt.dimension,
                        tnt.x, tnt.y + TNT_HEIGHT / 16.0, tnt.z, POWER);
                if (spent == null) {
                    spent = new ArrayList<>();
                }
                spent.add(tnt);
            }
        }
        if (spent != null) {
            primed.removeAll(spent);
        }
    }

    /** The shutdown: every primed body leaves the wire. */
    public void shutdown() {
        for (PrimedTnt tnt : List.copyOf(primed)) {
            for (Listener listener : listeners) {
                listener.onTntRemoved(tnt);
            }
        }
        primed.clear();
    }
}
