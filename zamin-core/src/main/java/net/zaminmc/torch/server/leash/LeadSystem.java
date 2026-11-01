package net.zaminmc.torch.server.leash;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The leash system (Slice 11c — the reference port of item/LeadItem +
 * entity/decoration/LeadKnotEntity + MobEntity's leash arms +
 * PathFinderMobEntity.updateLeashStatus): the knot registry at the
 * fences, the player's hold (the right-click attach + the re-click
 * release), the fence tie (the lead's use-on-fence arm — no consume,
 * the mob already paid it), the knot's own interact (the tie + the
 * removal, creative releasing without the drop), the survival poll (the
 * 100-tick canSurvive walk), and the leash physics — the follow at four
 * blocks, the pull at six ({@code v += d * |d| * 0.4} per component),
 * the snap at ten with the lead's drop.
 *
 * <p>FL4-GAP2 Slice D — the per-world law: each dimension owns its OWN
 * family (the reference's per-WorldServer leash knots — a knot lives in
 * the world its fence stands in): the knots are keyed in this family's
 * own map, the survival poll reads this family's OWN world's cells, the
 * tie scan gates on the mob's own dimension, and every knot carries the
 * family's dimension stamp (the wire's observer gate). The three
 * families share ONE id supplier so the Spawn Object 77 ids stay unique
 * across the worlds.</p>
 *
 * <p>The mobs stay the MobManager's; this system owns the knots and the
 * per-tick walk. The wire events (the Spawn Object 77, the Attach Entity
 * pair, the Destroy Entities) fan through the listener.</p>
 */
public final class LeadSystem {

    /** The leash's tie range (LeadItem.attachLead line 39: 7.0). */
    private static final double TIE_RANGE = 7.0;
    /** The follow threshold (PathFinderMobEntity line 96: 4.0F). */
    private static final float FOLLOW_RANGE = 4.0F;
    /** The pull threshold (PathFinderMobEntity line 103: 6.0F). */
    private static final float PULL_RANGE = 6.0F;
    /** The snap threshold (PathFinderMobEntity line 110: 10.0F). */
    private static final float SNAP_RANGE = 10.0F;
    /** The pull force (PathFinderMobEntity line 106-108: 0.4). */
    private static final double PULL_FORCE = 0.4;

    /** Events the protocol adapter translates into the leash's wire form. */
    public interface Listener {
        /** A knot spawned (the Spawn Object 77 at the fence). */
        default void onKnotSpawned(LeashKnot knot) { }

        /** A knot removed (the Destroy Entities). */
        default void onKnotRemoved(LeashKnot knot) { }

        /** A mob attached (the Attach Entity 1 with the holder's id). */
        default void onMobAttached(MobEntity mob) { }

        /** A mob detached (the Attach Entity clear). */
        default void onMobDetached(MobEntity mob) { }
    }

    /** The engine's mob surface (the scan + the drop). */
    public interface Mobs {
        /**
         * Every live mob in the box (the tie scan) — the caller's own
         * dimension's bodies alone (FL4-GAP2 Slice D: the reference's
         * per-WorldServer entity list; a same-coordinate mob in another
         * world is no tie candidate).
         */
        List<MobEntity> mobsIn(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ);

        /**
         * The lead's drop at the mob (detachLeash's dropItem) — stamped
         * with the leashed body's own dimension (a nether mob's snapped
         * lead drops a NETHER item).
         */
        void dropLead(Position at, int dimension);
    }

    private final Mobs mobs;
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<BlockPosition, LeashKnot> knots = new LinkedHashMap<>();
    /** The knot id supplier — ONE band shared by every dimension's family. */
    private final java.util.function.IntSupplier knotIds;
    /** This family's world (the knots' survival poll reads ITS cells). */
    private final net.zaminmc.torch.World world;
    /** The fence identifier the survival poll accepts (canSurvive's own). */
    private final Object fenceIdentifier;
    /** The owning dimension (every knot this family creates carries it). */
    private final int dimension;

