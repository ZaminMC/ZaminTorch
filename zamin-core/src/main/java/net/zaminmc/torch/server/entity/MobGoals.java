package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import java.util.List;

/**
 * The built-in goal set, in the historical priority order. The behaviors are
 * the ones the body always had (panic, the idle/wander cycle with its look-at
 * glance, the fluid swim-up); the goal layer is the community architecture
 * wrapped around them so new goals join by priority, not by editing the mind.
 *
 * <p>Each built-in carries a stable id (the addressable identity of the
 * public registration surface, §27.3): "float", "panic", "breed",
 * "stroll".</p>
 */
final class MobGoals {

    private MobGoals() {
    }

    /** Priority 0, additive: a body in fluid always swims upward a little. */
    static final MobGoal FLOAT = new MobGoal() {
        @Override public String goalId() { return "float"; }
        @Override public int priority() { return 0; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalInFluid();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalSwimUp();
        }
    };

    /**
     * Priority 0, additive, after the float (TF-7f — the skeleton's
     * AvoidSunlightGoal, reference/1.8.8 net/minecraft/entity/ai/goal/
     * AvoidSunlightGoal 1:1; the reference's own relative order, the
     * swim before the sun-shade, SkeletonEntity lines 47-49): a pure
     * FLAG goal — while the sun stands it arms the navigation's
     * avoid-sunlight trim (start line 23) and drives nothing else; the
     * mind-tick default clear carries the stop() arm (line 27) when
     * the sun sets. The ADDITIVE seat is the reference's own
     * concurrency: the goal sets no mutex flags, so it runs beside
     * whatever exclusive goal owns the body — the panic, the escape,
     * the stroll — and every path adopted under the sun gets trimmed
     * at the sky boundary (GroundPathNavigation.trimPath lines 61-77,
     * the cut at the last shaded node before the first sky-exposed
     * one — the reference's truncate(i - 1)).
     */
    static final MobGoal AVOID_SUNLIGHT = new MobGoal() {
        @Override public String goalId() { return "avoid_sunlight"; }
        @Override public int priority() { return 0; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalAvoidSunlightCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalAvoidSunlightTick();
        }
    };

    /** Priority 1, exclusive: the hurt panic outranks everything but floating.
     * (Wave PE-15 — EscapeDangerGoal 1:1: the census, the attacker/fire
     * gate, and TargetFinder's 5/4 pick all ride the canUse walk.) */
    static final MobGoal PANIC = new MobGoal() {
        @Override public String goalId() { return "panic"; }
        @Override public int priority() { return 1; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalPanicCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTickPanic();
        }
    };

    /**
     * Priority 1, exclusive (the band's second seat — a hurt body panics
     * first, a pressed one breaks): the zombie's door break (VIII9 —
     * BreakDoorGoal 1:1): a canBreakDoors-armed zombie that clips a
     * closed wooden door mid-walk claims it — the 240-tick hardness walk
     * with the 0-9 crack stages, the 1-in-20 knock sound, and the
     * HARD-alone completion (the door cell's removal + the 1012/2001
     * pair). The arming happens at the body's own clip site (the
     * reference's canStart reads collidingHorizontally + the live path —
     * the named adaptation the detour intercept carries); this goal's
     * canUse is the sticky walk, the tick the progress clock.
     */
    static final MobGoal BREAK_DOOR = new MobGoal() {
        @Override public String goalId() { return "break_door"; }
        @Override public int priority() { return 1; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalZombieBreakDoorCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalZombieBreakDoorTick();
        }
    };

    /**
     * Priority 1, exclusive, LATE in the band (the band's third seat —
     * the reference's own registration order, HorseBaseEntity lines
     * 107-108: the escape BEFORE the bond at the same number; a hurt
     * body panics first, a pressed one breaks, the rider's dice ride
     * last — Wave PE-25): the UNTAMED horse's riding window
     * (HorseBondWithPlayerGoal 1:1). The rider gate, the (5, 4)
     * TargetFinder pick, the 1.2-speed wander while the seat is held,
     * and THE RIDING DICE — a 1-in-50 roll per live tick: the temper
     * dice nextInt(maxTemper) &lt; temper TAMES (the hearts Entity
     * Status 7, no buck on the taming tick), else +5 temper and THE
     * BUCK (the thrown dismount, the angry flag + its 20-tick decay,
     * the angry sound, the smoke Entity Status 6). The taming law the
     * engine's former MountAttempt stand-in rolled at MOUNT time —
     * retired; the dice ride the window the reference's own mind runs
     * (the untamed seat keeps the mind: serverTickAi carries no rider
     * gate, moveRelative's rider arm requires the saddle).
     */
    static final MobGoal HORSE_BOND = new MobGoal() {
        @Override public String goalId() { return "horse_bond"; }
        @Override public int priority() { return 1; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalHorseBondCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalHorseBondTick();
        }
    };

