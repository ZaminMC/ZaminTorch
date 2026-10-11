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
import java.util.function.BiConsumer;
import java.util.function.Predicate;

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
 * reference's {@code y + height/16} = +0.06).
 *
 * <h2>Engine adaptations, all ledgered</h2>
 *
 * <ul>
 *   <li>The explosion damage is the engine's creeper walk parameterized
 *       (the falloff to double the power, the easy-difficulty cap); the
 *       reference's ray-density attenuation rides the explosion slice's
 *       own refinement.</li>
 *   <li>The burning-arrow collision arm (TntBlock.onEntityCollision) needs
 *       per-block entity collision callbacks — ledgered for the projectile
 *       slice.</li>
 *   <li>The smoke trail rides the particle system slice.</li>
 * </ul>
 */
public final class TntSystem implements WorldChangeListener {

    /** The vanilla fuse (PrimedTntEntity line 36). */
    static final int FUSE_TICKS = 80;
    /** The vanilla explosion power (PrimedTntEntity.explode). */
    static final float POWER = 4.0f;
    /** The point-blank cap on easy difficulty ((8 * 2 * power + 1) / 2). */
    public static final float MAX_DAMAGE = 32.0f;

    /** One primed body in flight. */
    public static final class PrimedTnt {
        private final int entityId;
        private double x, y, z;
        private double velocityX, velocityY, velocityZ;
        private int fuse;
        private boolean onGround;

        PrimedTnt(int entityId, double x, double y, double z, int fuse) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fuse = fuse;
        }

        public int entityId() {
            return entityId;
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

    private final EngineWorld world;
    private final RedstoneSystem redstone;
    private final Predicate<Position> solidAt;
    private final BiConsumer<Position, Float> explode;
    private final FxManager fx;
    private final Random random;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<PrimedTnt> primed = new ArrayList<>();
    private int nextEntityId;

    /**
     * @param explode the detonation sink: (the explosion center, the power)
     *                — the engine's own explosion walk (blocks, wounds, the
     *                Explosion packet).
     */
    public TntSystem(EngineWorld world, RedstoneSystem redstone,
                      Predicate<Position> solidAt,
                      BiConsumer<Position, Float> explode,
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

    // ------------------------------------------------------------------
    // The change listener: the redstone ignition
    // ------------------------------------------------------------------

    /**
     * The arrival arm (TntBlock.onAdded lines 38-43): a TNT placed into
     * power primes instantly. The neighbor arm rides the redstone system's
     * cross-system bus (every world commit and every source's ring walks
     * fan through it — the engine's neighborChanged).
     */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        if (isTnt(newType)) {
            checkIgnite(position);
        }
    }

    /**
     * The neighbor arm (TntBlock.neighborChanged lines 51-56): a position
     * heard a change — power at it primes the block. Registered on the
     * redstone system's cross-system neighbor bus. Tick-thread context.
     */
    public void neighborHeard(BlockPosition pos) {
        if (isTnt(world.getBlock(pos))) {
            checkIgnite(pos);
        }
    }

    /** The signal check (TntBlock lines 36-43 + 51-56): power primes the block. */
    private void checkIgnite(BlockPosition pos) {
        if (redstone.neighborSignal(pos) > 0) {
            ignite(pos, FUSE_TICKS);
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
     * world (the TNT never was a block there — it rode the slot). Tick-thread
     * context.
     */
    public void spawnPrimedAt(double x, double y, double z) {
        PrimedTnt tnt = new PrimedTnt(nextEntityId++, x, y, z, FUSE_TICKS);
        float angle = (float) (random.nextFloat() * Math.PI * 2.0);
        tnt.velocityX = -(float) Math.sin(angle) * 0.02f;
        tnt.velocityY = 0.2f;
        tnt.velocityZ = -(float) Math.cos(angle) * 0.02f;
        primed.add(tnt);
        for (Listener listener : listeners) {
            listener.onTntPrimed(tnt);
        }
        fx.sound(new Position(x, y + 0.5, z), "game.tnt.primed", 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    // The ignition
    // ------------------------------------------------------------------

    /**
     * The prime (TntBlock.ignite lines 66-74 + the removal): the block
     * leaves the world, the primed body takes its place with the small
     * random hop, the "game.tnt.primed" sound. Tick-thread context.
     */
    public void ignite(BlockPosition pos, int fuse) {
        world.setBlock(pos, world.airType());
        PrimedTnt tnt = new PrimedTnt(nextEntityId++,
                pos.x() + 0.5, pos.y(), pos.z() + 0.5, fuse);
        float angle = (float) (random.nextFloat() * Math.PI * 2.0);
        tnt.velocityX = -(float) Math.sin(angle) * 0.02f;
        tnt.velocityY = 0.2f;
        tnt.velocityZ = -(float) Math.cos(angle) * 0.02f;
        primed.add(tnt);
        for (Listener listener : listeners) {
            listener.onTntPrimed(tnt);
        }
        fx.sound(new Position(tnt.x(), tnt.y() + 0.5, tnt.z()), "game.tnt.primed", 1.0f, 1.0f);
    }

    /**
     * The exploded-by-blast arm (TntBlock.onExploded lines 58-65): the
     * consumed block primes a short-fuse body
     * {@code next(fuse/4) + fuse/8} — the chain-reaction stagger. The
     * caller (the explosion walk) has already removed the block.
     */
    public void primeFromBlast(BlockPosition pos) {
        PrimedTnt tnt = new PrimedTnt(nextEntityId++,
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
    public boolean useIgnite(BlockPosition pos) {
        if (!isTnt(world.getBlock(pos))) {
            return false;
        }
        ignite(pos, FUSE_TICKS);
        return true;
    }

    /** @return the live primed bodies (diagnostics/tests). */
    public List<PrimedTnt> primedBodies() {
        return List.copyOf(primed);
    }

    // ------------------------------------------------------------------
    // The fuse + the physics (PrimedTntEntity.tick lines 52-73)
    // ------------------------------------------------------------------

    /** The per-tick advance: the physics hop, the fuse, the detonation. */
    public void tick() {
        if (primed.isEmpty()) {
            return;
        }
        List<PrimedTnt> survivors = new ArrayList<>(primed.size());
        for (PrimedTnt tnt : primed) {
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
            // body's own y (the reference's collision-driven onGround).
            tnt.onGround = solidAt.test(new Position(tnt.x, tnt.y - 0.1, tnt.z));
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
                // the body's own position.
                for (Listener listener : listeners) {
                    listener.onTntRemoved(tnt);
                }
                explode.accept(new Position(tnt.x, tnt.y + 0.06, tnt.z), POWER);
                continue;
            }
            survivors.add(tnt);
        }
        primed.clear();
        primed.addAll(survivors);
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
