package net.zaminmc.torch.server.fishing;

import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The fishing system (Slice 67 — the reference port of
 * entity/FishingBobberEntity.java + item/FishingRodItem.java): the cast,
 * the bobber's flight and water float, the catchable cycle (the approach,
 * the splash, the bite window), and the retrieve with its loot law.
 *
 * <p><b>The flight (tick lines 174-314, the server arm, 1:1):</b> the
 * 0.4-power lob scaled 1.5 with the gaussian 0.0075 scatter (thrown lines
 * 134-155); the block raytrace grounds the bobber (the 1200-tick stuck
 * timeout); the entity clip hooks the first collidable body along the
 * flight segment (the 0.3-grown box, the angler's own body legal after 5
 * air ticks) with zero damage; the drag 0.92 (0.5 grounded); the 5-band
 * water fraction drives the buoyancy {@code vy += 0.04 * (d10 * 2 - 1)}
 * and the in-water extra damping ({@code *0.9} on the drag,
 * {@code *0.8} on vy).</p>
 *
 * <p><b>The catchable cycle (tick lines 322-430, 1:1):</b> in water the
 * {@code l} modifier rolls (a 25% chance of 2 under rain, a 50% chance
 * of -1 without sky access); the approach timer arms at
 * {@code 100..900 - Lure * 100} (the reference's
 * {@code getLureLevel * 20 * 5}); the fish approach runs 20-80 ticks of
 * the fishAngle wander; the splash (the {@code vy -= 0.2} bob, the
 * random.splash sound at 0.25 volume) opens THE BITE WINDOW — 10-30
 * ticks inside which the retrieve lands a catch.</p>
 *
 * <p><b>The retrieve (retrieve lines 435-475 + getResult lines 477-510,
 * 1:1):</b> a hooked body drags toward the angler (the 0.1 pull plus the
 * {@code sqrt(distance) * 0.08} lift — result 3); inside the bite window
 * the loot roll answers (junk at {@code 0.1 - luck * 0.025 - lure * 0.01},
 * treasure at {@code 0.05 + luck * 0.01 - lure * 0.01}, the fish
 * otherwise — the weighted tables) and the XP orb (1-6) rides the
 * catch — result 1; a grounded bobber answers 2; anything else 0. The
 * rod wears by the result code (FishingRodItem.startUsing line 23).</p>
 *
 * <p><b>Threading (§27.6 — the monitor protocol, the VillageSystem
 * form):</b> the census-mutating arms ({@code cast}/{@code retrieve}/
 * {@code noteAnglerState}/{@code tick}) and the collection reads
 * ({@code bobberOf}/{@code all}) synchronize on the system itself —
 * the monitor the public view ({@code FishingViews} —
 * {@code Server.fishing()}'s answer) copies the census under,
 * tear-free against the tick lane from any thread. The registration
 * arms ({@code addCatchInterceptor}/{@code removeCatchInterceptor}/
 * {@code setLootSource}) share the same monitor, so a registration
 * from any thread lands between ticks — never inside a retrieve (the
 * walk the registered callbacks ride). The whole walk stays
 * tick-lane-owned: the engine's own consults ({@code tickFishing}/
 * {@code useFishingRod}) are same-thread, uncontended.</p>
 */
public final class FishingSystem {

    /** The bite window's band (nextInt(10, 30) — the reference's own). */
    public static final int BITE_WINDOW_MIN = 10;
    public static final int BITE_WINDOW_MAX = 30;
    /** The fish-approach band (nextInt(20, 80)). */
    public static final int FISH_APPROACH_MIN = 20;
    public static final int FISH_APPROACH_MAX = 80;
    /** The approach-arming band (nextInt(100, 900) before the Lure cut). */
    public static final int CATCH_ARM_MIN = 100;
    public static final int CATCH_ARM_MAX = 900;
    /** The Lure cut per level (getLureLevel * 20 * 5). */
    public static final int LURE_CUT_PER_LEVEL = 100;
    /** The stuck-in-ground timeout (inBlockTicks == 1200). */
    public static final int STUCK_TIMEOUT = 1200;
    /** The angler's leash (squaredDistanceTo > 1024 removes). */
    public static final double LEASH_SQUARED = 1024.0;
    /** The throw's power and scale (the ctor's own 0.4F and 1.5F). */
    public static final float THROW_POWER = 0.4F;
    public static final float THROW_SCALE = 1.5F;
    /** The flight drag (tick's f6 = 0.92, 0.5 grounded). */
    public static final double FLIGHT_DRAG = 0.92;
    public static final double GROUNDED_DRAG = 0.5;
    /** The bobber's own hull (setSize(0.25F, 0.25F)). */
    public static final double BOBBER_HALF_WIDTH = 0.125;
    public static final double BOBBER_HEIGHT = 0.25;