    /**
     * Priority 1, additive, after the villager's flee (the reference's
     * own band-1 order — the flee, the customer look, the customer
     * stand; Wave PE-26): the customer LOOK (LookAtCustomerGoal over
     * LookAtEntityGoal 1:1): canStart's whole law is hasCustomer — NO
     * chance dice, a standing customer always claims the head. The
     * inherited window laws: the 40+nextInt(40) hold with the head on
     * the customer's eyes, the end at the customer's departure beyond
     * the ctor's 8. ADDITIVE (LOOK control only — setControls(2)) so it
     * runs BESIDE the stand goal's exclusive claim, the reference's
     * own mutex law (the stand holds MOVE+JUMP, the look holds LOOK).
     */
    static final MobGoal LOOK_CUSTOMER = new MobGoal() {
        @Override public String goalId() { return "look_customer"; }
        @Override public int priority() { return 1; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalLookCustomerCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalLookCustomerTick();
        }
    };

    /**
     * Priority 1, exclusive, after the customer look (the reference's
     * own band-1 order; Wave PE-26): the customer STAND
     * (StopFollowingCustomerGoal 1:1): the villager stands still while
     * its customer stands within 4 blocks with the trade window CLOSED
     * — and the claim's end CLEARS THE CUSTOMER (stop():
     * setCustomer(null)), the reference's own lifetime manager. The
     * gates: not in water, on the ground, not damaged, the window
     * closed, the customer within squared-16. start() stops the
     * navigation; the claim itself is the stand (setControls(5) — MOVE
     * and JUMP held, the head left free for the look).
     */
    static final MobGoal STOP_CUSTOMER = new MobGoal() {
        @Override public String goalId() { return "stop_customer"; }
        @Override public int priority() { return 1; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalStopCustomerCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalStopCustomerTick();
        }
    };

    /**
     * Priority 2, exclusive: the breeding drive (the vanilla AnimalBreedGoal).
     * A loved body scans for the nearest legal partner, walks to it, and
     * after 60 ticks inside the 3-block band the breed lands through the
     * pending flag (the manager executes it — it owns spawning).
     *
     * <p>VIII10 — the VILLAGER's own arm (VillagerMatingGoal over the
     * same breed surface): the 1-in-500 cadence, the village's census
     * gate, the food/willing model (the 3-bread/12-potato/12-carrot
     * consume arms the willing at the attempt), and the mate scan over
     * the willing pair — the walk itself (the proximity window + the
     * request) rides the shared shape.</p>
     *
     * <p>TF-1 — the tameable family's own yield (the reference's own
     * priority order: FollowOwnerGoal 5 over the wolf's AnimalBreedGoal 6
     * and the ocelot's 9): a tamed pet whose owner stands beyond the
     * follow's 10-block start band never breeds — the follow could claim
     * the body, so the breed yields exactly like the reference's selector
     * steal.</p>
     */
    static final MobGoal BREED = new MobGoal() {
        @Override public String goalId() { return "breed"; }
        @Override public int priority() { return 2; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            if (mob.type() == MobType.VILLAGER) {
                return mob.goalVillagerMatingCanUse();
            }
            return mob.isInLove() && mob.findMate() != null
                    && !mob.followOwnerStartable();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            MobEntity mate = mob.findMate();
            if (mate == null) {
                mob.resetBreedTimer();
                return;
            }
            mob.goalTickBreed(mate);
            if (mob.advanceBreedTimer() >= MobEntity.BREED_PROXIMITY_TICKS) {
                double dx = mate.position().x() - mob.position().x();
                double dy = mate.position().y() - mob.position().y();
                double dz = mate.position().z() - mob.position().z();
                if (dx * dx + dy * dy + dz * dz < MobEntity.BREED_DISTANCE_SQUARED) {
                    mob.requestBreedWith(mate);
                } else {
                    // The vanilla stop(): the 60-tick window closed without
                    // proximity, the goal restarts and the clock resets.
                    mob.resetBreedTimer();
                }
            }
        }
    };

