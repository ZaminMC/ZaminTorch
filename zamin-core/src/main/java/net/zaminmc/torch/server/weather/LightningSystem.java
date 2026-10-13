package net.zaminmc.torch.server.weather;

import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The lightning family (Slice 20c — {@code ServerWorld.tickChunks} lines
 * 313-321 + {@code LightningBoltEntity} 1:1): the per-chunk strike roll
 * (1-in-100000 per ticking chunk per tick, only while both flags ride),
 * the target resolution ({@code findLightningTarget}: the precipitation
 * height, the ±3-grown box's sky-visible bodies, the random pick among
 * them), and the bolt's own two-tick life — the thunder sound pair, the
 * 3-wide damage box ({@code struckByLightning}: 5.0 + the 8-second burn),
 * and the trailing fire attempts over the reference's own stage walk.
 */
public final class LightningSystem {

    /**
     * The struck body ({@code Entity.struckByLightning} lines 1546-1552):
     * 5.0 through the damage pipeline plus the 8-second ignite — the
     * engine's own player/mob arms behind one callback.
     */
    public interface StruckBody {

        /** The body's feet position (the box test's input). */
        Position position();

        /** The 5.0 wound + the 8-second burn (160 ticks). */
        void strike();
    }

    /** The engine queries the strike walk needs. */
    public interface Surface {

        /** The precipitation height ({@code getPrecipitationHeight}). */
        int precipitationHeight(int x, int z);

        /** Whether the cell is air (the fire gate's first half). */
        boolean isAirAt(int x, int y, int z);

        /** Whether the cell below is solid (the fire's support gate). */
        boolean isSolidAt(int x, int y, int z);

        /** Writes the fire block (the caller already passed the gate). */
        void placeFireAt(int x, int y, int z);

        /** The sky-visible living bodies inside the grown box. */
        List<StruckBody> skyVisibleBodies(double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ);
    }

    /** The wire observers: the bolt's spawn + the thunder sound pair. */
    public interface Listener {

        /** The bolt struck ({@code Spawn Global Entity}, type 1). */
        default void onBoltStruck(int entityId, double x, double y, double z) {
        }

        /**
         * The thunder pair ({@code tick} lines 87-90:
         * {@code ambient.weather.thunder} at volume 10000 pitch
         * 0.8+rand*0.2, {@code random.explode} at volume 2 pitch
         * 0.5+rand*0.2).
         */
        default void onThunderPair(double x, double y, double z,
                                   float thunderPitch, float explodePitch) {
        }
    }

    /** One live bolt ({@code LightningBoltEntity}'s own fields). */
    private static final class Bolt {
        final int entityId;
        final double x;
        final double y;
        final double z;
        int stage = 2;
        int fireAttempts;
        long seed;