    /**
     * The engine surfaces the walk needs. Wired by the engine; every read
     * runs on the tick thread.
     */
    public interface WorldQuery {
        /** The block-collision read along the flight (the raytrace's ground). */
        Position blockHit(Position from, Position to);

        /** The 5-band water fraction of a box (the tick's own d10 walk). */
        double waterFraction(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ);

        /** The sky-access read at the bobber's cell + 1 (hasSkyAccess). */
        boolean hasSkyAccess(int x, int y, int z);

        /** Whether it rains (the l modifier's 2x roll). */
        boolean raining();
    }

    /**
     * The entity-hook surface (the clip against the 0.3-grown boxes and
     * the hooked body's follow + pull).
     */
    public interface HookResolver {
        /**
         * The first collidable body along the segment (the angler's own
         * body legal once airTicks >= 5), or null.
         */
        Object hookAlong(Position from, Position to, int throwerId, int airTicks);

        /** Whether the hooked body still lives (the follow's own gate). */
        boolean hookedAlive(Object hooked);

        /** The hooked body's anchor (x, minY + height * 0.8, z). */
        Position hookedAnchor(Object hooked);

        /** The retrieve's pull: the velocity nudge toward the angler. */
        void pullToward(Object hooked, double dx, double dy, double dz);
    }

    /** The engine's item drop + XP + sound surfaces. */
    public interface CatchSink {
        /** The caught item entity launched toward the angler (the 0.1 + sqrt
         * law) — in the angler's own dimension (Wave TF-9). */
        void dropCatch(Position at, net.zaminmc.torch.item.ItemStack stack,
                       double anglerX, double anglerY, double anglerZ, int dimension);

        /** The catch's XP orb (nextInt(6) + 1) — in the angler's own
         * dimension (Wave TF-10). */
        void dropXp(Position at, int amount, int dimension);

        /** A world sound — in the bobber's own dimension (Wave TF-12: the
         * reference's playSound on the bobber's World). */
        void sound(Position at, String key, float volume, float pitch, int dimension);
    }

    /**
     * The loot band the roll landed in (the reference's three — the
     * junk/treasure/fish walk of getResult lines 487-506).
     */
    public enum LootCategory {
        /** The 0.1-band (the junk eleven). */
        JUNK,
        /** The 0.05-band (the treasure six). */
        TREASURE,
        /** The fish remainder (the 60/25/2/13 four). */
        FISH
    }

    /**
     * The loot override (§27.7's replaceable strategy): replaces the
     * caught stack per band. Answering {@code null} falls through to
     * the vanilla weighted roll for that band — the override composes
     * with the reference's own law instead of silently replacing the
     * whole walk. Runs on the tick lane inside the retrieve; the luck
     * and lure levels are the held rod's own reads (the same inputs the
     * vanilla roll sees).
     */
    public interface LootSource {
        /**
         * @param category the band the roll landed in
         * @return the caught stack, or {@code null} to use the vanilla
         *         weighted roll for the band
         */
        net.zaminmc.torch.item.ItemStack roll(LootCategory category, int luck, int lure,
                                              Random random);
    }

    /**
     * The catch's own context (the interceptor's view — the roll's
     * outcome plus the angler's state).
     */
    public record CatchContext(int anglerId, Position bobberAt, LootCategory category,
                               int luckLevel, int lureLevel,
                               net.zaminmc.torch.item.ItemStack caught) {
    }