    /**
     * Priority 3, additive (LOOK control only — WolfBegGoal's own shape):
     * the wolf's begging gaze. The nearest player within 8 blocks holding a
     * bone (the untamed gate) or the meat breeding family holds the head
     * for a {@code 40 + nextInt(40)} window; the wire's index-19 flag and
     * the stroll's glance both yield to it.
     */
    static final MobGoal BEG = new MobGoal() {
        @Override public String goalId() { return "beg"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalBegCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalBegTick();
        }
    };

    /**
     * Priority 3, exclusive: the ocelot's fish-follow (TemptGoal 1:1). The
     * cat walks to the nearest fish-holder within 10 blocks at the 0.6
     * creep (the shared-flags sneak posture), holds at 2.5 blocks, and
     * only that tempted state opens the tame's fish arm. A player who
     * moves or turns inside 6 blocks scares the cat off for 100 ticks.
     */
    static final MobGoal TEMPT = new MobGoal() {
        @Override public String goalId() { return "tempt"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalTemptCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTemptTick();
        }
    };

    /**
     * Priority 3, exclusive: the untamed ocelot's player-flee
     * (FleeEntityGoal 1:1). Any live player within 16 blocks sends the
     * wild cat running the other way — the far walk at 0.8, the 7-block
     * close band sprinting at 1.33 (the shared-flags sprint posture). The
     * tamed cat never flees (onTamedChanged's own removal). The rabbit's
     * own flee (Slice 21d — the wolf within 16 at 1.33 both bands) rides
     * the same band; the killer bunny never flees (setVariant's own
     * removeGoal).
     */
    /**
     * Priority 3, exclusive (Slice TF-7b — the skeleton's
     * EscapeSunlightGoal, registered at band 3 BEFORE the wolf flee —
     * the reference's own order, SkeletonEntity lines 50-51): the
     * on-fire body under the open sunny sky walks to the first shaded
     * cell the favor family rejects. The registration is the skeleton's
     * own (the wither variant never joins — the nether has no sun).
     */
    static final MobGoal ESCAPE_SUNLIGHT = new MobGoal() {
        @Override public String goalId() { return "escape_sunlight"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalEscapeSunlightCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalEscapeSunlightTick();
        }
    };

    /**
     * Priority 1, exclusive (the band's third seat — Wave PE-15): the
     * villager's zombie flee (VillagerEntity line 309,
     * FleeEntityGoal(ZombieEntity.class, 8.0F, 0.6, 0.6) — the
     * reference's own band 1, above every walk the villager carries):
     * the grown-box zombie census, the away-half-plane pick, the path
     * pair, and the 49-squared speed band inside the shared tick.
     */
    static final MobGoal VILLAGER_FLEE = new MobGoal() {
        @Override public String goalId() { return "flee_zombie"; }
        @Override public int priority() { return 1; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalFleeZombieCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTickFlee();
        }
    };

    /**
     * Priority 3, exclusive: the flee family (FleeEntityGoal 1:1 — Wave
     * PE-15 rebuilt the whole band): the ocelot's player-flee (the
     * reference's band 4, 16.0F/0.8/1.33 — the tamed cat never flees),
     * the rabbit's wolf-flee (band 4, 16.0F/1.33/1.33 — the killer
     * bunny never flees), the skeleton's wolf-flee (band 3,
     * 6.0F/1.0/1.2 — Slice 16b's intercept migrated onto the goal), and
     * the creeper's ocelot-flee (band 3, 6.0F/1.0/1.2 — the famous
     * cat-scares-creeper, yielding to the ignite's own band-2 claim).
     * Every site rides the same grown-box census, the away-half-plane
     * pick, the farther-check, and the path pair; the shared tick
     * carries the 49-squared speed band.
     */
    static final MobGoal FLEE_PLAYER = new MobGoal() {
        @Override public String goalId() { return "flee_player"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalFleePlayerCanUse() || mob.goalFleeWolfCanUse()
                    || mob.goalFleeWolfSkeletonCanUse() || mob.goalFleeCatCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTickFlee();
        }
    };

