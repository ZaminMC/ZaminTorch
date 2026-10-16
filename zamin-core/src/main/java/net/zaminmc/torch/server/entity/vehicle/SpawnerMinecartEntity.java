package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;

import java.util.Random;

/**
 * The spawner minecart (Slice 66 — the reference port of
 * vehicle/SpawnerMinecartEntity.java over block/spawner/MobSpawner.java):
 * the rolling mob spawner whose anonymous MobSpawner ticks at the cart's
 * own cell.
 *
 * <p><b>The type default (MobSpawner line 19, 1:1):</b> the field's own
 * initializer is {@code "Pig"} — a summoned spawner minecart with no NBT
 * IS a pig spawner until data says otherwise (readNbt's absent EntityId
 * would clear it; the engine's item has no NBT surface, so the field
 * default is the observable law — ledgered).</p>
 *
 * <p><b>The tick walk (MobSpawner.tick lines 49-121, the server arm,
 * 1:1):</b> the 16-block player gate at the cart's cell, the delay
 * countdown (the 20-tick initial, the 200..800 reroll), then the
 * four-attempt cycle — each attempt counts the same-kind mobs inside the
 * spawn-range-grown box (the 6-mob cap aborts the WHOLE cycle with a
 * fresh delay, the reference's own {@code return}), rolls the position
 * ({@code nextDouble() - nextDouble()} per axis, {@code nextInt(3) - 1}
 * for y), and spawns when the cell is unobstructed. The 2004 flame burst
 * fires PER SPAWNED MOB (the reference fires doEvent inside the loop);
 * the delay reroll's {@code broacastEvent(1)} rides the cart's own
 * Entity Status (SpawnerMinecartEntity's override:
 * {@code world.doEntityEvent(this, (byte) event)}).</p>
 *
 * <p><b>The NBT (readNbt/writeNbt through the entity's own):</b> the
 * spawner's EntityId/Delay rows persist through the ZVD variant payload
 * (the same envelope the block spawner rides — the single-type model,
 * the SpawnPotentials weighted list ledgered with the block family).</p>
 */
public class SpawnerMinecartEntity extends MinecartEntity {

    // The reference's own constants (MobSpawner lines 16-29).
    /** The initial delay (line 16: 20). */
    public static final int INITIAL_DELAY = 20;
    /** The reroll band's floor (line 22: 200). */
    public static final int MIN_SPAWN_DELAY = 200;
    /** The reroll band's ceiling (line 23: 800). */
    public static final int MAX_SPAWN_DELAY = 800;
    /** The attempts per cycle (line 24: 4). */
    public static final int SPAWN_COUNT = 4;
    /** The nearby cap (line 27: 6). */
    public static final int MAX_NEARBY_ENTITIES = 6;
    /** The player gate's range (line 28: 16). */
    public static final int REQUIRED_PLAYER_RANGE = 16;
    /** The spawn roll's range (line 29: 4). */
    public static final int SPAWN_RANGE = 4;

    /**
     * The engine surfaces the walk needs (the SpawnerManager's own gates
     * over the cart's moving position). Wired at spawn by the engine like
     * the hopper cart's transfer host.
     */
    public interface SpawnHost {
        /** The player gate (isPlayerWithinRange at the point, 16 blocks). */
        boolean playerNearby(double x, double y, double z);

        /** The same-kind count inside the spawn-range-grown box at the cell. */
        int nearbyOfKind(MobType type, int cellX, int cellY, int cellZ);

        /**
         * The spawn (the canSpawn + isUnobstructed stand-in: the engine's
         * body-cell + head-cell gate).
         */
        boolean spawnAt(MobType type, double x, double y, double z);
    }

    /** The reference's own field default: "Pig" (line 19). */
    private MobType type = MobType.PIG;
    private int delay = INITIAL_DELAY;
    private final SpawnHost host;
    /** The fired-count/spin-reset diff surface (the manager's listener flips). */
    private int flameBursts;
    private int spinResets;

