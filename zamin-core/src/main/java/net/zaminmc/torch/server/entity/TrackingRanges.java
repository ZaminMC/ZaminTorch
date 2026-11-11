package net.zaminmc.torch.server.entity;

/**
 * THE PER-ENTITY TRACKING RANGES (WAVE TR — the reference's own
 * EntityMap/TrackedEntity law, the foundation Paper 0216's per-entity
 * override rides): which observers a tracked body's wire reaches.
 *
 * <p>THE REFERENCE LAW (read 1:1 from the supplied 1.8.8 source): every
 * tracked body carries its own {@code trackingRange} stamped at the
 * EntityMap registration — the per-type table (EntityMap.onEntityAdded
 * lines 60-120): the player 512 (update interval 2), the fishing bobber
 * 64/5, the arrow 64/20, the projectile family 64/10, the item 64/20, the
 * minecart and the boat 80/3, THE SQUID 64/3, the wither 80/3, the bat
 * 80/3, THE ENDER DRAGON 160/3, every other living mob (SpawnableEntity)
 * 80/3, the primed TNT 160/10, the falling block 160/20, the decoration
 * family 160/{@code MAX_VALUE}, the armor stand 160/3, the experience orb
 * 160/20, the ender crystal 256/{@code MAX_VALUE}. The registration CLAMPS
 * the table's range to the server's view distance in BLOCKS
 * (EntityMap.startTracking lines 128-130: {@code if (trackingRange >
 * viewDistance) trackingRange = viewDistance} — PlayerManager.getViewDistance
 * over ChunkMap.getViewDistance line 262: {@code chunkViewDistance * 16 -
 * 16}). The visibility test itself is the ±range BOX on the horizontal
 * plane alone (TrackedEntity.broadcastTo lines 379-387: x and z within
 * [-range, +range] inclusive, y NEVER consulted) — a square, not a circle,
 * and the boundary row ITSELF is inside.</p>
 *
 * <p>THE OBSERVABLE the law owns: a body beyond its range NEVER reaches
 * the observer — no spawn, no move, no metadata — and a body that crosses
 * out is DESTROYED on the observer's client while it keeps living
 * server-side (TrackedEntity.updatePlayer's remove arm), crossing back in
 * re-spawning the full add chain. The engine's retired wire fanned every
 * mob event to EVERY same-dimension observer with no range consult at all
 * — a mob 10,000 blocks away spawned on every client in the dimension.</p>
 *
 * <p>PAPER 0216 ("Make tracking range configurable per-entity", the
 * KigPaper census's row): the per-entity CUSTOM override consulted FIRST
 * — {@code getCustomTrackingRange() > 0} answers the custom whole, before
 * the table's category. In the patch the custom flows through the view
 * clamp at initial tracking (startTracking's own arm) while the live-update
 * setter writes the entry directly; the engine's consult is stateless —
 * every gate is an initial tracking — so the custom ALWAYS rides the clamp
 * (the engine's named deviation note: the view-distance clamp law holds
 * whole, whatever arms the range).</p>
 */
public final class TrackingRanges {

    private TrackingRanges() {
    }

    /**
     * WAVE TR-2 — the tracked wire families beyond the mobs' own band
     * (the roll-out over EntityMap.onEntityAdded's OTHER rows): every
     * tracked body family carries its own registration row, stamped at
     * the same per-type table. The rows, read 1:1 from the reference:
     * the ITEM 64 (line 92-93), the PROJECTILE family 64 (the arrow
     * 64/20 lines 72-73, the small fireball 64/10 lines 74-75, the
     * generic projectile 64/10 lines 76-77, the snowball/ender pearl/
     * egg/potion/XP bottle 64/10 lines 78-89 — the RANGE is one band
     * 64 whole, the intervals differ), the FISHING BOBBER 64 (lines
     * 70-71), the ENDER EYE 64 (lines 82-83), the FIREWORKS 64 (lines
     * 90-91), the MINECART and the BOAT 80 (lines 94-97), the primed
     * TNT 160 (lines 108-109), the FALLING BLOCK 160 (lines 110-111),
     * the DECORATION family 160 (lines 112-113 — the item frame, the
     * painting, the leash knot), the ARMOR STAND 160 (lines 114-115),
     * the EXPERIENCE ORB 160 (lines 116-117), the ENDER CRYSTAL 256
     * (lines 118-119 — the table's lone 256 row).
     */
    public enum TrackedKind {
        /** ItemEntity's row (lines 92-93): 64. */
        ITEM(64),
        /** The projectile family's rows (lines 72-89): one band 64. */
        PROJECTILE(64),
        /** The fishing bobber's row (lines 70-71): 64. */
        BOBBER(64),
        /** The eye of ender's row (lines 82-83): 64. */
        ENDER_EYE(64),
        /** The firework rocket's row (lines 90-91): 64. */
        FIREWORKS(64),
        /** The minecart/boat row (lines 94-97): 80. */
        VEHICLE(80),
        /** The primed TNT's row (lines 108-109): 160. */
        TNT(160),
        /** The falling block's row (lines 110-111): 160. */
        FALLING(160),
        /** The decoration family's row (lines 112-113): 160. */
        DECORATION(160),
        /** The armor stand's row (lines 114-115): 160. */
        ARMOR_STAND(160),
        /** The experience orb's row (lines 116-117): 160. */
        XP_ORB(160),
        /** The ender crystal's row (lines 118-119): 256. */
        ENDER_CRYSTAL(256);