    /**
     * The catch intercept (§27.7's observe + cancel/replace hook): runs
     * on the tick lane between the loot roll and the drop, in
     * registration order — each interceptor sees the incoming stack
     * (an earlier interceptor's replacement included) and answers the
     * outgoing one.
     *
     * <ul>
     *   <li>return the incoming stack (or an equal one) — pass through;
     *   <li>return another stack — replace (the drop and the XP ride
     *       the replacement; the XP amount is the vanilla roll);
     *   <li>return {@link net.zaminmc.torch.item.ItemStack#EMPTY} (or
     *       {@code null}) — cancel the catch: no item, no XP, and the
     *       retrieve answers 0 (the bare case — the rod does not wear,
     *       the reference's own {@code result > 0} wear gate).
     * </ul>
     */
    public interface CatchInterceptor {
        /** @return the outgoing stack, or EMPTY/null to cancel the catch. */
        net.zaminmc.torch.item.ItemStack onCatch(CatchContext context);
    }

    /** Events the protocol adapter translates into wire updates. */
    public interface Listener {
        /** The bobber spawned: Spawn Object 90 (objectData = the angler). */
        void onBobberSpawned(Bobber bobber);

        /** The bobber moved: Entity Teleport (the per-tick sync). */
        void onBobberMoved(Bobber bobber);

        /** The bobber left: Destroy. */
        void onBobberRemoved(Bobber bobber);
    }

    /** One angler's bobber (the reference's own field set). */
    public static final class Bobber {
        final int entityId;
        final int throwerId;
        final int dimension;
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        boolean inGround;
        int inGroundTicks;
        int inAirTicks;
        int catchTimer;
        int fishTravelTimer;
        float fishAngle;
        int selfHitTimer;
        Object hooked;
        boolean removed;

        Bobber(int entityId, int throwerId, int dimension, double x, double y, double z) {
            this.entityId = entityId;
            this.throwerId = throwerId;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int entityId() {
            return entityId;
        }

        public int throwerId() {
            return throwerId;
        }

        /**
         * The bobber's own dimension (Wave TF-12 — the reference's
         * Entity.world): the angler's at the cast, the splash's own world.
         */
        public int dimension() {
            return dimension;
        }

        public Position position() {
            return new Position(x, y, z);
        }

        public double velocityX() {
            return vx;
        }

        public double velocityY() {
            return vy;
        }

        public double velocityZ() {
            return vz;
        }

        /** The bite window's own state (the retrieve's gate). */
        public boolean biting() {
            return selfHitTimer > 0;
        }

        public boolean grounded() {
            return inGround;
        }

        public Object hooked() {
            return hooked;
        }

        public boolean removed() {
            return removed;
        }
    }

    /** One weighted loot row (FishingLootEntry's own shape). */
    private record LootRow(net.zaminmc.torch.item.ItemStack stack, int weight) {
    }

    // The reference's own tables (lines 24-48), 1:1 — the engine's item
    // forms (the junk rod/boots carry their damage at the roll).
    private static final List<LootRow> JUNK_LOOT = List.of(
            new LootRow(damaged("leather_boots", 0.9F), 10),
            new LootRow(stack("leather"), 10),
            new LootRow(stack("bone"), 10),
            new LootRow(stack("potion"), 10),
            new LootRow(stack("string"), 5),
            new LootRow(damaged("fishing_rod", 0.9F), 2),
            new LootRow(stack("bowl"), 10),
            new LootRow(stack("stick"), 5),
            new LootRow(stack("dye"), 1), // ink sac (DyeColor.BLACK = 0)
            new LootRow(stack("tripwire_hook"), 10),
            new LootRow(stack("rotten_flesh"), 10));
    private static final List<LootRow> TREASURE_LOOT = List.of(
            new LootRow(stack("waterlily"), 1),
            new LootRow(stack("name_tag"), 1),
            new LootRow(stack("saddle"), 1),
            new LootRow(damaged("bow", 0.25F), 1),
            new LootRow(damaged("fishing_rod", 0.25F), 1),
            new LootRow(stack("book"), 1));
    private static final List<LootRow> FISH_LOOT = List.of(
            new LootRow(stack("fish", 0), 60), // cod
            new LootRow(stack("fish", 1), 25), // salmon
            new LootRow(stack("fish", 2), 2), // clownfish
            new LootRow(stack("fish", 3), 13)); // pufferfish

    private static net.zaminmc.torch.item.ItemStack stack(String name) {
        return net.zaminmc.torch.item.ItemStack.of(item(name), 1);
    }

    private static net.zaminmc.torch.item.ItemStack stack(String name, int damage) {
        return net.zaminmc.torch.item.ItemStack.of(item(name), 1).withDamage(damage);
    }