    /**
     * Priority 4, exclusive, FIRST in the band: the tameable family's
     * owner-follow (FollowOwnerGoal 1:1 — the reference's own goal 5 on
     * both kinds, above the wander's 7/10 and the perch's 6, below the
     * tempt's 3): the tamed pet whose owner stands 10+ blocks away walks
     * to them at the 1.0 goal speed, the path re-armed every 10 ticks,
     * and a failed path at 12+ blocks teleports through the 5x5 outer
     * ring. The wolf's own 2-block settle band, the ocelot's 5.
     */
    static final MobGoal FOLLOW_OWNER = new MobGoal() {
        @Override public String goalId() { return "follow_owner"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalFollowOwnerCanUse(night);
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalFollowOwnerTick();
        }
    };

    /**
     * Priority 4, exclusive, after the follow: the tamed cat's perch
     * (OcelotSitOnBlockGoal 1:1 over GoToBlockGoal — the reference's own
     * goal 6): the 200+nextInt(200) cadence, the 8-block ring scan for a
     * chest nobody watches, a lit furnace, or the bed's foot half, the
     * walk at 0.8, and the sit at the arrival — the window standing the
     * body down when its own clock runs out.
     */
    static final MobGoal SIT_ON_BLOCK = new MobGoal() {
        @Override public String goalId() { return "sit_on_block"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalSitOnBlockCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalSitOnBlockTick();
        }
    };

    /**
     * Priority 4, exclusive: the rabbit's garden raid (Slice 21d —
     * RaidFarmGoal over GoToBlockGoal 1:1). A hungry rabbit scans the
     * ±16 box on the reference's own spiral for a farmland cell with
     * age-7 carrots, walks there at 0.7, and eats the crop (the world
     * write lands through the manager's intent). Yields while any hunt
     * is live — the reference's own priority order (the attack family's
     * 4 over the farm's 5).
     */
    static final MobGoal RABBIT_RAID_FARM = new MobGoal() {
        @Override public String goalId() { return "rabbit_raid_farm"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return !mob.goalHasTarget(night) && mob.goalRabbitFarmCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalRabbitFarmTick();
        }
    };

    /**
     * Priority 3, additive (LOOK control only — IronGolemLookGoal's own
     * shape, Wave PE-22, the reference's golem band 5 over the village
     * walk's 4 and the plain wander's 6): the golem's glance at a
     * villager. A sunny 1-in-8000 roll arms the 400-tick window — the
     * head holds the nearest villager inside the golem's OWN shape
     * grown 6/2, and the golem's own offer clock
     * (lookingAtVillagerTicksLeft) stands above zero for the village
     * children's FollowGolemGoal to read.
     */
    static final MobGoal GOLEM_LOOK = new MobGoal() {
        @Override public String goalId() { return "golem_look"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalGolemLookCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalGolemLookTick();
        }
    };

    /**
     * Priority 4, exclusive, FIRST in the band (the reference villager's
     * own door band 4 under the village walk's 5 — Wave PE-22): the
     * villager's door OPEN walk (LongDoorInteractGoal over the shared
     * AbstractDoorInteractGoal base, shouldCloseDoor true). A pressed
     * body with a live path through a closed wooden door captures it at
     * the clip site, the door swings open, and the 20-tick window closes
     * it again — unless the body walks through first (the entry-offset
     * dot product's sign flip).
     */
    static final MobGoal OPEN_DOOR = new MobGoal() {
        @Override public String goalId() { return "open_door"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalVillagerDoorCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalVillagerDoorTick();
        }
    };

    /**
     * Priority 4, exclusive (the reference's own band — the cow/sheep/
     * chicken's 4 and the pig's 5, under the breed's 2 and the tempt's
     * 3, over the plain wander's 6/9 — Wave PE-22): the baby's parent
     * walk (FollowParentGoal 1:1). A baby of the four kinds scans for
     * the nearest adult inside its OWN shape grown 8/4, refuses the
     * start under 3 blocks, and walks to the parent — the re-path every
     * 10 ticks, the window the 3..16-block band, the walk's speed the
     * registration's own (the cow's 1.25, the others' 1.1).
     */
    static final MobGoal FOLLOW_PARENT = new MobGoal() {
        @Override public String goalId() { return "follow_parent"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalFollowParentCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalFollowParentTick();
        }
    };