    public SpawnerMinecartEntity(int entityId, Position position, Random random,
                                 SpawnHost host) {
        super(entityId, Kind.SPAWNER_MINECART, position, random);
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    /** The wire's minecart type index: 4 = spawner. */
    @Override
    public int minecartTypeIndex() {
        return 4;
    }

    /** The spawned kind (the reference's getType — the EntityId row). */
    public MobType type() {
        return type;
    }

    /** The type's write arm (the readNbt EntityId row's landing). */
    public void setType(MobType type) {
        this.type = java.util.Objects.requireNonNull(type, "type");
    }

    /** @return the remaining spawn-delay ticks (the Delay row). */
    public int delay() {
        return delay;
    }

    /** The delay's write arm (the readNbt Delay row's landing). */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /** @return the flame bursts fired since the last consume (per spawn). */
    public int consumeFlameBursts() {
        int fired = flameBursts;
        flameBursts = 0;
        return fired;
    }

    /** @return the spin resets since the last consume (the delay rerolls). */
    public int consumeSpinResets() {
        int resets = spinResets;
        spinResets = 0;
        return resets;
    }

    /** The delay reroll (delay lines 156-169): the 200..800 band + the event-1. */
    private void rerollDelay() {
        if (MAX_SPAWN_DELAY <= MIN_SPAWN_DELAY) {
            delay = MIN_SPAWN_DELAY;
        } else {
            delay = MIN_SPAWN_DELAY + random().nextInt(MAX_SPAWN_DELAY - MIN_SPAWN_DELAY);
        }
        spinResets++; // broadcastEvent(1) → doEntityEvent(this, (byte) 1)
    }

    /**
     * The kind's tick (MobSpawner.tick lines 49-121, the server arm,
     * 1:1): the cart body moves first, then the spawner walk at the
     * cart's floored cell (SpawnerMinecartEntity's getPos:
     * {@code new BlockPos(this)}).
     */
    @Override
    protected void tickKind(net.zaminmc.torch.server.entity.vehicle.VehicleEntity.WorldQuery world) {
        super.tickKind(world);
        if (dead()) {
            return;
        }
        Position at = position();
        int cellX = (int) Math.floor(at.x());
        int cellY = (int) Math.floor(at.y());
        int cellZ = (int) Math.floor(at.z());
        // isNearPlayer (lines 51-54): the 16-block gate at the cell's center.
        if (!host.playerNearby(cellX + 0.5, cellY + 0.5, cellZ + 0.5)) {
            return;
        }
        if (delay == -1) {
            rerollDelay();
        }
        if (delay > 0) {
            delay--;
            return;
        }
        boolean fired = false;
        for (int i = 0; i < SPAWN_COUNT; i++) {
            // The nearby cap (lines 77-85): the 6-mob abort re-arms the
            // delay and ends the WHOLE cycle (the reference's return).
            if (host.nearbyOfKind(type, cellX, cellY, cellZ) >= MAX_NEARBY_ENTITIES) {
                rerollDelay();
                return;
            }
            // The position roll (lines 87-89).
            double sx = cellX + (random().nextDouble() - random().nextDouble())
                    * SPAWN_RANGE + 0.5;
            double sy = cellY + random().nextInt(3) - 1;
            double sz = cellZ + (random().nextDouble() - random().nextDouble())
                    * SPAWN_RANGE + 0.5;
            if (host.spawnAt(type, sx, sy, sz)) {
                // Lines 106-108: the 2004 flame burst fires PER SPAWN
                // (inside the loop — the block spawner's engine port fires
                // once per cycle; the cart keeps the reference's own shape).
                flameBursts++;
                fired = true;
            }
        }
        // Line 117-119: the reroll only when the cycle spawned something.
        if (fired) {
            rerollDelay();
        }
    }
}