    /** The damaged loot form (setDamage's fraction of maxDurability). */
    private static net.zaminmc.torch.item.ItemStack damaged(String name, float fraction) {
        net.zaminmc.torch.item.ItemType type = item(name);
        int damage = Math.max(1, (int) (fraction * type.maxDurability()));
        return net.zaminmc.torch.item.ItemStack.of(type, 1).withDamage(damage);
    }

    private static net.zaminmc.torch.item.ItemType item(String name) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:" + name))
                .orElseThrow(() -> new IllegalStateException("Fishing loot item missing: " + name));
    }

    private final WorldQuery world;
    private final HookResolver hooks;
    private final CatchSink catches;
    private final Random random;
    private final List<Listener> listeners = new ArrayList<>();
    /** One bobber per angler (the reference's player.fishingBobber field). */
    private final java.util.Map<Integer, Bobber> byAngler = new java.util.LinkedHashMap<>();
    /** The §27.7 catch interceptors (registration order). */
    private final List<CatchInterceptor> interceptors = new ArrayList<>();
    private int nextEntityId;
    /** The enchant reads (overridden by the engine's held-rod wiring). */
    private java.util.function.IntUnaryOperator luckRead = throwerId -> 0;
    private java.util.function.IntUnaryOperator lureRead = throwerId -> 0;
    /** The §27.7 loot override (null = the vanilla roll). */
    private LootSource lootSource;