        Bolt(int entityId, double x, double y, double z, Random random) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.seed = random.nextLong();
            this.fireAttempts = random.nextInt(3) + 1;
        }
    }

    private final Surface surface;
    private final Random random;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<Bolt> bolts = new ArrayList<>();
    /** The reference's shared strike LCG ({@code randomTickLCG}). */
    private int strikeLcg;
    private int nextEntityId;
    /** The difficulty read (0 peaceful .. 3 hard); null until wired. */
    private volatile java.util.function.IntSupplier difficultyRead;

    public LightningSystem(Surface surface, Random random, int firstEntityId) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.random = Objects.requireNonNull(random, "random");
        this.nextEntityId = firstEntityId;
    }

    /**
     * Wires the difficulty read (the constructor's fire gate — the bolt
     * only ignites ground on NORMAL or HARD, LightningBoltEntity lines
     * 44-46).
     */
    public void setDifficultyRead(java.util.function.IntSupplier difficultyRead) {
        this.difficultyRead = Objects.requireNonNull(difficultyRead, "difficultyRead");
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** @return the live bolt count (diagnostics + tests). */
    public int liveBolts() {
        return bolts.size();
    }

    /**
     * The strike roll ({@code ServerWorld.tickChunks} lines 313-321): the
     * 1-in-100000 roll per ticking chunk, the LCG-derived in-chunk pick,
     * then {@code findLightningTarget} — the bolt spawns only when both
     * flags ride. Tick-thread context.
     *
     * @param chunkX the chunk's grid x (the roll's origin block)
     * @param chunkZ the chunk's grid z
     */
    public void tryStrikeAtChunk(int chunkX, int chunkZ, boolean raining, boolean thundering) {
        // The reference's own order (line 314): the roll runs first — the
        // shared stream advances every tick whatever the sky holds.
        if (random.nextInt(100000) != 0 || !raining || !thundering) {
            return;
        }
        // The reference's own pick derivation (lines 316-318): the LCG's
        // shared walk — the same stream the ice/snow arm reads.
        strikeLcg = strikeLcg * 3 + 1013904223;
        int pick = strikeLcg >> 2;
        int x = chunkX * 16 + (pick & 15);
        int z = chunkZ * 16 + (pick >> 8 & 15);
        int height = surface.precipitationHeight(x, z);
        // findLightningTarget lines 370-381: the ±3-grown box up to the
        // world's height, the sky-visible bodies, one at random — else the
        // precipitation cell itself.
        List<StruckBody> candidates = surface.skyVisibleBodies(
                x - 3.0, height - 3.0, z - 3.0, x + 4.0, height + 10.0, z + 4.0);
        double strikeY = height;
        double strikeX = x;
        double strikeZ = z;
        if (!candidates.isEmpty()) {
            Position chosen = candidates.get(random.nextInt(candidates.size())).position();
            strikeX = chosen.x();
            strikeY = Math.floor(chosen.y());
            strikeZ = chosen.z();
        }
        // The constructor's own fire gate (lines 44-46): NORMAL or HARD
        // only (peaceful/easy bolts still wound — the fire alone stays
        // behind the gate).
        java.util.function.IntSupplier difficultyAt = difficultyRead;
        int difficulty = difficultyAt == null ? 2 : difficultyAt.getAsInt();
        strike((int) strikeX, strikeY, (int) strikeZ, difficulty == 2 || difficulty == 3);
    }

    /**
     * The bolt's strike ({@code LightningBoltEntity}'s constructor): the
     * entity spawns, the constructor's own fire attempt lands (the
     * difficulty gate is the caller's), and the wire learns the spawn.
     * Tick-thread context.
     */
    public void strike(int x, double y, int z, boolean fireAllowed) {
        Bolt bolt = new Bolt(nextEntityId++, x + 0.0, y, z + 0.0, random);
        bolts.add(bolt);
        for (Listener listener : listeners) {
            listener.onBoltStruck(bolt.entityId, bolt.x, bolt.y, bolt.z);
        }
        if (fireAllowed) {
            // The constructor's fire walk (lines 48-59): the strike cell
            // first, then the four ±1 box attempts.
            tryPlaceFire((int) Math.floor(bolt.x), (int) Math.floor(bolt.y), (int) Math.floor(bolt.z));
            for (int i = 0; i < 4; i++) {
                tryPlaceFire((int) Math.floor(bolt.x) + random.nextInt(3) - 1,
                        (int) Math.floor(bolt.y) + random.nextInt(3) - 1,
                        (int) Math.floor(bolt.z) + random.nextInt(3) - 1);
            }
        }
    }

    private void tryPlaceFire(int x, int y, int z) {
        if (surface.isAirAt(x, y, z) && surface.isSolidAt(x, y - 1, z)) {
            surface.placeFireAt(x, y, z);
        }
    }

    /**
     * The bolt's life ({@code LightningBoltEntity.tick} lines 78-131): the
     * stage-2 sound pair, the stage >= 0 damage box, and the trailing fire
     * attempts over the reference's own stage walk. Tick-thread context.
     */
    public void tick() {
        List<Bolt> finished = new ArrayList<>();
        for (Bolt bolt : bolts) {
            if (bolt.stage == 2) {
                // Lines 87-90: the thunder at 10000 (the global volume) and
                // the explode pair.
                float thunderPitch = 0.8f + random.nextFloat() * 0.2f;
                float explodePitch = 0.5f + random.nextFloat() * 0.2f;
                for (Listener listener : listeners) {
                    listener.onThunderPair(bolt.x, bolt.y, bolt.z,
                            thunderPitch, explodePitch);
                }
            }
            bolt.stage--;
            if (bolt.stage < 0) {
                // Lines 96-112: the fire attempts over the
                // {@code stage < -nextInt(10)} re-gate, one per re-arm.
                if (bolt.fireAttempts == 0) {
                    finished.add(bolt);
                } else if (bolt.stage < -random.nextInt(10)) {
                    bolt.fireAttempts--;
                    bolt.stage = 1;
                    bolt.seed = random.nextLong();
                    tryPlaceFire((int) Math.floor(bolt.x),
                            (int) Math.floor(bolt.y), (int) Math.floor(bolt.z));
                }
            }
            if (bolt.stage >= 0) {
                // Lines 114-129: the damage box — every body within
                // (x±3, y-3..y+9, z±3) takes the strike.
                for (StruckBody body : surface.skyVisibleBodies(
                        bolt.x - 3.0, bolt.y - 3.0, bolt.z - 3.0,
                        bolt.x + 3.0, bolt.y + 6.0 + 3.0, bolt.z + 3.0)) {
                    body.strike();
                }
            }
        }
        bolts.removeAll(finished);
    }
}