        /** The registration row's own range, before the view clamp. */
        final int range;

        TrackedKind(int range) {
            this.range = range;
        }

        /** @return the reference table's own row (EntityMap lines 60-120). */
        public int trackingRange() {
            return range;
        }
    }

    /**
     * The per-type table (EntityMap.onEntityAdded's rows over the engine's
     * mob kinds — the mob family's own band of the reference's table; the
     * other families' rows ride their own spawn gates).
     */
    public static int rangeOf(MobType type) {
        // The squid's own row (EntityMap lines 98-99 — 64, NOT the living
        // mob family's 80).
        if (type == MobType.SQUID) {
            return 64;
        }
        // The wither's and the bat's own rows (lines 100-103).
        if (type == MobType.WITHER || type == MobType.BAT) {
            return 80;
        }
        // The ender dragon's own row (lines 104-105).
        if (type == MobType.ENDER_DRAGON) {
            return 160;
        }
        // Every other living mob — SpawnableEntity's row (lines 106-107).
        return 80;
    }

    /**
     * The view distance in BLOCKS (PlayerManager.getViewDistance over
     * ChunkMap.getViewDistance line 262: {@code chunkViewDistance * 16 - 16}
     * — the chunk radius's own block span: view 4 answers 48, view 10
     * answers 144).
     */
    public static int viewDistanceBlocks(int chunkViewDistance) {
        return chunkViewDistance * 16 - 16;
    }

    /**
     * The effective tracking range: Paper 0216's per-entity override
     * consulted FIRST (a positive custom answers before the table's
     * category), then the registration's view-distance clamp
     * (EntityMap.startTracking lines 128-130) — the table's 80 answers 48
     * on a view-4 server, a custom 512 answers 48 too, a custom 16
     * answers 16.
     */
    public static int effectiveRange(MobType type, int customTrackingRange,
                                     int chunkViewDistance) {
        int range = customTrackingRange > 0
                ? customTrackingRange // 0216's consult-first law
                : rangeOf(type);
        return Math.min(range, viewDistanceBlocks(chunkViewDistance));
    }

    /**
     * WAVE TR-2 — the other families' effective range (the roll-out's
     * consult): the kind's own table row through the SAME registration
     * clamp (EntityMap.startTracking lines 128-130 — every tracked body
     * rides the clamp, whatever its row): the item's 64 answers 48 on a
     * view-4 server, the crystal's 256 answers 48 too, the vehicle's 80
     * answers 48.
     */
    public static int effectiveRange(TrackedKind kind, int chunkViewDistance) {
        return Math.min(kind.trackingRange(), viewDistanceBlocks(chunkViewDistance));
    }

    /**
     * WAVE TR-3 — THE PLAYER'S OWN ROW (EntityMap.onEntityAdded lines
     * 61-62: {@code startTracking(entity, 512, 2)} — the table's widest
     * row, the update interval 2 the fastest). The registration clamp
     * rides it whole like every row: a player never reaches an observer
     * beyond the view distance in BLOCKS (the 512 answers 48 on the
     * view-4 rig, 144 on view 10, and answers whole only at view 33+).
     */
    public static final int PLAYER_RANGE = 512;

    /**
     * WAVE TR-3 — the player band's effective range: the 512 row through
     * the same registration clamp every family rides.
     */
    public static int effectivePlayerRange(int chunkViewDistance) {
        return Math.min(PLAYER_RANGE, viewDistanceBlocks(chunkViewDistance));
    }

    /**
     * The visibility test — TrackedEntity.broadcastTo's box law (lines
     * 379-387): the horizontal displacement within [-range, +range]
     * INCLUSIVE on both x and z, y never consulted. The boundary row is
     * inside the box (a body at exactly the range still reaches the
     * observer).
     */
    public static boolean inRange(int range, double playerX, double playerZ,
                                  double entityX, double entityZ) {
        double dx = playerX - entityX;
        double dz = playerZ - entityZ;
        return dx >= -range && dx <= range && dz >= -range && dz <= range;
    }
}