    public LeadSystem(Mobs mobs, java.util.function.IntSupplier knotIds,
                      net.zaminmc.torch.World world, Object fenceIdentifier,
                      int dimension) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.knotIds = Objects.requireNonNull(knotIds, "knotIds");
        this.world = Objects.requireNonNull(world, "world");
        this.fenceIdentifier = fenceIdentifier;
        this.dimension = dimension;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** The knot at the fence, or null (LeadKnotEntity.getOrCreate's probe). */
    public LeashKnot knotAt(BlockPosition fence) {
        return knots.get(fence);
    }

    /** Every live knot (the join view's walk). */
    public List<LeashKnot> allKnots() {
        return new ArrayList<>(knots.values());
    }

    /**
     * The lead's use-on-fence arm (LeadItem.useOn + attachLead lines
     * 26-56): every mob the player holds within the 7-cube of the fence
     * ties to the knot there (creating it on the first taker). The lead
     * itself is NOT consumed — the mob already paid it at the attach.
     *
     * @return whether any mob tied (the use's own success flag).
     */
    public boolean useLeadOnFence(PlayerSession player, BlockPosition fence) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(fence, "fence");
        boolean tied = false;
        for (MobEntity mob : mobsNear(fence.x() + 0.5, fence.y() + 0.5, fence.z() + 0.5)) {
            if (mob.isLeashed() && mob.leashHolderPlayer() == player) {
                attachToKnot(mob, fence);
                tied = true;
            }
        }
        return tied;
    }

    /**
     * The knot's own interact (LeadKnotEntity.interact lines 88-123): a
     * lead in hand ties every held mob within the 7-cube (same walk as the
     * fence arm); an empty hand (or a failed tie) removes the knot — the
     * creative hand releasing the leashed without the drop, the others
     * leaving the mobs to the next tick's holder-gone detach (which drops
     * the leads).
     */
    public void interactKnot(PlayerSession player, LeashKnot knot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(knot, "knot");
        boolean tied = false;
        var held = player.inventory().held();
        if (!held.isEmpty() && held.type().equals(BuiltinItems.LEAD)) {
            for (MobEntity mob : mobsNear(knot.position().x(), knot.position().y(),
                    knot.position().z())) {
                if (mob.isLeashed() && mob.leashHolderPlayer() == player) {
                    mob.attachLeashToKnot(knot);
                    for (Listener listener : listeners) {
                        listener.onMobAttached(mob);
                    }
                    tied = true;
                }
            }
        }
        if (!tied) {
            removeKnot(knot, player.gamemode() == net.zaminmc.torch.GameMode.CREATIVE);
        }
    }