    /**
     * Priority 4, exclusive, LATE in the band (the reference's own
     * order — the golem follow 7 under the mating 6 and the village
     * walk's 5, over the plain wander's 9 — Wave PE-22): the villager
     * child's golem walk (FollowGolemGoal 1:1). A child under the sunny
     * sky reads the golems whose offer clock stands, waits for the
     * golem's clock to hit its own armed trigger exactly, walks to it
     * at the flat 0.5, and releases the golem's offer inside the
     * 2-block band.
     */
    static final MobGoal FOLLOW_GOLEM = new MobGoal() {
        @Override public String goalId() { return "follow_golem"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalFollowGolemCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalFollowGolemTick();
        }
    };

    /**
     * Priority 4, exclusive, LAST before the stroll (the reference's
     * own order — the graze 5 over the plain wander 6, under the parent
     * walk's 4 — Wave PE-22): the sheep's graze (EatGrassGoal 1:1). The
     * baby's 1-in-50 dice (the adult's 1-in-1000) over a tall-grass
     * feet cell or a grass block below, the 40-tick head-down window
     * (the Entity Status 10 the client renders), and the write at
     * timer-4: the tall grass breaks drop-less or the grass block pays
     * the 2001 burst + the DIRT write, both under the mobGriefing gate
     * — the coat returns either way (onEatingGrass), and a baby grows
     * 60 units closer to adulthood.
     */
    static final MobGoal EAT_GRASS = new MobGoal() {
        @Override public String goalId() { return "eat_grass"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalEatGrassCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalEatGrassTick();
        }
    };

    /**
     * Priority 4, exclusive: the idle/wander cycle, with the historical
     * LookAtPlayer glance folded into the idle band.
     */
    static final MobGoal RANDOM_STROLL = new MobGoal() {
        @Override public String goalId() { return "stroll"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return !mob.goalHasTarget(night); // yields while a hunt is live
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTickIdleWander(mob.nearestVisiblePlayer());
        }
    };

    /**
     * Priority 4, exclusive, before the stroll (the reference's own
     * order — the village walk 5 over the plain wander 9): the
     * villager's return home when its cached village sits behind the
     * body's radius (WanderThroughVillageGoal, VT-7k).
     */
    static final MobGoal VILLAGE_RETURN = new MobGoal() {
        @Override public String goalId() { return "village_return"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalVillageReturnCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalVillageReturnTick();
        }
    };

    /**
     * Priority 4, exclusive, LATE in the walk band (the reference's
     * own village-family order — the zombie's night-raid walk at band
     * 6 beside the plain wander's 7, the golem's at band 3 above its
     * own wander — Wave PE-24): the night village walk
     * (WanderThroughVillageAtNightGoal 1:1). The zombie raids any
     * clock (the friendly gate false); the golem refuses the sunny
     * sky. The door-memory list (the 15-row cap) spreads the walk
     * across the village's doors, the pre-computed path walks on
     * under the navigation's own drive, and the (width+4)² continue
     * band + the stop's memory write own the window.
     */
    static final MobGoal NIGHT_VILLAGE = new MobGoal() {
        @Override public String goalId() { return "night_village"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalNightVillageCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalNightVillageTick();
        }
    };

    /**
     * Priority 4, exclusive, after the caravan (the reference's own
     * band 9 — beside the plain wander, after the golem walk's 7 and
     * the caravan's 8 — Wave PE-24): the villager's close-player stare
     * (GoToEntityGoal over LookAtEntityGoal — the LOOK family's window
     * laws with the MOVE mutex the subclass adds). A player within 3
     * blocks of an idle villager holds the body: the chance-1.0 roll,
     * the nearest player consult, the 40+nextInt(40) window with the
     * head on the player's eyes — the famous stand-and-stare.
     */
    static final MobGoal LOOK_AT_CLOSE = new MobGoal() {
        @Override public String goalId() { return "look_at_close"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalLookAtCloseCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalLookAtCloseTick();
        }
    };