    public FishingSystem(WorldQuery world, HookResolver hooks, CatchSink catches,
                         Random random, int firstEntityId) {
        this.world = Objects.requireNonNull(world, "world");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.catches = Objects.requireNonNull(catches, "catches");
        this.random = Objects.requireNonNull(random, "random");
        this.nextEntityId = firstEntityId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** @return the angler's live bobber, or null. §27.6: the monitor
     *         protocol — the read the public view's snapshot copies
     *         ride (safe from any thread under the monitor). */
    public synchronized Bobber bobberOf(int throwerId) {
        return byAngler.get(throwerId);
    }

    /** @return every live bobber (diagnostics). §27.6: the monitor
     *         protocol — the copy the public view's snapshot rides
     *         (safe from any thread under the monitor). */
    public synchronized List<Bobber> all() {
        return List.copyOf(byAngler.values());
    }

    /** Wires the held-rod enchant reads (the engine's EnchantmentHelper). */
    public void setEnchantReads(java.util.function.IntUnaryOperator luckRead,
                                java.util.function.IntUnaryOperator lureRead) {
        this.luckRead = Objects.requireNonNull(luckRead, "luckRead");
        this.lureRead = Objects.requireNonNull(lureRead, "lureRead");
    }

    /**
     * The loot override (§27.7 — the replaceable strategy): answering
     * null falls through per band. Tick-lane registration (before or
     * between ticks; an unregister mid-tick is honored on the next
     * retrieve).
     *
     * @param source the override, or null to restore the vanilla roll
     */
    public synchronized void setLootSource(LootSource source) {
        this.lootSource = source;
    }

    /**
     * The catch intercept (§27.7): registration order; an interceptor
     * may pass through, replace, or cancel the catch (EMPTY).
     * §27.6: the monitor protocol — safe from any thread (a
     * registration lands between ticks, never inside a retrieve).
     */
    public synchronized void addCatchInterceptor(CatchInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor, "interceptor"));
    }

    /** @return whether the interceptor was registered. §27.6: the
     *         monitor protocol — safe from any thread. */
    public synchronized boolean removeCatchInterceptor(CatchInterceptor interceptor) {
        return interceptors.remove(interceptor);
    }

    /**
     * The held-rod gate (tick lines 181-189): the angler gone, the rod
     * un-held, or the leash broken ends the cast. §27.6: the monitor
     * protocol (the engine's per-tick consult is same-thread,
     * uncontended).
     */
    public synchronized void noteAnglerState(int throwerId, boolean alive, boolean heldRod,
                                Position anglerAt) {
        Bobber bobber = byAngler.get(throwerId);
        if (bobber == null) {
            return;
        }
        if (!alive || !heldRod) {
            remove(bobber);
            return;
        }
        double dx = anglerAt.x() - bobber.x;
        double dy = anglerAt.y() - bobber.y;
        double dz = anglerAt.z() - bobber.z;
        if (dx * dx + dy * dy + dz * dz > LEASH_SQUARED) {
            remove(bobber);
        }
    }

    /**
     * The cast (the ctor + thrown lines 107-155): the eye-height origin
     * pulled back 0.16 along the look, the 0.4-power direction scaled
     * 1.5 with the gaussian 0.0075 scatter. One bobber per angler (the
     * reference's own field shape — a second cast is unreachable).
     * The bobber spawns in the angler's own dimension (Wave TF-12 — the
     * reference spawns it in the angler's World).
     * §27.6: the monitor protocol (the census mutation the public
     * view's snapshot copies serialize against).
     */
    public synchronized Bobber cast(int throwerId, Position eye, float yaw, float pitch,
                                     int dimension) {
        Bobber existing = byAngler.get(throwerId);
        if (existing != null) {
            return existing;
        }
        double radiansYaw = Math.toRadians(yaw);
        double radiansPitch = Math.toRadians(pitch);
        double x = eye.x() - Math.cos(radiansYaw) * 0.16;
        double y = eye.y() - 0.1;
        double z = eye.z() - Math.sin(radiansYaw) * 0.16;
        Bobber bobber = new Bobber(nextEntityId++, throwerId, dimension, x, y, z);
        double vx = -Math.sin(radiansYaw) * Math.cos(radiansPitch) * THROW_POWER;
        double vz = Math.cos(radiansYaw) * Math.cos(radiansPitch) * THROW_POWER;
        double vy = -Math.sin(radiansPitch) * THROW_POWER;
        // thrown lines 137-152: the normalize, the gaussian scatter, the scale.
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= length;
        vy /= length;
        vz /= length;
        vx += random.nextGaussian() * 0.0075;
        vy += random.nextGaussian() * 0.0075;
        vz += random.nextGaussian() * 0.0075;
        bobber.vx = vx * THROW_SCALE;
        bobber.vy = vy * THROW_SCALE;
        bobber.vz = vz * THROW_SCALE;
        byAngler.put(throwerId, bobber);
        for (Listener listener : listeners) {
            listener.onBobberSpawned(bobber);
        }
        return bobber;
    }

    /**
     * The retrieve (retrieve lines 435-475): the hooked drag (result 3),
     * the bite-window catch with its loot + XP (result 1), the grounded
     * answer (2), nothing (0). Removes the bobber either way.
     * §27.6: the monitor protocol — the registered callbacks run
     * inside the retrieve on the tick lane, under the monitor (a
     * blocking callback stalls the census reads, never the walk's
     * own lane order).
     */
    public synchronized int retrieve(int throwerId, Position anglerAt, int dimension) {
        Bobber bobber = byAngler.get(throwerId);
        if (bobber == null) {
            return 0;
        }
        int result = 0;
        // Wave TF-9 — the angler's own dimension rides the catch drop (the
        // body's world, never the system's binding).
        final int retrieveDimension = dimension;
        if (bobber.hooked != null) {
            double dx = anglerAt.x() - bobber.x;
            double dy = anglerAt.y() - bobber.y;
            double dz = anglerAt.z() - bobber.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            hooks.pullToward(bobber.hooked, dx * 0.1,
                    dy * 0.1 + Math.sqrt(distance) * 0.08, dz * 0.1);
            result = 3;
        } else if (bobber.selfHitTimer > 0) {
            // The §27.7 wave: the roll carries its band, the interceptors
            // observe/replace/cancel before the drop.
            LootRoll loot = getResult(throwerId);
            net.zaminmc.torch.item.ItemStack caught = loot.stack();
            boolean cancelled = false;
            if (!interceptors.isEmpty()) {
                for (CatchInterceptor interceptor : interceptors) {
                    CatchContext context = new CatchContext(throwerId,
                            bobber.position(), loot.category(), loot.luck(), loot.lure(),
                            caught);
                    net.zaminmc.torch.item.ItemStack next = interceptor.onCatch(context);
                    if (next == null || next.isEmpty()) {
                        cancelled = true;
                        break;
                    }
                    caught = next;
                }
            }
            if (cancelled) {
                // The catch never happened: no drop, no XP, and the
                // retrieve answers the bare 0 (the rod's own wear gate
                // is the reference's result > 0).
                result = 0;
            } else {
                catches.dropCatch(bobber.position(), caught,
                        anglerAt.x(), anglerAt.y(), anglerAt.z(), retrieveDimension);
                catches.dropXp(new Position(anglerAt.x(), anglerAt.y() + 0.5, anglerAt.z()),
                        random.nextInt(6) + 1, retrieveDimension);
                result = 1;
            }
        }
        if (bobber.inGround) {
            result = 2;
        }
        remove(bobber);
        return result;
    }

    /** One loot roll's outcome (the stack + the band it landed in). */
    private record LootRoll(net.zaminmc.torch.item.ItemStack stack, LootCategory category,
                            int luck, int lure) {
    }

    /** The loot roll (getResult lines 477-510, 1:1 — the §27.7 source rides the band picks). */
    private LootRoll getResult(int throwerId) {
        int luck = luckRead.applyAsInt(throwerId);
        int lure = lureRead.applyAsInt(throwerId);
        float roll = random.nextFloat();
        float junkChance = 0.1F - luck * 0.025F - lure * 0.01F;
        float treasureChance = 0.05F + luck * 0.01F - lure * 0.01F;
        junkChance = Math.max(0.0F, Math.min(1.0F, junkChance));
        treasureChance = Math.max(0.0F, Math.min(1.0F, treasureChance));
        if (roll < junkChance) {
            return new LootRoll(pickLoot(LootCategory.JUNK, JUNK_LOOT, luck, lure),
                    LootCategory.JUNK, luck, lure);
        }
        roll -= junkChance;
        if (roll < treasureChance) {
            return new LootRoll(pickLoot(LootCategory.TREASURE, TREASURE_LOOT, luck, lure),
                    LootCategory.TREASURE, luck, lure);
        }
        return new LootRoll(pickLoot(LootCategory.FISH, FISH_LOOT, luck, lure),
                LootCategory.FISH, luck, lure);
    }

    /** One band's pick: the override first (null = fall through), the vanilla table otherwise. */
    private net.zaminmc.torch.item.ItemStack pickLoot(LootCategory category,
                                                      List<LootRow> table, int luck, int lure) {
        if (lootSource != null) {
            net.zaminmc.torch.item.ItemStack override =
                    lootSource.roll(category, luck, lure, random);
            if (override != null) {
                return override;
            }
        }
        return pickWeighted(table);
    }

    /** WeightedPicker.pick's own walk (the weight-proportional roll). */
    private net.zaminmc.torch.item.ItemStack pickWeighted(List<LootRow> table) {
        int total = 0;
        for (LootRow row : table) {
            total += row.weight();
        }
        int roll = random.nextInt(total);
        for (LootRow row : table) {
            roll -= row.weight();
            if (roll < 0) {
                return row.stack();
            }
        }
        return table.get(table.size() - 1).stack();
    }

    private void remove(Bobber bobber) {
        bobber.removed = true;
        byAngler.remove(bobber.throwerId);
        for (Listener listener : listeners) {
            listener.onBobberRemoved(bobber);
        }
    }

    /**
     * The tick walk (tick lines 174-430, the server arm, 1:1): every
     * angler's bobber moves, floats, and runs the catchable cycle. The
     * held-rod gate rides the engine's per-angler noteAnglerState consult
     * before the walk (the same tick's own order). §27.6: the monitor
     * protocol — the walk holds the census monitor, so a foreign
     * snapshot read reflects the census between ticks, never a
     * half-written walk.
     */
    public synchronized void tick() {
        for (Bobber bobber : List.copyOf(byAngler.values())) {
            tickOne(bobber);
        }
    }

    private void tickOne(Bobber bobber) {
        // The hooked follow (lines 192-203): the bobber rides the body.
        if (bobber.hooked != null) {
            if (hooks.hookedAlive(bobber.hooked)) {
                Position anchor = hooks.hookedAnchor(bobber.hooked);
                bobber.x = anchor.x();
                bobber.y = anchor.y();
                bobber.z = anchor.z();
                return;
            }
            bobber.hooked = null;
        }
        // The stuck clock (lines 216-232): 1200 grounded ticks remove.
        if (bobber.inGround) {
            bobber.inGroundTicks++;
            if (bobber.inGroundTicks == STUCK_TIMEOUT) {
                remove(bobber);
                return;
            }
        } else {
            bobber.inAirTicks++;
        }
        if (!bobber.inGround) {
            // The block raytrace (lines 248-257): the flight's ground.
            Position from = bobber.position();
            Position to = new Position(bobber.x + bobber.vx, bobber.y + bobber.vy,
                    bobber.z + bobber.vz);
            Position blockHit = world.blockHit(from, to);
            // The entity clip (lines 259-292): the first collidable body
            // (the 0.0F damage hook).
            Object hooked = hooks.hookAlong(from,
                    blockHit != null ? blockHit : to, bobber.throwerId, bobber.inAirTicks);
            if (hooked != null) {
                bobber.hooked = hooked;
            } else if (blockHit != null) {
                bobber.inGround = true;
            }
        }
        if (!bobber.inGround && bobber.hooked == null) {
            // The flight (lines 294-314): move, drag, the water's buoyancy.
            bobber.x += bobber.vx;
            bobber.y += bobber.vy;
            bobber.z += bobber.vz;
            double drag = FLIGHT_DRAG;
            // The 5-band water fraction of the bobber's own 0.25 box.
            double fraction = world.waterFraction(
                    bobber.x - BOBBER_HALF_WIDTH, bobber.y, bobber.z - BOBBER_HALF_WIDTH,
                    bobber.x + BOBBER_HALF_WIDTH, bobber.y + BOBBER_HEIGHT,
                    bobber.z + BOBBER_HALF_WIDTH);
            if (fraction > 0.0) {
                drag *= 0.9;
                bobber.vy *= 0.8;
                tickCatchCycle(bobber);
            }
            bobber.vy += 0.04 * (fraction * 2.0 - 1.0);
            bobber.vx *= drag;
            bobber.vy *= drag;
            bobber.vz *= drag;
        }
        for (Listener listener : listeners) {
            listener.onBobberMoved(bobber);
        }
    }

    /**
     * The catchable cycle (lines 322-430, 1:1): the l modifier's rolls,
     * the bite window, the fish approach, the approach arming.
     */
    private void tickCatchCycle(Bobber bobber) {
        // The l modifier (lines 326-337): the rain's 2x roll (25%), the
        // no-sky -1 roll (50%).
        int l = 1;
        if (random.nextFloat() < 0.25F && world.raining()) {
            l = 2;
        }
        if (random.nextFloat() < 0.5F
                && !world.hasSkyAccess((int) Math.floor(bobber.x),
                (int) Math.floor(bobber.y) + 1, (int) Math.floor(bobber.z))) {
            l--;
        }
        if (bobber.selfHitTimer > 0) {
            bobber.selfHitTimer--;
            if (bobber.selfHitTimer <= 0) {
                bobber.catchTimer = 0;
                bobber.fishTravelTimer = 0;
            }
        } else if (bobber.fishTravelTimer > 0) {
            bobber.fishTravelTimer -= l;
            if (bobber.fishTravelTimer <= 0) {
                // The splash (lines 348-355): the bob + the sound.
                bobber.vy -= 0.2;
                catches.sound(bobber.position(), "random.splash", 0.25F,
                        1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F,
                        bobber.dimension());
                bobber.selfHitTimer = BITE_WINDOW_MIN
                        + random.nextInt(BITE_WINDOW_MAX - BITE_WINDOW_MIN);
            } else {
                bobber.fishAngle += (float) (random.nextGaussian() * 4.0);
            }
        } else if (bobber.catchTimer > 0) {
            bobber.catchTimer -= l;
            if (bobber.catchTimer <= 0) {
                bobber.fishAngle = random.nextFloat() * 360.0F;
                bobber.fishTravelTimer = FISH_APPROACH_MIN
                        + random.nextInt(FISH_APPROACH_MAX - FISH_APPROACH_MIN);
            }
        } else {
            // The arming (lines 424-430): 100..900 minus the Lure cut.
            bobber.catchTimer = CATCH_ARM_MIN + random.nextInt(CATCH_ARM_MAX - CATCH_ARM_MIN)
                    - lureRead.applyAsInt(bobber.throwerId) * LURE_CUT_PER_LEVEL;
        }
        // The bite window's own bob (lines 432-434): the y jitter.
        if (bobber.selfHitTimer > 0) {
            bobber.vy -= random.nextFloat() * random.nextFloat() * random.nextFloat() * 0.2;
        }
    }
}