    /**
     * A mob's right-click with the lead (MobEntity.interact lines 745-766):
     * a fresh lead on a leashing-kind mob attaches it to the player and
     * consumes one unit; the player's own held mob releases instead (the
     * drop unless creative).
     *
     * @return true when the click was consumed (no further interact arms).
     */
    public boolean interactMobWithLead(PlayerSession player, MobEntity mob) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(mob, "mob");
        if (mob.isLeashed() && mob.leashHolderPlayer() == player) {
            detachLeash(mob, player.gamemode() != net.zaminmc.torch.GameMode.CREATIVE);
            return true;
        }
        var held = player.inventory().held();
        if (!held.isEmpty() && held.type().equals(BuiltinItems.LEAD)
                && isLeashable(mob)) {
            // The tameable gate: the reference refuses the already-tamed
            // stranger's hand; the engine's mobs have no ownership yet, so
            // the tamed horse answers any rider's lead (the difference is
            // ledgered).
            mob.attachLeashToPlayer(player);
            for (Listener listener : listeners) {
                listener.onMobAttached(mob);
            }
            if (player.gamemode() != net.zaminmc.torch.GameMode.CREATIVE) {
                player.inventory().consumeHeld(1);
            }
            return true;
        }
        return false;
    }

    /** @return whether the kind takes a lead (MobEntity.isTameable: not a monster, not held). */
    private static boolean isLeashable(MobEntity mob) {
        return !mob.type().hostile && !mob.isLeashed();
    }

    /**
     * The detach (MobEntity.detachLeash lines 789-802): the state clears,
     * the lead drops at the mob (the dropItem arm), the wire clears.
     */
    public void detachLeash(MobEntity mob, boolean dropLead) {
        if (!mob.isLeashed()) {
            return;
        }
        mob.clearLeash();
        for (Listener listener : listeners) {
            listener.onMobDetached(mob);
        }
        if (dropLead) {
            mobs.dropLead(mob.position(), mob.dimension());
        }
    }

    /** The knot tie (attachLeash through the knot's getOrCreate). */
    private void attachToKnot(MobEntity mob, BlockPosition fence) {
        LeashKnot knot = knots.get(fence);
        if (knot == null) {
            // The first taker creates the knot (LeadKnotEntity.attatch —
            // the Spawn Object 77 announcement rides the creation).
            knot = new LeashKnot(knotIds.getAsInt(), fence, dimension);
            knots.put(fence, knot);
            for (Listener listener : listeners) {
                listener.onKnotSpawned(knot);
            }
        }
        mob.attachLeashToKnot(knot);
        for (Listener listener : listeners) {
            listener.onMobAttached(mob);
        }
    }

    /**
     * The restored knot (the anvil load path — the reference's
     * LeadKnotEntity.readCustomNbt: the Tile row IS the anchor; the
     * knot hangs at the anchor's center, the survival poll owns the
     * unsupported-anchor drop — exactly the decorations' load
     * convention). The publication fires the same Spawn Object 77 the
     * live tie carries; an anchor that already carries a knot keeps the
     * landed body (the occupied-anchor refusal, never a silent evict).
     */
    public LeashKnot restoreKnot(BlockPosition anchor) {
        Objects.requireNonNull(anchor, "anchor");
        LeashKnot existing = knots.get(anchor);
        if (existing != null) {
            return existing; // the occupied anchor keeps its landed body
        }
        LeashKnot knot = new LeashKnot(knotIds.getAsInt(), anchor, dimension);
        knots.put(anchor, knot);
        for (Listener listener : listeners) {
            listener.onKnotSpawned(knot);
        }
        return knot;
    }

    /**
     * The knot's removal: the registry entry drops, the wire destroys it,
     * and the creative hand releases the tied mobs without the lead drop
     * (the reference's own {@code detachLeash(true, false)}).
     */
    private void removeKnot(LeashKnot knot, boolean creative) {
        knots.remove(knot.fence());
        for (Listener listener : listeners) {
            listener.onKnotRemoved(knot);
        }
        if (creative) {
            for (MobEntity mob : mobs.mobsIn(
                    knot.position().x() - TIE_RANGE, knot.position().y() - TIE_RANGE,
                    knot.position().z() - TIE_RANGE, knot.position().x() + TIE_RANGE,
                    knot.position().y() + TIE_RANGE, knot.position().z() + TIE_RANGE)) {
                if (mob.isLeashed() && mob.leashHolderKnot() == knot) {
                    detachLeash(mob, false);
                }
            }
        }
        // The non-creative removal leaves the tied mobs holding a gone
        // holder: the tick walk's holder-gone arm releases them with the
        // drop (the reference's updateLeashStatus).
    }

    /**
     * The per-tick walk (PathFinderMobEntity.updateLeashStatus lines 72-122
     * + MobEntity.updateLeashStatus lines 773-787): every leashed mob
     * resolves its holder — a gone holder releases with the drop, a live
     * one walks the leash physics: the follow beyond four blocks, the pull
     * beyond six ({@code v += d * |d| * 0.4}), the snap at ten. The knots
     * run their own survival clock.
     *
     * <p><b>The stashed-leash resolution</b> (Slice 89 — the reference's
     * own {@code if (this.leashNbt != null) readLeashNbt()} at the walk's
     * top): a mob whose readNbt left an unresolved Leash compound resolves
     * it first — the knot form by position (a live knot attaches, anything
     * else drops with the lead), the holder-uuid form against the online
     * players (a live session attaches). The reference's any-entity holder
     * form (a mob holding another mob) has no engine model — those rows
     * resolve to nobody and drop (ledgered).</p>
     */
    public void tick(Iterable<MobEntity> allMobs,
                     java.util.function.Function<PlayerSession, Position> holderResolver,
                     java.util.function.Function<java.util.UUID,
                             java.util.Optional<PlayerSession>> playerByUuid) {
        for (MobEntity mob : allMobs) {
            if (!mob.isLeashed()) {
                continue;
            }
            // FL4-GAP2 Slice D — this family walks ITS OWN world's bodies
            // alone (the reference's per-WorldServer entity tick): the
            // leash rows live on the mob, but the knot consult and the
            // survival poll below read this family's own world.
            if (mob.dimension() != dimension) {
                continue;
            }
            if (mob.dead()) {
                detachLeash(mob, true);
                continue;
            }
            // The stashed Leash compound (the readNbt rows): resolve first.
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stash =
                    mob.takeLeashNbt();
            if (stash != null) {
                resolveStashedLeash(mob, stash, playerByUuid);
                continue; // the resolved walk picks up next tick
            }
            PlayerSession holderPlayer = mob.leashHolderPlayer();
            if (holderPlayer != null) {
                if (holderPlayer.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                        || holderPlayer.dead()) {
                    detachLeash(mob, true);
                    continue;
                }
                leashPhysics(mob, holderResolver.apply(holderPlayer));
            } else {
                LeashKnot knot = mob.leashHolderKnot();
                if (knot == null || !knots.containsKey(knot.fence())) {
                    // The holder-gone arm (the removed knot): the lead drops.
                    detachLeash(mob, true);
                    continue;
                }
                leashPhysics(mob, knot.position());
            }
        }
        // The knots' survival poll (DecorationEntity.tick's 100-tick walk)
        // — over THIS family's own world (a nether knot survives on the
        // NETHER's fences, never the overworld's cells at the same
        // coordinates).
        List<LeashKnot> fallen = null;
        for (LeashKnot knot : knots.values()) {
            if (!knot.tickSurvival(world, fenceIdentifier)) {
                if (fallen == null) {
                    fallen = new ArrayList<>();
                }
                fallen.add(knot);
            }
        }
        if (fallen != null) {
            for (LeashKnot knot : fallen) {
                removeKnot(knot, false); // the fence broke: no creative hand
            }
        }
    }

    /**
     * The stashed compound's resolution (the reference's readLeashNbt):
     * the X/Y/Z INT triple against the live knots, the UUIDMost/UUIDLeast
     * LONG pair against the online players; an unresolvable holder drops
     * the lead.
     */
    private void resolveStashedLeash(MobEntity mob,
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stash,
            java.util.function.Function<java.util.UUID,
                    java.util.Optional<PlayerSession>> playerByUuid) {
        net.zaminmc.torch.server.nbt.Snbt.Tag x =
                stash.get("X");
        if (x != null && net.zaminmc.torch.server.nbt.NbtOps.containsNumeric(stash, "X")
                && net.zaminmc.torch.server.nbt.NbtOps.containsNumeric(stash, "Y")
                && net.zaminmc.torch.server.nbt.NbtOps.containsNumeric(stash, "Z")) {
            net.zaminmc.torch.block.BlockPosition fence =
                    new net.zaminmc.torch.block.BlockPosition(
                            (int) net.zaminmc.torch.server.nbt.NbtOps.getDouble(stash, "X"),
                            (int) net.zaminmc.torch.server.nbt.NbtOps.getDouble(stash, "Y"),
                            (int) net.zaminmc.torch.server.nbt.NbtOps.getDouble(stash, "Z"));
            LeashKnot knot = knotAt(fence);
            if (knot != null) {
                mob.attachLeashToKnot(knot);
                return;
            }
            detachLeash(mob, true);
            return;
        }
        if (net.zaminmc.torch.server.nbt.NbtOps.contains(stash, "UUIDMost",
                        net.zaminmc.torch.server.nbt.Snbt.Tag.Type.LONG)
                && net.zaminmc.torch.server.nbt.NbtOps.contains(stash, "UUIDLeast",
                        net.zaminmc.torch.server.nbt.Snbt.Tag.Type.LONG)) {
            java.util.UUID holder = new java.util.UUID(
                    net.zaminmc.torch.server.nbt.NbtOps.getLong(stash, "UUIDMost"),
                    net.zaminmc.torch.server.nbt.NbtOps.getLong(stash, "UUIDLeast"));
            java.util.Optional<PlayerSession> session = playerByUuid.apply(holder);
            if (session.isPresent()) {
                mob.attachLeashToPlayer(session.get());
                return;
            }
        }
        // An unresolvable holder (the reference's any-entity form has no
        // engine model — ledgered): the lead drops.
        detachLeash(mob, true);
    }

    /** The leash physics (PathFinderMobEntity lines 88-111). */
    private void leashPhysics(MobEntity mob, Position holder) {
        double dx = holder.x() - mob.position().x();
        double dy = holder.y() - mob.position().y();
        double dz = holder.z() - mob.position().z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= FOLLOW_RANGE) {
            return; // inside the slack: no follow, no pull
        }
        // The follow (f > 4.0): the navigation walks to the holder at the
        // mob's own speed.
        mob.navigateToward(holder.x(), holder.y(), holder.z());
        if (distance > PULL_RANGE) {
            // The pull (f > 6.0): v += d * |d| * 0.4 per component.
            double ux = dx / distance;
            double uy = dy / distance;
            double uz = dz / distance;
            mob.addVelocity(ux * Math.abs(ux) * PULL_FORCE,
                    uy * Math.abs(uy) * PULL_FORCE,
                    uz * Math.abs(uz) * PULL_FORCE);
        }
        if (distance > SNAP_RANGE) {
            // The snap (f > 10.0): the leash breaks, the lead drops.
            detachLeash(mob, true);
        }
    }

    /** Every live mob within the 7-cube of a point (the tie scan). */
    private List<MobEntity> mobsNear(double x, double y, double z) {
        return mobs.mobsIn(x - TIE_RANGE, y - TIE_RANGE, z - TIE_RANGE,
                x + TIE_RANGE, y + TIE_RANGE, z + TIE_RANGE);
    }

    /** The owning dimension (the knots' own stamp — the wire's observer gate). */
    public int dimension() {
        return dimension;
    }
}