    /**
     * Priority 4, exclusive, after the close stare (the reference's own
     * band-9 order — the stare, the share walk, the wander; Wave PE-26):
     * the villager's FOOD SHARE (TradeWithVillagerGoal over
     * GoToEntityGoal 1:1): the 2% dice, the nearest villager within the
     * body's own shape grown (3, 3, 3) — the stand-and-stare the
     * subclass's MOVE mutex carries — and the donation arm: a walker
     * with two breeding units to spare staring at a villager with room
     * throws half its first donatable stack out at the 10-tick cooldown
     * (bread/potato/carrot beyond 3 donate count/2 of the same kind,
     * wheat beyond 5 donates its half rounded to loaves of 3 as BREAD,
     * the item thrown from the eyes along the head facing at 0.3
     * speed). The village food-sharing loop — the farmer feeds the
     * pack, and the found villager's own loot pickup gathers the throw.
     */
    static final MobGoal TRADE_SHARE = new MobGoal() {
        @Override public String goalId() { return "trade_share"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalTradeShareCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTradeShareTick();
        }
    };

    /**
     * Priority 3, additive (LOOK control only — LookAtEntityGoal's own
     * shape, Wave PE-23, {@code setControls(2)}): the universal
     * look-at. Every strolling kind's own registration — the 2% dice,
     * the nearest player inside the kind's own range (6 the animal
     * family and the golems, 8 the monster family and the wolf, 10 the
     * rabbit and the ocelot), the villager's own MOB target at 8, the
     * guardian's own-kind pair at 12/0.01 — holds the head on the
     * target's eyes for a {@code 40 + nextInt(40)} window. The head-yaw
     * write rides the wire's Entity Head Look packet.
     */
    static final MobGoal LOOK_AT = new MobGoal() {
        @Override public String goalId() { return "look_at"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalLookAtEntityCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalLookAtEntityTick();
        }
    };

    /**
     * Priority 4, exclusive, AFTER the stroll (the reference's own
     * per-kind ladders — the look-around 7/8/11 rides AFTER the plain
     * wander's 6/7/9/10 on every registrant's list, Wave PE-23): the
     * idle look-around ({@code LookAroundGoal} 1:1, {@code
     * setControls(3)} — it holds the MOVE bit too, so a live window
     * keeps the idle body standing). The same 2% dice, the random
     * angle's cos/sin pair, the {@code 20 + nextInt(20)} window. Its
     * place AFTER the stroll in the engine's selector is the reference's
     * own fill-idle-time law: a walk claim outranks the look-around
     * (the reference's earlier-band steal), the look-around fills the
     * gaps between walks. The registrants are the reference's own set —
     * every look-at kind EXCEPT the villager, the rabbit, the ocelot
     * and the ghast.
     */
    static final MobGoal LOOK_AROUND = new MobGoal() {
        @Override public String goalId() { return "look_around"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalLookAroundCanUse(night);
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalLookAroundTick();
        }
    };

    /**
     * Priority 2, exclusive, FIRST in the band (the reference's own
     * villager ladder — StayIndoorsGoal at band 2 above every walk the
     * villager carries, under the flee's 1 — Wave PE-23): the
     * night-and-rain shelter walk. The sunless-or-dry-biome-rain gate,
     * the 1-in-50 dice, the recent-cell refusal, the ticking-door
     * pick, and the walk to the indoors cell (the far arm's away pick
     * beyond 16 blocks) at the flat 1.0 speed.
     */
    static final MobGoal STAY_INDOORS = new MobGoal() {
        @Override public String goalId() { return "stay_indoors"; }
        @Override public int priority() { return 2; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalStayIndoorsCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalStayIndoorsTick();
        }
    };

    /**
     * Priority 3, ADDITIVE ({@code RestrictOpenDoorGoal} sets NO
     * controls — the reference's own ctor, zero mutex bits — it runs
     * beside every walk the villager carries — Wave PE-23): the
     * sunless door guard. A villager standing inside its nearest
     * door's indoors band drops the navigation's door permissions and
     * grows the door's over-use penalty every tick — the
     * restrictedTicks the ticking-door pick reads, the villagers' own
     * door-spread law.
     */
    static final MobGoal RESTRICT_DOOR = new MobGoal() {
        @Override public String goalId() { return "restrict_door"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalRestrictDoorCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalRestrictDoorTick();
        }
    };

