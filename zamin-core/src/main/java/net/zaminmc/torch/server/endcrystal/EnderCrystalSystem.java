package net.zaminmc.torch.server.endcrystal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The ender-crystal system (Slice 62 — the reference port of
 * entity/EnderCrystalEntity.java over the engine's ownership model): the
 * End-only registry, the per-tick fire walk, and the any-damage death.
 *
 * <p><b>The tick (EnderCrystalEntity.tick lines 39-51, 1:1):</b> the spin
 * counter advances, the synced slot-8 value re-publishes, and the crystal's
 * own feet cell takes FIRE whenever it holds anything else — the
 * reference's {@code world.dimension instanceof TheEndDimension} gate is
 * structural here (the system's surface IS the End world). The pillar-top
 * crystal's feet cell is the generator's BEDROCK cap: the reference spawns
 * the crystal at the cap's own cell (EndPillarFeature lines 47-50 — the
 * entity and the bedrock write land on the same y), so the first tick's
 * fire REPLACES the cap — vanilla's own observable (the burning base under
 * every pillar crystal, the fire that stays after the pop).</p>
 *
 * <p><b>The death (takeDamage lines 67-83, 1:1):</b> any wound that is not
 * invulnerable-zero lands — the countdown zeroes, the body removes, and
 * the 6.0F destructive explosion (the reference's {@code world.explode(
 * null, x, y, z, 6.0F, true)} — the six-arg overload's {@code createFire =
 * false, destructive = true}) detonates through the engine's End-world
 * blast walk. Arrows, melee, snowballs, anything: the crystal has no
 * armor, no resistance, no i-frames of its own.</p>
 *
 * <p><b>The spawn:</b> the End generator's populate-time records (the
 * pillar tops — EndPillarFeature lines 47-49's {@code nextFloat() * 360}
 * yaw roll) drain through the chunk-publish walk exactly once per chunk,
 * the chest-minecart idempotence shape. The rebuild is deterministic (the
 * decorate's own re-seeded stream), so persistence rides a destroyed-set
 * store instead of a population snapshot: a destroyed pillar stays
 * destroyed across restarts (vanilla's own entity ledger keeps the pop),
 * and a standing crystal re-materializes at its exact birth spot (the
 * reference's empty writeCustomNbt saves no spin phase to keep).</p>
 *
 * <p><b>Ownership:</b> the registry lives on the engine's tick thread with
 * the armor stands' own confinement — spawn, tick, and wound all run on
 * the simulation lane; the listeners fan the wire packets out.</p>
 */
public final class EnderCrystalSystem {

    /** The death blast (takeDamage line 77: 6.0F). */
    public static final float BLAST_POWER = 6.0f;

    /**
     * The blast's point-blank wound ceiling — the reference Explosion's
     * {@code (d10*d10 + d10) / 2.0 * 8.0 * f3 + 1.0} at density 1 (d10 1):
     * {@code 8 * power * 2 + 1} = 16 * power + 1.
     */
    public static final float BLAST_MAX_DAMAGE = 16.0f * BLAST_POWER + 1.0f;

    /** Events the protocol adapter translates into the wire form. */
    public interface Listener {
        /** A crystal spawned (the Spawn Object 200 + the slot-8 Int). */
        default void onCrystalSpawned(EnderCrystal crystal) { }

        /** A crystal removed (the Destroy Entities). */
        default void onCrystalRemoved(EnderCrystal crystal) { }

        /** The slot-8 Int changed (the wound's zero — the dirty re-send). */
        default void onCrystalHealthChanged(EnderCrystal crystal) { }
    }

    /** The engine surface the system reads (the End world only). */
    public interface Surface {
        /** The block at a cell of the End world. */
        String blockAt(int x, int y, int z);

        /** Writes the fire block into the End world. */
        void setFire(int x, int y, int z);

        /** The blast at a point of the End world (the 6.0F destructive walk). */
        void explode(double x, double y, double z);

        /** The engine's tick clock (diagnostics). */
        long now();
    }

    private final Surface surface;
    private final Random random;
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<Integer, EnderCrystal> crystals = new LinkedHashMap<>();
    /**
     * The destroyed birth cells (persistence's own key): a pillar whose
     * crystal popped never re-spawns one. Keyed by the packed block cell
     * the record spawned on — the rebuild's deterministic birth spot.
     */
    private final Set<Long> destroyedCells = new HashSet<>();
    private final int firstId;
    private int nextId;

    public EnderCrystalSystem(Surface surface, Random random, int firstId) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.random = Objects.requireNonNull(random, "random");
        this.firstId = firstId;
        this.nextId = firstId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The id band the crystals live in. */
    public int firstId() {
        return firstId;
    }

    /** Every standing crystal (the join and arrival views' walk). */
    public List<EnderCrystal> all() {
        return new ArrayList<>(crystals.values());
    }

    /** The crystal with the engine entity id, or null. */
    public EnderCrystal byId(int entityId) {
        return crystals.get(entityId);
    }

    /** The crystal whose box holds the point, or null (the arrow's walk). */
    public EnderCrystal at(double x, double y, double z) {
        for (EnderCrystal crystal : crystals.values()) {
            if (crystal.contains(x, y, z)) {
                return crystal;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The generation drain (EndPillarFeature lines 47-49)
    // ------------------------------------------------------------------

    /**
     * One pillar-top record lands: the cell's destroyed mark refuses the
     * re-spawn (the persistence law), and the standing body takes its
     * birth spot — the +0.5 centers, the pillar-top y, the rolled yaw.
     * The chest-minecart idempotence shape: a crystal already standing on
     * the cell means a previous drain landed it.
     */
    public boolean spawnFromGeneration(int blockX, int blockY, int blockZ, float yaw) {
        long cell = pack(blockX, blockY, blockZ);
        if (destroyedCells.contains(cell)) {
            return false; // the popped pillar stays empty
        }
        for (EnderCrystal standing : crystals.values()) {
            if ((int) Math.floor(standing.x()) == blockX
                    && (int) Math.floor(standing.y()) == blockY
                    && (int) Math.floor(standing.z()) == blockZ) {
                return false; // already drained in this life
            }
        }
        return spawn(blockX + 0.5, blockY, blockZ + 0.5, yaw);
    }

    /** The spawn proper (the public API surface: plugins may place one). */
    public boolean spawn(double x, double y, double z, float yaw) {
        EnderCrystal crystal = new EnderCrystal(nextId++, x, y, z, yaw,
                random.nextInt(100000));
        crystals.put(crystal.entityId(), crystal);
        for (Listener listener : listeners) {
            listener.onCrystalSpawned(crystal);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The tick (EnderCrystalEntity.tick lines 39-51)
    // ------------------------------------------------------------------

    /**
     * One tick of every standing crystal: the spin advances and the feet
     * cell takes FIRE whenever it holds anything else — the bedrock cap
     * of a fresh pillar included (the reference's own write order: the
     * cap lands at generate, the first tick's fire eats it).
     */
    public void tick() {
        for (EnderCrystal crystal : crystals.values()) {
            crystal.advanceRenderTicks();
            int x = (int) Math.floor(crystal.x());
            int y = (int) Math.floor(crystal.y());
            int z = (int) Math.floor(crystal.z());
            if (!"fire".equals(surface.blockAt(x, y, z))) {
                surface.setFire(x, y, z);
            }
        }
    }

    // ------------------------------------------------------------------
    // The death (takeDamage lines 67-83)
    // ------------------------------------------------------------------

    /**
     * Any damage landed (the reference's takeDamage — no gate but the
     * invulnerable refusal, which the callers' own sources never carry):
     * the countdown zeroes, the body removes, the 6.0F blast detonates.
     *
     * @return true when a crystal actually popped.
     */
    public boolean damage(EnderCrystal crystal) {
        Objects.requireNonNull(crystal, "crystal");
        if (!crystals.containsKey(crystal.entityId())) {
            return false; // already gone
        }
        crystal.wound();
        for (Listener listener : listeners) {
            listener.onCrystalHealthChanged(crystal);
        }
        remove(crystal);
        surface.explode(crystal.x(), crystal.y(), crystal.z());
        destroyedCells.add(pack((int) Math.floor(crystal.x()),
                (int) Math.floor(crystal.y()), (int) Math.floor(crystal.z())));
        return true;
    }

    /**
     * The projectile consult (the arrow's pop): the box holder at the
     * point, if any, takes its wound.
     *
     * @return true when a crystal popped (the projectile dies with it).
     */
    public boolean damageAt(double x, double y, double z) {
        EnderCrystal crystal = at(x, y, z);
        return crystal != null && damage(crystal);
    }

    /** The removal (the registry drop + the wire destroy). */
    public void remove(EnderCrystal crystal) {
        crystals.remove(crystal.entityId());
        for (Listener listener : listeners) {
            listener.onCrystalRemoved(crystal);
        }
    }

    // ------------------------------------------------------------------
    // The persistence (the destroyed-set law)
    // ------------------------------------------------------------------

    /** The destroyed birth cells (saved at saveAllNow). */
    public Set<Long> destroyedCellsSnapshot() {
        return new HashSet<>(destroyedCells);
    }

    /** The restore: the saved destroyed marks refuse their re-spawns. */
    public void restoreDestroyedCells(Set<Long> cells) {
        destroyedCells.addAll(cells);
    }

    /** The packed block cell (the persistence key). */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (long) (y & 0xFFF);
    }
}