    /**
     * Priority 4, exclusive (the reference's own band 6 for the
     * profession-0 adult, the grow-up registration at 8 — under the
     * village walk's 5 and the mating's 6, over the plain wander's 9
     * — Wave PE-23): the villager's farm walk (VillagerFarmGoal over
     * GoToBlockGoal 1:1). The mobGriefing gate, the 200+nextInt(200)
     * scan cadence, the ±16 spiral for a farmland cell with age-7
     * wheat (the harvest — broken WITH its drops, the stock grows by
     * the loot pickup) or an empty above-cell (the replant — the seed
     * consumed from the 8-slot stock, the crop placed at age 0), the
     * 0.6 walk with the 40-tick re-path, and the 10-tick cooldown
     * after a work tick.
     */
    static final MobGoal VILLAGER_FARM = new MobGoal() {
        @Override public String goalId() { return "villager_farm"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalVillagerFarmCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalVillagerFarmTick();
        }
    };

    /**
     * Priority 4, exclusive, LATE in the band (the reference's own
     * band 8 for the villager child, under the golem walk's 7 —
     * Wave PE-23): the baby's caravan walk (FormCaravanGoal 1:1). The
     * 1-in-400 dice over the census — the nearest other baby not
     * already in a caravan inside the grown-6/3/6 box — or the plain
     * (16, 3) pick alone; the 1000-tick window, the 0.32-speed walk
     * to the target beyond the squared-4 band, and the alone arm's
     * drift when the navigation idles.
     */
    static final MobGoal FORM_CARAVAN = new MobGoal() {
        @Override public String goalId() { return "form_caravan"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalFormCaravanCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalFormCaravanTick();
        }
    };

    /**
     * The selector the mob runs: float + panic + the zombie's door break
     * + the villager's shelter walk + breed + the awareness band (beg,
     * the golem's glance, the universal look-at, the door guard, tempt,
     * flee — the wolf, the ocelot, and the golem's own, all kind-gated)
     * + the walk band (the villager's door open, the tameable pair, the
     * baby's parent walk, the rabbit's garden raid, the villager's
     * village return, the farmer's farm walk, the child's golem walk,
     * the child's caravan, the sheep's graze) + stroll + the idle
     * look-around, in priority order. The band-1 order matters: the
     * panic claims a hurt body first, the door break a pressed one
     * (the reference's own 1-band; a hurt zombie runs, it does not stop
     * to break). The band-2 order matters: the shelter walk claims the
     * sunless villager before the breed (the reference's own 2-over-6).
     * The band-3 order matters: the beg, the glance and the look-at
     * are additive so they run first every tick, the door guard rides
     * additively beside them (the reference's own zero-controls
     * registration), then the tempt outranks the flee (the reference's
     * own 3-over-4), so a fish-holder holds a tempted cat even with
     * players around. The band-4 order matters the same way: the
     * villager's door open first (the reference's 4 under the village
     * walk's 5), the owner-follow outranks the perch and the plain
     * stroll (the reference's 5-over-6/7/10), the perch outranks the
     * stroll (the reference's 6-over-10), the child's golem walk and
     * the caravan ride LATE (the reference's 7 and 8 over the wander's
     * 9, under the village walk's 5), and the graze rides LAST before
     * the stroll (the reference's 5-over-6 on the sheep's own ladder)
     * — a tamed cat walks to its owner before it perches, and perches
     * before it wanders. The look-around rides after the stroll — the
     * reference's own per-kind ladder (the look 7/8/11 after the
     * wander's 6/7/9/10): the walks outrank it, it fills the idle gaps.
     */
    static GoalSelector standard() {
        return new GoalSelector(List.of(FLOAT, AVOID_SUNLIGHT, PANIC, BREAK_DOOR,
                HORSE_BOND, VILLAGER_FLEE, LOOK_CUSTOMER, STOP_CUSTOMER, STAY_INDOORS, BREED,
                BEG, GOLEM_LOOK, LOOK_AT, RESTRICT_DOOR, TEMPT, ESCAPE_SUNLIGHT, FLEE_PLAYER,
                OPEN_DOOR, FOLLOW_OWNER, SIT_ON_BLOCK, FOLLOW_PARENT,
                RABBIT_RAID_FARM, VILLAGE_RETURN, NIGHT_VILLAGE, VILLAGER_FARM,
                FOLLOW_GOLEM, FORM_CARAVAN, LOOK_AT_CLOSE, TRADE_SHARE, EAT_GRASS,
                RANDOM_STROLL, LOOK_AROUND));
    }
}
